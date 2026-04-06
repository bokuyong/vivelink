package com.vibelink.sender;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_RECORD_AUDIO = 3001;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();

    private Spinner spinnerTargets;
    private TextView tvSelectedTarget;
    private TextView tvRecognizedText;
    private TextView tvStatus;
    private EditText etManualHost;
    private EditText etManualCommand;

    private SpeechRecognizer speechRecognizer;
    private DeviceDiscoveryClient discoveryClient;
    private final List<ControllerTarget> discoveredTargets = new ArrayList<>();
    private ArrayAdapter<String> targetAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        spinnerTargets = findViewById(R.id.spinnerTargets);
        tvSelectedTarget = findViewById(R.id.tvSelectedTarget);
        tvRecognizedText = findViewById(R.id.tvRecognizedText);
        tvStatus = findViewById(R.id.tvStatus);
        etManualHost = findViewById(R.id.etManualHost);
        etManualCommand = findViewById(R.id.etManualCommand);
        Button btnScan = findViewById(R.id.btnScan);
        Button btnMic = findViewById(R.id.btnMic);
        Button btnSendManual = findViewById(R.id.btnSendManual);

        targetAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
        targetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTargets.setAdapter(targetAdapter);
        spinnerTargets.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> updateSelectedTargetText()));

        discoveryClient = new DeviceDiscoveryClient();
        initSpeechRecognizer();

        btnScan.setOnClickListener(v -> discoverTargets());
        btnMic.setOnClickListener(v -> toggleListening());
        btnSendManual.setOnClickListener(v -> {
            String rawCommand = etManualCommand.getText().toString().trim();
            if (rawCommand.isEmpty()) {
                Toast.makeText(this, "보낼 명령을 입력하세요", Toast.LENGTH_SHORT).show();
                return;
            }
            sendCommand(rawCommand);
        });

        discoverTargets();
    }

    private void initSpeechRecognizer() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new SenderRecognitionListener());
    }

    private void toggleListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            return;
        }

        initSpeechRecognizer();
        tvStatus.setText("상태: 음성 인식 대기 중");

        android.content.Intent intent = new android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREAN.toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        speechRecognizer.startListening(intent);
    }

    private void discoverTargets() {
        tvStatus.setText("상태: 컨트롤 기기 탐색 중");
        networkExecutor.execute(() -> {
            List<ControllerTarget> targets = discoveryClient.discover();
            mainHandler.post(() -> {
                discoveredTargets.clear();
                discoveredTargets.addAll(targets);
                targetAdapter.clear();
                for (ControllerTarget target : targets) {
                    targetAdapter.add(target.getDisplayLabel());
                }
                targetAdapter.notifyDataSetChanged();

                if (targets.isEmpty()) {
                    tvStatus.setText("상태: 자동 탐색 실패, 수동 IP를 사용할 수 있습니다.");
                    tvSelectedTarget.setText("선택된 기기가 없습니다.");
                } else {
                    spinnerTargets.setSelection(0);
                    tvStatus.setText("상태: " + targets.size() + "개 기기 발견");
                    updateSelectedTargetText();
                }
            });
        });
    }

    private void updateSelectedTargetText() {
        ControllerTarget target = getSelectedTarget();
        if (target == null) {
            tvSelectedTarget.setText("선택된 기기가 없습니다.");
            return;
        }
        tvSelectedTarget.setText("선택됨: " + target.getDisplayLabel() + " / " + target.getHost());
    }

    private ControllerTarget getSelectedTarget() {
        int position = spinnerTargets.getSelectedItemPosition();
        if (position >= 0 && position < discoveredTargets.size()) {
            return discoveredTargets.get(position);
        }
        return null;
    }

    private String resolveHost() {
        String manualHost = etManualHost.getText().toString().trim();
        if (!manualHost.isEmpty()) {
            return manualHost;
        }

        ControllerTarget target = getSelectedTarget();
        return target != null ? target.getHost() : "";
    }

    private void sendCommand(String rawCommand) {
        String host = resolveHost();
        if (TextUtils.isEmpty(host)) {
            Toast.makeText(this, "기기를 먼저 선택하거나 수동 IP를 입력하세요", Toast.LENGTH_SHORT).show();
            return;
        }

        tvRecognizedText.setText("최근 명령: " + rawCommand);
        tvStatus.setText("상태: 명령 전송 중");

        networkExecutor.execute(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("rawText", rawCommand);
                json.put("source", "clodock-sender-v5");
                json.put("requestedAt", System.currentTimeMillis());

                URL url = new URL("http://" + host + ":" + DeviceDiscoveryClient.COMMAND_PORT + "/command");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                try (OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write(json.toString().getBytes("UTF-8"));
                }

                int code = connection.getResponseCode();
                StringBuilder responseBuilder = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        code >= 400 ? connection.getErrorStream() : connection.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseBuilder.append(line);
                    }
                }
                connection.disconnect();

                String response = responseBuilder.length() == 0 ? "응답 없음" : responseBuilder.toString();
                String message = response;
                try {
                    JSONObject responseJson = new JSONObject(response);
                    message = responseJson.optString("message", response);
                } catch (Exception ignored) {}
                String finalMessage = message;
                mainHandler.post(() -> tvStatus.setText("상태: " + finalMessage));
            } catch (Exception e) {
                mainHandler.post(() -> tvStatus.setText("상태: 전송 실패 - " + e.getMessage()));
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        networkExecutor.shutdownNow();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            toggleListening();
        }
    }

    private class SenderRecognitionListener implements RecognitionListener {
        @Override public void onReadyForSpeech(Bundle params) {}
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() {}

        @Override
        public void onError(int error) {
            tvStatus.setText("상태: 음성 인식 오류 - 코드 " + error);
        }

        @Override
        public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            String recognized = (matches != null && !matches.isEmpty()) ? matches.get(0) : "";
            if (recognized.isEmpty()) {
                tvStatus.setText("상태: 인식된 문장이 없습니다.");
                return;
            }
            sendCommand(recognized);
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                tvStatus.setText("상태: 듣는 중 - " + matches.get(0));
            }
        }

        @Override public void onEvent(int eventType, Bundle params) {}
    }
}
