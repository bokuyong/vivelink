package com.vibelink.controller;

import android.graphics.Bitmap;
import android.graphics.Point;
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
import com.vibelink.controller.automation.GeminiAgent;
import com.vibelink.controller.automation.TapNotifier;
import com.vibelink.controller.automation.TossAutomation;
import com.vibelink.controller.network.StreamReceiver;
import com.vibelink.controller.vision.GeminiAnalyzer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "ControllerMain";

    private CommandServer commandServer;
    private WebDashboard webDashboard;

    private EditText etSenderIp;
    private EditText etDeviceIp;
    private Button btnConnect;
    private Button btnDisconnect;
    private Button btnRunToss;
    private Button btnCapture;
    private Button btnFind;
    private Button btnTapTest;
    private EditText etFindText;
    private Point lastFoundPoint;
    private final ExecutorService testExecutor = Executors.newSingleThreadExecutor();
    private ImageView ivScreen;
    private TextView tvLog;
    private ScrollView scrollLog;

    private StreamReceiver streamReceiver;
    private AdbController adbController;
    private GeminiAnalyzer geminiAnalyzer;
    private TossAutomation tossAutomation;
    private GeminiAgent geminiAgent;
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
        btnRunToss = findViewById(R.id.btnRunToss);
        ivScreen = findViewById(R.id.ivScreen);
        tvLog = findViewById(R.id.tvLog);
        scrollLog = findViewById(R.id.scrollLog);

        // Initialize Gemini once - reused for all automation runs
        geminiAnalyzer = new GeminiAnalyzer(AppConfig.GEMINI_API_KEY);
        appendLog("[Gemini] 초기화 완료");

        // Start command server for voice commands from sender app
        commandServer = new CommandServer();
        commandServer.setListener(new CommandServer.CommandListener() {
            @Override
            public void onTossCommand(String recipient, int amount) {
                mainHandler.post(() -> {
                    appendLog(">> 레거시 토스 명령: " + recipient + " / " + amount + "원");
                    runTossAutomationWith(recipient, amount);
                });
            }
            @Override
            public void onCommand(String rawText) {
                mainHandler.post(() -> runAiAgent(rawText));
            }
        });
        commandServer.start();
        appendLog("[CommandServer] 포트 " + CommandServer.PORT + " 대기 중");

        // Start web dashboard for browser-based control
        webDashboard = new WebDashboard();
        webDashboard.setCallback(new WebDashboard.DashboardCallback() {
            @Override public void onConnect(String senderIp, String deviceIp) {
                mainHandler.post(() -> {
                    etSenderIp.setText(senderIp);
                    etDeviceIp.setText(deviceIp);
                    connectAll();
                });
            }
            @Override public void onDisconnect() {
                mainHandler.post(() -> disconnectAll());
            }
            @Override public void onCommand(String rawText) {
                mainHandler.post(() -> runAiAgent(rawText));
            }
            @Override public AdbController getAdbController() { return adbController; }
            @Override public Bitmap getLatestFrame() { return latestFrame; }
            @Override public String getLogText() { return tvLog.getText().toString(); }
        });
        webDashboard.start();
        appendLog("[WebDashboard] http://<이 기기 IP>:" + WebDashboard.PORT);

        btnCapture = findViewById(R.id.btnCapture);
        btnFind = findViewById(R.id.btnFind);
        btnTapTest = findViewById(R.id.btnTapTest);
        etFindText = findViewById(R.id.etFindText);

        btnConnect.setOnClickListener(v -> connectAll());
        btnDisconnect.setOnClickListener(v -> disconnectAll());
        btnRunToss.setOnClickListener(v -> runTossAutomation());
        btnCapture.setOnClickListener(v -> testCapture());
        btnFind.setOnClickListener(v -> testFindElement());
        btnTapTest.setOnClickListener(v -> testTap());

        updateButtons(false);
    }

    // Connect stream receiver + ADB
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

        // Start screen stream
        streamReceiver = new StreamReceiver(senderIp, AppConfig.STREAM_PORT);
        streamReceiver.setListener(new StreamReceiver.FrameListener() {
            @Override
            public void onFrame(Bitmap frame) {
                // Keep latest frame for Gemini analysis
                latestFrame = frame;
                mainHandler.post(() -> ivScreen.setImageBitmap(frame));

                // Feed latest frame to running automation if active
                if (tossAutomation != null) tossAutomation.updateFrame(frame);
            }

            @Override
            public void onConnected() {
                mainHandler.post(() -> appendLog("[Stream] 연결됨 ✓"));
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

        // Connect ADB
        adbController = new AdbController(this, deviceIp);
        adbController.setCommandListener(new AdbController.AdbCommandListener() {
            @Override
            public void onConnected() {
                mainHandler.post(() -> {
                    appendLog("[ADB] 연결됨 ✓");
                    updateButtons(true);
                });
            }

            @Override
            public void onCommandResult(String command, String output) {
                mainHandler.post(() -> appendLog("[ADB] " + command));
            }

            @Override
            public void onError(String message) {
                mainHandler.post(() -> appendLog("[ADB] 오류: " + message));
            }
        });
        adbController.connect();

        // Enable buttons after 3s even if ADB handshake is still pending
        mainHandler.postDelayed(() -> updateButtons(true), 3000);
    }

    // Run Toss automation triggered by voice command from sender
    private void runTossAutomationWith(String recipient, int amount) {
        if (adbController == null || !adbController.isConnected()) {
            appendLog("[경고] ADB 미연결 - 연결 후 자동화 불가");
            Toast.makeText(this, "ADB 연결 필요 - 먼저 연결하세요", Toast.LENGTH_SHORT).show();
            return;
        }
        appendLog(">> 토스 자동화 시작: " + recipient + "에게 " + amount + "원");
        String senderIp = etSenderIp.getText().toString().trim();
        TapNotifier tapNotifier = new TapNotifier(senderIp);
        tossAutomation = new TossAutomation(adbController, geminiAnalyzer, tapNotifier);
        tossAutomation.setCommand(recipient, amount);
        if (latestFrame != null) tossAutomation.updateFrame(latestFrame);
        tossAutomation.setListener(new TossAutomation.AutomationListener() {
            @Override public void onStepStart(String step) { mainHandler.post(() -> appendLog("[▶] " + step)); }
            @Override public void onStepSuccess(String step) { mainHandler.post(() -> appendLog("[✓] " + step)); }
            @Override public void onStepFailed(String step, String reason) { mainHandler.post(() -> appendLog("[✗] " + step + " → " + reason)); }
            @Override public void onCompleted() {
                mainHandler.post(() -> {
                    appendLog("=== " + recipient + "에게 " + amount + "원 송금 완료! ===");
                    Toast.makeText(MainActivity.this, "송금 완료!", Toast.LENGTH_LONG).show();
                });
            }
        });
        tossAutomation.start();
    }

    // Run full Toss automation with Gemini vision assist
    private void runTossAutomation() {
        if (adbController == null || !adbController.isConnected()) {
            Toast.makeText(this, "ADB 연결이 필요합니다", Toast.LENGTH_SHORT).show();
            appendLog("[경고] ADB 미연결 상태 - 연결 후 재시도");
            return;
        }

        // Create automation with Gemini enabled
        String senderIp = etSenderIp.getText().toString().trim();
        TapNotifier tapNotifier = new TapNotifier(senderIp);
        tossAutomation = new TossAutomation(adbController, geminiAnalyzer, tapNotifier);
        if (latestFrame != null) tossAutomation.updateFrame(latestFrame);

        tossAutomation.setListener(new TossAutomation.AutomationListener() {
            @Override
            public void onStepStart(String step) {
                mainHandler.post(() -> appendLog("[▶] " + step));
            }

            @Override
            public void onStepSuccess(String step) {
                mainHandler.post(() -> appendLog("[✓] " + step));
            }

            @Override
            public void onStepFailed(String step, String reason) {
                mainHandler.post(() -> appendLog("[✗] " + step + " → " + reason));
            }

            @Override
            public void onCompleted() {
                mainHandler.post(() -> {
                    appendLog("=== 토스 10,000원 송금 완료! ===");
                    Toast.makeText(MainActivity.this, "송금 완료!", Toast.LENGTH_LONG).show();
                });
            }
        });

        tossAutomation.start();
        appendLog(">> 토스 자동화 시작 (Gemini 보조 활성화)");
    }

    // --- AI Agent ---

    private void runAiAgent(String command) {
        if (adbController == null || !adbController.isConnected()) {
            appendLog("[AI] ADB 미연결 - 연결 후 명령 가능");
            return;
        }
        appendLog(">> AI 에이전트 시작: \"" + command + "\"");

        if (geminiAgent != null) geminiAgent.cancel();
        geminiAgent = new GeminiAgent(adbController, geminiAnalyzer);
        geminiAgent.execute(command, new GeminiAgent.AgentListener() {
            @Override
            public void onStep(int step, String action, String detail) {
                mainHandler.post(() -> appendLog("[AI #" + step + "] " + action + " → " + detail));
            }
            @Override
            public void onCompleted(String summary) {
                mainHandler.post(() -> appendLog("=== AI 완료: " + summary + " ==="));
            }
            @Override
            public void onFailed(String reason) {
                mainHandler.post(() -> appendLog("[AI 실패] " + reason));
            }
        });
    }

    // --- 화면 테스트 메서드 ---

    private void testCapture() {
        if (adbController == null || !adbController.isConnected()) {
            appendLog("[테스트] ADB 미연결");
            return;
        }
        appendLog("[테스트] 화면 캡처 중...");
        testExecutor.execute(() -> {
            try {
                Bitmap bitmap = adbController.captureScreen().get();
                mainHandler.post(() -> {
                    if (bitmap != null) {
                        ivScreen.setImageBitmap(bitmap);
                        latestFrame = bitmap;
                        appendLog("[테스트] 캡처 성공: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                    } else {
                        appendLog("[테스트] 캡처 실패: null");
                    }
                });
            } catch (Exception ex) {
                mainHandler.post(() -> appendLog("[테스트] 캡처 실패: " + ex.getMessage()));
            }
        });
    }

    private void testFindElement() {
        if (adbController == null || !adbController.isConnected()) {
            appendLog("[테스트] ADB 미연결");
            return;
        }
        String text = etFindText.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "찾을 텍스트를 입력하세요", Toast.LENGTH_SHORT).show();
            return;
        }
        appendLog("[테스트] \"" + text + "\" 찾는 중...");
        testExecutor.execute(() -> {
            try {
                Point point = adbController.findElementByText(text).get();
                mainHandler.post(() -> {
                    if (point != null) {
                        lastFoundPoint = point;
                        appendLog("[테스트] 발견! (" + point.x + ", " + point.y + ") → '탭 실행' 가능");
                    } else {
                        lastFoundPoint = null;
                        appendLog("[테스트] \"" + text + "\" 못 찾음");
                    }
                });
            } catch (Exception ex) {
                mainHandler.post(() -> appendLog("[테스트] 찾기 실패: " + ex.getMessage()));
            }
        });
    }

    private void testTap() {
        if (adbController == null || !adbController.isConnected()) {
            appendLog("[테스트] ADB 미연결");
            return;
        }
        if (lastFoundPoint == null) {
            Toast.makeText(this, "먼저 요소를 찾아주세요", Toast.LENGTH_SHORT).show();
            return;
        }
        int x = lastFoundPoint.x, y = lastFoundPoint.y;
        appendLog("[테스트] 탭 실행: (" + x + ", " + y + ")");
        testExecutor.execute(() -> {
            try {
                adbController.tap(x, y).get();
                mainHandler.post(() -> appendLog("[테스트] 탭 완료!"));
            } catch (Exception ex) {
                mainHandler.post(() -> appendLog("[테스트] 탭 실패: " + ex.getMessage()));
            }
        });
    }

    private void disconnectAll() {
        if (streamReceiver != null) streamReceiver.disconnect();
        if (adbController != null) adbController.shutdown();
        if (tossAutomation != null) tossAutomation.shutdown();
        tossAutomation = null;
        updateButtons(false);
        appendLog(">> 연결 해제");
    }

    private void appendLog(String message) {
        tvLog.append(message + "\n");
        scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
        Log.d(TAG, message);
        if (webDashboard != null) webDashboard.addLog(message);
    }

    private void updateButtons(boolean connected) {
        btnConnect.setEnabled(!connected);
        btnDisconnect.setEnabled(connected);
        btnRunToss.setEnabled(connected);
        btnCapture.setEnabled(connected);
        btnFind.setEnabled(connected);
        btnTapTest.setEnabled(connected);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnectAll();
        if (geminiAnalyzer != null) geminiAnalyzer.shutdown();
        if (commandServer != null) commandServer.stop();
        if (webDashboard != null) webDashboard.stop();
    }
}
