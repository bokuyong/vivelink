package com.vibelink.controller;

import android.util.Log;

import com.vibelink.controller.command.CommandRequest;
import com.vibelink.controller.command.CommandResult;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CommandServer {

    private static final String TAG = "CommandServer";
    public static final int PORT = AppConfig.COMMAND_PORT;

    public interface CommandListener {
        CommandResult onCommand(CommandRequest request);
    }

    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private CommandListener listener;

    public void setListener(CommandListener listener) {
        this.listener = listener;
    }

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
                if (running) {
                    Log.e(TAG, "Server error: " + e.getMessage());
                }
            }
        });
    }

    private void handleClient(Socket client) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), "UTF-8"));

            String line;
            int contentLength = 0;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.split(":")[1].trim());
                }
            }

            char[] body = new char[contentLength];
            reader.read(body, 0, contentLength);
            String json = new String(body);
            JSONObject requestJson = new JSONObject(json);

            CommandRequest request = new CommandRequest(
                    requestJson.optString("rawText", ""),
                    requestJson.optString("source", "unknown"),
                    requestJson.optLong("requestedAt", System.currentTimeMillis())
            );

            CommandResult result = listener != null
                    ? listener.onCommand(request)
                    : CommandResult.unsupported("명령 리스너가 준비되지 않았습니다.");

            byte[] responseBody = buildResponseBody(result).getBytes("UTF-8");
            OutputStream out = client.getOutputStream();
            String response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
                    + responseBody.length + "\r\n\r\n";
            out.write(response.getBytes("UTF-8"));
            out.write(responseBody);
            out.flush();
            client.close();
        } catch (Exception e) {
            Log.e(TAG, "handleClient error: " + e.getMessage());
        }
    }

    private String buildResponseBody(CommandResult result) {
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("supported", result.isSupported());
            jsonObject.put("executed", result.isExecuted());
            jsonObject.put("message", result.getMessage());
            jsonObject.put("commandType", result.getCommandType());
            return jsonObject.toString();
        } catch (Exception e) {
            return "{\"supported\":false,\"executed\":false,\"message\":\"응답 생성 실패\"}";
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "stop: " + e.getMessage());
        }
        executor.shutdownNow();
    }
}
