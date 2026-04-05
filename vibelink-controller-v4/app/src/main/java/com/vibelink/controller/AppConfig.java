package com.vibelink.controller;

public class AppConfig {

    public static final String GEMINI_API_KEY = "AIzaSyArZBqCCI1bC77qbV8dcShbC-B3xt4SrxI";

    public static final int STREAM_PORT = 7788;
    public static final int ADB_PORT = 5555;

    // Must match vibelink-sender-v4 AdbInputMethodService
    public static final String ADB_IME_ID = "com.vibelink.sender.v4/com.vibelink.sender.ime.AdbInputMethodService";

    public static final String SENDER_PACKAGE = "com.vibelink.sender.v4";
    public static final String CLIPBOARD_RECEIVER = "com.vibelink.sender.v4/com.vibelink.sender.receiver.ClipboardReceiver";
}
