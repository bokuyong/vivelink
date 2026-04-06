package com.vibelink.sender.toss;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import com.vibelink.sender.service.TossAccessibilityService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

public class TossTransferManager {

    private static final String TOSS_PACKAGE = "viva.republica.toss";

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface Listener {
        void onProgress(String message);
        void onFinished(boolean success, String message);
    }

    public TossTransferManager(Context context) {
        this.context = context;
    }

    public void execute(TossTransferCommand command, Listener listener) {
        executor.submit(() -> runSequence(command, listener));
    }

    private void runSequence(TossTransferCommand command, Listener listener) {
        try {
            if (!TossAccessibilityService.isReady()) {
                listener.onFinished(false, "접근성 서비스가 꺼져 있습니다.");
                return;
            }

            listener.onProgress("홈 화면으로 이동");
            TossAccessibilityService.goHome();
            sleep(700);

            listener.onProgress("토스 실행");
            if (!launchToss()) {
                listener.onFinished(false, "토스 앱을 실행하지 못했습니다.");
                return;
            }

            if (!waitFor(() -> TossAccessibilityService.hasText("송금"), 10000)) {
                listener.onFinished(false, "토스 홈 화면의 송금 버튼을 찾지 못했습니다.");
                return;
            }

            listener.onProgress("송금 화면 진입");
            if (!TossAccessibilityService.clickTopMostMatchingNode("송금", true, true)) {
                listener.onFinished(false, "송금 버튼을 누르지 못했습니다.");
                return;
            }
            sleep(1800);

            if (!waitFor(() -> TossAccessibilityService.hasText("연락처"), 8000)) {
                listener.onFinished(false, "받는 사람 선택 화면으로 넘어가지 못했습니다.");
                return;
            }

            listener.onProgress("연락처 탭 선택");
            TossAccessibilityService.clickTopMostMatchingNode("연락처", true, true);
            sleep(1000);

            listener.onProgress(command.getRecipientQuery() + " 검색");
            if (!TossAccessibilityService.focusFirstEditable()) {
                listener.onFinished(false, "연락처 검색 입력창을 찾지 못했습니다.");
                return;
            }
            sleep(300);
            if (!TossAccessibilityService.setTextOnFirstEditable(command.getRecipientQuery())) {
                listener.onFinished(false, "연락처 검색어를 입력하지 못했습니다.");
                return;
            }
            sleep(1500);

            listener.onProgress("검색 결과 최상단 선택");
            if (!TossAccessibilityService.clickTopMostMatchingNode(command.getRecipientQuery(), false, true)) {
                listener.onFinished(false, "연락처 검색 결과에서 받는 사람을 찾지 못했습니다.");
                return;
            }
            sleep(1800);

            listener.onProgress("금액 입력");
            if (!waitFor(() -> TossAccessibilityService.hasAnyText("얼마나 보낼까요?", "다음"), 8000)) {
                listener.onFinished(false, "금액 입력 화면을 찾지 못했습니다.");
                return;
            }
            if (!TossAccessibilityService.inputAmount(command.getAmount())) {
                listener.onFinished(false, "금액 입력에 실패했습니다.");
                return;
            }
            sleep(500);

            listener.onProgress("다음 단계 이동");
            if (!TossAccessibilityService.clickTopMostMatchingNode("다음", true, true)) {
                listener.onFinished(false, "다음 버튼을 누르지 못했습니다.");
                return;
            }
            sleep(2000);

            listener.onProgress("최종 보내기");
            if (!waitFor(() -> TossAccessibilityService.hasAnyText("보내기", "보낼까요?"), 8000)) {
                listener.onFinished(false, "최종 확인 화면을 찾지 못했습니다.");
                return;
            }
            if (!TossAccessibilityService.clickTopMostMatchingNode("보내기", true, true)) {
                listener.onFinished(false, "보내기 버튼을 누르지 못했습니다.");
                return;
            }

            listener.onFinished(true, "보내기까지 완료했습니다. 이제 지문 인증만 진행하세요.");
        } catch (Exception e) {
            listener.onFinished(false, "송금 자동화 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    private boolean launchToss() {
        PackageManager packageManager = context.getPackageManager();
        Intent launchIntent = packageManager.getLaunchIntentForPackage(TOSS_PACKAGE);
        if (launchIntent == null) {
            return false;
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(launchIntent);
        return true;
    }

    private boolean waitFor(BooleanSupplier condition, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            sleep(250);
        }
        return false;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
