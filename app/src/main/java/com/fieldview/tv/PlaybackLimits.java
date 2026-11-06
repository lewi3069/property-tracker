package com.fieldview.tv;

import java.util.ArrayList;
import java.util.List;

/** Local playback budgets; the catalog can contain more cameras than the live wall. */
public final class PlaybackLimits {
    public static final int GRID_BUFFER_BYTES = 2 * 1024 * 1024;
    public static final int ENLARGED_BUFFER_BYTES = 8 * 1024 * 1024;
    private static final int[] COUNTS = {1, 2, 4, 6, 9};
    public final int maxVisible;

    private PlaybackLimits(int maxVisible) { this.maxVisible = maxVisible; }

    public static PlaybackLimits forDevice(String model, boolean lowRam) {
        // The Android 5.1 AFTT has a smaller decoder/memory budget.
        if ("AFTT".equalsIgnoreCase(model)) return new PlaybackLimits(2);
        // An AFTMM exhausted memory with nine feeds and its codec/display
        // services crashed. Keep this model at the four-feed layout already exercised.
        return new PlaybackLimits(lowRam || "AFTMM".equalsIgnoreCase(model) ? 4 : 9);
    }

    public int clamp(int requested) { return Math.max(1, Math.min(maxVisible, requested)); }

    public int[] layoutCounts() {
        List<Integer> choices = new ArrayList<>();
        for (int count : COUNTS) if (count <= maxVisible) choices.add(count);
        int[] result = new int[choices.size()];
        for (int i = 0; i < result.length; i++) result[i] = choices.get(i);
        return result;
    }
}
