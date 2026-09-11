package omegaai3.model;

import static omegaai3.model.BattleFrame.*;

/** Small explicit models used by the tactical planner; they never trigger shield/vent commands. */
public final class CombatMath {
    private CombatMath() {}
    public record FluxResult(double total, double hard, boolean overloadRisk) {}

    public static FluxResult fluxAfter(Flux start, double weaponRate, double softHitRate,
                                       double hardHitRate, double seconds) {
        if (seconds <= 0) return new FluxResult(start.total(), start.hard(), false);
        double total = start.total(), hard = start.hard();
        boolean overload = false;
        int steps = Math.max(1, (int) Math.ceil(seconds / .05));
        double dt = seconds / steps;
        for (int i = 0; i < steps; i++) {
            double incomingHard = Math.max(0, hardHitRate) * dt;
            double incomingSoft = (Math.max(0, weaponRate) + Math.max(0, softHitRate)
                    + (start.shieldUp() ? start.upkeep() : 0)) * dt;
            double hardRemoval = start.dissipation() * (start.shieldUp() ? start.hardDissipationFraction() : 1) * dt;
            hard = Math.max(0, hard + incomingHard - Math.min(start.dissipation() * dt, hardRemoval));
            // Dissipation is one budget; the floor prevents fictitious removal of hard flux.
            total = Math.max(hard, total + incomingHard + incomingSoft - start.dissipation() * dt);
            overload |= incomingHard > 0 && total >= start.capacity();
        }
        return new FluxResult(total, hard, overload);
    }

    /** Constant-speed interception of a moving firing envelope, not distance / pursuer speed. */
    public static double interceptTime(Vec relativePosition, Vec targetVelocity, double pursuerSpeed, double reach) {
        double c = relativePosition.dot(relativePosition) - reach * reach;
        if (c <= 0) return 0;
        double a = targetVelocity.dot(targetVelocity) - pursuerSpeed * pursuerSpeed;
        double b = 2 * (relativePosition.dot(targetVelocity) - pursuerSpeed * reach);
        if (Math.abs(a) < 1e-8) return b < -1e-8 ? -c / b : Double.POSITIVE_INFINITY;
        double discriminant = b * b - 4 * a * c;
        if (discriminant < 0) return Double.POSITIVE_INFINITY;
        double root = Math.sqrt(discriminant);
        double first = (-b - root) / (2 * a), second = (-b + root) / (2 * a);
        double result = Double.POSITIVE_INFINITY;
        if (first >= 0) result = first;
        if (second >= 0) result = Math.min(result, second);
        return result;
    }

    public static double impactTime(Shot shot, Ship ship, double horizon) {
        Vec p = shot.position().sub(ship.position()), v = shot.velocity().sub(ship.velocity());
        double time = interceptTime(p, v, 0, ship.radius() + shot.radius());
        if (shot.guided()) {
            // Conservative reachability bound, explicitly not a predicted guided trajectory.
            time = Math.min(time, Math.max(0, p.length() - ship.radius() - shot.radius())
                    / Math.max(1, shot.maxSpeed() + ship.velocity().length()));
        }
        return time <= horizon && time <= shot.remainingLife() ? time : Double.POSITIVE_INFINITY;
    }

    public static double stoppingDistance(double speed, double deceleration) {
        return deceleration > 0 ? speed * speed / (2 * deceleration) : Double.POSITIVE_INFINITY;
    }
}
