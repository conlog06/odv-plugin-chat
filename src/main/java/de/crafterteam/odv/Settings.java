package de.crafterteam.odv;

import de.crafterteam.odv.feature.Feature;
import de.crafterteam.odv.scenario.Scenario;
import de.crafterteam.odv.util.Txt;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** Alle Spielregeln aus der config.yml. Änderungen im Menü -> save(). */
public class Settings {

    public enum Mode {
        MEETUP("§b§lMEETUP", "Meetup"), ODV("§a§lODV", "ODV");
        public final String title, display;

        Mode(String title, String display) {
            this.title = title;
            this.display = display;
        }
    }

    public enum SpawnStyle {
        HOLES("Varo-Löcher"), PLATFORM("Schematic-Plattformen"), SCATTER("Zufällig verteilt");
        public final String display;

        SpawnStyle(String display) {
            this.display = display;
        }
    }

    private final OdvPlugin plugin;

    public Mode mode;
    public String prefix, serverIp;
    public int maxPlayers;

    public int teamSize, teamCount;
    public boolean friendlyFire;

    public int minPlayers, countdown, freeze, grace, endDelay, disconnectGrace, timeLimit;
    public boolean autoStart, naturalRegen, goldenApple, lightning, nether, stopOnEnd;

    public boolean oldPvp, swordBlocking, shields, oldGoldenApples, oldDamage;

    public int borderInitial, borderFinal, shrinkAfter, shrinkDuration;
    public double borderDamage;

    public double diamondRate, goldRate;
    public boolean plainsOnly, naturalOnly;

    public SpawnStyle spawnStyle;
    public String slab, teamSchematic;
    public boolean nameTags, regenerateOnStart;
    public int spawnRadius, minTeamDistance, protectionRadius, blocksPerTick;

    public boolean mysql;
    public String dbHost, dbName, dbUser, dbPassword, dbPrefix;
    public int dbPort;
    public boolean dbSsl;

    public boolean xrayDetection;
    public int xrayAlertVeins;

    public final Set<Scenario> scenarios = EnumSet.noneOf(Scenario.class);
    public int timebombSeconds;
    public double xtraAppleChance;

    public final Set<Feature> features = EnumSet.noneOf(Feature.class);
    public int supplyInterval;

    public boolean backupOnStart;
    public int backupKeep;

    public Settings(OdvPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        mode = parse(Mode.class, c.getString("mode"), Mode.MEETUP);
        prefix = Txt.color(c.getString("prefix", "&b&lODV &8» &7"));
        serverIp = c.getString("server-ip", "dein-server.net");
        maxPlayers = clamp(c.getInt("max-players", 50), 2, 1000);

        teamSize = clamp(c.getInt("teams.size", 2), 1, 10);
        teamCount = clamp(c.getInt("teams.count", 25), 2, 45);
        friendlyFire = c.getBoolean("teams.friendly-fire", false);

        minPlayers = clamp(c.getInt("match.min-players", 2), 1, 500);
        autoStart = c.getBoolean("match.auto-start", true);
        countdown = clamp(c.getInt("match.countdown", 30), 5, 600);
        freeze = clamp(c.getInt("match.freeze", 10), 0, 120);
        grace = clamp(c.getInt("match.grace", 30), 0, 7200);
        endDelay = clamp(c.getInt("match.end-delay", 15), 3, 300);
        disconnectGrace = clamp(c.getInt("match.disconnect-grace", 60), 0, 600);
        naturalRegen = c.getBoolean("match.natural-regeneration", true);
        goldenApple = c.getBoolean("match.golden-apple-on-death", true);
        lightning = c.getBoolean("match.lightning-on-death", true);
        nether = c.getBoolean("match.nether", true);
        timeLimit = clamp(c.getInt("match.time-limit", 45), 5, 600);
        stopOnEnd = c.getBoolean("match.stop-server-on-end", false);

        oldPvp = c.getBoolean("combat.old-pvp", true);
        swordBlocking = c.getBoolean("combat.sword-blocking", true);
        shields = c.getBoolean("combat.shields", false);
        oldGoldenApples = c.getBoolean("combat.old-golden-apples", true);
        oldDamage = c.getBoolean("combat.old-damage", true);

        borderInitial = clamp(c.getInt("border.initial", 600), 100, 20000);
        borderFinal = clamp(c.getInt("border.final", 40), 10, borderInitial);
        shrinkAfter = clamp(c.getInt("border.shrink-after", 300), 0, 14400);
        shrinkDuration = clamp(c.getInt("border.shrink-duration", 600), 30, 14400);
        borderDamage = Math.max(0, c.getDouble("border.damage", 1.0));

        diamondRate = Math.max(0, Math.min(10, c.getDouble("world.diamond-rate", 2.5)));
        goldRate = Math.max(0, Math.min(10, c.getDouble("world.gold-rate", 1.5)));
        plainsOnly = c.getBoolean("world.plains-only", true);
        naturalOnly = c.getBoolean("world.natural-only", true);

        spawnStyle = parse(SpawnStyle.class, c.getString("spawn.style"), SpawnStyle.HOLES);
        slab = c.getString("spawn.slab", "minecraft:smooth_stone_slab[type=top]");
        nameTags = c.getBoolean("spawn.name-tags", true);
        teamSchematic = c.getString("spawn.team-schematic", "plattform");
        spawnRadius = clamp(c.getInt("spawn.radius", 0), 0, 10000);
        minTeamDistance = clamp(c.getInt("spawn.min-team-distance", 40), 5, 1000);
        protectionRadius = clamp(c.getInt("spawn.protection-radius", 12), 0, 200);
        regenerateOnStart = c.getBoolean("spawn.regenerate-on-start", true);
        blocksPerTick = clamp(c.getInt("spawn.blocks-per-tick", 4000), 100, 50000);

        mysql = "MYSQL".equalsIgnoreCase(c.getString("storage.type", "LOCAL"));
        dbHost = c.getString("storage.mysql.host", "localhost");
        dbPort = c.getInt("storage.mysql.port", 3306);
        dbName = c.getString("storage.mysql.database", "odv");
        dbUser = c.getString("storage.mysql.user", "root");
        dbPassword = c.getString("storage.mysql.password", "");
        dbPrefix = c.getString("storage.mysql.table-prefix", "odv_").replaceAll("[^A-Za-z0-9_]", "");
        dbSsl = c.getBoolean("storage.mysql.ssl", false);

        xrayDetection = c.getBoolean("anti-xray.detection", true);
        xrayAlertVeins = clamp(c.getInt("anti-xray.alert-veins", 5), 1, 100);

        scenarios.clear();
        for (Scenario s : Scenario.values()) if (c.getBoolean("scenarios." + s.key(), false)) scenarios.add(s);
        timebombSeconds = clamp(c.getInt("scenarios.timebomb-seconds", 30), 5, 300);
        xtraAppleChance = Math.max(0, Math.min(100, c.getDouble("scenarios.xtra-apples-chance", 4.0)));

        features.clear();
        for (Feature f : Feature.values()) if (c.getBoolean("features." + f.key(), true)) features.add(f);
        supplyInterval = clamp(c.getInt("features.supply-interval", 300), 60, 3600);

        backupOnStart = c.getBoolean("backup.on-start", true);
        backupKeep = clamp(c.getInt("backup.keep", 10), 1, 100);
    }

