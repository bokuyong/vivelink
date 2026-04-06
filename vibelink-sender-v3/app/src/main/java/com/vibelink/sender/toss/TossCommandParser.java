package com.vibelink.sender.toss;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TossCommandParser {

    private static final int MAX_AMOUNT = 100000;
    private static final Pattern TRANSFER_PATTERN = Pattern.compile(
            "^(?:토스(?:로|에서)?\\s*)?(.+?)(?:한테|에게|께)\\s*(.+?)\\s*(?:송금(?:해줘|해|할래|해봐|해주라)?|보내(?:줘|줘요|주라|라|)|입금(?:해줘|해|해주라)?)$"
    );

    private final KoreanAmountParser amountParser = new KoreanAmountParser();

    public TossTransferCommand parse(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return TossTransferCommand.invalid(rawText, "명령이 비어 있습니다.");
        }

        String trimmed = rawText.trim();
        Matcher matcher = TRANSFER_PATTERN.matcher(trimmed);
        if (!matcher.find()) {
            return TossTransferCommand.invalid(trimmed, "토스 송금 명령 형식이 아닙니다.");
        }

        String recipient = cleanupRecipient(matcher.group(1));
        if (recipient.isEmpty()) {
            return TossTransferCommand.invalid(trimmed, "받는 사람 이름을 찾지 못했습니다.");
        }

        int amount = amountParser.parse(matcher.group(2));
        if (amount <= 0) {
            return TossTransferCommand.invalid(trimmed, "금액을 이해하지 못했습니다.");
        }
        if (amount > MAX_AMOUNT) {
            return TossTransferCommand.invalid(trimmed, "1회 최대 송금액은 10만원입니다.");
        }

        return TossTransferCommand.valid(trimmed, recipient, amount);
    }

    private String cleanupRecipient(String recipient) {
        return recipient == null ? "" : recipient
                .replace("토스로", "")
                .replace("토스에서", "")
                .replace("토스", "")
                .trim();
    }
}
