# Changelog

## 0.2.1 - local development build

- Replaced Observe/Coordinate with one Enable Omega AI switch that defaults to Yes.
- Stopped old saved Observe selections from silently disabling enabled control.
- Added a fixed combat HUD with active ship counts and reasons when no ships are controlled.
- Identified AI Tweaks and RTSAssist pilot conflicts by name.
- Allowed Starsector's own transparent pilot wrapper while preserving unknown mod controllers.
- Updated the mission briefing and gameplay test instructions for the supported setup.

## 0.2.0 - local development build

- Replaced temporary regrouping orders with direct target selection, facing, and movement control for ordinary combat ships.
- Added effective-range control, pressure-based withdrawal with recovery, support staging, and pursuit of reachable vulnerable enemies.
- Added shared target allocation and rejection of pursuits against faster fleeing enemies.
- Added short action labels tied to decisions executed by the ship pilot.
- Made Coordinate the default for new settings; Observe restores native pilots.
- Removed the global AI Tweaks cohesion block; individual orders and custom controllers still take precedence.
- Kept native weapons, shields, systems, and emergency collision avoidance active.

## 0.1.3 - local test build

- Simplified ship labels to one short active action: Regrouping, Waiting for support, or Disengaging.
- Removed ship labels when Omega has no active order, including Observe mode and excluded ships.

## 0.1.2 - local test build

- Added ship decision labels with Omega's action, eligibility reason, controller, current assignment, and public target.
- Added explicit blocked status when another fleet coordinator prevents Omega orders.
- Added observed and eligible ship counts and changed ship decisions to diagnostic logs.
- Added control-label updates while paused without issuing new orders.
- Kept the public update feed on the last published release while packaging local build metadata separately.
- Added an explicit LazyLib dependency for the decision labels.

## 0.1.1

- Fixed a startup crash caused by the missing Fleet Trial mission briefing file.
- Fixed the missing Omega AI icon in the LunaLib settings menu.
- Changed release packaging to create `OmegaAI/` and its ZIP in the mod project root, with only runtime files inside.

## 0.1.0

- Added Observe mode to display proposed fleet decisions without issuing combat orders.
- Added optional experimental coordination for unsupported approaches, detached stragglers, and pursuits without useful progress.
- Added temporary rally orders that defer to existing commands and release control on expiry, disable, or manual takeover.
- Added guards for earlier Omega AI, AI Tweaks fleet cohesion, RTSAssist control, and unknown ship controllers.
- Added LunaLib controls for each fleet, simulator participation, combat status, and diagnostic logging.
- Added the Fleet Trial mission with fixed ship variants for comparing Observe and Coordinate modes.
- Added the Omega emblem for the mod list, combat status, and mission.
