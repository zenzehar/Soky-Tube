# ParkedTube English-only + Android Auto launcher test (experiment)

This experiment uses upstream [ParkedTube v1.5.0](https://github.com/gadmin2151/ParkedTube) under its PolyForm Noncommercial 1.0.0 license. It is **separate** from Soky Tube mobile/TV and changes no working Soky Tube production code.

## Track A — English translation

- All 32 upstream Android `strings.xml` resources are translated to English.
- The Cyrillic/Russian car keyboard and language toggle are removed, leaving English QWERTY and numbers/symbols.
- Home and search URLs request the YouTube English interface (`hl=en`). YouTube account preferences may still affect web-page language; video titles are not translated.
- The debug APK uses package ID `dev.codex.aaparkedyoutube.en.debug` and displays as `ParkedTube English`, so it does not overwrite upstream ParkedTube.
- The misleading generic network error was reworded: a WebView main-frame HTTP error can also cause this screen.

A [GitHub Actions workflow](../../.github/workflows/parkedtube-english.yml) retrieves the upstream tag, applies the patch, checks English resources and the original car launcher declarations, builds the debug APK with upstream Docker, and uploads an installable test artifact.

Build output: Actions > **ParkedTube English - experimental APK** > successful run > **ParkedTube-English-Debug-APK** artifact. Download the ZIP and extract the APK, then sideload it. Android may require permission for APK installation from your file manager.

## Track B — Why the launcher icon does not show

**Manifest inspection:** upstream ParkedTube already declares both `android.intent.category.CAR_LAUNCHER` and `android:appCategory="game"`. This project does not pretend an extra manifest line can force Android Auto to show an unsupported video app.

**Official limits as of November 2026:** Android Auto's native parked-app pathway is available on Android 15+ phones, but only actual games are officially supported. ParkedTube is video software, not a game. Unsupported categories and sideloaded APKs may be hidden despite developer Unknown sources.

1. Check phone OS: Android 15 or above for officially supported parked games.
2. In Android Auto settings, enable developer mode and Unknown sources.
3. Look in Android Auto > Customize launcher for **ParkedTube English**; enable it if present.
4. While safely parked, reconnect Android Auto and check launcher. The car must support parked-app execution.
5. If missing, use `diagnose-launcher.ps1` with adb connected to your phone on Windows. It only reads package information and does not bypass restrictions.
6. If the app still does not appear, consider a documented *parked-only* experimental approach such as [CarBridge](https://github.com/jmwerk/CarBridge), which can prepare an isolated car copy of the English APK or Soky Tube. It is unofficial and may be blocked by the Android Auto host. Never play or interact with video while driving.

**Other unresolved issue:** Your screenshots show YouTube failing to load on the phone. That is independent of launcher visibility. Check YouTube in Chrome and update Android System WebView/Chrome before interpreting a car-side failure.

No driver-distraction bypass is implemented. This project does not claim an Android Auto video capability has been validated on a physical car.
