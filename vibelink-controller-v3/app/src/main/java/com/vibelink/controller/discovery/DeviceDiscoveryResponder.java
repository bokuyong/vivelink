package com.vibelink.controller.discovery;

import com.vibelink.controller.AppConfig;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class DeviceDiscoveryResponder {

    public interface PayloadProvider {
        String getPayload();
    }

    private static final String DISCOVERY_MESSAGE = "CLODOCK_DISCOVER_V5";
    private static final int BUFFER_SIZE = 1024;

    private final PayloadProvider payloadProvider;
    private volatile boolean running;
    private Thread responderThread;
    private DatagramSocket socket;

    public DeviceDiscoveryResponder(PayloadProvider payloadProvider) {
        this.payloadProvider = payloadProvider;
    }

    public void start() {
        if (responderThread != null) {
            return;
        }

        running = true;
        responderThread = new Thread(() -> {
            try (DatagramSocket datagramSocket = new DatagramSocket(AppConfig.DISCOVERY_PORT)) {
                socket = datagramSocket;
                while (running) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    datagramSocket.receive(packet);

                    String body = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
                    if (!DISCOVERY_MESSAGE.equals(body)) {
                        continue;
                    }

                    byte[] responseBytes = payloadProvider.getPayload().getBytes("UTF-8");
                    DatagramPacket response = new DatagramPacket(
                            responseBytes,
                            responseBytes.length,
                            packet.getAddress(),
                            packet.getPort()
                    );
                    datagramSocket.send(response);
                }
            } catch (Exception ignored) {
            } finally {
                socket = null;
            }
        }, "clodock-discovery");
        responderThread.start();
    }

    public void stop() {
        running = false;
        if (socket != null) {
            socket.close();
            socket = null;
        }
        responderThread = null;
    }
}
