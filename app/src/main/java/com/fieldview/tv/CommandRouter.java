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
    private final ViewSelector selector;
    private final LayoutHandler layouts;
    private final Map<String, Long> seen = new LinkedHashMap<>();
    private final Map<String, Long> horseTimes = new HashMap<>();
    public CommandRouter(ViewSelector selector, LayoutHandler layouts) { this.selector = selector; this.layouts = layouts; }

    public boolean accept(String payload, boolean retained, long now) {
        if (retained || payload == null || payload.length() > 8192) return false;
        try {
            JSONObject data = new JSONObject(payload);
            String type = data.getString("type");
            if ("location".equals(type)) {
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
            if ("activity".equals(type)) {
                String event = data.getString("event_id");
                if (event.isEmpty() || event.length() > 128) return false;
                seen.entrySet().removeIf(entry -> entry.getValue() <= now);
                if (seen.containsKey(event)) return false;
                if (!selector.activity(data.getString("camera_id"), now, expires)) return false;
                seen.put(event, expires);
                if (seen.size() > 256) { Iterator<String> it = seen.keySet().iterator(); it.next(); it.remove(); }
                return true;
            }
            if ("layout".equals(type)) {
                Object count = data.get("max_visible");
                if (!(count instanceof Number)) return false;
                double number = ((Number) count).doubleValue();
                if (number != Math.rint(number) || number < 1 || number > 9) return false;
                layouts.apply((int) number, expires);
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
