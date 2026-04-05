package com.vibelink.controller.vision;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Gemini 2.0 Flash vision API wrapper for v4 general-purpose AI agent.
 * Provides: agent action decisions, element finding, screen analysis.
 */
public class GeminiAnalyzer {

    private static final String TAG = "GeminiAnalyzer";

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=";

    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final int JPEG_QUALITY = 90;

    private final String apiKey;
    private final OkHttpClient http;
    private final Gson gson = new Gson();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // ── Data classes ──

    public static class AgentDecision {
        public String action;
        public int x, y;
        public String text;
        public String appPackage;
        public int waitMs;
        public String reason;
    }

    public static class ParsedCommand {
        public final String recipient;
        public final int amount;
        public ParsedCommand(String recipient, int amount) {
            this.recipient = recipient;
            this.amount = amount;
        }
    }

    public GeminiAnalyzer(String apiKey) {
        this.apiKey = apiKey;
        this.http = new OkHttpClient.Builder()
                .callTimeout(35, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(35, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    // ══════════════════════════════════════════════
    //  v4 AGENT: General-purpose action decision
    // ══════════════════════════════════════════════

    public Future<AgentDecision> decideAgentAction(Bitmap screen, String goal,
                                                    String actionHistory, int currentStep, int maxSteps) {
        return executor.submit(() -> {
            int w = screen.getWidth();
            int h = screen.getHeight();

            String prompt =
                "너는 안드로이드 폰을 자동으로 제어하는 AI 에이전트다.\n\n" +
                "━━━ 목표 ━━━\n" + goal + "\n\n" +
                "━━━ 현재 화면 ━━━\n" +
                "해상도: " + w + "x" + h + " 픽셀\n" +
                "스크린샷이 첨부되어 있다.\n\n" +
                "━━━ 이전 행동 ━━━\n" + actionHistory + "\n\n" +
                "━━━ 진행 현황 ━━━\n" +
                currentStep + "/" + maxSteps + " 단계\n\n" +
                "━━━ 사용 가능한 액션 ━━━\n" +
                "TAP        - 화면 특정 위치 터치 (x, y 좌표 필수)\n" +
                "LONG_TAP   - 길게 누르기 (x, y 좌표 필수)\n" +
                "TYPE       - 현재 포커스된 입력창에 텍스트 입력 (text 필수)\n" +
                "SWIPE_UP   - 위로 스크롤 (리스트/페이지 아래 내용 보기)\n" +
                "SWIPE_DOWN - 아래로 스크롤\n" +
                "SWIPE_LEFT - 왼쪽 스와이프\n" +
                "SWIPE_RIGHT- 오른쪽 스와이프\n" +
                "PRESS_BACK - 뒤로 가기\n" +
                "PRESS_HOME - 홈 버튼\n" +
                "PRESS_ENTER- 엔터/검색/확인 키\n" +
                "PRESS_RECENT - 최근 앱 버튼\n" +
                "LAUNCH_APP - 앱 실행 (app_package 또는 text에 앱이름)\n" +
                "WAIT       - 로딩 대기 (ms 단위)\n" +
                "DONE       - 목표 완료됨\n\n" +
                "━━━ 주요 앱 패키지명 ━━━\n" +
                "토스: viva.republica.toss\n" +
                "카카오톡: com.kakao.talk\n" +
                "네이버: com.nhn.android.search\n" +
                "네이버지도: com.nhn.android.nmap\n" +
                "유튜브: com.google.android.youtube\n" +
                "카카오맵: net.daum.android.map\n" +
                "배달의민족: com.baemin\n" +
                "쿠팡: com.coupang.mobile\n" +
                "쿠팡이츠: com.coupang.eats\n" +
                "당근마켓: com.towneers.www\n" +
                "인스타그램: com.instagram.android\n" +
                "페이스북: com.facebook.katana\n" +
                "트위터/X: com.twitter.android\n" +
                "틱톡: com.zhiliaoapp.musically\n" +
                "설정: com.android.settings\n" +
                "전화: com.samsung.android.dialer\n" +
                "메시지: com.samsung.android.messaging\n" +
                "카메라: com.sec.android.app.camera\n" +
                "갤러리: com.sec.android.gallery3d\n" +
                "크롬: com.android.chrome\n" +
                "지도: com.google.android.apps.maps\n" +
                "Gmail: com.google.android.gm\n" +
                "캘린더: com.google.android.calendar\n\n" +
                "━━━ 규칙 ━━━\n" +
                "1. 상태바(y < 100) 영역은 절대 터치 금지\n" +
                "2. 한 번에 정확히 하나의 액션만 결정\n" +
                "3. 좌표는 반드시 0~" + w + "(x), 0~" + h + "(y) 범위 내\n" +
                "4. 같은 액션을 3회 이상 반복했다면 다른 방법 시도\n" +
                "5. 목표가 완전히 달성되면 반드시 DONE 반환\n" +
                "6. 한국어 입력이 필요하면 반드시 TYPE 사용\n" +
                "7. 앱 실행이 필요하면 LAUNCH_APP 사용 (직접 탭하지 말고)\n" +
                "8. 키보드가 보이고 입력할 내용이 있으면 TYPE 사용\n" +
                "9. 팝업/알림이 보이면 닫거나 적절히 처리\n" +
                "10. 불필요한 WAIT는 하지 마라 - 화면이 준비된 상태면 바로 행동\n\n" +
                "━━━ 응답 형식 ━━━\n" +
                "반드시 아래 JSON 형식으로만 답해라. 다른 텍스트 절대 금지.\n" +
                "{\"action\":\"액션명\",\"x\":숫자,\"y\":숫자,\"text\":\"텍스트\"," +
                "\"app_package\":\"패키지명\",\"ms\":숫자,\"reason\":\"이유\"}\n\n" +
                "좌표가 필요 없는 액션은 x,y를 0으로. text가 없으면 빈 문자열로.";

            String response = callGeminiLarge(screen, prompt);
            if (response == null) return null;

            return parseAgentDecision(response);
        });
    }

    private AgentDecision parseAgentDecision(String raw) {
        try {
            String cleaned = raw.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("```[a-z]*\\n?", "").replace("```", "").trim();
            }
            int start = cleaned.indexOf("{");
            int end = cleaned.lastIndexOf("}");
            if (start >= 0 && end > start) {
                cleaned = cleaned.substring(start, end + 1);
            }

            AgentDecision d = new AgentDecision();
            d.action = extractJsonField(cleaned, "action");
            d.reason = extractJsonField(cleaned, "reason");
            d.text = extractJsonField(cleaned, "text");
            d.appPackage = extractJsonField(cleaned, "app_package");

            int x = extractJsonInt(cleaned, "x");
            int y = extractJsonInt(cleaned, "y");
            d.x = Math.max(0, x);
            d.y = Math.max(0, y);
            d.waitMs = Math.max(0, extractJsonInt(cleaned, "ms"));

            if (d.action == null || d.action.isEmpty()) {
                Log.e(TAG, "No action in response: " + raw);
                return null;
            }

            d.action = d.action.toUpperCase().trim();
            Log.i(TAG, "Agent decision: " + d.action + " reason=" + d.reason);
            return d;
        } catch (Exception e) {
            Log.e(TAG, "parseAgentDecision error: " + e.getMessage() + " | " + raw);
            return null;
        }
    }

    // ══════════════════════════════════════════════
    //  Element finding (shared with v3)
    // ══════════════════════════════════════════════

    public Future<Point> findElementOnScreen(Bitmap frame, String description) {
        return executor.submit(() -> {
            int w = frame.getWidth();
            int h = frame.getHeight();
            String prompt =
                "이 이미지는 안드로이드 기기의 실제 화면 캡처이며 해상도는 " + w + "x" + h + "픽셀이다.\n" +
                "화면에서 다음에 해당하는 UI 요소를 찾아라: " + description + "\n" +
                "주의: 상태바(y < 100)는 무시.\n" +
                "찾으면 중심 픽셀 좌표를 'x:숫자, y:숫자' 형식으로만 답해라.\n" +
                "없으면 'NOT_FOUND'라고만 답해라.";

            String response = callGemini(frame, prompt);
            if (response == null || response.contains("NOT_FOUND")) return null;
            return parseXY(response);
        });
    }

    public Future<Point> findTossAppOnHome(Bitmap frame) {
        return executor.submit(() -> {
            int w = frame.getWidth();
            int h = frame.getHeight();
            String prompt =
                "이 이미지는 안드로이드 홈/런처 화면이다. 해상도 " + w + "x" + h + "픽셀.\n" +
                "'토스' 앱 아이콘을 찾아라. 중심 좌표를 'x:숫자, y:숫자' 형식으로만 답해라.\n" +
                "없으면 'NOT_FOUND'라고만 답해라.";
            String response = callGemini(frame, prompt);
            if (response == null || response.contains("NOT_FOUND")) return null;
            return parseXY(response);
        });
    }

    // ══════════════════════════════════════════════
    //  Voice command parsing (shared with v3)
    // ══════════════════════════════════════════════

    public Future<ParsedCommand> parseVoiceCommand(String voiceText) {
        return executor.submit(() -> {
            String prompt =
                "아래 한국어 음성 명령에서 송금 대상과 금액을 추출해라.\n" +
                "음성: \"" + voiceText + "\"\n\n" +
                "금액은 원 단위 정수로 변환. 대상 불분명 시 \"unknown\", 금액 불분명 시 0.\n" +
                "반드시 JSON으로만: {\"recipient\":\"이름\",\"amount\":숫자}";

            String response = callGeminiText(prompt);
            if (response == null) return new ParsedCommand("unknown", 0);

            String cleaned = response.trim();
            if (cleaned.startsWith("```"))
                cleaned = cleaned.replaceAll("```[a-z]*\\n?", "").replace("```", "").trim();

            String recipient = extractJsonField(cleaned, "recipient");
            int amount = extractJsonInt(cleaned, "amount");
            if (recipient == null) recipient = "unknown";
            if (amount < 0) amount = 0;
            return new ParsedCommand(recipient, amount);
        });
    }

    // ══════════════════════════════════════════════
    //  Gemini API calls
    // ══════════════════════════════════════════════

    private String callGemini(Bitmap frame, String prompt) {
        return callGeminiInternal(frame, prompt, 64);
    }

    private String callGeminiLarge(Bitmap frame, String prompt) {
        return callGeminiInternal(frame, prompt, 256);
    }

    private String callGeminiInternal(Bitmap frame, String prompt, int maxTokens) {
        try {
            String base64 = bitmapToBase64(frame);
            String body = buildRequestBody(base64, prompt, maxTokens);

            Request req = new Request.Builder()
                    .url(GEMINI_URL + apiKey)
                    .post(RequestBody.create(body, JSON_TYPE))
                    .build();

            try (Response resp = http.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) {
                    String errBody = resp.body() != null ? resp.body().string() : "no body";
                    Log.e(TAG, "Gemini API error: " + resp.code() + " | " +
                            errBody.substring(0, Math.min(200, errBody.length())));
                    return null;
                }
                return parseGeminiText(resp.body().string());
            }
        } catch (IOException e) {
            Log.e(TAG, "callGemini failed: " + e.getMessage());
            return null;
        }
    }

    private String callGeminiText(String prompt) {
        try {
            JsonObject textPart = new JsonObject();
            textPart.addProperty("text", prompt);
            JsonArray parts = new JsonArray();
            parts.add(textPart);
            JsonObject content = new JsonObject();
            content.add("parts", parts);
            JsonArray contents = new JsonArray();
            contents.add(content);
            JsonObject root = new JsonObject();
            root.add("contents", contents);
            JsonObject genConfig = new JsonObject();
            genConfig.addProperty("maxOutputTokens", 128);
            genConfig.addProperty("temperature", 0.0);
            root.add("generationConfig", genConfig);

            String body = gson.toJson(root);
            Request req = new Request.Builder()
                    .url(GEMINI_URL + apiKey)
                    .post(RequestBody.create(body, JSON_TYPE))
                    .build();

            try (Response resp = http.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) return null;
                return parseGeminiText(resp.body().string());
            }
        } catch (IOException e) {
            Log.e(TAG, "callGeminiText failed: " + e.getMessage());
            return null;
        }
    }

