package com.fieldview.tv;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A floating session's chosen default plus temporary camera-motion overrides. */
public final class FloatingViewController {
    private final Set<String> cameras;
    private final Map<String, Motion> motions = new LinkedHashMap<>();
    private String defaultCamera, current;
    private long sequence;

    public FloatingViewController(List<String> enabledCameras, String selected) {
        cameras = new LinkedHashSet<>(enabledCameras);
        if (!cameras.contains(selected)) throw new IllegalArgumentException("Unknown default camera");
        defaultCamera = selected; current = selected;
    }

    public boolean contains(String id) { return cameras.contains(id); }
    public String defaultCamera() { return defaultCamera; }

    public boolean selectDefault(String id) {
        if (!cameras.contains(id)) return false;
        defaultCamera = id; current = id; motions.clear();
        return true;
    }

    public boolean activity(String id, boolean active, long now, long expiresAt) {
        if (!cameras.contains(id) || expiresAt <= now || expiresAt - now > 120_000) return false;
        if (!active) { motions.remove(id); return true; }
        Motion previous = motions.get(id);
        motions.put(id, new Motion(previous != null && previous.until > now ? previous.order : ++sequence, expiresAt));
        return true;
    }

    public String camera(long now) {
        motions.entrySet().removeIf(entry -> entry.getValue().until <= now);
        // Keep an active override stable: heartbeat refreshes do not bounce between cameras.
        if (!current.equals(defaultCamera) && motions.containsKey(current)) return current;
        String candidate = defaultCamera; long newest = Long.MIN_VALUE;
        for (Map.Entry<String, Motion> entry : motions.entrySet()) {
            if (!entry.getKey().equals(defaultCamera) && entry.getValue().order > newest) {
                candidate = entry.getKey(); newest = entry.getValue().order;
            }
        }
        current = candidate;
        return current;
    }

    private static final class Motion {
        final long order, until;
        Motion(long order, long until) { this.order = order; this.until = until; }
    }
}
