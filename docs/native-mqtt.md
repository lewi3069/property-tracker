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

### Motion clear and floating views (0.1.19+)

```json
{"type":"activity_end","camera_id":"barn","event_id":"unique-clear-id","expires_at":"2026-11-02T15:30:30Z"}
```

`activity_end` clears that camera immediately. It uses the same enabled-camera, unique-event, non-retained, and fresh-expiry checks as activity. Prefer this distinct type for compatibility: older APKs ignore it. Although 0.1.19 also accepts `"active":false` on an activity message, older versions interpret that message as ordinary motion.

An already running floating session subscribes to the same command topic as the main viewer, with a separate MQTT client ID. The camera chosen with **Float** is its session default. Activity on another enabled camera temporarily replaces its video. While that camera still has motion, other detections wait; once it clears, the newest remaining active camera is shown, or the default if none remain. Motion on the default camera does not block another active camera. All switches use grid streams and reuse the existing floating window and SurfaceView, releasing the old player before opening the next.

Send a fresh activity message every ten seconds while the sensor remains on, with expiry thirty seconds ahead. Send `activity_end` when it turns off. This keeps long activity alive, returns on clear, and falls back to the default within roughly thirty seconds if messages stop. Every refresh/clear needs a new event ID. If several sensors represent one camera, combine them into a single effective motion state in Home Assistant before publishing.

Home Assistant can select a new session default or close the active floating window:

```json
{"type":"floating","action":"select","camera_id":"barn","event_id":"unique-selection-id","expires_at":"2026-11-02T15:30:30Z"}
{"type":"floating","action":"close","event_id":"unique-close-id","expires_at":"2026-11-02T15:30:30Z"}
```

Both require fresh expiry and a unique event ID. Select clears current motion overrides and changes the session default; it does not rewrite the saved camera catalog. Its expiry is a delivery deadline, not a duration for the new default. Later fresh motion can override it again. Android redelivery restores the chosen default after process loss, with the existing awake/permission/enabled-camera checks. These commands do not open a stopped floating service. Returning to Property Tracker, screen-off, task removal, or close ends the session and its MQTT listener. No boot/wake listener is added. Layout and horse-location messages are ignored by Float; floating-select/close messages are ignored by the main viewer.

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

Replace the placeholder entity ID, topic, and camera ID. This example refreshes continuing motion and clears it on no motion. The example itself is not installed automatically.

```yaml
alias: Property Tracker - barn motion
triggers:
  - trigger: state
    entity_id: binary_sensor.REPLACE_WITH_BARN_ACTIVITY
    to: "on"
    id: changed
  - trigger: state
    entity_id: binary_sensor.REPLACE_WITH_BARN_ACTIVITY
    to: "off"
    id: changed
  - trigger: time_pattern
    seconds: "/10"
    id: refresh
conditions:
  - condition: template
    value_template: >-
      {{ is_state('binary_sensor.REPLACE_WITH_BARN_ACTIVITY', 'on')
         or (trigger.id == 'changed'
             and is_state('binary_sensor.REPLACE_WITH_BARN_ACTIVITY', 'off')) }}
actions:
  - action: mqtt.publish
    data:
      topic: fieldview/home/office/command
      qos: 1
      retain: false
      payload: >-
        {{ {
          "type": "activity" if is_state('binary_sensor.REPLACE_WITH_BARN_ACTIVITY', 'on') else "activity_end",
          "camera_id": "barn",
          "event_id": "barn-" ~ now().isoformat(),
          "expires_at": ((as_timestamp(now()) + 30) * 1000) | int
        } | to_json }}
mode: queued
max: 10
```

For horse tracking, normalize the tracker output to a camera zone in Home Assistant, then publish `location` commands on state changes and periodically while the location is still known. ESPresense node placement and outdoor zone reliability remain a separate field test.

See [Home Assistant MQTT publishing](https://www.home-assistant.io/integrations/mqtt/#action-mqttpublish) for the publish action.

## Deployment validation

On November 5, 2026, Home Assistant's existing MQTT integration was moved to its local Mosquitto app, with a SolarAssistant bridge preserving solar discovery and state traffic. The existing integration and entity identities were retained. All 216 active MQTT entities were available with known states after the migration.

A dedicated viewer login was saved in the native app on an AFTMM Fire TV Stick. Home Assistant published a non-retained QoS 1 layout command requesting one camera for twenty seconds. The viewer showed one camera with a temporary-layout indicator, then returned to its saved two-camera layout and remained connected. This verifies the command path and layout expiry; no camera-motion or horse-location automation was deployed.

The deployed broker uses the stock Mosquitto app's authenticated permissions. The viewer account is separate, but it is not restricted to a single topic by a broker ACL. The app itself subscribes only to its configured command topic. This deployment uses LAN TCP; TLS and topic-restricted permissions have not been validated. In version 0.1.18, MQTT stopped when the main Activity left the foreground, including when Float started. Version 0.1.19 gives an explicitly started floating session its own listener.


The two additional API 22/API 25 installations were subsequently connected to the same broker with distinct client and screen IDs. Home Assistant commands were tested against the real main Activities: one screen changed 1→2→1 and the other 2→1→2, using non-retained QoS 1 messages with fifteen-second expiry. The other screen's layout remained unchanged during each test. Camera settings and each screen's saved count were preserved. No broker restart was needed, and all 216 active solar MQTT entities remained available with known states.

A sleeping screen authenticated and then disconnected when its Activity stopped. Waking that Firestick allowed the real-viewer command test; it was returned to sleep afterward. Saved connection settings do not make the app a background listener or wake the TV automatically.

## Connection recovery in0.2.0

Connected status now waits for a successful subscription acknowledgement and accepted QoS. Initial failures and later losses retry with2/4/8/16/30-second backoff; connect/subscription callbacks have12/8-second deadlines. Closing a viewer stops retries and rejects late callbacks. Each UI inbox holds at most64 messages and processes8 per turn; overflow drops oldest pending commands, with command expiry retaining its fallback role. The command topic and payload contract are unchanged. The phone target starts with its own `phone` screen ID; configure a unique identity when connecting more than one phone.

## Dense TV layouts

Version 0.2.5 accepts layout requests up to 13 cameras, bounded by the receiving installation's effective playback profile. A remote request does not enable the experimental native engine; enable that locally in Layout first. At 13 cameras, Auto selects one detected camera for the large center position and restores the configured center after activity clears/expires and the existing hold interval. Grid and Pin remain manual overrides. Add each screen's command topic to the same activity fan-out to control more screens; each screen needs a unique screen/client identity and a matching camera-ID catalog.
