# Main-stream compatibility (0.2.9)

The user reported **Main unavailable** on a panoramic camera during the adaptive motion view. Read-only checks found a Reolink Duo Floodlight PoE sending a 4608 × 1728 HEVC main stream at 20 fps. The test Firestick's vendor codec configuration advertises a maximum HEVC hardware-decoder size of 3840 × 2176. The panoramic stream exceeds the advertised width even though its overall pixel count is similar to ordinary 4K. Reducing the layout to eight cameras does not change that decoder-size limit.

The camera's grid stream is H.264 at 1536 × 576 and remains playable. Its encoding API currently offers only 4608 × 1728 or 4096 × 1536 main-stream sizes, both wider than this Firestick advertises. No camera or recording settings were changed. The recent log ring no longer contained the original North failure, so these measurements establish a compatibility problem rather than the exact exception from the user's earlier observation.

For the enlarged hardware-only center, version 0.2.9 stops Media3 from selecting tracks that exceed renderer capabilities. It also applies the measured HEVC width limit specifically to Amazon AFTKM devices on API 30: streams wider than 3840 pixels are rejected at track inspection and again at decoder lookup, covering dimensions learned after initial track mapping or overly permissive capability reports. The renderer-time check refuses the format before codec lookup/creation. Other models, API levels, codecs, and phone playback do not inherit this device-specific width rule.

An unsupported main stream returns to the native grid path with **Main format unsupported on this TV · grid stream**. The specific reason survives motion changes during the existing five-minute cooldown. Unknown/empty video tracks do not cause early rejection; supported video continues normally. Generic connection/end-of-stream failures retain their existing message and recovery behavior. Ordinary fullscreen, floating-video decoder preferences, and 13→8→13 selection behavior are unchanged.

This improves rejection and explanation of unsupported formats. It does not make the 4608-pixel main stream hardware-decodable or provide a higher-resolution replacement. A separate TV-compatible downscaled feed could preserve the camera's full-resolution recording stream; server availability, capacity, and configuration would need review before implementing that approach. Full-resolution software decoding alongside seven other cameras has not been validated.

## Validation

Host checks passed with 140 TV/core and 14 phone tests (154 total), both builds, zero lint errors, and package/SDK/native-library/signature guards. Cases cover strict enlarged-track selection, supported 4K, empty/audio-only tracks, stale callbacks, explicit fallback text, cooldown reason retention/reset, ordinary-playback policy, and restoration of thirteen cameras after unsupported main fallback. Renderer tests exercise the exact model/API scope, late format errors, and refusal of an oversized format before any codec lookup.

The first track-capability-only candidate still allowed a main stream to reach a failing hardware decoder in device observation. The follow-up therefore adds the direct width rule at decoder lookup as well as track inspection. Supported 3840 × 2160 streams continued to render in the first candidate; final follow-up device checks are in progress.

Version 0.2.9 was installed in place on Kevin Office Cams, with its two encrypted settings files byte-identical during installation. The existing adaptive motion option, thirteen-camera overview, default camera, and identities are preserved. Device verification is in progress. No public APK or wider TV rollout has been performed for this version.
