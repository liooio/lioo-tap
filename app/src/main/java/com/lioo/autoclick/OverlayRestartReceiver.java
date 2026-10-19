package com.lioo.autoclick;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/** 在设备重启或进程被回收后恢复用户已启用的前台悬浮窗。 */
public final class OverlayRestartReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        boolean supportedAction = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || "com.lioo.autoclick.RESTART_OVERLAY".equals(action);
        if (!supportedAction
                || !ClickSettings.shouldShowOverlay(context)
                || !ClickAccessibilityService.isEnabled(context)
                || !Settings.canDrawOverlays(context)) {
            return;
        }
        OverlayService.show(context);
    }
}
