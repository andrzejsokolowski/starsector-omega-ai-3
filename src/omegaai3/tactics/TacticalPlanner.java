package omegaai3.tactics;

import omegaai3.model.*;
import java.util.*;
import static omegaai3.model.BattleFrame.*;
import static omegaai3.tactics.TacticalIntent.Action.*;

/** One shared target allocation per side, followed by each ship's velocity and facing decision. */
public final class TacticalPlanner {
    private static final class Memory {
        String target;
        double committedUntil, retreatUntil;
    }
    private final Map<String, Memory> memory = new HashMap<>();

    public Map<String, TacticalIntent> plan(BattleFrame frame) {
        Map<String, TacticalIntent> result = new LinkedHashMap<>();
        Map<String, Double> allocated = new HashMap<>();
        Set<String> live = new HashSet<>();
        for (Ship me : frame.friends().stream().sorted(Comparator.comparingDouble(Ship::gunDps).reversed().thenComparing(Ship::id)).toList()) {
            String key = frame.side() + ":" + me.id(); live.add(key);
            if (!me.controllable() || me.incapacitated() || me.speed() <= 0 || me.usefulRange() <= 0) { memory.remove(key); continue; }
            Memory state = memory.computeIfAbsent(key, ignored -> new Memory());
            Ship target = chooseTarget(frame, me, state, allocated);
            TacticalIntent intent = decide(frame, me, target, state);
            if (intent != null) {
                result.put(me.id(), intent);
                if (target != null && intent.action() != RETREAT && intent.action() != WAIT && intent.action() != DISENGAGE)
                    allocated.merge(target.id(), me.gunDps(), Double::sum);
            }
        }
        memory.keySet().removeIf(k -> k.startsWith(frame.side() + ":") && !live.contains(k));
        return Map.copyOf(result);
    }

    private Ship chooseTarget(BattleFrame f, Ship me, Memory state, Map<String, Double> allocated) {
        Ship best = null; double bestScore = -Double.MAX_VALUE;
        for (Ship enemy : f.enemies()) {
            if (enemy.fighter() || enemy.hull() <= 0) continue;
            double gap = me.position().distance(enemy.position());
            double range = combatRange(me, enemy);
            Vec separation = enemy.position().sub(me.position());
            double fleeing = enemy.velocity().dot(separation.unit());
            // A target already in gun range is worth fighting even if it could outrun us later.
            boolean unreachable = gap > range + 120 && fleeing > me.speed() * .92;
            double score = 1000 / (1 + Math.max(0, gap - range) / Math.max(30, me.speed()) / 5);
            score += enemy.flux().level() * 300 + (1 - enemy.hullFraction()) * 180 + (enemy.incapacitated() ? 250 : 0);
            score += Math.min(200, supportingDps(f, me, enemy) / Math.max(1, enemy.gunDps()) * 100);
            // Some concentration helps; gross overkill draws the next ship to a different target.
            double assigned = allocated.getOrDefault(enemy.id(), 0d);
            score += assigned > 0 ? 100 : 0;
            score -= Math.max(0, assigned - Math.max(enemy.gunDps() * 1.5, enemy.hull() / 5)) / Math.max(1, me.gunDps()) * 150;
            if (enemy.id().equals(state.target)) score += f.time() < state.committedUntil ? 300 : 110;
            if (unreachable) score -= 1500;
            if (score > bestScore) { bestScore = score; best = enemy; }
        }
        if (best != null && !best.id().equals(state.target)) { state.target = best.id(); state.committedUntil = f.time() + 3; }
        return best;
    }

