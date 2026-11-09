package com.fieldview.tv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Native, single-threaded view policy. All times are epoch milliseconds. */
public final class ViewSelector {
    public static final String AUTO = "auto", GRID = "grid", PIN = "pin";
    private final List<String> cameras;
    private final Map<String, List<String>> zones;
    private final Map<String, Motion> motions = new HashMap<>();
    private final Map<String, Horse> horses = new HashMap<>();
    private int capacity;
    private final int maxCapacity;
    private String mode = AUTO, pinned;
    private String defaultCamera;
    private Selection current;
    private long changedAt;

    public ViewSelector(List<String> cameraIds, Map<String, List<String>> zoneCameras, int capacity) {
        this(cameraIds, zoneCameras, capacity, 9);
    }

    public ViewSelector(List<String> cameraIds, Map<String, List<String>> zoneCameras, int capacity, int maxCapacity) {
        if (maxCapacity < 1 || maxCapacity > 13) throw new IllegalArgumentException("Invalid playback limit");
        this.maxCapacity = maxCapacity;
        if (new LinkedHashSet<>(cameraIds).size() != cameraIds.size()) throw new IllegalArgumentException("Duplicate camera IDs");
        cameras = new ArrayList<>(cameraIds);
        zones = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : zoneCameras.entrySet()) zones.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        setCapacity(capacity);
    }

    public void setCapacity(int count) {
        if (count < 1 || count > 13) throw new IllegalArgumentException("Camera count must be 1–13");
        capacity = Math.min(count, maxCapacity);
        current = null;
    }
    public int maxCapacity() { return maxCapacity; }
    public void setDefaultCamera(String id) { defaultCamera = id; }

    /** Keep activity/location and a valid manual selection when the catalog is edited. */
    public void reconfigure(List<String> ids, Map<String, List<String>> mapping) {
        if (new LinkedHashSet<>(ids).size() != ids.size()) throw new IllegalArgumentException("Duplicate camera IDs");
        cameras.clear(); cameras.addAll(ids); zones.clear();
        for (Map.Entry<String, List<String>> entry : mapping.entrySet()) zones.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        motions.keySet().retainAll(cameras);
        horses.entrySet().removeIf(entry -> !zones.containsKey(entry.getValue().zone));
        if (PIN.equals(mode) && !cameras.contains(pinned)) { mode = AUTO; pinned = null; }
        current = null;
    }

    public boolean setMode(String next, String cameraId) {
        if (!AUTO.equals(next) && !GRID.equals(next) && !PIN.equals(next)) return false;
        if (PIN.equals(next) && !cameras.contains(cameraId)) return false;
        mode = next;
        pinned = PIN.equals(next) ? cameraId : null;
        current = null;
        return true;
    }

    /** Browse the complete enabled catalog, independent of the grid's tile budget. */
    public boolean stepPinnedCamera(int direction) {
        if (!PIN.equals(mode) || cameras.isEmpty() || (direction != -1 && direction != 1)) return false;
        int index = cameras.indexOf(pinned);
        if (index < 0) return false;
        int next = (index + direction + cameras.size()) % cameras.size();
        if (next == index) return false;
        return setMode(PIN, cameras.get(next));
    }

    public boolean activity(String id, long now, long expiresAt) {
        if (!cameras.contains(id) || expiresAt <= now || expiresAt - now > 120_000) return false;
        Motion previous = motions.get(id);
        // Heartbeats extend a detection, rather than making publishing order the motion priority.
        motions.put(id, new Motion(previous != null && previous.expiresAt > now ? previous.triggeredAt : now, expiresAt));
        return true;
    }

    public boolean clearActivity(String id) {
        if (!cameras.contains(id)) return false;
        motions.remove(id);
        return true;
    }

    public boolean location(String horseId, String zone, long now, long observedAt) {
        if (horseId == null || horseId.trim().isEmpty() || horseId.length() > 80) return false;
        if (observedAt > now + 5_000 || observedAt <= now - 60_000) return false;
        Horse previous = horses.get(horseId);
        if (previous != null && observedAt <= previous.updatedAt) return false;
        if (!zones.containsKey(zone)) { horses.remove(horseId); return true; }
        horses.put(horseId, new Horse(zone, previous != null && previous.zone.equals(zone) ? previous.since : now, observedAt));
        return true;
    }

    public Selection snapshot(long now) {
        motions.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        horses.entrySet().removeIf(entry -> now - entry.getValue().updatedAt >= 60_000);
        List<String> motionIds = new ArrayList<>(motions.keySet());
        motionIds.sort(Comparator.<String>comparingLong(id -> motions.get(id).triggeredAt).reversed().thenComparing(id -> id));
        List<String> horseIds = new ArrayList<>(horses.keySet());
        Collections.sort(horseIds);
        LinkedHashSet<String> presence = new LinkedHashSet<>();
        for (String id : horseIds) {
            Horse horse = horses.get(id);
            if (now - horse.since >= 5_000) for (String camera : zones.get(horse.zone)) if (cameras.contains(camera)) presence.add(camera);
        }
        List<String> presenceIds = new ArrayList<>(presence);
        String reason;
        int priority;
        List<String> featured = new ArrayList<>();
        int featuredLimit = capacity == 13 ? 1 : Math.min(2, capacity);
        if (PIN.equals(mode)) { reason = PIN; priority = 100; featured.add(pinned); }
        else if (GRID.equals(mode)) { reason = GRID; priority = 100; }
        else if (!motionIds.isEmpty()) { reason = "activity"; priority = 20; featured.addAll(motionIds.subList(0, Math.min(featuredLimit, motionIds.size()))); }
        else if (!presenceIds.isEmpty()) { reason = "location"; priority = 10; featured.addAll(presenceIds.subList(0, Math.min(featuredLimit, presenceIds.size()))); }
        else { reason = "overview"; priority = 0; }

        boolean same = current != null && current.reason.equals(reason) && current.featured.equals(featured);
        List<String> eligible = current != null && current.reason.equals("activity") ? motionIds : presenceIds;
        boolean hold = current != null && !current.featured.isEmpty() && eligible.containsAll(current.featured)
                && priority <= current.priority && now - changedAt < 8_000;
        if (!same && !hold) { current = new Selection(reason, priority, featured, Collections.emptyList(), mode); changedAt = now; }
        LinkedHashSet<String> ordered = new LinkedHashSet<>(current.featured);
        if (!PIN.equals(mode) && capacity == 13 && cameras.contains(defaultCamera)) ordered.add(defaultCamera);
        if (!PIN.equals(mode)) ordered.addAll(cameras);
        List<String> visible = new ArrayList<>(ordered);
        if (visible.size() > capacity) visible = new ArrayList<>(visible.subList(0, capacity));
        return new Selection(current.reason, current.priority, current.featured, visible, mode);
    }

    private static final class Motion {
        final long triggeredAt, expiresAt;
        Motion(long triggeredAt, long expiresAt) { this.triggeredAt = triggeredAt; this.expiresAt = expiresAt; }
    }
    private static final class Horse {
        final String zone;
        final long since, updatedAt;
        Horse(String zone, long since, long updatedAt) { this.zone = zone; this.since = since; this.updatedAt = updatedAt; }
    }
    public static final class Selection {
        public final String reason, mode;
        public final int priority;
        public final List<String> featured, visible;
        Selection(String reason, int priority, List<String> featured, List<String> visible, String mode) {
            this.reason = reason; this.priority = priority; this.mode = mode;
            this.featured = Collections.unmodifiableList(new ArrayList<>(featured));
            this.visible = Collections.unmodifiableList(new ArrayList<>(visible));
        }
    }
}
