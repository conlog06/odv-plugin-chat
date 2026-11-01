package de.crafterteam.meetup;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Alle Einstellungen an einem Ort. Änderungen im Setup-GUI landen hier und werden per save() geschrieben.
 */
public class Settings {

    private final MeetupPlugin plugin;

    public String prefix, serverIp, scoreboardTitle;
    public Location lobby;

    public String worldName;
    public int centerX, centerZ;
    public int minPlayers, lobbyCountdown, freezeTime, pvpProtection, endDelay;
    public boolean naturalRegen, goldenAppleOnDeath, lightningOnDeath, restartOnEnd, lateJoinSpectator;

    public int teamSize, teamCount;
    public boolean friendlyFire;

    public int borderStart, borderEnd, shrinkStart, shrinkDuration;
    public double borderDamage, borderBuffer;

    public String schematic;
    public int spawnCount, spawnRadius, spawnYOffset, blocksPerTick;
    public boolean pasteAir, generateOnStart;

    public ItemStack[] kitContents;
    public ItemStack[] kitArmor;

    public Settings(MeetupPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        prefix = color(c.getString("prefix", "&b&lMeetup &8» &7"));
        serverIp = c.getString("server-ip", "dein-server.net");
        scoreboardTitle = color(c.getString("scoreboard-title", "&b&lMeetup"));
        lobby = c.isConfigurationSection("lobby") ? readLocation(c.getConfigurationSection("lobby").getValues(false)) : null;

        worldName = c.getString("game.world", "world");
        centerX = c.getInt("game.center-x", 0);
        centerZ = c.getInt("game.center-z", 0);
        minPlayers = c.getInt("game.min-players", 2);
        lobbyCountdown = c.getInt("game.lobby-countdown", 60);
        freezeTime = c.getInt("game.freeze-time", 10);
        pvpProtection = c.getInt("game.pvp-protection", 30);
        naturalRegen = c.getBoolean("game.natural-regeneration", true);
        goldenAppleOnDeath = c.getBoolean("game.golden-apple-on-death", true);
        lightningOnDeath = c.getBoolean("game.lightning-on-death", true);
        endDelay = c.getInt("game.end-delay", 15);
        restartOnEnd = c.getBoolean("game.restart-on-end", false);
        lateJoinSpectator = c.getBoolean("game.late-join-spectator", true);

        teamSize = clamp(c.getInt("teams.size", 1), 1, 8);
        teamCount = clamp(c.getInt("teams.count", 16), 2, 16);
        friendlyFire = c.getBoolean("teams.friendly-fire", false);

        borderStart = c.getInt("border.start-size", 600);
        borderEnd = c.getInt("border.end-size", 50);
        shrinkStart = c.getInt("border.shrink-start", 5);
        shrinkDuration = c.getInt("border.shrink-duration", 10);
        borderDamage = c.getDouble("border.damage", 1.0);
        borderBuffer = c.getDouble("border.buffer", 2.0);

        schematic = c.getString("spawns.schematic", "");
        spawnCount = c.getInt("spawns.count", 0);
        spawnRadius = c.getInt("spawns.radius", 0);
        spawnYOffset = c.getInt("spawns.y-offset", 0);
        pasteAir = c.getBoolean("spawns.paste-air", true);
        generateOnStart = c.getBoolean("spawns.generate-on-start", true);
        blocksPerTick = Math.max(100, c.getInt("spawns.blocks-per-tick", 3000));

        if (c.getBoolean("kit.saved", false)) {
            kitContents = readItems(c.getList("kit.contents"), 36);
            kitArmor = readItems(c.getList("kit.armor"), 4);
        } else {
            kitContents = null;
            kitArmor = null;
        }
    }

