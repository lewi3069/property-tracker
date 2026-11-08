# Property Tracker TV 0.2.2

TV update, version code 23, package `com.fieldview.tv`, minimum API 22, target API 35. AFTKM now uses a four-camera simultaneous playback budget after a reported vendor graphics-service crash. All saved cameras remain available through View.

Validation: 88 TV/core tests passed; lint reported zero errors and 29 warnings. Installed APK SHA-256 matched the distribution, and encrypted settings were unchanged during installation. Startup intentionally reduces an over-budget saved layout before opening streams. Signature certificate SHA-256: `c453e50d5489f8c6a9fe40355d0107db44a13d30d35a6ed16df093ac775e93a9`.

- `property-tracker-0.2.2-debug.apk`: 6,740,379 bytes; SHA-256 `b022445d5443a75fce8673b5b31bc86bde8872c53f7b5addcefb45272c02e4c5`.
- Current downloadable phone APK remains 0.2.0. The prepared 0.2.1 Google Play AAB is unchanged; Play work remains paused.

See [incident evidence and validation limits](../docs/INCIDENT-2026-11-08.md). This is a conservative mitigation; the vendor driver is unchanged. Prior HEVC/Plex freezes and unbounded RTSP/native memory remain separate limitations.
