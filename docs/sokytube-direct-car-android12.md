# Soky Tube Direct Android Auto Display: Android 12 Compatibility Probe

## Purpose

Test **Soky Tube itself** on an Android Auto head unit without installing another
video/mirroring player. The target phone is a Huawei Pura 70 Ultra (EMUI 14.2,
typically Android 12/API 31). This experiment is separate from the normal
Soky Tube mobile and TV APKs.

**This is not a video player yet.** It is a low-risk display/discovery probe.
The two possible entry points are:

- A Car App Library (1.4.0) `CarAppService` providing an English-language
  static diagnostic screen via a car template.
- A static `CAR_LAUNCHER` Activity for hosts that support direct car Activities.

The original mobile app retains its audio-only Android Auto MediaBrowserService.
The `stcar` variant removes that browser intent filter so the host can test
a car template rather than silently loading the old audio UI.

## Platform limitations

Google's officially supported **direct parked Activity** feature on Android
Auto requires Android 15 or higher, and as of 21 November 2026 the documented
supported category is games. Android 12 does not qualify.

Car App Library templates can operate on older phones, but the Android Auto
host may reject a sideloaded app even if its manifest is correctly merged.
A development build is not proof of compatibility on a production head unit.

A navigation-category test service is used for compatibility investigation
ONLY. Soky Tube is not a navigation app; this must not be published under that
category to Google Play, or used to circumvent driving restrictions.

## Build an APK

```bash
./gradlew --no-daemon --stacktrace :smarttubetv:assembleStcarDebug
```

Build products under:

```
smarttubetv/build/outputs/apk/stcar/debug/
```

The `universal` APK is suitable for broad testing; `arm64-v8a` is suitable
for modern ARM phones. This APK uses package `com.sokytube.car` and installs
alongside `com.sokytube.mobile`.

On the new GitHub branch, see the **Soky Tube Car probe APK** Actions workflow
for downloadable debug APK artifacts after the build succeeds.

## Test on the real Huawei (while safely parked)

1. Install only the **SokyTube_Car_Probe_arm64.apk** from a successful workflow,
   not a ZIP or the original mobile APK.
2. Open phone Settings > Apps. Verify `Soky Tube (Car) - Compatibility Probe`
   is installed.
3. In Android Auto developer settings, enable Unknown sources.
4. In Android Auto > Customize launcher, look for Soky Tube (Car).
5. Connect the phone to the head unit while parked. Open the car launcher.
6. If available, open Soky Tube (Car). The screen should say
   `Soky Tube connected` or `Car display interface detected`.
7. Check that the original Soky Tube still appears as an audio source.

Report **all three** separately:
- Installed on the phone (yes/no)?
- Listed in Customize launcher (yes/no)?
- Opened on the car screen (yes/no)? If opened, which of the two messages?

If it cannot appear, adding a video renderer cannot resolve the host's
launcher/installation restrictions. Test a Play internal-testing install or
a Desktop Head Unit only if justified; neither guarantees an actual-car result.

## Optional non-destructive Android Debug Bridge diagnostics

```bash
adb shell getprop ro.build.version.sdk
adb shell pm list packages | grep sokytube
adb shell dumpsys package com.sokytube.car
adb logcat -d -s CarApp.AppMgr CarAppService AndroidAuto AndroidRuntime
```

On Windows PowerShell, use `Select-String sokytube` instead of `grep`.
Do not publish raw logs without reviewing them for account details.

## If discovery succeeds

Next, add an **actual car Surface callback** and a car-owned video decoder
that reuses Soky Tube's existing YouTube format resolver. Keep one playback
owner, release the renderer on disconnect, preserve pause/position, and map
controls to a safe car UI. Crucially, **video must remain disabled until the
vehicle is reliably established as parked**; unknown drive-state fails closed.
No amount of fake speed readings or missing-speed permissions should enable
video in motion. An unofficial navigation-template technique may violate
Google's car app policy and is not a substitute for official parked-video
support.

This repo branch intentionally implements ONLY the launcher/display probe.
