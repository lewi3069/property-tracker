package com.fieldview.tv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Stable slots for the optional multi-motion TV wall; times share the selector's epoch clock. */
final class MultiMotionLayout {
    private int slots, pending;
    private long pendingSince;
    private final List<String> featured = new ArrayList<>();
    private final List<Long> selectedAt = new ArrayList<>();

    void reset() { slots = pending = 0; featured.clear(); selectedAt.clear(); }

    State update(long now, List<String> active, List<String> initial) {
        int desired = Math.min(4, Math.max(1, active.size()));
        if (slots == 0) {
            slots = pending = desired; pendingSince = now;
            for (String id : initial) if (featured.size() < slots && !featured.contains(id)) add(id, now);
        }
        // The selector owns the all-clear quiet period: preserve this shape until it ends.
        if (active.isEmpty()) { pending = slots; pendingSince = now; return state(); }
        if (now < pendingSince) pendingSince = now;
        for (int i = 0; i < selectedAt.size(); i++) if (now < selectedAt.get(i)) selectedAt.set(i, now);
        if (desired != pending) { pending = desired; pendingSince = now; }
        long delay = pending > slots ? 2_000 : 15_000;
        if (pending != slots && now >= pendingSince && now - pendingSince >= delay) {
            if (pending < slots) {
                // Retain active cameras before filling any remaining slots with recent activity.
                for (int i = featured.size() - 1; i >= 0 && featured.size() > pending; i--)
                    if (!active.contains(featured.get(i))) remove(i);
                while (featured.size() > pending) remove(featured.size() - 1);
            }
            slots = pending;
        }
        List<String> wanted = active.subList(0, Math.min(slots, active.size()));
        List<String> missing = new ArrayList<>(wanted); missing.removeAll(featured);
        for (int i = 0; i < featured.size() && !missing.isEmpty(); i++) {
            if (!wanted.contains(featured.get(i)) && now >= selectedAt.get(i) && now - selectedAt.get(i) >= 10_000) {
                featured.set(i, missing.remove(0)); selectedAt.set(i, now);
            }
        }
        for (String id : missing) if (featured.size() < slots) add(id, now);
        return state();
    }

    private void add(String id, long now) { featured.add(id); selectedAt.add(now); }
    private void remove(int index) { featured.remove(index); selectedAt.remove(index); }
    private State state() { return new State(slots, featured); }

    static final class State {
        final int slots;
        final List<String> featured;
        State(int slots, List<String> featured) {
            this.slots = slots; this.featured = Collections.unmodifiableList(new ArrayList<>(featured));
        }
    }
}
