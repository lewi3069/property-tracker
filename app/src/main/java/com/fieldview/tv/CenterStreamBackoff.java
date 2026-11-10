package com.fieldview.tv;

import java.util.HashMap;
import java.util.Map;

/** Per-viewer cooldown, so motion returns do not repeatedly reopen a failing main stream. */
final class CenterStreamBackoff {
    static final long COOLDOWN_MS = 5 * 60_000;
    private static final class Failure {
        final String uri;
        final long until;
        Failure(String uri, long until) { this.uri = uri; this.until = until; }
    }
    private final Map<String, Failure> failures = new HashMap<>();
    void failed(AppConfig.Camera camera, long now) {
        failures.put(camera.id, new Failure(camera.streamUri(true), now + COOLDOWN_MS));
    }
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
