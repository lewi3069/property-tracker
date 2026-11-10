package com.fieldview.tv;

import org.json.JSONObject;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Validates untrusted MQTT input before it reaches the native view controller. */
public final class CommandRouter {
    public interface LayoutHandler { void apply(int count, long expiresAt); }
    public interface ActivityHandler { boolean apply(String camera, boolean active, long now, long expiresAt); }
    public interface FloatingHandler { boolean apply(String action, String camera); }
    private final ViewSelector selector;
    private final LayoutHandler layouts;
    private final ActivityHandler activities;
    private final FloatingHandler floating;
    private final Map<String, Long> seen = new LinkedHashMap<>();
    private final Map<String, Long> horseTimes = new HashMap<>();
    public CommandRouter(ViewSelector selector, LayoutHandler layouts) {
        this.selector = selector; this.layouts = layouts; this.floating = null;
        this.activities = (id, active, now, until) -> active ? selector.activity(id, now, until) : selector.clearActivity(id, now);
    }
    public CommandRouter(ActivityHandler activities, FloatingHandler floating) {
        this.selector = null; this.layouts = null; this.activities = activities; this.floating = floating;
    }

    public boolean accept(String payload, boolean retained, long now) {
        if (retained || payload == null || payload.length() > 8192) return false;
        try {
            JSONObject data = new JSONObject(payload);
            String type = data.getString("type");
            if ("location".equals(type)) {
                if (selector == null) return false;
                String horse = data.getString("horse_id"), zone = data.getString("zone");
                if (horse.length() > 80 || zone.length() > 80) return false;
                long observed = timestamp(data.get("observed_at"));
                if (observed > now + 5_000 || observed <= now - 60_000 || observed <= horseTimes.getOrDefault(horse, Long.MIN_VALUE)) return false;
                horseTimes.entrySet().removeIf(entry -> entry.getValue() <= now - 60_000);
                if (horseTimes.size() >= 128 && !horseTimes.containsKey(horse)) return false;
                if (!selector.location(horse, zone, now, observed)) return false;
                horseTimes.put(horse, observed);
                return true;
            }
            long expires = timestamp(data.get("expires_at"));
            if (expires <= now || expires - now > 120_000) return false;
            if ("activity".equals(type) || "activity_end".equals(type)) {
                if (data.has("active") && !(data.get("active") instanceof Boolean)) return false;
                String event = data.getString("event_id");
                if (event.isEmpty() || event.length() > 128) return false;
                seen.entrySet().removeIf(entry -> entry.getValue() <= now);
                String key = "activity:" + event;
                if (seen.containsKey(key)) return false;
                boolean active = !"activity_end".equals(type) && (!data.has("active") || data.getBoolean("active"));
                if (!activities.apply(data.getString("camera_id"), active, now, expires)) return false;
                seen.put(key, expires);
                if (seen.size() > 256) { Iterator<String> it = seen.keySet().iterator(); it.next(); it.remove(); }
                return true;
            }
            if ("layout".equals(type)) {
                if (layouts == null) return false;
                Object count = data.get("max_visible");
                if (!(count instanceof Number)) return false;
                double number = ((Number) count).doubleValue();
                if (number != Math.rint(number) || number < 1 || number > 13) return false;
                layouts.apply((int) number, expires);
                return true;
            }
            if ("floating".equals(type)) {
                if (floating == null) return false;
                String action = data.getString("action");
                if (!"select".equals(action) && !"close".equals(action)) return false;
                String event = data.getString("event_id");
                if (event.isEmpty() || event.length() > 128) return false;
                seen.entrySet().removeIf(entry -> entry.getValue() <= now);
                String key = "floating:" + event;
                if (seen.containsKey(key)) return false;
                String camera = "select".equals(action) ? data.getString("camera_id") : null;
                if (!floating.apply(action, camera)) return false;
                seen.put(key, expires);
                if (seen.size() > 256) { Iterator<String> it = seen.keySet().iterator(); it.next(); it.remove(); }
                return true;
            }
        } catch (Exception ignored) { /* Malformed commands must not interrupt viewing. */ }
        return false;
    }

    private static long timestamp(Object value) {
        if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            if (!Double.isFinite(number) || number != Math.rint(number)) throw new IllegalArgumentException();
            return ((Number) value).longValue();
        }
        return Instant.parse((String) value).toEpochMilli();
    }
}
