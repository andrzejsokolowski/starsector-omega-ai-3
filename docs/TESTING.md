# Verification of local build 0.2.0

This build has direct movement control. Logging is optional and is not the gameplay test.
The automated checks run before packaging; in-game combat effectiveness remains unverified.

Local verification on 2026-09-11 passed 74 Java tests, 8 Python packaging tests, the runtime API scan, and ZIP verification.
The final ZIP SHA-256 is `e65662a60a23c3e5ed23718968bdf248ee6043de43f81206b55604498fcf1884`.

## Automated coverage

The tactical scenarios exercise repeated approach and braking until the ship settles inside weapon range, pressure-based withdrawal and recovery, waiting for heavy support, faster-target chase rejection, shared pursuit of a vulnerable target, broadside facing, obstacle avoidance, and absent targets.
The repeated movement scenario uses simplified acceleration physics, not a running battle.

Native integration tests load the installed RC8 game classes and execute the actual `BasicEngineAI`.
They check forward thrust, backward thrust, strafing with independent hull facing, braking to a lower speed, and preservation of fire, shield, and pre-existing commands during movement replacement.
A repeated-update harness applies actual native thrust commands to simplified acceleration physics and checks convergence to a lower speed, a stop, and reverse movement.
A failed native helm call must restore the movement commands it replaced.
A real `OmegaShipAI` constructor and advance run against mocked ship and combat-engine state.
This checks that a published intent becomes an executed thrust decision, that its label appears only after execution, and that manual takeover and expiration hide it.
Session tests install the real pilot, restore the original, preserve assignments, and leave another mod's replacement controller intact.
These tests do not create a graphical battle or simulate native weapons hitting real ships.

The existing model and observer tests cover hard/soft flux, damage types, interception, ammunition, turret arcs, fog of war, copied vectors, deployment points, and vent duration.
Legacy rally tests remain as regression fixtures for the earlier implementation, which is no longer called by the combat plugin.
Packaging checks the actual tested class bytes, source hash, Java version, metadata, icon registration, mission resources, and one-folder ZIP layout.

```powershell
.\gradlew.bat clean build --console=plain
python -m unittest discover -s tests -p "test_package.py"
python package.py
python package.py --verify Omega-AI-0.2.0.zip
```

Native tests use the verifier setting required by Starsector's obfuscated code and read the installed base settings in the test process only.
No game configuration or installed mod is changed. Test reflection, Mockito, and native harness setup are excluded from the runtime JAR and ZIP.

## In game

Install the local ZIP through the mod manager, select Coordinate, and open Omega AI: Fleet Trial.
Use autopilot for the flagship if Omega should control it.
The small action label describes the current executed movement decision; the status counts ships actually under Omega control.
A specific fleet order, active system, native collision response, manual control, or custom pilot can suspend Omega steering.

The behavior to assess is whether ships close to useful range, withdraw with room left, wait for support without becoming idle, and finish reachable vulnerable enemies.
Stalling, repeated advance/retreat loops, or worsening losses remain failures even when all automated tests pass.
No log collection is required for this check.
Observe remains available to restore native pilots during development.

## Earlier evidence

The first [Observe run](observations/2026-09-11-observe-01.md) ended in a reported win with one Wolf lost.
The first [Coordinate run](observations/2026-09-11-coordinate-01.md) ended in a reported total loss.
Both recorded zero Omega orders, with AI Tweaks cohesion enabled.
Those runs tested the older integration prototype, not this direct pilot, and establish no performance result for 0.2.0.
