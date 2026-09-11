package omegaai3.diagnostics;

import java.util.*;

/** Explains inactivity in the fleet HUD, never as a made-up ship action. */
public final class ControlStatus {
    private ControlStatus() {}
    public static String fleet(String name, boolean enabled, int active, Map<String, Integer> reasons) {
        if (!enabled) return name + ": excluded in settings";
        if (active > 0) return name + ": " + active + " under Omega control";
        if (reasons.isEmpty()) return name + ": no visible ships";
        var ordered = reasons.entrySet().stream().sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                .reversed().thenComparing(Map.Entry::getKey)).limit(2).toList();
        return name + ": " + String.join("; ", ordered.stream().map(e -> e.getKey() + " (" + e.getValue() + ")").toList());
    }
}
