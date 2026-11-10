# Adaptive motion layout (0.2.8)

Enable **Layout → Auto: show 8 cameras during motion, then return to 13** on an eligible native 13-camera TV profile. The preference defaults off on existing installations.

- With no motion, Auto shows the normal thirteen-camera layout, using its usual location/default-center policy.
- While any motion event is active, Auto shows eight cameras: one large active view and seven smaller views. Other active cameras take priority among the smaller views, followed by the default camera and saved catalog order.
- When the last motion event clears or expires, the full thirteen-camera overview returns automatically. Overlapping events and refreshed event expirations keep the eight-camera view open until all motion is finished.

The large view occupies three quarters of the screen width and height: 1440 × 810 in a 1920 × 1080 layout. Seven 480 × 270 thumbnails fill the right edge and bottom. Each slot is 16:9, with no overlapping or unused slots in fullscreen. The normal thirteen-camera center remains 960 × 540.

With **Use the main stream for enlarged cameras** enabled, only the large motion view requests its hardware-decoded main stream; the seven surrounding views use native grid streams. The separate **Experimental high-resolution center (13 cameras)** preference can stay off, keeping all thirteen overview feeds on grid streams. Turning the main-stream preference off still permits the eight-camera motion layout using grid streams throughout. Decoder errors, end-of-stream, and watchdog failures retain the existing native-grid fallback and five-minute per-camera cooldown.

Five hidden players are retired before starting the main stream. Returning to thirteen releases the main and restores grid players through the existing admission and native-retirement barriers. Players that remain visible are reused. The saved thirteen-camera count, camera order, credentials, and MQTT identities are preserved. Grid mode and pinned fullscreen views override automatic changes. Other saved/temporary counts, fewer than thirteen enabled cameras, unsupported TV profiles, phones, and horse-location-only events do not activate this policy.

This reduces the number of concurrent decoders during activity; it does not prove the buffering, RTSP stream-ending, or long-term native-memory issues are fixed. No camera encoders, network settings, brokers, or Home Assistant rules are changed.

## Validation

Host checks passed: 132 TV/core and 14 phone tests (146 total), both APK builds, lint with zero errors, package/SDK/native-library/signature guards, and eight Python tests. Regression cases cover overlapping motion, expiration refresh, last-clear restoration, location-only events, manual modes, ordinary eight-camera requests, fallback, player retirement/reuse, and the eight-slot geometry.

Version 0.2.8 was installed in place on Kevin Office Cams, with the existing encrypted files byte-identical immediately after installation. The adaptive option is enabled on that test TV; the separate thirteen-camera high-resolution option remains off. Hardware observation is in progress. No public APK or wider TV rollout has been performed for this version.
