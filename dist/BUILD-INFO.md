# Property Tracker 0.1.19 native APK

- Application ID: `com.fieldview.tv`; label: Property Tracker; version code: 20
- Minimum Android API: 22; target API: 35
- APK: `property-tracker-0.1.19-debug.apk` (6,796,497 bytes)
- Maintained signing certificate and encrypted-settings identity for in-place updates
- MQTT controls an existing floating session: motion override, motion clear, timeout fallback, select default, close
- Reuses one floating window/SurfaceView and preserves silent grid-stream/software-H.264 preferences
- 81 unit/Robolectric tests passed; lint zero errors / 27 warnings; APK v1/v2 signature verified
- Installed on three Fire TVs with encrypted settings unchanged; live MQTT and advancing H.264 video observed on AFTT/API 22 and AFTMM/API 25
- A real Home Assistant motion-on/clear cycle switched an AFTMM floating view away from and back to its selected default
- No new sustained Plex comparison or physical-TV visual confirmation; previous HEVC/Plex freezes and stale Home artwork remain known limitations
- Does not wake the TV, launch the app, or open a stopped floating session

See [validation scope and known limits](../docs/testing.md#version-0119-floating-view-mqtt-and-motion).

APK SHA-256:

`3ccc25c3743c93b32c5697c1442cd46c09553fcbbcc92a2e7e1e9791f53586e6`

Signing certificate SHA-256:

`c453e50d5489f8c6a9fe40355d0107db44a13d30d35a6ed16df093ac775e93a9`
