package com.lioo.autoclick;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

final class OverlayAccess {
    private OverlayAccess() {
    }

    static boolean hasPermission(Activity activity) {
        return Settings.canDrawOverlays(activity);
    }

    static void requestPermission(Activity activity) {
        Toast.makeText(activity, "请允许连点器显示在其他应用上层", Toast.LENGTH_LONG).show();
        // Android 11+ may show the app list even when the package URI is supplied.
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + activity.getPackageName()));
        activity.startActivity(intent);
    }
}
