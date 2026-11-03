package com.fieldview.tv;

import android.app.Activity;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.TextView;
import androidx.media3.common.Player;
import androidx.media3.common.MimeTypes;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CameraTileTest {
    private Field field(String name) throws Exception {
        Field field = CameraTile.class.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private ExoPlayer fakePlayer(int[] releases) {
        return (ExoPlayer) Proxy.newProxyInstance(ExoPlayer.class.getClassLoader(), new Class<?>[]{ExoPlayer.class}, (proxy, method, args) -> {
            if (method.getName().equals("release")) releases[0]++;
            if (method.getName().equals("getPlaybackState")) return Player.STATE_READY;
            if (method.getReturnType() == int.class) return 0;
            if (method.getReturnType() == long.class) return 0L;
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == float.class) return 0f;
            return null;
        });
    }
    private Player.Listener listener(CameraTile tile, ExoPlayer player, VideoProgress progress) throws Exception {
        Constructor<?> constructor = Class.forName("com.fieldview.tv.CameraTile$1").getDeclaredConstructors()[0];
        constructor.setAccessible(true); Class<?>[] types = constructor.getParameterTypes(); Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            if (types[i] == CameraTile.class) args[i] = tile;
            else if (types[i] == ExoPlayer.class) args[i] = player;
            else if (types[i] == VideoProgress.class) args[i] = progress;
            else throw new AssertionError("Unexpected listener dependency");
        }
        return (Player.Listener) constructor.newInstance(args);
    }
    private void arm(CameraTile tile) throws Exception {
        Method method = CameraTile.class.getDeclaredMethod("armStallTimeout"); method.setAccessible(true); method.invoke(tile);
    }

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

    @Test public void readyPlayerThatStopsProducingFramesIsReleasedAndRetried() throws Exception {
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup();
        CameraTile tile = new CameraTile(controller.get(), "Offline test");
        try {
            int[] releases = {0}; ExoPlayer player = fakePlayer(releases);
            VideoProgress progress = new VideoProgress(SystemClock.elapsedRealtime());
            field("player").set(tile, player); field("progress").set(tile, progress); field("attempts").setInt(tile, 4);
            Player.Listener events = listener(tile, player, progress); arm(tile);
            progress.frame(SystemClock.elapsedRealtime()); events.onRenderedFirstFrame(); events.onPlaybackStateChanged(Player.STATE_READY);
            assertEquals(4, field("attempts").getInt(tile));
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20));
            assertNull(field("player").get(tile)); assertNotNull(field("retry").get(tile)); assertEquals(1, releases[0]);
            assertTrue(((TextView) field("status").get(tile)).getText().toString().startsWith("Video stopped updating"));
        } finally { tile.release(); controller.pause().stop().destroy(); }
    }

    @Test public void continuingFramesKeepPlayerAliveAndResetRetryHistoryAfterStability() throws Exception {
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup();
        CameraTile tile = new CameraTile(controller.get(), "Offline test");
        try {
            int[] releases = {0}; ExoPlayer player = fakePlayer(releases);
            VideoProgress progress = new VideoProgress(SystemClock.elapsedRealtime());
            field("player").set(tile, player); field("progress").set(tile, progress); field("attempts").setInt(tile, 4);
            Player.Listener events = listener(tile, player, progress); arm(tile); events.onRenderedFirstFrame(); events.onPlaybackStateChanged(Player.STATE_READY);
            field("stalledRecoveries").setInt(tile, 2);
            assertEquals(4, field("attempts").getInt(tile));
            for (int i = 0; i < 8; i++) { progress.frame(SystemClock.elapsedRealtime()); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4)); }
            assertSame(player, field("player").get(tile)); assertEquals(0, releases[0]);
            assertNull(field("retry").get(tile)); assertEquals(0, field("attempts").getInt(tile));
            assertEquals(0, field("stalledRecoveries").getInt(tile));
        } finally { tile.release(); controller.pause().stop().destroy(); }
    }

    @Test public void compatibilityDecoderSelectionChangesOnlyAvcWhenEnabled() throws Exception {
        int[] calls = {0, 0};
        MediaCodecSelector regular = (mime, secure, tunneling) -> { calls[0]++; return Collections.emptyList(); };
        MediaCodecSelector software = (mime, secure, tunneling) -> { calls[1]++; assertTrue(secure); assertFalse(tunneling); return Collections.emptyList(); };
        MediaCodecSelector compatible = CameraTile.decoderSelector(true, regular, software);
        compatible.getDecoderInfos(MimeTypes.VIDEO_H264, true, false);
        compatible.getDecoderInfos(MimeTypes.VIDEO_H265, false, false);
        CameraTile.decoderSelector(false, regular, software).getDecoderInfos(MimeTypes.VIDEO_H264, false, false);
        assertArrayEquals(new int[]{2, 1}, calls);
    }

    @Test public void softwarePreferencePreservesOrderWithinGroupsAndDoesNotMutateCachedCodecList() throws Exception {
        MediaCodecInfo hardware = MediaCodecInfo.newInstance("OMX.vendor.avc", MimeTypes.VIDEO_H264, MimeTypes.VIDEO_H264, null, true, false, true, false, false);
        MediaCodecInfo software = MediaCodecInfo.newInstance("OMX.google.h264.decoder", MimeTypes.VIDEO_H264, MimeTypes.VIDEO_H264, null, false, true, false, false, false);
        List<MediaCodecInfo> original = Collections.unmodifiableList(Arrays.asList(hardware, software));
        List<MediaCodecInfo> ordered = CameraTile.softwareFirst((mime, secure, tunneling) -> original).getDecoderInfos(MimeTypes.VIDEO_H264, false, false);
        assertEquals(Arrays.asList(software, hardware), ordered);
        assertEquals(Arrays.asList(hardware, software), original);
    }

    @Test public void threeUnrecoveredStallsStopInsteadOfAccumulatingPlayersForever() throws Exception {
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup();
        CameraTile tile = new CameraTile(controller.get(), "Offline test");
        try {
            int[] releases = {0};
            for (int i = 0; i < 3; i++) {
                ExoPlayer player = fakePlayer(releases); VideoProgress progress = new VideoProgress(SystemClock.elapsedRealtime());
                field("player").set(tile, player); field("progress").set(tile, progress);
                Player.Listener events = listener(tile, player, progress); arm(tile);
                progress.frame(SystemClock.elapsedRealtime()); events.onRenderedFirstFrame(); events.onPlaybackStateChanged(Player.STATE_READY);
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20));
                assertNull(field("player").get(tile)); assertEquals(i + 1, releases[0]);
                if (i < 2) {
                    assertNotNull(field("retry").get(tile));
                    Method dispose = CameraTile.class.getDeclaredMethod("disposePlayer"); dispose.setAccessible(true); dispose.invoke(tile);
                }
            }
            assertNull(field("retry").get(tile)); assertNull(field("stalled").get(tile));
            assertTrue(((TextView) field("status").get(tile)).getText().toString().contains("reopen camera"));
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(2)); assertEquals(3, releases[0]);
        } finally { tile.release(); controller.pause().stop().destroy(); }
    }
}
