package omegaai3.runtime;

import com.fs.starfarer.api.combat.*;
import omegaai3.model.BattleFrame;
import omegaai3.model.Vec;
import org.lwjgl.util.vector.Vector2f;
import java.util.*;
import java.util.function.Predicate;
import static omegaai3.model.BattleFrame.*;

public final class Observer {
    public record Read(BattleFrame frame, Map<String, ShipAPI> ships, int skipped) {}

    public Read capture(CombatEngineAPI engine, int side, double time, Predicate<ShipAPI> eligible) {
        return capture(engine, side, time, eligible, true);
    }

    public Read capture(CombatEngineAPI engine, int side, double time, Predicate<ShipAPI> eligible, boolean includeProjectiles) {
        List<Ship> friends = new ArrayList<>(), enemies = new ArrayList<>();
        List<Obstacle> obstacles = new ArrayList<>();
        Map<String, ShipAPI> originals = new HashMap<>();
        Set<String> visibleIds = new HashSet<>();
        List<ShipAPI> visible = new ArrayList<>();
        int skipped = 0;
        for (ShipAPI ship : engine.getShips()) {
            try {
                if (!ship.isExpired() && !ship.isFighter() && (ship.getOwner() == side || engine.isAwareOf(side, ship))) {
                    obstacles.add(new Obstacle(key(ship), vec(ship.getLocation()), vec(ship.getVelocity()), positive(ship.getCollisionRadius())));
                }
                if (!ship.isAlive() || ship.isHulk() || ship.isExpired() || ship.isShuttlePod() || ship.isPiece()
                        || ship.getOwner() < 0 || ship.getOwner() > 1) continue;
                // Never read the live hidden enemy's equipment, target, flux, or position.
                if (ship.getOwner() != side && !engine.isAwareOf(side, ship)) continue;
                visible.add(ship); visibleIds.add(key(ship));
            } catch (RuntimeException e) { skipped++; }
        }
        for (ShipAPI ship : visible) {
            try {
                Ship read = readShip(ship, ship.getOwner() == side && eligible.test(ship), visibleIds);
                if (ship.getOwner() == side) { friends.add(read); originals.put(read.id(), ship); }
                else enemies.add(read);
            } catch (RuntimeException e) { skipped++; }
        }
        List<Shot> shots = new ArrayList<>();
        Set<DamagingProjectileAPI> projectiles = Collections.newSetFromMap(new IdentityHashMap<>());
        if (includeProjectiles) {
            projectiles.addAll(engine.getProjectiles());
            projectiles.addAll(engine.getMissiles());
        }
        for (DamagingProjectileAPI p : projectiles) {
            try {
                if (p.getOwner() == side || p.isExpired() || p.didDamage() || p.isFading() || !engine.isAwareOf(side, p)) continue;
                if (p instanceof MissileAPI m && (m.isFlare() || m.isDecoyFlare())) continue;
                boolean guided = p instanceof MissileAPI m && m.isGuided();
                double speed = p instanceof MissileAPI m ? m.getMaxSpeed() : p.getVelocity().length();
                double life = p instanceof MissileAPI m ? Math.max(0, m.getMaxFlightTime() - m.getFlightTime()) : Double.POSITIVE_INFINITY;
                shots.add(new Shot(vec(p.getLocation()), vec(p.getVelocity()), positive(p.getCollisionRadius()),
                        positive(p.getDamageAmount()), damage(p.getDamageType()), p.getDamage().isSoftFlux(), guided, positive(speed), life));
            } catch (RuntimeException e) { skipped++; }
        }
        return new Read(new BattleFrame(side, time, engine.getMapWidth(), engine.getMapHeight(), friends, enemies, shots, obstacles), Map.copyOf(originals), skipped);
    }

