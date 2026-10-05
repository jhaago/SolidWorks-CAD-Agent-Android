# SolidWorks CAD Agent Android

Native Android companion app for `jhaago/SolidWorks-CAD-Agent`. Development remains on `feature/android-v0`; nothing is merged to main.

The app includes Home, Jobs, New Job, Job Detail, Settings and **Remote**. CAD jobs use the in-memory demo backend. Remote supports the labelled demo and a first live Windows viewing/manual-control test build through private HTTPS pairing. Live AI desktop control and printer commands remain unavailable.

## Try Remote

1. Open **Remote** and select **Connect demo**.
2. **Manual**: tap/drag the sample desktop, try right click, scroll or a key. Input is recorded locally only.
3. **Assist**: enter an instruction, select **Run demo task**, then **Next demo step** to see a sample suggestion. You retain control.
4. **Agent**: run a demo task and advance its fixed steps. **Take Control**, **Stop AI**, or manual input immediately stops the task and returns to Manual.
5. The second Agent step pauses for a sample print confirmation. Approve or reject explicitly; dismissing the dialog rejects. Take Control is available inside the dialog too. Approval records a demo decision and never sends a print command.
6. Disconnect/reconnect to confirm old tasks remain stopped. Home and Jobs continue to work independently.

Instructions do not influence the fixed demonstration sequence. For live manual viewing/control, configure the workstation in Settings using the [pairing and Windows setup guide](docs/remote-workstation.md). Real Windows capture/input and mobile-data acceptance still require physical testing.

## Build and test

Requires JDK 17, Gradle 9.6.0 and Android SDK platform 36 / build-tools 36.0.0. The repository currently uses installed Gradle, matching CI.

```bash
gradle testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
gradle connectedDebugAndroidTest # requires an Android device or emulator
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions builds the APK and runs the Remote UI tests on an Android 35 emulator.

## Architecture

CAD screens use `CadAgentRepository`. Remote uses separate session, AI-control, display and input interfaces. The fake session publishes an atomic controller/task snapshot; connection-attempt and task IDs reject late callbacks. The app container survives Activity configuration changes, but sessions start disconnected after process termination. Pairing uses encrypted Android Keystore storage outside backups.

See [Remote Workstation](docs/remote-workstation.md) for guarantees, limitations and the live Windows backend boundary. Authoritative design and plan:

- [Remote Workstation design](https://github.com/jhaago/SolidWorks-CAD-Agent/blob/feature/v1-solidworks-2020/docs/superpowers/specs/2026-10-04-android-remote-workstation-design.md)
- [Remote shell implementation plan](https://github.com/jhaago/SolidWorks-CAD-Agent/blob/feature/v1-solidworks-2020/docs/superpowers/plans/2026-10-04-android-remote-session-shell-implementation.md)
