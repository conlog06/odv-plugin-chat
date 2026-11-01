package de.crafterteam.odv.data;

import de.crafterteam.odv.OdvPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * arena.yml: Spielwelt, Lobby, Mittelpunkt und gespeicherte Spawnpunkte.
 * (Kompatibel zum Format von ODVMeetup 1.0.)
 */
public class Arena {

    private final OdvPlugin plugin;
    private final File file;
    private Location lobby, center;
    private final List<Location> spawns = new ArrayList<Location>();

    public Arena(OdvPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arena.yml");
    }

    public void load() {
        lobby = null;
        center = null;
        spawns.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        String name = y.getString("world");
        if (name == null) return;
        World w = loadWorld(name);
        if (w == null) {
            plugin.getLogger().warning("Arena-Welt '" + name + "' nicht gefunden.");
            return;
        }
        lobby = read(y, "lobby", w);
        center = read(y, "center", w);
        if (center == null && lobby != null) center = lobby.clone();
        for (Map<?, ?> m : y.getMapList("spawns")) {
            Location l = new Location(w, num(m.get("x")), num(m.get("y")), num(m.get("z")), (float) num(m.get("yaw")), 0f);
            spawns.add(l);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("ready", isReady());
        if (center != null) y.set("world", center.getWorld().getName());
        else if (lobby != null) y.set("world", lobby.getWorld().getName());
        write(y, "lobby", lobby);
        write(y, "center", center);
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (Location l : spawns) {
            Map<String, Object> m = new java.util.LinkedHashMap<String, Object>();
            m.put("x", l.getX());
            m.put("y", l.getY());
            m.put("z", l.getZ());
            m.put("yaw", (double) l.getYaw());
            list.add(m);
        }
        y.set("spawns", list);
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("arena.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    public static World loadWorld(String name) {
        World w = Bukkit.getWorld(name);
        if (w == null && new File(Bukkit.getWorldContainer(), name).isDirectory()) {
            w = Bukkit.createWorld(new WorldCreator(name));
        }
        return w;
    }

    public boolean isReady() {
        return lobby != null && center != null && lobby.getWorld() != null && lobby.getWorld().equals(center.getWorld());
    }

    public World getWorld() {
        return center != null ? center.getWorld() : lobby != null ? lobby.getWorld() : null;
    }

    public Location getLobby() {
        return lobby == null ? null : lobby.clone();
    }

    public Location getCenter() {
        return center == null ? null : center.clone();
    }

    /** Lobby oder (Fallback) Spawn der Hauptwelt. */
    public Location getLobbyOrDefault() {
        return lobby != null ? lobby.clone() : Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    public void setLobby(Location l) {
        lobby = l.clone();
        if (center == null || !center.getWorld().equals(l.getWorld())) {
            center = l.clone();
            spawns.clear();
        }
        save();
    }

    /** Lobby + Mitte in einem Rutsch (Schnellsetup). */
    public void setLobbyAndCenter(Location l) {
        lobby = l.clone();
        center = l.clone();
        spawns.clear();
        save();
    }

    public void setCenter(Location l) {
        if (lobby != null && !l.getWorld().equals(lobby.getWorld())) {
            throw new IllegalArgumentException("Mittelpunkt muss in der Lobby-Welt liegen.");
        }
        center = l.clone();
        save();
    }

    public List<Location> getSpawns() {
        return Collections.unmodifiableList(spawns);
    }

    public void setSpawns(List<Location> list) {
        spawns.clear();
        spawns.addAll(list);
        save();
    }

    public void addSpawn(Location l) {
        spawns.add(l.clone());
        save();
    }

    public boolean isProtected(Location l) {
        int r = plugin.settings().protectionRadius;
        if (r <= 0 || center == null || l.getWorld() != center.getWorld()) return false;
        return Math.abs(l.getX() - center.getX()) <= r && Math.abs(l.getZ() - center.getZ()) <= r;
    }

    private static Location read(YamlConfiguration y, String k, World w) {
        ConfigurationSection s = y.getConfigurationSection(k);
        if (s == null || !s.contains("x")) return null;
        return new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    private static void write(YamlConfiguration y, String k, Location l) {
        if (l == null) return;
        y.set(k + ".x", l.getX());
        y.set(k + ".y", l.getY());
        y.set(k + ".z", l.getZ());
        y.set(k + ".yaw", (double) l.getYaw());
        y.set(k + ".pitch", (double) l.getPitch());
    }

    private static double num(Object o) {
        return o instanceof Number ? ((Number) o).doubleValue() : 0;
    }
}
