package com.fieldview.tv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class MotionLayoutTest {
    private ViewSelector selector(int capacity, int max) {
        List<String> ids = new ArrayList<>(); for (int i = 0; i < 13; i++) ids.add("camera-" + i);
        ViewSelector selector = new ViewSelector(ids, Collections.emptyMap(), capacity, max);
        selector.setDefaultCamera("camera-1"); selector.setMotionLayoutEight(true); return selector;
    }
    @Test public void overviewThirteenMotionEightAndAllClearRestoresOriginalOrder() {
        ViewSelector selector = selector(13, 13);
        List<String> original = selector.snapshot(1000).visible;
        assertEquals(13, original.size()); assertEquals("camera-1", original.get(0));
        selector.activity("camera-12", 2000, 20000);
        ViewSelector.Selection active = selector.snapshot(2000);
        assertEquals(8, active.visible.size()); assertEquals(Collections.singletonList("camera-12"), active.featured);
        selector.activity("camera-11", 3000, 30000);
        assertTrue(selector.snapshot(3000).visible.contains("camera-11"));
        selector.clearActivity("camera-12", 4000); assertEquals(8, selector.snapshot(4000).visible.size());
        assertEquals("camera-12", selector.snapshot(11999).visible.get(0));
        assertEquals("camera-11", selector.snapshot(12000).visible.get(0));
        selector.clearActivity("camera-11", 13000);
        assertEquals(8, selector.snapshot(27999).visible.size());
        assertEquals(original, selector.snapshot(28000).visible);
        assertEquals("overview", selector.snapshot(28000).reason);
    }
    @Test public void refreshExtendsEventAndExpiryReturnsThirteenWithoutASecondCommand() {
        ViewSelector selector = selector(13, 13);
        selector.activity("camera-3", 1000, 5000); selector.snapshot(1000);
        selector.activity("camera-3", 4000, 9000);
        assertEquals(8, selector.snapshot(5000).visible.size()); assertEquals(8, selector.snapshot(23999).visible.size());
        assertEquals(13, selector.snapshot(24000).visible.size());
    }
    @Test public void gridPinOtherCapacitiesAndOptOutKeepTheirOriginalBehavior() {
        ViewSelector selector = selector(13, 13); selector.activity("camera-12", 0, 10000);
        selector.setMode(ViewSelector.GRID, null); assertEquals(13, selector.snapshot(0).visible.size());
        selector.setMode(ViewSelector.PIN, "camera-4"); assertEquals(Collections.singletonList("camera-4"), selector.snapshot(0).visible);
        selector.setMode(ViewSelector.AUTO, null); selector.setMotionLayoutEight(false); assertEquals(13, selector.snapshot(0).visible.size());
        ViewSelector limited = selector(13, 4); limited.activity("camera-12", 0, 10000); assertEquals(4, limited.snapshot(0).visible.size());
        selector.setMotionLayoutEight(true); selector.setCapacity(9); assertEquals(9, selector.snapshot(0).visible.size());
    }
    @Test public void horsePresenceDoesNotKeepTheMotionLayoutOpen() {
        List<String> ids = new ArrayList<>(); for (int i = 0; i < 13; i++) ids.add("camera-" + i);
        ViewSelector selector = new ViewSelector(ids, Collections.singletonMap("yard", Collections.singletonList("camera-4")), 13, 13);
        selector.setMotionLayoutEight(true); selector.location("horse", "yard", 1000, 1000);
        assertEquals("location", selector.snapshot(6000).reason); assertEquals(13, selector.snapshot(6000).visible.size());
        selector.activity("camera-12", 7000, 20000); assertEquals(8, selector.snapshot(7000).visible.size());
        selector.clearActivity("camera-12", 7001); assertEquals("activity", selector.snapshot(22000).reason);
        assertEquals("location", selector.snapshot(22001).reason); assertEquals(13, selector.snapshot(22001).visible.size());
    }

    @Test public void newMotionDuringQuietPeriodKeepsEightAndRestartsTheQuietDeadline() {
        ViewSelector selector = selector(13, 13);
        selector.activity("camera-12", 0, 30000); selector.snapshot(0);
        selector.clearActivity("camera-12", 1000); selector.snapshot(1000);
        selector.activity("camera-11", 12000, 42000);
        assertEquals("camera-11", selector.snapshot(12000).visible.get(0));
        selector.clearActivity("camera-11", 13000);
        assertEquals(8, selector.snapshot(16000).visible.size());
        assertEquals(8, selector.snapshot(27999).visible.size());
        assertEquals(13, selector.snapshot(28000).visible.size());
    }
    @Test public void repeatedOffAndUnknownOffCannotPostponeReturn() {
        ViewSelector selector = selector(13, 13);
        selector.activity("camera-12", 0, 30000); selector.snapshot(0);
        assertTrue(selector.clearActivity("camera-12", 1000)); selector.snapshot(1000);
        assertTrue(selector.clearActivity("camera-12", 14000));
        assertTrue(selector.clearActivity("camera-11", 14500));
        assertFalse(selector.clearActivity("missing", 15000));
        assertEquals(13, selector.snapshot(16000).visible.size());
    }
    @Test public void firstDetectionIsImmediateAndShortBurstsDoNotReplaceTheCenterBeforeTenSeconds() {
        ViewSelector selector = selector(13, 13); selector.snapshot(0);
        selector.activity("camera-12", 1000, 31000);
        assertEquals("camera-12", selector.snapshot(1000).visible.get(0));
        selector.clearActivity("camera-12", 2000);
        selector.activity("camera-11", 3000, 33000);
        assertEquals("camera-12", selector.snapshot(10999).visible.get(0));
        assertTrue(selector.snapshot(10999).visible.contains("camera-11"));
        assertEquals("camera-11", selector.snapshot(11000).visible.get(0));
        selector.activity("camera-10", 12000, 42000);
        assertEquals("camera-11", selector.snapshot(20999).visible.get(0));
        assertEquals("camera-10", selector.snapshot(21000).visible.get(0));
    }
    @Test public void skippedSnapshotsAndLateOffUseTheExpiryRatherThanInventingFreshMotion() {
        ViewSelector selector = selector(13, 13);
        selector.activity("camera-12", 0, 5000); selector.snapshot(0);
        assertEquals(13, selector.snapshot(60000).visible.size());
        selector.activity("camera-11", 61000, 66000); selector.snapshot(61000);
        selector.clearActivity("camera-11", 90000);
        assertEquals(13, selector.snapshot(90000).visible.size());
    }
    @Test public void lastOverlappingExpiryStartsTheQuietPeriod() {
        ViewSelector selector = selector(13, 13);
        selector.activity("camera-12", 0, 5000); selector.snapshot(0);
        selector.activity("camera-11", 1000, 12000);
        selector.clearActivity("camera-12", 2000);
        assertEquals(8, selector.snapshot(26999).visible.size());
        assertEquals(13, selector.snapshot(27000).visible.size());
    }
    @Test public void manualOverridesCatalogEditsAndOptOutImmediatelyEndTheQuietHold() {
        ViewSelector selector = selector(13, 13);
        selector.activity("camera-12", 0, 30000); selector.snapshot(0);
        selector.clearActivity("camera-12", 1000); selector.snapshot(1000);
        selector.setMode(ViewSelector.GRID, null); assertEquals(13, selector.snapshot(1001).visible.size());
        selector.setMode(ViewSelector.PIN, "camera-3"); assertEquals(Collections.singletonList("camera-3"), selector.snapshot(1002).visible);
        selector.setMode(ViewSelector.AUTO, null); assertEquals(13, selector.snapshot(1003).visible.size());
        selector.activity("camera-12", 2000, 32000); selector.snapshot(2000);
        selector.clearActivity("camera-12", 3000); selector.setMotionLayoutEight(false);
        assertEquals(13, selector.snapshot(3001).visible.size());
        selector.setMotionLayoutEight(true); selector.activity("camera-12", 4000, 34000); selector.snapshot(4000);
        selector.clearActivity("camera-12", 5000); selector.setCapacity(9);
        assertEquals(9, selector.snapshot(5001).visible.size());
        selector.setCapacity(13); selector.activity("camera-12", 6000, 36000); selector.snapshot(6000);
        selector.reconfigure(Collections.singletonList("camera-1"), Collections.emptyMap());
        assertEquals(Collections.singletonList("camera-1"), selector.snapshot(6001).visible);
    }
}
