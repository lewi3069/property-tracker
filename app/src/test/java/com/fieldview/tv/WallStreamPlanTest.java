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
