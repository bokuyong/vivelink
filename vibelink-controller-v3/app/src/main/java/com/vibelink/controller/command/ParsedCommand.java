package com.vibelink.controller.command;

public class ParsedCommand {

    public enum Type {
        OPEN_APP,
        OPEN_SETTINGS,
        GO_HOME,
        GO_BACK,
        OPEN_RECENTS,
        OPEN_NOTIFICATIONS,
        OPEN_QUICK_SETTINGS,
        CLICK_TEXT,
        INPUT_TEXT,
        SCROLL_UP,
        SCROLL_DOWN,
        UNSUPPORTED
    }

    private final Type type;
    private final String argument;
    private final String rawText;

    public ParsedCommand(Type type, String argument, String rawText) {
        this.type = type;
        this.argument = argument;
        this.rawText = rawText;
    }

    public Type getType() {
        return type;
    }

    public String getArgument() {
        return argument;
    }

    public String getRawText() {
        return rawText;
    }
}
