# Omega AI

<img src="omega-ai-icon.png" alt="Omega AI emblem" width="192">

Omega AI 0.1.1 is an experimental fleet coordinator for Starsector 0.98a-RC8.
This first alpha introduces observation, temporary regrouping orders, and a mission for comparing their effects.
It does not yet implement the full combat AI described in the [design](docs/DESIGN.md).
Combat quality and compatibility still need in-game testing.

Omega AI starts in Observe mode.
This mode evaluates proposed orders and leaves combat decisions to the existing AI.
The combat status displays the current mode, proposal count, and active order count.
Optional diagnostics record decision changes in `starsector.log`.

In Coordinate mode, eligible ships can receive three types of temporary regrouping orders:

- A detached ship can abandon a chase after six seconds without useful closing progress.
- An unsupported frigate or destroyer can wait near heavier support before approaching a stronger or longer-range opponent.
- An idle straggler can rejoin a group that is closer to visible combat.

The existing ship AI pilots the ship and controls its weapons, shields, systems, and collision avoidance.
These first rules use limited estimates of support and arrival time.
They do not provide a complete model of danger, escape routes, or enemy strength.

## Install and try the alpha

The mod requires Starsector 0.98a-RC8 and LunaLib 2.0.0 or later.
The release ZIP contains an `OmegaAI` folder for a mod manager.
The mod ID is `omega_ai3`, which distinguishes this project from earlier Omega AI attempts.

1. Download `Omega-AI-0.1.1.zip` from the [GitHub release](https://github.com/andrzejsokolowski/starsector-omega-ai-3/releases/tag/v0.1.1).
2. Install the ZIP through your mod manager.
3. Enable Omega AI and LunaLib.
4. Open LunaLib and select Omega AI.
5. Leave the mode at Observe for the first run.
6. Open the mission Omega AI: Fleet Trial.
7. Enable autopilot for the flagship to compare autonomous fleets.
8. Repeat the mission with Coordinate selected.

LunaLib also controls observation and coordination for each fleet, simulator participation, combat status, and diagnostic logging.
Configuration changes take effect during combat.
When you select Observe or disable the mod, Omega releases its own unchanged orders.
The [test guide](docs/TESTING.md) explains how to compare runs and report failures.

## Control and compatibility

Existing player and commander orders take precedence.
Omega does not replace capture, escort, attack, defend, rally, or retreat assignments that it did not create.
Global Avoid, Ignore, Full Assault, and Full Retreat commands stop coordination for the affected fleet.
Enemy Full Retreat also stops regrouping so that Omega does not obstruct pursuit of a routed fleet.

This alpha coordinates ordinary combat ships that use the stock `BasicShipAI` controller.
It excludes manual player control, allied fleets, carriers, phase ships, fighters, drones, stations, station modules, and unknown custom controllers.
It also defers while a ship vents, overloads, or uses an active system.
Broadside ships keep their stock facing and weapon control.

An enabled earlier Omega AI mod blocks Coordinate mode.
AI Tweaks fleet cohesion also blocks Coordinate mode.
An unreadable AI Tweaks cohesion configuration blocks coordination rather than assuming that the feature is off.
RTSAssist control markers block commands to the affected ship.
Other AI Tweaks features can replace individual ship controllers, which then exclude those ships from Omega coordination.
The combat status reports a detected fleet coordinator conflict.

## Build and package

The project uses Java 17 bytecode and the Gradle wrapper.
A local Starsector installation supplies the game API and LunaLib dependencies.
The repository and ZIP do not contain those third-party JARs or extracted game source.

1. Install JDK 17 or later.
2. Set `STARSECTOR_HOME` to your Starsector installation directory.
3. Run the build from the mod directory.

```powershell
$env:STARSECTOR_HOME = 'D:/Games/StarSector'
.\gradlew.bat clean build --console=plain
python -m unittest discover -s tests -p "test_package.py"
python package.py
```

Alternatively, put `starsectorPath=D:/Games/StarSector` in an untracked `local.properties` file.
Python 3.10 or later runs the packaging script without additional packages.
The script creates `OmegaAI/` and `Omega-AI-0.1.1.zip` directly in the mod project root.
Both generated outputs are gitignored.
The staging folder contains only runtime files, including the mission briefing and LunaLib icon registration.
The ZIP contains that entire folder, so extracting it into `/mods` creates `mods/OmegaAI/mod_info.json`.
Source, tests, build tools, and documentation stay in the repository.
The script prints the ZIP's SHA-256 hash, a fingerprint of its contents.
It makes sure that the metadata, source fingerprint, compiled classes, Java version, test reports, and archive layout agree.
It never installs files into the game.

To inspect an existing ZIP, run:

```powershell
python package.py --verify Omega-AI-0.1.1.zip
```

The [changelog](CHANGELOG.md) lists release changes.
The [design](docs/DESIGN.md) describes the remaining work on survival, focus fire, exploitation, missiles, and carriers.
The [art record](docs/ART.md) contains the sprite generation prompt.
The [forum post draft](docs/FORUM-POST.txt) includes the icon and release link for later publication.
No forum topic exists yet, so the metadata does not contain a topic ID.
The registered version tracker points to this repository's raw `omega_ai.version` file and the matching GitHub release asset.
