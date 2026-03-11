package com.vibelink.controller.network;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

// Receives JPEG frames from sender device over TCP and decodes to Bitmap
public class StreamReceiver {

    private static final String TAG = "StreamReceiver";
    private static final int RECONNECT_DELAY_MS = 2000;
    private static final int MAX_FRAME_BYTES = 5_000_000; // 5MB safety cap

    private Socket socket;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private FrameListener listener;
    private String senderIp;
    private final int senderPort;

    public interface FrameListener {
        void onFrame(Bitmap frame);
        void onConnected();
        void onDisconnected();
        void onError(String message);
    }

    public StreamReceiver(String senderIp, int senderPort) {
        this.senderIp = senderIp;
        this.senderPort = senderPort;
    }

    public void setListener(FrameListener listener) {
        this.listener = listener;
    }

    // Start receiving - auto-reconnects on disconnect
    public void connect() {
        running.set(true);
        executor.submit(this::receiveLoop);
    }

    private void receiveLoop() {
        while (running.get()) {
            try {
                Log.i(TAG, "Connecting to " + senderIp + ":" + senderPort);
                socket = new Socket(senderIp, senderPort);
                socket.setTcpNoDelay(true);
                socket.setSoTimeout(30000); // 30s read timeout

                if (listener != null) listener.onConnected();

                DataInputStream din = new DataInputStream(socket.getInputStream());

                while (running.get()) {
                    // Read 4-byte big-endian length header
                    int length = din.readInt();

                    if (length <= 0 || length > MAX_FRAME_BYTES) {
                        Log.w(TAG, "Invalid frame length: " + length);
                        break;
                    }

                    byte[] data = new byte[length];
                    din.readFully(data);

                    Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                    if (bmp != null && listener != null) {
                        listener.onFrame(bmp);
                    }
                }

            } catch (IOException e) {
                if (running.get()) {
                    Log.w(TAG, "Disconnected: " + e.getMessage());
                    if (listener != null) listener.onDisconnected();
                    sleepMs(RECONNECT_DELAY_MS);
                }
            } finally {
                closeSocket();
            }
        }
    }

    private void closeSocket() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }

    private void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void disconnect() {
        running.set(false);
        closeSocket();
        executor.shutdownNow();
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public void updateIp(String ip) {
        this.senderIp = ip;
    }
}
