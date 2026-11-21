# Soky Tube Car: Android 12 native surface and safety audit

## Confirmed on the real head unit (21 November 2026)

The Huawei Pura 70 Ultra, running EMUI 14.2 (Android 12 base), successfully:
- discovered and launched **Soky Tube (Car)** in the Android Auto launcher;
- rendered the native `CarAppService` / `PaneTemplate`;
- showed the teal diagnostic pattern on the host Surface at **1202 x 462** pixels.

Therefore launcher registration and Canvas surface access have passed real-device tests.
These results **do not** establish that Android Auto supports video playback
through a navigation template.

Stable proven fallback: **v32.88**, branch
`experiment/soky-car-surface-check-20261121` / PR #24.

## Current implementation: v32.89 Safety Audit

Branch: `experiment/soky-car-safety-audit-20261121`

This branch extends ONLY the independently installable `stcar` flavor
(package `com.sokytube.car`; build 2479), not the original mobile player
(package `com.sokytube.mobile`).

Changes:
1. Displays the already confirmed car display result in the home Pane.
2. Renames the action to **Surface Diagnostics**.
3. Displays the real Surface size and device Android API level dynamically.
4. Introduces `SokyCarPlaybackGate`: no video render permission is granted;
   lack of a trusted parked-state signal always blocks visual playback.
5. Diagnostic Surface registration is scoped to the visible screen lifecycle;
   it is unregistered on stop/destroy and host-owned Surface objects are never
   released by the app.
6. Keeps English-only diagnostic UI, existing mobile audio playback, and
   separate app identities. No media decoder, external player or mirroring tool
   is added to the car flavor.

## Why the car video toggle is not yet enabled

As of 21 November 2026, Google documents that **Android Auto** supports
direct parked app Activities only on Android 15+ and presently supports
**games** as its parked app category. A navigation `SurfaceCallback`
is for drawing map graphics, NOT a video display surface. A trustworthy
"parked" authorisation is not available through this existing experiment.
Speed=0, lack of GPS movement, manual toggle and developer/unknown-sources
settings are not equivalent to host-enforced driver-distraction restrictions.

Official references:
- https://developer.android.com/training/cars/parked/auto
- https://developer.android.com/docs/quality-guidelines/car-app-quality
- https://developer.android.com/training/cars/platforms/android-auto

There is **no video decoder** wired to this Surface in v32.89.
This branch is only an isolated development compatibility diagnostic; do not
distribute it as a navigation app. Do not use it while driving.

## Download and install v32.89

Open GitHub Actions > **Soky Tube native Android Auto car probe APK** on
`experiment/soky-car-safety-audit-20261121`. After a successful run, download
`SokyTube-Android12-Safety-Audit` and extract
`SokyTube_Car_Audit_arm64.apk` (or the universal APK). Check
`SHA256SUMS.txt` if needed.

This APK updates only `com.sokytube.car`. If Android rejects the update
because the CI debug certificate changed, uninstall **Soky Tube (Car)** only;
do **not** uninstall the separate Soky Tube mobile app. The car diagnostic
flavor deliberately contains no account/login state.

## Manual parked test matrix

1. With the vehicle safely parked, reconnect Android Auto; verify the
   **Soky Tube (Car) - Safety Audit** launcher entry is available.
2. Open Soky Tube Car. Verify **Surface confirmed on car display** and
   **Video gate: BLOCKED**; no playable video controls should appear.
3. Tap **Surface Diagnostics** and verify the teal marker, English-only text,
   size (1202 x 462 on the tested head unit), Android API, and **VIDEO BLOCKED**.
4. Press BACK and reopen the diagnostic several times; check for crashes,
   frozen Canvas, or loss of the Android Auto launcher.
5. Disconnect and reconnect Android Auto; verify Surface re-registration.
6. Confirm ordinary Soky Tube mobile Android Auto audio still works,
   including play/pause and +10s/-10s commands.
7. Leave v32.88 available as the proven rollback if any of these regress.

If problems occur, capture (while parked):
`adb logcat -d -s SokyCarSurfaceProbe AndroidRuntime CarAppService`
Redact any personal/device identifiers before sharing.

## Engineering gate before any car video renderer

- Support an **official video app category and host-verified parked UX**;
  do not re-purpose navigation map content.
- Use the host's actual driving UX restriction callback. Immediately remove
  video from display when restrictions activate or state becomes unknown.
- Retain audio-only controls as permitted and test lock/reconnect behaviour
  independently in the mobile media service.
- Only THEN connect the existing Soky Tube media resolver to an appropriate
  parked video Activity/Surface with one playback owner, surface lifecycle
  handling and automated regression tests.

Until those gates are satisfied, `SokyCarPlaybackGate.canRenderCarVideo()`
must return `false`, and **the car screen must not show video**.