    private TacticalIntent decide(BattleFrame f, Ship me, Ship target, Memory state) {
        if (target == null) return null; // No visible target: let native deployment/objective navigation work.
        Vec radial = target.position().sub(me.position()).unit();
        double gap = target.position().distance(me.position());
        double facing = firingFacing(me, target);
        double range = combatRange(me, target);
        ThreatForecast.Pressure current = ThreatForecast.at(f, me, me.position(), me.defense().facing(), true);
        double danger = ThreatForecast.dangerSeconds(me, current, ThreatForecast.firingFlux(me, target));
        double reaction = escapeReserve(f, me, me.position());
        boolean pressure = current.total() > 0 || current.burstFlux() > 0;
        boolean emergency = pressure && (danger < reaction || me.flux().level() > .78 || me.hullFraction() < .2);
        if (emergency) state.retreatUntil = f.time() + 2;
        boolean recovering = state.retreatUntil > 0 && (f.time() < state.retreatUntil || me.flux().level() > .45 && pressure);
        if (recovering) {
            Vec away = current.away().length() > 0 ? current.away() : radial.scale(-1);
            Vec velocity = escape(f, me, away);
            // Keep front shields and useful weapons facing the immediate threat while backing out.
            double defensiveFacing = me.defense().frontShield() ? away.scale(-1).bearing() : facing;
            return intent(f, RETREAT, target, velocity, defensiveFacing, "Pressure exceeds escape reserve", danger);
        }
        state.retreatUntil = 0;

        double fleeing = target.velocity().dot(radial);
        if (gap > range + 120 && fleeing > me.speed() * .92) {
            Ship anchor = anchor(f, me, target);
            Vec velocity = anchor == null ? Vec.ZERO : toPoint(me, behind(anchor, target, me), anchor.velocity());
            return intent(f, DISENGAGE, target, avoid(f, me, velocity), facing, "Target is opening range faster than interception", danger);
        }

        // Evaluate the position we would have to occupy to bring most of the battery into range.
        Vec firingPoint = target.position().sub(radial.scale(range));
        ThreatForecast.Pressure approach = ThreatForecast.at(f, me, firingPoint, facing, false);
        double approachDanger = ThreatForecast.dangerSeconds(me, approach, me.guns().stream()
                .filter(g -> g.available() && !g.pd() && !g.missile()).mapToDouble(Gun::fluxPerSecond).sum());
        double support = supportingDps(f, me, target);
        Ship anchor = anchor(f, me, target);
        boolean fragileApproach = gap > range + 80 && approachDanger < escapeReserve(f, me, firingPoint) + 2 && support < target.gunDps() * .7
                && !target.incapacitated() && target.flux().level() < .8;
        boolean outrunning = anchor != null && (me.kind() == Kind.FRIGATE || me.kind() == Kind.DESTROYER)
                && me.position().distance(anchor.position()) > 800 + anchor.radius()
                && anchor.position().distance(target.position()) > combatRange(anchor, target) + 300
                && me.position().distance(target.position()) < anchor.position().distance(target.position())
                && gap < Math.max(range, target.usefulRange() + me.radius()) + 500;
        if (anchor != null && (fragileApproach || outrunning)) {
            return intent(f, WAIT, target, avoid(f, me, toPoint(me, behind(anchor, target, me), anchor.velocity())), facing,
                    fragileApproach ? "Firing position unsafe until support arrives" : "Heavy support has not reached engagement range", approachDanger);
        }
        if (fragileApproach) {
            // No anchor exists: stay outside the dangerous battery instead of charging it alone.
            double safeRange = Math.max(range, target.usefulRange() + me.radius() + 180);
            return intent(f, DISENGAGE, target, avoid(f, me, rangeVelocity(me, target, safeRange)), facing,
                    "Unsupported approach has insufficient flux or hull reserve", approachDanger);
        }

        boolean vulnerable = target.incapacitated() || target.flux().level() >= .75 || target.hullFraction() < .3;
        double desiredRange = range;
        // Close slightly on a vulnerable target, but retain room to withdraw from the rest of its fleet.
        if (vulnerable && danger > reaction + 2) desiredRange = Math.max(me.radius() + target.radius() + 100, range * .85);
        Vec velocity = avoid(f, me, rangeVelocity(me, target, desiredRange));
        TacticalIntent.Action action = gap > range + 60 ? (vulnerable ? PURSUE : ADVANCE) : ENGAGE;
        return intent(f, action, target, velocity, facing,
                action == PURSUE ? "Reachable vulnerable target" : action == ADVANCE ? "Closing to effective battery range" : "Maintaining effective battery range", danger);
    }

