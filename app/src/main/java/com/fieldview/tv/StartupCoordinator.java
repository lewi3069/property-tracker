package com.fieldview.tv;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/** Application-thread admission queue, shared across viewer/Float handovers. */
final class StartupCoordinator {
    private static final class Shared {
        static final StartupCoordinator VALUE=create();
        private static StartupCoordinator create() {
            android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
            return new StartupCoordinator(new Scheduler() {
                public void post(Runnable task,long delay) { main.postDelayed(task,delay); }
                public void remove(Runnable task) { main.removeCallbacks(task); }
            },2,VideoProgress.TIMEOUT_MS);
        }
    }
    static StartupCoordinator shared() { return Shared.VALUE; }
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
    private int retiring;
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
        if(retiring>0)return;
        waiting.sort(Comparator.<Ticket>comparingInt(t -> t.featured ? 0 : 1).thenComparingLong(t -> t.order));
        while (retiring == 0 && active < limit && !waiting.isEmpty()) {
            Ticket ticket = waiting.remove(0);
            if (ticket.done) continue;
            ticket.admitted = true; active++;
            scheduler.post(ticket.deadline, deadlineMs);
            ticket.start.accept(ticket);
        }
    }
    Runnable beginRelease() {
        retiring++;
        return new Runnable() {
            private boolean complete;
            @Override public void run() { if(complete)return;complete=true;retiring--;pump(); }
        };
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
        void stopDeadline() { scheduler.remove(deadline); }
    }
}