    public void save() {
        FileConfiguration c = plugin.getConfig();
        c.set("lobby", lobby == null ? null : writeLocation(lobby));
        c.set("game.world", worldName);
        c.set("game.center-x", centerX);
        c.set("game.center-z", centerZ);
        c.set("game.min-players", minPlayers);
        c.set("game.lobby-countdown", lobbyCountdown);
        c.set("game.freeze-time", freezeTime);
        c.set("game.pvp-protection", pvpProtection);
        c.set("game.natural-regeneration", naturalRegen);
        c.set("game.golden-apple-on-death", goldenAppleOnDeath);
        c.set("game.lightning-on-death", lightningOnDeath);
        c.set("game.end-delay", endDelay);
        c.set("game.restart-on-end", restartOnEnd);
        c.set("game.late-join-spectator", lateJoinSpectator);
        c.set("teams.size", teamSize);
        c.set("teams.count", teamCount);
        c.set("teams.friendly-fire", friendlyFire);
        c.set("border.start-size", borderStart);
        c.set("border.end-size", borderEnd);
        c.set("border.shrink-start", shrinkStart);
        c.set("border.shrink-duration", shrinkDuration);
        c.set("border.damage", borderDamage);
        c.set("border.buffer", borderBuffer);
        c.set("spawns.schematic", schematic);
        c.set("spawns.count", spawnCount);
        c.set("spawns.radius", spawnRadius);
        c.set("spawns.y-offset", spawnYOffset);
        c.set("spawns.paste-air", pasteAir);
        c.set("spawns.generate-on-start", generateOnStart);
        c.set("spawns.blocks-per-tick", blocksPerTick);
        if (kitContents != null) {
            c.set("kit.saved", true);
            c.set("kit.contents", toList(kitContents));
            c.set("kit.armor", toList(kitArmor));
        }
        plugin.saveConfig();
    }

    /** Lädt/erstellt die Spielwelt. */
    public World getGameWorld() {
        World w = Bukkit.getWorld(worldName);
        if (w == null && worldName != null && !worldName.isEmpty()) {
            w = Bukkit.createWorld(new WorldCreator(worldName));
        }
        return w;
    }

    public Location getCenter() {
        World w = getGameWorld();
        return w == null ? null : new Location(w, centerX + 0.5, 64, centerZ + 0.5);
    }

    public Location getLobbyOrSpawn() {
        if (lobby != null && lobby.getWorld() != null) return lobby;
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    public boolean isSolo() {
        return teamSize <= 1;
    }

    // ---------- Hilfsfunktionen ----------

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    public static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static Map<String, Object> writeLocation(Location l) {
        Map<String, Object> m = new java.util.LinkedHashMap<String, Object>();
        m.put("world", l.getWorld().getName());
        m.put("x", l.getX());
        m.put("y", l.getY());
        m.put("z", l.getZ());
        m.put("yaw", (double) l.getYaw());
        m.put("pitch", (double) l.getPitch());
        return m;
    }

    public static Location readLocation(Map<?, ?> m) {
        if (m == null || m.get("world") == null) return null;
        World w = Bukkit.getWorld(String.valueOf(m.get("world")));
        if (w == null) w = Bukkit.createWorld(new WorldCreator(String.valueOf(m.get("world"))));
        if (w == null) return null;
        return new Location(w, num(m.get("x")), num(m.get("y")), num(m.get("z")),
                (float) num(m.get("yaw")), (float) num(m.get("pitch")));
    }

    public static Map<String, Object> locToMap(Location l) {
        return writeLocation(l);
    }

    private static double num(Object o) {
        return o instanceof Number ? ((Number) o).doubleValue() : 0;
    }

    private static List<Object> toList(ItemStack[] items) {
        List<Object> list = new ArrayList<Object>();
        for (ItemStack i : items) list.add(i);
        return list;
    }

    private static ItemStack[] readItems(List<?> list, int size) {
        ItemStack[] arr = new ItemStack[size];
        if (list == null) return arr;
        for (int i = 0; i < Math.min(size, list.size()); i++) {
            Object o = list.get(i);
            if (o instanceof ItemStack) arr[i] = (ItemStack) o;
        }
        return arr;
    }
}
