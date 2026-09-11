package omegaai3.diagnostics;

import com.fs.starfarer.api.combat.*;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.ui.LazyFont;
import java.awt.Color;
import java.util.*;

/** Reuses text objects and never issues gameplay commands. Enemy labels obey viewer visibility. */
public final class DecisionOverlay {
    public record Entry(ShipAPI ship, DecisionView view) {}
    private static final Logger LOG = Logger.getLogger(DecisionOverlay.class);
    private final Map<ShipAPI, LazyFont.DrawableString> text = new IdentityHashMap<>();
    private LazyFont font;
    private boolean broken;

    public void render(CombatEngineAPI engine, ViewportAPI viewport, List<Entry> entries, boolean enabled) {
        if (!enabled || broken) { dispose(); return; }
        if (viewport == null || !engine.isUIShowingHUD() || engine.isUIShowingDialog()) return;
        try {
            if (font == null) font = LazyFont.loadFont("graphics/fonts/victor10.fnt");
            Set<ShipAPI> present = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Entry entry : entries) present.add(entry.ship());
            for (var iterator = text.entrySet().iterator(); iterator.hasNext();) {
                var item = iterator.next();
                if (!present.contains(item.getKey())) { item.getValue().dispose(); iterator.remove(); }
            }
            int viewer = engine.getPlayerShip() == null ? 0 : engine.getPlayerShip().getOwner();
            int drawn = 0;
            for (Entry entry : entries) {
                ShipAPI ship = entry.ship();
                if (!ship.isAlive() || ship.isExpired() || ship.isFighter()) continue;
                if (ship.getOwner() != viewer && !engine.isAwareOf(viewer, ship)) continue;
                if (!viewport.isNearViewport(ship.getLocation(), 200)) continue;
                if (++drawn > 60) break;
                LazyFont.DrawableString label = text.get(ship);
                if (label == null) {
                    label = font.createText("", Color.WHITE, 12);
                    label.setMaxWidth(290);
                    text.put(ship, label);
                }
                String value = entry.view().label();
                if (!value.equals(label.getText())) label.setText(value);
                Color color = color(entry.view().state());
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
    private Color color(String state) {
        return switch (state) {
            case "BLOCKED" -> new Color(255, 170, 95);
            case "EXCLUDED", "DISABLED" -> new Color(190, 190, 190);
            case "OWNED RALLY" -> new Color(135, 255, 165);
            case "OBSERVE" -> new Color(140, 200, 255);
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
