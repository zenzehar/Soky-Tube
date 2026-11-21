# Soky Car v32.90 — Rear-passenger display discovery

## User hardware and requirement

- Existing **Huawei Pura 70 Ultra**, Android 12 / API 31.
- Existing car's Android Auto screen (confirmed drawing surface: **1202 x 462**).
- User reports the display is physically mounted in the rear passenger seating area, out of the driver's view.
- Goal: rear-passenger video with vehicle in motion, **without any new hardware or paid service**.

This is a valid design goal **if video is presented only to passengers and
the vehicle host really provides an independent passenger display**.
Android Automotive OS supports different UX restrictions per display; a
passenger display may remain unrestricted while the driver's display is
restricted. But the current APK is an **Android Auto projected** CarAppService
from an Android 12 phone. Its navigation drawing Surface does not report
whether the physical monitor is located at the rear of the car.

## Changes in v32.90

- Adds **Passenger Check** to the existing successful Soky Car launcher pane.
- Reports Android phone projection versus native Android Automotive OS.
- Queries phone-visible Android displays and presentation-capable outputs.
- Keeps original Surface Diagnostics and audio-only Soky Tube Mobile intact.
- Keeps video disabled because the display-specific passenger restriction
  cannot yet be authenticated from the projection callback.
- Publishes an independently installable ARM64 APK for this probe.

Important limitations of the results:
- Non-default Android Display IDs do not prove the outputs are passenger-only.
- A physical Android Auto head unit may be absent from phone DisplayManager
  because Android Auto is projection, not an Android display registration.
- A 1202x462 drawing Surface shows rendering support but is not a supported
  video streaming output merely because it is behind the front seats.
- Do not spoof park state or replace the navigation map with streaming video.

## Installation

Download the successful **Soky Tube native Android Auto car probe APK**
GitHub Actions artifact **SokyTube-Android12-Passenger-Check**, branch
`experiment/soky-car-passenger-display-20261122`; install the extracted
`SokyTube_Car_PassengerCheck_arm64.apk`.

Package remains `com.sokytube.car` and can be installed alongside
`com.sokytube.mobile`. Signature mismatches can be resolved by uninstalling
only the diagnostic car package, without affecting the separate mobile app.

## What to test

While safely parked, open Soky Tube (Car) and tap **Passenger Check**. Record:
- Host platform line (Android phone projection or native Automotive OS).
- Total phone-visible display count.
- Non-default Android display count.
- Presentation-capable display count.

Also verify **Surface Diagnostics** continues to draw at 1202 x 462 and that
BACK returns to the launcher. Do not test while driving.

This test assesses *routing feasibility*, not whether passengers may see a
video. If the host exposes a dedicated secondary display with OEM-supported
passenger-specific UX restrictions, Soky Tube can be adapted to render to that
display and respect its own restrictions without defeating driver-safety
controls.

## Platform documentation

- https://developer.android.com/training/cars/platforms/automotive-os
- https://developer.android.com/training/cars/platforms/android-auto
- https://developer.android.com/training/cars/apps/library/draw-maps
- https://developer.android.com/docs/quality-guidelines/car-app-quality
