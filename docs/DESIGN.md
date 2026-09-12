# Direct combat control in 0.2.2

Omega now owns the target and movement decision for eligible ordinary combat ships.
The active runtime no longer calls the old rally planner or creates fleet assignments.
The earlier `FleetPlanner`, `OrderBook`, and `DecisionView` remain as historical implementation and regression fixtures; they do not drive this build.

## Control path

`OmegaCombatPlugin` captures one visibility-limited frame per enabled side every 0.25 simulation seconds.
`TacticalPlanner` assigns targets across the fleet and produces a velocity, hull facing, action, target, and expiration for each eligible ship.
`PilotSession` preserves the current pilot and installs an Omega adapter around it.
Accepted delegates are the exact native pilot, AI Tweaks ExtendedShipAI, and RTSAssist's known wrapper.
The adapter implements the game AI interfaces rather than replacing the existing pilot with a new native instance.

Before advancing its delegate, Omega temporarily installs that original controller.
This satisfies RC8 and AI Tweaks' installed-identity checks and lets RTS perform its own nested controller swaps.
A finally block restores Omega only if the remaining controller still represents the same delegate.
A different replacement made by another mod remains installed.

The existing pilot runs its attack, shield, vent, system, and collision modules.
RTS post-advance injections also run before Omega checks the movement command blocks.
If control remains permitted, `Rc8Helm` replaces only the delegate's new movement commands with Omega's desired velocity and facing.
Weapons, shields, other command types, and pre-existing commands survive.
RTS movement blocks, external movement commands, collision avoidance, and queued system use cause Omega to yield.

The session samples exposed native pilots each frame before RTS wraps newly deployed ships.
It uses a captured pilot only while both the wrapper's configuration and AI flags still identify that same pilot.
That gives Omega access to native targeting and collision queries without reading RTS's private pilot list.
If no matching native pilot was captured, movement still works through the wrapper.
In that fallback, Omega leaves target override to the delegate and yields when a short collision projection detects immediate risk.

RTS uses delegate calls to detect whether its wrapper is still running.
Omega keeps that bookkeeping active through the wrapper's public configuration getter while manual control skips AI updates.
This prevents RTS from wrapping the outer Omega adapter around its own existing wrapper.
A fresh RTS target command releases Omega's target override and briefly suspends its steering decision.
The integration uses public APIs and class-name checks, with no production reflection or third-party library dependency.

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
The native collision decision has priority when its pilot is available; the wrapper fallback uses the short collision projection described above.

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

AI Tweaks ExtendedShipAI and RTSAssist's known wrapper are supported by the adapter.
AI Tweaks' separate full Custom AI is outside the tested configuration.
RTSAssist remains enabled during autonomous combat and takes movement priority when a command is active.
Specific fleet assignments, including AI Tweaks cohesion orders, still take precedence.
The test guide describes the supported setup and the RTS handoff check.
