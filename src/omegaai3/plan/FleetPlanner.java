package omegaai3.plan;

import omegaai3.model.BattleFrame;
import omegaai3.model.CombatMath;
import omegaai3.model.Vec;
import java.util.*;
import static omegaai3.model.BattleFrame.*;

/** First milestone: bounded regrouping proposals, not an independent ship pilot. */
public final class FleetPlanner {
    public enum Reason { BREAK_PURSUIT, WAIT_FOR_SUPPORT, REJOIN_GROUP }
    public record Proposal(String shipId, String anchorId, Vec destination, Reason reason, String detail) {}
    private record Key(int side, String ship) {}
    private record Chase(String target, double started, double gap) {}
    private final Map<Key, Chase> chases = new HashMap<>();

    public List<Proposal> plan(BattleFrame frame) {
        Set<Key> present = new HashSet<>();
        for (Ship ship : frame.friends()) present.add(new Key(frame.side(), ship.id()));
        chases.keySet().removeIf(k -> k.side == frame.side() && !present.contains(k));
        if (frame.enemies().isEmpty()) {
            chases.keySet().removeIf(k -> k.side == frame.side());
            return List.of();
        }
        List<Proposal> proposals = new ArrayList<>();
        for (Ship ship : frame.friends().stream().sorted(Comparator.comparing(Ship::id)).toList()) {
            if (!ship.controllable() || ship.incapacitated() || ship.specialist() || ship.gunDps() <= 0) {
                chases.remove(new Key(frame.side(), ship.id()));
                continue;
            }
            Proposal proposal = decide(frame, ship, proposals);
            if (proposal != null) proposals.add(proposal);
        }
        return List.copyOf(proposals);
    }

    private Proposal decide(BattleFrame frame, Ship me, List<Proposal> allocated) {
        Ship target = frame.enemies().stream().filter(s -> s.id().equals(me.targetId())).findFirst().orElse(null);
        Ship nearest = frame.enemies().stream().filter(s -> !s.fighter())
                .min(Comparator.comparingDouble(s -> s.position().distance(me.position()))).orElse(null);
        if (nearest == null) return null;
        Ship anchor = anchorFor(frame, me);
        if (anchor == null) return null;
        double separation = me.position().distance(anchor.position());
        double leash = Math.max(850, Math.min(2000, .7 * (me.usefulRange() + anchor.usefulRange()) + me.radius() + anchor.radius()));
        double gap = target == null ? 0 : firingGap(me, target);
        boolean futile = stalledChase(frame, me, target, gap);
        if (separation <= leash) return null;
        Vec point = regroupPoint(frame, me, anchor, allocated);
        if (point == null) return null;

        // Productive contact always defeats a generic leash. A heavy ship can keep shooting.
        if (target != null && me.supportDps(target.position(), target.radius(), 1) > me.gunDps() * .25) return null;
        if (target != null && futile) {
            return new Proposal(me.id(), anchor.id(), point, Reason.BREAK_PURSUIT,
                    "No useful closing progress for 6s; rejoin " + anchor.name());
        }

        Ship approach = target != null ? target : nearest;
        double ownArrival = arrival(me, approach);
        double anchorArrival = arrival(anchor, approach);
        double support = frame.friends().stream().filter(s -> !s.id().equals(me.id()))
                .mapToDouble(s -> s.supportDps(approach.position(), approach.radius(), 2)).sum();
        boolean small = me.kind() == Kind.FRIGATE || me.kind() == Kind.DESTROYER;
        boolean outranged = approach.usefulRange() > me.usefulRange() + 250;
        boolean heavyOpponent = approach.dp() >= me.dp() * 2;
        if (small && anchor.dp() >= me.dp() * 1.5 && firingGap(me, approach) > 150
                && ownArrival < 8 && anchorArrival > ownArrival + 4 && (outranged || heavyOpponent)
                && support < Math.max(1, approach.gunDps() * .25)) {
            return new Proposal(me.id(), anchor.id(), point, Reason.WAIT_FOR_SUPPORT,
                    "Arriving before heavier support; stage beside " + anchor.name());
        }

        // Only draw idle stragglers toward a group already closer to visible combat.
        if (target == null && firingGap(me, nearest) > 600
                && anchor.position().distance(nearest.position()) + 500 < me.position().distance(nearest.position())) {
            return new Proposal(me.id(), anchor.id(), point, Reason.REJOIN_GROUP,
                    "No combat target; rejoin the nearer battle group");
        }
        return null;
    }

