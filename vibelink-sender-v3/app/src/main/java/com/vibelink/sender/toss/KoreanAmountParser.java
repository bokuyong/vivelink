package com.vibelink.sender.toss;

import java.util.LinkedHashMap;
import java.util.Map;

public class KoreanAmountParser {

    private static final Map<String, String> WORD_REPLACEMENTS = new LinkedHashMap<>();

    static {
        WORD_REPLACEMENTS.put("스물", "이십");
        WORD_REPLACEMENTS.put("스무", "이십");
        WORD_REPLACEMENTS.put("서른", "삼십");
        WORD_REPLACEMENTS.put("마흔", "사십");
        WORD_REPLACEMENTS.put("쉰", "오십");
        WORD_REPLACEMENTS.put("예순", "육십");
        WORD_REPLACEMENTS.put("일흔", "칠십");
        WORD_REPLACEMENTS.put("여든", "팔십");
        WORD_REPLACEMENTS.put("아흔", "구십");
        WORD_REPLACEMENTS.put("열", "십");
        WORD_REPLACEMENTS.put("하나", "일");
        WORD_REPLACEMENTS.put("한", "일");
        WORD_REPLACEMENTS.put("둘", "이");
        WORD_REPLACEMENTS.put("두", "이");
        WORD_REPLACEMENTS.put("셋", "삼");
        WORD_REPLACEMENTS.put("세", "삼");
        WORD_REPLACEMENTS.put("넷", "사");
        WORD_REPLACEMENTS.put("네", "사");
        WORD_REPLACEMENTS.put("다섯", "오");
        WORD_REPLACEMENTS.put("여섯", "육");
        WORD_REPLACEMENTS.put("일곱", "칠");
        WORD_REPLACEMENTS.put("여덟", "팔");
        WORD_REPLACEMENTS.put("아홉", "구");
    }

    public int parse(String rawAmountText) {
        if (rawAmountText == null) {
            return -1;
        }

        String normalized = rawAmountText
                .replace("원", "")
                .replace("정도", "")
                .replace("쯤", "")
                .replace("만큼", "")
                .replace(",", "")
                .replace(" ", "");

        for (Map.Entry<String, String> entry : WORD_REPLACEMENTS.entrySet()) {
            normalized = normalized.replace(entry.getKey(), entry.getValue());
        }

        if (normalized.isEmpty()) {
            return -1;
        }

        int total = 0;
        int section = 0;
        int current = 0;

        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (Character.isDigit(ch)) {
                int end = i + 1;
                while (end < normalized.length() && Character.isDigit(normalized.charAt(end))) {
                    end++;
                }
                current = Integer.parseInt(normalized.substring(i, end));
                i = end - 1;
                continue;
            }

            int number = mapDigit(ch);
            if (number >= 0) {
                current = number;
                continue;
            }

            int smallUnit = mapSmallUnit(ch);
            if (smallUnit > 0) {
                section += (current == 0 ? 1 : current) * smallUnit;
                current = 0;
                continue;
            }

            int largeUnit = mapLargeUnit(ch);
            if (largeUnit > 0) {
                int base = section + current;
                if (base == 0) {
                    base = 1;
                }
                total += base * largeUnit;
                section = 0;
                current = 0;
            }
        }

        return total + section + current;
    }

    private int mapDigit(char ch) {
        switch (ch) {
            case '영':
            case '공':
                return 0;
            case '일':
                return 1;
            case '이':
                return 2;
            case '삼':
                return 3;
            case '사':
                return 4;
            case '오':
                return 5;
            case '육':
                return 6;
            case '칠':
                return 7;
            case '팔':
                return 8;
            case '구':
                return 9;
            default:
                return -1;
        }
    }

    private int mapSmallUnit(char ch) {
        switch (ch) {
            case '십':
                return 10;
            case '백':
                return 100;
            case '천':
                return 1000;
            default:
                return -1;
        }
    }

    private int mapLargeUnit(char ch) {
        switch (ch) {
            case '만':
                return 10000;
            case '억':
                return 100000000;
            default:
                return -1;
        }
    }
}
