package com.fieldview.tv;

import java.util.HashMap;
import java.util.Map;

/** Per-viewer cooldown, so motion returns do not repeatedly reopen a failing main stream. */
final class CenterStreamBackoff {
    static final long COOLDOWN_MS = 5 * 60_000;
    private static final class Failure {
        final String uri;
        final long until;
        final boolean formatUnsupported;
        Failure(String uri, long until, boolean formatUnsupported) { this.uri = uri; this.until = until; this.formatUnsupported = formatUnsupported; }
    }
    private final Map<String, Failure> failures = new HashMap<>();
    void failed(AppConfig.Camera camera, long now) {
        failed(camera, now, false);
    }
    void failed(AppConfig.Camera camera, long now, boolean formatUnsupported) {
        failures.put(camera.id, new Failure(camera.streamUri(true), now + COOLDOWN_MS, formatUnsupported));
    }
    boolean formatUnsupported(AppConfig.Camera camera, long now) { return blocked(camera, now) && failures.get(camera.id).formatUnsupported; }
    boolean blocked(AppConfig.Camera camera, long now) {
        Failure failure = failures.get(camera.id);
        if (failure == null) return false;
        if (now >= failure.until || !camera.streamUri(true).equals(failure.uri)) {
            failures.remove(camera.id); return false;
        }
        return true;
    }
    void clear() { failures.clear(); }
}
