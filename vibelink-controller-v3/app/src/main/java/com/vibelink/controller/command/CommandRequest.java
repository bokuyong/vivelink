package com.vibelink.controller.command;

public class CommandRequest {

    private final String rawText;
    private final String source;
    private final long requestedAt;

    public CommandRequest(String rawText, String source, long requestedAt) {
        this.rawText = rawText;
        this.source = source;
        this.requestedAt = requestedAt;
    }

    public String getRawText() {
        return rawText;
    }

    public String getSource() {
        return source;
    }

    public long getRequestedAt() {
        return requestedAt;
    }
}
