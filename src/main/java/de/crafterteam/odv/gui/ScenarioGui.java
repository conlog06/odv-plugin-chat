package de.crafterteam.odv.gui;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.Settings;
import de.crafterteam.odv.feature.Feature;
import de.crafterteam.odv.game.GameState;
import de.crafterteam.odv.game.VoteManager;
import de.crafterteam.odv.scenario.Scenario;
import de.crafterteam.odv.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Aufgeräumtes Menü für Szenarien & Features (Tabs) und die Spieler-Abstimmung.
 * Host/Admin: Links = An/Aus, Rechts = zur Abstimmung stellen. Alle anderen sehen nur.
 */
public class ScenarioGui implements InventoryHolder {

    public enum Mode { SCENARIOS, FEATURES, VOTE }

    private static final int[] CONTENT = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private final OdvPlugin plugin;
    private final Mode mode;
    private final Inventory inv;
    private final Map<Integer, BiConsumer<Player, ClickType>> actions = new HashMap<>();
    private final boolean edit;

    private ScenarioGui(OdvPlugin plugin, Player viewer, Mode mode) {
        this.plugin = plugin;
        this.mode = mode;
        this.edit = canEdit(plugin, viewer);
        String title = switch (mode) {
            case SCENARIOS -> "§8» §6§lSzenarien";
            case FEATURES -> "§8» §b§lFeatures";
            case VOTE -> "§8» §d§lAbstimmung";
        };
        this.inv = Bukkit.createInventory(this, 54, Txt.c(title));
        render(viewer);
    }

    public static boolean canEdit(OdvPlugin plugin, Player p) {
        return p.hasPermission("odv.admin") || p.hasPermission("odv.host") || p.getUniqueId().equals(plugin.game().getHost());
    }

