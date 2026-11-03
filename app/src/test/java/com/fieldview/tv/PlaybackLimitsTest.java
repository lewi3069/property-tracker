package com.fieldview.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackLimitsTest {
    @Test public void affectedFirestickCannotOfferOrRestoreNineFeeds() {
        PlaybackLimits limits = PlaybackLimits.forDevice("AFTMM", false);
        assertEquals(4, limits.clamp(9));
        assertArrayEquals(new int[]{1, 2, 4}, limits.layoutCounts());
    }

    @Test public void lowRamDevicesUseTheSameConservativeLimit() {
        assertEquals(4, PlaybackLimits.forDevice("another-tv", true).clamp(6));
    }

    @Test public void otherDevicesRetainLargerLayouts() {
        assertArrayEquals(new int[]{1, 2, 4, 6, 9}, PlaybackLimits.forDevice("SHIELD Android TV", false).layoutCounts());
        assertEquals(2, PlaybackLimits.forDevice("AFTMM", false).clamp(2));
    }
}
