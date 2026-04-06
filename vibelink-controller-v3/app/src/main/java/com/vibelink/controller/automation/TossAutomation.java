package com.vibelink.controller.automation;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.Log;

import com.vibelink.controller.adb.AdbController;
import com.vibelink.controller.vision.GeminiAnalyzer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// Toss automation - UIAutomator-driven coordinate resolution
// Each tap: uiautomator dump XML -> find element by text -> extract bounds -> tap center
// Gemini used only for screen state detection (no coordinate tasks)
public class TossAutomation {

    private static final String TAG = "TossAutomation";

    private static final int DELAY_APP_LAUNCH   = 4000;
    private static final int DELAY_AFTER_TAP    = 2500;  // increased for screen transitions
    private static final int DELAY_AFTER_SEARCH = 2500;
    private static final int DELAY_KEYPAD       = 400;
    private static final int DELAY_CONFIRM      = 3000;

    private static final int UI_TIMEOUT_SEC     = 15;
    private static final int MAX_RETRY          = 3;

    private final AdbController adb;
    private final GeminiAnalyzer gemini;
    private final TapNotifier tapNotifier;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private AutomationListener listener;

    // Set externally before start()
    private String recipientName = "아내";
    private int transferAmount = 10000;

    public interface AutomationListener {
        void onStepStart(String step);
        void onStepSuccess(String step);
        void onStepFailed(String step, String reason);
        void onCompleted();
    }

    public TossAutomation(AdbController adb, GeminiAnalyzer gemini) {
        this(adb, gemini, null);
    }

    public TossAutomation(AdbController adb, GeminiAnalyzer gemini, TapNotifier tapNotifier) {
        this.adb = adb;
        this.gemini = gemini;
        this.tapNotifier = tapNotifier;
    }

    public void setListener(AutomationListener l) {
        this.listener = l;
    }

    // Set recipient and amount from voice command before calling start()
    public void setCommand(String recipient, int amount) {
        this.recipientName = recipient;
        this.transferAmount = amount;
    }

    public void updateFrame(Bitmap frame) {}

    public void start() {
        executor.submit(this::runSequence);
    }

