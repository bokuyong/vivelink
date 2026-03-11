package com.vibelink.sender.receiver;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

// Receives ADB_SET_CLIPBOARD from Controller to set device clipboard for paste fallback
// am broadcast -a ADB_SET_CLIPBOARD --es text "아내" -n com.vibelink.sender.v3/com.vibelink.sender.receiver.ClipboardReceiver
public class ClipboardReceiver extends BroadcastReceiver {

    private static final String TAG = "ClipboardReceiver";
    public static final String ACTION_SET_CLIPBOARD = "ADB_SET_CLIPBOARD";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_SET_CLIPBOARD.equals(intent.getAction())) return;
        String text = intent.getStringExtra("text");
        if (text == null) return;
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("vibelink", text));
            Log.i(TAG, "Clipboard set: " + text);
        }
    }
}
