package de.crafterteam.meetup.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Flackerfreies 1.8-Scoreboard (Zeilen über Team-Prefix/Suffix, je max. 16 Zeichen).
 * Zusätzlich verwaltet es die farbigen Nametags auf demselben Scoreboard.
 */
public class Sidebar {

    private static final int MAX_LINES = 15;

    private final Scoreboard board;
    private final Objective objective;
    private int shown;

    public Sidebar(Player player, String title) {
        board = Bukkit.getScoreboardManager().getNewScoreboard();
        objective = board.registerNewObjective("meetup", "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        setTitle(title);
        for (int i = 0; i < MAX_LINES; i++) {
            Team t = board.registerNewTeam("line" + i);
            t.addEntry(entry(i));
        }
        player.setScoreboard(board);
    }

    public Scoreboard getBoard() {
        return board;
    }

    public void setTitle(String title) {
        objective.setDisplayName(title.length() > 32 ? title.substring(0, 32) : title);
    }

    private static String entry(int i) {
        return ChatColor.values()[i].toString() + ChatColor.RESET;
    }

    public void setLines(List<String> lines) {
        int size = Math.min(MAX_LINES, lines.size());
        for (int i = 0; i < size; i++) {
            int score = size - i;
            String text = lines.get(i);
            Team t = board.getTeam("line" + i);
            String[] split = split(text);
            if (!split[0].equals(t.getPrefix())) t.setPrefix(split[0]);
            if (!split[1].equals(t.getSuffix())) t.setSuffix(split[1]);
            objective.getScore(entry(i)).setScore(score);
        }
        for (int i = size; i < shown; i++) {
            board.resetScores(entry(i));
        }
        shown = size;
    }

    /** Teilt bis zu 32 Zeichen auf Prefix/Suffix auf und überträgt die Farbe. */
    private static String[] split(String text) {
        if (text.length() <= 16) return new String[]{text, ""};
        int cut = 16;
        if (text.charAt(cut - 1) == ChatColor.COLOR_CHAR) cut--;
        String prefix = text.substring(0, cut);
        String rest = text.substring(cut);
        String lastColors = ChatColor.getLastColors(prefix);
        if (!rest.startsWith(String.valueOf(ChatColor.COLOR_CHAR))) rest = (lastColors.isEmpty() ? "§f" : lastColors) + rest;
        if (rest.length() > 16) rest = rest.substring(0, 16);
        return new String[]{prefix, rest};
    }

    /**
     * Setzt Nametag-Teams: key -> (prefix, Spielernamen).
     */
    public void setNametags(Map<String, String> prefixes, Map<String, Set<String>> members) {
        Set<String> wanted = new HashSet<String>();
        for (Map.Entry<String, Set<String>> e : members.entrySet()) {
            String name = "nt" + e.getKey();
            if (name.length() > 16) name = name.substring(0, 16);
            wanted.add(name);
            Team t = board.getTeam(name);
            if (t == null) t = board.registerNewTeam(name);
            String prefix = prefixes.get(e.getKey());
            if (prefix == null) prefix = "";
            if (prefix.length() > 16) prefix = prefix.substring(0, 16);
            if (!prefix.equals(t.getPrefix())) t.setPrefix(prefix);
            for (String entry : new HashSet<String>(t.getEntries())) {
                if (!e.getValue().contains(entry)) t.removeEntry(entry);
            }
            for (String player : e.getValue()) {
                if (!t.hasEntry(player)) t.addEntry(player);
            }
        }
        for (Team t : new HashSet<Team>(board.getTeams())) {
            if (t.getName().startsWith("nt") && !wanted.contains(t.getName())) t.unregister();
        }
    }
}
