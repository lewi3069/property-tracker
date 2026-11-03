package com.fieldview.tv;

import android.content.Intent;
import android.os.Looper;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SetupOnlyTest {
    @Test public void setupEntryKeepsSavedCameraPlayersStoppedUntilUserChoosesPlayback() throws Exception {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), MainActivity.class).putExtra(MainActivity.EXTRA_SETUP_ONLY, true);
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class, intent).create();
        try {
            MainActivity activity = controller.get(); AppConfig config = new AppConfig();
            for (String id : new String[]{"one", "two"}) {
                AppConfig.Camera camera = new AppConfig.Camera(); camera.id = id; camera.name = id; camera.host = "192.0.2.10"; config.cameras.add(camera);
            }
            Field configField = MainActivity.class.getDeclaredField("config"); configField.setAccessible(true); configField.set(activity, config);
            Method reset = MainActivity.class.getDeclaredMethod("resetSelector"); reset.setAccessible(true); reset.invoke(activity);
            controller.start().resume().visible(); shadowOf(Looper.getMainLooper()).idle();
            Field tiles = MainActivity.class.getDeclaredField("tiles"); tiles.setAccessible(true);
            assertTrue(((Map<?, ?>) tiles.get(activity)).isEmpty());
            Field empty = MainActivity.class.getDeclaredField("empty"); empty.setAccessible(true);
            assertTrue(((TextView) empty.get(activity)).getText().toString().contains("Playback paused for setup"));
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2));
            assertTrue(((Map<?, ?>) tiles.get(activity)).isEmpty());
        } finally { controller.pause().stop().destroy(); }
    }
}
