package com.fieldview.tv;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class StartupCoordinatorTest {
    @Test public void nativeRetirementBlocksNewStartsUntilAllReleasesComplete() {
        Clock clock=new Clock();StartupCoordinator c=new StartupCoordinator(clock,2,20000);
        Runnable first=c.beginRelease(),second=c.beginRelease();List<String> starts=new ArrayList<>();
        c.enqueue(true,t->starts.add("new"),()->{});clock.advance(25000);assertTrue(starts.isEmpty());
        first.run();first.run();clock.advance(0);assertTrue(starts.isEmpty());
        second.run();clock.advance(0);assertEquals(List.of("new"),starts);
    }
    private static final class Clock implements StartupCoordinator.Scheduler {
        private static final class Task {
            Runnable run; long at, sequence;
            Task(Runnable run, long at, long sequence) { this.run = run; this.at = at; this.sequence = sequence; }
        }
        long now, sequence;
        final List<Task> tasks = new ArrayList<>();
        public void post(Runnable run, long delay) { tasks.add(new Task(run, now + delay, sequence++)); }
        public void remove(Runnable run) { tasks.removeIf(t -> t.run == run); }
        void advance(long duration) {
            long end = now + duration;
            while (true) {
                tasks.sort(Comparator.<Task>comparingLong(t -> t.at).thenComparingLong(t -> t.sequence));
                if (tasks.isEmpty() || tasks.get(0).at > end) break;
                Task task = tasks.remove(0); now = task.at; task.run.run();
            }
            now = end;
        }
    }
    @Test public void twoStartsAtATimeWithFeaturedPriorityAndFifoWithinPriority() {
        Clock clock = new Clock(); StartupCoordinator c = new StartupCoordinator(clock, 2, 20_000);
        List<String> starts = new ArrayList<>();
        c.enqueue(false, t -> starts.add("grid1"), () -> fail("early timeout"));
        c.enqueue(false, t -> starts.add("grid2"), () -> fail("early timeout"));
        StartupCoordinator.Ticket feature = c.enqueue(true, t -> starts.add("feature"), () -> fail("early timeout"));
        assertTrue(starts.isEmpty()); clock.advance(0);
        assertEquals(List.of("feature", "grid1"), starts);
        feature.finish(); clock.advance(0);
        assertEquals(List.of("feature", "grid1", "grid2"), starts);
    }
    @Test public void timeoutReleasesAttemptBeforeAdmittingNextAndLateCompletionDoesNotFreeItsSlot() {
        Clock clock = new Clock(); StartupCoordinator c = new StartupCoordinator(clock, 1, 20_000);
        List<String> events = new ArrayList<>();
        StartupCoordinator.Ticket old = c.enqueue(false, t -> events.add("old-start"), () -> events.add("old-release"));
        StartupCoordinator.Ticket next = c.enqueue(false, t -> events.add("next-start"), () -> events.add("next-release"));
        c.enqueue(false, t -> events.add("third-start"), () -> {});
        clock.advance(20_000);
        assertEquals(List.of("old-start", "old-release", "next-start"), events);
        old.finish(); clock.advance(0);
        assertEquals(3, events.size());
        next.finish(); clock.advance(0); assertEquals("third-start", events.get(3));
    }
    @Test public void queuedWorkHasNoDeadlineUntilAdmittedAndCancelledWorkNeverStarts() {
        Clock clock = new Clock(); StartupCoordinator c = new StartupCoordinator(clock, 1, 20_000);
        StartupCoordinator.Ticket first = c.enqueue(false, t -> {}, () -> fail("first finished"));
        List<Long> starts = new ArrayList<>();
        c.enqueue(false, t -> starts.add(clock.now), () -> starts.add(-clock.now));
        StartupCoordinator.Ticket cancelled = c.enqueue(true, t -> fail("cancelled"), () -> fail("cancelled timeout"));
        cancelled.finish(); clock.advance(15_000); first.finish(); clock.advance(0);
        assertEquals(List.of(15_000L), starts);
        clock.advance(19_999); assertEquals(1, starts.size());
        clock.advance(1); assertEquals(List.of(15_000L, -35_000L), starts);
    }
}
