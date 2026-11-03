package com.fieldview.tv;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class ViewSelectorTest {
    private ViewSelector make(int count) {
        Map<String, List<String>> zones = new LinkedHashMap<>();
        zones.put("barn", Collections.singletonList("barn"));
        zones.put("pasture", Collections.singletonList("pasture"));
        return new ViewSelector(Arrays.asList("barn", "yard", "pasture", "gate"), zones, count);
    }
    @Test public void activityPromotesHiddenCameraAndExpires() {
        ViewSelector selector = make(2);
        assertEquals(Arrays.asList("barn", "yard"), selector.snapshot(0).visible);
        assertTrue(selector.activity("gate", 1, 30_001));
        assertEquals(Arrays.asList("gate", "barn"), selector.snapshot(1).visible);
        assertEquals(Arrays.asList("barn", "yard"), selector.snapshot(30_001).visible);
    }
    @Test public void pinWinsAndAutoResumesCurrentActivity() {
        ViewSelector selector = make(4);
        selector.setMode(ViewSelector.PIN, "gate");
        selector.activity("barn", 0, 30_000);
        assertEquals(Collections.singletonList("gate"), selector.snapshot(1).visible);
        selector.setMode(ViewSelector.AUTO, null);
        assertEquals(Collections.singletonList("barn"), selector.snapshot(1).featured);
    }
    @Test public void stableHorseLocationYieldsImmediatelyToActivity() {
        ViewSelector selector = make(4);
        selector.location("horse-1", "pasture", 0, 0);
        assertTrue(selector.snapshot(4_999).featured.isEmpty());
        assertEquals(Collections.singletonList("pasture"), selector.snapshot(5_000).featured);
        selector.activity("barn", 5_001, 35_001);
        assertEquals(Collections.singletonList("barn"), selector.snapshot(5_001).featured);
        assertEquals(Collections.singletonList("pasture"), selector.snapshot(35_001).featured);
    }
    @Test public void repeatedBeaconsDoNotRestartStability() {
        ViewSelector selector = make(4);
        selector.location("horse-1", "pasture", 0, 0);
        selector.location("horse-1", "pasture", 4_000, 4_000);
        assertEquals(Collections.singletonList("pasture"), selector.snapshot(5_000).featured);
        assertTrue(selector.snapshot(64_000).featured.isEmpty());
    }
    @Test public void unknownLocationAndOldObservationsNeverInventAZone() {
        ViewSelector selector = make(4);
        selector.location("horse-1", "pasture", 0, 0);
        selector.snapshot(5_000);
        selector.location("horse-1", "unknown", 5_001, 5_001);
        assertTrue(selector.snapshot(5_001).featured.isEmpty());
        assertFalse(selector.location("horse-1", "pasture", 100_000, 0));
        assertFalse(selector.location("horse-1", "pasture", 100_000, Long.MIN_VALUE));
    }
    @Test public void capacityChangesImmediatelyWithoutLosingEvents() {
        ViewSelector selector = make(4);
        selector.activity("barn", 0, 30_000); selector.activity("yard", 1, 30_001);
        assertEquals(2, selector.snapshot(1).featured.size());
        selector.setCapacity(1);
        assertEquals(Collections.singletonList("yard"), selector.snapshot(2).visible);
        selector.setCapacity(4);
        assertEquals(2, selector.snapshot(2).featured.size());
    }
    @Test public void equalPriorityChangesWaitForDwell() {
        ViewSelector selector = make(4);
        selector.activity("barn", 0, 30_000); selector.snapshot(0);
        selector.activity("yard", 1_000, 31_000);
        assertEquals(Collections.singletonList("barn"), selector.snapshot(1_000).featured);
        assertEquals(Arrays.asList("yard", "barn"), selector.snapshot(8_000).featured);
    }
    @Test public void gridIgnoresTriggersAndInvalidIdsAreRejected() {
        ViewSelector selector = make(2);
        assertFalse(selector.activity("missing", 0, 30_000));
        assertFalse(selector.setMode(ViewSelector.PIN, "missing"));
        selector.setMode(ViewSelector.GRID, null); selector.activity("gate", 0, 30_000);
        assertEquals(Arrays.asList("barn", "yard"), selector.snapshot(1).visible);
    }
    @Test public void twoHorsesCanSelectSeparateViewsButRespectOneTile() {
        ViewSelector selector = make(4);
        selector.location("horse-1", "barn", 0, 0); selector.location("horse-2", "pasture", 0, 0);
        assertEquals(Arrays.asList("barn", "pasture"), selector.snapshot(5_000).featured);
        selector.setCapacity(1);
        assertEquals(Collections.singletonList("barn"), selector.snapshot(5_000).visible);
    }
    @Test public void emptyCatalogIsValidAndOversizedCountHasNoExtraTiles() {
        assertTrue(new ViewSelector(Collections.emptyList(), Collections.emptyMap(), 4).snapshot(0).visible.isEmpty());
        assertEquals(4, make(9).snapshot(0).visible.size());
    }

    @Test public void deviceLimitSurvivesStartupAndLaterCapacityRequests() {
        List<String> cameras = Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h", "i");
        ViewSelector selector = new ViewSelector(cameras, Collections.emptyMap(), 9, 4);
        assertEquals(Arrays.asList("a", "b", "c", "d"), selector.snapshot(0).visible);
        selector.setCapacity(9);
        selector.activity("i", 1, 30_001);
        assertEquals(4, selector.snapshot(1).visible.size());
        assertTrue(selector.snapshot(1).visible.contains("i"));
        selector.setMode(ViewSelector.PIN, "i");
        assertEquals(Collections.singletonList("i"), selector.snapshot(2).visible);
    }

    @Test public void fullscreenStepsThroughEntireCatalogAndWrapsBothWays() {
        ViewSelector selector = make(1);
        selector.setMode(ViewSelector.PIN, "barn");
        for (String expected : Arrays.asList("yard", "pasture", "gate", "barn")) {
            assertTrue(selector.stepPinnedCamera(1));
            assertEquals(Collections.singletonList(expected), selector.snapshot(0).visible);
            assertEquals(ViewSelector.PIN, selector.snapshot(0).mode);
        }
        assertTrue(selector.stepPinnedCamera(-1));
        assertEquals(Collections.singletonList("gate"), selector.snapshot(0).visible);
    }

    @Test public void fullscreenStepsFollowEnabledSavedOrder() {
        AppConfig config = new AppConfig();
        for (String id : Arrays.asList("barn", "yard", "gate")) {
            AppConfig.Camera camera = new AppConfig.Camera(); camera.id = id; config.cameras.add(camera);
        }
        config.cameras.get(1).enabled = false;
        config.moveCamera(2, 0);
        java.util.ArrayList<String> ids = new java.util.ArrayList<>();
        for (AppConfig.Camera camera : config.enabledCameras()) ids.add(camera.id);
        ViewSelector selector = new ViewSelector(ids, Collections.emptyMap(), 1);
        selector.setMode(ViewSelector.PIN, "gate");
        assertTrue(selector.stepPinnedCamera(1));
        assertEquals(Collections.singletonList("barn"), selector.snapshot(0).visible);
        assertTrue(selector.stepPinnedCamera(1));
        assertEquals(Collections.singletonList("gate"), selector.snapshot(0).visible);
    }

    @Test public void fullscreenStepsAreNoOpOutsidePinOrWithoutAnotherCamera() {
        ViewSelector selector = make(2);
        assertFalse(selector.stepPinnedCamera(1));
        selector.setMode(ViewSelector.GRID, null);
        assertFalse(selector.stepPinnedCamera(-1));
        selector.setMode(ViewSelector.PIN, "barn");
        assertFalse(selector.stepPinnedCamera(0));
        ViewSelector single = new ViewSelector(Collections.singletonList("one"), Collections.emptyMap(), 1);
        single.setMode(ViewSelector.PIN, "one");
        assertFalse(single.stepPinnedCamera(1));
        assertEquals(Collections.singletonList("one"), single.snapshot(0).visible);
        assertFalse(new ViewSelector(Collections.emptyList(), Collections.emptyMap(), 1).stepPinnedCamera(1));
    }

    @Test public void browsingStaysPinnedUntilReturningToAuto() {
        ViewSelector selector = make(2);
        selector.setMode(ViewSelector.PIN, "barn");
        selector.activity("gate", 0, 30_000);
        selector.stepPinnedCamera(1);
        assertEquals(Collections.singletonList("yard"), selector.snapshot(1).visible);
        selector.setMode(ViewSelector.AUTO, null);
        assertEquals(Collections.singletonList("gate"), selector.snapshot(2).featured);
    }
}
