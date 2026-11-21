# Soky Tube Android 12 car surface test

## Confirmed result from real vehicle (21 November 2026)

The native `Soky Tube Car - Compatibility Test` Android Auto
`PaneTemplate` appeared on the user's vehicle head unit, displaying:

- "Soky Tube connected"
- "Car display interface detected"
- "Video not enabled yet"

This confirms discovery, launch and rendering of a **host template** on the
Huawei Pura 70 Ultra / EMUI 14.2 (Android 12 base). It does NOT demonstrate
a direct video rendering surface or Android Auto video policy approval.

## Phase two: static SurfaceCallback test

This experimental source branch uses Soky Tube's own `CarAppService`, no
external app or mirroring player.

The `Surface Test` action on the original safe screen pushes a
`NavigationTemplate` and registers `SurfaceCallback` through `AppManager`.
If the host grants surface access, the app draws a teal rectangle, and:

- `CAR SURFACE AVAILABLE`
- `Soky Tube native rendering test`
- `No video enabled`
- the surface resolution

The template has an explicit BACK action. The callback is detached when
the screen is destroyed. There is no media pipeline, decoder, playback,
GPS-location access, speed spoofing or driving-state override.

Important: a navigation-category surface is documented for drawing maps
and is *not* a supported way to publish general video streaming apps.
This is a developer-only, local compatibility probe.

## Install

Download the `SokyTube-Android12-Surface-Check` ZIP artifact from the
successful `Soky Tube native Android Auto car probe APK` GitHub Actions
run on `experiment/soky-car-surface-check-20261121`.
Extract `SokyTube_Car_Surface_arm64.apk` and install it as an update to
the previous `com.sokytube.car` package.

If the update fails with a signature mismatch (CI debug signing may differ
between builds), uninstall **Soky Tube (Car)** only, then install the new
APK. Do not uninstall `Soky Tube` mobile package. This test package has
no saved login state by design.

Park safely, reconnect Android Auto, open Soky Tube (Car), select
`Surface Test`, and report:

1. Does the normal `Soky Tube connected` screen still appear?
2. Is the new `Surface Test` button visible?
3. After tapping it, is a dark full-screen test image with a **teal box**
   shown (not just the Android Auto background or spinner)?
4. Can you use BACK to return?
5. Does the original Soky Tube audio card continue working?

For failures, inspect `adb logcat -s SokyCarSurfaceProbe AndroidRuntime`
and redact device/account identifiers before sharing.

## Next engineering gate

Even if a Surface is provided, video streaming must be gated to a **real
host-verified parked state**, with video hidden whenever vehicle state is
moving or unknown. This can be tricky on projected Android Auto on
Android 12 and cannot safely be inferred from GPS speed alone.

Only after surface support AND safe parked-state controls have been
confirmed should we attach a video renderer to the existing Soky Tube
media resolver, establish one player owner, and perform playback, surface
lifecycle and transport tests. This branch does not claim to enable video.