    private void runSequence() {
        try {
            // Press HOME to dismiss voice overlay / any stuck UI before launching Toss
            step("화면 정리 (홈)");
            adb.pressKey(3).get(3, TimeUnit.SECONDS);
            sleep(800);
            success("화면 정리 완료");

            // Step 0: Launch Toss - verify via "송금" (the button we need next)
            step("토스 앱 실행");
            Point sendBtn = adb.findElementByText("송금").get(5, TimeUnit.SECONDS);
            if (sendBtn != null) {
                success("토스 이미 실행 중");
            } else {
                boolean launched = launchTossFromHome();
                if (!launched) {
                    throw new RuntimeException("토스 앱 실행 실패");
                }
                for (int waitRetry = 0; waitRetry < 4; waitRetry++) {
                    sleep(DELAY_APP_LAUNCH);
                    sendBtn = adb.findElementByText("송금").get(5, TimeUnit.SECONDS);
                    if (sendBtn != null) break;
                    Log.w(TAG, "Toss home not ready, wait retry " + (waitRetry + 1));
                }
                if (sendBtn == null) {
                    throw new RuntimeException("토스 홈 로딩 실패 - '송금' 버튼 미발견");
                }
                success("토스 앱 실행");
            }

            // Step 1: Tap first "송금" button in account list (UIAutomator)
            step("송금 버튼");
            tapByUi("송금");
            sleep(DELAY_AFTER_TAP);
            success("송금 버튼");

            // Step 2: Account tab first - try to tap recipient directly (no clipboard, no search)
            step(recipientName + " 선택");
            Point accountRecipient = adb.findElementByDesc(recipientName).get(UI_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (accountRecipient != null) {
                Log.i(TAG, recipientName + " found in account tab, tapping directly");
                notifyTap(accountRecipient.x, accountRecipient.y);
                adb.tap(accountRecipient.x, accountRecipient.y).get(5, TimeUnit.SECONDS);
                sleep(DELAY_AFTER_TAP);
                success(recipientName + " 선택 (계좌 탭)");
            } else {
                // Step 2b: Not in account tab - switch to contact tab and search
                step("연락처 탭");
                tapByUi("연락처");
                sleep(DELAY_AFTER_TAP);
                success("연락처 탭");

                step("연락처 검색");
                Point searchPt = adb.findElementByResId("contact_search").get(UI_TIMEOUT_SEC, TimeUnit.SECONDS);
                if (searchPt == null) searchPt = new Point(540, 690);
                notifyTap(searchPt.x, searchPt.y);
                adb.tap(searchPt.x, searchPt.y).get(5, TimeUnit.SECONDS);
                sleep(800);
                adb.setClipboardAndPaste(recipientName).get(5, TimeUnit.SECONDS);
                sleep(DELAY_AFTER_SEARCH);
                adb.pressKey(111).get(3, TimeUnit.SECONDS);
                sleep(600);
                success("연락처 검색: " + recipientName);

                step(recipientName + " 선택");
                tapByDesc(recipientName);
                sleep(DELAY_AFTER_TAP);
                success(recipientName + " 선택 (연락처)");
            }

            // Step 4b: Tap confirmation popup - tds_bottom_cta_v1_cta (확인 button)
            // Fallback to fixed coord (773,2042) if resId not found after retries
            step("확인 팝업 처리");
            boolean confirmTapped = tapByResIdWithFallback("tds_bottom_cta_v1_cta", 773, 2042);
            if (!confirmTapped) {
                throw new RuntimeException("확인 팝업 버튼 탭 실패");
            }
            sleep(3000);
            success("확인 팝업 처리");

            // Step 5: Enter amount digit by digit via numpad resource-ids
            step("금액 입력: " + transferAmount + "원");
            enterAmount(transferAmount);
            success("금액 입력");

            // Step 6: Tap "다음" button (confirm_cta resource-id)
            step("다음 버튼");
            tapByResId("confirm_cta");
            sleep(DELAY_CONFIRM);
            success("다음");

            // Step 7: Tap final "보내기" button (tds_bottom_cta_v1_cta resource-id)
            step("최종 보내기");
            tapByResId("tds_bottom_cta_v1_cta");
            sleep(DELAY_CONFIRM);
            success("최종 보내기");

            Log.i(TAG, "Toss automation completed");
            if (listener != null) listener.onCompleted();

        } catch (Exception e) {
            Log.e(TAG, "runSequence failed: " + e.getMessage());
            if (listener != null) listener.onStepFailed("자동화 오류", e.getMessage());
        }
    }

    // Launch Toss from home: Gemini finds icon and tap, fallback to monkey
    private boolean launchTossFromHome() {
        try {
            Bitmap frame = adb.captureScreen().get(10, TimeUnit.SECONDS);
            if (frame != null) {
                Point pt = gemini.findTossAppOnHome(frame).get(15, TimeUnit.SECONDS);
                if (pt != null) {
                    Log.i(TAG, "Gemini tapped Toss icon at (" + pt.x + "," + pt.y + ")");
                    notifyTap(pt.x, pt.y);
                    adb.tap(pt.x, pt.y).get(5, TimeUnit.SECONDS);
                    return true;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Gemini tap Toss failed: " + e.getMessage());
        }
        Log.i(TAG, "Fallback to monkey launch");
        try {
            adb.launchToss().get(5, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "monkey launch failed: " + e.getMessage());
            return false;
        }
    }

    // Tap numpad digits for the given amount one by one
    private void enterAmount(int amount) throws Exception {
        String digits = String.valueOf(amount);
        for (char c : digits.toCharArray()) {
            if (c == '0') {
                tapByResId("numpad_0");
            } else {
                tapByResId("numpad_" + c);
            }
            sleep(DELAY_KEYPAD);
        }
    }

    // Find element by content-desc via UIAutomator dump, tap - throws if not found
    private void tapByDesc(String desc) throws Exception {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                Point pt = adb.findElementByDesc(desc).get(UI_TIMEOUT_SEC, TimeUnit.SECONDS);
                if (pt != null) {
                    Log.i(TAG, "UIAutomator(desc) tap '" + desc + "' -> (" + pt.x + "," + pt.y + ")");
                    notifyTap(pt.x, pt.y);
                    adb.tap(pt.x, pt.y).get(5, TimeUnit.SECONDS);
                    return;
                }
                Log.w(TAG, "UIAutomator(desc): '" + desc + "' not found (attempt " + attempt + ")");
                sleep(1500);
            } catch (Exception e) {
                Log.e(TAG, "tapByDesc error attempt " + attempt + ": " + e.getMessage());
                sleep(1500);
            }
        }
        throw new RuntimeException("UIAutomator(desc) failed to find: " + desc);
    }

    // Find element by resource-id suffix via UIAutomator dump, tap - throws if not found
    private void tapByResId(String resIdSuffix) throws Exception {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                Point pt = adb.findElementByResId(resIdSuffix).get(UI_TIMEOUT_SEC, TimeUnit.SECONDS);
                if (pt != null) {
                    Log.i(TAG, "UIAutomator(resId) tap '" + resIdSuffix + "' -> (" + pt.x + "," + pt.y + ")");
                    notifyTap(pt.x, pt.y);
                    adb.tap(pt.x, pt.y).get(5, TimeUnit.SECONDS);
                    return;
                }
                Log.w(TAG, "UIAutomator(resId): '" + resIdSuffix + "' not found (attempt " + attempt + ")");
                sleep(1000);
            } catch (Exception e) {
                Log.e(TAG, "tapByResId error attempt " + attempt + ": " + e.getMessage());
                sleep(1000);
            }
        }
        throw new RuntimeException("UIAutomator(resId) failed to find: " + resIdSuffix);
    }

    // Find element by resource-id, tap - on all retries fail use fixed fallback coord
    private boolean tapByResIdWithFallback(String resIdSuffix, int fallbackX, int fallbackY) {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                Point pt = adb.findElementByResId(resIdSuffix).get(UI_TIMEOUT_SEC, TimeUnit.SECONDS);
                if (pt != null) {
                    Log.i(TAG, "UIAutomator(resId) tap '" + resIdSuffix + "' -> (" + pt.x + "," + pt.y + ")");
                    notifyTap(pt.x, pt.y);
                    adb.tap(pt.x, pt.y).get(5, TimeUnit.SECONDS);
                    return true;
                }
                Log.w(TAG, "UIAutomator(resId): '" + resIdSuffix + "' not found (attempt " + attempt + ")");
                sleep(1000);
            } catch (Exception e) {
                Log.e(TAG, "tapByResIdWithFallback error attempt " + attempt + ": " + e.getMessage());
                sleep(1000);
            }
        }
        // All UIAutomator retries failed - use fixed fallback coordinate
        Log.w(TAG, "UIAutomator failed for '" + resIdSuffix + "', using fallback (" + fallbackX + "," + fallbackY + ")");
        try {
            notifyTap(fallbackX, fallbackY);
            adb.tap(fallbackX, fallbackY).get(5, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "fallback tap also failed: " + e.getMessage());
            return false;
        }
    }

    // Find element by text via UIAutomator dump, tap - throws if not found after retries
    private void tapByUi(String text) throws Exception {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                Point pt = adb.findElementByText(text).get(UI_TIMEOUT_SEC, TimeUnit.SECONDS);
                if (pt != null) {
                    Log.i(TAG, "UIAutomator tap '" + text + "' -> (" + pt.x + "," + pt.y + ")");
                    notifyTap(pt.x, pt.y);
                    adb.tap(pt.x, pt.y).get(5, TimeUnit.SECONDS);
                    if (listener != null)
                        listener.onStepStart("[UI] " + text + " -> (" + pt.x + "," + pt.y + ")");
                    return;
                }
                Log.w(TAG, "UIAutomator: '" + text + "' not found (attempt " + attempt + ")");
                sleep(1500);
            } catch (Exception e) {
                Log.e(TAG, "tapByUi error attempt " + attempt + ": " + e.getMessage());
                sleep(1500);
            }
        }
        throw new RuntimeException("UIAutomator failed to find: " + text);
    }

    // Same as tapByUi but does not throw on failure (for optional elements)
    private void tapByUiOptional(String text) {
        try {
            Point pt = adb.findElementByText(text).get(UI_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (pt != null) {
                Log.i(TAG, "UIAutomator(optional) tap '" + text + "' -> (" + pt.x + "," + pt.y + ")");
                notifyTap(pt.x, pt.y);
                adb.tap(pt.x, pt.y).get(5, TimeUnit.SECONDS);
            } else {
                Log.d(TAG, "UIAutomator(optional): '" + text + "' not present, skipping");
            }
        } catch (Exception e) {
            Log.w(TAG, "tapByUiOptional '" + text + "' skipped: " + e.getMessage());
        }
    }

    private void notifyTap(int x, int y) {
        if (tapNotifier != null && tapNotifier.isAvailable()) {
            tapNotifier.notifyTap(x, y);
        }
    }

    private void step(String name) {
        Log.i(TAG, "STEP: " + name);
        if (listener != null) listener.onStepStart(name);
    }

    private void success(String name) {
        if (listener != null) listener.onStepSuccess(name);
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
