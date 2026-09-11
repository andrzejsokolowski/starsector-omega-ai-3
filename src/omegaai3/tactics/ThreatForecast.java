package omegaai3.tactics;

import omegaai3.model.*;
import static omegaai3.model.BattleFrame.*;

/** Short-horizon pressure estimate. Armor is a directional reduction, never a second hull HP pool. */
public final class ThreatForecast {
    public record Pressure(double hardFlux, double softFlux, double hullDamage, double burstFlux, Vec away) {
        public double total() { return hardFlux + softFlux + hullDamage; }
    }
    private ThreatForecast() {}
    public static Pressure at(BattleFrame frame, Ship me, Vec position, double facing, boolean includeShots) {
        double hard = 0, soft = 0, hull = 0, burst = 0; Vec away = Vec.ZERO;
        for (Ship enemy : frame.enemies()) {
            if (enemy.incapacitated()) continue;
            for (Gun gun : enemy.guns()) {
                if (!gun.available() || gun.cooldown() > 2) continue;
                Vec relative = position.sub(gun.position());
                if (relative.length() > gun.range() + me.radius()) continue;
                double angle = Math.abs(Vec.angleDifference(relative.bearing(), gun.facing()));
                double angularRadius = Math.toDegrees(Math.asin(Math.min(1, me.radius() / Math.max(1, relative.length()))));
                if (angle > gun.arc() / 2 + angularRadius + enemy.defense().turnRate() * 2) continue;
                double weight = gun.canReach(position, me.radius(), 1) ? 1 : .5;
                double dps = gun.missile() ? Math.max(gun.sustainedDps(), gun.damagePerShot() / 3) : gun.sustainedDps();
                dps *= weight;
                Vec source = gun.position().sub(position);
                double shieldFacing = me.defense().frontShield() ? facing : me.defense().shieldFacing();
                boolean covered = me.flux().shieldEfficiency() > 0 && Math.abs(Vec.angleDifference(source.bearing(), shieldFacing)) <= me.defense().shieldArc() / 2 + 10;
                double flux = dps * gun.damage().shield * me.flux().shieldEfficiency();
                if (covered) {
                    if (gun.beam()) soft += flux; else hard += flux;
                    if (!gun.beam()) burst += Math.max(0, gun.damagePerShot() - dps * .5) * weight * gun.damage().shield * me.flux().shieldEfficiency();
                } else {
                    double localArmor = me.defense().armorToward(source);
                    double armorHit = Math.max(1, gun.damagePerShot() * gun.damage().armor);
                    double reduction = Math.max(.05, armorHit / (localArmor + armorHit));
                    hull += dps * reduction;
                }
                away = away.add(position.sub(enemy.position()).unit().scale(dps));
            }
        }
        if (includeShots) for (Shot shot : frame.shots()) {
            double hit = CombatMath.impactTime(shot, me, 2);
            if (!Double.isFinite(hit)) continue;
            Vec source = shot.position().sub(position);
            boolean covered = me.flux().shieldEfficiency() > 0 && Math.abs(Vec.angleDifference(source.bearing(),
                    me.defense().frontShield() ? facing : me.defense().shieldFacing())) <= me.defense().shieldArc() / 2 + 10;
            if (covered) burst += shot.damage() * shot.type().shield * me.flux().shieldEfficiency();
            else hull += shot.damage() / Math.max(.5, hit + .5);
            away = away.add(position.sub(shot.position()).unit().scale(shot.damage() / 2));
        }
        return new Pressure(hard, soft, hull, burst, away.unit());
    }
    public static double dangerSeconds(Ship ship, Pressure pressure, double firingFlux) {
        double fluxTime = Double.POSITIVE_INFINITY;
        Flux f = ship.flux();
        if (f.capacity() > 0 && pressure.hardFlux() + pressure.softFlux() + pressure.burstFlux() > 0) {
            Flux start = new Flux(f.total() + pressure.burstFlux(), f.hard() + pressure.burstFlux(), f.capacity(),
                    f.dissipation(), f.hardDissipationFraction(), f.upkeep(), true, f.shieldEfficiency(), f.ventTime(), f.canVent());
            for (double t = 0; t <= 12; t += .25) {
                if (start.total() >= f.capacity() * .9) { fluxTime = t; break; }
                var next = CombatMath.fluxAfter(start, firingFlux, pressure.softFlux(), pressure.hardFlux(), .25);
                start = new Flux(next.total(), next.hard(), f.capacity(), f.dissipation(), f.hardDissipationFraction(),
                        f.upkeep(), true, f.shieldEfficiency(), f.ventTime(), f.canVent());
            }
        }
        double hullTime = pressure.hullDamage() > 0 ? ship.hull() / pressure.hullDamage() : Double.POSITIVE_INFINITY;
        return Math.min(fluxTime, hullTime);
    }
    public static double firingFlux(Ship ship, Ship target) {
        return ship.guns().stream().filter(g -> !g.missile() && !g.pd() && g.canReach(target.position(), target.radius(), 1))
                .mapToDouble(Gun::fluxPerSecond).sum();
    }
}
