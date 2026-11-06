# Reliability and efficiency review — 0.2.0

Reviewed playback lifecycle and retries, main/floating selection, MQTT delivery and shutdown, encrypted configuration, catalog edits, phone layout, build configuration, and the current Home Assistant command contract. This update adds a native phone target sharing the same Java playback core. Live Home Assistant rules were inspected read-only; no production automation, broker, or camera setting was changed by this review.

## Implemented

- **MQTT readiness:** report connected after successful subscription acknowledgement and accepted granted QoS. Rejected subscriptions, missing callbacks, and initial connection failures retry with bounded 2/4/8/16/30-second backoff. Connect/subscription attempts have 12/8-second deadlines. Shutdown is idempotent and closes connections on the worker thread; late callbacks cannot revive a stopped session.
- **Bounded UI handoff:** each viewer's inbox holds at most 64 pending commands and drains eight at a time, yielding to other UI work. Overflow drops the oldest pending messages; existing freshness checks and motion expiry remain the fallback. Closed sessions discard pending and late messages.
- **Player reuse:** catalog edits retain unchanged tiles, URIs, valid manual pinning, and activity/location tracking. Metadata changes update accessibility labels. Changing one URI restarts that player. Disabled/removed cameras leave the active set.
- **Transition peaks:** demote existing main-stream players before promoting newly featured streams. Hidden streams still release before new players start.
- **Polling:** main-view housekeeping runs once per second instead of four times per second. MQTT and user actions still render immediately. This reduces polling frequency by 75%; no device CPU/battery percentage improvement is claimed.
- **Phone support:** independent native phone APK, responsive touch controls and portrait placement, system-bar/cutout/keyboard insets, narrow form actions, and manual-selection restoration through recreation. TV presentation and API 22 support remain intact. Dependency versions and signing configuration are shared across targets.

## Remaining risks and validation

1. **High: RTSP/native memory is not strictly bounded.** Media3 1.6.1's TCP packet buffering and decoder/graphics allocations are outside the configured 2/8 MiB load-control targets. Conservative feed limits, substreams and the existing watchdog reduce exposure, but do not prove a hard memory limit. A maintained upstream/transport solution remains separate work; arbitrary compressed-packet dropping is unsafe. See the [earlier source-level review](CODE-REVIEW-2026-11-03.md).
2. **HEVC alongside Plex:** prior shared-decoder freezes remain unresolved. Preserve the tested software-H.264 Float preference and one-substream handover. This update has no new sustained Plex comparison.
3. **Configuration I/O:** Keystore work and synchronous encrypted saves still occur on the UI thread. No ANR was reproduced. A serialized asynchronous settings layer remains a future responsiveness improvement.
4. **Dependencies:** Media3 1.6.1 remains pinned. Current releases include RTSP fixes, but newer Media3 versions raised their minimum Android API, so a shared upgrade needs an explicit older-stick compatibility plan and live playback regression tests. See [official release notes](https://developer.android.com/jetpack/androidx/releases/media3).
5. **Device testing:** host tests are not physical Pixel/Fire TV playback tests. Pixel networking, rendering, overlay permissions, battery use, long sessions, and renewed TV/Plex coexistence need device validation. No TV was navigated, updated or stress-tested during this review.
6. **Deployment security/artwork:** existing LAN RTSP/MQTT use and broker permissions remain as documented. Secrets are excluded from APK defaults and public Git. Fire TV Home's stale artwork remains unresolved. Camera reachability remains dependent on local routing/firewall/VPN configuration.

The automation chat has expanded the live rules since the earlier three-source handoff. Current Colorado mappings cover six Amcrest cameras and the doorbell's combined Reolink person/pet/vehicle detections. Their `activity` / `activity_end`, unique event IDs, non-retained QoS 1 and expiry contract matches both app targets. Combining detection types per camera prevents one classification clearing another. This read-only review does not replace that chat's deployment approval or Git history.

Validation details and APK identities are recorded in [testing](testing.md) and [build information](../dist/BUILD-INFO.md).
