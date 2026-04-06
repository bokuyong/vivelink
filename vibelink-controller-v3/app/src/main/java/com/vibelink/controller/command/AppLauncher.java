package com.vibelink.controller.command;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AppLauncher {

    private final Context context;
    private final PackageManager packageManager;

    public AppLauncher(Context context) {
        this.context = context;
        this.packageManager = context.getPackageManager();
    }

    public CommandResult launch(String appName) {
        ResolvedApp resolvedApp = resolve(appName);
        if (resolvedApp == null) {
            return CommandResult.failure("OPEN_APP", "앱을 찾지 못했습니다: " + appName);
        }

        Intent launchIntent = packageManager.getLaunchIntentForPackage(resolvedApp.packageName);
        if (launchIntent == null) {
            return CommandResult.failure("OPEN_APP", "실행 인텐트를 찾지 못했습니다: " + resolvedApp.label);
        }

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(launchIntent);
        return CommandResult.success("OPEN_APP", resolvedApp.label + " 실행");
    }

    private ResolvedApp resolve(String appName) {
        String normalizedQuery = normalize(appName);
        if (normalizedQuery.isEmpty()) {
            return null;
        }

        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> apps = packageManager.queryIntentActivities(intent, 0);
        List<ResolvedApp> candidates = new ArrayList<>();
        for (ResolveInfo info : apps) {
            String label = info.loadLabel(packageManager).toString();
            String packageName = info.activityInfo.packageName;
            candidates.add(new ResolvedApp(label, packageName));
        }

        ResolvedApp best = null;
        int bestScore = -1;
        for (ResolvedApp candidate : candidates) {
            String normalizedLabel = normalize(candidate.label);
            String normalizedPackage = normalize(candidate.packageName);
            int score = 0;
            if (normalizedLabel.equals(normalizedQuery)) {
                score = 100;
            } else if (normalizedLabel.contains(normalizedQuery)) {
                score = 90;
            } else if (normalizedQuery.contains(normalizedLabel)) {
                score = 80;
            } else if (normalizedPackage.contains(normalizedQuery)) {
                score = 70;
            }

            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        return bestScore >= 70 ? best : null;
    }

    private String normalize(String value) {
        return value == null ? "" : value
                .toLowerCase(Locale.KOREAN)
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "");
    }

    private static class ResolvedApp {
        private final String label;
        private final String packageName;

        private ResolvedApp(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }
    }
}
