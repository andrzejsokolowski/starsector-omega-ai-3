package omegaai3.runtime;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import omegaai3.Options;
import omegaai3.model.Vec;
import omegaai3.plan.FleetPlanner.Proposal;
import org.lwjgl.util.vector.Vector2f;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Function;

/** Owns only individually created rally assignments. Never cancels an unrelated task. */
public final class OrderBook {
    public enum Result { CREATED, REFRESHED, BLOCKED, REJECTED }
    private static final double EXPIRY = 1.25, MAX_DURATION = 12, STALL = 6, COOLDOWN = 8;
    private final CombatEngineAPI engine;
    private final Function<ShipAPI, String> pilotProblem;
    private final Map<ShipAPI, Lease> leases = new IdentityHashMap<>();
    private final Map<ShipAPI, Double> cooldowns = new IdentityHashMap<>();

    private static final class Lease {
        final ShipAPI ship;
        final CombatTaskManagerAPI tasks;
        final AssignmentInfo assignment;
        final AssignmentTargetAPI waypoint;
        final double created;
        final String purpose;
        Vec expected;
        double expires, progressAt, distance;
        Lease(ShipAPI ship, CombatTaskManagerAPI tasks, AssignmentInfo assignment, AssignmentTargetAPI waypoint, Vec expected, double now, String purpose) {
            this.ship = ship; this.tasks = tasks; this.assignment = assignment; this.waypoint = waypoint;
            this.expected = expected; created = now; progressAt = now; expires = now + EXPIRY;
            this.purpose = purpose;
            distance = expected.distance(new Vec(ship.getLocation().x, ship.getLocation().y));
        }
    }

    public OrderBook(CombatEngineAPI engine) { this.engine = engine; pilotProblem = ship -> ControlGate.blockReason(engine, ship); }
    public OrderBook(CombatEngineAPI engine, Predicate<ShipAPI> pilotAllowed) {
        this.engine = engine; pilotProblem = ship -> pilotAllowed.test(ship) ? "" : "Pilot retains control";
    }
    public int active() { return leases.size(); }

    public boolean canManage(ShipAPI ship, Options options, double now) {
        return blockReason(ship, options, now).isEmpty();
    }

    public String blockReason(ShipAPI ship, Options options, double now) {
        try {
            if (!options.enabled()) return "Omega disabled";
            if (!options.includes(ship.getOwner())) return "Fleet excluded in settings";
            String pilot = pilotProblem.apply(ship);
            if (!pilot.isEmpty()) return pilot;
            if (engine.getPlayerShip() == ship && !engine.isUIAutopilotOn()) return "Manual player control";
            if (ship.getOwner() < 0 || ship.getOwner() > 1 || ship.isAlly() || !ship.isAlive() || ship.isRetreating()) return "Ship unavailable";
            if (cooldowns.getOrDefault(ship, 0d) > now) return "Regroup cooldown";
            CombatTaskManagerAPI tasks = engine.getFleetManager(ship.getOwner()).getTaskManager(false);
            if (globalDirective(tasks)) return "Fleet directive: avoid, ignore, assault, or retreat";
            if (engine.getFleetManager(1 - ship.getOwner()).getTaskManager(false).isInFullRetreat()) return "Enemy fleet retreating";
            AssignmentInfo current = tasks.getAssignmentFor(ship);
            Lease lease = leases.get(ship);
            return current == null || lease != null && unchanged(lease, current) ? "" : "Existing " + current.getType() + " order";
        } catch (RuntimeException e) { return "Order or controller state unreadable"; }
    }

    public String activePurpose(ShipAPI ship) { Lease lease = leases.get(ship); return lease == null ? "" : lease.purpose; }
    public String assignmentName(ShipAPI ship) {
        try {
            AssignmentInfo current = engine.getFleetManager(ship.getOwner()).getTaskManager(false).getAssignmentFor(ship);
            return current == null ? "none" : current.getType().name();
        } catch (RuntimeException e) { return "unreadable"; }
    }

    private boolean globalDirective(CombatTaskManagerAPI tasks) {
        if (tasks.isInFullRetreat() || tasks.isFullAssault()) return true;
        for (AssignmentInfo a : tasks.getAllAssignments()) {
            if (a.getType() == CombatAssignmentType.AVOID || a.getType() == CombatAssignmentType.IGNORE) return true;
        }
        return false;
    }

