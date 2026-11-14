# Panoramic single-motion layout (0.2.13)

A single enlarged camera in the automatic motion wall uses one full-width panoramic view above four thumbnails when the measured video aspect ratio exceeds 2.2:1. This includes the deployed Duo Floodlight PoE streams at 8:3. Camera names and IP addresses are not used for detection. No camera settings or credentials change.

At 1920x1080, an 8:3 panoramic image gets a 1920x720 top tile. Four 480x360 tiles fill the remaining row; their ordinary 16:9 images retain padding rather than cropping or stretching. The top view is larger than the previous 1440-wide tile. Dimensions are learned from playback and cached for the current viewer session.

Multiple-motion layouts, the thirteen-camera overview, manual pinning, phone and floating views keep their existing policy. The single-main-stream limit, unsupported-format guards, fallback and quiet timing remain active. This layout does not enable an unsupported high-resolution stream or fix native-memory retention.
