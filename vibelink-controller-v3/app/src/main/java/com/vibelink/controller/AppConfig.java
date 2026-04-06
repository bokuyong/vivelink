package com.vibelink.controller;

// API keys and global constants - keep this file out of version control (add to .gitignore)
public class AppConfig {

    public static final String GEMINI_API_KEY = "";

    public static final int STREAM_PORT = 7788;
    public static final int ADB_PORT = 5555;
    public static final int COMMAND_PORT = 7789;
    public static final int DISCOVERY_PORT = 7791;

    public static final String ADB_IME_ID = "com.clodock.sender.v5/com.vibelink.sender.ime.AdbInputMethodService";
    public static final String SENDER_PACKAGE = "com.clodock.sender.v5";
    public static final String CLIPBOARD_RECEIVER = "com.clodock.sender.v5/com.vibelink.sender.receiver.ClipboardReceiver";
}
