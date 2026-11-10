# Simultaneous-motion layouts — 0.2.11

On a TV already using the optional native thirteen-camera motion layout, **Layout → Auto: enlarge multiple active cameras** enables these automatic arrangements:

| Cameras with motion | Layout | Concurrent streams |
| --- | --- | --- |
| None, after the quiet period | Original thirteen-camera overview | 13 |
| One | One large view and seven thumbnails | 8 |
| Two | Two large views across the top and eight thumbnails below | 10 |
| Three | Three large quadrants and four thumbnails in the remaining quadrant | 7 |
| Four or more | Four large views filling the screen | 4 |

At 1920 × 1080, each large view in the two/three/four layouts is 960 × 540; thumbnails are 480 × 270. All arrangements fill the display without overlapping tiles and preserve video aspect ratios.

The first motion response remains immediate. Additional motion must persist at its higher count for two seconds before the layout expands. A lower nonzero count must persist for fifteen seconds before reducing the number of large views. When all motion clears, the existing fifteen-second quiet period restores the original overview and default camera. Brief bursts do not continually resize the wall. Existing featured cameras keep their slots where possible, with a ten-second minimum hold before a newer detection can replace a camera; at most four active cameras are enlarged. During the hold, recently cleared cameras can remain enlarged while other current detections also appear among the smaller views.

Only the first enlarged camera may use its hardware-decoded main stream. The other enlarged cameras retain their native grid streams in this first implementation. This keeps the existing limit of one simultaneous main decoder; it does not promise equivalent detail in every enlarged view. Per-camera unsupported-main rejection and grid fallback remain active. Multiple simultaneous high-resolution streams require separate device validation.

The new preference defaults off, retaining the previous one-large-view motion behavior until enabled. It requires the existing automatic motion-layout option, thirteen saved visible cameras and an eligible native TV profile. Manual Grid/Pin overrides immediately. Other layout counts, phones and floating video are unchanged. The saved catalog, camera identities, default camera, thirteen-camera base count and MQTT settings are retained. No camera encoders, recording streams, broker or Home Assistant rules change.

Fewer concurrent streams during four-camera activity does not establish a memory fix or sustained stability. No public APK is released by this source update.

## Host validation

158 TV/core tests and 14 phone tests passed (172 total), both APKs built, and lint reported zero errors. Package/SDK/native-library/signing guards and eight Python checks passed. The geometry/player integration test exercises thirteen → eight → ten → seven → four → thirteen, checks all rectangles for full coverage and no overlap, verifies exactly one main decoder, retains unchanged players and releases hidden players. Policy coverage checks expansion/reduction delays, stable slot positions and minimum dwell, quiet-gap cancellation, expired events, manual/catalog overrides and opt-in guards. Existing single-motion timing and unsupported-main regression tests still pass.
