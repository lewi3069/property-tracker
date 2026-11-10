package com.fieldview.tv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class WallStreamPlanTest {
    @Test public void adaptiveMotionUsesOneMainAndSevenSubstreamsThenThirteenSubs() {
        List<AppConfig.Camera> cameras = cameras(); List<String> ids = new ArrayList<>();
        for (AppConfig.Camera camera : cameras) ids.add(camera.id);
        ViewSelector selector = new ViewSelector(ids, Collections.emptyMap(), 13, 13); selector.setMotionLayoutEight(true);
        selector.activity("camera-12", 0, 10000);
        ViewSelector.Selection active = selector.snapshot(0);
        WallStreamPlan plan = new WallStreamPlan(active, true, true, false, Collections.emptySet(), true);
        int main = 0, nativeCount = 0;
        for (String id : active.visible) {
            AppConfig.Camera camera = cameras.get(ids.indexOf(id));
            if (plan.main(camera)) { main++; assertEquals("camera-12", id); }
            if (plan.nativeGrid(camera)) nativeCount++;
        }
        assertEquals(1, main); assertEquals(7, nativeCount);
        assertFalse(new WallStreamPlan(active, true, false, false, Collections.emptySet(), true).main(cameras.get(12)));
        assertFalse(new WallStreamPlan(active, true, true, false, Collections.singleton("camera-12"), true).main(cameras.get(12)));
        selector.clearActivity("camera-12", 1);
        ViewSelector.Selection quiet = selector.snapshot(15000);
        assertEquals(8, quiet.visible.size());
        assertTrue(new WallStreamPlan(quiet, true, true, false, Collections.emptySet(), true).main(cameras.get(12)));
        ViewSelector.Selection overview = selector.snapshot(15001);
        WallStreamPlan restored = new WallStreamPlan(overview, true, true, false, Collections.emptySet(), true);
        assertEquals(13, overview.visible.size());
        for (AppConfig.Camera camera : cameras) { assertFalse(restored.main(camera)); assertTrue(restored.nativeGrid(camera)); }
        selector.setCapacity(8); selector.activity("camera-12", 16000, 46000);
        ViewSelector.Selection ordinaryEight = selector.snapshot(16000);
        assertFalse(ordinaryEight.motionLayoutEight);
        assertFalse(new WallStreamPlan(ordinaryEight, true, true, false, Collections.emptySet(), true).main(cameras.get(12)));
    }
    private List<AppConfig.Camera> cameras() {
        List<AppConfig.Camera> result = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            AppConfig.Camera camera = new AppConfig.Camera(); camera.id = "camera-" + i;
            camera.host = "192.0.2.10"; result.add(camera);
        }
        return result;
    }
    @Test public void defaultMotionAndReturnUseExactlyOneMainAndTwelveNativeSubstreams() {
        List<AppConfig.Camera> cameras = cameras(); List<String> ids = new ArrayList<>();
        for (AppConfig.Camera camera : cameras) ids.add(camera.id);
        ViewSelector selector = new ViewSelector(ids, Collections.emptyMap(), 13, 13);
        selector.setDefaultCamera("camera-4");
        assertCenter(selector, cameras, 1000, "camera-4");
        selector.activity("camera-8", 2000, 12000);
        assertCenter(selector, cameras, 2000, "camera-8");
        assertCenter(selector, cameras, 13000, "camera-4");
        selector.setMode(ViewSelector.GRID, null);
        assertCenter(selector, cameras, 14000, "camera-4");
    }
    private void assertCenter(ViewSelector selector, List<AppConfig.Camera> cameras, long now, String expected) {
        WallStreamPlan plan = new WallStreamPlan(selector.snapshot(now), true, true, true);
        int main = 0, nativeCount = 0;
        for (AppConfig.Camera camera : cameras) {
            if (plan.main(camera)) { main++; assertEquals(expected, camera.id); }
            if (plan.nativeGrid(camera)) nativeCount++;
        }
        assertEquals(1, main); assertEquals(12, nativeCount);
    }
    @Test public void optOutMasterOffAndIdenticalUrisKeepDenseWallOnSubstreams() {
        List<AppConfig.Camera> cameras = cameras(); List<String> ids = new ArrayList<>();
        for (AppConfig.Camera camera : cameras) ids.add(camera.id);
        ViewSelector selector = new ViewSelector(ids, Collections.emptyMap(), 13, 13);
        ViewSelector.Selection selection = selector.snapshot(0);
        for (WallStreamPlan plan : new WallStreamPlan[]{new WallStreamPlan(selection, true, true, false),
                new WallStreamPlan(selection, true, false, true)}) {
            for (AppConfig.Camera camera : cameras) { assertFalse(plan.main(camera)); assertTrue(plan.nativeGrid(camera)); }
        }
        cameras.get(0).mainPath = cameras.get(0).subPath;
        WallStreamPlan same = new WallStreamPlan(selection, true, true, true);
        assertFalse(same.main(cameras.get(0))); assertTrue(same.nativeGrid(cameras.get(0)));
    }
    @Test public void fewerCamerasAndOrdinaryPlaybackKeepExistingStreamPolicy() {
        List<AppConfig.Camera> cameras = cameras(); List<String> ids = new ArrayList<>();
        for (AppConfig.Camera camera : cameras) ids.add(camera.id);
        ViewSelector selector = new ViewSelector(ids, Collections.emptyMap(), 4, 13);
        selector.activity("camera-1", 0, 10000);
        ViewSelector.Selection selection = selector.snapshot(0);
        WallStreamPlan dense = new WallStreamPlan(selection, true, true, true);
        assertFalse(dense.main(cameras.get(1))); assertTrue(dense.nativeGrid(cameras.get(1)));
        WallStreamPlan ordinary = new WallStreamPlan(selection, false, true, true);
        assertTrue(ordinary.main(cameras.get(1))); assertFalse(ordinary.highResolutionCenter(cameras.get(1)));
        assertFalse(ordinary.main(cameras.get(0))); assertFalse(ordinary.nativeGrid(cameras.get(0)));
    }
}
