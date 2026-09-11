package omegaai3.runtime;

import com.fs.starfarer.combat.ai.movement.BasicEngineAI;
import com.fs.starfarer.combat.entities.Ship;
import omegaai3.tactics.TacticalIntent;
import omegaai3.model.Vec;
import java.util.*;

/** The only version-specific command bridge. Pinned to Starsector 0.98a-RC8. */
public final class Rc8Helm {
    private static final Set<String> MOVEMENT = Set.of("ACCELERATE", "ACCELERATE_BACKWARDS", "DECELERATE",
            "STRAFE_LEFT", "STRAFE_RIGHT", "TURN_LEFT", "TURN_RIGHT", "TRIGGER_AUTO_SLOWDOWN");
    private final Ship ship;
    private final BasicEngineAI engines;
    public Rc8Helm(Ship ship) { this.ship = ship; engines = new BasicEngineAI(ship); }
    public static boolean movement(Ship.Oo command) { return MOVEMENT.contains(command.\u00d200000.name()); }
    public static boolean system(Ship.Oo command) { return "USE_SYSTEM".equals(command.\u00d200000.name()); }
    public static boolean supported(String version) { return "0.98a-RC8".equals(version) || "Starsector 0.98a-RC8".equals(version); }
    public boolean blocked() { return ship.getBlockedCommands().stream().anyMatch(c -> MOVEMENT.contains(c.name())); }

    public void steer(TacticalIntent intent, float amount, Set<Ship.Oo> before) {
        List<Ship.Oo> commands = ship.getCommands();
        List<Ship.Oo> removed = new ArrayList<>();
        for (Iterator<Ship.Oo> it = commands.iterator(); it.hasNext();) {
            Ship.Oo command = it.next();
            if (!before.contains(command) && movement(command)) { removed.add(command); it.remove(); }
        }
        Set<Ship.Oo> retained = Collections.newSetFromMap(new IdentityHashMap<>()); retained.addAll(commands);
        try {
            engines.setDesiredFacing((float) intent.facing());
            // RC8's explicit-facing helper ignores requested speed for ordinary ships and maximizes
            // velocity along its heading. Give it the velocity ERROR, not the desired travel heading.
            Vec error = intent.velocity().sub(new Vec(ship.getVelocity().x, ship.getVelocity().y));
            double tolerance = Math.max(1, ship.getAcceleration() * Math.max(.001, amount) * .75);
            engines.setDesiredHeading(error.length() <= tolerance ? Float.MAX_VALUE : (float) error.bearing(), 1f);
            engines.advance(amount);
        } catch (RuntimeException | LinkageError e) {
            commands.removeIf(c -> !retained.contains(c) && movement(c));
            commands.addAll(removed);
            throw e;
        }
    }
}
