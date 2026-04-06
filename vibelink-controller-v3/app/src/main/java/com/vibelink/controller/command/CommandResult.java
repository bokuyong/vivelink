package com.vibelink.controller.command;

public class CommandResult {

    private final boolean supported;
    private final boolean executed;
    private final String commandType;
    private final String message;

    private CommandResult(boolean supported, boolean executed, String commandType, String message) {
        this.supported = supported;
        this.executed = executed;
        this.commandType = commandType;
        this.message = message;
    }

    public static CommandResult success(String commandType, String message) {
        return new CommandResult(true, true, commandType, message);
    }

    public static CommandResult failure(String commandType, String message) {
        return new CommandResult(true, false, commandType, message);
    }

    public static CommandResult unsupported(String message) {
        return new CommandResult(false, false, "UNSUPPORTED", message);
    }

    public boolean isSupported() {
        return supported;
    }

    public boolean isExecuted() {
        return executed;
    }

    public String getCommandType() {
        return commandType;
    }

    public String getMessage() {
        return message;
    }
}