    public static double combatRange(Ship me, Ship target) {
        return Math.max(me.radius() + target.radius() + 70, me.usefulRange() * .85 + target.radius());
    }
    private static double escapeReserve(BattleFrame f, Ship me, Vec point) {
        double exitDistance = 0;
        for (Ship enemy : f.enemies()) if (!enemy.incapacitated()) for (Gun gun : enemy.guns())
            if (gun.available() && !gun.pd()) exitDistance = Math.max(exitDistance, gun.range() + me.radius() + 100 - point.distance(gun.position()));
        // Backing and strafe thrust are weaker than forward thrust; account for reversal before escape.
        return Math.min(10, 2 + me.velocity().length() / Math.max(10, me.deceleration()) + exitDistance / Math.max(20, me.speed() * .65));
    }
    public static double firingFacing(Ship me, Ship target) {
        double bearing = target.position().sub(me.position()).bearing();
        double best = bearing, bestScore = -1;
        // Test native hull orientation and each battery's broadside orientation, retaining turn continuity.
        List<Double> candidates = new ArrayList<>(List.of(me.defense().facing(), bearing));
        for (Gun gun : me.guns()) if (gun.available() && !gun.pd() && !gun.missile())
            candidates.add(bearing - Vec.angleDifference(gun.facing(), me.defense().facing()));
        for (double angle : candidates) {
            double score = 0;
            for (Gun gun : me.guns()) if (gun.available() && !gun.pd() && !gun.missile()) {
                double mount = angle + Vec.angleDifference(gun.facing(), me.defense().facing());
                if (Math.abs(Vec.angleDifference(bearing, mount)) <= gun.arc() / 2 + 2) score += gun.sustainedDps();
            }
            score *= 1 - Math.abs(Vec.angleDifference(angle, me.defense().facing())) / 180 * .08;
            if (me.defense().frontShield() && Math.abs(Vec.angleDifference(bearing, angle)) > me.defense().shieldArc() / 2) score *= .7;
            if (score > bestScore) { best = angle; bestScore = score; }
        }
        return best;
    }
    private static Vec rangeVelocity(Ship me, Ship target, double range) {
        Vec delta = target.position().sub(me.position());
        double error = delta.length() - range;
        double speed = Math.copySign(Math.min(me.speed(), Math.sqrt(2 * Math.max(1, me.deceleration()) * Math.abs(error)) * .65), error);
        if (Math.abs(error) < 25) speed = 0;
        return limit(target.velocity().add(delta.unit().scale(speed)), me.speed());
    }
    private static Vec toPoint(Ship me, Vec point, Vec feedForward) {
        Vec delta = point.sub(me.position());
        double speed = Math.min(me.speed(), Math.sqrt(2 * Math.max(1, me.deceleration()) * Math.max(0, delta.length() - 80)) * .65);
        return limit(feedForward.add(delta.unit().scale(speed)), me.speed());
    }
    private static Ship anchor(BattleFrame f, Ship me, Ship target) {
        return f.friends().stream().filter(s -> !s.id().equals(me.id()) && s.readyAnchor() && s.dp() > me.dp() * 1.5)
                .min(Comparator.comparingDouble(s -> s.position().distance(me.position()) + s.position().distance(target.position()) * .2)).orElse(null);
    }
    private static Vec behind(Ship anchor, Ship target, Ship me) {
        return anchor.position().sub(target.position().sub(anchor.position()).unit().scale(anchor.radius() + me.radius() + 180));
    }
    private static double supportingDps(BattleFrame f, Ship me, Ship target) {
        return f.friends().stream().filter(s -> !s.id().equals(me.id())).mapToDouble(s -> s.supportDps(target.position(), target.radius(), 2)).sum();
    }
    private static Vec escape(BattleFrame f, Ship me, Vec away) {
        Vec best = away.scale(me.speed()); double bestScore = -Double.MAX_VALUE;
        for (int n = -3; n <= 3; n++) {
            double angle = Math.toRadians(away.bearing() + n * 30);
            Vec v = new Vec(Math.cos(angle), Math.sin(angle)).scale(me.speed());
            Vec position = me.position().add(v.scale(2));
            double score = v.unit().dot(away) * 1000 - obstaclePenalty(f, me, v);
            score -= position.distance(position.clamp(f.width() / 2, f.height() / 2, me.radius() + 100)) * 10;
            for (Ship ally : f.friends()) if (!ally.id().equals(me.id()) && ally.readyAnchor())
                score += Math.max(0, 500 - position.distance(ally.position())) * .2;
            if (score > bestScore) { bestScore = score; best = v; }
        }
        return avoid(f, me, best);
    }
    private static Vec avoid(BattleFrame f, Ship me, Vec desired) {
        Vec future = me.position().add(desired.scale(2));
        Vec bounded = future.clamp(f.width() / 2, f.height() / 2, me.radius() + 100);
        desired = desired.add(bounded.sub(future).scale(.5));
        if (obstaclePenalty(f, me, desired) <= 0) return limit(desired, me.speed());
        Vec best = Vec.ZERO; double bestCost = obstaclePenalty(f, me, best) + desired.length();
        for (int n = -3; n <= 3; n++) {
            double angle = Math.toRadians(desired.bearing() + n * 30);
            Vec v = new Vec(Math.cos(angle), Math.sin(angle)).scale(desired.length());
            double cost = obstaclePenalty(f, me, v) + v.distance(desired);
            if (cost < bestCost) { bestCost = cost; best = v; }
        }
        return limit(best, me.speed());
    }
    private static double obstaclePenalty(BattleFrame f, Ship me, Vec velocity) {
        double cost = 0;
        for (Obstacle o : f.obstacles()) {
            if (o.id().equals(me.id()) || me.position().distance(o.position()) > me.speed() * 3 + me.radius() + o.radius() + 200) continue;
            Vec separation = me.position().sub(o.position()), relative = velocity.sub(o.velocity());
            double t = Math.max(0, Math.min(2, -separation.dot(relative) / Math.max(1, relative.dot(relative))));
            double clearance = separation.add(relative.scale(t)).length() - me.radius() - o.radius() - 60;
            if (clearance < 0) cost += -clearance * 20;
        }
        return cost;
    }
    private static Vec limit(Vec v, double max) { return v.length() > max ? v.unit().scale(max) : v; }
    private static TacticalIntent intent(BattleFrame f, TacticalIntent.Action action, Ship target, Vec velocity, double facing, String why, double danger) {
        return new TacticalIntent(action, target.id(), velocity, facing, f.time() + .8, why, danger);
    }
}
