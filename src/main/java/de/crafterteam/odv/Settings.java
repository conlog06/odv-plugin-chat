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

    /** AN = immer, AUS = nie, ABSTIMMUNG = Spieler stimmen in der Lobby ab. */
    public enum State {
        OFF("§c✘ Aus", "§c"), ON("§a✔ An", "§a"), VOTE("§e✦ Abstimmung", "§e");
        public final String label, color;

        State(String label, String color) {
            this.label = label;
            this.color = color;
        }
    }

    public final java.util.Map<Scenario, State> scenarioState = new java.util.EnumMap<>(Scenario.class);
    public final java.util.Map<Feature, State> featureState = new java.util.EnumMap<>(Feature.class);
    public int voteWinners, meteorTime;
    /** Aktiv in der laufenden Runde (Lobby: nur „An“). */
    public final Set<Scenario> scenarios = EnumSet.noneOf(Scenario.class);
    public int timebombSeconds;
    public double xtraAppleChance;

    public final Set<Feature> features = EnumSet.noneOf(Feature.class);
    public int supplyInterval;

    public int spectatorRadius, chatSlowmode;
    public enum WorldReset {LIVE, RESTART, OFF}
    public WorldReset worldReset;
    public boolean combatLog, strikeCombatLog, strikeLeave;
    public int combatSeconds, strikeExpireDays;
    public final java.util.TreeMap<Integer, String> autoBan = new java.util.TreeMap<>();
    public String banAppeal;
    public String discordWebhook;
    public boolean discordResults;

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
        worldReset = parse(WorldReset.class, c.getString("world.reset-after-round"), WorldReset.LIVE);
        combatLog = c.getBoolean("combat-log.enabled", true);
        combatSeconds = clamp(c.getInt("combat-log.seconds", 15), 3, 120);
        strikeCombatLog = c.getBoolean("punishments.strike-combat-log", true);
        strikeLeave = c.getBoolean("punishments.strike-leave", true);
        strikeExpireDays = clamp(c.getInt("punishments.strike-expire-days", 30), 0, 3650);
        banAppeal = c.getString("punishments.appeal", "");
        autoBan.clear();
        var ab = c.getConfigurationSection("punishments.auto-ban");
        if (ab != null) for (String k : ab.getKeys(false)) {
            try {
                autoBan.put(Integer.parseInt(k.trim()), ab.getString(k, "1d"));
            } catch (NumberFormatException ignored) {
            }
        }

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

        for (Scenario s : Scenario.values()) scenarioState.put(s, state(c.get("scenarios." + s.key()), State.OFF));
        timebombSeconds = clamp(c.getInt("scenarios.timebomb-seconds", 30), 5, 300);
        xtraAppleChance = Math.max(0, Math.min(100, c.getDouble("scenarios.xtra-apples-chance", 4.0)));

        for (Feature f : Feature.values()) {
            State st = state(c.get("features." + f.key()), f == Feature.END_FINALE ? State.OFF : State.ON);
            featureState.put(f, st == State.VOTE && !f.votable() ? State.ON : st);
        }
        voteWinners = clamp(c.getInt("voting.winners", 3), 1, 10);
        meteorTime = clamp(c.getInt("features.meteor-time", 600), 60, 14400);
        resetActive();
        supplyInterval = clamp(c.getInt("features.supply-interval", 300), 60, 3600);

        spectatorRadius = clamp(c.getInt("match.spectator-radius", 150), 0, 10000);
        chatSlowmode = clamp(c.getInt("chat.slowmode", 2), 0, 60);
        discordWebhook = c.getString("discord.webhook-url", "").trim();
        discordResults = c.getBoolean("discord.round-results", true);

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
        for (Scenario s : Scenario.values()) c.set("scenarios." + s.key(), write(scenarioState.get(s)));
        c.set("scenarios.timebomb-seconds", timebombSeconds);
        c.set("scenarios.xtra-apples-chance", xtraAppleChance);
        for (Feature f : Feature.values()) c.set("features." + f.key(), write(featureState.get(f)));
        c.set("voting.winners", voteWinners);
        c.set("features.meteor-time", meteorTime);
        c.set("features.supply-interval", supplyInterval);
        c.set("match.spectator-radius", spectatorRadius);
        c.set("chat.slowmode", chatSlowmode);
        c.set("discord.round-results", discordResults);
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

    /** Aktive Sets auf „An“ zurücksetzen (Lobby). */
    public void resetActive() {
        scenarios.clear();
        features.clear();
        scenarioState.forEach((k, v) -> {
            if (v == State.ON) scenarios.add(k);
        });
        featureState.forEach((k, v) -> {
            if (v == State.ON) features.add(k);
        });
    }

    public void setState(Scenario s, State st) {
        scenarioState.put(s, st);
        resetActive();
    }

    public void setState(Feature f, State st) {
        featureState.put(f, st == State.VOTE && !f.votable() ? State.ON : st);
        resetActive();
    }

    private static State state(Object o, State def) {
        if (o instanceof Boolean b) return b ? State.ON : State.OFF;
        if (o instanceof String str) {
            return switch (str.trim().toLowerCase(Locale.ROOT)) {
                case "vote", "abstimmung", "voting" -> State.VOTE;
                case "true", "on", "an" -> State.ON;
                case "false", "off", "aus" -> State.OFF;
                default -> def;
            };
        }
        return def;
    }

    private static Object write(State st) {
        return st == State.VOTE ? "vote" : st == State.ON;
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
