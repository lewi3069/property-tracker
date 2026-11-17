# Doorbell focus layout (0.2.16)

When a measured 4:3 camera such as Wren Doorbell is the single motion focus, the eligible thirteen-camera TV profile shows one large picture, six thumbnails to its right (two columns of three), and four thumbnails across the bottom. This uses eleven streams in total. Cameras are identified by their video dimensions, so renaming a camera does not affect the layout.

On a 1920x1080 wall, a 4:3 picture uses a 1080x810 tile. The right thumbnails use 420x270 tiles; the bottom four use 480x270 tiles. The focus tile matches the Doorbell picture without cropping or stretching. The right thumbnails can have small fit bars because their tile proportions differ from an ordinary 16:9 stream. Nearby measured ratios adjust the focus width while retaining full wall coverage without overlap. Unknown metadata initially uses the existing single-focus arrangement.

These dimensions scale with the app's measured drawing area rather than assuming a 1080p display. Geometry tests cover 720p, 1080p and 4K; at a 3840x2160 drawing size, the 4:3 focus is 2160x1620. A television's panel resolution does not itself determine the app's drawing size. Main/grid stream choice and device feed limits do not automatically change between 1080p and 4K displays: a supported 4K main stream may still be decoded and scaled down on a 1080p display.

The layout applies to the automatic single-motion focus with the existing motion-layout option and native thirteen-camera playback profile enabled. The thirteen-camera overview, multiple-active-camera layouts, manual Grid/Pin, phone and floating views retain their existing behavior. After the quiet period, the original overview and default center return. Only the first featured camera can use a hardware main stream; its ten neighbors use native grid streams, and an unavailable main stream falls back normally. This adds three grid streams compared with the ordinary eight-camera focus arrangement, so it is not a memory or decoder-capacity improvement.

No camera credentials, encoder settings, catalog identities, MQTT settings or motion timing are changed. No current APK is published as a public release.

All 186 host tests passed (172 TV/core and 14 phone), both debug APKs built, lint reported zero errors, and package/SDK/native-library guards passed. Tests cover exact thumbnail positions at 1080p, 720p and 4K geometry, nearby measured aspect ratios, full coverage without overlapping tiles, metadata arriving after playback begins, reuse of the main player, fallback, manual modes, catalog removal and quiet restoration to the same thirteen-camera overview. Live visual confirmation remains pending.

Wren Livingroom Shield was updated in place to 0.2.16/code 37 on November 16, 2026. The installed APK matches the verified local build and original signing certificate. Encrypted settings remained byte-identical before and after installation, preserving the camera catalog, credentials, ordering, MQTT identity and viewing profile. Netflix remained in the foreground; the viewer was not reopened. Other TVs were not updated in this change.

[GitHub verification passed for the runtime change](https://github.com/lewi3069/property-tracker/actions/runs/43939962756), including the default and opt-in native-surface package guards.
