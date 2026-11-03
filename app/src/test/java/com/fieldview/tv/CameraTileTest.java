package com.fieldview.tv;

import android.app.Activity;
import androidx.media3.exoplayer.ExoPlayer;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CameraTileTest {
    @Test public void reentrantReleaseDoesNotReleaseTheSamePlayerTwice() throws Exception {
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup();
        try {
            CameraTile tile = new CameraTile(controller.get(), "Camera");
            int[] releases = {0};
            ExoPlayer player = (ExoPlayer) Proxy.newProxyInstance(ExoPlayer.class.getClassLoader(), new Class<?>[]{ExoPlayer.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("release")) {
                            releases[0]++;
                            if (releases[0] == 1) tile.release();
                        }
                        return null;
                    });
            Field field = CameraTile.class.getDeclaredField("player"); field.setAccessible(true); field.set(tile, player);
            tile.release();
            assertEquals(1, releases[0]);
            assertNull(field.get(tile));
            tile.release();
            assertEquals(1, releases[0]);
        } finally { controller.pause().stop().destroy(); }
    }
}
