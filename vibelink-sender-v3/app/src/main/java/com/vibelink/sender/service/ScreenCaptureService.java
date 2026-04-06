package com.vibelink.sender.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.vibelink.sender.MainActivity;
import com.vibelink.sender.network.StreamServer;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

// Foreground service that captures screen via MediaProjection (JPEG) and streams to controller
public class ScreenCaptureService extends Service {

    private static final String TAG = "ScreenCaptureService";
    private static final String CHANNEL_ID = "vibelink_capture";
    private static final int NOTIF_ID = 1001;

    private static final int STREAM_WIDTH = 720;
    private static final int STREAM_HEIGHT = 1280;
    private static final int JPEG_QUALITY = 60;
    private static final long MIN_FRAME_INTERVAL_MS = 100;

    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_RESULT_DATA = "resultData";

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private StreamServer streamServer;
    private HandlerThread imageThread;
    private Handler imageHandler;
    private PowerManager.WakeLock wakeLock;
    private long lastFrameMs = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        // Keep CPU running while streaming
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "clodock:StreamWakeLock");
        wakeLock.acquire();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, buildNotification());

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1);
        Intent resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);

        MediaProjectionManager projMgr =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = projMgr.getMediaProjection(resultCode, resultData);

        if (streamServer == null) {
            streamServer = new StreamServer(StreamServer.DEFAULT_PORT);
            streamServer.start();
        }

        startCapture();

        return START_NOT_STICKY;
    }

    // Set up ImageReader and VirtualDisplay for JPEG-based screen capture
    private void startCapture() {
        imageThread = new HandlerThread("ImageCapture");
        imageThread.start();
        imageHandler = new Handler(imageThread.getLooper());

        imageReader = ImageReader.newInstance(
                STREAM_WIDTH, STREAM_HEIGHT,
                PixelFormat.RGBA_8888, 2);

        imageReader.setOnImageAvailableListener(reader -> {
            long now = System.currentTimeMillis();
            if (now - lastFrameMs < MIN_FRAME_INTERVAL_MS) {
                Image img = reader.acquireLatestImage();
                if (img != null) img.close();

                return;
            }
            lastFrameMs = now;
            processImage(reader);
        }, imageHandler);

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(metrics);

        // Android 14+ requires callback before createVirtualDisplay
        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                Log.w(TAG, "MediaProjection stopped by system");
                // Release resources but keep service alive for reconnect
                releaseCapture();
            }
        }, imageHandler);

        virtualDisplay = mediaProjection.createVirtualDisplay(
                "clodockCapture",
                STREAM_WIDTH, STREAM_HEIGHT, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);

        Log.i(TAG, "Screen capture started: " + STREAM_WIDTH + "x" + STREAM_HEIGHT);
    }

    // Release only capture resources, keep StreamServer alive
    private void releaseCapture() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (imageThread != null) {
            imageThread.quitSafely();
            imageThread = null;
        }
    }

    // Acquire frame, encode as JPEG, send to StreamServer
    private void processImage(ImageReader reader) {
        try (Image image = reader.acquireLatestImage()) {
            if (image == null) return;

            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * STREAM_WIDTH;

            Bitmap bmp = Bitmap.createBitmap(
                    STREAM_WIDTH + rowPadding / pixelStride,
                    STREAM_HEIGHT,
                    Bitmap.Config.ARGB_8888);
            bmp.copyPixelsFromBuffer(buffer);

            Bitmap cropped = Bitmap.createBitmap(bmp, 0, 0, STREAM_WIDTH, STREAM_HEIGHT);
            bmp.recycle();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos);
            cropped.recycle();

            streamServer.sendFrame(baos.toByteArray());

        } catch (Exception e) {
            Log.e(TAG, "processImage error: " + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseCapture();
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        if (streamServer != null) {
            streamServer.stop();
            streamServer = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "clodock Capture", NotificationManager.IMPORTANCE_LOW);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("clodock Sender v5")
                .setContentText("화면 스트리밍 중... (앱을 포그라운드 유지)")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }
}
