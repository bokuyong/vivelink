package com.vibelink.sender.toss;

public class TossTransferCommand {

    private final boolean valid;
    private final String rawText;
    private final String recipientQuery;
    private final int amount;
    private final String reason;

    private TossTransferCommand(boolean valid, String rawText, String recipientQuery, int amount, String reason) {
        this.valid = valid;
        this.rawText = rawText;
        this.recipientQuery = recipientQuery;
        this.amount = amount;
        this.reason = reason;
    }

    public static TossTransferCommand valid(String rawText, String recipientQuery, int amount) {
        return new TossTransferCommand(true, rawText, recipientQuery, amount, "");
    }

    public static TossTransferCommand invalid(String rawText, String reason) {
        return new TossTransferCommand(false, rawText, "", 0, reason);
    }

    public boolean isValid() {
        return valid;
    }

    public String getRawText() {
        return rawText;
    }

    public String getRecipientQuery() {
        return recipientQuery;
    }

    public int getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }
}
