# Native MQTT control

The APK implements optional MQTT command input through a local broker. Set the broker URI, account, site ID, and screen ID using the native **Home Assistant** button. Supported broker transports are `tcp://host:1883` and `ssl://host:8883`; TLS uses the device's normal certificate validation.

For site `home` and screen `office`, publish to:

```text
fieldview/home/office/command
```

Use a dedicated broker account allowed to subscribe only to the needed command topic. Configure a different screen ID for each installation. The first setup should test streams and broker access within one site; routing between separate networks must be validated for each deployment.

## Commands

Messages must be JSON, at most 8192 bytes, and **not retained**. Timestamps can be UTC ISO 8601 strings or integer Unix epoch milliseconds. The broker/device/Home Assistant clocks should agree.

### Camera activity

```json
{"type":"activity","camera_id":"barn","event_id":"unique-event-id","expires_at":"2026-11-02T15:30:30Z"}
```

`camera_id` must match an enabled native camera ID. Disabled cameras are excluded from activity and location selection until enabled again in the native app. `event_id` must be unique for each detection. Expiry must be in the future and at most two minutes away. The view can enlarge up to two active cameras, subject to the screen's tile count and an eight-second minimum dwell between changes of equal priority. Activity immediately interrupts a horse-location view. A pinned/manual view stays in control.

### Horse location

```json
{"type":"location","horse_id":"horse-1","zone":"pasture","observed_at":"2026-11-02T15:30:00Z"}
```

The zone matches the **Location zone** entered on one or more cameras. Repeated reports for the same zone keep the existing stability timer. A location must remain stable for five seconds; reports become stale after sixty seconds. Reject observations that are out of order, too old, or more than five seconds in the future. Send periodic fresh observations while the tracker is present. An unknown/unmapped zone clears that horse's known location.

### Temporary screen layout

```json
{"type":"layout","max_visible":6,"expires_at":"2026-11-02T15:31:00Z"}
```

`max_visible` is an integer from 1 to 9, clamped to this installation's playback limit. The AFTMM Fire TV Stick 4K and Android low-RAM devices allow up to four simultaneous feeds, including when a command requests six or nine. This changes the tile budget until expiry, then restores the count saved on this device. The camera catalog remains available. Manual fullscreen pinning still shows one camera. Saving layout settings locally clears the temporary override. Native UI presets are 1/2/4 on limited devices and 1/2/4/6/9 elsewhere, while MQTT can request any integer within 1–9 subject to the same device limit.

## Home Assistant automation example

Replace the placeholder entity ID, topic, and camera ID. This is a file example; no automation has been installed in either Home Assistant system.

```yaml
alias: Property Tracker - enlarge barn on activity
triggers:
  - trigger: state
    entity_id: binary_sensor.REPLACE_WITH_BARN_ACTIVITY
    to: "on"
actions:
  - action: mqtt.publish
    data:
      topic: fieldview/home/office/command
      qos: 1
      retain: false
      payload: >-
        {{ {
          "type": "activity",
          "camera_id": "barn",
          "event_id": "barn-" ~ now().isoformat(),
          "expires_at": ((as_timestamp(now()) + 30) * 1000) | int
        } | to_json }}
mode: queued
max: 10
```

For horse tracking, normalize the tracker output to a camera zone in Home Assistant, then publish `location` commands on state changes and periodically while the location is still known. ESPresense node placement and outdoor zone reliability remain a separate field test.

See [Home Assistant MQTT publishing](https://www.home-assistant.io/actions/mqtt.publish/) for the publish action.

## Deployment validation

On November 5, 2026, Home Assistant's existing MQTT integration was moved to its local Mosquitto app, with a SolarAssistant bridge preserving solar discovery and state traffic. The existing integration and entity identities were retained. All 216 active MQTT entities were available with known states after the migration.

A dedicated viewer login was saved in the native app on an AFTMM Fire TV Stick. Home Assistant published a non-retained QoS 1 layout command requesting one camera for twenty seconds. The viewer showed one camera with a temporary-layout indicator, then returned to its saved two-camera layout and remained connected. This verifies the command path and layout expiry; no camera-motion or horse-location automation was deployed.

The deployed broker uses the stock Mosquitto app's authenticated permissions. The viewer account is separate, but it is not restricted to a single topic by a broker ACL. The app itself subscribes only to its configured command topic. This deployment uses LAN TCP; TLS and topic-restricted permissions have not been validated. MQTT stops when the main Activity leaves the foreground, including when Float starts.
