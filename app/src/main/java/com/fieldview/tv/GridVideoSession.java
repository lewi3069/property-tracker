package com.fieldview.tv;

import android.view.View;

/** Optional dense-grid backend; no native-library types enter the shared/phone code. */
interface GridVideoSession {
    interface Listener { void frames(); void error(); }
    final class Stats {
        final long decoded, displayed, lost, bytes;
        final int width, height;
        Stats(long decoded, long displayed, long lost, long bytes, int width, int height) {
            this.decoded=decoded;this.displayed=displayed;this.lost=lost;this.bytes=bytes;this.width=width;this.height=height;
        }
    }
    View view();
    void start(String uri, Listener listener);
    Stats stats();
    /** Detach on the UI thread; complete on that thread after native resources are released. */
    void close(Runnable complete);
}
