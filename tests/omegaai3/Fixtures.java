package omegaai3;

import omegaai3.model.BattleFrame;
import omegaai3.model.Vec;
import java.util.List;
import static omegaai3.model.BattleFrame.*;

public final class Fixtures {
    private Fixtures() {}
    public static Flux flux(double total, double hard) { return new Flux(total, hard, 10000, 300, 0, 0, true, .5, 5, true); }
    public static Gun gun(Vec position, double range, double dps) {
        return new Gun(position, range, 0, 0, 360, 360, dps, 100, 0, dps, Damage.ENERGY, false, false, false, true);
    }
    public static Ship ship(String id, Kind kind, double x, double speed, double dp, double range, String target) {
        Vec position = new Vec(x, 0);
        return new Ship(id, id, kind, position, new Vec(speed, 0), speed, 100, 50, kind == Kind.CAPITAL ? 150 : 50,
                dp, 5000, 5000, flux(0, 0), List.of(gun(position, range, dp * 20)), false, false, false, true, target);
    }
    public static Ship atFlux(Ship s, double level) {
        return new Ship(s.id(), s.name(), s.kind(), s.position(), s.velocity(), s.speed(), s.acceleration(), s.deceleration(), s.radius(),
                s.dp(), s.hull(), s.maxHull(), flux(level * 10000, level * 10000), s.guns(), s.incapacitated(), s.specialist(), s.fighter(), s.controllable(), s.targetId());
    }
    public static Ship control(Ship s, boolean allowed, boolean specialist) {
        return new Ship(s.id(), s.name(), s.kind(), s.position(), s.velocity(), s.speed(), s.acceleration(), s.deceleration(), s.radius(),
                s.dp(), s.hull(), s.maxHull(), s.flux(), s.guns(), s.incapacitated(), specialist, s.fighter(), allowed, s.targetId());
    }
    public static BattleFrame frame(double t, List<Ship> friends, List<Ship> enemies) {
        return new BattleFrame(0, t, 20000, 20000, friends, enemies, List.of());
    }
}
