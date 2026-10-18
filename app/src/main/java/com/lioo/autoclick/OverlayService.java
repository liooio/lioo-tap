package com.lioo.autoclick;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;

/** Owns the app overlay independently of the accessibility service used for gestures. */
public final class OverlayService extends Service {
    private static final String CHANNEL_ID = "floating_controls";
    private static final int NOTIFICATION_ID = 1;
    private static final String ACTION_PLAY_RECORDING = "com.lioo.autoclick.PLAY_RECORDING";
    private static final String EXTRA_BODY = "body";
    private static final String EXTRA_NAME = "name";
    private static OverlayService instance;

    private OverlayController overlay;
    private GestureRecordingOverlay recorder;
    private ScriptPlaybackOverlay playback;
    private boolean manualStop;

    static OverlayService getInstance() {
        return instance;
    }

    static boolean isVisible() {
        return instance != null && instance.overlay != null && instance.overlay.isVisible();
    }

    static void refreshShortcutKeys() {
        if (instance != null && instance.overlay != null) {
            instance.overlay.refreshShortcutKeys();
        }
    }

    static void show(Context context) {
        if (!ClickAccessibilityService.isEnabled(context)) {
            Toast.makeText(context, "请先开启连点器无障碍服务", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(context, OverlayService.class);
        context.startForegroundService(intent);
    }

    static void playRecording(Context context, String body, String name) {
        if (!ClickAccessibilityService.isEnabled(context)) {
            Toast.makeText(context, "请先开启连点器无障碍服务", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(context, OverlayService.class)
                .setAction(ACTION_PLAY_RECORDING)
                .putExtra(EXTRA_BODY, body)
                .putExtra(EXTRA_NAME, name);
        context.startForegroundService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        overlay = new OverlayController(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, createNotification());
        if (!ClickAccessibilityService.isEnabled(this)
                || !Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_PLAY_RECORDING.equals(intent == null ? null : intent.getAction())) {
            overlay.show();
            overlay.collapsePanel();
            if (playback != null) {
                playback.cancel();
            }
            playback = new ScriptPlaybackOverlay(this,
                    intent.getStringExtra(EXTRA_BODY),
                    intent.getStringExtra(EXTRA_NAME),
                    () -> {
                        playback = null;
                    });
            playback.start();
        } else {
            overlay.show();
        }
        ClickSettings.setShowOverlay(this, true);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        overlay.snapFloatingBallToEdge();
    }

    @Override
    public void onDestroy() {
        ClickAccessibilityService clickService = ClickAccessibilityService.getInstance();
        if (clickService != null) {
            clickService.stopClicking();
        }
        if (recorder != null) {
            recorder.cancel();
            recorder = null;
        }
        if (playback != null) {
            ScriptPlaybackOverlay activePlayback = playback;
            playback = null;
            activePlayback.cancel();
        }
        overlay.hide();
        instance = null;
        if (manualStop) {
            ClickSettings.setShowOverlay(this, false);
        } else if (ClickSettings.shouldShowOverlay(this)
                && ClickAccessibilityService.isEnabled(this)
                && Settings.canDrawOverlays(this)) {
            scheduleRestart();
        }
        super.onDestroy();
    }

    void hideOverlay() {
        manualStop = true;
        ClickSettings.setShowOverlay(this, false);
        stopSelf();
    }

    void suspendForMissingPermission() {
        manualStop = false;
        stopSelf();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (ClickSettings.shouldShowOverlay(this)) {
            scheduleRestart();
        }
        super.onTaskRemoved(rootIntent);
    }

    void startRecording() {
        if (recorder != null) {
            return;
        }
        ClickAccessibilityService accessibility = ClickAccessibilityService.getInstance();
        if (accessibility == null) {
            Toast.makeText(this, "请先开启连点器无障碍服务", Toast.LENGTH_LONG).show();
            return;
        }
        accessibility.stopClicking();
        overlay.collapsePanel();
        recorder = new GestureRecordingOverlay(this, accessibility,
                (steps, saveRecording) -> {
                    recorder = null;
                    if (saveRecording) {
                        Intent save = new Intent(this, RecordingSaveActivity.class)
                                .putExtra(RecordingSaveActivity.EXTRA_STEPS, steps.toString())
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            PendingIntent.getActivity(this, 41, save,
                                    PendingIntent.FLAG_UPDATE_CURRENT
                                            | PendingIntent.FLAG_IMMUTABLE).send();
                        } catch (PendingIntent.CanceledException exception) {
                            Toast.makeText(this, "无法打开保存页面", Toast.LENGTH_LONG).show();
                        }
                    }
                });
        recorder.start();
    }

    void runShortcut(Library.Entry shortcut) {
        Library.Entry target = ShortcutKey.resolveTarget(this, shortcut);
        if (target == null) {
            Toast.makeText(this, "快捷键尚未绑定有效脚本", Toast.LENGTH_SHORT).show();
            return;
        }
        runEntry(target);
    }

    void runEntry(Library.Entry target) {
        JSONArray plan = ExecutionPlan.from(this, target);
        if (plan.length() == 0) {
            Toast.makeText(this, "脚本没有可执行步骤", Toast.LENGTH_SHORT).show();
            return;
        }
        overlay.collapsePanel();
        if (playback != null) {
            playback.cancel();
        }
        playback = new ScriptPlaybackOverlay(this, plan.toString(), target.name, true, () -> {
            playback = null;
        });
        playback.start();
    }

    void toggleClicking() {
        ClickAccessibilityService clickService = ClickAccessibilityService.getInstance();
        if (clickService == null) {
            Toast.makeText(this, "点击前请开启连点器无障碍服务", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        }
        if (!clickService.isRunning() && !overlay.saveClickSettings()) {
            return;
        }
        overlay.hideClickEditor();
        clickService.toggleClicking();
    }

    boolean isClicking() {
        ClickAccessibilityService clickService = ClickAccessibilityService.getInstance();
        return clickService != null && clickService.isRunning();
    }

    void stopClicking() {
        ClickAccessibilityService clickService = ClickAccessibilityService.getInstance();
        if (clickService != null) {
            clickService.stopClicking();
        }
    }

    boolean shouldIgnoreOutsideTouch() {
        ClickAccessibilityService clickService = ClickAccessibilityService.getInstance();
        return clickService != null && clickService.shouldIgnoreOutsideTouch();
    }

    boolean hasTarget() {
        return overlay != null && overlay.hasTarget();
    }

    int[] getTargetCenter() {
        return overlay.getTargetCenter();
    }

    void setRunning(boolean running) {
        overlay.setRunning(running);
    }

    void setClickProgress(int completed, int total) {
        overlay.setClickProgress(completed, total);
    }

    void showClickFeedback() {
        overlay.showClickFeedback();
    }

    private void scheduleRestart() {
        Intent restart = new Intent(this, OverlayRestartReceiver.class)
                .setAction("com.lioo.autoclick.RESTART_OVERLAY");
        PendingIntent pending = PendingIntent.getBroadcast(this, 73, restart,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarms = getSystemService(AlarmManager.class);
        if (alarms != null) {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 3000L, pending);
        }
    }

    private Notification createNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "悬浮控制",
                NotificationManager.IMPORTANCE_LOW);
        manager.createNotificationChannel(channel);
        PendingIntent openApp = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID);
        return builder.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("连点器悬浮控制")
                .setContentText("悬浮窗运行中，点击返回应用")
                .setContentIntent(openApp)
                .setOngoing(true)
                .build();
    }
}
