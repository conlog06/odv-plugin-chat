package de.crafterteam.odv.gui;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.feature.Feature;
import de.crafterteam.odv.scenario.Scenario;
import de.crafterteam.odv.team.MTeam;
import de.crafterteam.odv.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Teams, Zuschauer, Wetten und Szenario-Übersicht – kleine Klick-Menüs. */
public class Menus implements InventoryHolder {

    public enum Kind { TEAMS, SPECTATE, BET, SCENARIOS, LEADERBOARD }

    private final OdvPlugin plugin;
    private final Kind kind;
    private final Inventory inv;
    private final Map<Integer, Consumer<Player>> actions = new HashMap<>();
    private final UUID viewer;
    private final int page;

    private Menus(OdvPlugin plugin, Kind kind, Player viewer, int rows, String title, int page) {
        this.plugin = plugin;
        this.kind = kind;
        this.viewer = viewer.getUniqueId();
        this.page = page;
        this.inv = Bukkit.createInventory(this, rows * 9, Txt.c(title));
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public Kind kind() {
        return kind;
    }

    public void click(Player p, int slot) {
        Consumer<Player> a = actions.get(slot);
        if (a != null) a.accept(p);
    }

    private void set(int slot, ItemStack it, Consumer<Player> a) {
        inv.setItem(slot, it);
        if (a != null) actions.put(slot, a);
    }

    private void bottomBar(int rows) {
        ItemStack glass = plugin.items().of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = (rows - 1) * 9; i < rows * 9; i++) inv.setItem(i, glass);
    }

    // ================================================================ Teams

    public static void teams(OdvPlugin plugin, Player p) {
        if (plugin.settings().isSolo()) {
            Txt.send(p, plugin.settings().prefix + "§7Es wird §fSolo §7gespielt – keine Teams.");
            return;
        }
        int rows = Math.min(6, (int) Math.ceil(plugin.teams().getTeams().size() / 9.0) + 1);
        Menus m = new Menus(plugin, Kind.TEAMS, p, rows, "§8» §b§lTeams", 0);
        m.renderTeams(rows);
        p.openInventory(m.inv);
    }

