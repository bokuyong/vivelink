package com.vibelink.sender;

import android.util.Log;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DeviceDiscoveryClient {

    public static final int COMMAND_PORT = 7789;
    private static final String TAG = "DeviceDiscovery";
    private static final int DISCOVERY_PORT = 7791;
    private static final String DISCOVERY_MESSAGE = "CLODOCK_DISCOVER_V5";
    private static final int BUFFER_SIZE = 1024;
    private static final int SOCKET_TIMEOUT_MS = 1200;

    public List<ControllerTarget> discover() {
        Map<String, ControllerTarget> foundTargets = new LinkedHashMap<>();

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);

            byte[] requestBytes = DISCOVERY_MESSAGE.getBytes("UTF-8");
            DatagramPacket request = new DatagramPacket(
                    requestBytes,
                    requestBytes.length,
                    InetAddress.getByName("255.255.255.255"),
                    DISCOVERY_PORT
            );
            socket.send(request);

            long deadline = System.currentTimeMillis() + SOCKET_TIMEOUT_MS;
            while (System.currentTimeMillis() < deadline) {
                try {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                    socket.receive(response);

                    String body = new String(response.getData(), 0, response.getLength(), "UTF-8");
                    JSONObject json = new JSONObject(body);

                    String host = json.optString("host", response.getAddress().getHostAddress());
                    String name = json.optString("name", "clodock-control");
                    String profile = json.optString("profile", "generic");
                    foundTargets.put(host, new ControllerTarget(name, profile, host));
                } catch (Exception receiveError) {
                    Log.d(TAG, "discover receive timeout or parse failure: " + receiveError.getMessage());
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "discover failed: " + e.getMessage());
        }

        return new ArrayList<>(foundTargets.values());
    }
}
