package de.crafterteam.meetup.team;

import de.crafterteam.meetup.MeetupPlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class TeamManager {

    private static final String[] NAMES = {"Rot", "Blau", "Grün", "Gelb", "Aqua", "Pink", "Orange", "Lila",
            "Weiß", "Grau", "Türkis", "Dunkelgrün", "Dunkelrot", "Schwarz", "Dunkelgrau", "Dunkelblau"};
    private static final ChatColor[] COLORS = {ChatColor.RED, ChatColor.BLUE, ChatColor.GREEN, ChatColor.YELLOW,
            ChatColor.AQUA, ChatColor.LIGHT_PURPLE, ChatColor.GOLD, ChatColor.DARK_PURPLE, ChatColor.WHITE,
            ChatColor.GRAY, ChatColor.DARK_AQUA, ChatColor.DARK_GREEN, ChatColor.DARK_RED, ChatColor.BLACK,
            ChatColor.DARK_GRAY, ChatColor.DARK_BLUE};
    private static final short[] WOOL = {14, 11, 5, 4, 3, 6, 1, 10, 0, 8, 9, 13, 12, 15, 7, 11};

    private final MeetupPlugin plugin;
    private final List<MTeam> teams = new ArrayList<MTeam>();

    public TeamManager(MeetupPlugin plugin) {
        this.plugin = plugin;
    }

    /** Baut die wählbaren Teams neu auf (Lobby). */
    public void reset() {
        teams.clear();
        if (!plugin.settings().isSolo()) {
            for (int i = 0; i < plugin.settings().teamCount; i++) {
                teams.add(new MTeam(i, NAMES[i], COLORS[i], WOOL[i]));
            }
        }
    }

    public List<MTeam> getTeams() {
        return teams;
    }

    public MTeam getTeam(UUID uuid) {
        for (MTeam t : teams) if (t.getMembers().contains(uuid)) return t;
        return null;
    }

    public MTeam getTeam(Player p) {
        return getTeam(p.getUniqueId());
    }

    public boolean isFull(MTeam t) {
        return t.getMembers().size() >= plugin.settings().teamSize;
    }

    public void leave(Player p) {
        for (MTeam t : teams) t.getMembers().remove(p.getUniqueId());
    }

    /** @return false, wenn Team voll */
    public boolean join(Player p, MTeam team) {
        if (team.getMembers().contains(p.getUniqueId())) return true;
        if (isFull(team)) return false;
        leave(p);
        team.getMembers().add(p.getUniqueId());
        return true;
    }

    public boolean joinRandom(Player p) {
        List<MTeam> open = new ArrayList<MTeam>();
        for (MTeam t : teams) if (!isFull(t)) open.add(t);
        if (open.isEmpty()) return false;
        // bevorzugt Teams, in denen schon jemand ist (füllt Teams auf)
        Collections.shuffle(open);
        Collections.sort(open, new Comparator<MTeam>() {
            @Override
            public int compare(MTeam a, MTeam b) {
                return b.getMembers().size() - a.getMembers().size();
            }
        });
        return join(p, open.get(0));
    }

    /**
     * Vor dem Start: Spieler ohne Team verteilen, leere Teams entfernen.
     * Im Solo-Modus bekommt jeder Spieler sein eigenes Team.
     */
    public void finalizeTeams(Collection<? extends Player> players) {
        if (plugin.settings().isSolo()) {
            teams.clear();
            int i = 0;
            for (Player p : players) {
                MTeam t = new MTeam(i++, p.getName(), ChatColor.WHITE, (short) 0);
                t.getMembers().add(p.getUniqueId());
                teams.add(t);
            }
            return;
        }
        // Offline-Spieler entfernen
        for (MTeam t : teams) {
            List<UUID> remove = new ArrayList<UUID>();
            for (UUID u : t.getMembers()) {
                boolean online = false;
                for (Player p : players) if (p.getUniqueId().equals(u)) online = true;
                if (!online) remove.add(u);
            }
            t.getMembers().removeAll(remove);
        }
        for (Player p : players) {
            if (getTeam(p) != null) continue;
            // zuerst angefangene Teams auffüllen, sonst ein leeres Team nehmen
            MTeam best = null;
            for (MTeam t : teams) {
                if (isFull(t)) continue;
                if (best == null || t.getMembers().size() > best.getMembers().size()) best = t;
            }
            if (best == null) { // alles voll -> kleinstes Team überfüllen
                for (MTeam t : teams) if (best == null || t.getMembers().size() < best.getMembers().size()) best = t;
            }
            if (best != null) best.getMembers().add(p.getUniqueId());
        }
        List<MTeam> empty = new ArrayList<MTeam>();
        for (MTeam t : teams) if (t.getMembers().isEmpty()) empty.add(t);
        teams.removeAll(empty);
    }
}
