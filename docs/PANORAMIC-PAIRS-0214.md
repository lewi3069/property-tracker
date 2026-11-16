# Two active panoramic cameras (0.2.14)

The automatic motion wall now uses both enlarged feeds' measured video dimensions:

| Enlarged pair | Layout | Total streams |
| --- | --- | --- |
| Two panoramic feeds | Stacked wide views on the left; four thumbnails on the right | 6 |
| Panoramic and regular feed, in either featured order | Wide view above the regular view; six thumbnails in the remaining right-hand areas | 8 |
| Two regular feeds | Existing two large views plus eight thumbnails | 10 |

For the deployed 8:3 Duo and 16:9 feeds on a 1920x1080 screen, each Duo uses 1440x540. A regular active camera uses 960x540, and thumbnails use 480x270. The two-Duo arrangement uses four thumbnails; the mixed arrangement uses two thumbnails beside the Duo and four beside the regular camera. Enlarged pictures fit without cropping or stretching. Other measured ratios adjust the shared boundaries; extreme ratios retain bounded tile sizes and may still require padding.

The featured camera order is preserved even when the first enlarged camera is the regular feed positioned below the Duo. This retains the single hardware-main-stream limit and reuses unchanged players. Other enlarged feeds continue using grid streams; layout optimization does not unlock unsupported main formats. Decoder guards, fallback, motion dwell, count-change hysteresis, quiet return and the thirteen-camera overview retain their policy. Aspect classification is learned during playback; unknown dimensions initially use the normal arrangement. No new configuration or credentials are required. The existing automatic multi-motion option and eligible thirteen-camera TV profile must already be enabled.

Phone layouts, manual pins and floating video are unchanged. This is not a native-memory stability fix or a multiple-main-stream capacity claim.

## Host validation

166 TV/core and 14 phone tests passed (180 total), both APK builds and lint passed with zero errors, and package/SDK/native-library/signature guards passed. Tests cover both panoramic/regular featured orders, measured ratios and full coverage without overlap, metadata arriving during playback, unchanged active-player reuse, duplicate-metadata stability, the single-main-stream limit and fallback, manual modes and quiet restoration to thirteen.

[GitHub verification passed](https://github.com/lewi3069/property-tracker/actions/runs/43891777421), including the default and opt-in native-surface package guards.

## Wren Livingroom Shield

The existing NVIDIA Shield installation was checked on November 16, 2026: it had the exact tested 0.2.13 APK. It was updated in place to 0.2.14/code 35. The installed APK matches the verified build, and the encrypted preferences remained byte-identical, preserving cameras, credentials, MQTT identity and viewing profile. It remained asleep and was not launched, so this verifies installation rather than live pictures. Other TVs were not changed in this update.
