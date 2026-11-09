package com.fieldview.tv;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class SharedVideoRuntimeTest {
    @Test public void finalReleaseKeepsAdmissionBlockedUntilWorkerDisposalAndUiCompletion() {
        ArrayDeque<Runnable> worker = new ArrayDeque<>(), ui = new ArrayDeque<>();
        List<String> events = new ArrayList<>();
        SharedVideoRuntime<Object> pool = new SharedVideoRuntime<>(worker::add, ui::add,
                value -> events.add("disposed"), () -> fail("Unexpected cleanup failure"));
        Object first = pool.acquire(Object::new);
        assertSame(first, pool.acquire(() -> { throw new AssertionError("Unexpected extra runtime"); }));
        pool.release(() -> events.add("first-tile-done"));
        assertTrue(worker.isEmpty());
        pool.release(() -> events.add("last-tile-done"));
        assertEquals(List.of("first-tile-done"), events);
        worker.remove().run();
        assertEquals(List.of("first-tile-done", "disposed"), events);
        ui.remove().run();
        assertEquals(List.of("first-tile-done", "disposed", "last-tile-done"), events);
    }

    @Test public void retiredCompletionCannotReleaseAReplacementAndDisposalFailureStillCompletesOnUi() {
        ArrayDeque<Runnable> worker = new ArrayDeque<>(), ui = new ArrayDeque<>();
        List<Object> disposed = new ArrayList<>();
        List<String> completed = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        SharedVideoRuntime<Object> pool = new SharedVideoRuntime<>(worker::add, ui::add, value -> {
            disposed.add(value);
            if (disposed.size() == 1) throw new IllegalStateException("Simulated cleanup failure");
        }, () -> failures.add("failed"));
        Object old = pool.acquire(Object::new);
        pool.release(() -> completed.add("old"));
        Object replacement = pool.acquire(Object::new);
        assertNotSame(old, replacement);
        worker.remove().run();
        assertEquals(List.of("failed"), failures);
        assertTrue(completed.isEmpty()); ui.remove().run();
        assertSame(replacement, pool.acquire(Object::new));
        pool.release(() -> completed.add("replacement-first"));
        assertTrue(worker.isEmpty());
        pool.release(() -> completed.add("replacement-last"));
        worker.remove().run(); ui.remove().run();
        assertEquals(List.of(old, replacement), disposed);
        assertEquals(List.of("old", "replacement-first", "replacement-last"), completed);
        assertEquals(List.of("failed"), failures);
    }
}
