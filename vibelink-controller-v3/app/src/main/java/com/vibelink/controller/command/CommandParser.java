package com.vibelink.controller.command;

import java.util.Locale;

public class CommandParser {

    public ParsedCommand parse(String rawText) {
        String normalized = normalize(rawText);
        if (normalized.isEmpty()) {
            return new ParsedCommand(ParsedCommand.Type.UNSUPPORTED, "", rawText);
        }

        if (containsAny(normalized, "홈으로가", "홈으로", "바탕화면으로가", "홈가")) {
            return new ParsedCommand(ParsedCommand.Type.GO_HOME, "", rawText);
        }

        if (containsAny(normalized, "뒤로가", "뒤로", "이전화면")) {
            return new ParsedCommand(ParsedCommand.Type.GO_BACK, "", rawText);
        }

        if (containsAny(normalized, "최근앱", "앱전환", "멀티태스킹")) {
            return new ParsedCommand(ParsedCommand.Type.OPEN_RECENTS, "", rawText);
        }

        if (containsAny(normalized, "알림창", "알림열어", "알림내려")) {
            return new ParsedCommand(ParsedCommand.Type.OPEN_NOTIFICATIONS, "", rawText);
        }

        if (containsAny(normalized, "빠른설정", "퀵설정")) {
            return new ParsedCommand(ParsedCommand.Type.OPEN_QUICK_SETTINGS, "", rawText);
        }

        if (containsAny(normalized, "설정열어", "설정켜", "설정실행")) {
            return new ParsedCommand(ParsedCommand.Type.OPEN_SETTINGS, "", rawText);
        }

        if (containsAny(normalized, "아래로스크롤", "아래스크롤", "스크롤내려")) {
            return new ParsedCommand(ParsedCommand.Type.SCROLL_DOWN, "", rawText);
        }

        if (containsAny(normalized, "위로스크롤", "위스크롤", "스크롤올려")) {
            return new ParsedCommand(ParsedCommand.Type.SCROLL_UP, "", rawText);
        }

        if (normalized.endsWith("눌러") || normalized.endsWith("클릭해") || normalized.endsWith("탭해") || normalized.endsWith("선택해")) {
            String argument = rawText
                    .replace("눌러", "")
                    .replace("클릭해", "")
                    .replace("탭해", "")
                    .replace("선택해", "")
                    .trim();
            return new ParsedCommand(ParsedCommand.Type.CLICK_TEXT, argument, rawText);
        }

        if (normalized.endsWith("입력해") || normalized.endsWith("써줘")) {
            String argument = rawText
                    .replace("입력해", "")
                    .replace("써줘", "")
                    .trim();
            return new ParsedCommand(ParsedCommand.Type.INPUT_TEXT, argument, rawText);
        }

        if (normalized.endsWith("열어줘") || normalized.endsWith("실행해") || normalized.endsWith("켜줘") || normalized.endsWith("열어") || normalized.endsWith("실행")) {
            String argument = rawText
                    .replace("앱", "")
                    .replace("열어줘", "")
                    .replace("실행해", "")
                    .replace("켜줘", "")
                    .replace("열어", "")
                    .replace("실행", "")
                    .trim();
            return new ParsedCommand(ParsedCommand.Type.OPEN_APP, argument, rawText);
        }

        return new ParsedCommand(ParsedCommand.Type.OPEN_APP, rawText.trim(), rawText);
    }

    private String normalize(String value) {
        return value == null ? "" : value
                .toLowerCase(Locale.KOREAN)
                .replace(" ", "")
                .replace("?", "")
                .replace(".", "");
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
