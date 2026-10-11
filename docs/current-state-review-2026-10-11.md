# Current state review — 11 October 2026

The review branch preserves `7c636f5`, the `c2ab52a` route/session renewal fixes,
the `dc71b0f` modern UI and subsequent documentation history. The APK release tag
`android-v0.4.4-preview` still identifies its historical baseline; this source
branch is the current reviewed code and does not retag or replace that APK.

Independent read-only review confirmed endpoint compatibility with the current
Windows RemoteRoutes and session/action ownership across token renewal. It found
an offscreen revision-draft restoration issue. The draft now saves job/revision
ownership with its text and exposes that text only to the matching owner. A JVM
regression test first failed for a changed revision, then passed after correction.

Final local verification: 124 JVM tests, zero failures; debug and instrumentation
APKs built; lint completed with zero errors and the same 17 baseline warnings.
Evidence is in `app/build/test-results/testDebugUnitTest`,
`app/build/reports/lint-results-debug.xml` and local `android-final.log`.
Builds used the existing offline JDK 17, Gradle 9.6.0 and Android SDK 36 caches.
No dependencies were changed. Independent review confirmed the owner-check fix.

No physical Android device was connected and no local emulator executable was
available. Instrumentation APK compilation is not device acceptance. Live pairing,
cloud planning, photos, approval/execution/cancel/complete/download, network
transitions, large text and accessibility still require the paired phone and
Windows/SOLIDWORKS workstation. Existing prompt-equality reconciliation of uncertain
submissions remains the documented P2 limit; durable submission IDs require a
coordinated phone/proxy/Host change.

Original checkouts and installed applications were preserved. Source publication
is now explicitly requested by the user; earlier documentation about source
remaining local describes the previous APK-only publication task.
