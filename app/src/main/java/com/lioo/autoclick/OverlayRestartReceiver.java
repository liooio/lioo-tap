package com.lioo.autoclick;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/** Restores the user-enabled foreground overlay after reboot or process reclamation. */
public final class OverlayRestartReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ClickSettings.shouldShowOverlay(context)
                || !ClickAccessibilityService.isEnabled(context)
                || !Settings.canDrawOverlays(context)) {
            return;
        }
        OverlayService.show(context);
    }
}
