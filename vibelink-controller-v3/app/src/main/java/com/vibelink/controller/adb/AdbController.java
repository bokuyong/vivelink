package com.vibelink.controller.adb;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vibelink.controller.AppConfig;
import dadb.AdbKeyPair;
import dadb.Dadb;

// Controls device A via ADB over WiFi using dadb library
// Device A must have USB debugging on and adb tcpip 5555 pre-configured
public class AdbController {

    private static final String TAG = "AdbController";
    private static final int ADB_PORT = 5555;

    private static final String REMOTE_SCREEN_PATH = "/sdcard/clodock_cap.png";

    private final String deviceIp;
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Dadb dadb;
    private AdbCommandListener commandListener;

    public interface AdbCommandListener {
        void onConnected();
        void onCommandResult(String command, String output);
        void onError(String message);
    }

    public AdbController(Context context, String deviceIp) {
        this.context = context;
        this.deviceIp = deviceIp;
    }

    public void setCommandListener(AdbCommandListener listener) {
        this.commandListener = listener;
    }

    // Connect to device A ADB daemon - must be called before any commands
    public Future<Boolean> connect() {
        return executor.submit(() -> {
            try {
                File keyDir = context.getFilesDir();
                File privateKey = new File(keyDir, "adbkey");
                File publicKey = new File(keyDir, "adbkey.pub");

                // Generate key pair if not exists
                if (!privateKey.exists()) {
                    AdbKeyPair.generate(privateKey, publicKey);
                }
                AdbKeyPair keyPair = AdbKeyPair.read(privateKey, publicKey);
                dadb = Dadb.create(deviceIp, ADB_PORT, keyPair);
                Log.i(TAG, "ADB connected to " + deviceIp);
                if (commandListener != null) commandListener.onConnected();

                return true;
            } catch (Exception e) {
                Log.e(TAG, "ADB connect failed: " + e.getMessage());
                if (commandListener != null) commandListener.onError("연결 실패: " + e.getMessage());

                return false;
            }
        });
    }

