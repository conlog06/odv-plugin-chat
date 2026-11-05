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
        leave(p.getUniqueId());
        MTeam t = pickTeam(Math.max(2, plugin.game().candidates().size()));
        return t != null && join(p, t);
    }

    /**
     * Smartes Auffüllen: so viele Teams wie nötig (mindestens 2), und immer in das Team mit den
     * wenigsten Spielern – so landet niemand automatisch mit allen anderen im selben Team.
     */
    private MTeam pickTeam(int totalPlayers) {
        int size = Math.max(1, plugin.settings().teamSize);
        int used = 0;
        for (MTeam t : teams) if (!t.getMembers().isEmpty()) used++;
        int target = Math.min(teams.size(), Math.max(used, Math.max(Math.min(2, totalPlayers), (int) Math.ceil(totalPlayers / (double) size))));
        MTeam best = null;
        int activated = used;
        for (MTeam t : teams) {
            if (isFull(t)) continue;
            if (t.getMembers().isEmpty()) {
                if (activated >= target) continue; // kein zusätzliches Team aufmachen
                activated++;
            }
            if (best == null || t.getMembers().size() < best.getMembers().size()) best = t;
        }
        if (best == null) { // Ziel-Teams voll → irgendein freies Team
            for (MTeam t : teams) if (!isFull(t) && (best == null || t.getMembers().size() < best.getMembers().size())) best = t;
        }
        return best;
    }

    /** Team für einen Latejoin: Team mit den wenigsten Spielern (Solo: neues eigenes Team). */
    public MTeam lateTeam(Player p) {
        if (plugin.settings().isSolo()) {
            int n = 1;
            for (MTeam t : teams) n = Math.max(n, t.getNumber() + 1);
            MTeam t = new MTeam(n, p.getName(), ChatColor.WHITE, Material.WHITE_WOOL);
            teams.add(t);
            return t;
        }
        MTeam best = null;
        for (MTeam t : teams) if (!isFull(t) && (best == null || t.getMembers().size() < best.getMembers().size())) best = t;
        return best;
    }

    /** Team nach Nummer oder Name (z. B. "3" oder "Rot"). */
    public MTeam find(String in) {
        try {
            MTeam t = byNumber(Integer.parseInt(in));
            if (t != null) return t;
        } catch (NumberFormatException ignored) {
        }
        for (MTeam t : teams) if (t.getName().equalsIgnoreCase(in)) return t;
        if (!plugin.settings().isSolo()) { // Team wurde beim Start entfernt (leer) → neu anlegen
            for (int i = 0; i < plugin.settings().teamCount; i++) {
                int c = i % 16;
                String name = NAMES[c] + (i >= 16 ? " " + (i / 16 + 1) : "");
                if (String.valueOf(i + 1).equals(in) || name.equalsIgnoreCase(in)) {
                    if (byNumber(i + 1) != null) return byNumber(i + 1);
                    MTeam t = new MTeam(i + 1, name, COLORS[c], WOOL[c]);
                    teams.add(t);
                    return t;
                }
            }
        }
        return null;
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
        List<Player> open = new ArrayList<>();
        for (Player p : players) if (getTeam(p) == null) open.add(p);
        java.util.Collections.shuffle(open);
        for (Player p : open) {
            MTeam t = pickTeam(players.size());
            if (t == null) continue;
            t.getMembers().add(p.getUniqueId());
            de.crafterteam.odv.util.Txt.send(p, plugin.settings().prefix + "§7Du wurdest automatisch in Team " + t.getDisplayName() + " §7eingeteilt.");
        }
        // Alle im selben Team? → aufteilen, sonst wäre die Runde sofort vorbei
        List<MTeam> used = new ArrayList<>();
        for (MTeam t : teams) if (!t.getMembers().isEmpty()) used.add(t);
        if (used.size() == 1 && used.get(0).getMembers().size() >= 2) {
            MTeam full = used.get(0);
            MTeam other = null;
            for (MTeam t : teams) if (t != full) {
                other = t;
                break;
            }
            if (other != null) {
                List<UUID> m = new ArrayList<>(full.getMembers());
                java.util.Collections.shuffle(m);
                for (int i = 0; i < m.size() / 2; i++) {
                    full.getMembers().remove(m.get(i));
                    other.getMembers().add(m.get(i));
                    Player p = org.bukkit.Bukkit.getPlayer(m.get(i));
                    if (p != null) de.crafterteam.odv.util.Txt.send(p, plugin.settings().prefix + "§7Alle waren im selben Team – du spielst jetzt in Team " + other.getDisplayName() + "§7.");
                }
            }
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
        int byCapacity = used + (int) Math.ceil(rest / (double) plugin.settings().teamSize);
        int smart = Math.max(used, Math.max(Math.min(2, players), (int) Math.ceil(players / (double) Math.max(1, plugin.settings().teamSize))));
        return Math.min(Math.max(1, teams.size()), Math.max(byCapacity, smart));
    }
}
