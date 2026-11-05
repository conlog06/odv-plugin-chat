package de.crafterteam.odv.gui;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.Settings;
import de.crafterteam.odv.game.GameState;
import de.crafterteam.odv.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Host-Menü: nur das, was ein Hoster für seine Runde braucht – Start/Stopp, Modus, Teams,
 * Szenarien & Abstimmung, PvP-Art. Keine Arena-, Speicher- oder Rechte-Einstellungen.
 */
public class HostGui implements InventoryHolder {

    private final OdvPlugin plugin;
    private final Inventory inv;
    private final Map<Integer, BiConsumer<Player, ClickType>> actions = new HashMap<>();

    private HostGui(OdvPlugin plugin) {
        this.plugin = plugin;
        this.inv = Bukkit.createInventory(this, 45, Txt.c("§8» §a§lHost-Menü"));
        render();
    }

    public static void open(OdvPlugin plugin, Player p) {
        if (!ScenarioGui.canEdit(plugin, p)) {
            Txt.send(p, plugin.settings().prefix + "§cDu bist nicht der Host dieser Runde.");
            return;
        }
        p.openInventory(new HostGui(plugin).getInventory());
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    private Settings s() {
        return plugin.settings();
    }

    private void render() {
        inv.clear();
        actions.clear();
        ItemStack glass = plugin.items().of(Material.LIME_STAINED_GLASS_PANE).name(" ").build();
        ItemStack dark = plugin.items().of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 45; i++) inv.setItem(i, i < 9 || i >= 36 ? glass : dark);
        GameState st = plugin.game().getState();

        inv.setItem(4, plugin.items().of(Material.GOLDEN_HELMET).name("§a§lDu hostest diese Runde").lore(
                "§7Modus: §f" + s().mode.display + " §8| §7Teams: §f" + (s().isSolo() ? "Solo" : s().teamSize + "er"),
                "§7Teilnehmer: §a" + plugin.game().candidates().size(), "§7Phase: §e" + st.display).build());

        if (st == GameState.LOBBY) {
            set(20, plugin.items().of(Material.EMERALD_BLOCK).name("§a§lRunde starten").lore("§7Startet in 10 Sekunden").glow(true).build(), (p, c) -> {
                p.closeInventory();
                String err = plugin.game().forceStart(10);
                if (err != null) Txt.send(p, s().prefix + err);
                else plugin.game().broadcast("§aRunde gestartet von Host §e" + p.getName() + "§a.");
            });
        } else {
            set(20, plugin.items().of(Material.REDSTONE_BLOCK).name("§c§lRunde stoppen").build(), (p, c) -> {
                p.closeInventory();
                plugin.game().stop();
            });
        }
        boolean meetup = s().mode == Settings.Mode.MEETUP;
        set(21, plugin.items().of(meetup ? Material.DIAMOND_SWORD : Material.IRON_PICKAXE).name("§e§lModus: " + s().mode.title)
                .lore("§eKlick: §7wechseln").build(), (p, c) -> {
            if (!lobby(p)) return;
            s().preset(meetup ? Settings.Mode.ODV : Settings.Mode.MEETUP);
            plugin.game().applyWorldSettings();
            refresh(p);
        });
        set(22, plugin.items().of(Material.PLAYER_HEAD, Math.max(1, s().teamSize)).name("§b§lTeamgröße: §f" + (s().isSolo() ? "Solo" : s().teamSize))
                .lore("§8Links §a+  §8Rechts §c-").build(), (p, c) -> {
            if (!lobby(p)) return;
            s().teamSize = Settings.clamp(s().teamSize + (c.isLeftClick() ? 1 : -1), 1, 10);
            plugin.teams().reset();
            s().save();
            refresh(p);
        });
        set(23, plugin.items().of(Material.BLUE_WOOL, Math.min(64, s().teamCount)).name("§b§lTeamanzahl: §f" + s().teamCount)
                .lore("§8Links §a+  §8Rechts §c-  §8Shift ±5").build(), (p, c) -> {
            if (!lobby(p)) return;
            int d = c.isShiftClick() ? 5 : 1;
            s().teamCount = Settings.clamp(s().teamCount + (c.isLeftClick() ? d : -d), 2, 45);
            plugin.teams().reset();
            s().save();
            refresh(p);
        });
        set(24, plugin.items().of(s().oldPvp ? Material.WOODEN_SWORD : Material.NETHERITE_SWORD).name("§f§lPvP: " + (s().oldPvp ? "§a1.8.9" : "§b26.3"))
                .lore("§eKlick: §7umschalten").build(), (p, c) -> {
            if (!lobby(p)) return;
            s().oldPvp = !s().oldPvp;
            s().save();
            plugin.combat().prepareAll();
            refresh(p);
        });
        set(30, plugin.items().of(Material.TNT).name("§6§lSzenarien").lore("§7An/Aus oder zur Abstimmung").build(),
                (p, c) -> ScenarioGui.open(plugin, p, ScenarioGui.Mode.SCENARIOS));
        set(31, plugin.items().of(Material.NETHER_STAR).name("§b§lFeatures").lore("§7Meteor, End-Finale, Supply-Drops …").build(),
                (p, c) -> ScenarioGui.open(plugin, p, ScenarioGui.Mode.FEATURES));
        set(32, plugin.items().of(Material.WRITABLE_BOOK).name("§d§lAbstimmung ansehen").lore("§7Live-Stimmen der Spieler").build(),
                (p, c) -> ScenarioGui.open(plugin, p, ScenarioGui.Mode.VOTE));
        set(44, plugin.items().of(Material.BARRIER).name("§cSchließen").build(), (p, c) -> p.closeInventory());
    }

    private boolean lobby(Player p) {
        if (plugin.game().getState() == GameState.LOBBY) return true;
        Txt.send(p, s().prefix + "§cGeht nur in der Lobby.");
        return false;
    }

    private void set(int slot, ItemStack it, BiConsumer<Player, ClickType> a) {
        inv.setItem(slot, it);
        actions.put(slot, a);
    }

    private void refresh(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.3f);
        render();
    }

    public void click(Player p, int slot, ClickType click) {
        BiConsumer<Player, ClickType> a = actions.get(slot);
        if (a != null) a.accept(p, click);
    }
}
