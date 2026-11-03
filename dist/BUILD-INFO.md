# Property Tracker 0.1.14 native APK

- Application ID: `com.fieldview.tv`; label: Property Tracker; version code: 15
- Minimum Android API: 23; target API: 35
- APK: `property-tracker-0.1.14-debug.apk`
- Maintained signing certificate; camera settings are preserved on update
- PNG launcher icons at five densities, replacing runtime XML/adaptive alternatives
- Explicit launcher activity artwork and label; matching TV banners and editable SVG masters
- 61 tests passed; lint zero errors / 19 warnings, including five square-TV-icon shape warnings and one redundant-label warning
- Installed APK digest verified; encrypted camera settings remained byte-for-byte unchanged
- New artwork visually confirmed in Home and Your Apps & Channels on AFTMM / API 25 after Fire TV catalog refresh
- Runtime playback Java unchanged from 0.1.12; no new sustained playback comparison

Fire TV retained stale catalog thumbnails after an in-place update. The installed-app library showed the current artwork before Home/app-grid entries refreshed. See [the completed icon checks and refresh scope](../docs/testing.md#version-0114-fire-tv-icon-compatibility).

APK SHA-256:

`f9d1c61d4ccb722cf710e9ff4807b6cd9c885ed67de502f74c9f6bbc38c9c171`

Signing certificate SHA-256:

`c453e50d5489f8c6a9fe40355d0107db44a13d30d35a6ed16df093ac775e93a9`
