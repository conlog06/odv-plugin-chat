package de.crafterteam.meetup;

import de.crafterteam.meetup.command.MeetupCommand;
import de.crafterteam.meetup.game.GameManager;
import de.crafterteam.meetup.listener.GameListener;
import de.crafterteam.meetup.schematic.Wand;
import de.crafterteam.meetup.spawn.SpawnManager;
import de.crafterteam.meetup.team.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class MeetupPlugin extends JavaPlugin {

    private Settings settings;
    private TeamManager teams;
    private SpawnManager spawns;
    private GameManager game;
    private Wand wand;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new Settings(this);
        settings.load();
        teams = new TeamManager(this);
        teams.reset();
        spawns = new SpawnManager(this);
        spawns.loadFromConfig();
        game = new GameManager(this);
        wand = new Wand(this);

        MeetupCommand cmd = new MeetupCommand(this);
        getCommand("meetup").setExecutor(cmd);
        getCommand("meetup").setTabCompleter(cmd);
        getCommand("teams").setExecutor(cmd);
        getCommand("teams").setTabCompleter(cmd);

        Bukkit.getPluginManager().registerEvents(new GameListener(this), this);
        Bukkit.getPluginManager().registerEvents(wand, this);

        // Spielwelt vorladen + Border setzen
        if (settings.getGameWorld() != null) {
            settings.getGameWorld().getWorldBorder().setCenter(settings.centerX + 0.5, settings.centerZ + 0.5);
            settings.getGameWorld().getWorldBorder().setSize(Math.max(10, settings.borderStart));
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            game.createBoard(p);
            game.toLobby(p);
        }
        game.startTicker();
        getLogger().info("CrafterMeetup aktiviert. Setup: /meetup setup");
    }

    @Override
    public void onDisable() {
        if (settings != null) settings.save();
    }

    public void reloadAll() {
        settings.load();
        spawns.loadFromConfig();
        if (game.getState().isLobby()) teams.reset();
    }

    public Settings settings() { return settings; }
    public TeamManager teams() { return teams; }
    public SpawnManager spawns() { return spawns; }
    public GameManager game() { return game; }
    public Wand wand() { return wand; }
}
