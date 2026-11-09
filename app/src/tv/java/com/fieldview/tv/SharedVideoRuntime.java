package com.fieldview.tv;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** UI-thread ownership; the last reference completes only after worker-thread disposal. */
final class SharedVideoRuntime<T> {
    private final Executor worker;
    private final Consumer<Runnable> post;
    private final Consumer<T> dispose;
    private final Runnable reportFailure;
    private T current;
    private int references;

    SharedVideoRuntime(Executor worker, Consumer<Runnable> post, Consumer<T> dispose,
                       Runnable reportFailure) {
        this.worker = worker; this.post = post; this.dispose = dispose;
        this.reportFailure = reportFailure;
    }

    T acquire(Supplier<T> create) {
        if (current == null) current = create.get();
        references++;
        return current;
    }

    void release(Runnable complete) {
        if (references == 0) throw new IllegalStateException("No video runtime reference");
        if (--references != 0) { complete.run(); return; }
        T retired = current; current = null;
        worker.execute(() -> {
            try { dispose.accept(retired); }
            catch (RuntimeException ignored) { reportFailure.run(); }
            finally { post.accept(complete); }
        });
    }
}