    // ══════════════════════════════════════════════
    //  JSON / response parsing helpers
    // ══════════════════════════════════════════════

    private String buildRequestBody(String base64Image, String prompt, int maxOutputTokens) {
        JsonObject imageData = new JsonObject();
        imageData.addProperty("mime_type", "image/jpeg");
        imageData.addProperty("data", base64Image);
        JsonObject inlineData = new JsonObject();
        inlineData.add("inline_data", imageData);
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", prompt);
        JsonArray parts = new JsonArray();
        parts.add(inlineData);
        parts.add(textPart);
        JsonObject content = new JsonObject();
        content.add("parts", parts);
        JsonArray contents = new JsonArray();
        contents.add(content);
        JsonObject root = new JsonObject();
        root.add("contents", contents);
        JsonObject genConfig = new JsonObject();
        genConfig.addProperty("maxOutputTokens", maxOutputTokens);
        genConfig.addProperty("temperature", 0.0);
        root.add("generationConfig", genConfig);
        return gson.toJson(root);
    }

    private String parseGeminiText(String json) {
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            return root.getAsJsonArray("candidates").get(0).getAsJsonObject()
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts").get(0).getAsJsonObject()
                    .get("text").getAsString().trim();
        } catch (Exception e) {
            Log.e(TAG, "parseGeminiText error: " + e.getMessage());
            return null;
        }
    }

    private Point parseXY(String text) {
        try {
            Pattern p = Pattern.compile("x\\s*:\\s*(\\d+).*?y\\s*:\\s*(\\d+)",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher m = p.matcher(text);
            if (m.find()) {
                return new Point(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
            }
        } catch (Exception e) {
            Log.e(TAG, "parseXY error: " + e.getMessage());
        }
        return null;
    }

    private String extractJsonField(String json, String key) {
        try {
            String search = "\"" + key + "\":\"";
            int start = json.indexOf(search);
            if (start < 0) return null;
            start += search.length();
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        } catch (Exception e) { return null; }
    }

    private int extractJsonInt(String json, String key) {
        try {
            String search = "\"" + key + "\":";
            int start = json.indexOf(search);
            if (start < 0) return -1;
            start += search.length();
            while (start < json.length() && json.charAt(start) == ' ') start++;
            int end = start;
            // Handle negative numbers
            if (end < json.length() && json.charAt(end) == '-') end++;
            while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
            if (end == start) return -1;
            return Integer.parseInt(json.substring(start, end));
        } catch (Exception e) { return -1; }
    }

    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos);
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
