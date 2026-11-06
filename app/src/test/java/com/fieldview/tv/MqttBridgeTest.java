package com.fieldview.tv;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.*;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MqttBridgeTest {
    static class Clock extends ScheduledThreadPoolExecutor {
        long now; final List<Job> jobs = new ArrayList<>();
        Clock() { super(1); }
        @Override public void execute(Runnable task) { task.run(); }
        @Override public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) { Job job = new Job(task, now + unit.toMillis(delay)); jobs.add(job); return job; }
        void advance(long millis) {
            long end = now + millis;
            while (true) { Job next = jobs.stream().filter(j -> !j.done && j.at <= end).min(Comparator.comparingLong(j -> j.at)).orElse(null); if (next == null) break; now = next.at; next.done = true; next.task.run(); }
            now = end;
        }
    }
    static class Job implements ScheduledFuture<Object> {
        final Runnable task; final long at; boolean done;
        Job(Runnable task, long at) { this.task = task; this.at = at; }
        public long getDelay(TimeUnit unit) { return 0; }
        public int compareTo(Delayed other) { return 0; }
        public boolean cancel(boolean interrupt) { done = true; return true; }
        public boolean isCancelled() { return done; }
        public boolean isDone() { return done; }
        public Object get() { return null; }
        public Object get(long value, TimeUnit unit) { return null; }
    }
    static class Client extends MqttAsyncClient {
        IMqttActionListener connect, subscribe; MqttCallback callback; boolean closed, connected;
        Client() throws Exception { super("tcp://example.invalid:1883", UUID.randomUUID().toString(), new MemoryPersistence()); }
        @Override public void setCallback(MqttCallback callback) { this.callback = callback; }
        @Override public IMqttToken connect(MqttConnectOptions options, Object context, IMqttActionListener listener) { connect = listener; return new MqttToken(); }
        @Override public IMqttToken subscribe(String topic, int qos, Object context, IMqttActionListener listener) { subscribe = listener; return new MqttToken(); }
        @Override public boolean isConnected() { return connected; }
        @Override public void disconnectForcibly(long quiesce, long timeout) { connected = false; }
        @Override public void close(boolean force) { closed = true; }
        void connectOk() { connected = true; connect.onSuccess(new MqttToken()); }
        void ack(int qos) { subscribe.onSuccess(new MqttToken() { @Override public int[] getGrantedQos() { return new int[]{qos}; } }); }
    }
    static class Fixture implements MqttBridge.Listener {
        final Clock clock = new Clock(); final List<Client> clients = new ArrayList<>(); final List<String> statuses = new ArrayList<>();
        final MqttBridge bridge = new MqttBridge(clock, (broker, id) -> { Client c = new Client(); clients.add(c); return c; });
        Fixture() { AppConfig config = new AppConfig(); config.broker = "tcp://example.invalid:1883"; bridge.start(config, this); }
        public void status(String value) { statuses.add(value); }
        public void message(String value, boolean retained) { }
    }
    @Test public void connectedRequiresAnAcceptedSubscriptionAndRejectedAckRetries() {
        Fixture f = new Fixture(); try {
            Client first = f.clients.get(0); first.connectOk();
            assertFalse(f.statuses.contains("Home Assistant control connected"));
            first.ack(128); assertTrue(first.closed); f.clock.advance(2000);
            assertEquals(2, f.clients.size()); Client second = f.clients.get(1); second.connectOk(); second.ack(1);
            assertEquals("Home Assistant control connected", f.statuses.get(f.statuses.size() - 1));
        } finally { f.bridge.stop(); }
    }
    @Test public void initialFailureBacksOffAndStopIsIdempotentAndCancelsRetry() {
        Fixture f = new Fixture();
        f.clients.get(0).connect.onFailure(null, new Exception()); f.clock.advance(1999); assertEquals(1, f.clients.size());
        f.clock.advance(1); assertEquals(2, f.clients.size()); f.clients.get(1).connect.onFailure(null, new Exception());
        f.clock.advance(3999); assertEquals(2, f.clients.size()); f.bridge.stop(); f.bridge.stop(); f.clock.advance(100000);
        assertEquals(2, f.clients.size());
    }
    @Test public void missingConnectAndSubscriptionCallbacksHaveDeadlines() {
        Fixture f = new Fixture(); try {
            f.clock.advance(14000); assertEquals(2, f.clients.size()); f.clients.get(1).connectOk();
            f.clock.advance(12000); assertEquals(3, f.clients.size());
            Client late = f.clients.get(2); f.bridge.stop(); int statuses = f.statuses.size(); late.connectOk();
            assertEquals(statuses, f.statuses.size()); assertTrue(late.closed);
        } finally { f.bridge.stop(); }
    }
}
