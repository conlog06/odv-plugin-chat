package de.crafterteam.odv.data;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.world.ArenaGenerator;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** arena.yml: Arena-Welt, Mittelpunkt, Lobby, Practice, gespeicherte Spawnpunkte. */
public class Arena {

    private final OdvPlugin plugin;
    private final File file;
    private String worldName;
    private Location center, lobby, practice;
    private final List<Location> spawns = new ArrayList<>();

    public Arena(OdvPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arena.yml");
    }

    public void load() {
        center = lobby = practice = null;
        spawns.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        worldName = y.getString("world");
        World w = worldName == null ? null : loadArenaWorld(plugin, worldName);
        if (w != null) {
            center = read(y, "center", w);
            for (Map<?, ?> m : y.getMapList("spawns")) {
                Location l = new Location(w, num(m.get("x")), num(m.get("y")), num(m.get("z")), (float) num(m.get("yaw")), 0f);
                spawns.add(l);
            }
        }
        lobby = read(y, "lobby", worldOf(y, "lobby", w));
        practice = read(y, "practice", worldOf(y, "practice", w));
        if (center == null && lobby != null && lobby.getWorld().equals(w)) center = lobby.clone();
    }

    private World worldOf(YamlConfiguration y, String key, World def) {
        String n = y.getString(key + ".world");
        if (n == null) return def;
        World w = Bukkit.getWorld(n);
        if (w == null && new File(Bukkit.getWorldContainer(), n).isDirectory()) w = Bukkit.createWorld(new WorldCreator(n));
        return w;
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("world", center != null ? center.getWorld().getName() : worldName);
        write(y, "center", center);
        write(y, "lobby", lobby);
        write(y, "practice", practice);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Location l : spawns) {
            Map<String, Object> m = new LinkedHashMap<>();
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
            plugin.getLogger().severe("arena.yml: " + e.getMessage());
        }
    }

    /** Arena-Welten immer mit unserem Generator laden (Extra-Diamanten). */
    public static World loadArenaWorld(OdvPlugin plugin, String name) {
        World w = Bukkit.getWorld(name);
        if (w == null && new File(Bukkit.getWorldContainer(), name).isDirectory()) {
            w = Bukkit.createWorld(new WorldCreator(name).generator(new ArenaGenerator(plugin)));
        }
        return w;
    }

    public boolean isReady() {
        return center != null && lobby != null;
    }

    public World getWorld() {
        return center == null ? null : center.getWorld();
    }

    public Location getCenter() {
        return center == null ? null : center.clone();
    }

    public Location getLobby() {
        return lobby == null ? null : lobby.clone();
    }

    public Location getLobbyOrDefault() {
        return lobby != null ? lobby.clone() : Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    public Location getPractice() {
        return practice == null ? null : practice.clone();
    }

    public void setCenter(Location l) {
        center = l.clone();
        worldName = l.getWorld().getName();
        spawns.clear();
        save();
    }

    public void setLobby(Location l) {
        lobby = l.clone();
        if (center == null) {
            center = l.clone();
            worldName = l.getWorld().getName();
        }
        save();
    }

    public void setPractice(Location l) {
        practice = l == null ? null : l.clone();
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

    /** Mittel-Spawn (Portal) und Lobby-Insel sind geschützt. */
    public boolean isProtected(Location l) {
        int r = plugin.settings().protectionRadius;
        if (r <= 0) return false;
        if (center != null && l.getWorld().equals(center.getWorld())
                && Math.abs(l.getX() - center.getX()) <= r && Math.abs(l.getZ() - center.getZ()) <= r
                && l.getY() > center.getY() - 6) return true;
        if (lobby == null || !l.getWorld().equals(lobby.getWorld()) || l.getY() < lobby.getY() - 10) return false;
        double dx = l.getX() - lobby.getX(), dz = l.getZ() - lobby.getZ();
        return dx * dx + dz * dz < 70 * 70;
    }

    private static Location read(YamlConfiguration y, String k, World w) {
        ConfigurationSection s = y.getConfigurationSection(k);
        if (s == null || w == null || !s.contains("x")) return null;
        return new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    private static void write(YamlConfiguration y, String k, Location l) {
        if (l == null) return;
        y.set(k + ".world", l.getWorld().getName());
        y.set(k + ".x", l.getX());
        y.set(k + ".y", l.getY());
        y.set(k + ".z", l.getZ());
        y.set(k + ".yaw", (double) l.getYaw());
        y.set(k + ".pitch", (double) l.getPitch());
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }
}
