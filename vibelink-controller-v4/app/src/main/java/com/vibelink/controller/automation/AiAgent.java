package com.vibelink.controller.automation;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.Log;

import com.vibelink.controller.adb.AdbController;
import com.vibelink.controller.vision.GeminiAnalyzer;
import com.vibelink.controller.vision.GeminiAnalyzer.AgentDecision;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * General-purpose AI agent that controls any Android app via natural language goals.
 * Operates in a capture → analyze → act → verify loop powered by Gemini vision.
 * Can navigate any app, not just Toss - a fully automated Bixby/Siri equivalent.
 */
public class AiAgent {

    private static final String TAG = "AiAgent";

    private static final int MAX_STEPS = 30;
    private static final int STEP_DELAY_MS = 1500;
    private static final int GEMINI_TIMEOUT_SEC = 30;
    private static final int CAPTURE_TIMEOUT_SEC = 10;
    private static final int SWIPE_DURATION_MS = 300;
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    private final AdbController adb;
    private final GeminiAnalyzer gemini;
    private final TapNotifier tapNotifier;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<String> actionHistory = new ArrayList<>();

    private AgentListener listener;
    private volatile boolean cancelled = false;

    public interface AgentListener {
        void onStepStart(int step, String description);
        void onStepComplete(int step, String result);
        void onGoalCompleted(String summary);
        void onGoalFailed(String reason);
    }

    public AiAgent(AdbController adb, GeminiAnalyzer gemini, TapNotifier tapNotifier) {
        this.adb = adb;
        this.gemini = gemini;
        this.tapNotifier = tapNotifier;
    }

    public void setListener(AgentListener listener) {
        this.listener = listener;
    }

    public void executeGoal(String goal) {
        cancelled = false;
        actionHistory.clear();
        executor.submit(() -> runAgentLoop(goal));
    }

    public void cancel() {
        cancelled = true;
    }

    private void runAgentLoop(String goal) {
        int consecutiveFailures = 0;

        try {
            notifyStep(0, "홈 화면으로 이동");
            adb.pressKey(3).get(3, TimeUnit.SECONDS);
            sleep(1000);

            for (int step = 1; step <= MAX_STEPS; step++) {
                if (cancelled) {
                    notifyFailed("사용자가 취소함");
                    return;
                }

                // 1. Capture current screen
                Bitmap screen = adb.captureScreen().get(CAPTURE_TIMEOUT_SEC, TimeUnit.SECONDS);
                if (screen == null) {
                    Log.w(TAG, "Screen capture failed at step " + step);
                    consecutiveFailures++;
                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        notifyFailed("화면 캡처 연속 실패");
                        return;
                    }
                    sleep(2000);
                    continue;
                }

                // 2. Ask Gemini to decide next action
                String historyStr = buildHistoryString();
                AgentDecision decision = gemini.decideAgentAction(
                        screen, goal, historyStr, step, MAX_STEPS)
                        .get(GEMINI_TIMEOUT_SEC, TimeUnit.SECONDS);

                if (decision == null) {
                    Log.w(TAG, "Gemini returned null at step " + step);
                    consecutiveFailures++;
                    actionHistory.add("Step " + step + ": AI 응답 없음");
                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        notifyFailed("AI 의사결정 연속 실패");
                        return;
                    }
                    sleep(2000);
                    continue;
                }

                consecutiveFailures = 0;
                Log.i(TAG, "Step " + step + ": " + decision.action + " - " + decision.reason);
                notifyStep(step, "[" + decision.action + "] " + decision.reason);

                // 3. Execute the decided action
                boolean success = executeAction(decision);

                // 4. Record in history
                actionHistory.add("Step " + step + ": " + decision.action +
                        " (" + (decision.reason != null ? decision.reason : "") + ")" +
                        (success ? " → 성공" : " → 실패"));

                if (listener != null) {
                    listener.onStepComplete(step, decision.action + ": " + decision.reason);
                }

                // 5. Check completion
                if ("DONE".equals(decision.action)) {
                    notifyCompleted(decision.reason);
                    return;
                }

                // 6. Adaptive delay based on action type
                sleep(getDelayForAction(decision.action));
            }

