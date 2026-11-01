package de.crafterteam.odv;

import de.crafterteam.odv.data.Arena;
import de.crafterteam.odv.data.Kits;
import de.crafterteam.odv.data.Stats;
import de.crafterteam.odv.game.GameManager;
import de.crafterteam.odv.listener.GameListener;
import de.crafterteam.odv.schematic.Wand;
import de.crafterteam.odv.spawn.SpawnManager;
import de.crafterteam.odv.team.TeamManager;
import de.crafterteam.odv.util.ChunkPregen;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class OdvPlugin extends JavaPlugin {

    private Settings settings;
    private Arena arena;
    private Kits kits;
    private Stats stats;
    private TeamManager teams;
    private SpawnManager spawns;
    private GameManager game;
    private Wand wand;
    private OdvCommand command;

    @Override
    public void onEnable() {
        if (!Bukkit.getBukkitVersion().startsWith("1.8")) {
            getLogger().warning("Gebaut für Spigot/Paper 1.8.8 – andere Versionen können Probleme machen.");
        }
        saveDefaultConfig();
        settings = new Settings(this);
        settings.load();
        spawns = new SpawnManager(this);
        spawns.ensureDefaults();
        arena = new Arena(this);
        arena.load();
        kits = new Kits(this);
        stats = new Stats(this);
        teams = new TeamManager(this);
        teams.reset();
        game = new GameManager(this);
        wand = new Wand(this);
        command = new OdvCommand(this);

        getCommand("odv").setExecutor(command);
        getCommand("odv").setTabCompleter(command);
        getCommand("team").setExecutor(command);
        getCommand("team").setTabCompleter(command);
        Bukkit.getPluginManager().registerEvents(new GameListener(this), this);
        Bukkit.getPluginManager().registerEvents(wand, this);

        game.applyWorldSettings();
        for (Player p : Bukkit.getOnlinePlayers()) game.onJoin(p);
        game.startTicker();
        getLogger().info(arena.isReady() ? "Bereit. Arena: " + arena.getWorld().getName()
                : "Noch keine Arena – ingame /odv → Schnellsetup.");
    }

    @Override
    public void onDisable() {
        ChunkPregen.stop();
        if (stats != null) stats.save();
        if (game != null && !game.getState().isLobby()) {
            for (Player p : Bukkit.getOnlinePlayers()) game.toLobby(p);
        }
        for (Player p : Bukkit.getOnlinePlayers()) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    public void reloadAll() {
        settings.load();
        arena.load();
        kits.load();
        teams.reset();
        game.applyWorldSettings();
        for (Player p : Bukkit.getOnlinePlayers()) if (game.getState().isLobby()) game.giveLobbyItems(p);
    }

    public Settings settings() { return settings; }
    public Arena arena() { return arena; }
    public Kits kits() { return kits; }
    public Stats stats() { return stats; }
    public TeamManager teams() { return teams; }
    public SpawnManager spawns() { return spawns; }
    public GameManager game() { return game; }
    public Wand wand() { return wand; }
    public OdvCommand command() { return command; }
}