    /** Run even on paused frames: the player can change an order or take control while paused. */
    public void reconcile(Options options, double now) {
        cooldowns.entrySet().removeIf(e -> e.getValue() <= now || !e.getKey().isAlive() || e.getKey().isExpired());
        for (Lease lease : new ArrayList<>(leases.values())) {
            AssignmentInfo current = lease.tasks.getAssignmentFor(lease.ship);
            if (!unchanged(lease, current)) {
                // An edited/shared waypoint is now someone else's instruction. Do not remove it.
                disown(lease, now);
                continue;
            }
            double distance = lease.expected.distance(new Vec(lease.ship.getLocation().x, lease.ship.getLocation().y));
            if (distance < lease.distance - 50) { lease.distance = distance; lease.progressAt = now; }
            if (!options.mayIssueOrders() || !canManage(lease.ship, options, now) || now >= lease.expires
                    || now - lease.created >= MAX_DURATION || now - lease.progressAt >= STALL || distance <= 220) {
                release(lease, now);
            }
        }
    }

    public Result apply(ShipAPI ship, Proposal proposal, Options options, double now) {
        if (!options.mayIssueOrders() || !canManage(ship, options, now)) return Result.BLOCKED;
        Lease existing = leases.get(ship);
        if (existing != null) {
            if (!unchanged(existing, existing.tasks.getAssignmentFor(ship))) { disown(existing, now); return Result.BLOCKED; }
            // Keep a fixed short commitment; do not make the goal chase the moving anchor every tick.
            existing.expires = now + EXPIRY;
            return Result.REFRESHED;
        }
        CombatTaskManagerAPI tasks = engine.getFleetManager(ship.getOwner()).getTaskManager(false);
        DeployedFleetMemberAPI member = engine.getFleetManager(ship.getOwner()).getDeployedFleetMember(ship);
        if (member == null || member.getShip() != ship) return Result.BLOCKED;
        Vec point = proposal.destination();
        AssignmentTargetAPI waypoint = tasks.createWaypoint2(new Vector2f((float) point.x(), (float) point.y()), false);
        AssignmentInfo assignment = null;
        try {
            assignment = tasks.createAssignment(CombatAssignmentType.RALLY_TASK_FORCE, waypoint, false);
            tasks.setAssignmentWeight(assignment, 0);
            Lease lease = new Lease(ship, tasks, assignment, waypoint,
                    new Vec(waypoint.getLocation().x, waypoint.getLocation().y), now, proposal.reason() + ": " + proposal.detail());
            leases.put(ship, lease); // retain ownership before any further call can fail
            tasks.giveAssignment(member, assignment, false);
            if (unchanged(lease, tasks.getAssignmentFor(ship))) return Result.CREATED;
            disown(lease, now);
            return Result.REJECTED;
        } catch (RuntimeException e) {
            if (!leases.containsKey(ship)) cleanupUnused(tasks, assignment, waypoint);
            throw e;
        }
    }

    public void releaseAll(double now) {
        for (Lease lease : new ArrayList<>(leases.values())) {
            if (unchanged(lease, lease.tasks.getAssignmentFor(lease.ship))) release(lease, now);
            else disown(lease, now);
        }
    }

    private boolean unchanged(Lease lease, AssignmentInfo current) {
        if (current != lease.assignment || current.getType() != CombatAssignmentType.RALLY_TASK_FORCE || current.getTarget() != lease.waypoint) return false;
        Vector2f p = lease.waypoint.getLocation();
        if (lease.expected.distance(new Vec(p.x, p.y)) > 1) return false;
        List<DeployedFleetMemberAPI> members = current.getAssignedMembers();
        return members.size() == 1 && members.get(0).getShip() == lease.ship;
    }

    private void release(Lease lease, double now) {
        // Recheck at the write boundary, not merely at the previous planning tick.
        if (!unchanged(lease, lease.tasks.getAssignmentFor(lease.ship))) { disown(lease, now); return; }
        lease.tasks.removeAssignment(lease.assignment);
        cleanupUnused(lease.tasks, null, lease.waypoint);
        leases.remove(lease.ship);
        cooldowns.put(lease.ship, now + COOLDOWN);
    }

    private void disown(Lease lease, double now) {
        cleanupUnused(lease.tasks, lease.assignment, lease.waypoint);
        leases.remove(lease.ship);
        cooldowns.put(lease.ship, now + COOLDOWN);
    }

    private void cleanupUnused(CombatTaskManagerAPI tasks, AssignmentInfo assignment, AssignmentTargetAPI waypoint) {
        if (assignment != null && assignment.getAssignedMembers().isEmpty()) tasks.removeAssignment(assignment);
        for (AssignmentInfo a : tasks.getAllAssignments()) if (a.getTarget() == waypoint) return;
        engine.removeObject(waypoint);
    }
}
