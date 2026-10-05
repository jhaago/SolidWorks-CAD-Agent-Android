# Remote Workstation milestone

Remote belongs to the existing Android CAD Agent app. This milestone is a tested simulated workflow, not production remote desktop access.

## Implemented behavior

- Disconnected → Connecting → Connected with an explicitly labelled demo workstation and sample CAD display. Duplicate Connect requests are ignored; late completion of a cancelled attempt cannot reconnect it.
- Manual, Assist and Agent modes, with a visible current controller and fixed demo task progression.
- Assist keeps User authority and provides a sample suggestion. Agent temporarily owns input while running; it gives up authority while waiting for print approval.
- Take Control and Stop AI immediately return to Manual/User, stop the active task and reject any pending confirmation. Manual pointer/key input in Agent mode performs takeover before input dispatch. A stopped task never resumes on stale progress or reconnect.
- Start-print confirmation requires explicit approval/rejection tied to the current task and request IDs. Duplicate, stale and wrong-ID decisions are ignored. Dismissal rejects. The modal includes Take Control. No physical printer command is implemented.
- Input accepts connected User authority only, validates finite normalized coordinates and key identifiers, and records a bounded local history. Aspect-fit display/input use the same rectangle; letterbox taps are ignored. Held demo input is invalidated on authority loss. The live adapter must physically release keys/buttons on takeover, disconnect and cancellation.
- CAD job repositories remain independent. Session/repository instances survive Activity configuration changes. Process death starts a new disconnected demo session.

## Test coverage

Unit tests exercise model authority constraints, duplicate commands, connection/task callback identity, takeover, stop/rejection, protected-action replay, reconnect without task resume, Assist, explicit ViewModel disconnect cancellation, input validation/authority and display coordinate mapping. Compose tests exercise Remote navigation without resetting CAD jobs, takeover, rejected print confirmation and takeover while the modal is open.

The initial red runs reached missing remote types/repository/ViewModel/input APIs, followed by green runs against the implementations. Android UI test compilation first failed against the missing container wiring. Device execution is reported separately from compile-only verification.

## Live Windows boundary

The next milestone introduces a separate Windows **Remote Workstation Agent**, outside the SOLIDWORKS COM bridge:

1. Desktop/window capture and a real display stream.
2. Authenticated device pairing, encrypted transport, short-lived session authority and device revocation. Do not expose the existing loopback CAD Host or an unrestricted router port.
3. Manual pointer/keyboard input with ordered events, authority checks, explicit release of held input and reconnect/takeover tests on Windows.
4. AI observation using screenshots and Windows UI Automation, then bounded actions with visible progress, interruption and protected confirmation.
5. Route supported CAD operations through the existing CAD Agent API, prefer UI Automation for other semantic controls, and use vision/pointer control where necessary.
6. Validate a Bambu Studio preparation workflow through slicing/preview. Starting a physical print remains separately confirmed.

`RemoteDemoDriver` isolates explicit demo progression from the live repository contracts. The display frame currently contains sample metadata, not a video decoder; the live source will add a render-surface implementation behind that boundary. No WebRTC/socket library, screen-capture service, OS input injection, pairing credential or AI key is shipped in this slice.

Live capture, authentication, transport and input must be implemented and physically tested before advertising real remote control. Existing SOLIDWORKS 2020 geometry acceptance remains a separate pending Windows gate.

## Implementation decisions

- Continued the existing `feature/android-v0` in a fresh clone; no additional worktree or main merge.
- Used the existing `di/AppContainer` and ViewModel factory in place of the plan's illustrative names.
- Implemented display/input interfaces before the ViewModel because it consumes them; the final delivered scope is unchanged.
- Used the repository's existing installed-Gradle 9.6.0 convention; did not add a wrapper or change dependency versions.
- Fake connection presentation uses a cancellable 400 ms UI delay. Repository correctness tests advance explicit callbacks and require no wall-clock sleeps.

## Verification and remaining device checks

The local complete unit suite passed 47 tests (zero failures/errors/skips), and both debug APKs compiled. [Android CI run 37248882919](https://github.com/jhaago/SolidWorks-CAD-Agent-Android/actions/runs/37248882919) passed for commit `8b99dfd`: the full unit suite/debug build job passed, and all **4 Android 35 emulator UI tests passed, with 0 failures and 0 skipped**. The delivered APK comes from this run. Live Windows/SOLIDWORKS acceptance is not part of this milestone.

Independent whole-branch review found no Important/Critical issues. Deferred minor verification gap: ViewModel-store disposal, Activity rotation and gesture cancellation are implemented but lack dedicated automated device tests. Check rotation, leaving/closing the app while connecting, and cancelling a drag during the next Android device session. These checks are not claimed to have passed.

The initial emulator runs exposed two test-harness errors: locating an uncomposed lazy-list child and matching a substring as an entire semantic label. The same flow tests failed first, then passed after using lazy-list-aware scrolling and exact controller text. All task/confirmation/state assertions remain in place. Local Android lint passed with 0 fatal/errors and 11 warnings in existing files (dependency/API updates, icon and style conventions); those were left outside this milestone.
