package com.fieldview.tv;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.PopupMenu;
import androidx.media3.common.util.UnstableApi;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@UnstableApi
public final class MainActivity extends Activity {
    public static final String EXTRA_SETUP_ONLY = "com.fieldview.tv.SETUP_ONLY";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StartupCoordinator startupCoordinator = StartupCoordinator.shared();
    private final Map<String, CameraTile> tiles = new LinkedHashMap<>();
    private final CenterStreamBackoff centerStreamBackoff = new CenterStreamBackoff();
    private ConfigStore store;
    private AppConfig config;
    private List<AppConfig.Camera> enabledCameras = new ArrayList<>();
    private PlaybackLimits playbackLimits;
    private ViewSelector selector;
    private CommandRouter router;
    private CameraWall wall;
    private TextView footer, empty;
    private LinearLayout root, toolbar;
    private LinearLayout phoneControls;
    private Button phonePrevious, phoneNext;
    private final Runnable hidePhoneControls = () -> { if (phoneControls != null) phoneControls.setVisibility(View.GONE); };
    private Button autoButton, gridButton, layoutButton;
    private boolean foreground;
    private boolean fullscreen;
    private boolean layoutFullscreen;
    private String modeBeforePin = ViewSelector.AUTO;
    private boolean floatingTransition;
    private boolean setupOnly;
    private String pendingFloatingCamera;
    private String pendingNotificationCamera;
    private static final int OVERLAY_PERMISSION_REQUEST = 41;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 42;
    private String signature = "", mqttState = "Home Assistant control not configured";
    private MqttBridge bridge;
    private CommandInbox inbox;
    private long layoutOverrideUntil;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!foreground) return;
            if (layoutOverrideUntil != 0 && System.currentTimeMillis() >= layoutOverrideUntil) {
                layoutOverrideUntil = 0; selector.setCapacity(config.visibleCount);
            }
            render(); handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        layoutFullscreen = state == null ? BuildConfig.TV_INTERFACE : state.getBoolean("layoutFullscreen", BuildConfig.TV_INTERFACE);
        setupOnly = getIntent().getBooleanExtra(EXTRA_SETUP_ONLY, false);
        if (state != null) { setupOnly = state.getBoolean("setupOnly", setupOnly); pendingFloatingCamera = state.getString("pendingFloatingCamera"); pendingNotificationCamera = state.getString("pendingNotificationCamera"); }
        if (!BuildConfig.TV_INTERFACE) getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        androidx.media3.common.util.Log.setLogLevel(androidx.media3.common.util.Log.LOG_LEVEL_OFF);
        store = new ConfigStore(this);
        try { config = store.load(); }
        catch (Exception ignored) {
            new AlertDialog.Builder(this).setTitle("Saved settings unavailable")
                    .setMessage("Android could not open this app's saved settings. Close the app and try again. If the device's encryption key was reset, clear Property Tracker's app data in Android settings and configure it again.")
                    .setPositiveButton("Close", (dialog, which) -> finish()).setCancelable(false).show();
            return;
        }
        if (config.enabledCameras().isEmpty()) layoutFullscreen = false;
        playbackLimits = limitsFor(config.textureGrid, config.denseGrid);
        // ViewSelector enforces the effective budget without overwriting the requested layout.
        root = new LinearLayout(this); root.setId(R.id.viewer_root); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF101516); root.setPadding(dp(20), dp(14), dp(20), dp(10));
        toolbar = new LinearLayout(this); toolbar.setId(R.id.viewer_toolbar); toolbar.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = label(getString(R.string.app_name), 22); brand.setTextColor(0xFFCBE7AB);
        brand.setSingleLine(true);
        toolbar.addView(brand, new LinearLayout.LayoutParams(0, dp(48), 1));
        autoButton = button("Auto", () -> { setupOnly = false; selector.setMode(ViewSelector.AUTO, null); render(); }); toolbar.addView(autoButton);
        gridButton = button("Grid", () -> { setupOnly = false; selector.setMode(ViewSelector.GRID, null); render(); }); toolbar.addView(gridButton);
        layoutButton = button("Layout", this::showLayout); toolbar.addView(layoutButton);
        toolbar.addView(button("View", this::showCameraPicker));
        if (BuildConfig.TV_INTERFACE) {
            toolbar.addView(button("Float", this::showFloatingCameraPicker));
            toolbar.addView(button("Cameras", this::showCameras));
            toolbar.addView(button("Home Assistant", this::showMqtt));
            toolbar.addView(button("About", this::showLicenses));
        } else {
            toolbar.removeView(layoutButton);
            brand.setVisibility(View.GONE);
            Button more = button("More", () -> showPhoneMenu(toolbar)); more.setId(R.id.phone_more); toolbar.addView(more);
            for (int i = 1; i < toolbar.getChildCount(); i++) toolbar.getChildAt(i).setLayoutParams(new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(58)));
        FrameLayout content = new FrameLayout(this);
        wall = new CameraWall(this); wall.setId(R.id.camera_wall); content.addView(wall, new FrameLayout.LayoutParams(-1, -1));
        empty = label("", 19);
        empty.setGravity(Gravity.CENTER); content.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        if (!BuildConfig.TV_INTERFACE) {
            phoneControls = new LinearLayout(this); phoneControls.setId(R.id.phone_controls); phoneControls.setGravity(Gravity.CENTER);
            phoneControls.setBackgroundColor(0xCC101516); phoneControls.setVisibility(View.GONE);
            phoneControls.addView(button("Back", () -> { onBackPressed(); showPhoneControls(); }), new LinearLayout.LayoutParams(0, dp(48), 1));
            phonePrevious = button("Previous", () -> stepPhone(-1)); phonePrevious.setId(R.id.phone_previous);
            phoneNext = button("Next", () -> stepPhone(1)); phoneNext.setId(R.id.phone_next);
            phoneControls.addView(phonePrevious, new LinearLayout.LayoutParams(0, dp(48), 1));
            phoneControls.addView(phoneNext, new LinearLayout.LayoutParams(0, dp(48), 1));
            phoneControls.addView(button("More", () -> showPhoneMenu(phoneControls)), new LinearLayout.LayoutParams(0, dp(48), 1));
            content.addView(phoneControls, new FrameLayout.LayoutParams(-1, dp(52), Gravity.BOTTOM));
        }
        footer = label("", 12); footer.setTextColor(0xFFAFBEAF); footer.setPadding(dp(4), dp(8), dp(4), 0); footer.setMaxLines(2);
        footer.setId(R.id.viewer_footer);
        root.addView(footer, new LinearLayout.LayoutParams(-1, dp(40)));
        setContentView(root);
        if (!BuildConfig.TV_INTERFACE) {
            PhoneInsets.install(root, () -> fullscreen, dp(8), dp(4));
            if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> { if (!handleBack()) finish(); });
        }
        resetSelector();
        if (state != null) {
            String mode = state.getString("viewerMode", ViewSelector.AUTO);
            selector.setMode(mode, state.getString("pinnedCamera"));
            modeBeforePin = state.getString("modeBeforePin", ViewSelector.AUTO);
        }
    }

    @Override protected void onStart() {
        super.onStart();
        if (config == null) return;
        FloatingCameraService.closeActive(this);
        floatingTransition = false;
        foreground = true; signature = ""; handler.post(tick); startBridge();
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        if (intent.getBooleanExtra(EXTRA_SETUP_ONLY, false)) {
            setupOnly = true; releaseTiles(); signature = ""; render();
        }
    }
    @Override protected void onStop() {
        handler.removeCallbacks(hidePhoneControls);
        foreground = false; handler.removeCallbacks(tick); stopBridge(); releaseTiles(); signature = "";
        super.onStop();
    }
    @Override public void onBackPressed() {
        if (!handleBack()) super.onBackPressed();
    }
    private boolean handleBack() {
        if (selector != null && ViewSelector.PIN.equals(selector.snapshot(System.currentTimeMillis()).mode)) {
            selector.setMode(modeBeforePin, null); render();
        } else if (layoutFullscreen) {
            layoutFullscreen = false; render(); layoutButton.requestFocus();
        } else return false;
        return true;
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("layoutFullscreen", layoutFullscreen);
        state.putBoolean("setupOnly", setupOnly); state.putString("pendingFloatingCamera", pendingFloatingCamera);
        state.putString("pendingNotificationCamera", pendingNotificationCamera);
        if (selector != null) {
            ViewSelector.Selection selection = selector.snapshot(System.currentTimeMillis());
            state.putString("viewerMode", selection.mode); state.putString("modeBeforePin", modeBeforePin);
            if (ViewSelector.PIN.equals(selection.mode) && !selection.visible.isEmpty()) state.putString("pinnedCamera", selection.visible.get(0));
        }
        super.onSaveInstanceState(state);
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applySystemUi();
    }

    private void applySystemUi() {
        if (!BuildConfig.TV_INTERFACE && Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController bars = getWindow().getInsetsController();
            if (bars != null) {
                bars.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                if (fullscreen) bars.hide(android.view.WindowInsets.Type.systemBars()); else bars.show(android.view.WindowInsets.Type.systemBars());
            }
            return;
        }
        getWindow().getDecorView().setSystemUiVisibility(fullscreen
                ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                : View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void setFullscreen(boolean value) {
        if (fullscreen == value) return;
        fullscreen = value;
        toolbar.setVisibility(value ? View.GONE : View.VISIBLE);
        footer.setVisibility(value ? View.GONE : View.VISIBLE);
        root.setPadding(value ? 0 : dp(20), value ? 0 : dp(14), value ? 0 : dp(20), value ? 0 : dp(10));
        wall.setFullscreen(value);
        applySystemUi();
        if (!BuildConfig.TV_INTERFACE) { root.requestApplyInsets(); showPhoneControls(); }
    }
    private void showPhoneControls() {
        if (phoneControls == null) return;
        handler.removeCallbacks(hidePhoneControls);
        phoneControls.setVisibility(fullscreen ? View.VISIBLE : View.GONE);
        if (fullscreen) handler.postDelayed(hidePhoneControls, 5000);
    }
    private void stepPhone(int direction) {
        if (selector != null && selector.stepPinnedCamera(direction)) render();
        showPhoneControls();
    }
    private void showPhoneMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        String[] labels = {"Cameras", "Layout", "Float camera", "Home Assistant", "Privacy policy", "Open-source licenses"};
        for (int i = 0; i < labels.length; i++) menu.getMenu().add(0, i, i, labels[i]);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0: showCameras(); break;
                case 1: showLayout(); break;
                case 2: showFloatingCameraPicker(); break;
                case 3: showMqtt(); break;
                case 4: showPrivacy(); break;
                case 5: showLicenses(); break;
            }
            return true;
        }); menu.show();
    }
    private void showPrivacy() {
        showTextDocument("Privacy policy", "privacy.txt");
    }
    private void showLicenses() { showTextDocument("Open-source licenses", "third-party-notices.txt"); }
    private void showTextDocument(String title, String asset) {
        try (java.io.InputStream input = getAssets().open(asset)) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            TextView policy = label(new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8), 15);
            policy.setPadding(dp(16), dp(12), dp(16), dp(12)); policy.setTextIsSelectable(true);
            ScrollView scroll = new ScrollView(this); scroll.addView(policy);
            new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("Close", null).show();
        } catch (java.io.IOException error) { toast("Document could not be opened."); }
    }
    @Override public boolean onKeyDown(int code, KeyEvent event) {
        if (code == KeyEvent.KEYCODE_MENU && config != null) { showLayout(); return true; }
        if (code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || code == KeyEvent.KEYCODE_MEDIA_PAUSE || code == KeyEvent.KEYCODE_MEDIA_PLAY) return true;
        return super.onKeyDown(code, event);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (foreground && fullscreen && hasWindowFocus() && selector != null
                && ViewSelector.PIN.equals(selector.snapshot(System.currentTimeMillis()).mode)
                && (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN)) {
            // One switch per press; holding a direction must not repeatedly restart decoders.
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0
                    && selector.stepPinnedCamera(code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1)) render();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void resetSelector() {
        enabledCameras = config.enabledCameras();
        List<String> ids = new ArrayList<>(); Map<String, List<String>> zones = new LinkedHashMap<>();
        for (AppConfig.Camera camera : enabledCameras) {
            ids.add(camera.id);
            if (!camera.zone.isEmpty()) zones.computeIfAbsent(camera.zone, ignored -> new ArrayList<>()).add(camera.id);
        }
        if (selector == null || selector.maxCapacity() != playbackLimits.maxVisible) selector = new ViewSelector(ids, zones, config.visibleCount, playbackLimits.maxVisible);
        else { selector.reconfigure(ids, zones); if (layoutOverrideUntil == 0) selector.setCapacity(config.visibleCount); }
        selector.setDefaultCamera(config.spotlightCamera);
        selector.setMotionLayoutEight(config.motionLayoutEight && BuildConfig.TV_INTERFACE && Build.VERSION.SDK_INT >= 26
                && config.denseGrid && config.textureGrid && playbackLimits.maxVisible == 13);
        router = new CommandRouter(selector, (count, until) -> { layoutOverrideUntil = until; selector.setCapacity(count); });
        signature = "";
    }

    private void render() {
        if (!foreground || floatingTransition || selector == null) return;
        if (setupOnly) {
            String prompt = "Playback paused for setup.\n\nChoose Auto, Grid, View, or Float to start video.";
            setFullscreen(false); empty.setVisibility(View.VISIBLE);
            if (!prompt.contentEquals(empty.getText())) empty.setText(prompt);
            return;
        }
        ViewSelector.Selection selection = selector.snapshot(System.currentTimeMillis());
        boolean pinned = ViewSelector.PIN.equals(selection.mode);
        if (phoneControls != null) {
            phonePrevious.setVisibility(pinned ? View.VISIBLE : View.GONE); phoneNext.setVisibility(pinned ? View.VISIBLE : View.GONE);
        }
        if (!pinned) modeBeforePin = selection.mode;
        setFullscreen(pinned || layoutFullscreen);
        empty.setVisibility(enabledCameras.isEmpty() ? View.VISIBLE : View.GONE);
        if (enabledCameras.isEmpty()) {
            String prompt = config.cameras.isEmpty()
                    ? "Add a camera to start.\n\nOpen Cameras and enter its local address and login.\nH.264 and H.265/HEVC streams are supported within this device's decoding limits."
                    : "No cameras enabled.\n\nOpen Cameras, select a camera, and choose Enable camera.";
            if (!prompt.contentEquals(empty.getText())) empty.setText(prompt);
        }
        String autoLabel = ViewSelector.AUTO.equals(selection.mode) ? "● Auto" : "Auto";
        String gridLabel = ViewSelector.GRID.equals(selection.mode) ? "● Grid" : "Grid";
        if (!autoLabel.contentEquals(autoButton.getText())) autoButton.setText(autoLabel);
        if (!gridLabel.contentEquals(gridButton.getText())) gridButton.setText(gridLabel);
        String status = config.site + " / " + config.display + "  ·  " + selection.reason + "  ·  "
                + selection.visible.size() + "/" + config.cameras.size() + " cameras  ·  " + mqttState;
        int disabled = config.cameras.size() - enabledCameras.size();
        if (disabled > 0) status += "  ·  " + disabled + " disabled";
        if (layoutOverrideUntil != 0) status += "  ·  temporary layout";
        if (!status.contentEquals(footer.getText())) footer.setText(status);
        boolean textureGrid = config.textureGrid && selection.visible.size() > 1;
        boolean nativeGrid = BuildConfig.TV_INTERFACE && Build.VERSION.SDK_INT >= 26
                && config.denseGrid && textureGrid && playbackLimits.maxVisible == 13;
        java.util.Set<String> unavailable = new java.util.HashSet<>();
        java.util.Set<String> unsupported = new java.util.HashSet<>();
        if (nativeGrid && (config.highResolutionCenter || config.motionLayoutEight)) for (String id : selection.visible) {
            long now = android.os.SystemClock.elapsedRealtime(); AppConfig.Camera camera = camera(id);
            if (centerStreamBackoff.blocked(camera, now)) {
                unavailable.add(id); if (centerStreamBackoff.formatUnsupported(camera, now)) unsupported.add(id);
            }
        }
        WallStreamPlan streams = new WallStreamPlan(selection, nativeGrid, config.useMainStream, config.highResolutionCenter, unavailable, config.motionLayoutEight);
        String nextSignature = selection.reason + selection.visible + selection.featured + config.useMainStream + fullscreen + textureGrid + config.denseGrid + config.highResolutionCenter + config.motionLayoutEight + selection.motionLayoutEight;
        if (signature.equals(nextSignature)) return;
        signature = nextSignature;
        android.util.Log.i("FieldviewLayout", "mode=" + selection.mode + " visible=" + selection.visible.size()
                + " reason=" + selection.reason + " motion_eight=" + selection.motionLayoutEight);
        String previouslyFocused = null;
        for (Map.Entry<String, CameraTile> entry : tiles.entrySet()) if (entry.getValue().hasFocus()) previouslyFocused = entry.getKey();
        Iterator<Map.Entry<String, CameraTile>> iterator = tiles.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CameraTile> entry = iterator.next();
            if (!selection.visible.contains(entry.getKey()) || entry.getValue().usesTexture() != (textureGrid && !streams.highResolutionCenter(camera(entry.getKey())))
                    || entry.getValue().usesNativeGrid() != streams.nativeGrid(camera(entry.getKey()))) {
                entry.getValue().release(); wall.removeView(entry.getValue()); iterator.remove();
            }
        }
        List<View> ordered = new ArrayList<>();
        // Demote existing featured feeds before starting any new high-resolution streams.
        for (Map.Entry<String, CameraTile> entry : tiles.entrySet()) {
            if (!streams.main(camera(entry.getKey())))
                entry.getValue().play(camera(entry.getKey()).streamUri(false), false);
        }
        for (String id : selection.visible) {
            AppConfig.Camera camera = camera(id);
            CameraTile tile = tiles.get(id);
            if (tile == null) {
                tile = new CameraTile(this, camera.name, false, textureGrid && !streams.highResolutionCenter(camera), streams.nativeGrid(camera));
                tile.configureStartup(startupCoordinator, id);
                tile.setOnClickListener(view -> {
                    if (!BuildConfig.TV_INTERFACE && ViewSelector.PIN.equals(selector.snapshot(System.currentTimeMillis()).mode)) showPhoneControls();
                    else { selector.setMode(ViewSelector.PIN, id); render(); }
                });
                tiles.put(id, tile); wall.addView(tile);
            }
            tile.setContentDescription("Enlarge " + camera.name);
            boolean featured = selection.featured.contains(id);
            tile.setFullscreen(fullscreen, !pinned && selection.visible.size() > 1);
            tile.setFeatured(featured || streams.highResolutionCenter(camera));
            tile.setMainSuppressed(streams.mainUnavailable(camera), unsupported.contains(id));
            CameraTile requestTile = tile;
            tile.setMainUnavailableListener(streams.highResolutionCenter(camera) ? () -> {
                centerStreamBackoff.failed(camera, android.os.SystemClock.elapsedRealtime(), requestTile.mainFormatRejected());
                handler.post(() -> { signature = ""; render(); });
            } : null);
            boolean main = streams.main(camera);
            String stream = camera.streamUri(main);
            if (streams.highResolutionCenter(camera)) tile.playMainOrGrid(stream, camera.streamUri(false));
            else tile.play(stream, !stream.equals(camera.streamUri(false)));
            ordered.add(tile);
        }
        wall.setOrder(ordered); wall.setFeaturedCount(selection.featured.size());
        if (previouslyFocused != null && !ordered.isEmpty()) {
            CameraTile old = tiles.get(previouslyFocused);
            (old == null ? ordered.get(0) : old).requestFocus();
        } else if (fullscreen && !ordered.isEmpty()) ordered.get(0).requestFocus();
    }

    private AppConfig.Camera camera(String id) {
        for (AppConfig.Camera camera : config.cameras) if (camera.id.equals(id)) return camera;
        throw new IllegalStateException("Unknown camera");
    }
    private void releaseTiles() {
        for (CameraTile tile : tiles.values()) tile.release();
        tiles.clear(); if (wall != null) { wall.removeAllViews(); wall.setOrder(new ArrayList<>()); }
    }

    private void startBridge() {
        if (bridge != null || !foreground) return;
        MqttBridge next = new MqttBridge(); bridge = next;
        final CommandInbox messages = new CommandInbox(handler, (payload, retained) -> {
            if (foreground && bridge == next && router.accept(payload, retained, System.currentTimeMillis())) render();
        });
        inbox = messages;
        mqttState = config.broker.isEmpty() ? "Home Assistant control not configured" : "Home Assistant control connecting";
        next.start(config, new MqttBridge.Listener() {
            @Override public void status(String value) { runOnUiThread(() -> { if (foreground && bridge == next) { mqttState = value; render(); } }); }
            @Override public void message(String payload, boolean retained) {
                messages.offer(payload, retained);
            }
        });
    }
    private void stopBridge() {
        if (inbox != null) { inbox.close(); inbox = null; }
        if (bridge != null) { bridge.stop(); bridge = null; }
    }

    private PlaybackLimits limitsFor(boolean texture, boolean dense) {
        ActivityManager memory = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        return PlaybackLimits.forDevice(Build.MODEL, memory != null && memory.isLowRamDevice(),
                BuildConfig.TV_INTERFACE && Build.VERSION.SDK_INT >= 26 && texture && dense);
    }
    private void showLayout() {
        LinearLayout fields = fields();
        Spinner count = spinner(new String[0]);
        fields.addView(label("Cameras visible on this screen", 14)); fields.addView(count);
        CheckBox fullLayout = new CheckBox(this); fullLayout.setText(R.string.full_screen_layout);
        fullLayout.setId(R.id.layout_fullscreen);
        fullLayout.setChecked(layoutFullscreen); fields.addView(fullLayout);
        fields.addView(label(getString(R.string.full_screen_layout_help), 13));
        CheckBox quality = new CheckBox(this); quality.setText("Use the main stream for enlarged cameras"); quality.setChecked(config.useMainStream); fields.addView(quality);
        CheckBox softwareFloat = new CheckBox(this); softwareFloat.setText("Prefer software H.264 for floating video");
        softwareFloat.setChecked(config.softwareFloatingH264); fields.addView(softwareFloat);
        fields.addView(label("Compatibility option for freezes alongside other video apps. Use a small H.264 grid stream; software decoding uses more CPU. H.265 and ordinary viewing keep their normal decoder selection.", 13));
        CheckBox texture = new CheckBox(this); texture.setText("Experimental grid compatibility");
        texture.setChecked(config.textureGrid); fields.addView(texture);
        fields.addView(label("Try a different drawing path for grid graphics freezes. Single-camera and floating video keep standard rendering.", 13));
        CheckBox dense = new CheckBox(this); dense.setText("Experimental larger layouts (up to 13 cameras)");
        dense.setChecked(config.denseGrid); fields.addView(dense);
        dense.setEnabled(BuildConfig.TV_INTERFACE && Build.VERSION.SDK_INT >= 26 && limitsFor(true, true).maxVisible == 13);
        fields.addView(label("Requires grid compatibility above. Uses grid streams and the native software video engine. More feeds use more CPU and memory and may reduce frame rate. Start with fewer cameras before trying 13.", 13));
        CheckBox motionEight = new CheckBox(this); motionEight.setText("Auto: show 8 cameras during motion, then return to 13");
        motionEight.setId(R.id.layout_motion_eight); motionEight.setChecked(config.motionLayoutEight);
        motionEight.setEnabled(dense.isEnabled()); fields.addView(motionEight);
        fields.addView(label("For a 13-camera layout: motion immediately opens one large active view plus seven smaller cameras. With main streams enabled above, only the large view uses its main stream. The enlarged camera is held for at least 10 seconds; all 13 return after 15 seconds without motion. Grid and pinned views respond immediately.", 13));
        CheckBox highResolution = new CheckBox(this); highResolution.setText("Experimental high-resolution center (13 cameras)");
        highResolution.setId(R.id.layout_high_resolution_center);
        highResolution.setChecked(config.highResolutionCenter); highResolution.setEnabled(dense.isEnabled()); fields.addView(highResolution);
        fields.addView(label("With main streams enabled above, the large center camera uses a hardware-decoded main stream. Surrounding cameras keep grid streams. A failed main stream falls back to grid video, with at least five minutes before a later center change can retry it. Toggle this option off and on to retry sooner.", 13));
        List<AppConfig.Camera> defaults = config.enabledCameras();
        String[] defaultNames = new String[defaults.size() + 1]; defaultNames[0] = "First enabled camera";
        int defaultIndex = 0;
        for (int i = 0; i < defaults.size(); i++) { defaultNames[i + 1] = defaults.get(i).name; if (defaults.get(i).id.equals(config.spotlightCamera)) defaultIndex = i + 1; }
        Spinner spotlight = spinner(defaultNames); spotlight.setSelection(defaultIndex);
        fields.addView(label("Default center camera for the 13-camera layout", 14)); fields.addView(spotlight);
        TextView limit = label("", 13); fields.addView(limit);
        final int[][] countOptions = {new int[0]};
        Runnable refreshCounts = () -> {
            int selected = count.getSelectedItemPosition();
            int requested = selected >= 0 && selected < countOptions[0].length ? countOptions[0][selected] : config.visibleCount;
            PlaybackLimits prospective = limitsFor(texture.isChecked(), dense.isChecked());
            int[] allowed = prospective.layoutCounts();
            int[] offered = requested > prospective.maxVisible ? java.util.Arrays.copyOf(allowed, allowed.length + 1) : allowed;
            if (offered != allowed) offered[offered.length - 1] = requested;
            countOptions[0] = offered;
            String[] names = new String[offered.length]; int index = 0;
            for (int i = 0; i < offered.length; i++) {
                names[i] = offered[i] + (offered[i] == 1 ? " camera" : " cameras")
                        + (offered[i] > prospective.maxVisible ? " requested (" + prospective.maxVisible + " live)" : "");
                if (offered[i] == requested) index = i;
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); count.setAdapter(adapter); count.setSelection(index);
            limit.setText("App compatibility limit: " + prospective.maxVisible + " live cameras. Saved request: " + requested + ".");
        };
        dense.setOnCheckedChangeListener((button, checked) -> { if (checked) texture.setChecked(true); refreshCounts.run(); });
        texture.setOnCheckedChangeListener((button, checked) -> { if (!checked) dense.setChecked(false); refreshCounts.run(); });
        refreshCounts.run();
        ScrollView scroll = new ScrollView(this); scroll.addView(fields);
        AlertDialog.Builder layoutDialog = new AlertDialog.Builder(this).setTitle("Screen layout").setView(scroll).setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    AppConfig next = copyConfig();
                    boolean resetMainFailures = config.highResolutionCenter != highResolution.isChecked() || config.useMainStream != quality.isChecked()
                            || config.motionLayoutEight != motionEight.isChecked();
                    next.visibleCount = countOptions[0][count.getSelectedItemPosition()];
                    next.useMainStream = quality.isChecked();
                    next.softwareFloatingH264 = softwareFloat.isChecked();
                    next.textureGrid = texture.isChecked();
                    next.denseGrid = dense.isChecked() && dense.isEnabled() && texture.isChecked();
                    next.highResolutionCenter = highResolution.isChecked();
                    next.motionLayoutEight = motionEight.isChecked();
                    next.spotlightCamera = spotlight.getSelectedItemPosition() == 0 ? "" : defaults.get(spotlight.getSelectedItemPosition() - 1).id;
                    if (save(next)) {
                        if (resetMainFailures) centerStreamBackoff.clear();
                        layoutFullscreen = fullLayout.isChecked();
                        if (layoutFullscreen) setupOnly = false;
                        layoutOverrideUntil = 0;
                        playbackLimits = limitsFor(config.textureGrid, config.denseGrid); resetSelector(); render();
                    }
                });
        if (ViewSelector.PIN.equals(selector.snapshot(System.currentTimeMillis()).mode)) {
            String id = selector.snapshot(System.currentTimeMillis()).visible.get(0);
            layoutDialog.setNeutralButton("Float camera", (ignored, which) -> startFloatingCamera(id));
        }
        layoutDialog.show();
    }

    private void showCameras() {
        showCameras(0);
    }

    private void showCameras(int selected) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < config.cameras.size(); i++) {
            AppConfig.Camera camera = config.cameras.get(i);
            names.add((i + 1) + ". " + camera.name + (camera.enabled ? "" : " (disabled)"));
        }
        names.add("＋ Add camera");
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Cameras · default viewing order").setItems(names.toArray(new String[0]),
                (d, index) -> { if (index < config.cameras.size()) showCameraActions(index); else editCamera(-1); })
                .setNegativeButton("Close", null).create();
        dialog.setOnShowListener(ignored -> dialog.getListView().setSelection(Math.min(selected, names.size() - 1)));
        dialog.show();
    }

    private void showCameraActions(int index) {
        List<String> actions = new ArrayList<>();
        actions.add("Edit camera"); actions.add("Copy camera");
        actions.add(config.cameras.get(index).enabled ? "Disable camera" : "Enable camera");
        if (index > 0) actions.add("Move up");
        if (index < config.cameras.size() - 1) actions.add("Move down");
        new AlertDialog.Builder(this).setTitle(config.cameras.get(index).name)
                .setItems(actions.toArray(new String[0]), (dialog, choice) -> {
                    switch (actions.get(choice)) {
                        case "Edit camera": editCamera(index); break;
                        case "Copy camera": editCamera(-1, config.copyCameraDraft(index), "Copy camera"); break;
                        case "Disable camera": case "Enable camera": toggleCamera(index); break;
                        case "Move up": moveCamera(index, index - 1); break;
                        case "Move down": moveCamera(index, index + 1); break;
                    }
                }).setNegativeButton("Back", null).show();
    }

    private void toggleCamera(int index) {
        AppConfig next = copyConfig();
        next.cameras.get(index).enabled = !next.cameras.get(index).enabled;
        if (save(next)) { resetSelector(); render(); showCameras(index); }
    }

    private void moveCamera(int from, int to) {
        AppConfig next = copyConfig();
        if (next.moveCamera(from, to) && save(next)) {
            resetSelector(); render(); showCameras(to);
        }
    }

    private void showCameraPicker() {
        if (enabledCameras.isEmpty()) { showCameras(); return; }
        List<String> names = new ArrayList<>(); for (AppConfig.Camera camera : enabledCameras) names.add(camera.name);
        new AlertDialog.Builder(this).setTitle("View a camera").setItems(names.toArray(new String[0]),
                (dialog, index) -> { setupOnly = false; selector.setMode(ViewSelector.PIN, enabledCameras.get(index).id); render(); })
                .setNegativeButton("Cancel", null).show();
    }

    private void showFloatingCameraPicker() {
        if (enabledCameras.isEmpty()) { showCameras(); return; }
        List<String> names = new ArrayList<>(); for (AppConfig.Camera camera : enabledCameras) names.add(camera.name);
        new AlertDialog.Builder(this).setTitle("Float a camera over other apps")
                .setItems(names.toArray(new String[0]), (dialog, index) -> startFloatingCamera(enabledCameras.get(index).id))
                .setNegativeButton("Cancel", null).show();
    }

    private void startFloatingCamera(String id) {
        if (!BuildConfig.TV_INTERFACE && Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                && !getSharedPreferences("phone-ui", MODE_PRIVATE).getBoolean("notificationPrompted", false)) {
            getSharedPreferences("phone-ui", MODE_PRIVATE).edit().putBoolean("notificationPrompted", true).apply();
            pendingNotificationCamera = id;
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
            return;
        }
        if (!canDrawOverlays()) {
            new AlertDialog.Builder(this).setTitle("Allow floating camera")
                    .setMessage("Allow Property Tracker to display over other apps. One small camera window will stay visible; returning to Property Tracker closes it.")
                    .setPositiveButton("Open settings", (dialog, which) -> {
                        pendingFloatingCamera = id;
                        try { startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName())), OVERLAY_PERMISSION_REQUEST); }
                        catch (RuntimeException error) { pendingFloatingCamera = null; toast("This device does not provide an overlay permission screen."); }
                    }).setNegativeButton("Cancel", null).show();
            return;
        }
        floatingTransition = true; releaseTiles(); stopBridge(); signature = "";
        ResultReceiver callback = new ResultReceiver(handler) {
            @Override protected void onReceiveResult(int result, Bundle data) {
                if (result == FloatingCameraService.STARTED) {
                    if (foreground && !isFinishing()) {
                        if (!moveTaskToBack(true)) startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
                    }
                } else {
                    floatingTransition = false;
                    if (foreground) { startBridge(); render(); toast("Floating camera unavailable. Check display-over-apps permission."); }
                }
            }
        };
        Intent service = new Intent(this, FloatingCameraService.class).setAction(FloatingCameraService.ACTION_START)
                .putExtra(FloatingCameraService.EXTRA_CAMERA, id).putExtra(FloatingCameraService.EXTRA_CALLBACK, callback);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
        } catch (RuntimeException error) {
            floatingTransition = false; startBridge(); render(); toast("Floating camera could not start.");
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == OVERLAY_PERMISSION_REQUEST && pendingFloatingCamera != null) {
            String id = pendingFloatingCamera; pendingFloatingCamera = null;
            if (canDrawOverlays()) handler.post(() -> { if (foreground) startFloatingCamera(id); });
            else toast("Floating view needs permission to display over other apps.");
        }
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == NOTIFICATION_PERMISSION_REQUEST && pendingNotificationCamera != null) {
            String id = pendingNotificationCamera; pendingNotificationCamera = null;
            // A denied notification permission must not block viewing or repeatedly prompt.
            handler.post(() -> { if (foreground && !isFinishing()) startFloatingCamera(id); });
        }
    }

    private boolean canDrawOverlays() { return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this); }

    private void editCamera(int index) {
        editCamera(index, index < 0 ? new AppConfig.Camera() : config.cameras.get(index), index < 0 ? "Add camera" : "Edit camera");
    }

    private void editCamera(int index, AppConfig.Camera existing, String title) {
        LinearLayout form = fields();
        CheckBox enabled = new CheckBox(this); enabled.setText("Enabled for live viewing"); enabled.setChecked(existing.enabled); form.addView(enabled);
        EditText name = field(form, "Camera name", existing.name, false);
        EditText id = field(form, "Camera ID for automations (for example: barn)", existing.id, false);
        EditText zone = field(form, "Location zone (optional, for example: pasture)", existing.zone, false);
        EditText host = field(form, "Camera or NVR address", existing.host, false);
        host.setHint("192.0.2.10");
        EditText port = field(form, "RTSP port", String.valueOf(existing.port), false); port.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText user = field(form, "Camera username", existing.username, false);
        EditText password = field(form, "Camera password", existing.password, true);
        form.addView(label("Stream path preset", 14));
        Spinner preset = spinner(new String[]{"Keep / custom paths", "Reolink", "Amcrest"}); form.addView(preset);
        EditText sub = field(form, "Grid stream path", existing.subPath, false);
        EditText main = field(form, "Enlarged stream path (optional)", existing.mainPath, false);
        preset.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long selectedId) {
                if (position == 1) { sub.setText("/Preview_01_sub"); main.setText("/Preview_01_main"); }
                if (position == 2) { sub.setText("/cam/realmonitor?channel=1&subtype=1"); main.setText("/cam/realmonitor?channel=1&subtype=0"); }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        form.addView(label("H.264 and H.265/HEVC streams are supported within this device's decoding limits. Edit the paths above for model-specific streams.", 13));
        TvFormDialog dialog = new TvFormDialog(this, title, form);
        if (index >= 0) dialog.setRemoveAction(() -> {
            AppConfig next = copyConfig(); next.cameras.remove(index);
            if (save(next)) { resetSelector(); render(); dialog.dismiss(); }
        });
        dialog.saveButton().setOnClickListener(view -> {
                try {
                    AppConfig.Camera camera = new AppConfig.Camera();
                    camera.enabled = enabled.isChecked();
                    camera.name = text(name); camera.id = text(id).toLowerCase(Locale.ROOT); camera.zone = text(zone).toLowerCase(Locale.ROOT);
                    camera.host = text(host); camera.port = Integer.parseInt(text(port));
                    camera.username = text(user); camera.password = password.getText().toString();
                    camera.subPath = text(sub); camera.mainPath = text(main);
                    if (camera.name.isEmpty()) throw new IllegalArgumentException("Enter a camera name.");
                    if (!camera.id.matches("[a-z0-9_-]{1,60}")) throw new IllegalArgumentException("Use letters, numbers, hyphens, or underscores for the camera ID.");
                    if (!camera.zone.isEmpty() && !camera.zone.matches("[a-z0-9_-]{1,60}")) throw new IllegalArgumentException("Use a short zone name such as pasture or barn.");
                    if (!camera.host.matches("[A-Za-z0-9.-]{1,253}") || camera.port < 1 || camera.port > 65535) throw new IllegalArgumentException("Enter a camera hostname or IPv4 address and a valid port.");
                    if (camera.subPath.isEmpty() || !validPath(camera.subPath) || (!camera.mainPath.isEmpty() && !validPath(camera.mainPath))) throw new IllegalArgumentException("Enter stream paths, starting with /, without a full URL.");
                    for (int i = 0; i < config.cameras.size(); i++) if (i != index && config.cameras.get(i).id.equals(camera.id)) throw new IllegalArgumentException("That camera ID is already used.");
                    AppConfig next = copyConfig(); if (index < 0) next.cameras.add(camera); else next.cameras.set(index, camera);
                    if (save(next)) { resetSelector(); render(); dialog.dismiss(); }
                } catch (NumberFormatException error) { toast("Enter a valid RTSP port."); }
                catch (IllegalArgumentException error) { toast(error.getMessage()); }
        });
        dialog.show();
    }

    private void showMqtt() {
        LinearLayout form = fields();
        EditText site = field(form, "Site ID", config.site, false);
        EditText display = field(form, "Screen ID (unique within this site)", config.display, false);
        EditText broker = field(form, "MQTT broker URI (leave empty to disable)", config.broker, false); broker.setHint("tcp://192.0.2.2:1883");
        EditText user = field(form, "MQTT username", config.brokerUser, false);
        EditText password = field(form, "MQTT password", config.brokerPassword, true);
        TextView info = label("Commands use fieldview/<site>/<screen>/command. Configure a dedicated broker login. Changes operate while Property Tracker is open.", 13); form.addView(info);
        TvFormDialog dialog = new TvFormDialog(this, "Home Assistant · MQTT", form);
        dialog.saveButton().setText("Save settings");
        dialog.saveButton().setOnClickListener(view -> {
            try {
                AppConfig next = copyConfig(); next.site = text(site).toLowerCase(Locale.ROOT); next.display = text(display).toLowerCase(Locale.ROOT);
                next.broker = text(broker); next.brokerUser = text(user); next.brokerPassword = password.getText().toString();
                if (!next.site.matches("[a-z0-9_-]{1,40}") || !next.display.matches("[a-z0-9_-]{1,40}")) throw new IllegalArgumentException("Use letters, numbers, hyphens, or underscores for site and screen IDs.");
                if (!next.broker.isEmpty()) {
                    URI uri = new URI(next.broker);
                    if (!("tcp".equals(uri.getScheme()) || "ssl".equals(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null
                            || uri.getPort() < 1 || uri.getPort() > 65535 || uri.getRawQuery() != null || uri.getRawFragment() != null
                            || (uri.getPath() != null && !uri.getPath().isEmpty())) throw new IllegalArgumentException("Use tcp://host:1883 or ssl://host:8883 and the separate login fields.");
                }
                if (save(next)) { stopBridge(); startBridge(); render(); dialog.dismiss(); }
            } catch (Exception error) { toast(error instanceof IllegalArgumentException ? error.getMessage() : "Enter a valid MQTT broker URI."); }
        });
        dialog.show();
    }

    private boolean validPath(String value) { return value.startsWith("/") && !value.startsWith("//") && !value.contains(" ") && !value.contains("#"); }
    private AppConfig copyConfig() {
        try { return AppConfig.fromJson(config.toJson()); } catch (Exception error) { throw new IllegalStateException("Unable to copy settings"); }
    }
    private boolean save(AppConfig next) {
        try { store.save(next); config = next; return true; }
        catch (Exception ignored) { toast("Settings could not be saved. Your previous settings are unchanged."); return false; }
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private String text(EditText field) { return field.getText().toString().trim(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView label(String text, int size) { TextView label = new TextView(this); label.setText(text); label.setTextSize(size); label.setTextColor(Color.WHITE); label.setPadding(0, dp(8), 0, dp(6)); return label; }
    private Button button(String name, Runnable click) { Button button = new Button(this); button.setText(name); button.setTextSize(13); button.setAllCaps(false); button.setOnClickListener(view -> click.run()); return button; }
    private LinearLayout fields() { LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(24), dp(8), dp(24), dp(16)); return form; }
    private EditText field(LinearLayout form, String name, String value, boolean secret) {
        form.addView(label(name, 14)); EditText edit = new EditText(this); edit.setSingleLine(true); edit.setTextSize(15);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | (secret ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));
        edit.setText(value); edit.setSelectAllOnFocus(true); form.addView(edit, new LinearLayout.LayoutParams(-1, dp(48))); return edit;
    }
    private Spinner spinner(String[] options) {
        Spinner spinner = new Spinner(this); ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinner.setAdapter(adapter); return spinner;
    }
}
