package de.crafterteam.odv.game;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.Settings;
import de.crafterteam.odv.feature.Feature;
import de.crafterteam.odv.scenario.Scenario;
import de.crafterteam.odv.util.Txt;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lobby-Abstimmung über Szenarien/Features mit Status „Abstimmung“.
 * Jeder Spieler hat so viele Stimmen, wie es Gewinner gibt; beim Start werden die meistgewählten aktiv.
 */
public class VoteManager {

    private final OdvPlugin plugin;
    private final Map<UUID, Set<Enum<?>>> votes = new HashMap<>();

    public VoteManager(OdvPlugin plugin) {
        this.plugin = plugin;
    }

    private Settings s() {
        return plugin.settings();
    }

    /** Alles, worüber gerade abgestimmt werden kann. */
    public List<Enum<?>> options() {
        List<Enum<?>> l = new ArrayList<>();
        for (Scenario sc : Scenario.values()) if (s().scenarioState.get(sc) == Settings.State.VOTE) l.add(sc);
        for (Feature f : Feature.values()) if (s().featureState.get(f) == Settings.State.VOTE) l.add(f);
        return l;
    }

    public boolean active() {
        return !options().isEmpty();
    }

    public Set<Enum<?>> of(Player p) {
        return votes.getOrDefault(p.getUniqueId(), Set.of());
    }

    public int count(Enum<?> option) {
        int n = 0;
        for (Set<Enum<?>> v : votes.values()) if (v.contains(option)) n++;
        return n;
    }

    public void toggle(Player p, Enum<?> option) {
        if (!plugin.game().getState().isLobby()) {
            Txt.send(p, s().prefix + "§cAbstimmen geht nur in der Lobby.");
            return;
        }
        if (!options().contains(option)) return;
        Set<Enum<?>> mine = votes.computeIfAbsent(p.getUniqueId(), k -> new LinkedHashSet<>());
        if (mine.remove(option)) return;
        if (mine.size() >= s().voteWinners) {
            Txt.send(p, s().prefix + "§cDu hast schon §e" + s().voteWinners + " §cStimmen vergeben – nimm erst eine zurück.");
            return;
        }
        mine.add(option);
    }

    public void remove(UUID id) {
        votes.remove(id);
    }

    public void reset() {
        votes.clear();
    }

    /** Beim Rundenstart: Gewinner aktivieren und verkünden. */
    public void apply() {
        List<Enum<?>> opts = options();
        if (opts.isEmpty()) return;
        List<Enum<?>> sorted = new ArrayList<>(opts);
        Collections.shuffle(sorted); // Gleichstand = Zufall
        sorted.sort((a, b) -> count(b) - count(a));
        List<String> won = new ArrayList<>();
        for (int i = 0; i < sorted.size() && won.size() < s().voteWinners; i++) {
            Enum<?> e = sorted.get(i);
            if (count(e) == 0) break;
            if (e instanceof Scenario sc) {
                s().scenarios.add(sc);
                won.add(sc.display + " §8(" + count(e) + ")");
            } else if (e instanceof Feature f) {
                s().features.add(f);
                won.add(f.display + " §8(" + count(e) + ")");
            }
        }
        if (won.isEmpty()) plugin.game().broadcast("§7Abstimmung: §8keine Stimmen – nichts zusätzlich aktiv.");
        else plugin.game().broadcast("§d§lAbstimmung entschieden: §e" + String.join("§7, §e", won));
        votes.clear();
    }

    public static String display(Enum<?> e) {
        return e instanceof Scenario sc ? sc.display : e instanceof Feature f ? f.display : e.name();
    }

    public static String description(Enum<?> e) {
        return e instanceof Scenario sc ? sc.description : e instanceof Feature f ? f.description : "";
    }

    public static org.bukkit.Material icon(Enum<?> e) {
        return e instanceof Scenario sc ? sc.icon : e instanceof Feature f ? f.icon : org.bukkit.Material.PAPER;
    }
}
