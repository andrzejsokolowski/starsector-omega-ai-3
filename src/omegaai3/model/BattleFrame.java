package omegaai3.model;

import java.util.List;

/** A side's current perception. No game object or mutable game vector is retained here. */
public record BattleFrame(int side, double time, double width, double height,
                          List<Ship> friends, List<Ship> enemies, List<Shot> shots, List<Obstacle> obstacles) {
    public BattleFrame { friends = List.copyOf(friends); enemies = List.copyOf(enemies); shots = List.copyOf(shots); obstacles = List.copyOf(obstacles); }
    public BattleFrame(int side, double time, double width, double height, List<Ship> friends, List<Ship> enemies, List<Shot> shots) {
        this(side, time, width, height, friends, enemies, shots, List.of());
    }
    public record Obstacle(String id, Vec position, Vec velocity, double radius) {}
    public record Defense(double facing, double turnRate, double shieldArc, double shieldFacing, boolean frontShield,
                          double armor, List<Double> armorSectors) {
        public Defense { armorSectors = List.copyOf(armorSectors); }
        public double armorToward(Vec relativeSource) {
            if (armorSectors.isEmpty()) return armor;
            double angle = (relativeSource.bearing() - facing + 720) % 360;
            int sector = ((int) Math.round(angle / 45)) % 8;
            return armorSectors.get(sector);
        }
    }
    public enum Kind { FRIGATE, DESTROYER, CRUISER, CAPITAL, OTHER }
    public enum Damage {
        KINETIC(2, .5), HIGH_EXPLOSIVE(.5, 2), ENERGY(1, 1), FRAGMENTATION(.25, .25);
        public final double shield, armor;
        Damage(double shield, double armor) { this.shield = shield; this.armor = armor; }
        public double hull() { return 1; }
    }
    public record Flux(double total, double hard, double capacity, double dissipation,
                       double hardDissipationFraction, double upkeep, boolean shieldUp,
                       double shieldEfficiency, double ventTime, boolean canVent) {
        public double level() { return capacity > 0 ? total / capacity : 0; }
        public double headroom() { return Math.max(0, capacity - total); }
    }
    public record Gun(Vec position, double range, double facing, double aim, double arc, double turnRate,
                      double sustainedDps, double damagePerShot, double cooldown,
                      double fluxPerSecond, Damage damage, boolean beam, boolean missile,
                      boolean pd, boolean available) {
        public boolean canReach(Vec target, double targetRadius, double seconds) {
            if (!available || cooldown > seconds) return false;
            Vec delta = target.sub(position);
            if (delta.length() > range + targetRadius) return false;
            double angularSize = Math.toDegrees(Math.asin(Math.min(1, targetRadius / Math.max(1, delta.length()))));
            if (Math.abs(Vec.angleDifference(delta.bearing(), facing)) > arc / 2 + angularSize) return false;
            double slew = Math.max(0, Math.abs(Vec.angleDifference(delta.bearing(), aim)) - angularSize);
            return slew < 2 || turnRate > 0 && slew / turnRate <= seconds;
        }
    }
    public record Ship(String id, String name, Kind kind, Vec position, Vec velocity,
                       double speed, double acceleration, double deceleration, double radius,
                       double dp, double hull, double maxHull, Flux flux, List<Gun> guns,
                       boolean incapacitated, boolean specialist, boolean fighter,
                       boolean controllable, String targetId, Defense defense) {
        public Ship { guns = List.copyOf(guns); }
        public Ship(String id, String name, Kind kind, Vec position, Vec velocity, double speed, double acceleration,
                    double deceleration, double radius, double dp, double hull, double maxHull, Flux flux, List<Gun> guns,
                    boolean incapacitated, boolean specialist, boolean fighter, boolean controllable, String targetId) {
            this(id, name, kind, position, velocity, speed, acceleration, deceleration, radius, dp, hull, maxHull, flux,
                    guns, incapacitated, specialist, fighter, controllable, targetId, new Defense(0, 30, 360, 0, false, 0, List.of()));
        }
        public double hullFraction() { return maxHull > 0 ? hull / maxHull : 0; }
        public double gunDps() {
            if (incapacitated) return 0;
            return guns.stream().filter(g -> g.available && !g.pd && !g.missile).mapToDouble(Gun::sustainedDps).sum();
        }
        public double usefulRange() {
            // Long-range pressure cannot be replaced by one short secondary gun.
            double total = gunDps();
            if (total <= 0) return 0;
            List<Gun> ordered = guns.stream().filter(g -> g.available && !g.pd && !g.missile)
                    .sorted((a, b) -> Double.compare(b.range, a.range)).toList();
            double dps = 0;
            for (Gun g : ordered) { dps += g.sustainedDps; if (dps >= total * .6) return g.range; }
            return 0;
        }
        public double supportDps(Vec target, double targetRadius, double seconds) {
            if (incapacitated || flux.level() >= .8) return 0;
            return guns.stream().filter(g -> !g.pd && !g.missile && g.canReach(target, targetRadius, seconds))
                    .mapToDouble(Gun::sustainedDps).sum();
        }
        public boolean readyAnchor() {
            return !fighter && !specialist && !incapacitated && gunDps() > 0 && hullFraction() >= .45 && flux.level() < .65;
        }
    }
    public record Shot(Vec position, Vec velocity, double radius, double damage, Damage type,
                       boolean softFlux, boolean guided, double maxSpeed, double remainingLife) {}
}
