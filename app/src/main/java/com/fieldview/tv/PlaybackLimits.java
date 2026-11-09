package com.fieldview.tv;

import java.util.ArrayList;
import java.util.List;

/** Local playback budgets; the catalog can contain more cameras than the live wall. */
public final class PlaybackLimits {
    public static final int GRID_BUFFER_BYTES = 2 * 1024 * 1024;
    public static final int ENLARGED_BUFFER_BYTES = 8 * 1024 * 1024;
    private static final int[] COUNTS = {1, 2, 4, 6, 9, 13};
    public final int maxVisible;

    private PlaybackLimits(int maxVisible) { this.maxVisible = maxVisible; }

    public static PlaybackLimits forDevice(String model, boolean lowRam) {
        return forDevice(model, lowRam, false);
    }
    public static boolean supportsDenseTrial(String model, boolean lowRam) {
        return !lowRam && !"AFTT".equalsIgnoreCase(model) && !"AFTMM".equalsIgnoreCase(model);
    }
    public static PlaybackLimits forDevice(String model, boolean lowRam, boolean denseTrial) {
        if (denseTrial && supportsDenseTrial(model, lowRam)) return new PlaybackLimits(13);
        // The Android 5.1 AFTT has a smaller decoder/memory budget.
        if ("AFTT".equalsIgnoreCase(model)) return new PlaybackLimits(2);
        // An AFTMM exhausted memory with nine feeds and its codec/display
        // services crashed. Keep this model at the four-feed layout already exercised.
        // AFTKM also reported a vendor hardware-composer crash with a saved
        // nine-feed layout. Reduce simultaneous surfaces on this model.
        return new PlaybackLimits(lowRam || "AFTMM".equalsIgnoreCase(model) || "AFTKM".equalsIgnoreCase(model) ? 4 : 9);
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