    private Ship readShip(ShipAPI s, boolean controllable, Set<String> visibleIds) {
        boolean incapacitated = s.getFluxTracker().isOverloadedOrVenting() || s.isPhased();
        boolean specialist = s.isStation() || s.isStationModule() || s.getPhaseCloak() != null || s.hasLaunchBays() || s.isNonCombat(false);
        FluxTrackerAPI ft = s.getFluxTracker();
        var stats = s.getMutableStats();
        ShieldAPI shield = s.getShield();
        boolean hasShield = shield != null && (shield.getType() == ShieldAPI.ShieldType.FRONT || shield.getType() == ShieldAPI.ShieldType.OMNI);
        Flux flux = new Flux(positive(ft.getCurrFlux()), positive(ft.getHardFlux()), positive(ft.getMaxFlux()),
                positive(stats.getFluxDissipation().getModifiedValue()), Math.min(1, positive(stats.getHardFluxDissipationFraction().getModifiedValue())),
                hasShield ? positive(shield.getUpkeep()) : 0, hasShield && shield.isOn(),
                hasShield ? positive(shield.getFluxPerPointOfDamage() * stats.getShieldDamageTakenMult().getModifiedValue()) : 0,
                positive(ft.getTimeToVent()), stats.getVentRateMult().getModifiedValue() > 0);
        List<Gun> guns = new ArrayList<>();
        for (WeaponAPI w : s.getAllWeapons()) {
            if (w.isDecorative() || w.getType() == WeaponAPI.WeaponType.SYSTEM || w.getType() == WeaponAPI.WeaponType.LAUNCH_BAY
                    || w.getType() == WeaponAPI.WeaponType.STATION_MODULE) continue;
            WeaponAPI.DerivedWeaponStatsAPI ds = w.getDerivedStats();
            boolean available = !incapacitated && !w.isDisabled() && !w.isPermanentlyDisabled() && !w.isForceDisabled()
                    && (!w.usesAmmo() || w.getAmmo() > 0);
            guns.add(new Gun(vec(w.getLocation()), positive(w.getRange()), s.getFacing() + w.getArcFacing(), w.getCurrAngle(),
                    positive(w.getArc()), positive(w.getTurnRate()), positive(ds.getSustainedDps()), positive(ds.getDamagePerShot()),
                    positive(w.getCooldownRemaining()), positive(ds.getSustainedFluxPerSecond()), damage(w.getDamageType()),
                    w.isBeam(), w.getType() == WeaponAPI.WeaponType.MISSILE,
                    w.hasAIHint(WeaponAPI.AIHints.PD) || w.hasAIHint(WeaponAPI.AIHints.PD_ONLY), available));
        }
        String target = null;
        if (s.getAIFlags() != null) {
            Object maneuver = s.getAIFlags().getCustom(ShipwideAIFlags.AIFlags.MANEUVER_TARGET);
            if (maneuver instanceof ShipAPI ship && visibleIds.contains(key(ship))) target = key(ship);
        }
        if (target == null && s.getShipTarget() != null && visibleIds.contains(key(s.getShipTarget()))) target = key(s.getShipTarget());
        double dp = s.getFleetMember() == null ? 0 : positive(s.getFleetMember().getDeploymentPointsCost());
        return new Ship(key(s), s.getName() == null ? key(s) : s.getName(), kind(s), vec(s.getLocation()), vec(s.getVelocity()),
                positive(s.getMaxSpeed()), positive(s.getAcceleration()), positive(s.getDeceleration()), positive(s.getCollisionRadius()),
                dp, positive(s.getHitpoints()), positive(s.getMaxHitpoints()), flux, guns, incapacitated, specialist, s.isFighter(), controllable && dp > 0, target,
                defense(s, shield, hasShield));
    }
    private Defense defense(ShipAPI ship, ShieldAPI shield, boolean hasShield) {
        ArmorGridAPI armor = ship.getArmorGrid();
        List<Double> sectors = new ArrayList<>();
        double rating = armor == null ? 0 : positive(armor.getArmorRating());
        if (armor != null && armor.getMaxArmorInCell() > 0) {
            float[][] grid = armor.getGrid();
            for (int n = 0; n < 8; n++) {
                double angle = Math.toRadians(ship.getFacing() + n * 45);
                int[] cell = armor.getCellAtLocation(new Vector2f(ship.getLocation().x + (float) (Math.cos(angle) * ship.getCollisionRadius() * .45),
                        ship.getLocation().y + (float) (Math.sin(angle) * ship.getCollisionRadius() * .45)));
                double sum = 0; int count = 0;
                if (cell != null) for (int x = Math.max(0, cell[0] - 1); x <= cell[0] + 1 && x < grid.length; x++) {
                    for (int y = Math.max(0, cell[1] - 1); y <= cell[1] + 1 && y < grid[x].length; y++) {
                        sum += Math.max(0, grid[x][y]) / armor.getMaxArmorInCell(); count++;
                    }
                }
                sectors.add(count == 0 ? 0 : rating * Math.min(1, sum / count));
            }
        }
        return new Defense(ship.getFacing(), positive(ship.getMaxTurnRate()), hasShield ? shield.getArc() : 0,
                hasShield ? shield.getFacing() : ship.getFacing(), hasShield && shield.getType() == ShieldAPI.ShieldType.FRONT, rating, sectors);
    }
    public static String key(ShipAPI ship) { String id = ship.getId(); return id != null ? id : "object-" + System.identityHashCode(ship); }
    private static Vec vec(Vector2f v) { return new Vec(v.x, v.y); }
    private static double positive(double d) { return Double.isFinite(d) ? Math.max(0, d) : 0; }
    private static Damage damage(DamageType type) {
        if (type == DamageType.KINETIC) return Damage.KINETIC;
        if (type == DamageType.HIGH_EXPLOSIVE) return Damage.HIGH_EXPLOSIVE;
        if (type == DamageType.FRAGMENTATION) return Damage.FRAGMENTATION;
        return Damage.ENERGY;
    }
    private static Kind kind(ShipAPI s) {
        if (s.isFrigate()) return Kind.FRIGATE;
        if (s.isDestroyer()) return Kind.DESTROYER;
        if (s.isCruiser()) return Kind.CRUISER;
        if (s.isCapital()) return Kind.CAPITAL;
        return Kind.OTHER;
    }
}
