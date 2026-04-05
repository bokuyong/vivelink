package com.vibelink.sender;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// AI tab - general voice command → send to controller AI agent for any app automation
public class AiFragment extends Fragment {

    private static final String TAG = "AiFragment";
    private static final int COMMAND_PORT = 7789;

    private static final String PREFS_NAME = "ai_prefs";
    private static final String KEY_CONTROLLER_IP = "controller_ip";

    // Gemini gradient colors for mic animation
    private static final int COLOR_BLUE   = 0xFF4A90FF;
    private static final int COLOR_PURPLE = 0xFF8B5CF6;
    private static final int COLOR_TEAL   = 0xFF06B6D4;

    private ImageButton btnMic;
    private TextView tvSttResult;
    private TextView tvInputHint;

    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Mic animation animators
    private ValueAnimator micColorAnimator;
    private ObjectAnimator micScaleXAnimator;
    private ObjectAnimator micScaleYAnimator;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_ai, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        btnMic = view.findViewById(R.id.btnMic);
        tvSttResult = view.findViewById(R.id.tvSttResult);
        tvInputHint = view.findViewById(R.id.tvInputHint);

        initRecognizer();
        btnMic.setOnClickListener(v -> onMicPressed());
    }

    // Create or recreate SpeechRecognizer
    private void initRecognizer() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext());
        speechRecognizer.setRecognitionListener(new VoiceListener());
    }

    private void onMicPressed() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 201);
            showResult("마이크 권한을 허용한 후 다시 눌러주세요");
            return;
        }

        if (isListening) {
            stopListening();
        } else {
            startListening();
        }
    }

    private void startListening() {
        // Always recreate recognizer to avoid ERROR_RECOGNIZER_BUSY
        initRecognizer();

        isListening = true;
        startMicAnimation();
        tvInputHint.setText("듣는 중... (무엇이든 말씀하세요)");
        tvSttResult.setVisibility(View.VISIBLE);
        tvSttResult.setText("🎙 무엇이든 말씀하세요...\n예: \"카카오톡에서 엄마한테 메시지 보내줘\"");

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREAN);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        speechRecognizer.startListening(intent);
    }

    private void stopListening() {
        isListening = false;
        stopMicAnimation();
        tvInputHint.setText("무엇이든 말해보세요 (모든 앱 제어 가능)");
        speechRecognizer.stopListening();
    }

    // Start Gemini-style gradient pulse animation on mic button
    private void startMicAnimation() {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(COLOR_BLUE);
        btnMic.setBackground(bg);

        // Color cycling: blue → purple → teal → blue
        micColorAnimator = ValueAnimator.ofArgb(COLOR_BLUE, COLOR_PURPLE, COLOR_TEAL, COLOR_BLUE);
        micColorAnimator.setDuration(2000);
        micColorAnimator.setRepeatCount(ValueAnimator.INFINITE);
        micColorAnimator.setRepeatMode(ValueAnimator.RESTART);
        micColorAnimator.setInterpolator(new LinearInterpolator());
        micColorAnimator.addUpdateListener(animator -> {
            int color = (int) animator.getAnimatedValue();
            bg.setColor(color);
            btnMic.setBackground(bg);
        });

        // Scale pulse: 1.0 → 1.18 → 1.0 repeating
        micScaleXAnimator = ObjectAnimator.ofFloat(btnMic, "scaleX", 1f, 1.18f, 1f);
        micScaleXAnimator.setDuration(900);
        micScaleXAnimator.setRepeatCount(ValueAnimator.INFINITE);
        micScaleXAnimator.setRepeatMode(ValueAnimator.RESTART);
        micScaleXAnimator.setInterpolator(new AccelerateDecelerateInterpolator());

        micScaleYAnimator = ObjectAnimator.ofFloat(btnMic, "scaleY", 1f, 1.18f, 1f);
        micScaleYAnimator.setDuration(900);
        micScaleYAnimator.setRepeatCount(ValueAnimator.INFINITE);
        micScaleYAnimator.setRepeatMode(ValueAnimator.RESTART);
        micScaleYAnimator.setInterpolator(new AccelerateDecelerateInterpolator());

        micColorAnimator.start();
        micScaleXAnimator.start();
        micScaleYAnimator.start();
    }

    // Stop animation and restore idle blue circle
    private void stopMicAnimation() {
        if (micColorAnimator != null) {
            micColorAnimator.cancel();
            micColorAnimator = null;
        }
        if (micScaleXAnimator != null) {
            micScaleXAnimator.cancel();
            micScaleXAnimator = null;
        }
        if (micScaleYAnimator != null) {
            micScaleYAnimator.cancel();
            micScaleYAnimator = null;
        }
        btnMic.setScaleX(1f);
        btnMic.setScaleY(1f);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(COLOR_BLUE);
        btnMic.setBackground(bg);
    }

    // Send voice command to controller AI agent for any app automation
    private void sendVoiceCommand(String recognizedText) {
        String ip = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_CONTROLLER_IP, "").trim();
        if (ip.isEmpty()) {
            showResult("⚠ 컨트롤러 IP를 입력하세요");
            return;
        }

        showResult("✅ 인식: \"" + recognizedText + "\"\n🤖 AI 에이전트에 전송 중...");
        startTapOverlayService();

        executor.execute(() -> {
            try {
                String escaped = recognizedText.replace("\\", "\\\\")
                        .replace("\"", "\\\"");
                String json = "{\"voice_text\":\"" + escaped + "\"}";
                URL url = new URL("http://" + ip + ":" + COMMAND_PORT + "/command");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                OutputStream os = conn.getOutputStream();
                os.write(json.getBytes("UTF-8"));
                os.flush();

                int code = conn.getResponseCode();
                conn.disconnect();

                mainHandler.post(() -> {
                    if (code == 200) {
                        showResult("✅ AI 에이전트 실행 중!\n\"" + recognizedText + "\"\n자동으로 앱을 찾아 실행합니다...");
                        requireActivity().moveTaskToBack(true);
                    } else {
                        showResult("⚠ 컨트롤러 오류 HTTP " + code);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "HTTP POST failed: " + e.getMessage());
                mainHandler.post(() -> showResult("❌ 전송 실패: " + e.getMessage() +
                        "\n컨트롤러 IP와 네트워크를 확인하세요"));
            }
        });
    }

    private void startTapOverlayService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !android.provider.Settings.canDrawOverlays(requireContext())) {
            try {
                Intent i = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                i.setData(android.net.Uri.parse("package:" + requireContext().getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                showResult("⚠ 탭 표시를 위해 '다른 앱 위에 그리기' 권한을 켜주세요");
            } catch (Exception e) {
                Log.e(TAG, "Overlay settings: " + e.getMessage());
            }
            return;
        }
        Intent svc = new Intent(requireContext(), com.vibelink.sender.service.TapOverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(svc);
        } else {
            requireContext().startService(svc);
        }
    }

    private void showResult(String msg) {
        tvSttResult.setVisibility(View.VISIBLE);
        tvSttResult.setText(msg);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopMicAnimation();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        executor.shutdownNow();
    }

    private class VoiceListener implements RecognitionListener {

        @Override public void onReadyForSpeech(Bundle params) {}
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}

        @Override
        public void onEndOfSpeech() {
            isListening = false;
            mainHandler.post(() -> {
                stopMicAnimation();
                tvInputHint.setText("마이크 버튼을 눌러 명령하세요...");
            });
        }

        @Override
        public void onError(int error) {
            isListening = false;
            mainHandler.post(() -> {
                stopMicAnimation();
                tvInputHint.setText("마이크 버튼을 눌러 명령하세요...");

                String msg;
                switch (error) {
                    case SpeechRecognizer.ERROR_AUDIO:          msg = "마이크 오류(3) - 권한 확인"; break;
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: msg = "너무 오래 기다림(6) - 버튼 후 바로 말하세요"; break;
                    case SpeechRecognizer.ERROR_NO_MATCH:       msg = "인식 불가(7) - 다시 시도"; break;
                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:msg = "인식기 바쁨(8) - 잠시 후 재시도"; break;
                    case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: msg = "마이크 권한 없음(9)"; break;
                    default: msg = "오류 코드: " + error + " - 다시 시도";
                }
                showResult("❌ " + msg);
            });
        }

        @Override
        public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            String text = (matches != null && !matches.isEmpty()) ? matches.get(0) : "(음성 인식됨)";
            Log.d(TAG, "STT result: " + text);

            mainHandler.post(() -> sendVoiceCommand(text));
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            ArrayList<String> partial = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (partial != null && !partial.isEmpty()) {
                mainHandler.post(() -> showResult("🎙 " + partial.get(0) + "..."));
            }
        }

        @Override public void onEvent(int eventType, Bundle params) {}
    }
}
