package com.vibelink.controller.automation;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.Log;

import com.vibelink.controller.adb.AdbController;
import com.vibelink.controller.vision.GeminiAnalyzer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI Agent that uses Gemini vision to autonomously execute any user command.
 * Each step: capture screen → ask Gemini what to do → execute action → repeat.
 */
public class GeminiAgent {

    private static final String TAG = "GeminiAgent";
    private static final int MAX_STEPS = 20;
    private static final long STEP_DELAY_MS = 1500;

    public interface AgentListener {
        void onStep(int step, String action, String detail);
        void onCompleted(String summary);
        void onFailed(String reason);
    }

    private final AdbController adb;
    private final GeminiAnalyzer gemini;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean cancelled = false;

    public GeminiAgent(AdbController adb, GeminiAnalyzer gemini) {
        this.adb = adb;
        this.gemini = gemini;
    }

    public void execute(String userCommand, AgentListener listener) {
        cancelled = false;
        executor.execute(() -> {
            List<String> history = new ArrayList<>();
            try {
                for (int step = 1; step <= MAX_STEPS && !cancelled; step++) {
                    // 1. Capture screen
                    Bitmap screen = adb.captureScreen().get();
                    if (screen == null) {
                        listener.onFailed("화면 캡처 실패");
                        return;
                    }

                    // 2. Build history string
                    StringBuilder histStr = new StringBuilder();
                    for (String h : history) histStr.append("- ").append(h).append("\n");
                    if (history.isEmpty()) histStr.append("(아직 없음)\n");

                    // 3. Ask Gemini for next action
                    String response = gemini.planNextAction(screen, userCommand, histStr.toString());
                    if (response == null || response.isEmpty()) {
                        listener.onFailed("Gemini 응답 없음 (스텝 " + step + ")");
                        return;
                    }

                    // 4. Extract JSON from response (Gemini may wrap in markdown)
                    String json = extractJson(response);
                    if (json == null) {
                        listener.onFailed("JSON 파싱 실패: " + response);
                        return;
                    }

                    String action = extractString(json, "action");
                    if (action == null) {
                        listener.onFailed("action 필드 없음: " + json);
                        return;
                    }

                    // 5. Execute action
                    String detail = executeAction(action, json);
                    history.add(action + ": " + detail);
                    listener.onStep(step, action, detail);

                    // 6. Check if done
                    if ("done".equals(action)) {
                        String summary = extractString(json, "summary");
                        listener.onCompleted(summary != null ? summary : "완료");
                        return;
                    }

                    // 7. Wait for screen to update
                    Thread.sleep(STEP_DELAY_MS);
                }

                listener.onFailed("최대 스텝 수(" + MAX_STEPS + ") 초과");
            } catch (Exception e) {
                Log.e(TAG, "Agent error: " + e.getMessage());
                listener.onFailed("에러: " + e.getMessage());
            }
        });
    }

    private String executeAction(String action, String json) throws Exception {
        switch (action) {
            case "tap": {
                int x = extractInt(json, "x");
                int y = extractInt(json, "y");
                adb.tap(x, y).get();
                return "(" + x + ", " + y + ")";
            }
            case "tap_text": {
                String text = extractString(json, "text");
                Point pt = adb.findElementByText(text).get();
                if (pt != null) {
                    adb.tap(pt.x, pt.y).get();
                    return "\"" + text + "\" → (" + pt.x + ", " + pt.y + ")";
                } else {
                    return "\"" + text + "\" 못 찾음";
                }
            }
            case "type": {
                String text = extractString(json, "text");
                adb.setClipboardAndPaste(text).get();
                return "\"" + text + "\"";
            }
            case "swipe": {
                String dir = extractString(json, "direction");
                int cx = 540, cy = 1170;
                int dist = 800;
                switch (dir != null ? dir : "up") {
                    case "up":    adb.swipe(cx, cy + dist/2, cx, cy - dist/2, 300).get(); break;
                    case "down":  adb.swipe(cx, cy - dist/2, cx, cy + dist/2, 300).get(); break;
                    case "left":  adb.swipe(cx + dist/2, cy, cx - dist/2, cy, 300).get(); break;
                    case "right": adb.swipe(cx - dist/2, cy, cx + dist/2, cy, 300).get(); break;
                }
                return dir;
            }
            case "back":
                adb.pressKey(4).get();
                return "뒤로가기";
            case "home":
                adb.pressKey(3).get();
                return "홈";
            case "open_app": {
                String app = extractString(json, "app");
                // Try to find and tap the app on screen first
                Point pt = adb.findElementByText(app).get();
                if (pt != null) {
                    adb.tap(pt.x, pt.y).get();
                    return "\"" + app + "\" 탭으로 실행";
                }
                return "\"" + app + "\" 못 찾음 - 홈 화면에서 다시 시도 필요";
            }
            case "wait": {
                int sec = extractInt(json, "seconds");
                if (sec <= 0) sec = 2;
                Thread.sleep(sec * 1000L);
                return sec + "초 대기";
            }
            case "done":
                return extractString(json, "summary");
            default:
                return "알 수 없는 액션: " + action;
        }
    }

    /** Extract first JSON object from response (handles markdown code blocks) */
    private String extractJson(String text) {
        int start = text.indexOf('{');
        if (start < 0) return null;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') { depth--; if (depth == 0) return text.substring(start, i + 1); }
        }
        return null;
    }

    private String extractString(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private int extractInt(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+)");
        Matcher m = p.matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    public void cancel() {
        cancelled = true;
    }

    public void shutdown() {
        cancelled = true;
        executor.shutdownNow();
    }
}
