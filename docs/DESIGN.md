# Design and scope

The project starts with observable fleet coordination and a narrow control boundary.
An earlier audit covered two prior implementations, Starsector mechanics, developer explanations, and player reports.
Those implementations remain separate comparison cases.
This project uses new Java source and does not ship their controllers or extracted game source.

The first alpha tests whether temporary fleet assignments can improve support and pursuit behavior while the stock AI pilots each ship.
Observation is the default because an accepted order does not prove that its effect improves combat.
The optional Coordinate mode exists for that experiment.
No measured improvement in battle outcomes is claimed for 0.1.0.

## The eleven problems

The table separates the requested behavior from what this release implements.
Partial support means that a limited rule exists, with further work and in-game evidence still required.
Planned features do not change combat in this release.

| Problem | Status in 0.1.0 | Further work |
| --- | --- | --- |
| Fleet cohesion | Partial: idle stragglers can rejoin a nearby combat group | Local support networks, line shape, reinforcement handling |
| Futile chases | Partial: six-second progress history and moving-target interception | Route cost, pursuit opportunities, target uncertainty |
| Retreat before rapid destruction | Planned | Predicted shield, armor, and hull failure along an escape route |
| Opponent strength and range | Partial: live guns, physical arcs, support range, arrival time, and DP | Damage types, bursts, armor sectors, systems, and uncertainty |
| Focus fire and overload targets | Planned | Shared damage commitments and suitable attacker allocation |
| Shield and flux control | Model fixtures only, no commands | Calibrated incoming damage and legal recovery choices |
| Punish retreating targets | Existing productive contact avoids generic regrouping | Coordinated finishing attacks with an affordable return route |
| Fast ships outrun support | Partial: stage eligible small ships near heavier support | Objective-aware groups and broader engagement timing |
| Flanking and encirclement | Planned | Useful attack bearings that preserve support and firing lanes |
| Missile use | Live ammunition observation only | Weapon roles, salvo budgets, flight time, and firing safety |
| Carrier and fighter focus | Carriers and fighters retain existing control | Wing missions, strike commitments, travel time, and replacement pressure |

## Observation and mechanics

`Observer` builds a separate view for each fleet.
It reads an enemy ship only after the game reports that the side can perceive it.
It does not maintain a hidden contact by reading that enemy's live position.
Every observation copies mutable game vectors and weapon state into immutable records.

Weapon observation uses current ammunition, disabled state, cooldown, range, mount arc, aim, and derived damage rates.
Point defense and missiles do not count as sustained offensive gun support in this alpha.
The support range covers weapons that contribute at least 60% of available offensive gun damage per second.
This prevents one short secondary gun from defining the ship's fleet support range.
It is a rough grouping measure, not an instruction to fight at that distance.

DP means deployment points, the fleet budget used to deploy ships.
The observer reads DP from `FleetMemberAPI.getDeploymentPointsCost()`.
It does not use `ShipAPI.getDeployCost()`, which describes a deployment CR cost.
DP helps select a heavier support ship but does not establish its combat strength.
Actual support also depends on ready weapons, range, physical arcs, aim, and current flux.

The observer reads the game's vent duration and vent-rate modifier.
The flux model separates hard flux, soft flux, weapon flux, shield upkeep, and one shared dissipation budget.
Damage-type fixtures keep armor and shield multipliers separate from hull damage.
These calculations remain model scaffolding and do not issue shield or vent commands.
They do not yet account for armor cells, defense systems, damage listeners, or discrete weapon bursts.

Optional projectile diagnostics include visible missiles even when their launchers no longer exist.
An empty launcher contributes no new gun threat.
Guided missile reach uses a conservative travel bound rather than a predicted flight path.
It ignores turning, interception by point defense, shields, obstacles, and uncertainty in future motion.
The diagnostic count is neither a hit prediction nor a survival estimate.

## Fleet proposals

`FleetPlanner` evaluates ships in stable ID order.
Each ship chooses a nearby combat-ready support ship instead of a center point across the entire fleet.
The support ship needs available offensive guns, at least 45% hull, and less than 65% flux.
Heavier candidates take precedence when they exist.
This avoids treating two early scouts as a substitute for their delayed capital ship.

The distance limit varies with weapon range and hull radius, within 850 to 2,000 units.
Crossing that limit alone never produces an order.
A proposal also needs a stalled pursuit, an unsupported approach, or an idle ship behind the combat group.
Useful firing contact prevents a generic regrouping proposal.
Existing commander objectives prevent the planner from controlling the assigned ship.

A chase needs six seconds without 100 units of useful gap reduction.
The ship must also have at most five units per second of radial closing speed and an interception estimate above twelve seconds.
Interception uses the target's velocity vector and the pursuer's speed and weapon reach.
Target changes reset the history.
The model assumes sustained target motion and does not predict maneuvers or movement systems.

