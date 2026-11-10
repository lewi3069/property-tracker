# Calmer automatic motion layout — 0.2.10

Short motion/off bursts previously changed the enlarged camera immediately when its detection cleared, or returned the eight-camera event layout to thirteen as soon as the last detection cleared. Repeated bursts could therefore cycle players and layouts frequently.

For the existing optional automatic thirteen-to-eight TV layout:

- The first detection still opens the eight-camera event view immediately.
- The enlarged camera is held for at least ten seconds before another detection can replace it. Other active cameras are included among the seven smaller views within the eight-camera budget.
- The event layout and its enlarged camera remain for fifteen continuous seconds without motion before returning to the thirteen-camera overview and its original default camera.
- New motion during that quiet period cancels the pending return. The next return is measured from the end of the new activity. Repeated off messages do not extend the timer.
- Expired events use their actual expiry time, so resuming after an extended absence does not invent a fresh fifteen-second hold.

The timing applies automatically when the existing motion-layout option is enabled on an eligible TV profile. Manual Grid/Pin controls override immediately. Other layout counts, phones, floating video and horse-location-only selection keep their previous policy. No new settings or identities are added, and the saved camera count remains thirteen. Main-stream compatibility and fallback retain the 0.2.9 behavior.

This reduces short-interval view changes; it is not a fix or stability claim for Fire OS native-memory retention. Camera encoders, recording streams and Home Assistant automation rules are unchanged.

## Validation

Host verification passed: 147 TV/core tests and 14 phone tests (161 total), both APK builds and lint with zero errors, package/SDK/native-library/signing guards, and eight Python checks. Timing coverage includes exact dwell/quiet boundaries, overlapping detections, heartbeats, new activity during the quiet period, duplicate off messages, actual event expiry after backgrounding, manual overrides and catalog changes. An integration test verifies the held viewer reuses its players and restores the default thirteen-camera layout afterward; stream-policy coverage keeps the single main stream during the hold.

[GitHub verification passed](https://github.com/lewi3069/property-tracker/actions/runs/42918173892), including the default and opt-in native-surface package guards.

Version 0.2.10 was installed in place on Kevin Office Cams; the existing encrypted settings files were byte-identical immediately after installation. Two short passive log captures retained the same app process, recorded real automatic eight/thirteen-camera transitions and 3840 × 2160 hardware HEVC first frames, and contained no failure/stall/fallback events. A passive UI audit verified the installed APK and a thirteen-camera fullscreen overview with no overlap or unused area. Changing feeds within these short windows prevented a complete all-camera progress comparison; they do not establish a soak result or a memory fix. The user subsequently confirmed that switching felt calmer on the physical TV. This is point-in-time preference feedback, not sustained reliability evidence.

Other TVs were not updated. No public 0.2.10 APK has been released; the downloadable TV release remains 0.2.5.
