package com.vibelink.sender;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.vibelink.sender.service.TossAccessibilityService;
import com.vibelink.sender.toss.TossCommandParser;
import com.vibelink.sender.toss.TossTransferCommand;
import com.vibelink.sender.toss.TossTransferManager;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_RECORD_AUDIO = 3001;

    private TextView tvAccessibilityStatus;
    private TextView tvRecognizedText;
    private TextView tvParsedCommand;
    private TextView tvStatus;
    private EditText etManualCommand;

    private SpeechRecognizer speechRecognizer;
    private final TossCommandParser commandParser = new TossCommandParser();
    private TossTransferManager transferManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus);
        tvRecognizedText = findViewById(R.id.tvRecognizedText);
        tvParsedCommand = findViewById(R.id.tvParsedCommand);
        tvStatus = findViewById(R.id.tvStatus);
        etManualCommand = findViewById(R.id.etManualCommand);

        transferManager = new TossTransferManager(getApplicationContext());
        initSpeechRecognizer();

        findViewById(R.id.btnOpenAccessibility).setOnClickListener(v ->
                startActivity(new android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        findViewById(R.id.btnMic).setOnClickListener(v -> startVoiceRecognition());
        findViewById(R.id.btnRunManual).setOnClickListener(v -> runCommand(etManualCommand.getText().toString().trim()));

        refreshAccessibilityStatus();
    }

    private void initSpeechRecognizer() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new SenderRecognitionListener());
    }

    private void startVoiceRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            return;
        }

        initSpeechRecognizer();
        tvStatus.setText("상태: 음성을 듣는 중");

        android.content.Intent intent = new android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREAN.toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        speechRecognizer.startListening(intent);
    }

    private void runCommand(String rawText) {
        if (rawText.isEmpty()) {
            Toast.makeText(this, "명령을 입력해 주세요", Toast.LENGTH_SHORT).show();
            return;
        }

        tvRecognizedText.setText("최근 명령: " + rawText);

        TossTransferCommand command = commandParser.parse(rawText);
        if (!command.isValid()) {
            tvParsedCommand.setText("해석 결과: 실패");
            tvStatus.setText("상태: " + command.getReason());
            return;
        }

        tvParsedCommand.setText("해석 결과: " + command.getRecipientQuery() + " / " + command.getAmount() + "원");
        refreshAccessibilityStatus();
        if (!TossAccessibilityService.isReady()) {
            tvStatus.setText("상태: 접근성 서비스를 먼저 켜 주세요");
            return;
        }

        transferManager.execute(command, new TossTransferManager.Listener() {
            @Override
            public void onProgress(String message) {
                runOnUiThread(() -> tvStatus.setText("상태: " + message));
            }

            @Override
            public void onFinished(boolean success, String message) {
                runOnUiThread(() -> {
                    tvStatus.setText("상태: " + message);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void refreshAccessibilityStatus() {
        if (TossAccessibilityService.isReady()) {
            tvAccessibilityStatus.setText("접근성 서비스: 켜짐");
            tvAccessibilityStatus.setTextColor(0xFF2E8B57);
        } else {
            tvAccessibilityStatus.setText("접근성 서비스: 꺼짐");
            tvAccessibilityStatus.setTextColor(0xFFB54747);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAccessibilityStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        if (transferManager != null) {
            transferManager.shutdown();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoiceRecognition();
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
                tvStatus.setText("상태: 인식된 문장이 없습니다");
                return;
            }
            runCommand(recognized);
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