    public void save() {
        validate();
        FileConfiguration c = plugin.getConfig();
        c.set("mode", mode.name());
        c.set("max-players", maxPlayers);
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
        c.set("match.nether", nether);
        c.set("match.time-limit", timeLimit);
        c.set("match.stop-server-on-end", stopOnEnd);
        c.set("combat.old-pvp", oldPvp);
        c.set("combat.sword-blocking", swordBlocking);
        c.set("combat.shields", shields);
        c.set("combat.old-golden-apples", oldGoldenApples);
        c.set("combat.old-damage", oldDamage);
        c.set("border.initial", borderInitial);
        c.set("border.final", borderFinal);
        c.set("border.shrink-after", shrinkAfter);
        c.set("border.shrink-duration", shrinkDuration);
        c.set("border.damage", borderDamage);
        c.set("world.diamond-rate", diamondRate);
        c.set("world.gold-rate", goldRate);
        c.set("world.plains-only", plainsOnly);
        c.set("world.natural-only", naturalOnly);
        c.set("spawn.style", spawnStyle.name());
        c.set("spawn.slab", slab);
        c.set("spawn.name-tags", nameTags);
        c.set("spawn.team-schematic", teamSchematic);
        c.set("spawn.radius", spawnRadius);
        c.set("spawn.min-team-distance", minTeamDistance);
        c.set("spawn.protection-radius", protectionRadius);
        c.set("spawn.regenerate-on-start", regenerateOnStart);
        c.set("spawn.blocks-per-tick", blocksPerTick);
        c.set("anti-xray.detection", xrayDetection);
        c.set("anti-xray.alert-veins", xrayAlertVeins);
        for (Scenario s : Scenario.values()) c.set("scenarios." + s.key(), scenarios.contains(s));
        c.set("scenarios.timebomb-seconds", timebombSeconds);
        c.set("scenarios.xtra-apples-chance", xtraAppleChance);
        for (Feature f : Feature.values()) c.set("features." + f.key(), features.contains(f));
        c.set("features.supply-interval", supplyInterval);
        c.set("backup.on-start", backupOnStart);
        c.set("backup.keep", backupKeep);
        plugin.saveConfig();
    }

    /** Zeiten/Border/Kit-Stil des Modus laden. Teams, Szenarien, Spawns bleiben. */
    public void preset(Mode m) {
        mode = m;
        if (m == Mode.ODV) {
            grace = 600;
            freeze = 5;
            borderInitial = 1500;
            borderFinal = 50;
            shrinkAfter = 1200;
            shrinkDuration = 900;
            timeLimit = 60;
            nether = true;
        } else {
            grace = 30;
            freeze = 10;
            borderInitial = 600;
            borderFinal = 40;
            shrinkAfter = 300;
            shrinkDuration = 600;
            timeLimit = 30;
        }
        save();
    }

    public boolean has(Scenario s) {
        return scenarios.contains(s);
    }

    public boolean has(Feature f) {
        return features.contains(f);
    }

    public boolean isSolo() {
        return teamSize <= 1;
    }

    public void validate() {
        if (borderFinal > borderInitial) borderFinal = borderInitial;
        if (shrinkAfter < grace) shrinkAfter = grace;
    }

    public static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
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
