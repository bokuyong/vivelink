package com.vibelink.sender.service;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TossAccessibilityService extends AccessibilityService {

    private static TossAccessibilityService instance;

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

    public static boolean goHome() {
        return instance != null && instance.performGlobalAction(GLOBAL_ACTION_HOME);
    }

    public static boolean hasText(String query) {
        if (instance == null) {
            return false;
        }
        AccessibilityNodeInfo root = instance.getRootInActiveWindow();
        if (root == null) {
            return false;
        }
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(query);
        return nodes != null && !nodes.isEmpty();
    }

    public static boolean hasAnyText(String... queries) {
        for (String query : queries) {
            if (hasText(query)) {
                return true;
            }
        }
        return false;
    }

    // 검색 결과가 여러 개면 항상 화면에서 가장 위에 있는 후보를 누른다.
    public static boolean clickTopMostMatchingNode(String query, boolean exact, boolean skipEditable) {
        return clickMatchingNode(query, exact, skipEditable, false);
    }

    public static boolean clickBottomMostMatchingNode(String query, boolean exact, boolean skipEditable) {
        return clickMatchingNode(query, exact, skipEditable, true);
    }

    private static boolean clickMatchingNode(String query, boolean exact, boolean skipEditable, boolean preferBottom) {
        if (instance == null || query == null || query.trim().isEmpty()) {
            return false;
        }

        AccessibilityNodeInfo root = instance.getRootInActiveWindow();
        if (root == null) {
            return false;
        }

        List<AccessibilityNodeInfo> foundNodes = root.findAccessibilityNodeInfosByText(query.trim());
        if (foundNodes == null || foundNodes.isEmpty()) {
            return false;
        }

        List<CandidateNode> candidates = new ArrayList<>();
        for (AccessibilityNodeInfo node : foundNodes) {
            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();
            String merged = (text != null ? text.toString() : "") + " " + (desc != null ? desc.toString() : "");

            if (exact && !merged.contains(query.trim())) {
                continue;
            }
            if (skipEditable && node.isEditable()) {
                continue;
            }

            AccessibilityNodeInfo clickable = findClickableNode(node);
            AccessibilityNodeInfo target = clickable != null ? clickable : node;
            Rect bounds = new Rect();
            target.getBoundsInScreen(bounds);

            if (bounds.height() <= 0 || bounds.width() <= 0) {
                continue;
            }
            candidates.add(new CandidateNode(target, bounds));
        }

        Comparator<CandidateNode> comparator = Comparator
                .comparingInt((CandidateNode candidate) -> candidate.bounds.top)
                .thenComparingInt(candidate -> candidate.bounds.left);
        if (preferBottom) {
            comparator = comparator.reversed();
        }
        candidates.sort(comparator);

        for (CandidateNode candidate : candidates) {
            if (candidate.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true;
            }
        }
        return false;
    }

    public static boolean focusFirstEditable() {
        if (instance == null) {
            return false;
        }
        AccessibilityNodeInfo root = instance.getRootInActiveWindow();
        AccessibilityNodeInfo editable = findFirstEditable(root);
        if (editable == null) {
            return false;
        }
        editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        return editable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    public static boolean setTextOnFirstEditable(String text) {
        if (instance == null || text == null || text.trim().isEmpty()) {
            return false;
        }
        AccessibilityNodeInfo root = instance.getRootInActiveWindow();
        AccessibilityNodeInfo editable = findFirstEditable(root);
        if (editable == null) {
            return false;
        }
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text.trim());
        return editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    public static boolean inputAmount(int amount) {
        if (instance == null || amount <= 0) {
            return false;
        }
        String digits = String.valueOf(amount);
        for (char digit : digits.toCharArray()) {
            if (!clickBottomMostMatchingNode(String.valueOf(digit), true, true)) {
                return false;
            }
            sleep(180);
        }
        return true;
    }

    private static AccessibilityNodeInfo findClickableNode(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        while (current != null) {
            if (current.isClickable()) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private static AccessibilityNodeInfo findFirstEditable(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }
        if (node.isEditable()) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo editable = findFirstEditable(node.getChild(i));
            if (editable != null) {
                return editable;
            }
        }
        return null;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class CandidateNode {
        private final AccessibilityNodeInfo node;
        private final Rect bounds;

        private CandidateNode(AccessibilityNodeInfo node, Rect bounds) {
            this.node = node;
            this.bounds = bounds;
        }
    }
}
