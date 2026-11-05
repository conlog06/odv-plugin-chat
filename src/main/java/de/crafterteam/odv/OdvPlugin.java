package de.crafterteam.odv;

import de.crafterteam.odv.data.Arena;
import de.crafterteam.odv.data.Kits;
import de.crafterteam.odv.data.Stats;
import de.crafterteam.odv.feature.FeatureManager;
import de.crafterteam.odv.game.AntiXray;
import de.crafterteam.odv.game.CombatManager;
import de.crafterteam.odv.game.GameManager;
import de.crafterteam.odv.game.PracticeManager;
import de.crafterteam.odv.listener.GameListener;
import de.crafterteam.odv.scenario.ScenarioManager;
import de.crafterteam.odv.schematic.Wand;
import de.crafterteam.odv.team.TeamManager;
import de.crafterteam.odv.util.Holograms;
import de.crafterteam.odv.util.Items;
import de.crafterteam.odv.world.ArenaGenerator;
import de.crafterteam.odv.world.ChunkPregen;
import de.crafterteam.odv.world.SpawnManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

public class OdvPlugin extends JavaPlugin {

    private Settings settings;
    private Items items;
    private Holograms holograms;
    private Arena arena;
    private Kits kits;
    private Stats stats;
    private TeamManager teams;
    private SpawnManager spawns;
    private CombatManager combat;
    private ScenarioManager scenarios;
    private FeatureManager features;
    private PracticeManager practice;
    private AntiXray antiXray;
    private GameManager game;
    private Wand wand;
    private OdvCommand command;
    private de.crafterteam.odv.data.RankManager ranks;
    private de.crafterteam.odv.util.Discord discord;
    private de.crafterteam.odv.game.VoteManager votes;
    private AdminCommands admin;
    private de.crafterteam.odv.feature.EventManager events;
    private de.crafterteam.odv.feature.Heatmap heatmap;
    private de.crafterteam.odv.game.Punishments punish;
    private de.crafterteam.odv.game.HostCommands host;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new Settings(this);
        settings.load();
        items = new Items(this);
        holograms = new Holograms(this);
        stats = new Stats(this);
        stats.open();
        ranks = new de.crafterteam.odv.data.RankManager(this);
        ranks.load();
        ranks.openTable();
        punish = new de.crafterteam.odv.game.Punishments(this);
        punish.open();
        discord = new de.crafterteam.odv.util.Discord(this);
        votes = new de.crafterteam.odv.game.VoteManager(this);
        admin = new AdminCommands(this);
        events = new de.crafterteam.odv.feature.EventManager(this);
        heatmap = new de.crafterteam.odv.feature.Heatmap(this);
        combat = new CombatManager(this);
        spawns = new SpawnManager(this);
        arena = new Arena(this);
        arena.load();
        kits = new Kits(this);
        teams = new TeamManager(this);
        teams.reset();
        scenarios = new ScenarioManager(this);
        features = new FeatureManager(this);
        practice = new PracticeManager(this);
        antiXray = new AntiXray(this);
        game = new GameManager(this);
        wand = new Wand(this);
        command = new OdvCommand(this);

        for (String c : new String[]{"odv", "team", "practice", "szenarios", "backpack", "ping", "coords", "mod", "lb", "teamliste", "changelog", "rank", "sc", "mute", "unmute"}) {
            var pc = getCommand(c);
            if (pc != null) {
                pc.setExecutor(command);
                pc.setTabCompleter(command);
            }
        }
        for (String c : AdminCommands.COMMANDS) {
            var pc = getCommand(c);
            if (pc != null) {
                pc.setExecutor(admin);
                pc.setTabCompleter(admin);
            }
        }
        host = new de.crafterteam.odv.game.HostCommands(this);
        for (String c : de.crafterteam.odv.game.HostCommands.COMMANDS) {
            var hc = getCommand(c);
            if (hc != null) {
                hc.setExecutor(host);
                hc.setTabCompleter(host);
            }
        }
        for (String c : de.crafterteam.odv.game.Punishments.COMMANDS) {
            var pc = getCommand(c);
            if (pc != null) {
                pc.setExecutor(punish);
                pc.setTabCompleter(punish);
            }
        }
        for (Listener l : new Listener[]{punish, host, admin, new GameListener(this), combat, scenarios, features, antiXray, wand}) {
            Bukkit.getPluginManager().registerEvents(l, this);
        }
        game.applyWorldSettings();
        game.refreshHolograms();
        for (Player p : Bukkit.getOnlinePlayers()) game.onJoin(p);
        game.startTicker();
        if (settings.worldReset != Settings.WorldReset.OFF) {
            spawns.cleanupOldWorlds();
            if (arena.isDirty()) {
                getLogger().info("Letzte Runde wurde gespielt - Arena wird neu erstellt.");
                Bukkit.getScheduler().runTaskLater(this, () -> spawns.resetArena(null), 40L);
            }
        }
        getLogger().info(arena.isReady() ? "Bereit - Arena: " + arena.getWorld().getName() : "Noch keine Arena - ingame /odv -> Schnellsetup.");
    }

    @Override
    public void onDisable() {
        ChunkPregen.stop();
        if (events != null) {
            events.restoreTickRate();
            events.clearPodium();
        }
        if (holograms != null) holograms.removeAll();
        if (game != null) {
            game.shutdown();
            if (!game.getState().isLobby()) for (Player p : Bukkit.getOnlinePlayers()) game.toLobby(p);
        }
        if (stats != null) stats.close();
        for (Player p : Bukkit.getOnlinePlayers()) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    /** Erlaubt "generator: ODVMeetup" in bukkit.yml bzw. Multiverse. */
    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new ArenaGenerator(this);
    }

    public void reloadAll() {
        settings.load();
        ranks.load();
        arena.load();
        kits.load();
        teams.reset();
        game.applyWorldSettings();
        game.refreshHolograms();
        game.resetToLobbyItems();
    }

    public Settings settings() { return settings; }
    public Items items() { return items; }
    public Holograms holograms() { return holograms; }
    public Arena arena() { return arena; }
    public Kits kits() { return kits; }
    public Stats stats() { return stats; }
    public TeamManager teams() { return teams; }
    public SpawnManager spawns() { return spawns; }
    public CombatManager combat() { return combat; }
    public ScenarioManager scenarios() { return scenarios; }
    public FeatureManager features() { return features; }
    public PracticeManager practice() { return practice; }
    public AntiXray antiXray() { return antiXray; }
    public GameManager game() { return game; }
    public Wand wand() { return wand; }
    public OdvCommand command() { return command; }
    public de.crafterteam.odv.data.RankManager ranks() { return ranks; }
    public de.crafterteam.odv.util.Discord discord() { return discord; }
    public de.crafterteam.odv.game.VoteManager votes() { return votes; }
    public AdminCommands admin() { return admin; }
    public de.crafterteam.odv.feature.EventManager events() { return events; }
    public de.crafterteam.odv.feature.Heatmap heatmap() { return heatmap; }
    public de.crafterteam.odv.game.Punishments punish() { return punish; }
    public de.crafterteam.odv.game.HostCommands host() { return host; }
}
