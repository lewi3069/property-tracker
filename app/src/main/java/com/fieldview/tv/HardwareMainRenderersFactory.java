package com.fieldview.tv;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Hardware-only main streams: apply the measured AFTKM width limit when format dimensions become known. */
@UnstableApi
final class HardwareMainRenderersFactory extends DefaultRenderersFactory {
    HardwareMainRenderersFactory(Context context) { super(context); }
    static boolean widthGuard(boolean television, String manufacturer, String model, int sdk) {
        return television && "Amazon".equalsIgnoreCase(manufacturer) && "AFTKM".equalsIgnoreCase(model) && sdk == 30;
    }
    static boolean tooWide(Format format, boolean guardedDevice) {
        return guardedDevice && MimeTypes.VIDEO_H265.equals(format.sampleMimeType) && format.width > 3840;
    }
    static boolean tooWide(Format format) {
        return tooWide(format, widthGuard(BuildConfig.TV_INTERFACE, Build.MANUFACTURER, Build.MODEL, Build.VERSION.SDK_INT));
    }
    @Override protected void buildVideoRenderers(Context context, int extensionMode, MediaCodecSelector selector,
            boolean fallback, Handler handler, VideoRendererEventListener listener, long joiningMs, ArrayList<Renderer> out) {
        out.add(new SizeGuardedRenderer(context, getCodecAdapterFactory(), selector, joiningMs, fallback, handler, listener,
                widthGuard(BuildConfig.TV_INTERFACE, Build.MANUFACTURER, Build.MODEL, Build.VERSION.SDK_INT)));
    }
    static final class SizeGuardedRenderer extends MediaCodecVideoRenderer {
        private final boolean widthGuard;
        SizeGuardedRenderer(Context context, MediaCodecAdapter.Factory factory, MediaCodecSelector selector, long joiningMs,
                boolean fallback, Handler handler, VideoRendererEventListener listener, boolean widthGuard) {
            super(context, factory, selector, joiningMs, fallback, handler, listener, 50);
            this.widthGuard = widthGuard;
        }
        @Override protected List<MediaCodecInfo> getDecoderInfos(MediaCodecSelector selector, Format format, boolean secure)
                throws MediaCodecUtil.DecoderQueryException {
            // Refuse before codec lookup/creation, including formats learned after initial track mapping.
            if (tooWide(format, widthGuard)) return Collections.emptyList();
            return super.getDecoderInfos(selector, format, secure);
        }
    }
}
