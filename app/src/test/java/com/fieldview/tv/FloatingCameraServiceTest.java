package com.fieldview.tv;

import android.content.Intent;
import android.content.ContextWrapper;
import android.os.Bundle;
import android.os.Looper;
import android.os.ResultReceiver;
import android.util.DisplayMetrics;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import androidx.media3.ui.PlayerView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class FloatingCameraServiceTest {
    public static class TestService extends FloatingCameraService {
        boolean permitted = true, screenOn = true;
        final AppConfig config = new AppConfig();
        String played;
        CameraTile view;
        public TestService() {
            AppConfig.Camera camera = new AppConfig.Camera(); camera.id = "one"; camera.name = "Test camera";
            camera.host = "192.0.2.10"; camera.subPath = "/sub"; camera.mainPath = "/main"; config.cameras.add(camera);
        }
        @Override protected boolean canDrawOverlays() { return permitted; }
        @Override protected boolean isScreenOn() { return screenOn; }
        @Override protected AppConfig loadConfig() { return config; }
        @Override protected void playStream(CameraTile tile, String uri) { played = uri; view = tile; }
    }

    private Intent request(TestService service, int[] result) {
        return new Intent(service, FloatingCameraService.class).setAction(FloatingCameraService.ACTION_START)
                .putExtra(FloatingCameraService.EXTRA_CAMERA, "one")
                .putExtra(FloatingCameraService.EXTRA_CALLBACK, new ResultReceiver(null) {
                    @Override protected void onReceiveResult(int code, Bundle data) { result[0] = code; }
                });
    }

    @Test public void windowDoesNotTakeRemoteFocusAndFitsTheScreen() {
        DisplayMetrics display = new DisplayMetrics(); display.widthPixels = 1920; display.heightPixels = 1080; display.density = 2;
        WindowManager.LayoutParams old = FloatingCameraService.windowLayout(display, 25);
        assertEquals(WindowManager.LayoutParams.TYPE_PHONE, old.type);
        assertEquals(640, old.width); assertEquals(360, old.height);
        assertTrue(old.width + old.x * 2 <= display.widthPixels);
        assertTrue(old.height + old.y * 2 <= display.heightPixels);
        assertNotEquals(0, old.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, FloatingCameraService.windowLayout(display, 28).type);
    }

    @Test public void permissionIsRequiredBeforeOpeningCamera() {
        ServiceController<TestService> controller = Robolectric.buildService(TestService.class).create();
        try {
            TestService service = controller.get(); service.permitted = false; int[] result = {-1};
            service.onStartCommand(request(service, result), 0, 1);
            assertEquals(FloatingCameraService.FAILED, result[0]); assertNull(service.played);
        } finally { controller.destroy(); }
    }

    @Test public void disabledCameraCannotStartInTheBackground() {
        ServiceController<TestService> controller = Robolectric.buildService(TestService.class).create();
        try {
            TestService service = controller.get(); service.config.cameras.get(0).enabled = false; int[] result = {-1};
            service.onStartCommand(request(service, result), 0, 1);
            assertEquals(FloatingCameraService.FAILED, result[0]); assertNull(service.played);
        } finally { controller.destroy(); }
    }

    @Test public void floatingViewUsesSubstreamAndClosesWhenReturningToViewer() {
        ServiceController<TestService> controller = Robolectric.buildService(TestService.class).create();
        try {
            TestService service = controller.get(); int[] result = {-1};
            assertEquals(TestService.START_REDELIVER_INTENT, service.onStartCommand(request(service, result), 0, 1));
            assertEquals(FloatingCameraService.STARTED, result[0]);
            assertEquals("rtsp://192.0.2.10:554/sub", service.played);
            assertFalse(service.view.isFocusable());
            PlayerView player = (PlayerView) service.view.getChildAt(0);
            assertTrue(player.getVideoSurfaceView() instanceof SurfaceView);
            assertEquals(View.GONE, service.view.getChildAt(1).getVisibility());
            service.view.setFullscreen(true); service.view.setFullscreen(false);
            assertEquals(View.GONE, service.view.getChildAt(1).getVisibility());
            shadowOf(Looper.getMainLooper()).idle();
            assertTrue(service.view.isAttachedToWindow());
            FloatingCameraService.closeActive(service);
            shadowOf(Looper.getMainLooper()).idle();
            assertFalse(service.view.isAttachedToWindow());
        } finally { controller.destroy(); }
    }

    @Test public void redeliveryRestoresCameraWithoutCallingOldActivity() {
        ServiceController<TestService> controller = Robolectric.buildService(TestService.class).create();
        try {
            TestService service = controller.get(); int[] result = {-1};
            assertEquals(TestService.START_REDELIVER_INTENT,
                    service.onStartCommand(request(service, result), TestService.START_FLAG_REDELIVERY, 2));
            assertEquals("rtsp://192.0.2.10:554/sub", service.played);
            assertEquals(-1, result[0]);
            shadowOf(Looper.getMainLooper()).idle(); assertTrue(service.view.isAttachedToWindow());
        } finally { controller.destroy(); }
    }

    @Test public void redeliveryRechecksWhetherCameraIsEnabled() {
        ServiceController<TestService> controller = Robolectric.buildService(TestService.class).create();
        try {
            TestService service = controller.get(); service.config.cameras.get(0).enabled = false;
            assertEquals(TestService.START_NOT_STICKY,
                    service.onStartCommand(request(service, new int[]{-1}), TestService.START_FLAG_REDELIVERY, 2));
            assertNull(service.played); assertTrue(shadowOf(service).isStoppedBySelf());
        } finally { controller.destroy(); }
    }

    @Test public void redeliveryDoesNotResumeOnSleepingTv() {
        ServiceController<TestService> controller = Robolectric.buildService(TestService.class).create();
        try {
            TestService service = controller.get(); service.screenOn = false;
            assertEquals(TestService.START_NOT_STICKY,
                    service.onStartCommand(request(service, new int[]{-1}), TestService.START_FLAG_REDELIVERY, 2));
            assertNull(service.played); assertTrue(shadowOf(service).isStoppedBySelf());
        } finally { controller.destroy(); }
    }

    @Test public void explicitCloseStopsSessionInsteadOfRequestingRedelivery() {
        ServiceController<TestService> controller = Robolectric.buildService(TestService.class).create();
        try {
            TestService service = controller.get(); service.onStartCommand(request(service, new int[]{-1}), 0, 1);
            shadowOf(Looper.getMainLooper()).idle(); assertTrue(service.view.isAttachedToWindow());
            assertEquals(TestService.START_NOT_STICKY, service.onStartCommand(
                    new Intent(service, FloatingCameraService.class).setAction(FloatingCameraService.ACTION_CLOSE), 0, 2));
            shadowOf(Looper.getMainLooper()).idle(); assertFalse(service.view.isAttachedToWindow());
            assertTrue(shadowOf(service).isStoppedBySelf());
        } finally { controller.destroy(); }
    }

    @Test public void returningToViewerCancelsRestartEvenWithoutLiveServiceInstance() {
        ServiceController<TestService> controller = Robolectric.buildService(TestService.class).create();
        TestService service = controller.get();
        controller.destroy(); // As after process loss, there is no active Java service reference.
        final Intent[] stopped = {null};
        FloatingCameraService.closeActive(new ContextWrapper(service.getApplicationContext()) {
            @Override public boolean stopService(Intent intent) { stopped[0] = intent; return true; }
        });
        assertNotNull(stopped[0]);
        assertEquals(FloatingCameraService.class.getName(), stopped[0].getComponent().getClassName());
    }
}
