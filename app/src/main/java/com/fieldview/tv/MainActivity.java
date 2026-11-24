package com.fieldview.tv;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
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
import android.view.MotionEvent;
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
    private final Map<String, Double> videoAspects = new LinkedHashMap<>();
    private final CenterStreamBackoff centerStreamBackoff = new CenterStreamBackoff();
    private ConfigStore store;
    private AppConfig config;
    private List<AppConfig.Camera> enabledCameras = new ArrayList<>();
    private PlaybackLimits playbackLimits;
    private ViewSelector selector;
    private CommandRouter router;
    private CameraWall wall;
    private TextView footer, empty, phoneSettingsStatus;
    private String viewerStatus = "";
    private LinearLayout root, toolbar;
    private LinearLayout phoneControls;
    // One pinned phone camera has no bar: pinch zooms, a vertical swipe at 1x changes camera, long-press opens More,
    // system Back returns.
    private boolean phonePinned;
    /** The phone's pinned camera, whose zoom lasts until the camera changes or Pin ends. */
    private String pinnedCamera;
    private PinnedCameraGesture pinnedGesture;
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
    // Phone portrait Grid is Browse: one row per enabled camera, holding a live tile only while it is in view.
    private final Map<String, View> browsePlaceholders = new LinkedHashMap<>();
    private List<String> browseIds = new ArrayList<>();
    private boolean browsing, browseTexture, browseStartPending;
    /** New Browse feeds wait for scrolling to settle; feeds that leave the view are released without waiting. */
    private static final long BROWSE_SETTLE_MS = 200;
    private final Runnable browseRelease = () -> syncBrowse(false);
    private final Runnable browseStart = () -> { browseStartPending = false; syncBrowse(true); };
    // Phone portrait Auto shows its third ranked camera only when all three natural rows fit Auto's measured area.
    /** Area changes within this distance are layout jitter and never re-fit the rows. */
    private static final int AUTO_AREA_JITTER_DP = 2;
    private int autoWallWidth, autoWallHeight;
    private boolean autoAreaFullscreen;
    private final Runnable autoAreaUpdate = this::updateAutoArea;

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
        gridButton = button(gridName(), () -> { setupOnly = false; selector.setMode(ViewSelector.GRID, null); render(); }); toolbar.addView(gridButton);
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
        wall = new CameraWall(this); wall.setId(R.id.camera_wall); wall.setViewportListener(this::browseViewportChanged);
        // Layout only schedules the Auto area check (width, height or insets); any re-fit renders after layout.
        if (!BuildConfig.TV_INTERFACE) wall.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int jitter = dp(AUTO_AREA_JITTER_DP);
            if (Math.abs(right - left - autoWallWidth) > jitter || Math.abs(bottom - top - autoWallHeight) > jitter
                    || autoAreaFullscreen != layoutFullscreen) { handler.removeCallbacks(autoAreaUpdate); handler.post(autoAreaUpdate); }
        });
        content.addView(wall, new FrameLayout.LayoutParams(-1, -1));
        empty = label("", 19);
        empty.setGravity(Gravity.CENTER); content.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        if (!BuildConfig.TV_INTERFACE) {
            phoneControls = new LinearLayout(this); phoneControls.setId(R.id.phone_controls); phoneControls.setGravity(Gravity.CENTER);
            phoneControls.setBackgroundColor(0xCC101516); phoneControls.setVisibility(View.GONE);
            // Fullscreen multi-camera layouts only; a single pinned camera never shows this bar.
            phoneControls.addView(button("Back", () -> { onBackPressed(); showPhoneControls(); }), new LinearLayout.LayoutParams(0, dp(48), 1));
            phoneControls.addView(button("More", () -> showPhoneMenu(phoneControls)), new LinearLayout.LayoutParams(0, dp(48), 1));
            content.addView(phoneControls, new FrameLayout.LayoutParams(-1, dp(52), Gravity.BOTTOM));
        }
        if (BuildConfig.TV_INTERFACE) {
            footer = label("", 12);
            footer.setTextColor(0xFFAFBEAF);
            footer.setPadding(dp(4), dp(8), dp(4), 0);
            footer.setMaxLines(2);
            footer.setId(R.id.viewer_footer);
            root.addView(footer, new LinearLayout.LayoutParams(-1, dp(40)));
        }
        setContentView(root);
        if (!BuildConfig.TV_INTERFACE) {
            pinnedGesture = new PinnedCameraGesture(this, new PinnedCameraGesture.Host() {
                @Override public CameraTile pinnedTile() { return foreground && phonePinned && fullscreen ? tiles.get(pinnedCamera) : null; }
                @Override public void cancelViews(MotionEvent cancel) { getWindow().superDispatchTouchEvent(cancel); }
                @Override public void step(int direction) { switchPinned(direction); }
            });
            PhoneInsets.install(root, () -> fullscreen, dp(8), dp(4));
            if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> { if (!handleBack()) finish(); });
        }
        resetSelector();
        if (state != null) {
            String mode = state.getString("viewerMode", ViewSelector.AUTO);
            selector.setMode(mode, state.getString("pinnedCamera"));
            modeBeforePin = state.getString("modeBeforePin", ViewSelector.AUTO);
            wall.restoreBrowseOffset(state.getInt("browseOffset", 0));
        } else if (!BuildConfig.TV_INTERFACE) {
            // A fresh phone launch opens the camera list: Browse in portrait, Grid in landscape. Auto is one tap away.
            selector.setMode(ViewSelector.GRID, null); modeBeforePin = ViewSelector.GRID;
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
        handler.removeCallbacks(hidePhoneControls); handler.removeCallbacks(autoAreaUpdate);
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
        if (wall != null) state.putInt("browseOffset", wall.browseOffset());
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
        if (footer != null) footer.setVisibility(value ? View.GONE : View.VISIBLE);
        root.setPadding(value ? 0 : dp(20), value ? 0 : dp(14), value ? 0 : dp(20), value ? 0 : dp(10));
        wall.setFullscreen(value);
        applySystemUi();
        if (!BuildConfig.TV_INTERFACE) { root.requestApplyInsets(); showPhoneControls(); }
    }
    private void showPhoneControls() {
        if (phoneControls == null) return;
        handler.removeCallbacks(hidePhoneControls);
        boolean show = fullscreen && !phonePinned;
        phoneControls.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) handler.postDelayed(hidePhoneControls, 5000);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        // Phone Pin only (PinnedCameraGesture); every other layout and the TV keep the views' own touch handling.
        return (pinnedGesture != null && pinnedGesture.onTouch(event)) || super.dispatchTouchEvent(event);
    }
    /** One step through the enabled cameras; render() retires the old camera before the next starts. */
    private void switchPinned(int direction) {
        if (selector != null && selector.stepPinnedCamera(direction)) render();
    }
    private void showPhoneMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        String[] labels = {"Cameras", "Settings", "Float camera", "Home Assistant", "Privacy policy", "Open-source licenses"};
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
        videoAspects.keySet().retainAll(ids);
        if (selector == null || selector.maxCapacity() != playbackLimits.maxVisible) selector = new ViewSelector(ids, zones, config.visibleCount, playbackLimits.maxVisible);
        else { selector.reconfigure(ids, zones); if (layoutOverrideUntil == 0) selector.setCapacity(config.visibleCount); }
        selector.setDefaultCamera(config.spotlightCamera);
        selector.setMotionLayoutEight(config.motionLayoutEight && BuildConfig.TV_INTERFACE && Build.VERSION.SDK_INT >= 26
                && config.denseGrid && config.textureGrid && playbackLimits.maxVisible == 13);
        selector.setMultiMotionLayout(config.multiMotionLayout);
        selector.setFastMotion(config.fastMotion);
        // Rotation recreates the activity, so the policy follows the current orientation.
        // The selector ranks and holds up to three portrait candidates; render() fits rows without resetting that hold.
        selector.setPortraitAuto(phonePortrait() ? 3 : 0);
        for (Map.Entry<String, Double> entry : videoAspects.entrySet()) selector.setVideoAspect(entry.getKey(), entry.getValue());
        router = new CommandRouter(selector, (count, until) -> { layoutOverrideUntil = until; selector.setCapacity(count); });
        signature = "";
    }

    private void render() {
        if (!foreground || floatingTransition || selector == null) return;
        if (setupOnly) {
            String prompt = "Playback paused for setup.\n\nChoose Auto, " + gridName() + ", View, or Float to start video.";
            phonePinned = false; setFullscreen(false); empty.setVisibility(View.VISIBLE);
            if (!BuildConfig.TV_INTERFACE) updateViewerStatus("setup", 0);
            if (!prompt.contentEquals(empty.getText())) empty.setText(prompt);
            return;
        }
        ViewSelector.Selection selection = selector.snapshot(System.currentTimeMillis());
        boolean pinned = ViewSelector.PIN.equals(selection.mode);
        boolean browse = ViewSelector.GRID.equals(selection.mode) && phonePortrait();
        boolean wasPinned = phonePinned;
        phonePinned = !BuildConfig.TV_INTERFACE && pinned;
        // Zoom belongs to one pinned camera: changing camera or leaving Pin returns every picture to the 1x fit.
        String zoomCamera = phonePinned && !selection.visible.isEmpty() ? selection.visible.get(0) : null;
        if (!java.util.Objects.equals(zoomCamera, pinnedCamera)) {
            pinnedCamera = zoomCamera;
            for (CameraTile tile : tiles.values()) tile.resetZoom();
        }
        if (!pinned) modeBeforePin = selection.mode;
        setFullscreen(pinned || layoutFullscreen);
        // Fullscreen may not change between a fullscreen grid and Pin: hide the bar on entry, offer it again on return.
        if (wasPinned != phonePinned) showPhoneControls();
        empty.setVisibility(enabledCameras.isEmpty() ? View.VISIBLE : View.GONE);
        if (enabledCameras.isEmpty()) {
            String prompt = config.cameras.isEmpty()
                    ? "Add a camera to start.\n\nOpen Cameras and enter its local address and login.\nH.264 and H.265/HEVC streams are supported within this device's decoding limits."
                    : "No cameras enabled.\n\nOpen Cameras, select a camera, and choose Enable camera.";
            if (!prompt.contentEquals(empty.getText())) empty.setText(prompt);
        }
        // Phone portrait Auto: the selector's ranked candidates, trimmed to the rows that fit; the selection is untouched.
        boolean stackedAuto = ViewSelector.AUTO.equals(selection.mode) && selector.portraitAuto() > 0;
        List<String> visible = stackedAuto ? fitStackedAuto(selection.visible) : selection.visible;
        double[] rowAspects = stackedAuto ? substreamAspects(visible) : null;
        String autoLabel = ViewSelector.AUTO.equals(selection.mode) ? "● Auto" : "Auto";
        String gridName = gridName();
        String gridLabel = ViewSelector.GRID.equals(selection.mode) ? "● " + gridName : gridName;
        if (!autoLabel.contentEquals(autoButton.getText())) autoButton.setText(autoLabel);
        if (!gridLabel.contentEquals(gridButton.getText())) gridButton.setText(gridLabel);
        updateViewerStatus(browse ? "browse" : selection.reason, browse ? enabledCameras.size() : visible.size());
        if (browse) { renderBrowse(); return; }
        boolean textureGrid = config.textureGrid && visible.size() > 1;
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
        // Phone Auto uses the same substreams as Browse. Motion must not promote a live preview
        // to a main stream that may exceed the phone decoder's limits.
        boolean useMain = config.useMainStream && (BuildConfig.TV_INTERFACE || pinned);
        WallStreamPlan streams = new WallStreamPlan(selection, nativeGrid, useMain, config.highResolutionCenter, unavailable, config.motionLayoutEight);
        double firstAspect = selection.motionLayoutSlots > 0 && !selection.visible.isEmpty()
                ? videoAspects.getOrDefault(selection.visible.get(0), selection.visible.size() == 5 ? 8.0 / 3.0 : 16.0 / 9.0) : 0;
        double secondAspect = selection.motionLayoutSlots == 2 && selection.visible.size() > 1
                ? videoAspects.getOrDefault(selection.visible.get(1), 16.0 / 9.0) : 0;
        String nextSignature = selection.reason + visible + selection.featured + config.useMainStream + fullscreen + textureGrid + config.denseGrid + config.highResolutionCenter + config.motionLayoutEight + config.multiMotionLayout + selection.motionLayoutSlots + ":" + firstAspect + ":" + secondAspect;
        if (stackedAuto) nextSignature += ":" + java.util.Arrays.toString(rowAspects);
        if (signature.equals(nextSignature)) return;
        signature = nextSignature;
        leaveBrowse();
        android.util.Log.i("FieldviewLayout", "mode=" + selection.mode + " visible=" + visible.size()
                + " reason=" + selection.reason + " motion_eight=" + selection.motionLayoutEight + " motion_slots=" + selection.motionLayoutSlots);
        String previouslyFocused = null;
        for (Map.Entry<String, CameraTile> entry : tiles.entrySet()) if (entry.getValue().hasFocus()) previouslyFocused = entry.getKey();
        Iterator<Map.Entry<String, CameraTile>> iterator = tiles.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CameraTile> entry = iterator.next();
            if (config.fastMotion && selection.motionLayoutSlots > 0 && streams.highResolutionCenter(camera(entry.getKey()))) {
                AppConfig.Camera source = camera(entry.getKey());
                entry.getValue().prepareMainFromGrid(source.streamUri(true), source.streamUri(false));
            }
            if (!visible.contains(entry.getKey()) || entry.getValue().usesTexture() != (textureGrid && !streams.highResolutionCenter(camera(entry.getKey())))
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
        for (String id : visible) {
            AppConfig.Camera camera = camera(id);
            CameraTile tile = tiles.get(id);
            if (tile == null) tile = newTile(id, camera, textureGrid && !streams.highResolutionCenter(camera), streams.nativeGrid(camera));
            tile.setContentDescription("Enlarge " + camera.name);
            boolean featured = selection.featured.contains(id);
            tile.setFullscreen(fullscreen, !pinned && visible.size() > 1);
            tile.setFeatured(featured || streams.highResolutionCenter(camera));
            tile.setMainSuppressed(streams.mainUnavailable(camera), unsupported.contains(id));
            CameraTile requestTile = tile;
            tile.setMainUnavailableListener(streams.highResolutionCenter(camera) ? () -> {
                centerStreamBackoff.failed(camera, android.os.SystemClock.elapsedRealtime(), requestTile.mainFormatRejected());
                handler.post(() -> { signature = ""; render(); });
            } : null);
            boolean main = streams.main(camera);
            String stream = camera.streamUri(main);
            // Phone: a pinned main falls back once to its distinct substream; the fallback holds until a new open.
            boolean phonePinnedMain = !BuildConfig.TV_INTERFACE && pinned && !stream.equals(camera.streamUri(false));
            if (streams.highResolutionCenter(camera) || phonePinnedMain) tile.playMainOrGrid(stream, camera.streamUri(false));
            else tile.play(stream, !stream.equals(camera.streamUri(false)));
            ordered.add(tile);
        }
        wall.setOrder(ordered); wall.setFeaturedCount(selection.featured.size()); wall.setMotionLayoutSlots(selection.motionLayoutSlots);
        wall.setStacked(stackedAuto);
        if (stackedAuto) wall.setStackedAspects(rowAspects);
        wall.setActiveAspects(firstAspect, secondAspect);
        if (previouslyFocused != null && !ordered.isEmpty()) {
            CameraTile old = tiles.get(previouslyFocused);
            (old == null ? ordered.get(0) : old).requestFocus();
        } else if (fullscreen && !ordered.isEmpty()) ordered.get(0).requestFocus();
    }

    private void updateViewerStatus(String mode, int visibleCount) {
        String status = config.site + " / " + config.display + "  ·  " + mode + "  ·  "
                + visibleCount + "/" + config.cameras.size() + " cameras  ·  " + mqttState;
        int disabled = config.cameras.size() - enabledCameras.size();
        if (disabled > 0) status += "  ·  " + disabled + " disabled";
        if (layoutOverrideUntil != 0) status += "  ·  temporary layout";
        if (status.equals(viewerStatus)) return;
        viewerStatus = status;
        if (footer != null) footer.setText(status);
        if (phoneSettingsStatus != null) phoneSettingsStatus.setText(status);
    }

    private CameraTile newTile(String id, AppConfig.Camera camera, boolean texture, boolean nativeGrid) {
        CameraTile tile = new CameraTile(this, camera.name, false, texture, nativeGrid);
        tile.configureStartup(startupCoordinator, id);
        tile.setOnClickListener(view -> {
            // A tap on the pinned phone camera does nothing; swipes, long-press and system Back cover it.
            if (!BuildConfig.TV_INTERFACE && ViewSelector.PIN.equals(selector.snapshot(System.currentTimeMillis()).mode)) return;
            selector.setMode(ViewSelector.PIN, id); render();
        });
        // Long-press the pinned phone camera for the existing More menu; elsewhere it stays an ordinary tap.
        if (!BuildConfig.TV_INTERFACE) tile.setOnLongClickListener(view -> {
            if (!ViewSelector.PIN.equals(selector.snapshot(System.currentTimeMillis()).mode)) return false;
            pinnedGesture.reset(); // The rest of this touch belongs to the menu: no camera switch or pinch.
            showPhoneMenu(view); return true;
        });
        tiles.put(id, tile); wall.addView(tile);
        tile.setAspectListener(ratio -> {
            // Phone rows are sized from substream pictures; a pinned main's own dimensions never resize Auto or Browse.
            if (!BuildConfig.TV_INTERFACE) {
                Double cached = videoAspects.get(id);
                if (tile.playingMainStream() || (cached != null && Math.abs(cached - ratio) < .01)) return;
            }
            Double old = videoAspects.put(id, ratio);
            selector.setVideoAspect(id, ratio);
            if (old == null || Math.abs(old - ratio) > .01) handler.post(this::render);
        });
        return tile;
    }

    private AppConfig.Camera camera(String id) {
        for (AppConfig.Camera camera : config.cameras) if (camera.id.equals(id)) return camera;
        throw new IllegalStateException("Unknown camera");
    }
    private void releaseTiles() {
        if (pinnedGesture != null) pinnedGesture.reset();
        cancelBrowseSync(); browsing = false; browseIds = new ArrayList<>(); browsePlaceholders.clear();
        for (CameraTile tile : tiles.values()) tile.release();
        tiles.clear(); if (wall != null) { wall.setBrowse(false); wall.removeAllViews(); wall.setOrder(new ArrayList<>()); }
    }

    private boolean phonePortrait() {
        return !BuildConfig.TV_INTERFACE && getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
    }
    /** Phone portrait Grid is the scrolling Browse list. */
    private String gridName() { return phonePortrait() ? "Browse" : "Grid"; }
    /**
     * The ranked portrait Auto candidates: a third only when all three natural full-width rows (substream shapes,
     * 16:9 if unknown, the wall's gaps and rounding) fit Auto's measured area; otherwise the top two.
     */
    private List<String> fitStackedAuto(List<String> ranked) {
        if (ranked.size() < 3) return ranked;
        boolean measured = autoWallWidth > 0 && autoWallHeight > 0 && autoAreaFullscreen == layoutFullscreen;
        if (measured) {
            if (wall.stackedNaturalHeight(autoWallWidth, substreamAspects(ranked), ranked.size()) <= autoWallHeight) return ranked;
        } else if (tiles.keySet().containsAll(ranked)) {
            // A fullscreen change waits for the next layout before retiring an already-playing third camera.
            return ranked;
        }
        return new ArrayList<>(ranked.subList(0, 2));
    }
    private double[] substreamAspects(List<String> ids) {
        double[] aspects = new double[ids.size()];
        for (int i = 0; i < aspects.length; i++) aspects[i] = videoAspects.getOrDefault(ids.get(i), 0.0);
        return aspects;
    }
    /** Records the area portrait Auto lays out in (never Pin's); a real change re-fits the rows through render(). */
    private void updateAutoArea() {
        // Not while stopped, while Pin changes the chrome, or before a pending layout lands.
        if (!foreground || selector == null || !phonePortrait() || fullscreen != layoutFullscreen || root.isLayoutRequested()) return;
        int width = wall.getWidth(), height = wall.getHeight(), jitter = dp(AUTO_AREA_JITTER_DP);
        if (width <= 0 || height <= 0) return;
        if (autoAreaFullscreen == layoutFullscreen && Math.abs(width - autoWallWidth) <= jitter && Math.abs(height - autoWallHeight) <= jitter) return;
        autoWallWidth = width; autoWallHeight = height; autoAreaFullscreen = layoutFullscreen;
        render();
    }

    /** Every enabled camera in saved order. Rows stream only while in view; render() keeps the list current. */
    private void renderBrowse() {
        List<String> ids = new ArrayList<>(); double[] aspects = new double[enabledCameras.size()];
        for (int i = 0; i < enabledCameras.size(); i++) {
            String id = enabledCameras.get(i).id; ids.add(id); aspects[i] = videoAspects.getOrDefault(id, 0.0);
        }
        boolean textureGrid = config.textureGrid && ids.size() > 1;
        String nextSignature = "browse" + ids + textureGrid + fullscreen + java.util.Arrays.toString(aspects);
        if (signature.equals(nextSignature)) return;
        signature = nextSignature;
        android.util.Log.i("FieldviewLayout", "mode=grid browse=" + ids.size() + " budget=" + playbackLimits.maxVisible);
        Iterator<Map.Entry<String, CameraTile>> iterator = tiles.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CameraTile> entry = iterator.next();
            if (!ids.contains(entry.getKey()) || entry.getValue().usesTexture() != textureGrid || entry.getValue().usesNativeGrid()) {
                entry.getValue().release(); wall.removeView(entry.getValue()); iterator.remove();
            }
        }
        // Names can change with the catalog, so placeholders are rebuilt with the list.
        for (View placeholder : browsePlaceholders.values()) wall.removeView(placeholder);
        browsePlaceholders.clear();
        browseIds = ids; browseTexture = textureGrid; browsing = true;
        // Feeds already on screen keep their connection; an enlarged main stream returns to the grid stream.
        for (Map.Entry<String, CameraTile> entry : tiles.entrySet()) configureBrowseTile(entry.getKey(), entry.getValue());
        wall.setBrowseAspects(aspects);
        arrangeBrowse();
        browseViewportChanged(false);
    }
    private void leaveBrowse() {
        if (!browsing) return;
        browsing = false; cancelBrowseSync(); browseIds = new ArrayList<>();
        for (View placeholder : browsePlaceholders.values()) wall.removeView(placeholder);
        browsePlaceholders.clear(); wall.setBrowse(false);
    }
    /** One wall child per listed camera, in saved order: its live tile, or a placeholder without a stream. */
    private void arrangeBrowse() {
        List<View> ordered = new ArrayList<>();
        for (String id : browseIds) {
            View view = tiles.get(id), placeholder = browsePlaceholders.get(id);
            if (view != null && placeholder != null) { wall.removeView(placeholder); browsePlaceholders.remove(id); }
            if (view == null) view = placeholder != null ? placeholder : browsePlaceholder(id);
            ordered.add(view);
        }
        wall.setOrder(ordered); wall.setBrowse(true);
    }
    private View browsePlaceholder(String id) {
        AppConfig.Camera camera = camera(id);
        TextView row = label(camera.name + "\nTap to view", 14);
        row.setGravity(Gravity.CENTER); row.setTextColor(0xFFAFBEAF); row.setBackgroundColor(Color.BLACK);
        row.setContentDescription("Enlarge " + camera.name); row.setFocusable(true); row.setClickable(true);
        row.setOnClickListener(view -> { selector.setMode(ViewSelector.PIN, id); render(); });
        browsePlaceholders.put(id, row); wall.addView(row);
        return row;
    }
    private void configureBrowseTile(String id, CameraTile tile) {
        AppConfig.Camera camera = camera(id);
        tile.setContentDescription("Enlarge " + camera.name);
        tile.setFullscreen(fullscreen, browseIds.size() > 1);
        tile.setFeatured(false); tile.setMainSuppressed(false, false); tile.setMainUnavailableListener(null);
        tile.play(camera.streamUri(false), false);
    }
    /** Called by the wall during layout, scrolling or drawing, so view changes are posted. */
    private void browseViewportChanged(boolean scrolled) {
        if (!browsing || !foreground) return;
        handler.removeCallbacks(browseRelease); handler.post(browseRelease);
        if (scrolled) { handler.removeCallbacks(browseStart); handler.postDelayed(browseStart, BROWSE_SETTLE_MS); browseStartPending = true; }
        else if (!browseStartPending) { browseStartPending = true; handler.post(browseStart); }
    }
    private void cancelBrowseSync() {
        handler.removeCallbacks(browseRelease); handler.removeCallbacks(browseStart); browseStartPending = false;
    }
    /** Release rows that left the viewport; when settled, start rows in view within the playback budget. */
    private void syncBrowse(boolean start) {
        // A stopped, floating or setup-paused viewer must not reconnect from a late layout or scroll callback.
        if (!foreground || floatingTransition || setupOnly || !browsing) return;
        List<Rect> rows = wall.browseFrames();
        if (rows == null || rows.size() != browseIds.size()) return;
        boolean[] live = new boolean[rows.size()];
        for (int i = 0; i < live.length; i++) live[i] = tiles.containsKey(browseIds.get(i));
        boolean[] keep = BrowseViewport.select(rows, wall.getScrollY(), wall.getHeight(), live, playbackLimits.maxVisible);
        boolean changed = false;
        // Release first, so the decoder and startup slots are free before any replacement starts.
        for (int i = 0; i < live.length; i++) if (live[i] && !keep[i]) {
            CameraTile tile = tiles.remove(browseIds.get(i)); tile.release(); wall.removeView(tile); changed = true;
        }
        if (start) for (int i = 0; i < live.length; i++) if (keep[i] && !live[i]) {
            String id = browseIds.get(i);
            configureBrowseTile(id, newTile(id, camera(id), browseTexture, false)); changed = true;
        }
        if (changed) arrangeBrowse();
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
        TextView status = BuildConfig.TV_INTERFACE ? null : label(viewerStatus, 14);
        if (status != null) {
            status.setId(R.id.phone_settings_status);
            fields.addView(status);
        }
        // In phone portrait, Grid is Browse and Auto fits its count to the screen; the saved grid size is kept and offered in landscape.
        boolean portrait = phonePortrait();
        Spinner count = spinner(new String[0]);
        TextView countLabel = label("Cameras visible on this screen", 14);
        fields.addView(countLabel); fields.addView(count);
        if (portrait) { countLabel.setVisibility(View.GONE); count.setVisibility(View.GONE); }
        CheckBox fullLayout = new CheckBox(this); fullLayout.setText(R.string.full_screen_layout);
        fullLayout.setId(R.id.layout_fullscreen);
        fullLayout.setChecked(layoutFullscreen); fields.addView(fullLayout);
        fields.addView(label(getString(R.string.full_screen_layout_help), 13));
        CheckBox quality = new CheckBox(this);
        quality.setText(BuildConfig.TV_INTERFACE ? "Use the main stream for enlarged cameras" : "Use the main stream in single-camera full screen");
        quality.setChecked(config.useMainStream); fields.addView(quality);
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
        fields.addView(label("For a 13-camera layout: motion opens an enlarged active view with surrounding grid cameras. Only one enlarged view uses its main stream. The response option below controls camera hold time; all 13 return after 15 seconds without motion. Grid and pinned views respond immediately.", 13));
        CheckBox multiMotion = new CheckBox(this); multiMotion.setText("Auto: enlarge multiple active cameras");
        multiMotion.setId(R.id.layout_multi_motion); multiMotion.setChecked(config.multiMotionLayout);
        multiMotion.setEnabled(dense.isEnabled()); fields.addView(multiMotion);
        fields.addView(label("Enlarge up to four active cameras. Layouts adapt to camera proportions. The response option below controls expansion and camera hold time; a lower activity count must remain for 15 seconds before shrinking. At most one enlarged view uses its main stream; the others use grid streams.", 13));
        CheckBox fastMotion = new CheckBox(this); fastMotion.setText("Faster motion switches");
        fastMotion.setId(R.id.layout_fast_motion); fastMotion.setChecked(config.fastMotion);
        fastMotion.setEnabled(dense.isEnabled()); fields.addView(fastMotion);
        fields.addView(label("Use a 5-second camera hold and 1-second expansion delay, keeping the 15-second quiet return. When an active camera is already playing, keep its live grid picture visible while the main stream connects. Turn off for the previous 10-second hold and 2-second expansion.", 13));
        CheckBox highResolution = new CheckBox(this); highResolution.setText("Experimental high-resolution center (13 cameras)");
        highResolution.setId(R.id.layout_high_resolution_center);
        highResolution.setChecked(config.highResolutionCenter); highResolution.setEnabled(dense.isEnabled()); fields.addView(highResolution);
        fields.addView(label("With main streams enabled above, the large center camera uses a hardware-decoded main stream. Surrounding cameras keep grid streams. A failed main stream falls back to grid video, with at least five minutes before a later center change can retry it. Toggle this option off and on to retry sooner.", 13));
        List<AppConfig.Camera> defaults = config.enabledCameras();
        String[] defaultNames = new String[defaults.size() + 1]; defaultNames[0] = "First enabled camera";
        int defaultIndex = 0;
        for (int i = 0; i < defaults.size(); i++) { defaultNames[i + 1] = defaults.get(i).name; if (defaults.get(i).id.equals(config.spotlightCamera)) defaultIndex = i + 1; }
        Spinner spotlight = spinner(defaultNames); spotlight.setSelection(defaultIndex);
        fields.addView(label(portrait ? "Default camera, shown first in portrait Auto" : "Default center camera for the 13-camera layout", 14)); fields.addView(spotlight);
        TextView limit = label("", 13); fields.addView(limit);
        if (portrait) limit.setVisibility(View.GONE);
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
        AlertDialog.Builder layoutDialog = new AlertDialog.Builder(this)
                .setTitle(BuildConfig.TV_INTERFACE ? "Screen layout" : "Settings").setView(scroll).setNegativeButton("Cancel", null)
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
                    next.multiMotionLayout = multiMotion.isChecked();
                    next.fastMotion = fastMotion.isChecked();
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
        if (status != null) {
            phoneSettingsStatus = status;
            layoutDialog.setOnDismissListener(dialog -> {
                if (phoneSettingsStatus == status) phoneSettingsStatus = null;
            });
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
