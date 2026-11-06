package com.fieldview.tv;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** One lifecycle-owned MQTT connection, including bounded initial/reconnect retries. */
public final class MqttBridge {
    public interface Listener {
        void status(String value);
        void message(String payload, boolean retained);
    }
    interface ClientFactory { MqttAsyncClient create(String broker, String id) throws Exception; }
    private final ScheduledExecutorService worker;
    private final ClientFactory factory;
    private volatile boolean stopped;
    private boolean started;
    private volatile MqttAsyncClient client;
    private ScheduledFuture<?> retry, deadline;
    private int failures;

    public MqttBridge() { this(executor(), (broker, id) -> new MqttAsyncClient(broker, id, new MemoryPersistence())); }
    MqttBridge(ScheduledExecutorService worker, ClientFactory factory) { this.worker = worker; this.factory = factory; }
    private static ScheduledExecutorService executor() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "PropertyTracker-MQTT"); thread.setDaemon(true); return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return executor;
    }

    public synchronized void start(AppConfig config, Listener listener) {
        if (started || stopped) return;
        started = true;
        if (config.broker.isEmpty()) { listener.status("Home Assistant control not configured"); return; }
        String broker = config.broker, id = config.clientId;
        String topic = "fieldview/" + config.site + "/" + config.display + "/command";
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(false); options.setCleanSession(true);
        options.setConnectionTimeout(8); options.setKeepAliveInterval(30);
        options.setHttpsHostnameVerificationEnabled(true);
        if (!config.brokerUser.isEmpty()) options.setUserName(config.brokerUser);
        if (!config.brokerPassword.isEmpty()) options.setPassword(config.brokerPassword.toCharArray());
        submit(() -> connect(broker, id, topic, options, listener));
    }

    private void submit(Runnable task) {
        if (stopped) return;
        try { worker.execute(() -> { if (!stopped) task.run(); }); }
        catch (java.util.concurrent.RejectedExecutionException ignored) { }
    }
    private boolean current(MqttAsyncClient candidate) { return !stopped && client == candidate; }

    private void connect(String broker, String id, String topic, MqttConnectOptions options, Listener listener) {
        if (stopped) return;
        listener.status("Home Assistant control connecting");
        try {
            MqttAsyncClient next = factory.create(broker, id); client = next;
            Runnable failed = () -> fail(next, broker, id, topic, options, listener);
            next.setCallback(new MqttCallback() {
                @Override public void connectionLost(Throwable cause) { submit(failed); }
                @Override public void messageArrived(String incoming, MqttMessage message) {
                    if (current(next) && topic.equals(incoming) && message.getPayload().length <= 8192)
                        listener.message(new String(message.getPayload(), StandardCharsets.UTF_8), message.isRetained());
                }
                @Override public void deliveryComplete(IMqttDeliveryToken token) { }
            });
            deadline = worker.schedule(failed, 12, TimeUnit.SECONDS);
            next.connect(options, null, new IMqttActionListener() {
                @Override public void onFailure(IMqttToken token, Throwable error) { submit(failed); }
                @Override public void onSuccess(IMqttToken token) { submit(() -> {
                    if (!current(next)) return;
                    cancelDeadline();
                    deadline = worker.schedule(failed, 8, TimeUnit.SECONDS);
                    try { next.subscribe(topic, 1, null, new IMqttActionListener() {
                        @Override public void onFailure(IMqttToken token, Throwable error) { submit(failed); }
                        @Override public void onSuccess(IMqttToken token) { submit(() -> {
                            if (!current(next)) return;
                            int[] granted = token.getGrantedQos();
                            if (granted == null || granted.length != 1 || granted[0] < 0 || granted[0] > 1) { failed.run(); return; }
                            cancelDeadline(); failures = 0;
                            listener.status("Home Assistant control connected");
                        }); }
                    }); } catch (Exception ignored) { failed.run(); }
                }); }
            });
        } catch (Exception ignored) { fail(client, broker, id, topic, options, listener); }
    }

    private void fail(MqttAsyncClient candidate, String broker, String id, String topic, MqttConnectOptions options, Listener listener) {
        if (!current(candidate)) return;
        cancelDeadline(); dispose();
        listener.status("Home Assistant control reconnecting");
        long delay = Math.min(30, 2L << Math.min(failures++, 4));
        retry = worker.schedule(() -> connect(broker, id, topic, options, listener), delay, TimeUnit.SECONDS);
    }
    private void cancelDeadline() { if (deadline != null) { deadline.cancel(false); deadline = null; } }
    private void dispose() {
        MqttAsyncClient old = client; client = null;
        if (old == null) return;
        try { if (old.isConnected()) old.disconnectForcibly(100, 300); } catch (Exception ignored) { }
        try { old.close(true); } catch (Exception ignored) { }
    }
    public synchronized void stop() {
        if (stopped) return;
        stopped = true;
        worker.execute(() -> { if (retry != null) retry.cancel(false); cancelDeadline(); dispose(); });
        worker.shutdown();
    }
}
