# Current state review — 11 October 2026

## Submission recovery followup — 12 October 2026

Product commit `db51e7a` enables exact-ID submission recovery when workstation
status advertises `clientSubmissionIds`. Before POST, Android commits a scoped
UUID/cancellation record in app-private preferences; it stores no prompt, photo
or credentials. Lost receipts, reconnects and process recreation read that exact
job, even when another active job has identical text. One foreground retry can
reuse the original UUID and copied in-memory image; recreated state cannot resend
missing input. A 404, conflicting receipt or rejected retry preserves uncertainty
about the original POST. Stop intent survives until its own job is recovered.
A task stopped before any POST finishes locally and clears its record safely.
Terminal confirmation compare-clears the record; device rejection/forgetting
clears only the paired endpoint/device scope. Successful reconnect can recover
from a transient tracking-store error. Older workstations retain the legacy flow
and its prompt-matching limitation.

Local verification: 140 JVM tests passed, both APKs built, and lint completed with
zero errors and 20 offline warnings. Three new `UseKtx` suggestions refer to the
tracking store: it deliberately checks the boolean `commit()` result before
allowing a POST; the KTX helper discards that result. Independent review found three
material lifecycle bugs; all three regressions failed before the combined fix,
then passed with the full suite. The new emulator test uses the actual preferences
store, recreates the Activity/driver, and confirms exact-job cancellation without
resending a task. Hosted results are recorded in the existing draft PR:
https://github.com/jhaago/SolidWorks-CAD-Agent-Android/pull/2

The initial feature emulator run passed 18/19 tests, including the new recovery
test; an existing `LiveRemoteFlowTest` failed with Espresso's
`RootViewWithoutFocusException`. Final feature CI includes the reviewed fixes.
Physical-phone acceptance, cloud planning, mobile network transitions, merge,
installed-app upgrade and release remain deferred. The historical review below
describes the earlier baseline.

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

GitHub's API 35 emulator initially passed 16 of 18 UI tests. The two failures
identified stale test assumptions: the remote screen hides control when its live
frame disappears, and Compose retains a focused lazy item even offscreen. The
remote test now checks absence of control; the draft test explicitly disposes and
restores its saveable panel, verifies the same revision retains text, and verifies
a changed revision restores an empty field with revision submission disabled.
The updated instrumentation APK compiles (`android-emulator-test-update.log`).
The current GitHub workflow is the source of emulator execution results.

No physical Android device was connected and no local emulator executable was
available. Instrumentation APK compilation is not device acceptance. Live pairing,
cloud planning, photos, approval/execution/cancel/complete/download, network
transitions, large text and accessibility still require the paired phone and
Windows/SOLIDWORKS workstation. Existing prompt-equality reconciliation of uncertain
submissions remains the documented P2 limit for older workstation builds. The
coordinated phone/proxy/Host change above replaces it for supported builds.

Original checkouts and installed applications were preserved. Source publication
is now explicitly requested by the user; earlier documentation about source
remaining local describes the previous APK-only publication task.
