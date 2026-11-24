package com.fieldview.tv;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class ViewSelectorTest {
    @Test public void thirteenCameraSpotlightSwitchesToOneDetectionAndReturnsToItsDefault() {
        java.util.List<String> ids = new java.util.ArrayList<>(); for (int i=0;i<14;i++) ids.add("camera-"+i);
        ViewSelector s = new ViewSelector(ids, Collections.emptyMap(),13,13); s.setDefaultCamera("camera-1");
        assertEquals(13,s.snapshot(0).visible.size()); assertEquals("camera-1",s.snapshot(0).visible.get(0));
        s.activity("camera-12",100,30100); s.activity("camera-10",200,30200);
        assertEquals(Collections.singletonList("camera-10"),s.snapshot(200).featured);
        assertEquals("camera-10",s.snapshot(200).visible.get(0)); assertEquals(13,new java.util.HashSet<>(s.snapshot(200).visible).size());
        assertEquals("camera-1",s.snapshot(30200).visible.get(0));
        s.setMode(ViewSelector.GRID,null); s.activity("camera-12",40000,70000);
        assertEquals("camera-1",s.snapshot(40000).visible.get(0));
        s.setDefaultCamera("missing"); assertEquals("camera-0",s.snapshot(40001).visible.get(0));
    }
    @Test public void heartbeatExtendsExpiryWithoutOvertakingANewerDetection() {
        ViewSelector s = new ViewSelector(java.util.Arrays.asList("one", "two"), java.util.Collections.emptyMap(), 1);
        s.activity("one", 0, 30_000); s.activity("two", 10_000, 40_000);
        assertEquals("two", s.snapshot(10_000).featured.get(0));
        s.activity("one", 20_000, 50_000);
        assertEquals("two", s.snapshot(20_000).featured.get(0));
        assertEquals("one", s.snapshot(40_000).featured.get(0));
    }
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

    // Phone portrait Auto: a fixed 2 or 3 feeds, driven through the validated MQTT router.
    private static String activity(String camera, String event, long expires, boolean active) {
        return "{\"type\":\"" + (active ? "activity" : "activity_end") + "\",\"camera_id\":\"" + camera
                + "\",\"event_id\":\"" + event + "\",\"expires_at\":" + expires + "}";
    }
    private ViewSelector portrait(int count, int cap) {
        ViewSelector selector = new ViewSelector(Arrays.asList("barn", "yard", "pasture", "gate", "shed"), Collections.emptyMap(), 4, cap);
        selector.setDefaultCamera("pasture"); selector.setPortraitAuto(count); return selector;
    }
    @Test public void portraitAutoShowsUpToThreeActiveFeedsAndReturnsToIdleWhenTheyClearOrExpire() {
        ViewSelector s = portrait(3, 9);
        CommandRouter router = new CommandRouter(s, (count, until) -> s.setCapacity(count));
        assertEquals(Arrays.asList("pasture", "barn", "yard"), s.snapshot(0).visible);
        assertTrue(router.accept(activity("gate", "a", 31_000, true), false, 1_000));
        assertEquals(Arrays.asList("gate", "pasture", "barn"), s.snapshot(1_000).visible);
        assertTrue(router.accept(activity("shed", "b", 32_000, true), false, 2_000));
        assertEquals(Collections.singletonList("gate"), s.snapshot(2_000).featured); // The existing 8 s dwell still applies.
        assertTrue(router.accept("{\"type\":\"layout\",\"max_visible\":9,\"expires_at\":47000}", false, 2_001));
        assertEquals("Layout overrides don't restart the portrait hold", Collections.singletonList("gate"), s.snapshot(2_001).featured);
        assertEquals(Arrays.asList("shed", "gate", "pasture"), s.snapshot(9_000).visible);
        assertTrue(router.accept(activity("barn", "c", 40_000, true), false, 9_500));
        assertTrue(router.accept(activity("yard", "d", 41_000, true), false, 10_000));
        assertEquals("The three most recent of four active cameras", Arrays.asList("yard", "barn", "shed"), s.snapshot(17_000).visible);
        assertEquals(3, s.snapshot(17_000).featured.size());
        assertTrue(router.accept("{\"type\":\"layout\",\"max_visible\":9,\"expires_at\":47000}", false, 17_000));
        assertEquals("A layout override cannot raise portrait Auto", Arrays.asList("yard", "barn", "shed"), s.snapshot(17_000).visible);
        for (String camera : new String[]{"yard", "barn", "shed", "gate"})
            assertTrue(router.accept(activity(camera, "end-" + camera, 48_000, false), false, 18_000));
        ViewSelector.Selection idle = s.snapshot(18_000);
        assertEquals("overview", idle.reason); assertTrue(idle.featured.isEmpty());
        assertEquals(Arrays.asList("pasture", "barn", "yard"), idle.visible);
        assertTrue(router.accept(activity("gate", "e", 50_000, true), false, 20_000));
        assertEquals(Arrays.asList("gate", "pasture", "barn"), s.snapshot(20_000).visible);
        assertEquals(Arrays.asList("pasture", "barn", "yard"), s.snapshot(50_000).visible);
    }
    @Test public void portraitAutoHonorsTheDeviceCapAndLeavesGridPinAndTheLegacyLayoutUnchanged() {
        ViewSelector two = portrait(2, 9);
        for (String camera : new String[]{"gate", "shed", "barn"}) two.activity(camera, 1_000, 31_000);
        assertEquals(Arrays.asList("barn", "gate"), two.snapshot(1_000).visible);
        two.setCapacity(9); assertEquals(Arrays.asList("barn", "gate"), two.snapshot(1_000).visible);
        assertEquals(Arrays.asList("pasture", "barn"), portrait(3, 2).snapshot(0).visible);
        ViewSelector missingDefault = portrait(3, 9); missingDefault.setDefaultCamera("missing");
        assertEquals(Arrays.asList("barn", "yard", "pasture"), missingDefault.snapshot(0).visible);
        ViewSelector grid = portrait(3, 9); grid.setMode(ViewSelector.GRID, null);
        assertEquals(Arrays.asList("barn", "yard", "pasture", "gate"), grid.snapshot(0).visible);
        grid.setMode(ViewSelector.PIN, "shed"); assertEquals(Collections.singletonList("shed"), grid.snapshot(0).visible);
        // Disabled policy (TV, phone landscape, or an invalid count): the saved count and two featured cameras.
        ViewSelector legacy = portrait(4, 9); assertEquals(0, legacy.portraitAuto());
        assertEquals(Arrays.asList("barn", "yard", "pasture", "gate"), legacy.snapshot(0).visible);
        for (String camera : new String[]{"gate", "shed", "barn"}) legacy.activity(camera, 1_000, 31_000);
        assertEquals(Arrays.asList("barn", "gate"), legacy.snapshot(1_000).featured);
        assertEquals(Arrays.asList("barn", "gate", "yard", "pasture"), legacy.snapshot(1_000).visible);
        two.setPortraitAuto(0); assertEquals(5, two.snapshot(1_000).visible.size()); // Saved/overridden capacity again.
    }
}
