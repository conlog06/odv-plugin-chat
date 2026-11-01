package de.crafterteam.odv.team;

import de.crafterteam.odv.OdvPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class TeamManager {

    private static final String[] NAMES = {"Rot", "Blau", "Grün", "Gelb", "Aqua", "Pink", "Orange", "Lila",
            "Weiß", "Grau", "Türkis", "Dunkelgrün", "Dunkelrot", "Schwarz", "Dunkelgrau", "Dunkelblau"};
    private static final ChatColor[] COLORS = {ChatColor.RED, ChatColor.BLUE, ChatColor.GREEN, ChatColor.YELLOW,
            ChatColor.AQUA, ChatColor.LIGHT_PURPLE, ChatColor.GOLD, ChatColor.DARK_PURPLE, ChatColor.WHITE,
            ChatColor.GRAY, ChatColor.DARK_AQUA, ChatColor.DARK_GREEN, ChatColor.DARK_RED, ChatColor.BLACK,
            ChatColor.DARK_GRAY, ChatColor.DARK_BLUE};
    private static final Material[] WOOL = {Material.RED_WOOL, Material.BLUE_WOOL, Material.LIME_WOOL, Material.YELLOW_WOOL,
            Material.LIGHT_BLUE_WOOL, Material.PINK_WOOL, Material.ORANGE_WOOL, Material.PURPLE_WOOL, Material.WHITE_WOOL,
            Material.LIGHT_GRAY_WOOL, Material.CYAN_WOOL, Material.GREEN_WOOL, Material.BROWN_WOOL, Material.BLACK_WOOL,
            Material.GRAY_WOOL, Material.BLUE_WOOL};

    private final OdvPlugin plugin;
    private final List<MTeam> teams = new ArrayList<>();

    public TeamManager(OdvPlugin plugin) {
        this.plugin = plugin;
    }

    public void reset() {
        teams.clear();
        if (plugin.settings().isSolo()) return;
        for (int i = 0; i < plugin.settings().teamCount; i++) {
            int c = i % 16;
            teams.add(new MTeam(i + 1, NAMES[c] + (i >= 16 ? " " + (i / 16 + 1) : ""), COLORS[c], WOOL[c]));
        }
    }

    public List<MTeam> getTeams() {
        return teams;
    }

    public MTeam getTeam(UUID id) {
        for (MTeam t : teams) if (t.getMembers().contains(id)) return t;
        return null;
    }

    public MTeam getTeam(Player p) {
        return getTeam(p.getUniqueId());
    }

    public MTeam byNumber(int n) {
        for (MTeam t : teams) if (t.getNumber() == n) return t;
        return null;
    }

    public boolean sameTeam(UUID a, UUID b) {
        if (a.equals(b)) return true;
        MTeam t = getTeam(a);
        return t != null && t.getMembers().contains(b);
    }

    public boolean isFull(MTeam t) {
        return t.getMembers().size() >= plugin.settings().teamSize;
    }

    public void leave(UUID id) {
        for (MTeam t : teams) t.getMembers().remove(id);
    }

    public boolean join(Player p, MTeam t) {
        if (t.getMembers().contains(p.getUniqueId())) return true;
        if (isFull(t)) return false;
        leave(p.getUniqueId());
        t.getMembers().add(p.getUniqueId());
        return true;
    }

    public boolean joinRandom(Player p) {
        MTeam best = null;
        for (MTeam t : teams) {
            if (isFull(t) || t.getMembers().contains(p.getUniqueId())) continue;
            if (best == null || t.getMembers().size() > best.getMembers().size()) best = t;
        }
        return best != null && join(p, best);
    }

    public boolean hasCapacity(int players) {
        return plugin.settings().isSolo() || players <= plugin.settings().teamCount * plugin.settings().teamSize;
    }

    public void finalizeTeams(Collection<? extends Player> players) {
        if (plugin.settings().isSolo()) {
            teams.clear();
            int i = 1;
            for (Player p : players) {
                MTeam t = new MTeam(i++, p.getName(), ChatColor.WHITE, Material.WHITE_WOOL);
                t.getMembers().add(p.getUniqueId());
                teams.add(t);
            }
            return;
        }
        Set<UUID> ids = new HashSet<>();
        for (Player p : players) ids.add(p.getUniqueId());
        for (MTeam t : teams) t.getMembers().retainAll(ids);
        for (Player p : players) {
            if (getTeam(p) != null) continue;
            MTeam best = null;
            for (MTeam t : teams) {
                if (isFull(t)) continue;
                if (best == null || t.getMembers().size() > best.getMembers().size()) best = t;
            }
            if (best != null) best.getMembers().add(p.getUniqueId());
        }
        teams.removeIf(t -> t.getMembers().isEmpty());
    }

    public int countTeamsAfterFill(int players) {
        if (plugin.settings().isSolo()) return players;
        int used = 0, free = 0, assigned = 0;
        for (MTeam t : teams) {
            assigned += t.getMembers().size();
            if (!t.getMembers().isEmpty()) {
                used++;
                free += plugin.settings().teamSize - t.getMembers().size();
            }
        }
        int rest = Math.max(0, players - assigned - free);
        return used + (int) Math.ceil(rest / (double) plugin.settings().teamSize);
    }
}
