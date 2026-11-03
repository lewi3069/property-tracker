# Property Tracker 0.1.11 native APK

- Application label: Property Tracker
- Application ID: `com.fieldview.tv`; version code: 12
- Minimum Android API: 23; target API: 35
- APK: `property-tracker-0.1.11-debug.apk`
- Same signing certificate as previous Fieldview versions; saved configuration is preserved on update
- 49 unit/Robolectric tests passed; Android lint zero errors / 16 warnings
- Floating view hides the camera-name bar; grid titles and connection/error status remain available
- SurfaceView, one silent floating substream, playback limits, and stream/recovery behavior are unchanged
- Public source uses generic connection examples; operator inventory, credentials, signing keys, tool caches, and diagnostic captures are excluded

A five-minute Plex-only baseline and 15-minute Plex-plus-camera comparison completed on an AFTMM Fire TV Stick using the preceding 0.1.10 playback code. Both app processes stayed stable; the camera remained present in all 60 samples, and its frame timestamps advanced through the final sample. The operator confirmed normal pictures. See [test scope and limitations](../docs/testing.md).

APK SHA-256:

`c3d676ebf1e121c492dd296cd304872fa703d4d1a4d5c8f254cdcbac977775c2`

Signing certificate SHA-256:

`c453e50d5489f8c6a9fe40355d0107db44a13d30d35a6ed16df093ac775e93a9`
