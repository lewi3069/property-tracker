package com.fieldview.tv;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class HardwareMainRenderersFactoryTest {
    private Format hevc(int width, int height) { return new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H265).setWidth(width).setHeight(height).build(); }
    @Test public void workaroundIsScopedToTheMeasuredTvAndPreservesOrdinaryFourK() {
        assertTrue(HardwareMainRenderersFactory.widthGuard(true, "Amazon", "AFTKM", 30));
        assertFalse(HardwareMainRenderersFactory.widthGuard(false, "Amazon", "AFTKM", 30));
        assertFalse(HardwareMainRenderersFactory.widthGuard(true, "NVIDIA", "SHIELD", 30));
        assertFalse(HardwareMainRenderersFactory.widthGuard(true, "Amazon", "AFTKM", 31));
        assertFalse(HardwareMainRenderersFactory.widthGuard(true, "Amazon", "AFTMM", 30));
        assertTrue(HardwareMainRenderersFactory.tooWide(hevc(4608, 1728), true));
        assertTrue(HardwareMainRenderersFactory.tooWide(hevc(4096, 1536), true));
        assertFalse(HardwareMainRenderersFactory.tooWide(hevc(3840, 2160), true));
        assertFalse(HardwareMainRenderersFactory.tooWide(hevc(Format.NO_VALUE, Format.NO_VALUE), true));
        assertFalse(HardwareMainRenderersFactory.tooWide(hevc(4608, 1728), false));
        assertFalse(HardwareMainRenderersFactory.tooWide(new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).setWidth(4608).build(), true));
    }
    @Test public void wideHevcIsRejectedBeforeCodecLookupEvenWhenLearnedAtRendererTime() throws Exception {
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup();
        int[] queries = {0};
        MediaCodecSelector selector = (mime, secure, tunneling) -> { queries[0]++; return Collections.emptyList(); };
        MediaCodecAdapter.Factory adapter = config -> { throw new AssertionError("Decoder must not be created by this query"); };
        HardwareMainRenderersFactory.SizeGuardedRenderer renderer = new HardwareMainRenderersFactory.SizeGuardedRenderer(
                controller.get(), adapter, selector, 5000, true, new Handler(Looper.getMainLooper()), new VideoRendererEventListener() {}, true);
        try {
            assertTrue(renderer.getDecoderInfos(selector, hevc(4608, 1728), false).isEmpty()); assertEquals(0, queries[0]);
            renderer.getDecoderInfos(selector, hevc(3840, 2160), false); assertTrue(queries[0] > 0);
        } finally { renderer.reset(); controller.pause().stop().destroy(); }
    }
}
