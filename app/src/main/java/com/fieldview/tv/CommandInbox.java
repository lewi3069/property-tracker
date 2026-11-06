package com.fieldview.tv;

import android.os.Handler;
import java.util.ArrayDeque;

/** Bounded handoff to the UI thread; a broker burst cannot queue unlimited UI tasks. */
final class CommandInbox {
    interface Receiver { void receive(String payload, boolean retained); }
    private final Handler handler;
    private final Receiver receiver;
    private final ArrayDeque<Message> pending = new ArrayDeque<>();
    private final Runnable drain = this::drain;
    private boolean scheduled, closed;
    CommandInbox(Handler handler, Receiver receiver) { this.handler = handler; this.receiver = receiver; }
    synchronized void offer(String payload, boolean retained) {
        if (closed || payload == null || payload.length() > 8192) return;
        if (pending.size() == 64) pending.removeFirst();
        pending.addLast(new Message(payload, retained));
        if (!scheduled) { scheduled = true; handler.post(drain); }
    }
    private void drain() {
        for (int i = 0; i < 8; i++) {
            Message message;
            synchronized (this) {
                if (closed) return;
                message = pending.pollFirst();
                if (message == null) { scheduled = false; return; }
            }
            receiver.receive(message.payload, message.retained);
        }
        synchronized (this) { if (!closed) handler.post(drain); }
    }
    synchronized void close() { closed = true; pending.clear(); handler.removeCallbacks(drain); }
    private static final class Message {
        final String payload; final boolean retained;
        Message(String payload, boolean retained) { this.payload = payload; this.retained = retained; }
    }
}
