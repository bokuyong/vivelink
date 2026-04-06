package com.vibelink.sender.ime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

// Built-in IME that receives ADB broadcast and injects text into focused input field
// Replaces the need to install a separate ADBKeyboard.apk
// Broadcast: adb shell am broadcast -a ADB_INPUT_TEXT --es msg 'text'
// Clear:     adb shell am broadcast -a ADB_INPUT_CLEAR
public class AdbInputMethodService extends InputMethodService {

    private static final String TAG = "AdbIME";
    private static final String ACTION_INPUT_TEXT  = "ADB_INPUT_TEXT";
    private static final String ACTION_INPUT_CLEAR = "ADB_INPUT_CLEAR";
    private static final String ACTION_INPUT_KEYCODE = "ADB_INPUT_KEYCODE";

    private BroadcastReceiver receiver;

    @Override
    public void onCreate() {
        super.onCreate();
        registerAdbReceiver();
        Log.i(TAG, "AdbInputMethodService created");
    }

    // Register broadcast receiver for ADB text injection commands
    private void registerAdbReceiver() {
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action == null) return;

                InputConnection ic = getCurrentInputConnection();
                if (ic == null) {
                    Log.w(TAG, "No active InputConnection, cannot inject text");
                    return;
                }

                if (ACTION_INPUT_TEXT.equals(action)) {
                    String text = intent.getStringExtra("msg");
                    if (text != null) {
                        ic.commitText(text, 1);
                        Log.i(TAG, "Injected text: " + text);
                    }
                } else if (ACTION_INPUT_CLEAR.equals(action)) {
                    ic.deleteSurroundingText(9999, 0);
                    Log.i(TAG, "Cleared input");
                } else if (ACTION_INPUT_KEYCODE.equals(action)) {
                    int keycode = intent.getIntExtra("code", -1);
                    if (keycode >= 0) {
                        ic.sendKeyEvent(new android.view.KeyEvent(
                            android.view.KeyEvent.ACTION_DOWN, keycode));
                        ic.sendKeyEvent(new android.view.KeyEvent(
                            android.view.KeyEvent.ACTION_UP, keycode));
                        Log.i(TAG, "Sent keycode: " + keycode);
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_INPUT_TEXT);
        filter.addAction(ACTION_INPUT_CLEAR);
        filter.addAction(ACTION_INPUT_KEYCODE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
    }

    @Override
    public View onCreateInputView() {
        // Return empty view - this IME has no visible keyboard UI
        return new View(this);
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        Log.d(TAG, "onStartInput restarting=" + restarting);
    }

    @Override
    public void onDestroy() {
        if (receiver != null) {
            unregisterReceiver(receiver);
        }
        super.onDestroy();
    }
}
