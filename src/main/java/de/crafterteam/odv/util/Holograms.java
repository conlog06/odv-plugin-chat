package de.crafterteam.odv.util;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Schwebende Texte über TextDisplay-Entities (nicht persistent, werden beim Stoppen entfernt). */
public class Holograms {

    private final Plugin plugin;
    private final Map<String, List<TextDisplay>> groups = new HashMap<>();

    public Holograms(Plugin plugin) {
        this.plugin = plugin;
    }

    public TextDisplay spawn(String group, Location at, String text, float scale, Collection<? extends Player> onlyFor) {
        if (at.getWorld() == null) return null;
        TextDisplay td = at.getWorld().spawn(at, TextDisplay.class, d -> {
            d.text(Txt.c(text));
            d.setBillboard(Display.Billboard.CENTER);
            d.setPersistent(false);
            d.setShadowed(true);
            d.setSeeThrough(false);
            d.setViewRange(4f);
            d.setBackgroundColor(Color.fromARGB(90, 0, 0, 0));
            d.setTransformation(new org.bukkit.util.Transformation(
                    new org.joml.Vector3f(), new org.joml.Quaternionf(), new org.joml.Vector3f(scale, scale, scale), new org.joml.Quaternionf()));
            if (onlyFor != null) d.setVisibleByDefault(false);
        });
        if (onlyFor != null) for (Player p : onlyFor) p.showEntity(plugin, td);
        groups.computeIfAbsent(group, k -> new ArrayList<>()).add(td);
        return td;
    }

    public TextDisplay spawn(String group, Location at, String text) {
        return spawn(group, at, text, 1f, null);
    }

    public void remove(String group) {
        List<TextDisplay> l = groups.remove(group);
        if (l != null) for (TextDisplay d : l) if (d.isValid()) d.remove();
    }

    public void removePrefix(String prefix) {
        for (String g : new ArrayList<>(groups.keySet())) if (g.startsWith(prefix)) remove(g);
    }

    public void removeAll() {
        for (String g : new ArrayList<>(groups.keySet())) remove(g);
    }
}
