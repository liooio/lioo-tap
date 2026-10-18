package com.lioo.autoclick;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;

final class BackgroundAccess {
    private BackgroundAccess() {
    }

    static boolean isProtected(Context context) {
        PowerManager manager = context.getSystemService(PowerManager.class);
        return manager != null
                && manager.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    static void request(Activity activity) {
        Intent request = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + activity.getPackageName()));
        activity.startActivity(request);
    }
}