            notifyFailed("최대 " + MAX_STEPS + "단계 초과 - 목표 미달성");

        } catch (Exception e) {
            Log.e(TAG, "Agent loop error: " + e.getMessage());
            notifyFailed("에이전트 오류: " + e.getMessage());
        }
    }

    private boolean executeAction(AgentDecision decision) {
        try {
            switch (decision.action) {
                case "TAP":
                    if (decision.x <= 0 && decision.y <= 0) return false;
                    notifyTap(decision.x, decision.y);
                    adb.tap(decision.x, decision.y).get(5, TimeUnit.SECONDS);
                    return true;

                case "LONG_TAP":
                    if (decision.x <= 0 && decision.y <= 0) return false;
                    notifyTap(decision.x, decision.y);
                    adb.swipe(decision.x, decision.y, decision.x, decision.y, 1500)
                            .get(5, TimeUnit.SECONDS);
                    return true;

                case "TYPE":
                    if (decision.text == null || decision.text.isEmpty()) return false;
                    adb.setClipboardAndPaste(decision.text).get(5, TimeUnit.SECONDS);
                    return true;

                case "SWIPE_UP":
                    adb.swipe(540, 1600, 540, 600, SWIPE_DURATION_MS)
                            .get(5, TimeUnit.SECONDS);
                    return true;

                case "SWIPE_DOWN":
                    adb.swipe(540, 600, 540, 1600, SWIPE_DURATION_MS)
                            .get(5, TimeUnit.SECONDS);
                    return true;

                case "SWIPE_LEFT":
                    adb.swipe(900, 1000, 100, 1000, SWIPE_DURATION_MS)
                            .get(5, TimeUnit.SECONDS);
                    return true;

                case "SWIPE_RIGHT":
                    adb.swipe(100, 1000, 900, 1000, SWIPE_DURATION_MS)
                            .get(5, TimeUnit.SECONDS);
                    return true;

                case "PRESS_BACK":
                    adb.pressKey(4).get(3, TimeUnit.SECONDS);
                    return true;

                case "PRESS_HOME":
                    adb.pressKey(3).get(3, TimeUnit.SECONDS);
                    return true;

                case "PRESS_ENTER":
                    adb.pressKey(66).get(3, TimeUnit.SECONDS);
                    return true;

                case "PRESS_RECENT":
                    adb.pressKey(187).get(3, TimeUnit.SECONDS);
                    return true;

                case "LAUNCH_APP":
                    return launchApp(decision.appPackage, decision.text);

                case "WAIT":
                    sleep(decision.waitMs > 0 ? decision.waitMs : 3000);
                    return true;

                case "DONE":
                    return true;

                default:
                    Log.w(TAG, "Unknown action: " + decision.action);
                    return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "executeAction [" + decision.action + "] failed: " + e.getMessage());
            return false;
        }
    }

    private boolean launchApp(String packageName, String appName) {
        // Strategy 1: monkey launch with package name (most reliable)
        if (packageName != null && !packageName.isEmpty()) {
            try {
                adb.executeShell("monkey -p " + packageName +
                        " -c android.intent.category.LAUNCHER 1").get(5, TimeUnit.SECONDS);
                sleep(3000);
                return true;
            } catch (Exception e) {
                Log.w(TAG, "monkey launch [" + packageName + "]: " + e.getMessage());
            }
        }

        // Strategy 2: Find app icon on home screen via UIAutomator
        if (appName != null && !appName.isEmpty()) {
            try {
                adb.pressKey(3).get(3, TimeUnit.SECONDS);
                sleep(1000);

                Point pt = adb.findElementByText(appName).get(10, TimeUnit.SECONDS);
                if (pt != null) {
                    notifyTap(pt.x, pt.y);
                    adb.tap(pt.x, pt.y).get(5, TimeUnit.SECONDS);
                    sleep(2000);
                    return true;
                }
            } catch (Exception e) {
                Log.w(TAG, "UIAutomator app launch: " + e.getMessage());
            }

            // Strategy 3: Gemini vision to find app icon
            try {
                Bitmap screen = adb.captureScreen().get(CAPTURE_TIMEOUT_SEC, TimeUnit.SECONDS);
                if (screen != null) {
                    Point gpt = gemini.findElementOnScreen(screen,
                                    "홈 화면에서 '" + appName + "' 앱 아이콘 또는 라벨")
                            .get(GEMINI_TIMEOUT_SEC, TimeUnit.SECONDS);
                    if (gpt != null) {
                        notifyTap(gpt.x, gpt.y);
                        adb.tap(gpt.x, gpt.y).get(5, TimeUnit.SECONDS);
                        sleep(2000);
                        return true;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Gemini app launch: " + e.getMessage());
            }
        }

        return false;
    }

    private int getDelayForAction(String action) {
        switch (action) {
            case "LAUNCH_APP": return 3000;
            case "TAP": return STEP_DELAY_MS;
            case "TYPE": return 1000;
            case "PRESS_BACK":
            case "PRESS_HOME": return 1500;
            case "SWIPE_UP":
            case "SWIPE_DOWN":
            case "SWIPE_LEFT":
            case "SWIPE_RIGHT": return 1200;
            default: return STEP_DELAY_MS;
        }
    }

    private String buildHistoryString() {
        if (actionHistory.isEmpty()) return "없음 (첫 번째 행동)";
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, actionHistory.size() - 10);
        for (int i = start; i < actionHistory.size(); i++) {
            sb.append(actionHistory.get(i)).append("\n");
        }
        return sb.toString();
    }

    private void notifyTap(int x, int y) {
        if (tapNotifier != null && tapNotifier.isAvailable()) {
            tapNotifier.notifyTap(x, y);
        }
    }

    private void notifyStep(int step, String description) {
        if (listener != null) listener.onStepStart(step, description);
    }

    private void notifyCompleted(String summary) {
        if (listener != null) listener.onGoalCompleted(summary);
    }

    private void notifyFailed(String reason) {
        if (listener != null) listener.onGoalFailed(reason);
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public void shutdown() {
        cancelled = true;
        executor.shutdownNow();
    }
}
