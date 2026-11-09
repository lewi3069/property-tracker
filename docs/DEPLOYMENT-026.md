# 0.2.6 in-place TV updates — November 9, 2026

The user authorized installation after the [overnight review](OVERNIGHT-REVIEW-2026-11-09.md) closed. Five existing TV installations were updated to **0.2.6 / code 27**:

| Hardware / Android API | Devices updated | Prior version |
| --- | ---: | --- |
| AFTKM / API 30 | 3 | 0.2.4 or 0.2.5 |
| NVIDIA Shield Android TV / API 30 | 1 | 0.2.4 |
| AFTT / API 22 | 1 | 0.1.19 |

Two additional configured screens were unreachable through ADB. The user explicitly requested that they be skipped; they were left unchanged.

## What was verified

The APK matches the host-tested candidate's SHA-256 and original signing certificate. Its v1/v2 signatures and API-22 minimum were checked before deployment. Each update replaced the existing package in place without uninstalling it or clearing its data. Encrypted preferences were compared byte for byte before and after installation, preserving the saved camera catalog, credentials, MQTT identity, ordering, enabled flags and layout preferences.

Installed package versions and file-integrity checks passed. The newer devices supplied native SHA-256 checksums; the API-22 device's native MD5 utility checked its exact copied APK against the verified source. Android's in-place update also enforced compatibility with the existing signing identity. The slow legacy transfer was resumed from a checked prefix, with the remaining bytes and reassembled file checked before installation. Temporary transfer files were removed afterward.

All five screens were asleep before and after updating. No viewer was launched, so this establishes installation and settings preservation, **not live-camera playback validation of 0.2.6**. Existing host validation remains 112 TV/core tests, 14 phone tests and four diagnostic-summary tests, both APK builds and lint with zero errors. The [runtime source CI run passed](https://github.com/lewi3069/property-tracker/actions/runs/42598332795). Runtime source did not change during deployment.

## Remaining limits

The native-allocation growth observed in 0.2.5 remains unresolved. Installing 0.2.6 does not establish that it fixes that growth or that the thirteen-feed wall is stable indefinitely. The [controlled memory-validation plan](NATIVE-MEMORY-VALIDATION.md), candidate playback checks and longer fault/coexistence testing remain future work.

No camera encoding, firewall, broker, Home Assistant rules, camera settings or TV power settings were changed. No new APK was publicly released. Google Play work remains paused. The old statement that 0.2.6 was uninstalled describes the overnight-review window; this document records the subsequent authorized installation.
