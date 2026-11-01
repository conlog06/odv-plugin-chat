package de.crafterteam.odv.game;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.data.Kits;
import de.crafterteam.odv.feature.Feature;
import de.crafterteam.odv.util.Txt;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Practice-Arena in der Lobby: Free-for-all, bis die Runde startet. */
public class PracticeManager {

    private final OdvPlugin plugin;
    private final Set<UUID> players = new HashSet<>();
    private final Random random = new Random();

    public PracticeManager(OdvPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isIn(Player p) {
        return players.contains(p.getUniqueId());
    }

    public Set<UUID> players() {
        return players;
    }

    public void toggle(Player p) {
        if (isIn(p)) leave(p, true);
        else join(p);
    }

    public void join(Player p) {
        Location spawn = plugin.arena().getPractice();
        if (!plugin.settings().has(Feature.PRACTICE) || spawn == null) {
            Txt.send(p, plugin.settings().prefix + "§cPractice ist nicht eingerichtet.");
            return;
        }
        if (!plugin.game().getState().isLobby()) {
            Txt.send(p, plugin.settings().prefix + "§cPractice geht nur in der Lobby.");
            return;
        }
        players.add(p.getUniqueId());
        respawn(p);
        Txt.title(p, "§3§lPractice", "§e/practice §7um zurückzukehren");
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 1f);
    }

    /** Spieler an einen zufälligen Punkt der Arena setzen und ausrüsten. */
    public void respawn(Player p) {
        Location spawn = plugin.arena().getPractice();
        if (spawn == null) return;
        plugin.game().resetPlayer(p, GameMode.SURVIVAL);
        double a = random.nextDouble() * Math.PI * 2, r = 4 + random.nextDouble() * 8;
        Location l = spawn.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
        l.setYaw(de.crafterteam.odv.world.SpawnManager.yawTo(l, spawn));
        p.teleport(l);
        plugin.kits().give(p, Kits.Type.PRACTICE);
    }

    public void leave(Player p, boolean toLobby) {
        if (!players.remove(p.getUniqueId())) return;
        if (toLobby && p.isOnline()) {
            plugin.game().toLobby(p);
            Txt.send(p, plugin.settings().prefix + "§7Practice verlassen.");
        }
    }

    public void leaveAll() {
        for (UUID id : new ArrayList<>(players)) {
            Player p = plugin.getServer().getPlayer(id);
            players.remove(id);
            if (p != null) plugin.game().toLobby(p);
        }
    }

    public void onKill(Player victim, Player killer) {
        plugin.stats().add(victim.getUniqueId(), "p_deaths");
        String msg = "§8[§3Practice§8] §c" + victim.getName() + " §7";
        if (killer != null && isIn(killer)) {
            plugin.stats().add(killer.getUniqueId(), "p_kills");
            msg += "wurde von §a" + killer.getName() + " §7getötet.";
            killer.setHealth(Math.min(killer.getHealth() + 6, 20));
        } else msg += "ist gestorben.";
        for (UUID id : players) {
            Player o = plugin.getServer().getPlayer(id);
            if (o != null) Txt.send(o, msg);
        }
    }
}
