package com.vibelink.controller;

// API keys and global constants - keep this file out of version control (add to .gitignore)
public class AppConfig {

    // Gemini 1.5 Flash API key
    public static final String GEMINI_API_KEY = "AIzaSyArZBqCCI1bC77qbV8dcShbC-B3xt4SrxI";

    // Stream server port (must match vibelink-sender StreamServer.DEFAULT_PORT)
    public static final int STREAM_PORT = 7788;

    // ADB port on device A
    public static final int ADB_PORT = 5555;

    // IME for Korean input - must match vibelink-sender-v3 AdbInputMethodService (no popup)
    public static final String ADB_IME_ID = "com.vibelink.sender.v3/com.vibelink.sender.ime.AdbInputMethodService";

    // Sender package for clipboard broadcast (paste fallback)
    public static final String SENDER_PACKAGE = "com.vibelink.sender.v3";
    public static final String CLIPBOARD_RECEIVER = "com.vibelink.sender.v3/com.vibelink.sender.receiver.ClipboardReceiver";
}
