package com.fieldview.tv;

import android.view.View;

/**
 * Native video session behind GridVideoFactory: TV dense-grid tiles, or the phone's pinned main stream.
 * No native-library types enter the shared code.
 */
interface GridVideoSession {
    interface Listener { void frames(); void error(); }
    final class Stats {
        final long decoded, displayed, lost, bytes, demuxBytes, mediaTimeMs;
        final int width, height;
        final float pixelWidthHeightRatio;
        Stats(long decoded, long displayed, long lost, long bytes, int width, int height) {
            this(decoded, displayed, lost, bytes, width, height, 0, -1);
        }
        Stats(long decoded, long displayed, long lost, long bytes, int width, int height,
              long demuxBytes, long mediaTimeMs) {
            this(decoded, displayed, lost, bytes, width, height, demuxBytes, mediaTimeMs, 1f);
        }
        Stats(long decoded, long displayed, long lost, long bytes, int width, int height,
              long demuxBytes, long mediaTimeMs, float pixelWidthHeightRatio) {
            this.pixelWidthHeightRatio = pixelWidthHeightRatio;
            this.decoded=decoded;this.displayed=displayed;this.lost=lost;this.bytes=bytes;this.width=width;this.height=height;
            this.demuxBytes=demuxBytes;this.mediaTimeMs=mediaTimeMs;
        }
    }
    View view();
    void start(String uri, Listener listener);
    Stats stats();
    /** Only a texture output can remain visibly above the new main SurfaceView. */
    default boolean supportsLivePreview() { return false; }
    /** Detach on the UI thread; complete on that thread after native resources are released. */
    void close(Runnable complete);
}
