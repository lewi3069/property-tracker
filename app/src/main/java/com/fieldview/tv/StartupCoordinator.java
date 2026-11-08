package com.fieldview.tv;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/** Viewer-owned admission queue. All methods and callbacks run on the player application thread. */
final class StartupCoordinator {
    interface Scheduler {
        void post(Runnable task, long delayMs);
        void remove(Runnable task);
    }
    private final Scheduler scheduler;
    private final int limit;
    private final long deadlineMs;
    private final List<Ticket> waiting = new ArrayList<>();
    private final Runnable drain = this::drain;
    private int active;
    private long sequence;

    StartupCoordinator(Scheduler scheduler, int limit, long deadlineMs) {
        if (limit < 1 || deadlineMs < 1) throw new IllegalArgumentException("Invalid startup budget");
        this.scheduler = scheduler; this.limit = limit; this.deadlineMs = deadlineMs;
    }

    Ticket enqueue(boolean featured, Consumer<Ticket> start, Runnable abort) {
        Ticket ticket = new Ticket(featured, sequence++, start, abort);
        waiting.add(ticket); pump(); return ticket;
    }
    private void pump() { scheduler.remove(drain); scheduler.post(drain, 0); }
    private void drain() {
        waiting.sort(Comparator.<Ticket>comparingInt(t -> t.featured ? 0 : 1).thenComparingLong(t -> t.order));
        while (active < limit && !waiting.isEmpty()) {
            Ticket ticket = waiting.remove(0);
            if (ticket.done) continue;
            ticket.admitted = true; active++;
            scheduler.post(ticket.deadline, deadlineMs);
            ticket.start.accept(ticket);
        }
    }

    final class Ticket {
        private boolean featured;
        private final long order;
        private final Consumer<Ticket> start;
        private final Runnable abort;
        private final Runnable deadline;
        private boolean admitted, done;
        private Ticket(boolean featured, long order, Consumer<Ticket> start, Runnable abort) {
            this.featured = featured; this.order = order; this.start = start; this.abort = abort;
            deadline = () -> {
                if (done) return;
                // Abort must detach/release the old player before the next admission.
                try { this.abort.run(); } finally { finish(); }
            };
        }
        /** Called after first frame, or after the cancelled/failed player has been released. */
        void finish() {
            if (done) return;
            done = true; waiting.remove(this); scheduler.remove(deadline);
            if (admitted) active--;
            pump();
        }
        void prioritize(boolean value) {
            if (done || admitted || featured == value) return;
            featured = value; pump();
        }
    }
}
