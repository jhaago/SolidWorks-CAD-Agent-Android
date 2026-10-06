# SolidWorks CAD Agent Android

Native Android companion app for `jhaago/SolidWorks-CAD-Agent`. Development remains on `feature/android-v0`; nothing is merged to main.

The app opens directly into **Remote** with **Settings** as its only other top-level destination. Home/Jobs demo navigation and simulated workstation controls have been removed from the app. Their old screens, models and fakes remain under `app/src/test` for regression tests only. A workstation must be paired over private HTTPS before connecting.

## Try Remote

1. Configure the HTTPS origin and pair with Windows in **Settings** using the [pairing and Windows setup guide](docs/remote-workstation.md).
2. Open **Remote**, connect to the paired workstation, and confirm the view-only desktop image appears.
3. Explicitly resume control before sending mouse or keyboard input. Reconnects start view-only.
4. Submit, revise, approve, review, or download live CAD jobs from the task panel when connected.

There is no in-app simulated workstation path. Real Windows capture/input, mobile-data behavior and SOLIDWORKS geometry still require physical testing.

## Build and test

Requires JDK 17, Gradle 9.6.0 and Android SDK platform 36 / build-tools 36.0.0. The repository currently uses installed Gradle, matching CI.

```bash
gradle testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
gradle connectedDebugAndroidTest # requires an Android device or emulator
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions builds the APK and runs the Remote UI tests on an Android 35 emulator.

## Architecture

Remote uses separate session, AI-control, display and input interfaces. Test fakes publish deterministic session/task snapshots from test sources; production starts in an unpaired state and cannot connect until live pairing selects adapters. The app container survives Activity configuration changes, but sessions start disconnected after process termination. Leaving Remote releases phone input and its viewing session; it does not cancel a durable workstation CAD job. Pairing uses encrypted Android Keystore storage outside backups.

See [Remote Workstation](docs/remote-workstation.md) for guarantees, limitations and the live Windows backend boundary. Authoritative design and plan:

- [Remote Workstation design](https://github.com/jhaago/SolidWorks-CAD-Agent/blob/feature/v1-solidworks-2020/docs/superpowers/specs/2026-10-04-android-remote-workstation-design.md)
- [Remote shell implementation plan](https://github.com/jhaago/SolidWorks-CAD-Agent/blob/feature/v1-solidworks-2020/docs/superpowers/plans/2026-10-04-android-remote-session-shell-implementation.md)
