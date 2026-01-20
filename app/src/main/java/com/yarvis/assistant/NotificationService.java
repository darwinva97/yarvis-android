package com.yarvis.assistant;

import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

public class NotificationService extends NotificationListenerService {

    private static final String TAG = "NotificationService";

    public static final String ACTION_NOTIFICATION_RECEIVED = "com.yarvis.assistant.NOTIFICATION_RECEIVED";
    public static final String EXTRA_NOTIFICATION_TITLE = "title";
    public static final String EXTRA_NOTIFICATION_TEXT = "text";
    public static final String EXTRA_NOTIFICATION_APP = "app";

    private static final String[] IGNORED_PACKAGES = {
            "com.yarvis.assistant",
            "com.android.systemui",
            "android"
    };

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        String packageName = sbn.getPackageName();

        for (String ignored : IGNORED_PACKAGES) {
            if (ignored.equals(packageName)) {
                return;
            }
        }

        Notification notification = sbn.getNotification();
        if (notification == null) return;

        Bundle extras = notification.extras;
        if (extras == null) return;

        String title = "";
        String text = "";

        CharSequence titleSeq = extras.getCharSequence(Notification.EXTRA_TITLE);
        if (titleSeq != null) {
            title = titleSeq.toString();
        }

        CharSequence textSeq = extras.getCharSequence(Notification.EXTRA_TEXT);
        if (textSeq != null) {
            text = textSeq.toString();
        }

        if (title.isEmpty() && text.isEmpty()) {
            return;
        }

        String appName = getAppName(packageName);

        Log.d(TAG, "Notification from " + appName + ": " + title + " - " + text);

        Intent intent = new Intent(ACTION_NOTIFICATION_RECEIVED);
        intent.putExtra(EXTRA_NOTIFICATION_APP, appName);
        intent.putExtra(EXTRA_NOTIFICATION_TITLE, title);
        intent.putExtra(EXTRA_NOTIFICATION_TEXT, text);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
    }

    private String getAppName(String packageName) {
        try {
            return getPackageManager()
                    .getApplicationLabel(
                            getPackageManager().getApplicationInfo(packageName, 0))
                    .toString();
        } catch (Exception e) {
            String[] parts = packageName.split("\\.");
            return parts[parts.length - 1];
        }
    }
}
