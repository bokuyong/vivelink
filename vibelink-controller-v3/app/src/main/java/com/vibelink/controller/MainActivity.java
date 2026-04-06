package com.vibelink.controller;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.vibelink.controller.command.CommandExecutor;
import com.vibelink.controller.command.CommandRequest;
import com.vibelink.controller.command.CommandResult;
import com.vibelink.controller.discovery.DeviceDiscoveryResponder;
import com.vibelink.controller.service.ClodockAccessibilityService;

import org.json.JSONObject;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "ClodockControl";

    private CommandServer commandServer;
    private DeviceDiscoveryResponder discoveryResponder;
    private CommandExecutor commandExecutor;

    private TextView tvProfile;
    private TextView tvServerStatus;
    private TextView tvIpStatus;
    private TextView tvAccessibilityStatus;
    private TextView tvLastCommand;
    private TextView tvLastResult;
    private TextView tvLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvProfile = findViewById(R.id.tvProfile);
        tvServerStatus = findViewById(R.id.tvServerStatus);
        tvIpStatus = findViewById(R.id.tvIpStatus);
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus);
        tvLastCommand = findViewById(R.id.tvLastCommand);
        tvLastResult = findViewById(R.id.tvLastResult);
        tvLog = findViewById(R.id.tvLog);

        commandExecutor = new CommandExecutor(getApplicationContext());
        commandServer = new CommandServer();
        commandServer.setListener(this::handleCommand);
        commandServer.start();

        discoveryResponder = new DeviceDiscoveryResponder(this::buildDiscoveryPayload);
        discoveryResponder.start();

        findViewById(R.id.btnOpenAccessibility).setOnClickListener(v ->
                startActivity(new android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        findViewById(R.id.btnRefreshStatus).setOnClickListener(v -> refreshStatus());

        tvProfile.setText("프로필: " + BuildConfig.DEVICE_PROFILE);
        refreshStatus();
        appendLog("명령 서버가 포트 " + CommandServer.PORT + "에서 대기 중입니다.");
        appendLog("자동 탐색 응답기가 포트 " + AppConfig.DISCOVERY_PORT + "에서 대기 중입니다.");
    }

    private CommandResult handleCommand(CommandRequest request) {
        CommandResult result = commandExecutor.execute(request.getRawText());
        runOnUiThread(() -> {
            tvLastCommand.setText(request.getRawText());
            tvLastResult.setText(result.getMessage());
            appendLog("[" + request.getSource() + "] " + request.getRawText() + " -> " + result.getMessage());
        });
        return result;
    }

    private String buildDiscoveryPayload() {
        try {
            JSONObject json = new JSONObject();
            json.put("name", getString(R.string.app_name));
            json.put("profile", BuildConfig.DEVICE_PROFILE);
            json.put("host", getLocalIpAddress());
            json.put("commandPort", AppConfig.COMMAND_PORT);
            return json.toString();
        } catch (Exception e) {
            return "{\"name\":\"clodock-control\",\"profile\":\"unknown\"}";
        }
    }

    private void refreshStatus() {
        tvServerStatus.setText("명령 서버: 실행 중 / 포트 " + AppConfig.COMMAND_PORT);
        tvIpStatus.setText("현재 IP: " + getLocalIpAddress());
        if (ClodockAccessibilityService.isReady()) {
            tvAccessibilityStatus.setText("접근성 서비스: 켜짐");
            tvAccessibilityStatus.setTextColor(0xFF2E8B57);
        } else {
            tvAccessibilityStatus.setText("접근성 서비스: 꺼짐");
            tvAccessibilityStatus.setTextColor(0xFFB54747);
        }
    }

    private String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                Enumeration<java.net.InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && address instanceof Inet4Address) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "IP 확인 실패: " + e.getMessage());
        }
        return "unknown";
    }

    private void appendLog(String message) {
        tvLog.append(message + "\n");
        Log.d(TAG, message);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (commandServer != null) {
            commandServer.stop();
        }
        if (discoveryResponder != null) {
            discoveryResponder.stop();
        }
    }
}
