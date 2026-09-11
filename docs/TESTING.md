# Tests and battle comparisons

Automated tests cover model calculations, visibility, live weapon state, order ownership, and cleanup.
They use synthetic combat states and API test doubles.
They do not run the Starsector engine or establish an improvement in battle outcomes.
The experimental coordination still requires in-game validation.
Version 0.1.1 adds the missing mission text that prevented startup in 0.1.0.

The [first Observe run](observations/2026-09-11-observe-01.md) records a player victory with one Wolf lost.
It also records zero Omega orders and zero proposals in a setup with 248 active mods.
The report separates those facts from missing controller data and the AI Tweaks conflict that affects the next comparison.
The [first Coordinate run](observations/2026-09-11-coordinate-01.md) records a reported total loss with zero accepted Omega orders.
AI Tweaks fleet cohesion was still enabled.
Local build 0.1.2 adds labels and eligibility diagnostics to make that blocked state visible.

The build compiles against the installed Starsector and LunaLib JARs.
It also scans the mod classes for blocked filesystem and reflection APIs.
Packaging compares the JAR with current compiled classes and the source fingerprint.
These steps establish build and package consistency.
They do not establish the behavior of the stock pilot after receiving an order.

## Automated tests

Run the following commands from the mod directory:

```powershell
.\gradlew.bat clean build --console=plain
python -m unittest discover -s tests -p "test_package.py"
python package.py
```

The HTML test report is `build/reports/tests/test/index.html`.
The XML reports are in `build/test-results/test`.
The Python tests reject missing mission resources and invalid LunaLib icon registrations.
They also extract an in-memory ZIP into a temporary directory and make sure that its sole mod folder contains the runtime files.
This extraction test does not install anything into the game.
The suite includes the following cases:

- Hard flux remains a floor while shields prevent hard-flux dissipation.
- Soft flux, weapon flux, and shield upkeep share the correct dissipation budget.
- Damage types do not apply their armor multiplier to hull damage.
- Moving-target interception distinguishes a fleeing target from a head-on meeting.
- Turret slew does not extend the physical mount arc.
- Empty ammunition removes new weapon threat while existing projectiles remain independent.
- Hidden enemy positions and equipment remain unread.
- Observations copy live mutable vectors and read real DP and vent duration.
- Small ships can wait for delayed support without stopping a safe duel.
- Productive contact and changing targets prevent false chase rejection.
- Regrouping avoids a route through an enemy line.
- Observe mode creates no assignment or waypoint.
- Existing orders, full assault, retreat, manual control, and controller conflicts take precedence.
- Edited and shared waypoints survive cleanup as player-owned orders.
- Rejected orders, stalled orders, expiry, and disable release the appropriate resources.

## First in-game comparison

The included mission uses fixed ship variants and map objectives.
The simulation still contains variation between runs.
Identical fleets do not establish a deterministic battle seed.
Configuration changes and other mods can also change the comparison.

1. Start with Omega AI, LunaLib, and LunaLib's required libraries, with other combat behavior mods disabled.
2. Enable Omega AI diagnostic logging in LunaLib.
3. Select Observe mode and enable both fleets.
4. Start Omega AI: Fleet Trial from the mission list.
5. Enable autopilot for the flagship.
6. Record the result and the behaviors listed below.
7. Repeat the mission with Coordinate selected and all other configuration unchanged.
8. Alternate modes over several runs before drawing a conclusion.

Record the following observations for each run:

| Measurement | What to record |
| --- | --- |
| Battle setup | Game and mod versions, mode, enabled fleets, other mods, and issued orders |
| Actual controller | Whether the ship uses the stock pilot or another mod's controller |
| Order effect | Whether a visible Omega rally order appears and whether the ship moves toward it |
| Support | Seconds that scouts face enemy weapons before the heavy ship can contribute |
| Pursuit | Seconds spent chasing outside useful weapon range without closing |
| Participation | Ships that wait, repeatedly regroup, or fail to return to combat |
| Outcome | Time to victory or defeat, lost ships, and surviving hull |
| Cost | Peak planning milliseconds and visible frame stutter |

The log distinguishes a proposal from an accepted assignment.
An accepted assignment means that the task manager returned the expected ownership state.
It does not mean that the ship reached the waypoint or that the decision helped.
Record the visible result separately.

For local build 0.1.2, also record the ship labels and the `observed`, `eligible`, `effective`, and `conflict` fields.
The slash-separated counts show the player side first and enemy side second.
An eligible count of zero needs an exclusion explanation before a behavior comparison is meaningful.
The `Omega ship` records identify changed decisions, controller classes, assignments, and proposals.
Enemy labels require the player's side to see that ship.

## Control and compatibility cases

Run these cases before enabling more behavior by default.
The expected result describes the intended contract.
Actual in-game results remain pending for this release.

| Case | Expected result |
| --- | --- |
| Observe from battle start | No Omega gameplay orders |
| Coordinate to Observe while moving | Only Omega's unchanged assignment disappears |
| Disable while paused | Ownership cleanup occurs without advancing simulation time |
| Take manual control during a regroup | No stale Omega order remains in control |
| Issue an attack, escort, capture, defend, or rally order | The new player order remains intact |
| Move an Omega waypoint or add another ship to it | Omega transfers ownership without deleting the edited order |
| Full Assault, Full Retreat, Avoid, or Ignore | Omega stops intervening for the affected fleet |
| Enemy full retreat | No new regrouping orders obstruct the rout pursuit |
| Enemy and player fleet switches | Only enabled fleets receive proposals and orders |
| Phase ship, carrier, station, drone, or custom AI | Existing control remains intact |
| AI Tweaks fleet cohesion | Combat status reports observing only |
| RTSAssist active selection and control | The controlled ship receives no Omega order |
| StopStackingMe and other movement mods | Record actual controller ownership and any competing behavior |
| Ship death, retreat, battle end, or a runtime error | Omega releases only resources that it still owns |

## Behavioral limits and later cases

Test a slow cruiser against a faster fleeing frigate and against an interceptable target.
Test two fast scouts with a delayed capital ship.
Test a short-range frigate approaching a long-range capital.
Test a damaged or overfluxed support ship, a crowded line, a map edge, and an enemy between the ship and its support.
Watch for repeated rallies, stalled advances, excessive caution, lost objectives, and routes that expose a ship to another enemy.

Repeat the comparison with only one fleet coordinated, then reverse the advantage using matched fleets.
Keep loadouts, officers, skills, combat readiness, deployment, and other mods consistent.
Use Observe results to estimate normal variation before choosing quantitative acceptance thresholds.
Do not treat one victory as evidence of a general improvement.

Later survival work needs armor sectors, missiles already in flight, real vent durations, and movement during escape.
Later exploitation work needs isolated targets and targets with intact escorts.
Later weapon work needs torpedoes, pressure missiles, saturation weapons, regeneration, and scripted ammunition.
Later carrier work needs fighters, interceptors, bombers, support wings, long sorties, and replacement losses.
Those modules are outside the 0.1.0 gameplay scope.
