# Native stream-size detection (0.2.15)

A live Shield check found that 0.2.14 could display native grid video while reporting its format as 0x0. Consequently, the panoramic layout rules did not recognize those cameras, despite passing layout tests with valid dimensions.

The pinned LibVLC API caches the first media-track list. The app polled that API during RTSP startup, before pictures were displayed, which could retain an empty track list for the session. Version 0.2.15 defers the lookup until the displayed-picture counter is positive. Existing polling, rendering, stream selection and resource limits are retained; no extra camera connection or encoder change is needed. Diagnostics now include video dimensions and report valid size changes without credentials or stream URLs.

After the update on Wren Livingroom Shield, the native streams for Horse Cam-South and Horse Cam-North reported 1536x576, with advancing decoded/displayed counters. The capture also recorded ordinary hardware-main frames and no failure/stall/fallback events. This validates real stream-size reporting, rather than only simulated metadata. It does not establish long-term stability or resolve the native-memory investigation.

All 180 Android tests, both APK builds, lint with zero errors, and package/SDK/native-library/signing guards passed. The existing layout/player tests remain useful but do not replace the real-device startup check above. The Shield update preserved its encrypted settings and reopened its already-active viewer. Other TVs were not changed in this follow-up.
