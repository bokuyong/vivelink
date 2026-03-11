package com.vibelink.controller.automation;

import android.util.Log;

import java.io.OutputStream;
import java.net.Socket;

// Sends tap coordinates to Sender TapOverlayService (port 7790)
public class TapNotifier {

    private static final String TAG = "TapNotifier";
    private static final int TAP_PORT = 7790;
    private static final int CONNECT_TIMEOUT_MS = 500;

    private final String senderIp;

    public TapNotifier(String senderIp) {
        this.senderIp = senderIp != null ? senderIp.trim() : "";
    }

    public boolean isAvailable() {
        return !senderIp.isEmpty();
    }

    // Fire-and-forget: send "x,y\n" to Sender; ignores failures
    public void notifyTap(int x, int y) {
        if (!isAvailable()) return;
        new Thread(() -> {
            try (Socket s = new Socket()) {
                s.connect(new java.net.InetSocketAddress(senderIp, TAP_PORT), CONNECT_TIMEOUT_MS);
                OutputStream out = s.getOutputStream();
                out.write((x + "," + y + "\n").getBytes("UTF-8"));
                out.flush();
            } catch (Exception e) {
                Log.v(TAG, "tap notify failed: " + e.getMessage());
            }
        }).start();
    }
}
