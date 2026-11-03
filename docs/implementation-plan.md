# Property Tracker implementation and remaining work

## Product behavior

The project now builds a native Android APK with an Android Activity, native TV controls, Media3 players, encrypted local configuration, camera editing, per-screen tile count, fullscreen selection, retry handling, and optional MQTT command input. Its interface and playback do not use a WebView or browser server. Playback and remote behavior have been exercised on an AFTMM Fire TV Stick; other hardware and MQTT deployment behavior still need validation. See [test results](testing.md).

Camera count is dynamic and specific to each installation. The native layout menu offers 1, 2, 4, 6, and 9 tiles, with AFTMM/low-RAM devices limited to four; the camera catalog is independent of this count. The View picker can select a configured camera outside the current grid. Native MQTT layout commands accept any integer from 1 to 9. The earlier browser experiment is archived under `design-reference/browser-prototype` and is not packaged in the APK.

The native app persists site/display identity, camera catalog and default order, preferred tile count, and enlarged-stream preference. The Cameras menu supports prefilled copy drafts and Move up/down actions; custom groups remain future work. A copy receives a unique name/ID and is not added until Save. Home Assistant can request a temporary tile count, which expires back to the saved count. Activity outside the overview can bring that camera into the visible set. A one-camera layout stays at one camera; fullscreen pinning does not overwrite the saved count.

Activity should enlarge the relevant feed, with two large feeds when activity matters in two places. Manual pinning has precedence until explicitly released. If several horses are in different zones, select the useful cameras for those zones rather than assuming all horses move together. When activity expires, return to the current horse view or overview. Location changes need a stability interval; lost locations must not be interpreted as a known zone.

## Streaming path

```text
Reolink / Amcrest cameras or NVR -- local video --> Fire TV native players
Camera activity sensors ---------> Home Assistant
Horse BLE tags -> ESPresense ----> Home Assistant
Home Assistant -- view commands through local MQTT --> Fire TV view controller
```

Video does not have to pass through Home Assistant. Direct local streams keep the initial system small; a restreamer can be added if stream connection limits or format conversion make it useful. Do not assume a restreamer increases the number of streams the Stick can decode.

The shared native codebase uses Java and Media3 player surfaces for Android-based Fire OS and NVIDIA Shield's Android TV. It has TV launcher declarations, makes touchscreen hardware optional, and does not require Google Play Services. The APK's minimum Android API is 23. Amazon documents Android media player support on Fire OS, and NVIDIA documents Android TV development for Shield. An APK has been compiled; device-specific compatibility still needs testing.

Media3's documented RTSP video format is H.264; H.265 RTSP must not be assumed to work just because a device's hardware decodes HEVC. Check the actual camera output first. H.265-only high-resolution streams may require a different player path or server-side conversion. Audio is muted initially; supported audio formats also need validation.

Use lower-bitrate H.264 substreams in the grid. Promote a selected feed to a higher-resolution compatible stream, releasing its previous decoder rather than keeping both. A larger video surface can appear immediately, but reconnecting to another stream can introduce a visible delay. Measure that before promising seamless quality changes.

Treat the user's desired layout and the device's tested live-stream budget as separate values. Screen size alone does not determine safe decoder concurrency. The native app releases hidden players and uses only the chosen visible set, but adaptive decoder-budget detection, pages, and snapshot fallback are not implemented yet. Start conservatively on both Shield and Fire TV and increase the count only after sustained playback tests. Any future snapshot fallback must be explicitly labeled.

Release video and network resources when the app leaves the foreground. Recover individually from camera failures. Test thermal behavior and reconnects over hours, not just initial playback. Camera recording remains with the existing camera/NVR system.

