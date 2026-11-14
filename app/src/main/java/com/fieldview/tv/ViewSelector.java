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
    private boolean motionLayoutEight;
    private final java.util.Set<String> panoramic = new java.util.HashSet<>();
    public void setPanoramicCamera(String id, boolean value) { if (value) panoramic.add(id); else panoramic.remove(id); }
    private boolean multiMotionLayout;
    private final MultiMotionLayout multiLayout = new MultiMotionLayout();
    private Selection current;
    private long changedAt;
    private long lastMotionEndedAt = Long.MIN_VALUE;
    private static final long MOTION_CAMERA_DWELL_MS = 10_000, MOTION_QUIET_MS = 15_000;

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
        multiLayout.reset();
    }
    public int maxCapacity() { return maxCapacity; }
    public void setDefaultCamera(String id) { defaultCamera = id; }
    public void setMotionLayoutEight(boolean value) { motionLayoutEight = value; }
    public void setMultiMotionLayout(boolean value) { if (multiMotionLayout != value) multiLayout.reset(); multiMotionLayout = value; }

    /** Keep activity/location and a valid manual selection when the catalog is edited. */
    public void reconfigure(List<String> ids, Map<String, List<String>> mapping) {
        if (new LinkedHashSet<>(ids).size() != ids.size()) throw new IllegalArgumentException("Duplicate camera IDs");
        cameras.clear(); cameras.addAll(ids); zones.clear();
        for (Map.Entry<String, List<String>> entry : mapping.entrySet()) zones.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        motions.keySet().retainAll(cameras);
        horses.entrySet().removeIf(entry -> !zones.containsKey(entry.getValue().zone));
        if (PIN.equals(mode) && !cameras.contains(pinned)) { mode = AUTO; pinned = null; }
        current = null;
        multiLayout.reset();
    }

    public boolean setMode(String next, String cameraId) {
        if (!AUTO.equals(next) && !GRID.equals(next) && !PIN.equals(next)) return false;
        if (PIN.equals(next) && !cameras.contains(cameraId)) return false;
        mode = next;
        pinned = PIN.equals(next) ? cameraId : null;
        current = null;
        multiLayout.reset();
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

    public boolean clearActivity(String id, long now) {
        if (!cameras.contains(id)) return false;
        Motion removed = motions.remove(id);
        // Repeated off messages must not extend the quiet period. An already-expired
        // event ended at its deadline, even if we did not render while backgrounded.
        if (removed != null) lastMotionEndedAt = Math.max(lastMotionEndedAt, Math.min(now, removed.expiresAt));
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
        motions.entrySet().removeIf(entry -> {
            if (entry.getValue().expiresAt > now) return false;
            lastMotionEndedAt = Math.max(lastMotionEndedAt, entry.getValue().expiresAt);
            return true;
        });
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
        boolean adaptive = motionLayoutEight && capacity == 13 && cameras.size() >= 13 && AUTO.equals(mode);
        boolean activitySelected = current != null && "activity".equals(current.reason);
        // Keep the first detection immediate, but avoid cycling main decoders on
        // short motion/off bursts. Other layouts and explicit Grid/Pin stay immediate.
        boolean calmHold = adaptive && activitySelected && (motionIds.isEmpty()
                ? within(now, lastMotionEndedAt, MOTION_QUIET_MS)
                : within(now, changedAt, MOTION_CAMERA_DWELL_MS));
        boolean hold = calmHold || ((!adaptive || !activitySelected) && current != null && !current.featured.isEmpty() && eligible.containsAll(current.featured)
                && priority <= current.priority && within(now, changedAt, 8_000));
        if (!same && !hold) { current = new Selection(reason, priority, featured, Collections.emptyList(), mode); changedAt = now; }
        boolean motionEight = adaptive && "activity".equals(current.reason);
        int motionSlots = motionEight ? 1 : 0;
        List<String> selected = current.featured;
        if (motionEight && multiMotionLayout) {
            MultiMotionLayout.State layout = multiLayout.update(now, motionIds, current.featured);
            motionSlots = layout.slots; selected = layout.featured;
        } else multiLayout.reset();
        LinkedHashSet<String> ordered = new LinkedHashSet<>(selected);
        if (motionEight) ordered.addAll(motionIds);
        if (!PIN.equals(mode) && capacity == 13 && cameras.contains(defaultCamera)) ordered.add(defaultCamera);
        if (!PIN.equals(mode)) ordered.addAll(cameras);
        List<String> visible = new ArrayList<>(ordered);
        int visibleLimit = motionSlots == 4 ? 4 : motionSlots == 3 ? 7 : motionSlots == 2 ? 10 : motionEight ? 8 : capacity;
        if (motionSlots == 1 && !selected.isEmpty() && panoramic.contains(selected.get(0))) visibleLimit = 5;
        if (visible.size() > visibleLimit) visible = new ArrayList<>(visible.subList(0, visibleLimit));
        return new Selection(current.reason, current.priority, selected, visible, mode, motionSlots);
    }

    private static boolean within(long now, long since, long duration) {
        return since != Long.MIN_VALUE && now >= since && now - since < duration;
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
        public final boolean motionLayoutEight;
        public final int motionLayoutSlots;
        public final List<String> featured, visible;
        Selection(String reason, int priority, List<String> featured, List<String> visible, String mode) {
            this(reason, priority, featured, visible, mode, false);
        }
        Selection(String reason, int priority, List<String> featured, List<String> visible, String mode, boolean motionLayoutEight) {
            this(reason, priority, featured, visible, mode, motionLayoutEight ? 1 : 0);
        }
        Selection(String reason, int priority, List<String> featured, List<String> visible, String mode, int motionLayoutSlots) {
            this.reason = reason; this.priority = priority; this.mode = mode;
            this.motionLayoutSlots = motionLayoutSlots;
            this.motionLayoutEight = motionLayoutSlots == 1;
            this.featured = Collections.unmodifiableList(new ArrayList<>(featured));
            this.visible = Collections.unmodifiableList(new ArrayList<>(visible));
        }
    }
}