    private void renderTeams(int rows) {
        inv.clear();
        actions.clear();
        int size = plugin.settings().teamSize;
        MTeam own = plugin.teams().getTeam(viewer);
        List<MTeam> teams = plugin.teams().getTeams();
        for (int i = 0; i < teams.size() && i < (rows - 1) * 9; i++) {
            MTeam t = teams.get(i);
            boolean full = t.getMembers().size() >= size;
            List<String> lore = new ArrayList<>();
            lore.add("§7Spieler: " + (full ? "§c" : "§a") + t.getMembers().size() + "§7/" + size);
            for (UUID u : t.getMembers()) lore.add("§8• " + t.getColor() + plugin.stats().name(u));
            lore.add("");
            lore.add(t == own ? "§a✔ Dein Team" : full ? "§cVoll" : "§eKlick: §7beitreten");
            set(i, plugin.items().of(t.getWool(), Math.max(1, t.getMembers().size())).name(t.getColor() + "§l" + t.getName() + " §8#" + t.getNumber())
                    .lore(lore).glow(t == own).build(), p -> {
                if (!check(p)) return;
                if (plugin.teams().join(p, t)) p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.5f);
                else {
                    Txt.send(p, plugin.settings().prefix + "§cDieses Team ist voll.");
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                }
                refreshTeams();
            });
        }
        bottomBar(rows);
        int base = (rows - 1) * 9;
        set(base + 3, plugin.items().of(Material.NETHER_STAR).name("§e§lZufälliges Team").build(), p -> {
            if (!check(p)) return;
            if (!plugin.teams().joinRandom(p)) Txt.send(p, plugin.settings().prefix + "§cAlle Teams sind voll.");
            refreshTeams();
        });
        set(base + 5, plugin.items().of(Material.BARRIER).name("§c§lTeam verlassen").build(), p -> {
            plugin.teams().leave(p.getUniqueId());
            refreshTeams();
        });
    }

    private boolean check(Player p) {
        if (!plugin.game().getState().isLobby()) {
            p.closeInventory();
            Txt.send(p, plugin.settings().prefix + "§cTeams nur in der Lobby.");
            return false;
        }
        if (plugin.game().isOptedOut(p)) {
            Txt.send(p, plugin.settings().prefix + "§cDu schaust zu. §7(/odv join)");
            return false;
        }
        return true;
    }

    public void refreshTeams() {
        refreshAllTeams(plugin);
    }

    public static void refreshAllTeams(OdvPlugin plugin) {
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.getOpenInventory().getTopInventory().getHolder() instanceof Menus m && m.kind == Kind.TEAMS) {
                m.renderTeams(m.inv.getSize() / 9);
            }
        }
    }

    // ================================================================ Zuschauer

    public static void spectate(OdvPlugin plugin, Player p, int page) {
        if (plugin.game().isPlaying(p)) {
            Txt.send(p, plugin.settings().prefix + "§cNur für Zuschauer.");
            return;
        }
        if (plugin.settings().spectatorRadius > 0 && !p.hasPermission("odv.staff")) {
            Txt.send(p, plugin.settings().prefix + "§7Zuschauer bleiben im Spawnbereich §8(" + plugin.settings().spectatorRadius + " Blöcke)§7.");
            return;
        }
        Menus m = new Menus(plugin, Kind.SPECTATE, p, 6, "§8» §b§lSpieler beobachten", page);
        List<UUID> alive = new ArrayList<>(plugin.game().getAlive());
        for (int slot = 0; slot < 45; slot++) {
            int idx = page * 45 + slot;
            if (idx >= alive.size()) break;
            Player t = Bukkit.getPlayer(alive.get(idx));
            if (t == null) continue;
            MTeam team = plugin.teams().getTeam(t);
            ItemStack skull = plugin.items().of(Material.PLAYER_HEAD).name((team != null && !plugin.settings().isSolo() ? team.getColor() : "§b") + t.getName())
                    .lore("§7❤ §c" + String.format(java.util.Locale.ROOT, "%.1f", t.getHealth() / 2.0),
                            "§7Kills: §c" + plugin.game().getKills(t.getUniqueId()), "", "§eKlick: §7hinteleportieren").build();
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setOwningPlayer(t);
            skull.setItemMeta(meta);
            m.set(slot, skull, v -> {
                if (plugin.game().isAlive(t)) {
                    v.closeInventory();
                    v.teleport(t.getLocation().add(0, 2, 0));
                }
            });
        }
        m.bottomBar(6);
        if (page > 0) m.set(45, plugin.items().of(Material.ARROW).name("§7« Zurück").build(), v -> spectate(plugin, v, page - 1));
        if ((page + 1) * 45 < alive.size()) m.set(53, plugin.items().of(Material.ARROW).name("§7Weiter »").build(), v -> spectate(plugin, v, page + 1));
        m.set(49, plugin.items().of(Material.BARRIER).name("§cSchließen").build(), Player::closeInventory);
        p.openInventory(m.inv);
    }

    // ================================================================ Wetten

    public static void bet(OdvPlugin plugin, Player p) {
        if (!plugin.features().canBet(p)) {
            Txt.send(p, plugin.settings().prefix + "§c" + plugin.features().betBlockedReason(p));
            return;
        }
        List<MTeam> teams = plugin.game().aliveTeams();
        int rows = Math.min(6, (int) Math.ceil(teams.size() / 9.0) + 1);
        Menus m = new Menus(plugin, Kind.BET, p, rows, "§8» §a§lAuf wen wettest du?", 0);
        for (int i = 0; i < teams.size() && i < (rows - 1) * 9; i++) {
            MTeam t = teams.get(i);
            List<String> lore = new ArrayList<>();
            int alive = 0;
            for (UUID u : t.getMembers()) if (plugin.game().isAlive(u)) alive++;
            lore.add("§7Lebend: §a" + alive + " §8| §7Kills: §c" + t.getKills());
            lore.add("§eKlick: §7wetten");
            m.set(i, plugin.items().of(t.getWool()).name(plugin.settings().isSolo() ? "§f" + t.getName() : t.getDisplayName()).lore(lore).build(), v -> {
                v.closeInventory();
                plugin.features().placeBet(v, t);
            });
        }
        m.bottomBar(rows);
        p.openInventory(m.inv);
    }

    // ================================================================ Szenarien

    public static void scenarios(OdvPlugin plugin, Player p) {
        Menus m = new Menus(plugin, Kind.SCENARIOS, p, 4, "§8» §e§lSzenarien & Features", 0);
        int slot = 0;
        for (Scenario sc : Scenario.values()) {
            boolean on = plugin.settings().has(sc);
            m.set(slot++, plugin.items().of(sc.icon).name((on ? "§a" : "§8") + "§l" + sc.display + (on ? " §a✔" : " §c✘"))
                    .lore("§7" + sc.description).glow(on).build(), null);
        }
        slot = 18;
        for (Feature f : Feature.values()) {
            if (!plugin.settings().has(f)) continue;
            m.set(slot++, plugin.items().of(f.icon).name("§b§l" + f.display).lore("§7" + f.description).build(), null);
        }
        m.set(31, plugin.items().of(plugin.settings().oldPvp ? Material.WOODEN_SWORD : Material.NETHERITE_SWORD)
                .name("§f§lKampf: " + (plugin.settings().oldPvp ? "§a1.8.9-PvP" : "§b26.3-PvP")).build(), null);
        p.openInventory(m.inv);
    }

    // ================================================================ Leaderboard (/lb)

    private static final String[][] LB = {{"wins", "Siege", "GOLDEN_HELMET"}, {"kills", "Kills", "DIAMOND_SWORD"},
            {"p_kills", "Practice-Kills", "IRON_SWORD"}, {"bets", "Wettpunkte", "EMERALD"}};

    public static void leaderboard(OdvPlugin plugin, Player p, int category) {
        String[] cat = LB[Math.floorMod(category, LB.length)];
        plugin.stats().top(cat[0], 10, list -> {
            if (!p.isOnline()) return;
            Menus m = new Menus(plugin, Kind.LEADERBOARD, p, 4, "§8» §6§lLeaderboard §8– §e" + cat[1], category);
            int[] slots = {4, 12, 13, 14, 19, 20, 21, 22, 23, 24};
            for (int i = 0; i < list.size() && i < slots.length; i++) {
                var e = list.get(i);
                ItemStack head = plugin.items().of(Material.PLAYER_HEAD).name((i == 0 ? "§6§l" : i < 3 ? "§e§l" : "§f") + "#" + (i + 1) + " " + e.getKey())
                        .lore("§7" + cat[1] + ": §a" + e.getValue()).build();
                var owner = Bukkit.getOfflinePlayerIfCached(e.getKey()); // kein blockierender Mojang-Lookup
                if (owner != null) {
                    SkullMeta meta = (SkullMeta) head.getItemMeta();
                    meta.setOwningPlayer(owner);
                    head.setItemMeta(meta);
                }
                m.set(slots[i], head, null);
            }
            if (list.isEmpty()) m.set(13, plugin.items().of(Material.BARRIER).name("§7Noch keine Einträge").build(), null);
            m.bottomBar(4);
            for (int c = 0; c < LB.length; c++) {
                int cc = c;
                boolean active = c == Math.floorMod(category, LB.length);
                m.set(28 + c * 2, plugin.items().of(Material.valueOf(LB[c][2])).name((active ? "§a§l" : "§7") + LB[c][1]).glow(active).build(),
                        v -> leaderboard(plugin, v, cc));
            }
            p.openInventory(m.inv);
        });
    }
}