References: [Amazon media players](https://developer.amazon.com/docs/fire-tv/media-players.html), [Media3 RTSP formats and transport](https://developer.android.com/media/media3/exoplayer/rtsp), [Amazon device identification and OS table](https://developer.amazon.com/docs/device-specs/identify-fire-tv-devices.html), [NVIDIA Shield development](https://developer.nvidia.com/develop4shield), [Android TV app setup](https://developer.android.com/training/tv/get-started/create).

## Home Assistant control

The native app implements optional MQTT command input with a foreground connection lifecycle. It does not need a Home Assistant API token. MQTT connectivity on the user's network and actual Home Assistant automation delivery remain untested. The connection is configured using the native Home Assistant menu; no automation has been installed in either site.

Deployments can include multiple independent Home Assistant sites. Viewer configuration and command topics use explicit site/display IDs so an automation affects the intended screen. Configure each site's camera integrations, entity IDs, and MQTT broker privately.

Home Assistant should normalize camera motion/person/animal events and tracking data into stable camera IDs and zone IDs. Reolink and Amcrest already have Home Assistant integrations, with available detections depending on camera model and firmware. Do not require raw pixel-motion triggers if better detections are available.

Implemented app event formats:

```json
{"type":"activity","camera_id":"barn","event_id":"unique-event-id","expires_at":"2026-11-02T15:30:30Z"}
```

```json
{"type":"location","horse_id":"horse-1","zone":"pasture","observed_at":"2026-11-02T15:30:00Z"}
```

The command router validates IDs, payload size, timestamps, expiry, and tile counts. It deduplicates activity, rejects retained messages, and drops stale/out-of-order locations. Temporary layout requests restore the local count on expiry. See [the native MQTT contract](native-mqtt.md) for topics, the layout command, and an example Home Assistant automation.

Use a dedicated MQTT account with topic permissions, authenticated connections, and TLS where configured. Store camera/broker credentials privately on the device and exclude them from logs, source control, screenshots, and backup. Commands use camera IDs, not credential-bearing stream URLs. No internet-facing camera ports are required.

Automatic changes initially operate while the viewer is open. Launching the app over other TV content, waking the display, and HDMI-CEC power behavior are separate features that need device testing. Home Assistant's Android TV Remote integration specifically does not support Fire TV; MQTT view control must not rely on it. ADB-based launching, if wanted later, is a separate integration choice.

References: [Home Assistant Reolink](https://www.home-assistant.io/integrations/reolink/), [Home Assistant Amcrest](https://www.home-assistant.io/integrations/amcrest/), [Home Assistant MQTT](https://www.home-assistant.io/integrations/mqtt/), [Android TV Remote limitations](https://www.home-assistant.io/integrations/androidtv_remote/).

## Horse tracking

ESPresense reports Bluetooth-tag proximity/distance observations; Home Assistant can turn these into zones. A zone-to-camera mapping then drives the same layout controller. Start with broad zones such as barn, yard, and pasture. Do not assume GPS-like tracking or coverage of an entire pasture: ESPresense Companion is documented for indoor positioning, so outdoor range, node placement, tag orientation, and zone reliability require a field trial.

Tracking should remain replaceable. A future GPS or other tracker can supply the same horse/zone event without changing the viewer. The prototype simulates two horses and four zones solely to test behavior.

References: [ESPresense overview](https://espresense.com/), [ESPresense Companion](https://espresense.com/companion/), [Home Assistant MQTT room presence](https://www.home-assistant.io/integrations/mqtt_room/).

## Inputs still needed

- Camera catalog and models. Simultaneous view count is a per-screen preference and does not need one global answer.
- Whether streams come directly from cameras or an NVR, and which H.264 streams are available.
- Camera entity IDs and activity sensors in the existing Home Assistant installations; whether a local MQTT broker is installed.
- Actual camera names and coverage zones; desired behavior when several cameras have activity.
- Generation of the generic older Fire TV Stick if it needs support.
- NVIDIA Shield model and OS version before on-device playback testing.

Do not put real camera credentials into this planning document. Configure them privately when the native setup flow is implemented.
