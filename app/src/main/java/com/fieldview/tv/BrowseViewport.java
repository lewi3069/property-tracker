package com.fieldview.tv;

import android.graphics.Rect;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Phone Browse admission: a row owns a stream only while it overlaps the viewport, within the playback budget. */
final class BrowseViewport {
    private BrowseViewport() { }

    /** Height of a content-coordinate row inside the viewport that starts at {@code top}. */
    static int visible(Rect row, int top, int height) {
        return Math.max(0, Math.min(row.bottom, top + height) - Math.max(row.top, top));
    }

    /**
     * Rows that may own a stream. An overlapping row that already streams keeps it, so scrolling never
     * reconnects a displayed feed. Remaining budget goes to the most visible rows, then to saved order.
     */
    static boolean[] select(List<Rect> rows, int top, int height, boolean[] live, int budget) {
        boolean[] keep = new boolean[rows.size()];
        int[] visible = new int[rows.size()];
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            visible[i] = visible(rows.get(i), top, height);
            if (visible[i] > 0) candidates.add(i);
        }
        Collections.sort(candidates, (a, b) -> live[a] != live[b] ? (live[a] ? -1 : 1)
                : visible[a] != visible[b] ? Integer.compare(visible[b], visible[a]) : Integer.compare(a, b));
        for (int i = 0; i < candidates.size() && i < budget; i++) keep[candidates.get(i)] = true;
        return keep;
    }
}
