# Property Tracker 0.1.12 native APK

- Application label: Property Tracker
- Application ID: `com.fieldview.tv`; version code: 13
- Minimum Android API: 23; target API: 35
- APK: `property-tracker-0.1.12-debug.apk`
- Same signing certificate as previous Fieldview versions; saved configuration is preserved on update
- 61 unit/Robolectric tests passed; Android lint zero errors / 14 warnings
- Floating view hides the camera-name bar; grid titles and connection/error status remain available
- SurfaceView, one silent floating substream, and conservative playback limits are preserved
- Continuing frame-submission watchdog, sustained-progress retry reset, and a three-stall recovery limit
- Optional software H.264 preference for Float; off by default, scoped to H.264 only
- Playback statistics omit camera addresses and credentials; setup-only ADB launch avoids starting overview players
- Public source uses generic connection examples; operator inventory, credentials, signing keys, tool caches, and diagnostic captures are excluded

A five-minute Plex-only baseline and 15-minute Plex-plus-camera comparison completed on an AFTMM Fire TV Stick using the preceding 0.1.10 playback code. Both app processes stayed stable; the camera remained present in all 60 samples, and its frame timestamps advanced through the final sample. The operator confirmed normal pictures. See [test scope and limitations](../docs/testing.md).

A subsequent 0.1.11 run confirmed the hidden name bar but reproduced a playback freeze while the app process stayed alive. Later inspection identified an HEVC floating camera alongside hardware H.264 Plex playback. Version 0.1.12 then completed one 45-minute comparison using software H.264 at 640 × 480 for the camera and hardware H.264 for Plex: both surfaces advanced at every interval across 181 samples, all monitored process IDs stayed stable, and no camera playback errors, stalls, or dropped frames were recorded. The operator confirmed normal pictures initially and after 35 minutes. Encoding, decoder policy, app version, and a prior platform reboot differ from earlier runs; this does not prove an isolated cause or indefinite stability. See [the complete trial and limits](../docs/testing.md#later-freeze-and-0112-compatibility-trial).

APK SHA-256:

`d530383dc69a82ccf37315eb6a6dcea377540601e5d8d36b75a6431a87aa9cbd`

Signing certificate SHA-256:

`c453e50d5489f8c6a9fe40355d0107db44a13d30d35a6ed16df093ac775e93a9`