    // Capture device A screen: screencap -> pull -> decode Bitmap
    // Returns null on failure
    public Future<Bitmap> captureScreen() {
        return executor.submit(() -> {
            if (dadb == null) return null;
            try {
                // Capture to sdcard on device A
                dadb.shell("screencap -p " + REMOTE_SCREEN_PATH);

                // Pull PNG file to controller local cache
                File localFile = new File(context.getCacheDir(), "screen_cap.png");
                dadb.pull(localFile, REMOTE_SCREEN_PATH);

                // Decode to Bitmap
                Bitmap bitmap = BitmapFactory.decodeFile(localFile.getAbsolutePath());
                if (bitmap != null) {
                    Log.d(TAG, "Screen captured: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                } else {
                    Log.e(TAG, "BitmapFactory failed to decode captured screen");
                }

                return bitmap;
            } catch (Exception e) {
                Log.e(TAG, "captureScreen failed: " + e.getMessage());
                return null;
            }
        });
    }

    // Execute tap at absolute pixel coordinates on device A
    public Future<String> tap(int x, int y) {
        return executeShell("input tap " + x + " " + y);
    }

    // Swipe from (x1,y1) to (x2,y2) over durationMs milliseconds
    public Future<String> swipe(int x1, int y1, int x2, int y2, int durationMs) {
        return executeShell("input swipe " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + durationMs);
    }

    // Input text - spaces replaced with %s for adb shell compatibility
    public Future<String> inputText(String text) {
        String safeText = text.replace(" ", "%s");
        return executeShell("input text " + safeText);
    }

    // Input Korean (or any Unicode) text via ADBKeyboard broadcast
    public Future<String> inputTextKorean(String text) {
        return executeShell("am broadcast -a ADB_INPUT_TEXT --es msg '" + text + "'");
    }

    // Set clipboard on device via Sender ClipboardReceiver, then paste - IME independent
    public Future<String> setClipboardAndPaste(String text) {
        return executor.submit(() -> {
            String escaped = text.replace("'", "'\\''");
            runShell("am broadcast -a ADB_SET_CLIPBOARD --es text '" + escaped + "' -n " + AppConfig.CLIPBOARD_RECEIVER);
            Thread.sleep(300);
            runShell("input keyevent 279"); // KEYCODE_PASTE
            Log.i(TAG, "Clipboard+paste: " + text);
            return "ok";
        });
    }

    // Get current default IME package/class (settings get secure default_input_method)
    public Future<String> getDefaultIme() {
        return executor.submit(() -> {
            if (dadb == null) return "";
            try {
                String out = dadb.shell("settings get secure default_input_method").getAllOutput().trim();
                Log.i(TAG, "Current IME: " + out);
                return out;
            } catch (Exception e) {
                Log.e(TAG, "getDefaultIme failed: " + e.getMessage());
                return "";
            }
        });
    }

    // Switch IME on device A - imeId format: "com.package/.ServiceClass"
    public Future<String> switchIme(String imeId) {
        return executeShell("ime set " + imeId);
    }

    // Switch to VibeLink Sender IME (built-in, no popup) for Korean broadcast input
    public Future<String> switchToAdbKeyboard() {
        return executor.submit(() -> {
            try {
                String original = "";
                try {
                    original = dadb.shell("settings get secure default_input_method").getAllOutput().trim();
                } catch (Exception e) {
                    Log.w(TAG, "getDefaultIme before switch failed: " + e.getMessage());
                }
                dadb.shell("ime enable " + AppConfig.ADB_IME_ID);
                dadb.shell("ime set " + AppConfig.ADB_IME_ID);
                Thread.sleep(600);
                Log.i(TAG, "Switched IME -> Clodock Keyboard (was: " + original + ")");
                return original;
            } catch (Exception e) {
                Log.e(TAG, "switchToAdbKeyboard failed: " + e.getMessage());
                return "";
            }
        });
    }

    // Restore IME to previously saved value; if empty or null, skip
    public Future<Void> restoreIme(String savedIme) {
        return executor.submit(() -> {
            if (savedIme == null || savedIme.isEmpty() || savedIme.equals("null")) {
                Log.d(TAG, "restoreIme: nothing to restore");
                return null;
            }
            try {
                dadb.shell("ime set " + savedIme);
                Log.i(TAG, "Restored IME -> " + savedIme);
            } catch (Exception e) {
                Log.e(TAG, "restoreIme failed: " + e.getMessage());
            }
            return null;
        });
    }

    // Press keycode (KEYCODE_BACK=4, KEYCODE_HOME=3, KEYCODE_ENTER=66, KEYCODE_DEL=67)
    public Future<String> pressKey(int keyCode) {
        return executeShell("input keyevent " + keyCode);
    }

    // Check if device is in a phone call and end it if so
    public Future<Boolean> endCallIfActive() {
        return executor.submit(() -> {
            try {
                String state = runShellWithOutput("dumpsys telephony.registry | grep mCallState");
                Log.d(TAG, "Call state: " + state);
                if (state != null && state.contains("mCallState=2")) {
                    // CALL_STATE_OFFHOOK=2 means active call - end it
                    runShell("input keyevent 6"); // KEYCODE_ENDCALL
                    Log.i(TAG, "Active call ended before automation");
                    Thread.sleep(1000);
                    return true;
                }
                return false;
            } catch (Exception e) {
                Log.e(TAG, "endCallIfActive failed: " + e.getMessage());
                return false;
            }
        });
    }

    // Launch Toss app - monkey is most reliable when activity name may change
    public Future<String> launchToss() {
        return executeShell("monkey -p viva.republica.toss -c android.intent.category.LAUNCHER 1");
    }

    // Dump UI hierarchy via uiautomator and find element by text, return center Point
    // Returns null if element not found
    public Future<Point> findElementByText(String text) {
        return executor.submit(() -> {
            if (dadb == null) return null;
            try {
                String remotePath = "/sdcard/clodock_ui.xml";
                File localFile = new File(context.getCacheDir(), "ui_dump.xml");

                dadb.shell("uiautomator dump " + remotePath);
                dadb.pull(localFile, remotePath);

                String xml = readFile(localFile);
                if (xml == null) return null;

                // Find node containing exact text, extract bounds
                Pattern p = Pattern.compile(
                    "text=\"[^\"]*" + Pattern.quote(text) + "[^\"]*\"[^<]*bounds=\"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]\"",
                    Pattern.CASE_INSENSITIVE
                );
                Matcher m = p.matcher(xml);
                if (m.find()) {
                    int x = (Integer.parseInt(m.group(1)) + Integer.parseInt(m.group(3))) / 2;
                    int y = (Integer.parseInt(m.group(2)) + Integer.parseInt(m.group(4))) / 2;
                    Log.i(TAG, "UIAutomator found '" + text + "' at (" + x + "," + y + ")");
                    return new Point(x, y);
                }

                // Try bounds before text (alternate attribute order)
                Pattern p2 = Pattern.compile(
                    "bounds=\"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]\"[^<]*text=\"[^\"]*" + Pattern.quote(text) + "[^\"]*\"",
                    Pattern.CASE_INSENSITIVE
                );
                Matcher m2 = p2.matcher(xml);
                if (m2.find()) {
                    int x = (Integer.parseInt(m2.group(1)) + Integer.parseInt(m2.group(3))) / 2;
                    int y = (Integer.parseInt(m2.group(2)) + Integer.parseInt(m2.group(4))) / 2;
                    Log.i(TAG, "UIAutomator found '" + text + "' at (" + x + "," + y + ")");
                    return new Point(x, y);
                }

                Log.w(TAG, "UIAutomator: '" + text + "' not found in dump");
                return null;
            } catch (Exception e) {
                Log.e(TAG, "findElementByText failed: " + e.getMessage());
                return null;
            }
        });
    }

    // Dump UI and find first element by resource-id (partial match)
    public Future<Point> findElementByResId(String resId) {
        return executor.submit(() -> {
            if (dadb == null) return null;
            try {
                String remotePath = "/sdcard/clodock_ui.xml";
                File localFile = new File(context.getCacheDir(), "ui_dump.xml");

                dadb.shell("uiautomator dump " + remotePath);
                dadb.pull(localFile, remotePath);

                String xml = readFile(localFile);
                if (xml == null) return null;

                // Forward: resource-id before bounds (exact suffix match - no trailing chars after resId)
                Pattern p = Pattern.compile(
                    "resource-id=\"[^\"]*" + Pattern.quote(resId) + "\"[^<]*bounds=\"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]\"",
                    Pattern.CASE_INSENSITIVE
                );
                Matcher m = p.matcher(xml);
                if (m.find()) {
                    int x = (Integer.parseInt(m.group(1)) + Integer.parseInt(m.group(3))) / 2;
                    int y = (Integer.parseInt(m.group(2)) + Integer.parseInt(m.group(4))) / 2;
                    Log.i(TAG, "UIAutomator(resId) found '" + resId + "' at (" + x + "," + y + ")");
                    return new Point(x, y);
                }

                // Reverse: bounds before resource-id
                Pattern p2 = Pattern.compile(
                    "bounds=\"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]\"[^<]*resource-id=\"[^\"]*" + Pattern.quote(resId) + "\"",
                    Pattern.CASE_INSENSITIVE
                );
                Matcher m2 = p2.matcher(xml);
                if (m2.find()) {
                    int x = (Integer.parseInt(m2.group(1)) + Integer.parseInt(m2.group(3))) / 2;
                    int y = (Integer.parseInt(m2.group(2)) + Integer.parseInt(m2.group(4))) / 2;
                    Log.i(TAG, "UIAutomator(resId-rev) found '" + resId + "' at (" + x + "," + y + ")");
                    return new Point(x, y);
                }

                Log.w(TAG, "UIAutomator(resId): '" + resId + "' not found");
                return null;
            } catch (Exception e) {
                Log.e(TAG, "findElementByResId failed: " + e.getMessage());
                return null;
            }
        });
    }

    // Dump UI and find element by content-desc attribute
    public Future<Point> findElementByDesc(String desc) {
        return executor.submit(() -> {
            if (dadb == null) return null;
            try {
                String remotePath = "/sdcard/clodock_ui.xml";
                File localFile = new File(context.getCacheDir(), "ui_dump.xml");

                dadb.shell("uiautomator dump " + remotePath);
                dadb.pull(localFile, remotePath);

                String xml = readFile(localFile);
                if (xml == null) return null;

                Pattern p = Pattern.compile(
                    "content-desc=\"[^\"]*" + Pattern.quote(desc) + "[^\"]*\"[^<]*bounds=\"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]\"",
                    Pattern.CASE_INSENSITIVE
                );
                Matcher m = p.matcher(xml);
                if (m.find()) {
                    int x = (Integer.parseInt(m.group(1)) + Integer.parseInt(m.group(3))) / 2;
                    int y = (Integer.parseInt(m.group(2)) + Integer.parseInt(m.group(4))) / 2;
                    Log.i(TAG, "UIAutomator(desc) found '" + desc + "' at (" + x + "," + y + ")");
                    return new Point(x, y);
                }

                // Reverse: bounds before content-desc
                Pattern p2 = Pattern.compile(
                    "bounds=\"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]\"[^<]*content-desc=\"[^\"]*" + Pattern.quote(desc) + "[^\"]*\"",
                    Pattern.CASE_INSENSITIVE
                );
                Matcher m2 = p2.matcher(xml);
                if (m2.find()) {
                    int x = (Integer.parseInt(m2.group(1)) + Integer.parseInt(m2.group(3))) / 2;
                    int y = (Integer.parseInt(m2.group(2)) + Integer.parseInt(m2.group(4))) / 2;
                    Log.i(TAG, "UIAutomator(desc-rev) found '" + desc + "' at (" + x + "," + y + ")");
                    return new Point(x, y);
                }

                Log.w(TAG, "UIAutomator(desc): '" + desc + "' not found");
                return null;
            } catch (Exception e) {
                Log.e(TAG, "findElementByDesc failed: " + e.getMessage());
                return null;
            }
        });
    }

    private String readFile(File f) {
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new FileReader(f));
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "readFile failed: " + e.getMessage());
            return null;
        }
    }

    // Run arbitrary shell command via dadb
    public Future<String> executeShell(String shellCmd) {
        return executor.submit(() -> runShell(shellCmd));
    }

    private String runShell(String cmd) {
        if (dadb == null) {
            String err = "ADB not connected";
            Log.e(TAG, err);
            if (commandListener != null) commandListener.onError(err);
            return "";
        }
        try {
            dadb.shell(cmd);
            Log.d(TAG, "ADB shell: " + cmd);
            if (commandListener != null) commandListener.onCommandResult(cmd, "ok");
            return "ok";
        } catch (Exception e) {
            Log.e(TAG, "shell failed [" + cmd + "]: " + e.getMessage());
            if (commandListener != null) commandListener.onError(e.getMessage());
            return "";
        }
    }

    // Run shell command and return stdout as string
    private String runShellWithOutput(String cmd) {
        if (dadb == null) return null;
        try {
            dadb.shell(cmd);
            return "ok";
        } catch (Exception e) {
            Log.e(TAG, "runShellWithOutput failed: " + e.getMessage());
            return null;
        }
    }

    public boolean isConnected() {
        return dadb != null;
    }

    public void shutdown() {
        executor.shutdownNow();
        if (dadb != null) {
            try {
                dadb.close();
            } catch (Exception ignored) {}
            dadb = null;
        }
    }
}
