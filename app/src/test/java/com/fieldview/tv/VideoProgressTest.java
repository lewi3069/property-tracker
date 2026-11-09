package com.fieldview.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class VideoProgressTest {
    @Test public void twoSecondNativeSamplesRemainHealthyAndStillDetectStoppedPresentation() {
        VideoProgress progress = new VideoProgress(0);
        progress.rendered(500);
        for (int t = 500; t <= 20_500; t += 2_000) {
            progress.frame(t);
            assertFalse(progress.stalled(t + 1_999));
        }
        assertTrue(progress.sustained());
        assertFalse(progress.stalled(40_499));
        assertTrue(progress.stalled(40_500));
    }
    @Test public void startupRequiresAnActualRenderedFrame() {
        VideoProgress progress = new VideoProgress(1_000);
        progress.frame(20_000);
        assertFalse(progress.stalled(20_999)); assertTrue(progress.stalled(21_000));
        assertFalse(progress.sustained());
    }
    @Test public void firstFrameDoesNotDisableLaterStallDetection() {
        VideoProgress progress = new VideoProgress(0); progress.frame(1_000); progress.rendered(1_000);
        assertFalse(progress.stalled(20_999)); assertTrue(progress.stalled(21_000));
        assertFalse(progress.sustained());
    }
    @Test public void frameProgressKeepsTheStreamHealthyAndResetsBackoffAfterTenSeconds() {
        VideoProgress progress = new VideoProgress(0); progress.rendered(0);
        for (int t = 0; t <= 30_000; t += 1_000) { progress.frame(t); assertFalse(progress.stalled(t + 1_000)); }
        assertTrue(progress.sustained());
    }
    @Test public void longFrameGapRestartsTheStabilityWindow() {
        VideoProgress progress = new VideoProgress(0); progress.rendered(0);
        progress.frame(0); progress.frame(4_000); progress.frame(8_000); progress.frame(12_000);
        assertTrue(progress.sustained());
        progress.frame(18_000); assertFalse(progress.sustained());
        progress.frame(22_000); progress.frame(26_000); progress.frame(30_000); assertTrue(progress.sustained());
    }
    @Test public void oldFrameTimestampsCannotMoveTheWatchdogBackwards() {
        VideoProgress progress = new VideoProgress(1_000); progress.rendered(1_000);
        progress.frame(5_000); progress.frame(2_000);
        assertEquals(1_000, progress.silentFor(6_000));
        assertFalse(progress.stalled(24_999)); assertTrue(progress.stalled(25_000));
        assertEquals(0, progress.silentFor(0));
    }
}
