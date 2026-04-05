package com.vibelink.sender.network;

import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

// TCP server that streams H.264 encoded frames to the controller device
public class StreamServer {

    private static final String TAG = "StreamServer";
    public static final int DEFAULT_PORT = 7788;
    private static final int FRAME_QUEUE_SIZE = 5;

    private ServerSocket serverSocket;
    private Socket clientSocket;
    private OutputStream outputStream;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final BlockingQueue<byte[]> frameQueue = new ArrayBlockingQueue<>(FRAME_QUEUE_SIZE);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final int port;
    private StreamListener listener;

    public interface StreamListener {
        void onClientConnected(String ip);
        void onClientDisconnected();
        void onError(String message);
    }

    public StreamServer(int port) {
        this.port = port;
    }

    public void setListener(StreamListener listener) {
        this.listener = listener;
    }

    // Start accepting connection from controller device
    public void start() {
        running.set(true);
        executor.submit(this::acceptLoop);
        executor.submit(this::sendLoop);
    }

    // Accept one controller connection at a time
    private void acceptLoop() {
        try {
            serverSocket = new ServerSocket(port);
            Log.i(TAG, "Server listening on port " + port);

            while (running.get()) {
                clientSocket = serverSocket.accept();
                outputStream = clientSocket.getOutputStream();
                String clientIp = clientSocket.getInetAddress().getHostAddress();
                Log.i(TAG, "Controller connected: " + clientIp);

                if (listener != null) {
                    listener.onClientConnected(clientIp);
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                Log.e(TAG, "Accept error: " + e.getMessage());
                if (listener != null) listener.onError(e.getMessage());
            }
        }
    }

    // Drain frame queue and write to socket
    private void sendLoop() {
        while (running.get()) {
            try {
                byte[] frame = frameQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (frame == null) continue;

                OutputStream out = outputStream;
                if (out == null) continue;

                // Write 4-byte length header then frame data
                int len = frame.length;
                out.write(new byte[]{
                        (byte) (len >> 24),
                        (byte) (len >> 16),
                        (byte) (len >> 8),
                        (byte) len
                });
                out.write(frame);
                out.flush();

            } catch (IOException e) {
                Log.w(TAG, "Client disconnected during send: " + e.getMessage());
                outputStream = null;
                if (listener != null) listener.onClientDisconnected();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // Called by encoder with each encoded H.264 frame
    public void sendFrame(byte[] data) {
        if (!frameQueue.offer(data)) {
            frameQueue.poll(); // drop oldest if full
            frameQueue.offer(data);
        }
    }

    public void stop() {
        running.set(false);
        executor.shutdownNow();
        try {
            if (clientSocket != null) clientSocket.close();
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
    }

    public boolean isClientConnected() {
        return clientSocket != null && clientSocket.isConnected() && !clientSocket.isClosed();
    }
}
