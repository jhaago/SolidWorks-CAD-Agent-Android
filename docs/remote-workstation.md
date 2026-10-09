# Remote workstation: live test build

CAD Chat is the app's primary screen. Remote provides the desktop view and manual input; Settings handles pairing. The Home/Jobs demo navigation and simulated workstation controls have been removed. The app starts unpaired and connects only to a live paired Windows workstation.

## Pair and connect to Windows

Follow the [Windows setup and physical test guide](https://github.com/jhaago/SolidWorks-CAD-Agent/blob/feature/v1-solidworks-2020/docs/live-remote-windows-testing.md). Install Tailscale on Windows and Android, connect both to the same private network, reserve the separate loopback service and start RemoteAgent. Use private HTTPS Serve forwarding to `127.0.0.1:5079`. Never expose the CAD Agent Host on 53741, use Funnel or forward a router port.

1. In Windows Remote Agent open its two-minute pairing window and copy the one-time code.
2. In Android **Settings**, enter the HTTPS origin and pairing code, then **Pair with Windows**. Accept the device in the Windows window. Pending/rejected pairing cannot enable live access.
3. Open **CAD Chat** or **Remote** and choose **Connect**. In Remote, wait for a current image, then **Take control** if needed. The first connection and every reconnect start view-only. A request for control must be acknowledged before input is enabled. Choose **View only** to release control before a CAD task.
4. Tap/drag the fitted image for primary input. Right click/scroll use the image centre. Send key supports named allowlisted keys; arbitrary text, clipboard and file transfer are unavailable.
5. Use **Stop Remote** or **Disconnect** to close the session. Switching to CAD Chat releases input while keeping the viewing connection; entering Settings or backgrounding pauses it. Returning can reconnect but never restores control automatically. The local Windows **Stop Remote** and **Ctrl+Alt+F12** remain the emergency controls. Offline phone requests cannot guarantee an immediate stop; Windows has a three-second heartbeat watchdog.
6. After restarting the phone app, use Settings → **Use saved live workstation**, then connect again. No session authority is persisted. Disconnect before changing the endpoint/mode or using **Forget saved pairing**. Remove the device in Windows too.

In CAD Chat, choose **Add picture** to use a gallery photo or take a camera photo with the next normal CAD instruction. The app converts it to a bounded JPEG, and the workstation retains it for the initial plan and later clarification. If a plan is ambiguous, the existing job clarification field asks for more detail; the separate Design Brief interface is not automatically opened. A job in **Awaiting clarification** shows the current question and a **Clarification or requested changes** field. Submit an answer to request a new plan, then review that plan before approving it. The reply is tied to the current plan revision; if the revision changes, refresh the job before replying. A brief workstation status must not replace the current revision or hide this field while a detailed job refresh is delayed.

Pairing records use Android Keystore AES-GCM encryption in the app's non-backup directory. Only the non-secret HTTPS origin is saved in preferences. Pairing codes, session tokens and desktop images remain in memory. Neither the app nor its settings asks for an OpenAI API key. Credentials go only into HTTPS authorization headers; redirects are disabled and TLS validation remains enabled.

## Limits and verification

Live JPEG images have a 1600-pixel longest edge and a maximum of five frames per second. Only the Windows primary monitor and ordinary unlocked desktop are supported. UAC, elevated applications, other Windows sessions and lock-screen control are unsupported. Input is ordered and bounded; unsent moves can coalesce, but a failed input event is never replayed. Stale images, generation changes and full queues disable control until an explicit Resume Control.

CI runs the complete JVM unit suite, debug APK build, lint and Android 35 emulator tests. Tests cover pairing approval/rejection and persistence, real Android Keystore round-trip/corruption/deletion, TLS request bounds, reconnect backoff, stale authority/frames, renewal races, queue overflow, input timeout, JPEG rendering, navigation, rotation/background and drag cancellation. These do not prove real Windows pixels/input, emergency hotkeys, mobile-data connectivity or SOLIDWORKS geometry. Use the physical checklist in the Windows guide for those separate acceptance gates.

The APK is a debug-signed test build. If Android rejects an update because CI used a different debug signing key, uninstall the earlier build before installing this one. Uninstalling removes saved app data and pairing; revoke any previously paired device on Windows before pairing again.

## Architecture

`di/AppContainer` starts with unavailable adapters and selects revisioned live Remote adapters after pairing. The old demo screens, models and fakes are under the JVM test source set and cannot be selected by the production app. `LiveConnectionDriver` owns separate frame, heartbeat and ordered input coroutines, pauses its phone-side session work when the app is backgrounded or Settings is open, renews short-lived tokens and retries connection failures at 1, 2, 4, 8, then 15 seconds. Closing the app does not cancel a durable workstation CAD job; stopping it is explicit. The HTTPS transport does not contain a Tailscale SDK; another private HTTPS connector can replace it later without changing the app protocol.

Approved [live design](https://github.com/jhaago/SolidWorks-CAD-Agent/blob/feature/v1-solidworks-2020/docs/superpowers/specs/2026-10-05-live-remote-workstation-design.md) and [implementation plan](https://github.com/jhaago/SolidWorks-CAD-Agent/blob/feature/v1-solidworks-2020/docs/superpowers/plans/2026-10-05-live-remote-workstation-implementation.md). Development remains on `feature/android-v0`; no main merge.
