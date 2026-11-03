package com.fieldview.tv;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Foreground-only MQTT connection; it never needs a Home Assistant API token. */
public final class MqttBridge {
    public interface Listener {
        void status(String value);
        void message(String payload, boolean retained);
    }
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean stopped;
    private volatile MqttAsyncClient client;

    public void start(AppConfig config, Listener listener) {
        if (config.broker.isEmpty()) { listener.status("Home Assistant control not configured"); return; }
        final String topic = "fieldview/" + config.site + "/" + config.display + "/command";
        worker.execute(() -> {
            try {
                MqttAsyncClient next = new MqttAsyncClient(config.broker, config.clientId, new MemoryPersistence());
                client = next;
                next.setCallback(new MqttCallbackExtended() {
                    @Override public void connectComplete(boolean reconnect, String serverURI) {
                        if (stopped) return;
                        try { next.subscribe(topic, 1); listener.status("Home Assistant control connected"); }
                        catch (Exception ignored) { listener.status("Home Assistant subscription failed"); }
                    }
                    @Override public void connectionLost(Throwable cause) { if (!stopped) listener.status("Home Assistant control reconnecting"); }
                    @Override public void messageArrived(String incoming, MqttMessage message) {
                        if (!stopped && topic.equals(incoming) && message.getPayload().length <= 8192)
                            listener.message(new String(message.getPayload(), StandardCharsets.UTF_8), message.isRetained());
                    }
                    @Override public void deliveryComplete(IMqttDeliveryToken token) { }
                });
                MqttConnectOptions options = new MqttConnectOptions();
                options.setAutomaticReconnect(true); options.setCleanSession(true);
                options.setConnectionTimeout(8); options.setKeepAliveInterval(30);
                options.setHttpsHostnameVerificationEnabled(true);
                if (!config.brokerUser.isEmpty()) options.setUserName(config.brokerUser);
                if (!config.brokerPassword.isEmpty()) options.setPassword(config.brokerPassword.toCharArray());
                if (!stopped) next.connect(options).waitForCompletion(10_000);
            } catch (Exception ignored) { if (!stopped) listener.status("Home Assistant control unavailable; reopen settings to retry"); }
        });
    }

    public void stop() {
        stopped = true;
        worker.execute(() -> {
            MqttAsyncClient old = client;
            if (old != null) {
                try { if (old.isConnected()) old.disconnectForcibly(100, 300); } catch (Exception ignored) { }
                try { old.close(true); } catch (Exception ignored) { }
            }
        });
        worker.shutdown();
    }
}
