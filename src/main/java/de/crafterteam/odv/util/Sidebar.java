package de.crafterteam.odv.util;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Modernes Sidebar-Scoreboard: Zeilen als Components, ohne rote Zahlen. */
public class Sidebar {

    private static final int MAX = 15;

    private final Scoreboard board;
    private final Objective objective;
    private int shown;
    private String title = "";

    public Sidebar(Player player, String title) {
        board = Bukkit.getScoreboardManager().getNewScoreboard();
        objective = board.registerNewObjective("odv", Criteria.DUMMY, Txt.c(title));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());
        this.title = title;
        player.setScoreboard(board);
    }

    public Scoreboard getBoard() {
        return board;
    }

    public void setTitle(String t) {
        if (!t.equals(title)) {
            title = t;
            objective.displayName(Txt.c(t));
        }
    }

    public void setLines(List<String> lines) {
        int size = Math.min(MAX, lines.size());
        for (int i = 0; i < size; i++) {
            Score sc = objective.getScore("l" + i);
            sc.customName(Txt.c(lines.get(i)));
            sc.setScore(size - i);
        }
        for (int i = size; i < shown; i++) board.resetScores("l" + i);
        shown = size;
    }

    /** Nametag-Teams: key -> (Farbe, Prefix, Spieler). */
    public void setNametags(Map<String, NamedTextColor> colors, Map<String, String> prefixes, Map<String, Set<String>> members) {
        Set<String> wanted = new HashSet<>();
        for (Map.Entry<String, Set<String>> e : members.entrySet()) {
            String name = ("nt" + e.getKey());
            if (name.length() > 16) name = name.substring(0, 16);
            wanted.add(name);
            Team t = board.getTeam(name);
            if (t == null) t = board.registerNewTeam(name);
            NamedTextColor c = colors.get(e.getKey());
            if (c != null && !c.equals(t.color())) t.color(c);
            String pre = prefixes.getOrDefault(e.getKey(), "");
            t.prefix(Txt.c(pre));
            for (String entry : new HashSet<>(t.getEntries())) if (!e.getValue().contains(entry)) t.removeEntry(entry);
            for (String p : e.getValue()) if (!t.hasEntry(p)) t.addEntry(p);
        }
        for (Team t : new HashSet<>(board.getTeams())) if (t.getName().startsWith("nt") && !wanted.contains(t.getName())) t.unregister();
    }
}
