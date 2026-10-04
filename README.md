# SolidWorks CAD Agent Android

Native Android companion app for the SolidWorks CAD Agent project.

Implementation work is developed on feature branches before integration to `main`.

## Planned Remote Workstation capability

The main Android app is also planned to contain a **Remote Workstation** area rather than requiring a separate remote-control app. The intended workflow combines:

- live view of the trusted Windows workstation;
- manual TeamViewer-style mouse/keyboard control;
- Manual / Assist / Agent control modes;
- AI co-control with an immediate **Take Control** override;
- protected-action confirmation for consequential operations such as starting a physical 3D print;
- SolidWorks operations routed through the existing CAD Agent API whenever that is more reliable than UI clicking;
- ordinary Windows-app control for workflows such as Bambu Studio.

Authoritative design and next-session implementation plan are maintained in `jhaago/SolidWorks-CAD-Agent` on `feature/v1-solidworks-2020`:

- `docs/superpowers/specs/2026-10-04-android-remote-workstation-design.md`
- `docs/superpowers/plans/2026-10-04-android-remote-session-shell-implementation.md`

The first Android remote slice intentionally uses fake session/display/input backends to establish the UI, control-mode, safety, and human/AI handoff contracts before live Windows capture/transport/input are connected.
