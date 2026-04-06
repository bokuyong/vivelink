package com.vibelink.controller.command;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import com.vibelink.controller.service.ClodockAccessibilityService;

public class CommandExecutor {

    private final Context context;
    private final CommandParser parser = new CommandParser();
    private final AppLauncher appLauncher;

    public CommandExecutor(Context context) {
        this.context = context;
        this.appLauncher = new AppLauncher(context);
    }

    public CommandResult execute(String rawText) {
        ParsedCommand command = parser.parse(rawText);

        switch (command.getType()) {
            case OPEN_APP:
                return appLauncher.launch(command.getArgument());

            case OPEN_SETTINGS:
                Intent settingsIntent = new Intent(Settings.ACTION_SETTINGS);
                settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(settingsIntent);
                return CommandResult.success("OPEN_SETTINGS", "설정 화면 실행");

            case GO_HOME:
                return runAccessibilityAction("GO_HOME", ClodockAccessibilityService.performHome(), "홈으로 이동");

            case GO_BACK:
                return runAccessibilityAction("GO_BACK", ClodockAccessibilityService.performBack(), "뒤로 이동");

            case OPEN_RECENTS:
                return runAccessibilityAction("OPEN_RECENTS", ClodockAccessibilityService.performRecents(), "최근 앱 열기");

            case OPEN_NOTIFICATIONS:
                return runAccessibilityAction("OPEN_NOTIFICATIONS", ClodockAccessibilityService.performNotifications(), "알림창 열기");

            case OPEN_QUICK_SETTINGS:
                return runAccessibilityAction("OPEN_QUICK_SETTINGS", ClodockAccessibilityService.performQuickSettings(), "빠른 설정 열기");

            case CLICK_TEXT:
                return runAccessibilityAction(
                        "CLICK_TEXT",
                        ClodockAccessibilityService.clickText(command.getArgument()),
                        "\"" + command.getArgument() + "\" 눌렀습니다."
                );

            case INPUT_TEXT:
                return runAccessibilityAction(
                        "INPUT_TEXT",
                        ClodockAccessibilityService.inputText(command.getArgument()),
                        "\"" + command.getArgument() + "\" 입력"
                );

            case SCROLL_UP:
                return runAccessibilityAction("SCROLL_UP", ClodockAccessibilityService.scrollUp(), "위로 스크롤");

            case SCROLL_DOWN:
                return runAccessibilityAction("SCROLL_DOWN", ClodockAccessibilityService.scrollDown(), "아래로 스크롤");

            default:
                return CommandResult.unsupported("지원하지 않는 명령입니다. 앱 실행, 홈, 뒤로, 최근 앱, 알림, 설정, 텍스트 클릭/입력, 스크롤을 사용할 수 있습니다.");
        }
    }

    private CommandResult runAccessibilityAction(String commandType, boolean actionResult, String successMessage) {
        if (!ClodockAccessibilityService.isReady()) {
            return CommandResult.failure(commandType, "접근성 서비스가 꺼져 있어 실행할 수 없습니다.");
        }
        return actionResult
                ? CommandResult.success(commandType, successMessage)
                : CommandResult.failure(commandType, "화면에서 실행하지 못했습니다.");
    }
}
