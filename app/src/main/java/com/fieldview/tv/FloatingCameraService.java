package com.fieldview.tv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.ResultReceiver;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import androidx.media3.common.util.UnstableApi;
import java.util.ArrayList;
import java.util.List;

/** A user-started, silent, single-substream window. No boot restart or exported controls. */
@UnstableApi
public class FloatingCameraService extends Service {
    public static final String ACTION_START = "com.fieldview.tv.FLOAT_START";
    public static final String ACTION_CLOSE = "com.fieldview.tv.FLOAT_CLOSE";
    private static final String ACTION_REMEMBER_DEFAULT = "com.fieldview.tv.FLOAT_DEFAULT";
    public static final String EXTRA_CAMERA = "camera_id", EXTRA_CALLBACK = "callback";
    public static final int STARTED = 1, FAILED = 0;
    private static final String CHANNEL = "floating-camera";
    private static final int NOTIFICATION_ID = 101;
    private static FloatingCameraService active;
    private WindowManager windows;
    private CameraTile tile;
    private boolean attached, closing;
    private AppConfig config;
    private FloatingViewController controller;
    private CommandRouter router;
    private MqttBridge bridge;
    private String currentCamera;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = this::pollSelection;
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { close("screen-off"); }
    };

    @Override public void onCreate() {
        super.onCreate(); active = this;
        androidx.media3.common.util.Log.setLogLevel(androidx.media3.common.util.Log.LOG_LEVEL_OFF);
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_CLOSE.equals(intent.getAction())) {
            close(intent == null ? "empty-restart" : "close-action"); return START_NOT_STICKY;
        }
        boolean restored = (flags & START_FLAG_REDELIVERY) != 0;
        // A restored request must not send an old Activity callback or wake a sleeping TV.
        ResultReceiver callback = restored ? null : intent.getParcelableExtra(EXTRA_CALLBACK);
        if (restored && !isScreenOn()) { close("restart-screen-off"); return START_NOT_STICKY; }
        try {
            String id = intent.getStringExtra(EXTRA_CAMERA);
            // Refresh Android's redelivery request without restarting a live session/player.
            if (ACTION_REMEMBER_DEFAULT.equals(intent.getAction()) && !restored) {
                if (closing || controller == null || !controller.contains(id)) throw new IllegalStateException();
                return START_REDELIVER_INTENT;
            }
            if (!(ACTION_START.equals(intent.getAction()) || (restored && ACTION_REMEMBER_DEFAULT.equals(intent.getAction()))) || !canDrawOverlays()) throw new IllegalStateException();
            AppConfig loaded = loadConfig();
            AppConfig.Camera selected = null;
            List<String> enabled = new ArrayList<>();
            for (AppConfig.Camera camera : loaded.cameras) if (camera.enabled) {
                enabled.add(camera.id); if (camera.id.equals(id)) selected = camera;
            }
            if (selected == null) throw new IllegalStateException();
            stopControl(); closing = false; config = loaded;
            controller = new FloatingViewController(enabled, id);
            router = new CommandRouter(controller::activity, (action, camera) -> {
                if ("close".equals(action)) { close("home-assistant"); return true; }
                if (!controller.selectDefault(camera)) return false;
                refreshFloatingView(); updateNotification();
                rememberDefault(camera);
                return true;
            });
            showForegroundNotification(selected.name);
            if (tile == null) {
                tile = new CameraTile(this, selected.name, true);
                tile.setId(R.id.floating_camera);
                tile.setFocusable(false); tile.setClickable(false);
                windows.addView(tile, windowLayout(getResources().getDisplayMetrics(), Build.VERSION.SDK_INT));
                attached = true;
            }
            tile.setSoftwareFloatingH264(config.softwareFloatingH264);
            currentCamera = null; refreshFloatingView(); startControl();
            handler.postDelayed(poll, 1000);
            Log.i("FieldviewOverlay", restored ? "Floating camera restored after process restart" : "Floating camera window started");
            if (callback != null) callback.send(STARTED, null);
            // Android retains only this private camera-ID request until stopSelf/stopService.
            // No boot receiver, credentials, or additional saved session state are needed.
            return START_REDELIVER_INTENT;
        } catch (Exception error) {
            Log.w("FieldviewOverlay", "Floating camera unavailable: " + error.getClass().getSimpleName());
            if (callback != null) callback.send(FAILED, null);
            close("start-failed");
        }
        return START_NOT_STICKY;
    }

    protected boolean canDrawOverlays() { return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this); }
    protected boolean isScreenOn() { return ((PowerManager) getSystemService(POWER_SERVICE)).isInteractive(); }
    protected AppConfig loadConfig() throws Exception { return new ConfigStore(this).load(); }
    protected void playStream(CameraTile view, String uri) { view.play(uri); }
    protected long now() { return System.currentTimeMillis(); }
    protected void startMqtt(AppConfig subscription, MqttBridge.Listener listener) {
        bridge = new MqttBridge(); bridge.start(subscription, listener);
    }
    protected void stopMqtt() { MqttBridge previous = bridge; bridge = null; if (previous != null) previous.stop(); }
    protected void rememberDefault(String camera) {
        startService(new Intent(this, FloatingCameraService.class).setAction(ACTION_REMEMBER_DEFAULT).putExtra(EXTRA_CAMERA, camera));
    }

    private void startControl() throws Exception {
        final FloatingViewController session = controller;
        AppConfig subscription = AppConfig.fromJson(config.toJson());
        // A separate role ID prevents Activity/service handover from evicting either connection.
        subscription.clientId += "-floating";
        startMqtt(subscription, new MqttBridge.Listener() {
            @Override public void status(String value) {
                handler.post(() -> { if (!closing && controller == session) Log.i("FieldviewOverlay", value); });
            }
            @Override public void message(String payload, boolean retained) {
                handler.post(() -> {
                    if (closing || controller != session || router == null) return;
                    if (router.accept(payload, retained, now()) && !closing) refreshFloatingView();
                });
            }
        });
    }

    private void stopControl() {
        handler.removeCallbacks(poll); router = null; controller = null; stopMqtt();
    }

    private void pollSelection() {
        if (closing || controller == null) return;
        refreshFloatingView(); handler.postDelayed(poll, 1000);
    }

    protected void refreshFloatingView() {
        if (closing || controller == null || tile == null) return;
        String next = controller.camera(now());
        if (next.equals(currentCamera)) return;
        AppConfig.Camera selected = null;
        for (AppConfig.Camera camera : config.cameras) if (camera.enabled && camera.id.equals(next)) selected = camera;
        if (selected == null) { close("camera-unavailable"); return; }
        currentCamera = next;
        tile.setContentDescription("Floating camera " + selected.name);
        // CameraTile disposes the old decoder first and keeps its existing SurfaceView.
        playStream(tile, selected.streamUri(false));
        updateNotification();
        Log.i("FieldviewOverlay", "Floating selection: " + next + (next.equals(controller.defaultCamera()) ? " (default)" : " (activity)"));
    }

    private void updateNotification() {
        if (config == null || currentCamera == null) return;
        for (AppConfig.Camera camera : config.cameras) if (camera.id.equals(currentCamera)) {
            showForegroundNotification(camera.name);
            return;
        }
    }

    private void showForegroundNotification(String name) {
        Notification notice = notification(name);
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(NOTIFICATION_ID, notice);
    }

    public static WindowManager.LayoutParams windowLayout(DisplayMetrics display, int sdk) {
        int margin = Math.max(8, Math.round(12 * display.density));
        int width = Math.min(Math.round(320 * display.density), display.widthPixels / 3);
        width = Math.max(1, Math.min(width, display.widthPixels - margin * 2));
        int height = Math.max(1, Math.min(width * 9 / 16, display.heightPixels - margin * 2));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(width, height,
                sdk >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM | Gravity.RIGHT; params.x = margin; params.y = margin;
        params.setTitle("Fieldview floating camera");
        return params;
    }

    private Notification notification(String name) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Floating camera", NotificationManager.IMPORTANCE_LOW));
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP), pendingFlags);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, FloatingCameraService.class).setAction(ACTION_CLOSE), pendingFlags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return builder.setSmallIcon(R.drawable.ic_camera_notification).setContentTitle("Floating camera · " + name)
                .setContentText("Return to Property Tracker to close this window.").setContentIntent(open).setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE).setVisibility(Notification.VISIBILITY_PRIVATE)
                .addAction(new Notification.Action.Builder(R.drawable.ic_camera_notification, "Close camera", stop).build()).build();
    }

    /** MainActivity calls on its main thread before starting wall players. */
    public static void closeActive(Context context) {
        if (active != null) active.close("return-to-viewer");
        // Also cancel a scheduled restart when Android has killed the service process.
        context.stopService(new Intent(context, FloatingCameraService.class));
    }

    private void removeCamera() {
        CameraTile previous = tile; tile = null;
        if (previous != null) {
            previous.release();
            if (attached) {
                attached = false;
                try { windows.removeViewImmediate(previous); } catch (IllegalArgumentException ignored) { }
            }
        }
    }

    private void close(String reason) {
        if (closing) return;
        closing = true; stopControl();
        Log.i("FieldviewOverlay", "Floating camera stopped: " + reason);
        removeCamera(); stopForeground(true); stopSelf();
    }

    @Override public void onTaskRemoved(Intent rootIntent) { close("task-removed"); }
    @Override public void onDestroy() {
        closing = true; stopControl(); config = null;
        removeCamera(); stopForeground(true); unregisterReceiver(screenOff);
        if (active == this) active = null;
        Log.i("FieldviewOverlay", "Floating camera window closed");
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
