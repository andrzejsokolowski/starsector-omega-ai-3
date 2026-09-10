package omegaai3;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.lwjgl.util.vector.Vector2f;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Function;

/** API-boundary test double; it does not simulate the game's pilot or physics. */
final class FakeCombat {
    static <T> T mock(Class<T> type, Map<String, Function<Object[], Object>> handlers) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, method, args) -> {
            if (method.getName().equals("equals")) return self == args[0];
            if (method.getName().equals("hashCode")) return System.identityHashCode(self);
            if (method.getName().equals("toString")) return "Fake " + type.getSimpleName();
            var handler = handlers.get(method.getName());
            if (handler != null) return handler.apply(args == null ? new Object[0] : args);
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == int.class) return 0;
            if (method.getReturnType() == float.class) return 0f;
            if (method.getReturnType() == double.class) return 0d;
            return null;
        }));
    }
    static final class Unit {
        final Map<String, Function<Object[], Object>> calls = new HashMap<>();
        final Vector2f location = new Vector2f(), velocity = new Vector2f();
        final List<WeaponAPI> weapons = new ArrayList<>();
        final Map<String, Object> data = new HashMap<>();
        boolean alive = true, retreating, ally;
        int owner;
        final ShipAPI ship;
        final DeployedFleetMemberAPI member;
        Unit(String id) {
            var stats = mock(MutableShipStatsAPI.class, Map.of(
                    "getFluxDissipation", a -> new MutableStat(300), "getVentRateMult", a -> new MutableStat(1),
                    "getHardFluxDissipationFraction", a -> new MutableStat(0), "getShieldDamageTakenMult", a -> new MutableStat(1)));
            var flux = mock(FluxTrackerAPI.class, Map.of("getMaxFlux", a -> 10000f, "getTimeToVent", a -> 42f));
            calls.put("getId", a -> id); calls.put("getName", a -> id);
            calls.put("getLocation", a -> location); calls.put("getVelocity", a -> velocity);
            calls.put("isAlive", a -> alive); calls.put("isRetreating", a -> retreating); calls.put("isAlly", a -> ally);
            calls.put("getOwner", a -> owner); calls.put("getAllWeapons", a -> weapons);
            calls.put("getMaxHitpoints", a -> 5000f); calls.put("getHitpoints", a -> 5000f);
            calls.put("getMaxSpeed", a -> 80f); calls.put("getAcceleration", a -> 100f);
            calls.put("getDeceleration", a -> 50f); calls.put("getCollisionRadius", a -> 50f);
            calls.put("getFluxTracker", a -> flux); calls.put("getMutableStats", a -> stats);
            calls.put("getCustomData", a -> data);
            calls.put("getDeployCost", a -> .15f);
            calls.put("getFleetMember", a -> mock(FleetMemberAPI.class, Map.of("getDeploymentPointsCost", unused -> 40f)));
            ship = mock(ShipAPI.class, calls);
            member = mock(DeployedFleetMemberAPI.class, Map.of("getShip", a -> ship));
        }
    }
    static final class Waypoint implements AssignmentTargetAPI {
        final Vector2f location;
        Waypoint(Vector2f location) { this.location = new Vector2f(location); }
        public Vector2f getLocation() { return location; }
        public Vector2f getVelocity() { return new Vector2f(); }
        public int getOwner() { return 0; }
    }
    static final class Assignment implements AssignmentInfo {
        CombatAssignmentType type;
        final AssignmentTargetAPI target;
        final List<DeployedFleetMemberAPI> members = new ArrayList<>();
        Assignment(CombatAssignmentType type, AssignmentTargetAPI target) { this.type = type; this.target = target; }
        public CombatAssignmentType getType() { return type; }
        public AssignmentTargetAPI getTarget() { return target; }
        public List<DeployedFleetMemberAPI> getAssignedMembers() { return members; }
    }
    final class Tasks {
        final List<AssignmentInfo> all = new ArrayList<>();
        final Map<ShipAPI, AssignmentInfo> current = new IdentityHashMap<>();
        boolean fullAssault, fullRetreat, reject, failRead, throwAfterGive, failRemove;
        int created, removed, assigned;
        final CombatTaskManagerAPI api = mock(CombatTaskManagerAPI.class, Map.ofEntries(
                Map.entry("getAssignmentFor", a -> { if (failRead) throw new IllegalStateException("No order state"); return current.get(a[0]); }),
                Map.entry("getAllAssignments", a -> new ArrayList<>(all)),
                Map.entry("isFullAssault", a -> fullAssault), Map.entry("isInFullRetreat", a -> fullRetreat),
                Map.entry("createWaypoint2", a -> { if (!Boolean.FALSE.equals(a[1])) throw new AssertionError("Unexpected ally mode"); return new Waypoint((Vector2f) a[0]); }),
                Map.entry("createAssignment", a -> {
                    if (!Boolean.FALSE.equals(a[2])) throw new AssertionError("Command points requested");
                    created++; var task = new Assignment((CombatAssignmentType) a[0], (AssignmentTargetAPI) a[1]); all.add(task); return task;
                }),
                Map.entry("giveAssignment", a -> {
                    if (!Boolean.FALSE.equals(a[2])) throw new AssertionError("Command points requested");
                    assigned++;
                    if (!reject) set((DeployedFleetMemberAPI) a[0], (AssignmentInfo) a[1]);
                    if (throwAfterGive) throw new IllegalStateException("Assignment failed after application");
                    return null;
                }),
                Map.entry("removeAssignment", a -> {
                    if (failRemove) throw new IllegalStateException("Cleanup temporarily unavailable");
                    removed++; all.remove(a[0]); current.entrySet().removeIf(e -> e.getValue() == a[0]); return null;
                })
        ));
        void set(DeployedFleetMemberAPI member, AssignmentInfo task) {
            AssignmentInfo old = current.put(member.getShip(), task);
            if (old != null) old.getAssignedMembers().remove(member);
            if (!task.getAssignedMembers().contains(member)) task.getAssignedMembers().add(member);
            if (!all.contains(task)) all.add(task);
        }
    }
    final Tasks player = new Tasks(), enemy = new Tasks();
    final List<ShipAPI> ships = new ArrayList<>();
    final Map<ShipAPI, DeployedFleetMemberAPI> members = new IdentityHashMap<>();
    final Set<Object> deleted = Collections.newSetFromMap(new IdentityHashMap<>());
    final Set<CombatEntityAPI> hidden = Collections.newSetFromMap(new IdentityHashMap<>());
    ShipAPI playerShip;
    boolean autopilot = true;
    final CombatFleetManagerAPI playerManager = manager(player), enemyManager = manager(enemy);
    final CombatEngineAPI engine = mock(CombatEngineAPI.class, Map.ofEntries(
            Map.entry("getFleetManager", a -> ((Integer) a[0]) == 0 ? playerManager : enemyManager),
            Map.entry("getPlayerShip", a -> playerShip), Map.entry("isUIAutopilotOn", a -> autopilot),
            Map.entry("removeObject", a -> { deleted.add(a[0]); return null; }), Map.entry("getShips", a -> ships),
            Map.entry("isAwareOf", a -> !hidden.contains(a[1])), Map.entry("getProjectiles", a -> List.of()),
            Map.entry("getMissiles", a -> List.of()), Map.entry("getMapWidth", a -> 20000f), Map.entry("getMapHeight", a -> 20000f)
    ));
    private CombatFleetManagerAPI manager(Tasks tasks) {
        return mock(CombatFleetManagerAPI.class, Map.of("getTaskManager", a -> tasks.api, "getDeployedFleetMember", a -> members.get(a[0])));
    }
    Unit add(String id) { Unit u = new Unit(id); ships.add(u.ship); members.put(u.ship, u.member); return u; }
}
