package com.fieldview.tv;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CenterStreamBackoffTest {
    @Test public void unsupportedFormatReasonSurvivesMotionReturnsAndClearsWithTheFailure() {
        CenterStreamBackoff backoff = new CenterStreamBackoff(); AppConfig.Camera camera = new AppConfig.Camera();
        camera.id = "panorama"; camera.host = "192.0.2.10";
        backoff.failed(camera, 1000, true); assertTrue(backoff.formatUnsupported(camera, 2000));
        camera.mainPath = "/changed"; assertFalse(backoff.formatUnsupported(camera, 2001));
        backoff.failed(camera, 3000, true); assertFalse(backoff.formatUnsupported(camera, 3000 + CenterStreamBackoff.COOLDOWN_MS));
        backoff.failed(camera, 400000, true); backoff.clear(); assertFalse(backoff.formatUnsupported(camera, 400001));
        backoff.failed(camera, 500000); assertFalse(backoff.formatUnsupported(camera, 500001)); assertTrue(backoff.blocked(camera, 500001));
    }
    @Test public void cooldownIsPerCameraExpiresAndChangedSettingsCanRetry() {
        CenterStreamBackoff backoff = new CenterStreamBackoff();
        AppConfig.Camera first = new AppConfig.Camera(); first.id = "first"; first.host = "192.0.2.10";
        AppConfig.Camera other = new AppConfig.Camera(); other.id = "other"; other.host = "192.0.2.11";
        backoff.failed(first, 1000);
        assertTrue(backoff.blocked(first, 2000)); assertFalse(backoff.blocked(other, 2000));
        assertFalse(backoff.blocked(first, 1000 + CenterStreamBackoff.COOLDOWN_MS));
        backoff.failed(first, 400000); first.mainPath = "/new-main";
        assertFalse(backoff.blocked(first, 400001));
        backoff.failed(first, 400002); backoff.clear(); assertFalse(backoff.blocked(first, 400003));
    }
}
