# Property Tracker 0.1.13 native APK

- Application label: Property Tracker
- Application ID: `com.fieldview.tv`; version code: 14
- Minimum Android API: 23; target API: 35
- APK: `property-tracker-0.1.13-debug.apk`
- Same signing certificate as earlier Property Tracker / Fieldview versions; saved configuration is preserved on update
- 61 unit/Robolectric tests passed; Android lint zero errors / 13 warnings
- New property-and-camera launcher artwork with native legacy and adaptive icons
- Matching TV banner with the Property Tracker wordmark, exported at all five densities
- Monochrome foreground for supported launchers; the notification retains its separate camera glyph
- Editable SVG masters, PNG exports, and a Windows regeneration script under `assets/branding` and `scripts`
- Public source excludes operator inventory, credentials, signing keys, tool caches, and diagnostic captures

This is an artwork and packaging update. Playback logic is unchanged from 0.1.12, which completed one 45-minute software-H.264 camera / hardware-H.264 Plex comparison on AFTMM. See [the test scope](../docs/testing.md). Version 0.1.13 was built, linted, and signature/package-verified on the host; it was not installed or live-playback-tested during the artwork update.

APK SHA-256:

`640e2edd45432fd1aac8d13d1e29900d6c077ae7bcab33196dae10f03395f835`

Signing certificate SHA-256:

`c453e50d5489f8c6a9fe40355d0107db44a13d30d35a6ed16df093ac775e93a9`
