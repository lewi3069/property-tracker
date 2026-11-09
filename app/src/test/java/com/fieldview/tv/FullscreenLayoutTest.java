package com.fieldview.tv;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.CheckBox;
import androidx.media3.exoplayer.ExoPlayer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, instrumentedPackages = "com.fieldview.tv",
        shadows = {FullscreenLayoutTest.NoVideo.class, FullscreenLayoutTest.MemoryStore.class})
public class FullscreenLayoutTest {
    // Exercise real rendering, selection, and player reuse without opening network streams or a Keystore.
    @Implements(value = ConfigStore.class, isInAndroidSdk = false)
    public static class MemoryStore {
        static AppConfig saved;
        @Implementation protected AppConfig load() { return saved == null ? new AppConfig() : saved; }
        @Implementation protected void save(AppConfig config) { }
    }

    @Implements(value = CameraTile.class, isInAndroidSdk = false)
    public static class NoVideo extends org.robolectric.shadows.ShadowViewGroup {
        @RealObject CameraTile tile;
        static int starts;
        @Implementation protected void startPlayer() throws Exception {
            starts++;
            ExoPlayer player = (ExoPlayer) Proxy.newProxyInstance(ExoPlayer.class.getClassLoader(),
                    new Class<?>[]{ExoPlayer.class}, (proxy, method, args) -> null);
            field(CameraTile.class, "player").set(tile, player);
            Method ready = CameraTile.class.getDeclaredMethod("finishStartup"); ready.setAccessible(true); ready.invoke(tile);
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private void invoke(MainActivity activity, String name) throws Exception {
        Method method = MainActivity.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(activity);
        shadowOf(Looper.getMainLooper()).idle();
    }
    @SuppressWarnings("unchecked") private Map<String, CameraTile> tiles(MainActivity activity) throws Exception {
        return (Map<String, CameraTile>) field(MainActivity.class, "tiles").get(activity);
    }
    private ViewSelector selector(MainActivity activity) throws Exception {
        return (ViewSelector) field(MainActivity.class, "selector").get(activity);
    }
    @Test public void freshTvLaunchFillsSelectedLayoutAndBackSurvivesRecreation() throws Exception {
        AppConfig saved = new AppConfig(); saved.visibleCount = 4; saved.useMainStream = false;
        for (int i = 0; i < 4; i++) {
            AppConfig.Camera camera = new AppConfig.Camera(); camera.id = "camera-" + i;
            camera.name = "Camera " + i; camera.host = "192.0.2.10"; saved.cameras.add(camera);
        }
        MemoryStore.saved = saved; NoVideo.starts = 0;
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup().visible().windowFocusChanged(true);
        try {
            shadowOf(Looper.getMainLooper()).idle(); MainActivity activity = controller.get();
            assertFullLayout(activity, 4);
            CameraTile first = tiles(activity).get("camera-0"); int starts = NoVideo.starts;
            activity.onBackPressed(); shadowOf(Looper.getMainLooper()).idle();
            assertEquals(View.VISIBLE, activity.findViewById(R.id.viewer_toolbar).getVisibility());
            assertSame(first, tiles(activity).get("camera-0")); assertEquals(starts, NoVideo.starts);
            controller.recreate(); shadowOf(Looper.getMainLooper()).idle();
            assertEquals(View.VISIBLE, controller.get().findViewById(R.id.viewer_toolbar).getVisibility());
        } finally { controller.pause().stop().destroy(); MemoryStore.saved = null; }
    }
    @Test public void firstLaunchWithNoCamerasKeepsSetupControlsVisible() {
        MemoryStore.saved = null;
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
        try {
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(View.VISIBLE, controller.get().findViewById(R.id.viewer_toolbar).getVisibility());
        } finally { controller.pause().stop().destroy(); }
    }
    private ActivityController<MainActivity> open(int count, String model) throws Exception {
        org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.class, "MODEL", model);
        NoVideo.starts = 0;
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), MainActivity.class)
                .putExtra(MainActivity.EXTRA_SETUP_ONLY, true);
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class, intent).create();
        MainActivity activity = controller.get(); AppConfig config = new AppConfig();
        config.visibleCount = count; config.useMainStream = false;
        for (int i = 0; i < 9; i++) {
            AppConfig.Camera camera = new AppConfig.Camera(); camera.id = "camera-" + i;
            camera.name = "Camera " + i; camera.host = "192.0.2.10"; config.cameras.add(camera);
        }
        field(MainActivity.class, "config").set(activity, config);
        field(MainActivity.class, "playbackLimits").set(activity, PlaybackLimits.forDevice(model, false));
        invoke(activity, "resetSelector");
        controller.start().resume().visible().windowFocusChanged(true);
        return controller;
    }
    @Test public void catalogEditsReuseUnchangedPlayersAndKeepAValidPinnedCamera() throws Exception {
        ActivityController<MainActivity> controller = open(2, "SHIELD");
        try {
            MainActivity activity = controller.get(); field(MainActivity.class,"setupOnly").set(activity,false); invoke(activity,"render");
            CameraTile original = tiles(activity).get("camera-0"); int starts = NoVideo.starts;
            AppConfig config = (AppConfig) field(MainActivity.class,"config").get(activity);
            config.cameras.get(8).name = "Hidden camera renamed"; invoke(activity,"resetSelector"); invoke(activity,"render");
            assertSame(original,tiles(activity).get("camera-0")); assertEquals(starts,NoVideo.starts);
            selector(activity).setMode(ViewSelector.PIN,"camera-0"); invoke(activity,"render");
            config.cameras.get(0).name = "Renamed visible camera"; invoke(activity,"resetSelector"); invoke(activity,"render");
            assertSame(original,tiles(activity).get("camera-0")); assertEquals(starts,NoVideo.starts);
            assertEquals(ViewSelector.PIN,selector(activity).snapshot(System.currentTimeMillis()).mode);
            assertEquals("Enlarge Renamed visible camera",original.getContentDescription());
            config.cameras.get(0).subPath = "/changed"; invoke(activity,"resetSelector"); invoke(activity,"render");
            assertEquals(starts+1,NoVideo.starts);
            config.cameras.get(0).enabled=false; invoke(activity,"resetSelector"); invoke(activity,"render");
            assertFalse(tiles(activity).containsKey("camera-0"));
        } finally { controller.pause().stop().destroy(); }
    }

    private void saveFullLayout(MainActivity activity) throws Exception {
        invoke(activity, "showLayout");
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        ((CheckBox) dialog.findViewById(R.id.layout_fullscreen)).setChecked(true);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
    }
    private void measure(MainActivity activity) {
        View root = activity.findViewById(R.id.viewer_root);
        root.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1920, 1080);
    }
    private void assertFullLayout(MainActivity activity, int count) throws Exception {
        measure(activity);
        CameraWall wall = activity.findViewById(R.id.camera_wall);
        assertEquals(View.GONE, activity.findViewById(R.id.viewer_toolbar).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.viewer_footer).getVisibility());
        assertEquals(1920, wall.getWidth()); assertEquals(1080, wall.getHeight());
        assertEquals(count, wall.getChildCount()); assertEquals(count, tiles(activity).size());
        for (int i = 0; i < count; i++) {
            View tile = wall.getChildAt(i);
            assertTrue(tile.getWidth() > 0); assertTrue(tile.getHeight() > 0);
            assertTrue(tile.getLeft() >= 0 && tile.getRight() <= wall.getWidth());
            assertTrue(tile.getTop() >= 0 && tile.getBottom() <= wall.getHeight());
        }
    }

    @Test public void twoFourAndNineCameraLayoutsFillTheDisplayAndBackReusesPlayers() throws Exception {
        for (int count : new int[]{2, 4, 9}) {
            ActivityController<MainActivity> controller = open(count, "SHIELD");
            try {
                MainActivity activity = controller.get(); saveFullLayout(activity);
                assertFullLayout(activity, count); assertEquals(count, NoVideo.starts);
                CameraTile first = tiles(activity).get("camera-0");
                assertTrue(first.hasFocus()); assertTrue(first.getPaddingLeft() > 0);
                activity.onBackPressed(); measure(activity);
                assertFalse(activity.isFinishing());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.viewer_toolbar).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.viewer_footer).getVisibility());
                assertSame(first, tiles(activity).get("camera-0"));
                assertEquals(count, NoVideo.starts);
            } finally { controller.pause().stop().destroy(); }
        }
    }

    @Test public void pinAndBackReturnToTheSameFullscreenGridAndDirectionsStillNavigateTiles() throws Exception {
        ActivityController<MainActivity> controller = open(4, "SHIELD");
        try {
            MainActivity activity = controller.get(); selector(activity).setMode(ViewSelector.GRID, null);
            saveFullLayout(activity); assertFullLayout(activity, 4);
            CameraTile first = tiles(activity).get("camera-0"); first.requestFocus();
            // ViewRoot performs directional focus navigation after the Activity declines the key.
            assertFalse(activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)));
            View next = first.focusSearch(View.FOCUS_DOWN);
            assertTrue(next instanceof CameraTile); next.requestFocus();
            assertFalse(first.hasFocus());
            assertEquals(ViewSelector.GRID, selector(activity).snapshot(0).mode);
            first.performClick(); measure(activity);
            assertEquals(ViewSelector.PIN, selector(activity).snapshot(0).mode);
            assertEquals(1, tiles(activity).size()); assertEquals(0, first.getPaddingLeft());
            activity.onBackPressed(); assertFullLayout(activity, 4);
            assertEquals(ViewSelector.GRID, selector(activity).snapshot(0).mode);
            invoke(activity, "showLayout");
            View floatButton = ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEUTRAL);
            assertTrue(floatButton == null || floatButton.getVisibility() != View.VISIBLE);
        } finally { controller.pause().stop().destroy(); }
    }

    @Test public void fullLayoutCannotBypassTheFourCameraFirestickLimit() throws Exception {
        ActivityController<MainActivity> controller = open(4, "AFTMM");
        try {
            MainActivity activity = controller.get(); saveFullLayout(activity);
            selector(activity).setCapacity(9); invoke(activity, "render");
            assertFullLayout(activity, 4); assertEquals(4, NoVideo.starts);
        } finally { controller.pause().stop().destroy(); }
    }

    @Test public void karatSavedAndRemoteNineFeedRequestsKeepFourSurfacesAndTheFullCatalog() throws Exception {
        ActivityController<MainActivity> controller = open(9, "AFTKM");
        try {
            MainActivity activity = controller.get();
            field(MainActivity.class, "setupOnly").set(activity, false);
            field(MainActivity.class, "layoutFullscreen").set(activity, true);
            invoke(activity, "render");
            assertFullLayout(activity, 4);
            CommandRouter router = (CommandRouter) field(MainActivity.class, "router").get(activity);
            long now = System.currentTimeMillis();
            assertTrue(router.accept("{\"type\":\"layout\",\"max_visible\":9,\"expires_at\":" + (now + 30000) + "}", false, now));
            invoke(activity, "render"); assertFullLayout(activity, 4);
            AppConfig config = (AppConfig) field(MainActivity.class, "config").get(activity);
            assertEquals(9, config.visibleCount);
            saveFullLayout(activity);
            assertEquals(9, ((AppConfig) field(MainActivity.class, "config").get(activity)).visibleCount);
            assertEquals(9, config.cameras.size());
            selector(activity).setMode(ViewSelector.PIN, "camera-8"); invoke(activity, "render");
            assertEquals(1, tiles(activity).size()); assertTrue(tiles(activity).containsKey("camera-8"));
            selector(activity).setMode(ViewSelector.AUTO, null); invoke(activity, "render");
            assertFullLayout(activity, 4);
        } finally { controller.pause().stop().destroy(); }
    }
    @Test public void textureGridHandsOffToSurfaceForSingleCameraAndBackWithoutChangingBudget() throws Exception {
        ActivityController<MainActivity> controller = open(9, "AFTKM");
        try {
            MainActivity activity = controller.get();
            AppConfig config = (AppConfig) field(MainActivity.class, "config").get(activity);
            config.textureGrid = true; field(MainActivity.class, "setupOnly").set(activity, false);
            invoke(activity, "render"); assertEquals(4, tiles(activity).size());
            CameraTile grid = tiles(activity).get("camera-0"); assertTrue(grid.usesTexture());
            grid.performClick(); shadowOf(Looper.getMainLooper()).idle();
            CameraTile single = tiles(activity).get("camera-0");
            assertNotSame(grid, single); assertFalse(single.usesTexture());
            assertNull(field(CameraTile.class, "player").get(grid));
            activity.onBackPressed(); shadowOf(Looper.getMainLooper()).idle();
            assertEquals(4, tiles(activity).size()); assertTrue(tiles(activity).get("camera-0").usesTexture());
            assertNull(field(CameraTile.class, "player").get(single));
        } finally { controller.pause().stop().destroy(); }
    }
    @Test public void thirteenCameraCenterAndPerimeterFillTheScreenWithoutOverlappingAndPinUsesSurface() throws Exception {
        ActivityController<MainActivity> controller = open(13,"AFTKM");
        try {
            MainActivity activity=controller.get(); AppConfig config=(AppConfig)field(MainActivity.class,"config").get(activity);
            for(int i=9;i<14;i++) { AppConfig.Camera camera=new AppConfig.Camera();camera.id="camera-"+i;camera.name="Camera "+i;camera.host="192.0.2.10";config.cameras.add(camera); }
            config.textureGrid=true;config.denseGrid=true;config.spotlightCamera="camera-1";
            field(MainActivity.class,"playbackLimits").set(activity,PlaybackLimits.forDevice("AFTKM",false,true));
            invoke(activity,"resetSelector");field(MainActivity.class,"setupOnly").set(activity,false);field(MainActivity.class,"layoutFullscreen").set(activity,true);invoke(activity,"render");
            assertFullLayout(activity,13);assertEquals("camera-1",selector(activity).snapshot(System.currentTimeMillis()).visible.get(0));
            CameraTile center=tiles(activity).get("camera-1");assertEquals(480,center.getLeft());assertEquals(270,center.getTop());assertEquals(960,center.getWidth());assertEquals(540,center.getHeight());
            long area=0;java.util.List<android.graphics.Rect> rectangles=new java.util.ArrayList<>();
            for(CameraTile tile:tiles(activity).values()) {
                assertTrue(tile.usesTexture());android.graphics.Rect r=new android.graphics.Rect(tile.getLeft(),tile.getTop(),tile.getRight(),tile.getBottom());
                for(android.graphics.Rect previous:rectangles)assertFalse(android.graphics.Rect.intersects(previous,r));
                rectangles.add(r);area+=(long)r.width()*r.height();
            }
            assertEquals(1920L*1080,area);
            center.performClick();shadowOf(Looper.getMainLooper()).idle();assertFalse(tiles(activity).get("camera-1").usesTexture());
            activity.onBackPressed();shadowOf(Looper.getMainLooper()).idle();assertFullLayout(activity,13);
            config.denseGrid=false; invoke(activity,"showLayout");
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle();
            assertFullLayout(activity,4);
        } finally {controller.pause().stop().destroy();}
    }
}
