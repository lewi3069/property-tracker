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
        selector.activity("camera-11", 3000, 20000);
        assertTrue(selector.snapshot(3000).visible.contains("camera-11"));
        selector.clearActivity("camera-12"); assertEquals(8, selector.snapshot(4000).visible.size());
        assertEquals("camera-11", selector.snapshot(4000).visible.get(0));
        selector.clearActivity("camera-11"); assertEquals(original, selector.snapshot(4001).visible);
        assertEquals("overview", selector.snapshot(4001).reason);
    }
    @Test public void refreshExtendsEventAndExpiryReturnsThirteenWithoutASecondCommand() {
        ViewSelector selector = selector(13, 13);
        selector.activity("camera-3", 1000, 5000); selector.snapshot(1000);
        selector.activity("camera-3", 4000, 9000);
        assertEquals(8, selector.snapshot(5000).visible.size()); assertEquals(13, selector.snapshot(9000).visible.size());
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
        selector.clearActivity("camera-12"); assertEquals("location", selector.snapshot(7001).reason); assertEquals(13, selector.snapshot(7001).visible.size());
    }
}
