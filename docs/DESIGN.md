# Direct combat control in 0.2.1

Omega now owns the target and movement decision for eligible ordinary combat ships.
The active runtime no longer calls the old rally planner or creates fleet assignments.
The earlier `FleetPlanner`, `OrderBook`, and `DecisionView` remain as historical implementation and regression fixtures; they do not drive this build.

## Control path

`OmegaCombatPlugin` captures one visibility-limited frame per enabled side every 0.25 simulation seconds.
`TacticalPlanner` assigns targets across the fleet and produces a velocity, hull facing, action, target, and expiration for each eligible ship.
`PilotSession` installs `OmegaShipAI` over the exact native `BasicShipAI`, either directly or inside Starsector's own transparent wrapper.
It preserves the original outer pilot for restoration.
It does not replace unknown wrappers, custom pilots, or existing target overrides.

`OmegaShipAI` extends the game's native pilot. A plain delegating wrapper is unsafe because RC8 checks that the advancing AI is the ship's installed controller.
The native update still runs the attack, shield, vent, system, and collision modules.
If Omega remains permitted, `Rc8Helm` removes only that update's newly queued movement commands and runs a native `BasicEngineAI` with Omega's desired velocity and facing.
Weapons, shield commands, other command types, and commands already present before the update survive.
Existing movement commands, blocked movement, emergency collision avoidance, and queued system use cause Omega to yield.

RC8's explicit-facing helper ignores requested speed for ordinary ships and tries to maximize velocity along its heading.
The adapter supplies the difference between desired and actual velocity as that heading, with a small acceleration-dependent tolerance.
This makes the native module accelerate, brake, or strafe to reduce velocity error while handling facing separately.
Repeated native-command tests check convergence to a lower speed, a stop, and reverse movement.
The bridge is restricted to 0.98a-RC8 and has no production reflection or filesystem access.

An action label is available only after this helm call succeeds.
Publishing a proposal does not display a label. Expiry, manual takeover, loss of permission, changed controller, or disabling Omega hides it.
Intents expire after 0.8 simulation seconds. Pausing does not consume their lifetime, but control checks still apply.
Disabling Omega and battle cleanup restore the original pilot only when the installed pilot still belongs to Omega.

## Decisions

The fleet processes ships in descending battery strength to allocate shared targets.
Scoring considers distance and arrival time, enemy flux and hull, nearby fire support, existing commitment, and excessive allocated damage.
A target gets a short commitment to reduce pointless switching; unreachable fleeing targets lose priority.

Combat range uses the distance at which at least 60 percent of available non-PD, non-missile battery damage contributes, with a collision margin.
Facing candidates include each battery's mounting direction, so a broadside can face its battery toward the target.
Velocity includes target motion and a braking term around the desired range.

Pressure estimates use visible enemy weapons, range, arcs, cooldown, ammunition, shield damage multipliers, live flux, and nearby projectiles.
Directional armor samples reduce estimated unshielded damage; armor is not treated as an extra hull pool.
The short forecast asks when flux reaches 90 percent or estimated hull damage exhausts current hull.
Retreat reserve includes braking and estimated time to leave enemy weapon range at reduced backing speed.
Retreat has a minimum commitment and a flux recovery threshold to limit repeated advance/retreat switching.

Before an unsupported approach, the planner estimates pressure at the position needed to fire.
A frigate or destroyer that outruns heavier support moves behind that support until it can contribute.
A lone ship can hold outside an unsafe enemy battery.
Reachable high-flux, disabled, or badly damaged targets invite pursuit if the pursuer has reserve.
A faster target opening range outside the battery causes disengagement instead of endless chase.

Movement candidates account for visible ship/hulk radii, relative velocity, and map edges.
Escape directions favor lower collision risk and nearby ready allies.
Native emergency collision handling has final priority.

## Limits and remaining work

These forecasts are estimates, not exact time-to-death predictions.
They approximate armor damage, turret turning, burst timing, accuracy, and shielding; future shield behavior and missiles remain uncertain.
Current projectiles and future gun pressure can overlap conservatively in the burst estimate.
A two-second collision projection cannot predict a whole battle or guarantee an escape route.

Native weapon aim, missile firing, shield decisions, venting, and system activation remain unchanged.
Dedicated encirclement and flank assignment, carrier target/wing control, and specialist phase pilots are not implemented.
The automated scenarios establish specific control properties, not improved battle outcomes.
The original research and previous-iteration analysis remain in repository history and the workspace's private references.

## Enable switch and inactive control

Enable Omega AI is the only on/off control and defaults to Yes.
The settings loader ignores the removed radio setting, including saved Observe selections.
The upper-left HUD reports active control separately for each fleet.
When a fleet has no active ships, it shows the most common reasons from the actual control checks.
These include AI Tweaks, RTSAssist, specific fleet orders, manual control, systems, and missing weapon or deployment data.
The HUD only counts ships visible to the player and does not depend on a flagship being alive.
Ship labels remain limited to executed movement decisions.

AI Tweaks ExtendedShipAI and RTSAssist's pilot wrapper remain unsupported.
The known native wrapper is not treated as a custom pilot, but Omega does not unwrap opaque third-party controllers.
The installed AI Tweaks picker selects ExtendedShipAI even with its Custom AI settings off.
RTSAssist hooks friendly ships at deployment without requiring an RTS movement command.
Use the minimal mod setup in the test guide to exercise Omega's controller.