    public static void open(OdvPlugin plugin, Player p, Mode mode) {
        p.openInventory(new ScenarioGui(plugin, p, mode).getInventory());
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    private Settings s() {
        return plugin.settings();
    }

    // ================================================================= Aufbau

    private void render(Player viewer) {
        inv.clear();
        actions.clear();
        ItemStack dark = plugin.items().of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        ItemStack accent = plugin.items().of(switch (mode) {
            case SCENARIOS -> Material.ORANGE_STAINED_GLASS_PANE;
            case FEATURES -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case VOTE -> Material.MAGENTA_STAINED_GLASS_PANE;
        }).name(" ").build();
        for (int i = 0; i < 54; i++) inv.setItem(i, dark);
        for (int i = 9; i < 18; i++) inv.setItem(i, accent);
        for (int r = 2; r <= 4; r++) {
            inv.setItem(r * 9, accent);
            inv.setItem(r * 9 + 8, accent);
        }

        // Tabs
        tab(2, Material.TNT, "§6§lSzenarien", Mode.SCENARIOS, count(Settings.State.ON, true) + " an §8· §e" + count(Settings.State.VOTE, true) + " Abstimmung");
        tab(4, Material.NETHER_STAR, "§b§lFeatures", Mode.FEATURES, count(Settings.State.ON, false) + " an §8· §e" + count(Settings.State.VOTE, false) + " Abstimmung");
        if (plugin.votes().active() || mode == Mode.VOTE) {
            tab(6, Material.WRITABLE_BOOK, "§d§lAbstimmung", Mode.VOTE, plugin.votes().options().size() + " zur Wahl");
        }

        switch (mode) {
            case SCENARIOS -> {
                int i = 0;
                for (Scenario sc : Scenario.values()) entry(CONTENT[i++], sc, sc.icon, sc.display, sc.description, s().scenarioState.get(sc), true);
            }
            case FEATURES -> {
                int i = 0;
                for (Feature f : Feature.values()) {
                    if (i >= CONTENT.length) break;
                    entry(CONTENT[i++], f, f.icon, f.display, f.description, s().featureState.get(f), f.votable());
                }
            }
            case VOTE -> vote(viewer);
        }

        // Fußzeile
        if (mode != Mode.VOTE) {
            inv.setItem(49, plugin.items().of(Material.BOOK).name("§f§lLegende").lore(
                    "§a✔ An §8– §7immer aktiv",
                    "§e✦ Abstimmung §8– §7Spieler wählen in der Lobby",
                    "§c✘ Aus §8– §7nicht aktiv", "",
                    edit ? "§eLinks: §7An/Aus  §eRechts: §7Abstimmung" : "§8Nur Host/Admin können ändern").build());
        }
        if (edit) {
            set(51, plugin.items().of(Material.COMPARATOR, s().voteWinners).name("§d§lGewinner pro Abstimmung: §f" + s().voteWinners)
                    .lore("§7So viele Szenarien/Features", "§7werden aktiv – und so viele", "§7Stimmen hat jeder Spieler.", "",
                            "§8Links §a+1  §8Rechts §c-1").build(), (p, c) -> {
                s().voteWinners = Settings.clamp(s().voteWinners + (c.isLeftClick() ? 1 : -1), 1, 10);
                s().save();
                refresh(p);
            });
        }
        if (edit && viewer.hasPermission("odv.admin")) {
            set(45, plugin.items().of(Material.ARROW).name("§7« Setup").build(), (p, c) -> SetupGui.open(plugin, p, SetupGui.Page.MAIN));
        } else if (edit) {
            set(45, plugin.items().of(Material.ARROW).name("§7« Host-Menü").build(), (p, c) -> HostGui.open(plugin, p));
        }
        set(53, plugin.items().of(Material.BARRIER).name("§cSchließen").build(), (p, c) -> p.closeInventory());
    }

    private void tab(int slot, Material m, String name, Mode target, String info) {
        boolean active = mode == target;
        set(slot, plugin.items().of(m).name(name + (active ? " §8◀" : "")).lore("§7" + info, "", active ? "§aGeöffnet" : "§eKlick: §7öffnen")
                .glow(active).build(), (p, c) -> {
            if (!active) open(plugin, p, target);
        });
    }

    private int count(Settings.State st, boolean scenarios) {
        int n = 0;
        if (scenarios) {
            for (Settings.State v : s().scenarioState.values()) if (v == st) n++;
        } else {
            for (Settings.State v : s().featureState.values()) if (v == st) n++;
        }
        return n;
    }

    private void entry(int slot, Enum<?> key, Material icon, String name, String desc, Settings.State state, boolean votable) {
        boolean activeNow = key instanceof Scenario sc ? s().has(sc) : s().has((Feature) key);
        List<String> lore = new ArrayList<>(wrap(desc));
        lore.add("");
        lore.add("§7Status: " + state.label);
        if (state == Settings.State.VOTE) lore.add("§7Stimmen: §d" + plugin.votes().count(key));
        if (plugin.game().getState().isIngame()) lore.add(activeNow ? "§aIn dieser Runde aktiv" : "§8In dieser Runde nicht aktiv");
        if (edit) {
            lore.add("");
            lore.add("§eLinks: §7" + (state == Settings.State.ON ? "ausschalten" : "einschalten"));
            if (votable) lore.add("§eRechts: §7" + (state == Settings.State.VOTE ? "Abstimmung beenden" : "zur Abstimmung stellen"));
        }
        set(slot, plugin.items().of(icon).name(state.color + "§l" + name).lore(lore).glow(state == Settings.State.ON).build(), (p, c) -> {
            if (!edit) return;
            if (plugin.game().getState() != GameState.LOBBY) {
                Txt.send(p, s().prefix + "§cÄndern geht nur in der Lobby.");
                return;
            }
            Settings.State next;
            if (c.isRightClick()) {
                if (!votable) return;
                next = state == Settings.State.VOTE ? Settings.State.OFF : Settings.State.VOTE;
            } else next = state == Settings.State.ON ? Settings.State.OFF : Settings.State.ON;
            if (key instanceof Scenario sc) s().setState(sc, next);
            else s().setState((Feature) key, next);
            s().save();
            plugin.game().resetToLobbyItems(); // Abstimmungs-/Practice-Item ein- oder ausblenden
            refresh(p);
            refreshVoteMenus();
        });
    }

    private void vote(Player viewer) {
        List<Enum<?>> opts = plugin.votes().options();
        var mine = plugin.votes().of(viewer);
        int left = s().voteWinners - mine.size();
        inv.setItem(49, plugin.items().of(Material.PAPER).name("§d§lDeine Stimmen: §f" + mine.size() + "§7/" + s().voteWinners).lore(
                "§7Die §e" + s().voteWinners + " §7meistgewählten werden", "§7beim Start aktiv.", "",
                left > 0 ? "§aNoch " + left + " Stimme" + (left == 1 ? "" : "n") + " frei" : "§eAlle Stimmen vergeben").build());
        if (opts.isEmpty()) {
            inv.setItem(31, plugin.items().of(Material.BARRIER).name("§7Gerade steht nichts zur Wahl").build());
            return;
        }
        int total = Math.max(1, plugin.game().candidates().size());
        for (int i = 0; i < opts.size() && i < CONTENT.length; i++) {
            Enum<?> o = opts.get(i);
            int votes = plugin.votes().count(o);
            boolean picked = mine.contains(o);
            List<String> lore = new ArrayList<>(wrap(VoteManager.description(o)));
            lore.add("");
            lore.add("§7Stimmen: §d" + votes + " " + bar(votes, total));
            lore.add(picked ? "§a✔ Deine Stimme §8(Klick: zurücknehmen)" : "§eKlick: §7abstimmen");
            set(CONTENT[i], plugin.items().of(VoteManager.icon(o), Math.max(1, votes)).name((picked ? "§a§l" : "§f§l") + VoteManager.display(o)
                    + (o instanceof Feature ? " §8(Feature)" : "")).lore(lore).glow(picked).build(), (p, c) -> {
                plugin.votes().toggle(p, o);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, plugin.votes().of(p).contains(o) ? 1.6f : 0.9f);
                refreshVoteMenus();
            });
        }
    }

    private static String bar(int v, int total) {
        int filled = (int) Math.round(10.0 * v / total);
        return "§d" + "▌".repeat(Math.min(10, filled)) + "§8" + "▌".repeat(Math.max(0, 10 - filled));
    }

    private static List<String> wrap(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : text.split(" ")) {
            if (line.length() + w.length() > 30) {
                out.add("§7" + line.toString().trim());
                line.setLength(0);
            }
            line.append(w).append(' ');
        }
        if (!line.isEmpty()) out.add("§7" + line.toString().trim());
        return out;
    }

    private void set(int slot, ItemStack it, BiConsumer<Player, ClickType> a) {
        inv.setItem(slot, it);
        actions.put(slot, a);
    }

    private void refresh(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.3f);
        render(p);
    }

    /** Alle offenen Abstimmungs-/Szenario-Menüs aktualisieren (Live-Stimmen). */
    private void refreshVoteMenus() {
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.getOpenInventory().getTopInventory().getHolder(false) instanceof ScenarioGui g) g.render(o);
        }
    }

    public void click(Player p, int slot, ClickType click) {
        BiConsumer<Player, ClickType> a = actions.get(slot);
        if (a != null) a.accept(p, click);
    }
}
