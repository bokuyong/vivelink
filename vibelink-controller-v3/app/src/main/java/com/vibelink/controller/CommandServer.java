package com.vibelink.controller;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Minimal HTTP server - receives voice command JSON from sender app
// POST /toss  body: {"recipient":"아내","amount":10000}
public class CommandServer {

    private static final String TAG = "CommandServer";
    public static final int PORT = 7789;

    public interface CommandListener {
        void onTossCommand(String recipient, int amount);
    }

    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private CommandListener listener;

    public void setListener(CommandListener l) {
        this.listener = l;
    }

    // Start listening on PORT in background thread
    public void start() {
        running = true;
        executor.execute(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                Log.i(TAG, "CommandServer listening on port " + PORT);

                while (running) {
                    Socket client = serverSocket.accept();
                    executor.execute(() -> handleClient(client));
                }
            } catch (Exception e) {
                if (running) Log.e(TAG, "Server error: " + e.getMessage());
            }
        });
    }

    private void handleClient(Socket client) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), "UTF-8"));

            // Read HTTP request headers until blank line
            String line;
            int contentLength = 0;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.split(":")[1].trim());
                }
            }

            // Read JSON body
            char[] body = new char[contentLength];
            reader.read(body, 0, contentLength);
            String json = new String(body);
            Log.d(TAG, "Received: " + json);

            // Parse JSON manually (avoid Gson dependency here)
            String recipient = extractJsonString(json, "recipient");
            int amount = extractJsonInt(json, "amount");

            // Send HTTP 200 response
            OutputStream out = client.getOutputStream();
            String response = "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK";
            out.write(response.getBytes("UTF-8"));
            out.flush();
            client.close();

            // Dispatch command on listener
            if (listener != null && recipient != null && amount > 0) {
                listener.onTossCommand(recipient, amount);
            } else {
                Log.w(TAG, "Invalid command - recipient=" + recipient + " amount=" + amount);
            }

        } catch (Exception e) {
            Log.e(TAG, "handleClient error: " + e.getMessage());
        }
    }

    // Extract string value from simple JSON: {"key":"value"}
    private String extractJsonString(String json, String key) {
        try {
            String search = "\"" + key + "\":\"";
            int start = json.indexOf(search) + search.length();
            int end = json.indexOf("\"", start);

            return json.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }

    // Extract int value from simple JSON: {"key":1234}
    private int extractJsonInt(String json, String key) {
        try {
            String search = "\"" + key + "\":";
            int start = json.indexOf(search) + search.length();
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end)))) end++;

            return Integer.parseInt(json.substring(start, end));
        } catch (Exception e) {
            return -1;
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception e) {
            Log.w(TAG, "stop: " + e.getMessage());
        }
        executor.shutdownNow();
    }
}
