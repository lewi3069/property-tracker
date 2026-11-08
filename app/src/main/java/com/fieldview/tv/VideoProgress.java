package com.fieldview.tv;

/** Per-player frame health using monotonic time; no images or per-frame UI work. */
final class VideoProgress {
    static final long TIMEOUT_MS = 20_000;
    private static final long STABLE_MS = 10_000, MAX_STABLE_GAP_MS = 5_000;
    private final long startedAt;
    private volatile long firstRenderedAt = -1, lastFrameAt = -1, stableSince = -1;
    private volatile long submittedFrames;

    VideoProgress(long startedAt) { this.startedAt = startedAt; }

    /** Called on the video renderer thread. Each attempt owns a separate instance. */
    void frame(long now) {
        long previous = lastFrameAt;
        if (now < startedAt || now < previous) return;
        if (previous < 0 || now - previous > MAX_STABLE_GAP_MS) stableSince = now;
        lastFrameAt = now;
        submittedFrames++;
    }

    /** The actual first-frame callback is required before calling a stream healthy. */
    void rendered(long now) { if (firstRenderedAt < 0) firstRenderedAt = now; }

    long silentFor(long now) {
        long rendered = firstRenderedAt;
        long reference = rendered < 0 ? startedAt : Math.max(rendered, lastFrameAt);
        return Math.max(0, now - reference);
    }

    boolean stalled(long now) { return silentFor(now) >= TIMEOUT_MS; }
    long submittedFrames() { return submittedFrames; }
    boolean sustained() {
        long since = stableSince;
        return firstRenderedAt >= 0 && since >= 0 && lastFrameAt - since >= STABLE_MS;
    }
}
