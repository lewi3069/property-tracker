package com.fieldview.tv;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class CameraCatalogTest {
    @Test public void floatingCompatibilityPreferenceDefaultsOffAndSurvivesSavingAndCopying() throws Exception {
        AppConfig config = new AppConfig(); config.cameras.add(camera("barn"));
        assertFalse(config.softwareFloatingH264);
        org.json.JSONObject old = config.toJson(); old.remove("softwareFloatingH264");
        assertFalse(AppConfig.fromJson(old).softwareFloatingH264);
        config.softwareFloatingH264 = true;
        AppConfig copied = AppConfig.fromJson(config.toJson());
        assertTrue(copied.softwareFloatingH264);
        assertEquals(config.cameras.get(0).password, copied.cameras.get(0).password);
        assertEquals(config.visibleCount, copied.visibleCount);
    }

    private AppConfig.Camera camera(String id) {
        AppConfig.Camera camera = new AppConfig.Camera();
        camera.id = id; camera.name = "Barn"; camera.zone = "pasture";
        camera.host = "192.0.2.20"; camera.port = 8554; camera.username = "viewer";
        camera.password = "example-only-secret"; camera.subPath = "/custom/sub?channel=2"; camera.mainPath = "/custom/main?channel=2";
        return camera;
    }
    @Test public void copyPrefillsAllConnectionFieldsWithoutSavingOrMutatingSource() {
        AppConfig config = new AppConfig(); AppConfig.Camera original = camera("barn"); config.cameras.add(original);
        AppConfig.Camera draft = config.copyCameraDraft(0);
        assertNotSame(original, draft); assertEquals(1, config.cameras.size());
        assertEquals("barn-copy", draft.id); assertEquals("Barn (copy)", draft.name);
        assertEquals(original.host, draft.host); assertEquals(original.port, draft.port);
        assertEquals(original.username, draft.username); assertEquals(original.password, draft.password);
        assertEquals(original.zone, draft.zone); assertEquals(original.subPath, draft.subPath); assertEquals(original.mainPath, draft.mainPath);
        draft.host = "192.0.2.21";
        assertEquals("192.0.2.20", original.host);
    }
    @Test public void copyUsesUniqueNamesAndIdsAndKeepsLongIdsValid() {
        AppConfig config = new AppConfig(); config.cameras.add(camera("barn")); config.cameras.add(config.copyCameraDraft(0));
        AppConfig.Camera second = config.copyCameraDraft(0);
        assertEquals("barn-copy-2", second.id); assertEquals("Barn (copy 2)", second.name);
        config.cameras.get(0).id = "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefgh";
        AppConfig.Camera longCopy = config.copyCameraDraft(0);
        assertTrue(longCopy.id.length() <= 60); assertTrue(longCopy.id.matches("[a-z0-9_-]{1,60}"));
    }
    @Test public void movingChangesDefaultOrderAndSurvivesSavingWithoutChangingCameras() throws Exception {
        AppConfig config = new AppConfig(); config.cameras.add(camera("barn")); config.cameras.add(camera("yard")); config.cameras.add(camera("gate"));
        assertTrue(config.moveCamera(2, 0));
        AppConfig restored = AppConfig.fromJson(config.toJson());
        assertEquals("gate", restored.cameras.get(0).id); assertEquals("barn", restored.cameras.get(1).id); assertEquals("yard", restored.cameras.get(2).id);
        List<String> ids = new ArrayList<>(); for (AppConfig.Camera camera : restored.cameras) ids.add(camera.id);
        assertEquals(Arrays.asList("gate", "barn"), new ViewSelector(ids, Collections.emptyMap(), 2).snapshot(0).visible);
        assertEquals("example-only-secret", restored.cameras.get(0).password);
        assertFalse(restored.moveCamera(0, -1)); assertFalse(restored.moveCamera(2, 3)); assertFalse(restored.moveCamera(1, 1));
        assertEquals("gate", restored.cameras.get(0).id);
    }

    @Test public void olderSavedCamerasRemainEnabledAfterUpgrade() throws Exception {
        AppConfig config = new AppConfig(); config.cameras.add(camera("barn"));
        org.json.JSONObject old = config.toJson();
        old.getJSONArray("cameras").getJSONObject(0).remove("enabled");
        AppConfig restored = AppConfig.fromJson(old);
        assertTrue(restored.cameras.get(0).enabled);
        assertEquals(1, restored.enabledCameras().size());
    }

    @Test public void disabledCamerasRetainSettingsAndOrderAcrossSaving() throws Exception {
        AppConfig config = new AppConfig();
        config.cameras.add(camera("barn")); config.cameras.add(camera("yard")); config.cameras.add(camera("gate"));
        config.cameras.get(0).enabled = false;
        AppConfig restored = AppConfig.fromJson(config.toJson());
        assertEquals(3, restored.cameras.size());
        assertFalse(restored.cameras.get(0).enabled);
        assertEquals("barn", restored.cameras.get(0).id);
        assertEquals("example-only-secret", restored.cameras.get(0).password);
        assertEquals("/custom/main?channel=2", restored.cameras.get(0).mainPath);
        assertEquals(Arrays.asList(restored.cameras.get(1), restored.cameras.get(2)), restored.enabledCameras());
        restored.cameras.get(0).enabled = true;
        assertEquals(restored.cameras, restored.enabledCameras());
    }

    @Test public void copyingDisabledCameraKeepsItDisabledWithoutChangingSource() {
        AppConfig config = new AppConfig(); config.cameras.add(camera("barn"));
        config.cameras.get(0).enabled = false;
        AppConfig.Camera draft = config.copyCameraDraft(0);
        assertFalse(draft.enabled);
        draft.enabled = true;
        assertFalse(config.cameras.get(0).enabled);
        assertTrue(config.enabledCameras().isEmpty());
    }
}
