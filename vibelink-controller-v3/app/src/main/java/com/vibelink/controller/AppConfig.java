package com.vibelink.controller;

// API keys and global constants - keep this file out of version control (add to .gitignore)
public class AppConfig {

    // Gemini API key — Google AI Studio에서 발급 (https://aistudio.google.com/apikey)
    // 보안: 실제 키는 별도 관리하고 이 파일에 직접 커밋하지 마세요
    public static final String GEMINI_API_KEY = "YOUR_GEMINI_API_KEY_HERE";

    // Stream server port (must match clodock-sender StreamServer.DEFAULT_PORT)
    public static final int STREAM_PORT = 7788;

    // ADB port on device A
    public static final int ADB_PORT = 5555;

    // IME for Korean input - must match clodock-sender AdbInputMethodService (no popup)
    public static final String ADB_IME_ID = "com.vibelink.sender.v3/com.vibelink.sender.ime.AdbInputMethodService";

    // Sender package for clipboard broadcast (paste fallback)
    public static final String SENDER_PACKAGE = "com.vibelink.sender.v3";
    public static final String CLIPBOARD_RECEIVER = "com.vibelink.sender.v3/com.vibelink.sender.receiver.ClipboardReceiver";
}
