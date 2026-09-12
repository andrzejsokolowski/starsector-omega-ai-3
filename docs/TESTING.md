# Testing local build 0.2.2

Enable Omega AI defaults to Yes. There is no Observe/Coordinate selector.
Keep AI Tweaks and RTSAssist enabled for the compatibility test.
The tested versions are AI Tweaks 2.2.10 and RTSAssist 0.1.9c.
Use AI Tweaks' normal pilot, with its separate full Custom AI options off.
Logging is optional, and no log collection is required for this test.

## First gameplay test

1. Install `Omega-AI-0.2.2.zip` through the mod manager.
2. Keep Omega AI, LunaLib, LazyLib, AI Tweaks, and RTSAssist enabled.
3. Restart Starsector.
4. Open LunaLib and leave Enable Omega AI, Ship action labels, and Combat status set to Yes.
5. Open Omega AI: Fleet Trial from the mission list.
6. Enable autopilot for the flagship.
7. Open the command screen and give your fleet Search & Destroy.
8. Let the ships approach and enter sensor contact.

Search & Destroy permits Omega to choose targets and movement.
Specific capture, escort, attack, and rally orders take precedence and can suspend Omega.
The first visible check is a player count above zero under Omega control in the upper-left HUD.
Action labels such as Advancing and Engaging appear above ships while their Omega pilot executes those decisions.
Labels remain absent before a ship has a target, during native collision handling, or when another control check rejects it.
A count that stays at zero means the controller is not active; read the adjacent HUD reason before judging combat behavior.

## RTSAssist handoff

1. Wait until an autonomous ship displays an Omega action label.
2. Give that ship a movement command through RTSAssist.
3. Make sure that it follows your command and loses its Omega action label.
4. Cancel or complete the RTS command.
5. Make sure that Omega resumes when the ship has a current movement decision and no other blocking order.

A hold command remains active until you cancel it.
During the order, the fleet HUD can show Following RTSAssist commands.
Other autonomous ships can remain under Omega control throughout your intervention.
No mod disable or restart is required to return control after an RTS command.

## Switch check and behavior

During combat, set Enable Omega AI to No.
The HUD must show disabled, and Omega action labels must disappear.
Set it back to Yes, then resume combat.
Eligible ships regain Omega control after a planning update and a ship update.
This confirms that the switch changes the controller; a different battle outcome is not required to prove that it activated.

Watch whether ships reach useful weapon range, keep room to withdraw, wait for support, and pursue reachable vulnerable targets.
Stalling, repeated movement loops, and worse losses remain failures even when automated tests pass.
Automated coverage does not establish improved battle outcomes.

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
python package.py --verify Omega-AI-0.2.2.zip
```

Native tests use the verifier setting required by Starsector's obfuscated code and read the installed base settings in the test process only.
No game configuration or installed mod is changed. Test reflection, Mockito, and native harness setup are excluded from the runtime JAR and ZIP.

The regression suite also covers saved Observe settings, the Yes/No switch, native pilot wrappers, and HUD text without active ship labels.
A complete plugin test captures live-style ship data, plans movement, installs Omega, executes native thrust, reports active control, and restores the original pilot.
The ship and combat-engine state in that test are mocks; it does not launch a graphical Starsector battle.

## Compatibility coverage

`InteropTest` executes RTSAssist's installed wrapper and injection registry.
It checks repeated autonomous updates, post-advance movement priority, cancellation, and a long manual-control interval followed by autopilot.
It also checks restoration, native-pilot capture before RTS attaches its wrapper, and recovery when RTS attaches after Omega.

`AiTweaksInteropTest` loads the installed AI Tweaks pilot through AI Tweaks' own class transformer.
It executes ExtendedShipAI alone and inside the real RTS wrapper, with Omega around the complete chain.
The checks cover target override, controller identity, configuration and flag forwarding, RTS command priority, and return to autonomous control.
The ship and engine state remain test doubles, so these checks do not prove live battle quality or every mod interaction.
Test loaders, reflection, third-party JARs, and copied game classes are excluded from the mod package.

## Local 0.2.2 verification, 2026-09-12

The Java build passed all 85 tests, including the integration checks using the installed AI Tweaks and RTSAssist code.
All 9 Python packaging tests passed, and the generated ZIP passed package verification.
The archive contains 12 runtime files under one `OmegaAI/` folder, with the current compiled JAR and local 0.2.2 metadata.
Its SHA-256 is `6d5dd19502a8e8aeaa253ecd595685e379dab96a7abfb5fc969a88f2d8811458`.
Live battle testing of this build is still pending.

## Earlier evidence

The first [Observe run](observations/2026-09-11-observe-01.md) ended in a reported win with one Wolf lost.
The first [Coordinate run](observations/2026-09-11-coordinate-01.md) ended in a reported total loss.
Both recorded zero Omega orders, with AI Tweaks cohesion enabled.
Those runs tested the older integration prototype, not this direct pilot, and establish no performance result for 0.2.0.
