package com.vibelink.sender.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.vibelink.sender.MainActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

// Shows black cursor overlay when Controller sends tap coordinates
public class TapOverlayService extends Service {

    private static final String TAG = "TapOverlay";
    private static final int TAP_PORT = 7790;
    private static final int CURSOR_SIZE_DP = 40;
    private static final long CURSOR_DURATION_MS = 900;

    private static final String CHANNEL_ID = "vibelink_tap";
    private static final int NOTIF_ID = 1002;

    private ServerSocket serverSocket;
    private Thread listenerThread;
    private WindowManager windowManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, buildNotification());
        startTapServer();
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopTapServer();
    }

    private void startTapServer() {
        if (listenerThread != null) return;
        listenerThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(TAP_PORT);
                Log.i(TAG, "Tap overlay server on port " + TAP_PORT);

                while (serverSocket != null && !serverSocket.isClosed()) {
                    try (Socket client = serverSocket.accept();
                         BufferedReader br = new BufferedReader(
                                 new InputStreamReader(client.getInputStream()))) {
                        String line = br.readLine();
                        if (line != null) {
                            String[] parts = line.trim().split(",");
                            if (parts.length >= 2) {
                                int x = Integer.parseInt(parts[0].trim());
                                int y = Integer.parseInt(parts[1].trim());
                                mainHandler.post(() -> showTapCursor(x, y));
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Tap server client error: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Tap server error: " + e.getMessage());
            }
        });
        listenerThread.start();
    }

    private void stopTapServer() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
                serverSocket = null;
            }
        } catch (Exception ignored) {}
        listenerThread = null;
    }

    private void showTapCursor(int x, int y) {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission required");
            return;
        }

        int sizePx = (int) (CURSOR_SIZE_DP * getResources().getDisplayMetrics().density);
        int radius = sizePx / 2;

        View circle = new View(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.BLACK);
        int strokePx = (int) (4 * getResources().getDisplayMetrics().density);
        bg.setStroke(strokePx, Color.WHITE);
        circle.setBackground(bg);
        circle.setAlpha(1f);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                sizePx, sizePx,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = x - radius;
        params.y = y - radius;

        windowManager.addView(circle, params);

        mainHandler.postDelayed(() -> {
            try {
                windowManager.removeView(circle);
            } catch (Exception ignored) {}
        }, CURSOR_DURATION_MS);
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "clodock Tap", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("clodock")
                .setContentText("자동화 중 - 탭 표시")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }
}
