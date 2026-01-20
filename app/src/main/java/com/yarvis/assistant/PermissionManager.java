package com.yarvis.assistant;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.activity.result.ActivityResultLauncher;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class PermissionManager {

    private final Context context;

    public PermissionManager(Context context) {
        this.context = context;
    }

    public boolean hasAudioPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    public boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public List<String> getMissingPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        if (!hasAudioPermission()) {
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
            permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        return permissionsNeeded;
    }

    public void requestMissingPermissions(ActivityResultLauncher<String[]> launcher) {
        List<String> missing = getMissingPermissions();
        if (!missing.isEmpty()) {
            launcher.launch(missing.toArray(new String[0]));
        }
    }

    public boolean hasAllRequiredPermissions() {
        return getMissingPermissions().isEmpty();
    }

    public boolean isNotificationAccessEnabled() {
        String pkgName = context.getPackageName();
        String flat = Settings.Secure.getString(context.getContentResolver(),
                "enabled_notification_listeners");
        if (!TextUtils.isEmpty(flat)) {
            String[] names = flat.split(":");
            for (String name : names) {
                ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null && TextUtils.equals(pkgName, cn.getPackageName())) {
                    return true;
                }
            }
        }
        return false;
    }

    public Intent getNotificationAccessSettingsIntent() {
        return new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
    }

    public boolean isBatteryOptimizationIgnored() {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            return pm.isIgnoringBatteryOptimizations(context.getPackageName());
        }
        return false;
    }

    public Intent getBatteryOptimizationExclusionIntent() {
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        return intent;
    }
}
