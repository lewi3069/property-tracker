package com.fieldview.tv;

import org.junit.Test;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class CommandRouterTest {
    private ViewSelector selector() {
        return new ViewSelector(Arrays.asList("barn", "yard"), Collections.singletonMap("yard", Collections.singletonList("yard")), 2);
    }
    private String activity(String event, long expires) throws Exception {
        return new JSONObject().put("type", "activity").put("camera_id", "barn").put("event_id", event).put("expires_at", expires).toString();
    }
    @Test public void retainedDuplicateAndExpiredActivityAreRejected() throws Exception {
        CommandRouter router = new CommandRouter(selector(), (count, until) -> fail());
        assertFalse(router.accept(activity("a", 30_000), true, 0));
        assertTrue(router.accept(activity("a", 30_000), false, 0));
        assertFalse(router.accept(activity("a", 30_000), false, 1));
        assertFalse(router.accept(activity("b", 30_000), false, 30_000));
        assertFalse(router.accept(activity("c", 999_999), false, 0));
    }
    @Test public void malformedOrOversizedMessagesCannotChangeTheView() {
        CommandRouter router = new CommandRouter(selector(), (count, until) -> fail());
        assertFalse(router.accept("not JSON", false, 0));
        assertFalse(router.accept("{\"type\":\"activity\"}", false, 0));
        assertFalse(router.accept(new String(new char[8193]), false, 0));
    }
    @Test public void temporaryLayoutAcceptsIsoExpiryAndOnlyIntegralCounts() {
        int[] applied = {0};
        CommandRouter router = new CommandRouter(selector(), (count, until) -> applied[0] = count);
        assertTrue(router.accept("{\"type\":\"layout\",\"max_visible\":6,\"expires_at\":\"2026-11-02T15:30:30Z\"}", false, 1_793_633_400_000L));
        assertEquals(6, applied[0]);
        assertFalse(router.accept("{\"type\":\"layout\",\"max_visible\":1.5,\"expires_at\":30000}", false, 0));
        assertFalse(router.accept("{\"type\":\"layout\",\"max_visible\":\"9\",\"expires_at\":30000}", false, 0));
        assertFalse(router.accept("{\"type\":\"layout\",\"max_visible\":16,\"expires_at\":30000}", false, 0));
    }
    @Test public void oldOrOutOfOrderHorseReportsAreRejectedIncludingAfterSignalLoss() throws Exception {
        ViewSelector selector = selector();
        CommandRouter router = new CommandRouter(selector, (count, until) -> fail());
        JSONObject location = new JSONObject().put("type", "location").put("horse_id", "horse-1").put("zone", "yard").put("observed_at", 1000);
        assertTrue(router.accept(location.toString(), false, 1000));
        assertFalse(router.accept(location.toString(), false, 1001));
        location.put("observed_at", 2000).put("zone", "unknown");
        assertTrue(router.accept(location.toString(), false, 2000));
        location.put("observed_at", 1500).put("zone", "yard");
        assertFalse(router.accept(location.toString(), false, 2001));
        assertTrue(selector.snapshot(10_000).featured.isEmpty());
        location.put("observed_at", Long.MIN_VALUE);
        assertFalse(router.accept(location.toString(), false, 100_000));
    }

    @Test public void remoteLayoutCannotBypassDevicePlaybackLimit() {
        ViewSelector selector = new ViewSelector(Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h", "i"), Collections.emptyMap(), 2, 4);
        CommandRouter router = new CommandRouter(selector, (count, until) -> selector.setCapacity(count));
        assertTrue(router.accept("{\"type\":\"layout\",\"max_visible\":9,\"expires_at\":30000}", false, 0));
        assertEquals(4, selector.snapshot(1).visible.size());
    }
}
