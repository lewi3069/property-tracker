package com.fieldview.tv;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class FloatingViewControllerTest {
    private FloatingViewController controller() { return new FloatingViewController(Arrays.asList("default", "yard", "barn"), "default"); }

    @Test public void motionEndsBackAtSelectedDefault() {
        FloatingViewController c = controller();
        assertEquals("default", c.camera(0));
        assertTrue(c.activity("yard", true, 1000, 31000));
        assertEquals("yard", c.camera(1000));
        assertTrue(c.activity("yard", false, 2000, 32000));
        assertEquals("default", c.camera(2000));
    }

    @Test public void missingOffMessageExpiresSafely() {
        FloatingViewController c = controller(); c.activity("yard", true, 0, 30000);
        assertEquals("yard", c.camera(29999)); assertEquals("default", c.camera(30000));
    }

    @Test public void heartbeatsHoldCurrentCameraAndExtendItsDeadline() {
        FloatingViewController c = controller(); c.activity("yard", true, 0, 30000);
        assertEquals("yard", c.camera(0));
        c.activity("barn", true, 1000, 31000); c.activity("yard", true, 10000, 40000);
        assertEquals("yard", c.camera(30001));
        assertEquals("default", c.camera(40000));
    }

    @Test public void anotherActiveCameraContinuesUntilAllMotionEnds() {
        FloatingViewController c = controller(); c.activity("yard", true, 0, 30000);
        assertEquals("yard", c.camera(0)); c.activity("barn", true, 1000, 31000);
        assertEquals("yard", c.camera(1000)); c.activity("yard", false, 2000, 32000);
        assertEquals("barn", c.camera(2000)); c.activity("barn", false, 3000, 33000);
        assertEquals("default", c.camera(3000));
    }

    @Test public void defaultMotionDoesNotHideOtherCameraActivity() {
        FloatingViewController c = controller(); c.activity("default", true, 0, 30000);
        c.activity("yard", true, 1000, 31000); assertEquals("yard", c.camera(1000));
        c.activity("yard", false, 2000, 32000); assertEquals("default", c.camera(2000));
    }

    @Test public void explicitSelectionBecomesTheNewSessionDefault() {
        FloatingViewController c = controller(); c.activity("yard", true, 0, 30000); c.camera(0);
        assertTrue(c.selectDefault("barn")); assertEquals("barn", c.camera(1000));
        c.activity("yard", true, 2000, 32000); assertEquals("yard", c.camera(2000));
        assertEquals("barn", c.camera(32000));
        assertFalse(c.selectDefault("disabled")); assertFalse(c.activity("disabled", true, 0, 1000));
        assertFalse(c.activity("yard", true, 1000, 1000)); assertFalse(c.activity("yard", true, 1000, 999999));
    }
}
