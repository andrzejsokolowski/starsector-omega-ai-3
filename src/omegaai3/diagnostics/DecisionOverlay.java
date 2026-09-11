package omegaai3.diagnostics;

import com.fs.starfarer.api.combat.*;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.ui.LazyFont;
import java.awt.Color;
import java.util.*;
import java.util.function.Predicate;

/** One short action above each ship Omega directs. Enemy labels obey viewer visibility. */
public final class DecisionOverlay {
    public record Entry(ShipAPI ship, DecisionView view) {}
    private static final Logger LOG = Logger.getLogger(DecisionOverlay.class);
    private static final Color REGROUP = new Color(140, 200, 255);
    private static final Color WAIT = new Color(255, 220, 130);
    private static final Color DISENGAGE = new Color(255, 160, 130);
    private final Map<ShipAPI, LazyFont.DrawableString> text = new IdentityHashMap<>();
    private LazyFont font;
    private boolean broken;

    public void render(CombatEngineAPI engine, ViewportAPI viewport, List<Entry> entries, boolean enabled, Predicate<Entry> activeControl) {
        if (!enabled || broken) { dispose(); return; }
        if (viewport == null || !engine.isUIShowingHUD() || engine.isUIShowingDialog()) return;
        try {
            Set<ShipAPI> present = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Entry entry : entries) {
                if (!entry.view().label().isEmpty() && activeControl.test(entry)) present.add(entry.ship());
            }
            for (var iterator = text.entrySet().iterator(); iterator.hasNext();) {
                var item = iterator.next();
                if (!present.contains(item.getKey())) { item.getValue().dispose(); iterator.remove(); }
            }
            if (present.isEmpty()) return;
            if (font == null) font = LazyFont.loadFont("graphics/fonts/victor10.fnt");
            int viewer = engine.getPlayerShip() == null ? 0 : engine.getPlayerShip().getOwner();
            int drawn = 0;
            for (Entry entry : entries) {
                String value = entry.view().label();
                if (value.isEmpty()) continue;
                ShipAPI ship = entry.ship();
                if (!present.contains(ship)) continue;
                if (!ship.isAlive() || ship.isExpired() || ship.isFighter()) continue;
                if (ship.getOwner() != viewer && !engine.isAwareOf(viewer, ship)) continue;
                if (!viewport.isNearViewport(ship.getLocation(), 200)) continue;
                if (++drawn > 60) break;
                LazyFont.DrawableString label = text.get(ship);
                if (label == null) {
                    label = font.createText("", Color.WHITE, 12);
                    text.put(ship, label);
                }
                if (!value.equals(label.getText())) label.setText(value);
                Color color = color(value);
                if (!color.equals(label.getBaseColor())) label.setBaseColor(color);
                label.triggerRebuildIfNeeded();
                float x = viewport.convertWorldXtoScreenX(ship.getLocation().x);
                float y = viewport.convertWorldYtoScreenY(ship.getLocation().y + ship.getCollisionRadius()) + 12;
                label.draw(x - label.getWidth() / 2, y + label.getHeight());
            }
        } catch (Exception | LinkageError e) {
            broken = true;
            LOG.warn("Omega decision labels stopped after a rendering error; combat control continues.", e);
            dispose();
        }
    }
    private Color color(String action) {
        return switch (action) {
            case "Regrouping" -> REGROUP;
            case "Waiting for support" -> WAIT;
            case "Disengaging" -> DISENGAGE;
            default -> Color.WHITE;
        };
    }
    public void dispose() {
        for (var label : text.values()) {
            try { label.dispose(); } catch (RuntimeException | LinkageError ignored) { }
        }
        text.clear(); font = null;
    }
}
