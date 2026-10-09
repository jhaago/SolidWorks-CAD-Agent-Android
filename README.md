# SolidWorks CAD Agent Android

Native Android companion app for `jhaago/SolidWorks-CAD-Agent`. Development is reviewed on feature branches before integration to `main`.

The app opens in **CAD Chat**, with separate **Remote** desktop and **Settings** tabs. Home/Jobs demo navigation and simulated workstation controls have been removed from the app. Their old screens, models and fakes remain under `app/src/test` for regression tests only. A workstation must be paired over private HTTPS before connecting.

## Try the paired workstation

1. Configure the HTTPS origin and pair with Windows in **Settings** using the [pairing and Windows setup guide](docs/remote-workstation.md).
2. Open **CAD Chat** and connect. Enter a CAD instruction to prepare a plan; revise, approve, review, or download the resulting job there.
3. Open **Remote** to see the desktop. Choose **Take control** before sending mouse or keyboard input, and **View only** to release it. Reconnects start view-only.

CAD Chat can attach one reference picture from the gallery or camera to a normal CAD task. The phone scales and encodes the selected image as JPEG; the workstation keeps it with the job for clarification and replanning. The app blocks photo submission until the paired Windows Agent reports support for image jobs, preventing an older version from silently dropping the photo. A plan still needs separate approval before CAD execution. This source build has not yet been installed and checked against the paired Windows workstation.

Version 0.4.1 displays the steps in a version-2 CAD plan before approval. The Windows Agent currently admits only `V2 rectangle boss: 20 x 10 x 5 mm; save as new-part.sldprt`-style requests: a new centred Top Plane rectangle, blind boss and non-overwriting save. Android hides in-place revision controls for this validated plan; submit a new task with a new filename to change it. This APK has passed local unit tests and lint, but a paired physical-phone run has not yet been performed.

There is no in-app simulated workstation path. Real Windows capture/input, mobile-data behavior and SOLIDWORKS geometry still require physical testing.

## Build and test

Requires JDK 17, Gradle 9.6.0 and Android SDK platform 36 / build-tools 36.0.0. The repository currently uses installed Gradle, matching CI.

```bash
gradle testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
gradle connectedDebugAndroidTest # requires an Android device or emulator
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions builds the APK and runs the Remote UI tests on an Android 35 emulator.

## Architecture

Remote uses separate session, AI-control, display and input interfaces. Test fakes publish deterministic session/task snapshots from test sources; production starts in an unpaired state and cannot connect until live pairing selects adapters. The app container survives Activity configuration changes, but sessions start disconnected after process termination. Moving between CAD Chat and Remote retains the connection while releasing phone input; entering Settings pauses the connection. None of those actions cancels a durable workstation CAD job. Pairing uses encrypted Android Keystore storage outside backups.

See [Remote Workstation](docs/remote-workstation.md) for guarantees, limitations and the live Windows backend boundary. Authoritative design and plan:

- [Remote Workstation design](https://github.com/jhaago/SolidWorks-CAD-Agent/blob/feature/v1-solidworks-2020/docs/superpowers/specs/2026-10-04-android-remote-workstation-design.md)
- [Remote shell implementation plan](https://github.com/jhaago/SolidWorks-CAD-Agent/blob/feature/v1-solidworks-2020/docs/superpowers/plans/2026-10-04-android-remote-session-shell-implementation.md)
