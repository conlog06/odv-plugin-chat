package de.crafterteam.odv;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;

/**
 * Alle Spielregeln aus der config.yml. Änderungen im Menü -> save().
 */
public class Settings {

    public enum Mode {
        MEETUP("§b§lMEETUP", "Meetup"), ODV("§a§lODV", "ODV");

        public final String title, display;

        Mode(String title, String display) {
            this.title = title;
            this.display = display;
        }
    }

    public enum SpawnMode {
        PLATFORM("Schematic-Plattformen"), SCATTER("Zufällig verteilt");

        public final String display;

        SpawnMode(String display) {
            this.display = display;
        }
    }

    private final OdvPlugin plugin;

    public Mode mode;
    public String prefix, serverIp;

    public int teamSize, teamCount;
    public boolean friendlyFire;

    public int minPlayers, countdown, freeze, grace, endDelay, disconnectGrace;
    public boolean autoStart, naturalRegen, goldenApple, lightning, stopOnEnd;

    public int borderInitial, borderFinal, shrinkAfter, shrinkDuration;
    public double borderDamage;

    public SpawnMode spawnMode;
    public String teamSchematic, lobbySchematic;
    public int spawnRadius, spawnYOffset, protectionRadius, minTeamDistance, blocksPerTick;
    public boolean pasteAir, regenerateOnStart;

    public boolean backupOnStart;
    public int backupKeep;

    public Settings(OdvPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        mode = parse(Mode.class, c.getString("mode"), Mode.MEETUP);
        prefix = color(c.getString("prefix", "&b&lODV &8» &7"));
        serverIp = c.getString("server-ip", "dein-server.net");

        teamSize = clamp(c.getInt("teams.size", 1), 1, 10);
        teamCount = clamp(c.getInt("teams.count", 16), 2, 36);
        friendlyFire = c.getBoolean("teams.friendly-fire", false);

        minPlayers = clamp(c.getInt("match.min-players", 2), 1, 360);
        autoStart = c.getBoolean("match.auto-start", true);
        countdown = clamp(c.getInt("match.countdown", 30), 5, 600);
        freeze = clamp(c.getInt("match.freeze", 10), 0, 120);
        grace = clamp(c.getInt("match.grace", 30), 0, 7200);
        endDelay = clamp(c.getInt("match.end-delay", 15), 3, 300);
        disconnectGrace = clamp(c.getInt("match.disconnect-grace", 60), 0, 600);
        naturalRegen = c.getBoolean("match.natural-regeneration", true);
        goldenApple = c.getBoolean("match.golden-apple-on-death", true);
        lightning = c.getBoolean("match.lightning-on-death", true);
        stopOnEnd = c.getBoolean("match.stop-server-on-end", false);

        borderInitial = clamp(c.getInt("border.initial", 600), 100, 10000);
        borderFinal = clamp(c.getInt("border.final", 40), 10, borderInitial);
        shrinkAfter = clamp(c.getInt("border.shrink-after", 300), 0, 14400);
        shrinkDuration = clamp(c.getInt("border.shrink-duration", 600), 30, 14400);
        borderDamage = Math.max(0, c.getDouble("border.damage", 1.0));

        spawnMode = parse(SpawnMode.class, c.getString("spawn.mode"), SpawnMode.PLATFORM);
        teamSchematic = c.getString("spawn.team-schematic", "plattform");
        lobbySchematic = c.getString("spawn.lobby-schematic", "lobby");
        spawnRadius = clamp(c.getInt("spawn.radius", 0), 0, 5000);
        spawnYOffset = clamp(c.getInt("spawn.y-offset", 0), -20, 50);
        pasteAir = c.getBoolean("spawn.paste-air", true);
        regenerateOnStart = c.getBoolean("spawn.regenerate-on-start", true);
        protectionRadius = clamp(c.getInt("spawn.protection-radius", 15), 0, 200);
        minTeamDistance = clamp(c.getInt("spawn.min-team-distance", 40), 5, 500);
        blocksPerTick = clamp(c.getInt("spawn.blocks-per-tick", 2000), 100, 20000);

        backupOnStart = c.getBoolean("backup.on-start", true);
        backupKeep = clamp(c.getInt("backup.keep", 10), 1, 100);
    }

    public void save() {
        FileConfiguration c = plugin.getConfig();
        c.set("mode", mode.name());
        c.set("teams.size", teamSize);
        c.set("teams.count", teamCount);
        c.set("teams.friendly-fire", friendlyFire);
        c.set("match.min-players", minPlayers);
        c.set("match.auto-start", autoStart);
        c.set("match.countdown", countdown);
        c.set("match.freeze", freeze);
        c.set("match.grace", grace);
        c.set("match.end-delay", endDelay);
        c.set("match.disconnect-grace", disconnectGrace);
        c.set("match.natural-regeneration", naturalRegen);
        c.set("match.golden-apple-on-death", goldenApple);
        c.set("match.lightning-on-death", lightning);
        c.set("match.stop-server-on-end", stopOnEnd);
        c.set("border.initial", borderInitial);
        c.set("border.final", borderFinal);
        c.set("border.shrink-after", shrinkAfter);
        c.set("border.shrink-duration", shrinkDuration);
        c.set("border.damage", borderDamage);
        c.set("spawn.mode", spawnMode.name());
        c.set("spawn.team-schematic", teamSchematic);
        c.set("spawn.lobby-schematic", lobbySchematic);
        c.set("spawn.radius", spawnRadius);
        c.set("spawn.y-offset", spawnYOffset);
        c.set("spawn.paste-air", pasteAir);
        c.set("spawn.regenerate-on-start", regenerateOnStart);
        c.set("spawn.protection-radius", protectionRadius);
        c.set("spawn.min-team-distance", minTeamDistance);
        c.set("spawn.blocks-per-tick", blocksPerTick);
        c.set("backup.on-start", backupOnStart);
        c.set("backup.keep", backupKeep);
        plugin.saveConfig();
    }

    /** Lädt die Zeit-/Border-Werte des Spielmodus. Teams, Kits und Spawns bleiben. */
    public void preset(Mode m) {
        mode = m;
        if (m == Mode.ODV) {
            grace = 600;
            freeze = 5;
            borderInitial = 1200;
            borderFinal = 40;
            shrinkAfter = 900;
            shrinkDuration = 900;
        } else {
            grace = 30;
            freeze = 10;
            borderInitial = 600;
            borderFinal = 40;
            shrinkAfter = 300;
            shrinkDuration = 600;
        }
        save();
    }

    public boolean isSolo() {
        return teamSize <= 1;
    }

    public void validate() {
        if (borderFinal > borderInitial) borderFinal = borderInitial;
        if (shrinkAfter < grace) shrinkAfter = grace;
    }

    // ---------------- Hilfen ----------------

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    public static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    public static String time(int seconds) {
        seconds = Math.max(0, seconds);
        if (seconds >= 3600) return seconds / 3600 + "h " + (seconds % 3600) / 60 + "m";
        int m = seconds / 60, s = seconds % 60;
        return (m < 10 ? "0" : "") + m + ":" + (s < 10 ? "0" : "") + s;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, E def) {
        if (value == null) return def;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
