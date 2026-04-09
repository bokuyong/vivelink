package com.vibelink.controller;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.Base64;
import android.util.Log;

import com.vibelink.controller.adb.AdbController;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Embedded HTTP server for headless control of the Clodock Controller.
 * Access from any browser: http://<controller-ip>:8080
 */
public class WebDashboard {

    private static final String TAG = "WebDashboard";
    public static final int PORT = 8080;

    public interface DashboardCallback {
        void onConnect(String senderIp, String deviceIp);
        void onDisconnect();
        void onCommand(String rawText);
        AdbController getAdbController();
        Bitmap getLatestFrame();
        String getLogText();
    }

    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private DashboardCallback callback;
    private final CopyOnWriteArrayList<String> logs = new CopyOnWriteArrayList<>();

    public void setCallback(DashboardCallback cb) { this.callback = cb; }

    public void addLog(String msg) {
        logs.add(msg);
        if (logs.size() > 200) logs.remove(0);
    }

    public void start() {
        running = true;
        executor.execute(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                Log.i(TAG, "WebDashboard on port " + PORT);
                while (running) {
                    Socket client = serverSocket.accept();
                    executor.execute(() -> handle(client));
                }
            } catch (Exception e) {
                if (running) Log.e(TAG, "Server error: " + e.getMessage());
            }
        });
    }

    private void handle(Socket client) {
        try {
            java.io.InputStream rawIn = client.getInputStream();

            // Read request line and headers as raw bytes to get Content-Length
            StringBuilder headerBuilder = new StringBuilder();
            int prev = 0, cur;
            boolean headersDone = false;
            int contentLength = 0;
            String requestLine = null;

            // Read byte-by-byte until \r\n\r\n
            ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
            int nlCount = 0;
            while ((cur = rawIn.read()) != -1) {
                headerBytes.write(cur);
                if (cur == '\n') nlCount++;
                else if (cur != '\r') nlCount = 0;
                if (nlCount >= 2) break;
            }

            String headerStr = headerBytes.toString("UTF-8");
            String[] headerLines = headerStr.split("\r\n");
            if (headerLines.length > 0) requestLine = headerLines[0];
            for (String h : headerLines) {
                if (h.toLowerCase().startsWith("content-length:"))
                    contentLength = Integer.parseInt(h.split(":")[1].trim());
            }

            if (requestLine == null) { client.close(); return; }

            // Read body as raw bytes, then decode as UTF-8
            String body = "";
            if (contentLength > 0) {
                byte[] bodyBytes = new byte[contentLength];
                int totalRead = 0;
                while (totalRead < contentLength) {
                    int r = rawIn.read(bodyBytes, totalRead, contentLength - totalRead);
                    if (r == -1) break;
                    totalRead += r;
                }
                body = new String(bodyBytes, 0, totalRead, "UTF-8");
            }

            String method = requestLine.split(" ")[0];
            String path = requestLine.split(" ")[1];

            String response;
            String contentType = "application/json";

            if (path.equals("/") || path.equals("/index.html")) {
                response = getHtmlPage();
                contentType = "text/html; charset=utf-8";
            } else if (path.equals("/api/connect") && method.equals("POST")) {
                response = apiConnect(body);
            } else if (path.equals("/api/disconnect") && method.equals("POST")) {
                response = apiDisconnect();
            } else if (path.equals("/api/capture") && method.equals("POST")) {
                response = apiCapture();
            } else if (path.equals("/api/find") && method.equals("POST")) {
                response = apiFind(body);
            } else if (path.equals("/api/tap") && method.equals("POST")) {
                response = apiTap(body);
            } else if (path.equals("/api/logs")) {
                response = apiLogs();
            } else if (path.equals("/api/command") && method.equals("POST")) {
                response = apiCommand(body);
            } else if (path.equals("/api/status")) {
                response = apiStatus();
            } else {
                response = "{\"error\":\"not found\"}";
            }

            OutputStream out = client.getOutputStream();
            byte[] bytes = response.getBytes("UTF-8");
            String header = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: " + contentType + "\r\n"
                    + "Content-Length: " + bytes.length + "\r\n"
                    + "Access-Control-Allow-Origin: *\r\n"
                    + "Access-Control-Allow-Methods: GET, POST\r\n"
                    + "Access-Control-Allow-Headers: Content-Type\r\n"
                    + "\r\n";
            out.write(header.getBytes("UTF-8"));
            out.write(bytes);
            out.flush();
            client.close();
        } catch (Exception e) {
            Log.e(TAG, "handle: " + e.getMessage());
        }
    }

    private String apiConnect(String body) {
        try {
            String senderIp = extractJson(body, "senderIp");
            String deviceIp = extractJson(body, "deviceIp");
            if (callback != null) callback.onConnect(senderIp, deviceIp);
            return "{\"ok\":true}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private String apiDisconnect() {
        if (callback != null) callback.onDisconnect();
        return "{\"ok\":true}";
    }

    private String apiCapture() {
        try {
            AdbController adb = callback != null ? callback.getAdbController() : null;
            if (adb == null || !adb.isConnected()) return "{\"error\":\"ADB not connected\"}";

            Bitmap bitmap = adb.captureScreen().get();
            if (bitmap == null) return "{\"error\":\"capture returned null\"}";

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            String base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
            addLog("[웹] 캡처 성공: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            return "{\"ok\":true,\"image\":\"data:image/jpeg;base64," + base64 + "\","
                    + "\"width\":" + bitmap.getWidth() + ",\"height\":" + bitmap.getHeight() + "}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private String apiFind(String body) {
        try {
            AdbController adb = callback != null ? callback.getAdbController() : null;
            if (adb == null || !adb.isConnected()) return "{\"error\":\"ADB not connected\"}";

            String text = extractJson(body, "text");
            Point pt = adb.findElementByText(text).get();
            if (pt != null) {
                addLog("[웹] \"" + text + "\" 발견: (" + pt.x + ", " + pt.y + ")");
                return "{\"ok\":true,\"x\":" + pt.x + ",\"y\":" + pt.y + "}";
            } else {
                addLog("[웹] \"" + text + "\" 못 찾음");
                return "{\"ok\":false,\"message\":\"not found\"}";
            }
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private String apiTap(String body) {
        try {
            AdbController adb = callback != null ? callback.getAdbController() : null;
            if (adb == null || !adb.isConnected()) return "{\"error\":\"ADB not connected\"}";

            int x = Integer.parseInt(extractJson(body, "x"));
            int y = Integer.parseInt(extractJson(body, "y"));
            adb.tap(x, y).get();
            addLog("[웹] 탭: (" + x + ", " + y + ")");
            return "{\"ok\":true}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private String apiCommand(String body) {
        try {
            Log.d(TAG, "apiCommand body[" + body.length() + "]: " + body);
            // Debug hex
            StringBuilder hex = new StringBuilder();
            for (byte bb : body.getBytes("UTF-8")) hex.append(String.format("%02x ", bb));
            Log.d(TAG, "apiCommand hex: " + hex.toString().trim());
            String command = extractJson(body, "command");
            if (command == null || command.isEmpty()) return "{\"error\":\"command required\"}";
            if (callback != null) callback.onCommand(command);
            addLog("[웹] AI 명령: " + command);
            return "{\"ok\":true}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private String apiLogs() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < logs.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(logs.get(i))).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private String apiStatus() {
        AdbController adb = callback != null ? callback.getAdbController() : null;
        boolean connected = adb != null && adb.isConnected();
        return "{\"connected\":" + connected + "}";
    }

    private String extractJson(String json, String key) {
        // Handle both string and number values
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return "";
        int start = idx + search.length();
        if (start < json.length() && json.charAt(start) == '"') {
            start++;
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        } else {
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-'))
                end++;
            return json.substring(start, end);
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    public void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        executor.shutdownNow();
    }

    private String getHtmlPage() {
        return "<!DOCTYPE html>\n"
+ "<html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>\n"
+ "<title>Clodock Control v5</title>\n"
+ "<style>\n"
+ "* { box-sizing: border-box; margin: 0; padding: 0; }\n"
+ "body { font-family: -apple-system, sans-serif; background: #1a1a2e; color: #eee; padding: 16px; }\n"
+ "h1 { font-size: 22px; margin-bottom: 4px; }\n"
+ ".sub { color: #888; font-size: 13px; margin-bottom: 16px; }\n"
+ ".card { background: #16213e; border-radius: 12px; padding: 16px; margin-bottom: 12px; }\n"
+ ".row { display: flex; gap: 8px; margin-bottom: 8px; }\n"
+ "input { flex: 1; padding: 10px; border-radius: 8px; border: 1px solid #333; background: #0f3460; color: #fff; font-size: 14px; }\n"
+ "button { padding: 10px 16px; border-radius: 8px; border: none; font-size: 14px; cursor: pointer; font-weight: 600; }\n"
+ ".btn-primary { background: #4361ee; color: #fff; }\n"
+ ".btn-danger { background: #e63946; color: #fff; }\n"
+ ".btn-success { background: #2ec4b6; color: #fff; }\n"
+ ".btn-warn { background: #f77f00; color: #fff; }\n"
+ "button:disabled { opacity: 0.4; cursor: default; }\n"
+ "#status { display: inline-block; padding: 4px 12px; border-radius: 20px; font-size: 12px; font-weight: 600; }\n"
+ ".st-on { background: #2ec4b6; color: #000; }\n"
+ ".st-off { background: #e63946; color: #fff; }\n"
+ "#screen { width: 100%; max-height: 400px; object-fit: contain; border-radius: 8px; background: #000; display: block; margin-top: 8px; cursor: crosshair; }\n"
+ "#logs { background: #0a0a1a; border-radius: 8px; padding: 10px; font-family: monospace; font-size: 11px; color: #aaa; height: 200px; overflow-y: auto; white-space: pre-wrap; }\n"
+ "#coords { color: #f77f00; font-size: 13px; margin-top: 4px; }\n"
+ "</style></head><body>\n"
+ "<h1>Clodock Control v5</h1>\n"
+ "<div class='sub'>웹 대시보드 · <span id='status' class='st-off'>미연결</span></div>\n"
+ "\n"
+ "<div class='card'>\n"
+ "  <div class='row'>\n"
+ "    <input id='senderIp' placeholder='Sender IP (예: 192.168.50.245)'>\n"
+ "    <input id='deviceIp' placeholder='Device IP (ADB, 예: 192.168.50.245)'>\n"
+ "  </div>\n"
+ "  <div class='row'>\n"
+ "    <button class='btn-primary' onclick='doConnect()'>연결</button>\n"
+ "    <button class='btn-danger' onclick='doDisconnect()'>해제</button>\n"
+ "  </div>\n"
+ "</div>\n"
+ "\n"
+ "<div class='card'>\n"
+ "  <div class='row'>\n"
+ "    <input id='cmdText' placeholder='AI 명령 (예: 크롬 열어줘, 설정에서 와이파이 켜줘)' style='flex:2'>\n"
+ "    <button class='btn-primary' onclick='doCommand()' style='background:#8B5CF6'>AI 실행</button>\n"
+ "  </div>\n"
+ "</div>\n"
+ "\n"
+ "<div class='card'>\n"
+ "  <div class='row'>\n"
+ "    <button class='btn-success' onclick='doCapture()'>화면 캡처</button>\n"
+ "    <input id='findText' placeholder='찾을 텍스트 (예: 설정)'>\n"
+ "    <button class='btn-warn' onclick='doFind()'>요소 찾기</button>\n"
+ "    <button class='btn-primary' id='btnTap' onclick='doTap()' disabled>탭 실행</button>\n"
+ "  </div>\n"
+ "  <div id='coords'></div>\n"
+ "  <img id='screen' src='' style='display:none' onclick='onScreenClick(event)'>\n"
+ "</div>\n"
+ "\n"
+ "<div class='card'>\n"
+ "  <div id='logs'>로그 대기중...</div>\n"
+ "</div>\n"
+ "\n"
+ "<script>\n"
+ "let lastPt = null;\n"
+ "let imgW = 0, imgH = 0;\n"
+ "\n"
+ "async function api(path, body) {\n"
+ "  const r = await fetch(path, body ? { method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify(body) } : {});\n"
+ "  return r.json();\n"
+ "}\n"
+ "\n"
+ "async function doConnect() {\n"
+ "  const s = document.getElementById('senderIp').value;\n"
+ "  const d = document.getElementById('deviceIp').value;\n"
+ "  if (!s || !d) { alert('IP를 입력하세요'); return; }\n"
+ "  addLog('>> 연결 시도: Sender=' + s + ' Device=' + d);\n"
+ "  await api('/api/connect', { senderIp: s, deviceIp: d });\n"
+ "  setTimeout(checkStatus, 3000);\n"
+ "}\n"
+ "\n"
+ "async function doDisconnect() {\n"
+ "  await api('/api/disconnect', {});\n"
+ "  addLog('>> 연결 해제');\n"
+ "  checkStatus();\n"
+ "}\n"
+ "\n"
+ "async function doCommand() {\n"
+ "  const cmd = document.getElementById('cmdText').value;\n"
+ "  if (!cmd) { alert('명령을 입력하세요'); return; }\n"
+ "  addLog('>> AI 명령: ' + cmd);\n"
+ "  await api('/api/command', { command: cmd });\n"
+ "}\n"
+ "\n"
+ "async function doCapture() {\n"
+ "  addLog('>> 화면 캡처 중...');\n"
+ "  const r = await api('/api/capture', {});\n"
+ "  if (r.image) {\n"
+ "    document.getElementById('screen').src = r.image;\n"
+ "    document.getElementById('screen').style.display = 'block';\n"
+ "    imgW = r.width; imgH = r.height;\n"
+ "    addLog('[캡처] ' + r.width + 'x' + r.height);\n"
+ "  } else { addLog('[캡처 실패] ' + (r.error||'')); }\n"
+ "}\n"
+ "\n"
+ "async function doFind() {\n"
+ "  const t = document.getElementById('findText').value;\n"
+ "  if (!t) { alert('텍스트를 입력하세요'); return; }\n"
+ "  addLog('>> \"' + t + '\" 찾는 중...');\n"
+ "  const r = await api('/api/find', { text: t });\n"
+ "  if (r.ok && r.x !== undefined) {\n"
+ "    lastPt = { x: r.x, y: r.y };\n"
+ "    document.getElementById('coords').textContent = '발견: (' + r.x + ', ' + r.y + ')';\n"
+ "    document.getElementById('btnTap').disabled = false;\n"
+ "    addLog('[발견] (' + r.x + ', ' + r.y + ')');\n"
+ "  } else {\n"
+ "    lastPt = null;\n"
+ "    document.getElementById('coords').textContent = '못 찾음';\n"
+ "    document.getElementById('btnTap').disabled = true;\n"
+ "    addLog('[못 찾음] ' + t);\n"
+ "  }\n"
+ "}\n"
+ "\n"
+ "async function doTap() {\n"
+ "  if (!lastPt) return;\n"
+ "  addLog('>> 탭: (' + lastPt.x + ', ' + lastPt.y + ')');\n"
+ "  await api('/api/tap', lastPt);\n"
+ "  addLog('[탭 완료]');\n"
+ "  setTimeout(doCapture, 1000);\n"
+ "}\n"
+ "\n"
+ "function onScreenClick(e) {\n"
+ "  if (!imgW) return;\n"
+ "  const rect = e.target.getBoundingClientRect();\n"
+ "  const scaleX = imgW / rect.width;\n"
+ "  const scaleY = imgH / rect.height;\n"
+ "  const x = Math.round((e.clientX - rect.left) * scaleX);\n"
+ "  const y = Math.round((e.clientY - rect.top) * scaleY);\n"
+ "  lastPt = { x, y };\n"
+ "  document.getElementById('coords').textContent = '클릭 좌표: (' + x + ', ' + y + ')';\n"
+ "  document.getElementById('btnTap').disabled = false;\n"
+ "  addLog('[화면 클릭] (' + x + ', ' + y + ')');\n"
+ "}\n"
+ "\n"
+ "async function checkStatus() {\n"
+ "  const r = await api('/api/status');\n"
+ "  const el = document.getElementById('status');\n"
+ "  if (r.connected) { el.textContent = '연결됨'; el.className = 'st-on'; }\n"
+ "  else { el.textContent = '미연결'; el.className = 'st-off'; }\n"
+ "}\n"
+ "\n"
+ "function addLog(msg) {\n"
+ "  const el = document.getElementById('logs');\n"
+ "  const time = new Date().toLocaleTimeString();\n"
+ "  el.textContent += time + ' ' + msg + '\\n';\n"
+ "  el.scrollTop = el.scrollHeight;\n"
+ "}\n"
+ "\n"
+ "async function pollLogs() {\n"
+ "  try {\n"
+ "    const r = await fetch('/api/logs');\n"
+ "    const arr = await r.json();\n"
+ "    if (arr.length > 0) {\n"
+ "      const el = document.getElementById('logs');\n"
+ "      el.textContent = arr.join('\\n');\n"
+ "      el.scrollTop = el.scrollHeight;\n"
+ "    }\n"
+ "  } catch(e) {}\n"
+ "}\n"
+ "\n"
+ "checkStatus();\n"
+ "setInterval(checkStatus, 5000);\n"
+ "setInterval(pollLogs, 3000);\n"
+ "</script></body></html>";
    }
}