    private boolean stalledChase(BattleFrame frame, Ship me, Ship target, double gap) {
        Key key = new Key(frame.side(), me.id());
        if (target == null || gap <= 150) { chases.remove(key); return false; }
        Chase prior = chases.get(key);
        if (prior == null || !prior.target.equals(target.id()) || gap < prior.gap - 100) {
            chases.put(key, new Chase(target.id(), frame.time(), gap));
            return false;
        }
        double radialClosing = me.velocity().sub(target.velocity()).dot(target.position().sub(me.position()).unit());
        return frame.time() - prior.started >= 6 && radialClosing <= 5 && arrival(me, target) > 12;
    }

    private Ship anchorFor(BattleFrame frame, Ship me) {
        List<Ship> candidates = frame.friends().stream().filter(s -> !s.id().equals(me.id()) && s.readyAnchor()
                        && s.dp() >= me.dp() * .75 && s.gunDps() >= me.gunDps() * .35)
                .toList();
        List<Ship> heavier = candidates.stream().filter(s -> s.dp() >= me.dp() * 1.5).toList();
        // Two fast scouts arriving together are not a substitute for their delayed heavy support.
        return (heavier.isEmpty() ? candidates : heavier).stream()
                .min(Comparator.comparingDouble(s -> s.position().distance(me.position()) / Math.sqrt(Math.max(1, s.dp()))))
                .orElse(null);
    }

    private Vec regroupPoint(BattleFrame frame, Ship me, Ship anchor, List<Proposal> allocated) {
        Vec radial = me.position().sub(anchor.position()).unit();
        if (radial.length() == 0) radial = new Vec(1, 0);
        double spacing = anchor.radius() + me.radius() + 240;
        for (double angle : new double[]{0, 45, -45, 90, -90, 135, -135, 180}) {
            double r = Math.toRadians(angle);
            Vec dir = new Vec(radial.x() * Math.cos(r) - radial.y() * Math.sin(r), radial.x() * Math.sin(r) + radial.y() * Math.cos(r));
            Vec point = anchor.position().add(dir.scale(spacing)).clamp(frame.width() / 2, frame.height() / 2, me.radius() + 200);
            boolean blocked = frame.friends().stream().filter(s -> !s.id().equals(me.id()) && !s.fighter())
                    .anyMatch(s -> point.distance(s.position()) < me.radius() + s.radius() + 100);
            if (blocked || allocated.stream().anyMatch(p -> p.destination().distance(point) < 2 * me.radius() + 160)) continue;
            boolean crossesEnemy = frame.enemies().stream().filter(s -> !s.fighter()).anyMatch(enemy -> {
                double clearance = enemy.radius() + me.radius() + 150;
                double pathDistance = distanceToSegment(enemy.position(), me.position(), point);
                if (pathDistance < clearance) return true;
                double range = enemy.usefulRange() + enemy.radius() + me.radius();
                double startDistance = me.position().distance(enemy.position());
                double endDistance = point.distance(enemy.position());
                // Regrouping must not pull an exposed ship deeper into another enemy's guns.
                if (endDistance < range && endDistance < startDistance - 100) return true;
                // Don't enter and exit an enemy gun envelope en route to an otherwise safe point.
                return startDistance > range && endDistance > range && pathDistance < range;
            });
            if (!crossesEnemy) return point;
        }
        return null;
    }

    public static double firingGap(Ship me, Ship target) {
        return Math.max(0, me.position().distance(target.position()) - me.usefulRange() - me.radius() - target.radius());
    }
    public static double arrival(Ship me, Ship target) {
        if (me.speed() <= 0) return firingGap(me, target) == 0 ? 0 : Double.POSITIVE_INFINITY;
        double coast = CombatMath.interceptTime(target.position().sub(me.position()), target.velocity(), me.speed(),
                me.usefulRange() + me.radius() + target.radius());
        double accelerationDelay = Math.max(0, me.speed() - me.velocity().length()) / Math.max(1, me.acceleration());
        return coast == 0 ? 0 : coast + accelerationDelay;
    }
    private static double distanceToSegment(Vec p, Vec a, Vec b) {
        Vec ab = b.sub(a);
        double along = Math.max(0, Math.min(1, p.sub(a).dot(ab) / Math.max(1, ab.dot(ab))));
        return p.distance(a.add(ab.scale(along)));
    }
}
