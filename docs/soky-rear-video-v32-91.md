# Soky Rear Video v32.91 (build 2481)

## Actual playback capability added

The phone-side **Soky Rear Video** Activity can open a **local video file**
with Android's built-in document picker and play it on an **independent,
presentation-capable secondary display** via `DisplayManager` and the native
Android `Presentation` window. Playback uses `VideoView` / Android's built-in
media decoder and has play/pause and +/-10s controls on the phone.

This is *real* video playback code, not an animated mock/test rectangle.
There is no added hardware, subscription, SDK or external player app.

The screen must be truly separate from the phone's default display and
exposed as `DisplayManager.DISPLAY_CATEGORY_PRESENTATION`. A generic
Android Auto projected/navigation surface is **not** such a display. It
cannot be targeted with `Presentation`.

### Quick test on existing hardware

1. Install the `SokyTube_RearVideo_arm64.apk` build from GitHub Actions.
   It is the standalone `com.sokytube.car` package, not the original
   `com.sokytube.mobile` app.
2. Connect the phone to the rear-seat display as you normally do.
3. On the **phone**, open the new **Soky Rear Video** launcher icon.
4. Confirm the playback destination really is exclusively visible to
   rear passengers (never to the driver).
5. Read the display topology count on the phone:
   - If `Presentation outputs: 1` or higher, choose a locally stored
     test video file and try playing it on the external screen.
   - If `Presentation outputs: 0`, Android Auto projection is not an
     independent display and video will **not** be routed by this mode.
6. Use PLAY/PAUSE/-10s/+10s controls on the phone, not the AA screen.
7. Disconnect and reconnect; confirm no continued playback on stale output.

The separate Android Auto **Soky Car** launcher still has **Passenger Check**
and **Surface Diagnostics**; the navigation drawing interface is not
repurposed for streaming video.

### Why this mode exists

The existing 1202x462 Android Auto SurfaceCallback can draw diagnostic
graphics but it does not disclose the physical position of the vehicle
screen or a passenger-only driver-distraction exception. The user has a
custom rear-mounted monitor, but Android Auto may still report it as the
main driver output. The safe and supported solution is to use a truly
independent passenger display when the system exposes one.

`Soky Rear Video` doesn't fake park status, interfere with Android Auto
host policy, use `MediaProjection`, or modify a vehicle safety interlock.
If this test shows **0 presentation outputs**, the next requirement is
to identify the head unit's *existing* available connection/output mode,
rather than assuming navigation drawing is a compliant movie interface.

### Limits in v32.91

- The phone picker currently supports **local media files**, not Soky Tube
  account content or YouTube format resolution. This isolates video
  routing feasibility before connecting the application media pipeline.
- Background/lost-focus video lifetime depends on the host display and
  Android system policies; the app closes the Presentation on activity
  destruction or display removal.
- A presentation display count alone cannot verify physical rear
  location. User must confirm that the display is exclusively for
  passengers.

References:
- https://developer.android.com/reference/android/app/Presentation
- https://developer.android.com/reference/android/hardware/display/DisplayManager
- https://developer.android.com/training/cars/platforms/android-auto
