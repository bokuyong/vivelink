package com.vibelink.controller.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

public class ClodockAccessibilityService extends AccessibilityService {

    private static ClodockAccessibilityService instance;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    public static boolean isReady() {
        return instance != null;
    }

    public static boolean performHome() {
        return instance != null && instance.performGlobalAction(GLOBAL_ACTION_HOME);
    }

    public static boolean performBack() {
        return instance != null && instance.performGlobalAction(GLOBAL_ACTION_BACK);
    }

    public static boolean performRecents() {
        return instance != null && instance.performGlobalAction(GLOBAL_ACTION_RECENTS);
    }

    public static boolean performNotifications() {
        return instance != null && instance.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
    }

    public static boolean performQuickSettings() {
        return instance != null && instance.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
    }

    public static boolean clickText(String target) {
        if (instance == null || target == null || target.trim().isEmpty()) {
            return false;
        }

        AccessibilityNodeInfo root = instance.getRootInActiveWindow();
        if (root == null) {
            return false;
        }

        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(target.trim());
        if (nodes == null || nodes.isEmpty()) {
            return false;
        }

        for (AccessibilityNodeInfo node : nodes) {
            if (performClick(node)) {
                return true;
            }
        }
        return false;
    }

    public static boolean inputText(String text) {
        if (instance == null || text == null || text.trim().isEmpty()) {
            return false;
        }

        AccessibilityNodeInfo focused = instance.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null) {
            AccessibilityNodeInfo root = instance.getRootInActiveWindow();
            focused = findEditableNode(root);
        }

        if (focused == null) {
            return false;
        }

        Bundle arguments = new Bundle();
        arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text.trim()
        );
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
    }

    public static boolean scrollUp() {
        return instance != null && instance.performScroll(true);
    }

    public static boolean scrollDown() {
        return instance != null && instance.performScroll(false);
    }

    private static boolean performClick(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        while (current != null) {
            if (current.isClickable()) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            current = current.getParent();
        }
        return false;
    }

    private static AccessibilityNodeInfo findEditableNode(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }
        if (node.isEditable()) {
            return node;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findEditableNode(node.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private boolean performScroll(boolean up) {
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;

        float startX = width / 2f;
        float startY = up ? height * 0.35f : height * 0.75f;
        float endY = up ? height * 0.75f : height * 0.35f;

        Path path = new Path();
        path.moveTo(startX, startY);
        path.lineTo(startX, endY);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 260))
                .build();
        return dispatchGesture(gesture, null, null);
    }
}