An unsupported approach requires a frigate or destroyer that reaches combat in less than eight seconds.
Its heavier support must arrive more than four seconds later.
The enemy must have twice its DP or over 250 units of additional support range.
Current supporting gun damage must also remain below one quarter of the enemy's offensive gun damage.
These thresholds are initial experimental values, not calibrated survival limits.

Regroup points sit beside a support ship with space for the controlled ship's hull.
The planner separates proposed points, avoids occupied friendly hulls, and keeps points within the map.
It rejects straight routes through enemy hulls or across enemy gun envelopes from outside.
It also rejects endpoints that lead deeper into a nearby enemy's gun envelope.
The stock pilot still handles actual navigation and collision avoidance.
The planner does not solve paths around hulks, terrain, or moving obstructions.

## One owner for each order

`OrderBook` creates individual `RALLY_TASK_FORCE` assignments through the public task manager.
Its lease is temporary ownership of one assignment and waypoint.
It records the assignment identity, type, target identity, target position, and assigned member.
It refreshes ownership only while all these properties agree.
It does not replace ship AI, issue direct movement commands, set target overrides, or write shared AI flags.

Omega refuses a ship that already has an unrelated assignment.
It also defers to manual control, unsupported controllers, allied fleets, active systems, recovery states, and global directives.
Player control and order ownership are inspected again at the command boundary.
Handover runs every frame, including paused frames.
An edited or shared waypoint transfers to its new owner and remains intact.

An unchanged order expires after 1.25 seconds without a new proposal.
It also ends on arrival within 220 units, after six seconds without movement progress, or after twelve seconds of continuous ownership.
The waypoint stays fixed during that commitment rather than following a moving support ship every tick.
An eight-second cooldown prevents immediate repeated orders after release or transfer.
On disable or failure, Omega removes only its own unchanged assignment and unused waypoint.
Cleanup failures cause another cleanup attempt on the next frame.

The exact stock controller class is an eligibility boundary for the supported game version.
Wrappers and unknown custom controllers retain control.
An earlier Omega AI or active AI Tweaks fleet cohesion disables order creation globally.
RTSAssist's three known control markers exclude individual ships.
These guards do not establish compatibility with every mod that changes combat.

## Measurement and next stages

Fleet planning runs at most four times per simulation second.
Observation copies live equipment on each planning pass.
Projectile diagnostics run at ten-second intervals only when logging is enabled.
The log records changed proposal reasons, accepted and rejected orders, unreadable observations, and peak planning duration.
Large-fleet performance remains unmeasured in the game.
Pairwise searches and per-pass allocation need profiling before broader behavior or scale claims.

The next stages depend on evidence from controlled battles:

1. Establish that stock ships obey the temporary orders and that handover works in the actual command interface.
2. Measure support, unproductive pursuit, engagement time, losses, and any waiting loops against Observe runs.
3. Replace coarse strength estimates with route-based danger, arrival, and recovery predictions.
4. Add shared pressure and finishing assignments while retaining useful local commitments.
5. Add carrier wing missions and missile budgets with their existing aiming and firing safeguards.
6. Introduce specialist shield or movement control only where the evidence requires a separate controller.

The [test guide](TESTING.md) defines the comparison procedure and outstanding integration cases.
Each behavioral stage needs both benefit measurements and tests for displaced behavior.
A unit test, accepted assignment, or higher missile count does not establish a better fleet AI.

## Reference basis

The implementation uses the installed 0.98a-RC8 API and local inspection of its controller and task-manager behavior.
The repository contains no extracted reference source.
These public sources explain the broader design questions and player reports:

- [Starsector 0.98a release notes](https://fractalsoftworks.com/forum/index.php?topic=31536.0) describe the supported game release.
- [Uniquifying the Factions, Part 1](https://fractalsoftworks.com/2022/03/18/uniquifying-the-factions-part-1/) explains interactions between ship AI and system AI.
- [Offensive and defensive weapon behavior](https://fractalsoftworks.com/forum/index.php?topic=28364.msg416540#msg416540) explains why weapon timing changes also require defensive testing.
- [Player reports of fleet cohesion failures](https://www.reddit.com/r/starsector/comments/1k7fwww/) identify unsupported charges and command frustrations.
- [Player reports of missile use](https://www.reddit.com/r/starsector/comments/l565c7/) describe the linked-gun workaround.
- [Player reports of early missile depletion](https://www.reddit.com/r/starsector/comments/1ktazxb/) show that missile problems also include overspending ammunition.

Player reports identify symptoms and test cases.
They do not establish game formulas or controlled comparisons.
The private research report contains the wider audit and source list.
