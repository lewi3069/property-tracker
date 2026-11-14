package com.fieldview.tv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class MultiMotionLayoutTest {
    @Test public void singlePanoramaUsesFiveButMultipleMotionAndClearRestoreTheirLayouts() {
        ViewSelector s = selector(); s.setPanoramicCamera("camera-12", true);
        s.activity("camera-12", 0, 100000); assertShape(s, 0, 1, 5);
        s.activity("camera-11", 1000, 101000); s.snapshot(1000); assertShape(s, 3000, 2, 10);
        s.clearActivity("camera-11", 4000); s.snapshot(4000); assertShape(s, 19000, 1, 5);
        s.clearActivity("camera-12", 20000); assertShape(s, 34999, 1, 5);
        assertEquals(13, s.snapshot(35000).visible.size());
    }
    private ViewSelector selector() {
        List<String> ids = new ArrayList<>(); for (int i = 0; i < 13; i++) ids.add("camera-" + i);
        ViewSelector s = new ViewSelector(ids, Collections.emptyMap(), 13, 13);
        s.setDefaultCamera("camera-1"); s.setMotionLayoutEight(true); s.setMultiMotionLayout(true); return s;
    }
    @Test public void expandsThroughEightTenSevenAndFourThenRestoresTheSameThirteen() {
        ViewSelector s = selector(); List<String> original = s.snapshot(0).visible;
        s.activity("camera-12", 1000, 101000); assertShape(s, 1000, 1, 8);
        s.activity("camera-11", 2000, 102000); assertShape(s, 2000, 1, 8);
        assertShape(s, 3999, 1, 8); assertShape(s, 4000, 2, 10);
        assertEquals(Arrays.asList("camera-12", "camera-11"), s.snapshot(4000).featured);
        s.activity("camera-10", 5000, 105000); assertShape(s, 5000, 2, 10);
        assertShape(s, 7000, 3, 7); assertEquals(3, s.snapshot(7000).featured.size());
        assertTrue(s.snapshot(7000).visible.containsAll(Arrays.asList("camera-12", "camera-11", "camera-10")));
        s.activity("camera-9", 8000, 108000); assertShape(s, 8000, 3, 7); assertShape(s, 10000, 4, 4);
        for (int i = 9; i < 13; i++) s.clearActivity("camera-" + i, 11000);
        assertShape(s, 25999, 4, 4); assertEquals(original, s.snapshot(26000).visible);
        assertEquals(0, s.snapshot(26000).motionLayoutSlots);
    }
    @Test public void briefExtraDetectionDoesNotExpandAndRepeatedCountFlipsRestartTheDelay() {
        ViewSelector s = selector(); s.activity("camera-12", 0, 100000); s.snapshot(0);
        s.activity("camera-11", 1000, 101000); s.snapshot(1000);
        s.clearActivity("camera-11", 2999); assertShape(s, 2999, 1, 8);
        s.activity("camera-11", 4000, 104000); assertShape(s, 4000, 1, 8);
        assertShape(s, 5999, 1, 8); assertShape(s, 6000, 2, 10);
    }
    @Test public void lowerCountsMustStayLowerForFifteenSecondsAndActiveSlotsSurvive() {
        ViewSelector s = selector();
        for (int i = 10; i < 13; i++) s.activity("camera-" + i, 0, 100000);
        assertShape(s, 0, 3, 7);
        s.clearActivity("camera-11", 1000); assertShape(s, 1000, 3, 7);
        assertShape(s, 15999, 3, 7); assertShape(s, 16000, 2, 10);
        assertTrue(s.snapshot(16000).featured.containsAll(Arrays.asList("camera-10", "camera-12")));
        s.clearActivity("camera-10", 17000); assertShape(s, 17000, 2, 10);
        assertShape(s, 31999, 2, 10); assertShape(s, 32000, 1, 8);
        assertEquals(Collections.singletonList("camera-12"), s.snapshot(32000).featured);
    }
    @Test public void renewedActivityCancelsADownshiftAndAllClearTimerIsNotRestarted() {
        ViewSelector s = selector(); s.activity("camera-12", 0, 100000); s.activity("camera-11", 0, 100000); s.snapshot(0);
        s.clearActivity("camera-11", 1000); s.snapshot(1000);
        s.activity("camera-11", 15000, 115000); assertShape(s, 15000, 2, 10);
        assertShape(s, 16000, 2, 10);
        s.clearActivity("camera-11", 17000); s.clearActivity("camera-12", 17000); s.snapshot(17000);
        s.clearActivity("camera-12", 30000); assertShape(s, 31999, 2, 10);
        assertEquals(13, s.snapshot(32000).visible.size());
    }
    @Test public void slotPositionsStayStableAndFifthDetectionWaitsForMinimumDwell() {
        MultiMotionLayout m = new MultiMotionLayout();
        List<String> four = Arrays.asList("a", "b", "c", "d");
        assertEquals(four, m.update(0, four, Collections.emptyList()).featured);
        List<String> five = Arrays.asList("e", "a", "b", "c", "d");
        assertEquals(four, m.update(9999, five, Collections.emptyList()).featured);
        assertEquals(Arrays.asList("a", "b", "c", "e"), m.update(10000, five, Collections.emptyList()).featured);
        assertEquals(Arrays.asList("a", "b", "c", "e"), m.update(10001, Arrays.asList("c", "e", "b", "a"), Collections.emptyList()).featured);
    }
    @Test public void quietGapRestartsPendingExpansionAndClockRollbackCannotFreezeIt() {
        MultiMotionLayout m = new MultiMotionLayout();
        m.update(0, Arrays.asList("a"), Collections.emptyList());
        assertEquals(1, m.update(1000, Arrays.asList("a", "b"), Collections.emptyList()).slots);
        m.update(1500, Collections.emptyList(), Collections.emptyList());
        assertEquals(1, m.update(4000, Arrays.asList("a", "b"), Collections.emptyList()).slots);
        assertEquals(2, m.update(6000, Arrays.asList("a", "b"), Collections.emptyList()).slots);
        m.update(7000, Arrays.asList("a", "b", "c"), Collections.emptyList());
        assertEquals(2, m.update(1000, Arrays.asList("a", "b", "c"), Collections.emptyList()).slots);
        assertEquals(3, m.update(3000, Arrays.asList("a", "b", "c"), Collections.emptyList()).slots);
    }
    @Test public void expiredEventsAndManualOrCatalogChangesCannotLeaveAStaleMultiLayout() {
        ViewSelector s = selector(); s.activity("camera-12", 0, 5000); s.activity("camera-11", 0, 5000); assertShape(s, 0, 2, 10);
        assertEquals(13, s.snapshot(60000).visible.size());
        s.activity("camera-12", 61000, 100000); s.activity("camera-11", 61000, 100000); assertShape(s, 61000, 2, 10);
        s.setMode(ViewSelector.GRID, null); assertEquals(0, s.snapshot(62000).motionLayoutSlots);
        s.setMode(ViewSelector.PIN, "camera-2"); assertEquals(1, s.snapshot(62001).visible.size());
        s.setMode(ViewSelector.AUTO, null); assertShape(s, 62002, 2, 10);
        s.reconfigure(Collections.singletonList("camera-1"), Collections.emptyMap());
        assertEquals(Collections.singletonList("camera-1"), s.snapshot(62003).visible);
        assertEquals(0, s.snapshot(62003).motionLayoutSlots);
    }
    @Test public void profileAndOptInGuardsKeepExistingLayouts() {
        ViewSelector s = selector(); s.setMultiMotionLayout(false);
        s.activity("camera-12", 0, 100000); s.activity("camera-11", 0, 100000); assertShape(s, 20000, 1, 8);
        s.setMultiMotionLayout(true); s.setMotionLayoutEight(false); assertEquals(0, s.snapshot(20001).motionLayoutSlots);
        s.setMotionLayoutEight(true); s.setCapacity(9); assertEquals(9, s.snapshot(20002).visible.size());
        assertEquals(0, s.snapshot(20002).motionLayoutSlots);
    }
    private void assertShape(ViewSelector s, long now, int slots, int count) {
        ViewSelector.Selection state = s.snapshot(now);
        assertEquals(slots, state.motionLayoutSlots); assertEquals(count, state.visible.size());
        assertEquals(count, new java.util.HashSet<>(state.visible).size());
    }
}
