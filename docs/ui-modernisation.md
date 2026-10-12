# Android UI modernisation — 11 October 2026

## Scope and source

Local branch `ui/android-modernisation`, based on reviewed commit `c2ab52a`.
The source was cloned from the 10 October engineering-review workspace. Its
working tree was clean and remains untouched. This repository is independent
of the .NET desktop solution; no source was pushed or installed on a phone.

Retain Kotlin, Compose and Material 3. The existing production flows are CAD
Chat (including reference pictures), Remote (including full screen and manual
input), and Settings (private HTTPS pairing). The retired Home/Jobs demo screens
remain test fixtures, and no simulated production connection was introduced.

## Presentation decisions

- Dark primary theme: charcoal `#10151C`, slate `#171F2A`, cyan `#52C2E7`,
  pale text, restrained semantic success/error colors. Retain an explicitly
  selectable light scheme for code consumers; no theme setting is persisted.
- Reusable headers, outlined cards, section headings, status pills and notices;
  system sans typography, 16–20 dp spacing, and code-native vector icons.
- Screen content is scrollable, including headers, with IME padding for editors.
  Phone actions wrap; readable content widths are capped for larger displays.
- Read-only brief, plan and evidence text can be selected. Headings have
  accessibility semantics, error notices are polite live regions, and icons
  accompany labels rather than substituting for them.
- Request, build/review, operations and evidence are grouped. Indeterminate
  progress indicates pending work; no invented progress percentage or result.
- Review feedback identified revision draft disposal in a separate lazy item.
  The draft now uses revision-scoped `rememberSaveable`; stable item keys
  preserve it while scrolling. The document picker/effect is owned by the
  screen and cannot disappear when a review card leaves composition.

Controllers, models, repositories, HTTPS/Keystore code, input authorization,
approval gates and protected-action callbacks are unchanged. The file picker
retains its original native Save As contract. APK identity stays
`com.jhaago.cadagent`; version becomes 0.4.4, code 8.

## Verification

Final offline build: `testDebugUnitTest assembleDebug assembleDebugAndroidTest`
passed; **123 JVM tests, zero failures**. The final instrumentation APK compiled.
Four navigation/full-screen tests passed on an earlier UI build on an isolated
API 30 emulator. Final new navigation, large-text approval and draft-restoration
tests require another device/emulator run; compilation is not a device pass.
The final rerun could not start because the local emulator executable was no
longer available. No APK was installed on a physical phone.

The first lint run stalled in IntelliJ UAST comment traversal while sources
were still being edited and was stopped. The final separate single-worker lint
run passed with **0 errors and 17 warnings**, matching the baseline.
No dependencies were downloaded or changed.

Build normally with installed JDK 17, Gradle 9.6.0 and Android SDK 36:

```powershell
gradle testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
gradle connectedDebugAndroidTest
```

## Phone acceptance

GitHub APK prerelease: [Android 0.4.4](https://github.com/jhaago/SolidWorks-CAD-Agent-Android/releases/tag/android-v0.4.4-preview).
The published APK was signed with the existing local debug key, and its public
certificate matches the 0.4.2 GitHub APK. No private key was uploaded. APK SHA-256:
`5ea9e3aab605eed11be5790c317e258e2d38e64b2ab27e9ab690ee859580e19c`.
Only the APK and release notes were published. Automatic approval review rejected
source-tree publication because the user requested an APK link. Source remains
local; the prerelease tag anchors the already-published `7c636f5` baseline,
and release notes explicitly distinguish that tag from APK source `dc71b0f`.

1. Transfer `CAD-Agent-Android-0.4.4-debug.apk` to an Android 8+ phone and open
   it. Allow APK installation for that file source if Android requests it.
   An update can retain existing pairing when the installed APK uses the same
   application ID and debug signing key. If signing differs, stop and preserve
   the existing install/pairing rather than automatically uninstalling it.
2. Open CAD Chat, Remote and Settings. Check large text, portrait/landscape,
   keyboard visibility, touch targets and TalkBack on the physical phone.
3. Reuse the saved private HTTPS workstation pairing or follow the existing
   [pairing guide](remote-workstation.md). Keep Tailscale connected as before.
4. Prepare a supported CAD request; review operations, revise where supported,
   approve explicitly, inspect the native model/evidence and download it.
5. While entering a revision, scroll to the connection/photo card and back;
   confirm the draft persists. Test native Save As while the review is scrolled.
6. Check Remote view-only entry, explicit Take control, pinch/pan, touch/drag,
   keyboard shortcuts, full-screen exit, cancellation and reconnect. Input
   must remain unavailable during active CAD work.

Physical pairing, photo/camera round trips, artifact downloads, live SolidWorks
execution and network transitions are not verified by the local JVM suite.
