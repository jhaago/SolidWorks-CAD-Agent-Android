# Remote workstation: demo and first live test build

Remote is part of the existing Android CAD Agent app. Home and Jobs remain simulated, with their repository preserved when switching between Demo and Live. Live provides primary-monitor viewing and manual input; AI desktop control and printer commands are unavailable.

## Pair and connect to Windows

Follow the [Windows setup and physical test guide](https://github.com/jhaago/SolidWorks-CAD-Agent/blob/feature/v1-solidworks-2020/docs/live-remote-windows-testing.md). Install Tailscale on Windows and Android, connect both to the same private network, reserve the separate loopback service and start RemoteAgent. Use private HTTPS Serve forwarding to `127.0.0.1:5079`. Never expose the CAD Agent Host on 53741, use Funnel or forward a router port.

1. In Windows Remote Agent open its two-minute pairing window and copy the one-time code.
2. In Android **Settings**, enter the HTTPS origin and pairing code, then **Pair with Windows**. Accept the device in the Windows window. Pending/rejected pairing cannot enable live access.
3. Open **Remote** and choose **Connect live workstation**. Wait for a current image, then **Resume Control**. The first connection and every reconnect start view-only. A request for control must be acknowledged before input is enabled.
4. Tap/drag the fitted image for primary input. Right click/scroll use the image centre. Send key supports named allowlisted keys; arbitrary text, clipboard and file transfer are unavailable.
5. Use **Stop Remote** or **Disconnect** to close the session. Leaving Remote, backgrounding or rotating releases input; returning can reconnect but never restores control automatically. The local Windows **Stop Remote** and **Ctrl+Alt+F12** remain the emergency controls. Offline phone requests cannot guarantee an immediate stop; Windows has a three-second heartbeat watchdog.
6. After restarting the phone app, use Settings → **Use saved live workstation**, then connect again. No session authority is persisted. Disconnect before changing the endpoint/mode or using **Forget saved pairing**. Remove the device in Windows too.

Pairing records use Android Keystore AES-GCM encryption in the app's non-backup directory. Only the non-secret HTTPS origin is saved in preferences. Pairing codes, session tokens and desktop images remain in memory. Neither the app nor its settings asks for an OpenAI API key. Credentials go only into HTTPS authorization headers; redirects are disabled and TLS validation remains enabled.

## Demo

Select Settings → **Use simulated demo** while disconnected, then **Connect demo** in Remote. Manual records local input only. Assist shows a fixed suggestion; Agent runs a fixed sample sequence. Take Control, Stop AI or manual input stops the demo task. The sample print confirmation requires explicit approval and never sends a physical printer command. Instructions do not change the demonstration's fixed progression.

## Limits and verification

Live JPEG images have a 1600-pixel longest edge and a maximum of five frames per second. Only the Windows primary monitor and ordinary unlocked desktop are supported. UAC, elevated applications, other Windows sessions and lock-screen control are unsupported. Input is ordered and bounded; unsent moves can coalesce, but a failed input event is never replayed. Stale images, generation changes and full queues disable control until an explicit Resume Control.

CI runs the complete JVM unit suite, debug APK build, lint and Android 35 emulator tests. Tests cover pairing approval/rejection and persistence, real Android Keystore round-trip/corruption/deletion, TLS request bounds, reconnect backoff, stale authority/frames, renewal races, queue overflow, input timeout, JPEG rendering, navigation, rotation/background and drag cancellation. These do not prove real Windows pixels/input, emergency hotkeys, mobile-data connectivity or SOLIDWORKS geometry. Use the physical checklist in the Windows guide for those separate acceptance gates.

The APK is a debug-signed test build. If Android rejects an update because CI used a different debug signing key, uninstall the earlier demo before installing this build. Uninstalling removes saved app data and pairing; revoke any previously paired device on Windows before pairing again.

## Architecture

`di/AppContainer` preserves the CAD repository and selects revisioned Remote adapters. Demo adapter instances remain available to existing demo flows. `LiveConnectionDriver` owns separate frame, heartbeat and ordered input coroutines, cancels work when Remote is not visible, renews short-lived tokens and retries connection failures at 1, 2, 4, 8, then 15 seconds. `RemoteDemoDriver` stays separate from live control. The HTTPS transport does not contain a Tailscale SDK; another private HTTPS connector can replace it later without changing the app protocol.

Approved [live design](https://github.com/jhaago/SolidWorks-CAD-Agent/blob/feature/v1-solidworks-2020/docs/superpowers/specs/2026-10-05-live-remote-workstation-design.md) and [implementation plan](https://github.com/jhaago/SolidWorks-CAD-Agent/blob/feature/v1-solidworks-2020/docs/superpowers/plans/2026-10-05-live-remote-workstation-implementation.md). Development remains on `feature/android-v0`; no main merge.
