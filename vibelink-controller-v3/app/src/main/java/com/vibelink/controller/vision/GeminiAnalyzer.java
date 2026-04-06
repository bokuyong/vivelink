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

// Gemini 1.5 Flash vision API wrapper
// Locates UI elements by description and returns pixel coordinates for ADB tap
public class GeminiAnalyzer {

    private static final String TAG = "GeminiAnalyzer";

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=";

    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    // Higher quality = better OCR accuracy for Korean text
    private static final int JPEG_QUALITY = 90;

    private final String apiKey;
    private final OkHttpClient http;
    private final Gson gson = new Gson();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public GeminiAnalyzer(String apiKey) {
        this.apiKey = apiKey;
        this.http = new OkHttpClient.Builder()
                .callTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    // Find UI element by label and return center pixel coordinate
    // screenWidth/screenHeight = actual device resolution for accurate coordinate output
    public Future<Point> findElement(Bitmap frame, String elementLabel, int screenWidth, int screenHeight) {
        return executor.submit(() -> doFindElement(frame, elementLabel, screenWidth, screenHeight));
    }

    // Overload: use bitmap dimensions directly (for screencap which is full resolution)
    public Future<Point> findElement(Bitmap frame, String elementLabel) {
        return findElement(frame, elementLabel, frame.getWidth(), frame.getHeight());
    }

    // Find Toss app icon on home screen (launcher) - different from in-app elements
    public Future<Point> findTossAppOnHome(Bitmap frame) {
        return executor.submit(() -> {
            int w = frame.getWidth();
            int h = frame.getHeight();
            String prompt =
                "이 이미지는 안드로이드 홈/런처 화면이다. 해상도 " + w + "x" + h + "픽셀.\n" +
                "'토스' 앱 아이콘을 찾아라. 아이콘 또는 그 아래 '토스' 라벨의 중심 픽셀 좌표를 'x:숫자, y:숫자' 형식으로만 답해라.\n" +
                "예시: x:540, y:900\n" +
                "없으면 'NOT_FOUND' 라고만 답해라.\n" +
                "다른 설명은 절대 추가하지 마라.";
            String response = callGemini(frame, prompt);
            if (response == null || response.contains("NOT_FOUND")) {
                Log.d(TAG, "Toss app icon not found on home");
                return null;
            }
            return parseXY(response);
        });
    }

    private Point doFindElement(Bitmap frame, String elementLabel, int screenWidth, int screenHeight) {
        String prompt =
            "이 이미지는 안드로이드 기기의 실제 화면 캡처이며 해상도는 " + screenWidth + "x" + screenHeight + "픽셀이다.\n" +
            "화면에서 '" + elementLabel + "' 라고 표시된 버튼 또는 텍스트를 찾아라.\n" +
            "주의사항:\n" +
            "1. 화면 최상단(y < 200) 영역은 상태바이므로 절대 선택하지 마라.\n" +
            "2. '결제' 버튼과 '송금' 버튼을 절대 혼동하지 마라. '결제'는 화면 상단 우측에 있고, '송금'은 계좌 목록 각 행의 오른쪽 끝에 있다.\n" +
            "찾으면 반드시 해당 요소의 중심 픽셀 좌표를 'x:숫자, y:숫자' 형식으로만 답해라.\n" +
            "예시: x:540, y:800\n" +
            "요소가 화면에 없으면 'NOT_FOUND' 라고만 답해라.\n" +
            "다른 설명은 절대 추가하지 마라.";

        String response = callGemini(frame, prompt);
        if (response == null || response.contains("NOT_FOUND")) {
            Log.d(TAG, "Element not found: " + elementLabel);
            return null;
        }

        return parseXY(response);
    }

    // Analyze current Toss screen and return which step is visible
    public Future<String> detectTossStep(Bitmap frame) {
        return executor.submit(() -> doDetectTossStep(frame));
    }

    // Detect what app/screen is currently visible
    // Returns: "토스홈", "토스기타", "배경화면", "다른앱", "잠금화면"
    public Future<String> detectCurrentScreen(Bitmap frame) {
        return executor.submit(() -> {
            String prompt =
                "이 안드로이드 화면을 보고 현재 상태를 아래 선택지 중 하나만 답해라.\n" +
                "선택지: 토스홈, 토스기타, 배경화면, 다른앱, 잠금화면\n" +
                "- 토스홈: 토스 앱이 열려있고 홈화면(하단에 보내기/자산 탭 보임)\n" +
                "- 토스기타: 토스 앱이 열려있지만 다른 화면\n" +
                "- 배경화면: 홈 런처나 배경화면 상태\n" +
                "- 다른앱: 토스가 아닌 다른 앱 화면\n" +
                "- 잠금화면: 잠금 또는 꺼진 화면\n" +
                "반드시 선택지 중 하나만 답하고 다른 설명은 절대 하지 마라.";

            String result = callGemini(frame, prompt);

            return result != null ? result.trim() : "다른앱";
        });
    }

    private String doDetectTossStep(Bitmap frame) {
        String prompt =
            "이 화면은 토스(Toss) 앱의 어떤 단계인지 아래 선택지 중 하나만 답해라.\n" +
            "선택지: 홈화면, 보내기목록, 연락처검색, 금액입력, 송금확인, 완료, 기타\n" +
            "반드시 선택지 중 하나만 답하고 다른 설명은 하지 마라.";

        String result = callGemini(frame, prompt);

        return result != null ? result.trim() : "기타";
    }

    // Send image + text prompt to Gemini, return raw text response
    private String callGemini(Bitmap frame, String prompt) {
        try {
            String base64 = bitmapToBase64(frame);
            String body = buildRequestBody(base64, prompt);

            Request req = new Request.Builder()
                    .url(GEMINI_URL + apiKey)
                    .post(RequestBody.create(body, JSON_TYPE))
                    .build();

            try (Response resp = http.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) {
                    String errBody = resp.body() != null ? resp.body().string() : "no body";
                    Log.e(TAG, "Gemini API error: " + resp.code() + " | " + errBody.substring(0, Math.min(200, errBody.length())));
                    return null;
                }
                return parseGeminiText(resp.body().string());
            }

        } catch (IOException e) {
            Log.e(TAG, "callGemini failed: " + e.getMessage());
            return null;
        }
    }

    // Build Gemini multimodal request JSON
    private String buildRequestBody(String base64Image, String prompt) {
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
        genConfig.addProperty("maxOutputTokens", 64);
        genConfig.addProperty("temperature", 0.0);
        root.add("generationConfig", genConfig);

        return gson.toJson(root);
    }

    private String parseGeminiText(String json) {
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);

            return root
                    .getAsJsonArray("candidates").get(0).getAsJsonObject()
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts").get(0).getAsJsonObject()
                    .get("text").getAsString().trim();
        } catch (Exception e) {
            Log.e(TAG, "parseGeminiText error: " + e.getMessage() + " | raw: " + json);
            return null;
        }
    }

    // Parse "x:540, y:1200" into Point
    private Point parseXY(String text) {
        try {
            Pattern p = Pattern.compile("x\\s*:\\s*(\\d+).*?y\\s*:\\s*(\\d+)",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher m = p.matcher(text);
            if (m.find()) {
                int x = Integer.parseInt(m.group(1));
                int y = Integer.parseInt(m.group(2));
                Log.d(TAG, "Gemini coord: (" + x + ", " + y + ")");
                return new Point(x, y);
            }
        } catch (Exception e) {
            Log.e(TAG, "parseXY error: " + e.getMessage());
        }
        Log.w(TAG, "Could not parse coords from: " + text);
        return null;
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
