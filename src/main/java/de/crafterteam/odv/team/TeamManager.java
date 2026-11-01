package de.crafterteam.odv.team;

import de.crafterteam.odv.OdvPlugin;
import org.bukkit.ChatColor;
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
    private static final short[] WOOL = {14, 11, 5, 4, 3, 6, 1, 10, 0, 8, 9, 13, 12, 15, 7, 11};

    private final OdvPlugin plugin;
    private final List<MTeam> teams = new ArrayList<MTeam>();

    public TeamManager(OdvPlugin plugin) {
        this.plugin = plugin;
    }

    /** Wählbare Teams neu aufbauen (Lobby). */
    public void reset() {
        teams.clear();
        if (plugin.settings().isSolo()) return;
        for (int i = 0; i < plugin.settings().teamCount; i++) {
            int c = i % 16;
            String name = NAMES[c] + (i >= 16 ? " " + (i / 16 + 1) : "");
            teams.add(new MTeam(i + 1, name, COLORS[c], WOOL[c]));
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
        MTeam t = getTeam(a);
        return t != null && t.getMembers().contains(b);
    }

    public boolean isFull(MTeam t) {
        return t.getMembers().size() >= plugin.settings().teamSize;
    }

    public void leave(UUID id) {
        for (MTeam t : teams) t.getMembers().remove(id);
    }

    public boolean join(Player p, MTeam team) {
        if (team.getMembers().contains(p.getUniqueId())) return true;
        if (isFull(team)) return false;
        leave(p.getUniqueId());
        team.getMembers().add(p.getUniqueId());
        return true;
    }

    /** Freies Team: zuerst angefangene Teams auffüllen. */
    public boolean joinRandom(Player p) {
        MTeam best = null;
        for (MTeam t : teams) {
            if (isFull(t) || t.getMembers().contains(p.getUniqueId())) continue;
            if (best == null || t.getMembers().size() > best.getMembers().size()) best = t;
        }
        return best != null && join(p, best);
    }

    /** Platz für alle Teilnehmer? */
    public boolean hasCapacity(int players) {
        return plugin.settings().isSolo() || players <= plugin.settings().teamCount * plugin.settings().teamSize;
    }

    /**
     * Vor dem Start: nur Teilnehmer behalten, Rest verteilen, leere Teams entfernen.
     * Solo: jeder Spieler bekommt sein eigenes Team.
     */
    public void finalizeTeams(Collection<? extends Player> players) {
        if (plugin.settings().isSolo()) {
            teams.clear();
            int i = 1;
            for (Player p : players) {
                MTeam t = new MTeam(i++, p.getName(), ChatColor.WHITE, (short) 0);
                t.getMembers().add(p.getUniqueId());
                teams.add(t);
            }
            return;
        }
        Set<UUID> ids = new HashSet<UUID>();
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
        List<MTeam> empty = new ArrayList<MTeam>();
        for (MTeam t : teams) if (t.getMembers().isEmpty()) empty.add(t);
        teams.removeAll(empty);
    }

    /** Anzahl der Teams, auf die die Teilnehmer verteilt würden. */
    public int countTeamsAfterFill(int players) {
        if (plugin.settings().isSolo()) return players;
        int used = 0, free = 0;
        for (MTeam t : teams) {
            if (!t.getMembers().isEmpty()) {
                used++;
                free += plugin.settings().teamSize - t.getMembers().size();
            }
        }
        int assigned = 0;
        for (MTeam t : teams) assigned += t.getMembers().size();
        int rest = Math.max(0, players - assigned - free);
        return used + (int) Math.ceil(rest / (double) plugin.settings().teamSize);
    }
}
