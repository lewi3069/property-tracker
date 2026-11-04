# Property Tracker 0.1.15 native APK

- Application ID: `com.fieldview.tv`; label: Property Tracker; version code: 16
- Minimum Android API: 23; target API: 35
- APK: `property-tracker-0.1.15-debug.apk` (6,391,627 bytes)
- Maintained signing certificate and encrypted-settings identity for in-place upgrades
- Camera-name bars removed in all views; names retained in pickers and accessibility labels
- Layout includes Full screen layout: hide controls and fill the display with the selected camera layout
- Remote focus remains visible in multi-camera layouts; pin/Back restores the prior Auto/Grid mode
- Back restores controls without restarting unchanged players; existing device feed limits retained
- 64 unit/Robolectric tests passed; lint zero errors / 19 existing warnings
- APK signature, package identity, and version verified locally
- Not installed or live-tested: operator requested that the TV's current screen be left alone

See [validation scope](../docs/testing.md#version-0115-full-screen-camera-layouts). Host tests simulate video initialization; 9-tile geometry is not a nine-camera hardware capacity test. The earlier 45-minute playback comparison belongs to 0.1.12.

APK SHA-256:

`b04df25324b7c39a82b001572cb84a9e27e82c633e08c9d9535ad1de296ed347`

Signing certificate SHA-256:

`c453e50d5489f8c6a9fe40355d0107db44a13d30d35a6ed16df093ac775e93a9`
