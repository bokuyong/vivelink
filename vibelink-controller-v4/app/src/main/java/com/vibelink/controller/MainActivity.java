package com.vibelink.controller;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.vibelink.controller.adb.AdbController;
import com.vibelink.controller.automation.AiAgent;
import com.vibelink.controller.automation.TapNotifier;
import com.vibelink.controller.network.StreamReceiver;
import com.vibelink.controller.vision.GeminiAnalyzer;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "ControllerMain";

    private CommandServer commandServer;

    private EditText etSenderIp;
    private EditText etDeviceIp;
    private Button btnConnect;
    private Button btnDisconnect;
    private Button btnCancel;
    private ImageView ivScreen;
    private TextView tvLog;
    private ScrollView scrollLog;

    private StreamReceiver streamReceiver;
    private AdbController adbController;
    private GeminiAnalyzer geminiAnalyzer;
    private AiAgent aiAgent;
    private volatile Bitmap latestFrame;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etSenderIp = findViewById(R.id.etSenderIp);
        etDeviceIp = findViewById(R.id.etDeviceIp);
        btnConnect = findViewById(R.id.btnConnect);
        btnDisconnect = findViewById(R.id.btnDisconnect);
        btnCancel = findViewById(R.id.btnRunToss);
        ivScreen = findViewById(R.id.ivScreen);
        tvLog = findViewById(R.id.tvLog);
        scrollLog = findViewById(R.id.scrollLog);

        btnCancel.setText("AI 중지");
        btnCancel.setEnabled(false);

        geminiAnalyzer = new GeminiAnalyzer(AppConfig.GEMINI_API_KEY);
        appendLog("[Gemini] 초기화 완료 (v4 범용 AI 에이전트)");

        commandServer = new CommandServer();
        commandServer.setListener(new CommandServer.CommandListener() {
            @Override
            public void onTossCommand(String recipient, int amount) {
                String goal = recipient + "에게 토스로 " + amount + "원 송금해줘";
                mainHandler.post(() -> {
                    appendLog(">> 직접 명령 → 목표 변환: " + goal);
                    executeGoal(goal);
                });
            }

            @Override
            public void onVoiceCommand(String voiceText) {
                mainHandler.post(() -> {
                    appendLog(">> 음성 명령 수신: \"" + voiceText + "\"");
                    executeGoal(voiceText);
                });
            }
        });
        commandServer.start();
        appendLog("[CommandServer] 포트 " + CommandServer.PORT + " 대기 중 (범용 AI 에이전트)");

        btnConnect.setOnClickListener(v -> connectAll());
        btnDisconnect.setOnClickListener(v -> disconnectAll());
        btnCancel.setOnClickListener(v -> cancelAgent());

        updateButtons(false);
    }

    private void connectAll() {
        String senderIp = etSenderIp.getText().toString().trim();
        String deviceIp = etDeviceIp.getText().toString().trim();

        if (senderIp.isEmpty() || deviceIp.isEmpty()) {
            Toast.makeText(this, "Sender IP와 Device IP를 모두 입력하세요", Toast.LENGTH_SHORT).show();
            return;
        }

        appendLog(">> 연결 시도 중...");
        appendLog("   Stream : " + senderIp + ":" + AppConfig.STREAM_PORT);
        appendLog("   ADB    : " + deviceIp + ":" + AppConfig.ADB_PORT);

        streamReceiver = new StreamReceiver(senderIp, AppConfig.STREAM_PORT);
        streamReceiver.setListener(new StreamReceiver.FrameListener() {
            @Override
            public void onFrame(Bitmap frame) {
                latestFrame = frame;
                mainHandler.post(() -> ivScreen.setImageBitmap(frame));
            }

            @Override
            public void onConnected() {
                mainHandler.post(() -> appendLog("[Stream] 연결됨"));
            }

            @Override
            public void onDisconnected() {
                mainHandler.post(() -> appendLog("[Stream] 끊어짐 - 재연결 대기..."));
            }

            @Override
            public void onError(String message) {
                mainHandler.post(() -> appendLog("[Stream] 오류: " + message));
            }
        });
        streamReceiver.connect();

        adbController = new AdbController(this, deviceIp);
        adbController.setCommandListener(new AdbController.AdbCommandListener() {
            @Override
            public void onConnected() {
                mainHandler.post(() -> {
                    appendLog("[ADB] 연결됨");
                    updateButtons(true);
                });
            }

            @Override
            public void onCommandResult(String command, String output) {
                // Suppress verbose ADB logging in v4
            }

            @Override
            public void onError(String message) {
                mainHandler.post(() -> appendLog("[ADB] 오류: " + message));
            }
        });
        adbController.connect();

        mainHandler.postDelayed(() -> updateButtons(true), 3000);
    }

    private void executeGoal(String goal) {
        if (adbController == null || !adbController.isConnected()) {
            appendLog("[경고] ADB 미연결 - 먼저 연결하세요");
            Toast.makeText(this, "ADB 연결 필요", Toast.LENGTH_SHORT).show();
            return;
        }

        appendLog("═══════════════════════════════════");
        appendLog("  AI 에이전트 시작");
        appendLog("  목표: " + goal);
        appendLog("═══════════════════════════════════");

        String senderIp = etSenderIp.getText().toString().trim();
        TapNotifier tapNotifier = new TapNotifier(senderIp);

        if (aiAgent != null) aiAgent.shutdown();
        aiAgent = new AiAgent(adbController, geminiAnalyzer, tapNotifier);

        aiAgent.setListener(new AiAgent.AgentListener() {
            @Override
            public void onStepStart(int step, String description) {
                mainHandler.post(() -> appendLog("[Step " + step + "] " + description));
            }

            @Override
            public void onStepComplete(int step, String result) {
                // Logged by onStepStart already
            }

            @Override
            public void onGoalCompleted(String summary) {
                mainHandler.post(() -> {
                    appendLog("═══════════════════════════════════");
                    appendLog("  목표 달성! " + summary);
                    appendLog("═══════════════════════════════════");
                    Toast.makeText(MainActivity.this, "목표 달성!", Toast.LENGTH_LONG).show();
                    btnCancel.setEnabled(false);
                });
            }

            @Override
            public void onGoalFailed(String reason) {
                mainHandler.post(() -> {
                    appendLog("[실패] " + reason);
                    Toast.makeText(MainActivity.this, "실패: " + reason, Toast.LENGTH_LONG).show();
                    btnCancel.setEnabled(false);
                });
            }
        });

        btnCancel.setEnabled(true);
        aiAgent.executeGoal(goal);
    }

    private void cancelAgent() {
        if (aiAgent != null) {
            aiAgent.cancel();
            appendLog(">> AI 에이전트 중지 요청");
        }
        btnCancel.setEnabled(false);
    }

    private void disconnectAll() {
        if (aiAgent != null) aiAgent.shutdown();
        if (streamReceiver != null) streamReceiver.disconnect();
        if (adbController != null) adbController.shutdown();
        aiAgent = null;
        updateButtons(false);
        appendLog(">> 연결 해제");
    }

    private void appendLog(String message) {
        tvLog.append(message + "\n");
        scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
        Log.d(TAG, message);
    }

    private void updateButtons(boolean connected) {
        btnConnect.setEnabled(!connected);
        btnDisconnect.setEnabled(connected);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnectAll();
        if (geminiAnalyzer != null) geminiAnalyzer.shutdown();
        if (commandServer != null) commandServer.stop();
    }
}
