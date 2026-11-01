package de.crafterteam.odv.gui;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.team.MTeam;
import de.crafterteam.odv.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Teamauswahl: bis zu 36 Teams als farbige Wolle. */
public class TeamGui implements InventoryHolder {

    private final OdvPlugin plugin;
    private final Inventory inv;
    private final int rows;
    private final UUID viewer;

    public TeamGui(OdvPlugin plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer.getUniqueId();
        int teamRows = (int) Math.ceil(plugin.teams().getTeams().size() / 9.0);
        this.rows = Math.min(6, Math.max(1, teamRows) + 1);
        this.inv = Bukkit.createInventory(this, rows * 9, "§8» §b§lTeams");
        render();
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public static void open(OdvPlugin plugin, Player p) {
        if (plugin.settings().isSolo()) {
            p.sendMessage(plugin.settings().prefix + "§7Aktuell wird §fSolo §7gespielt – keine Teams.");
            return;
        }
        p.openInventory(new TeamGui(plugin, p).getInventory());
    }

    public void render() {
        inv.clear();
        List<MTeam> teams = plugin.teams().getTeams();
        int size = plugin.settings().teamSize;
        MTeam own = plugin.teams().getTeam(viewer);
        for (int i = 0; i < teams.size() && i < (rows - 1) * 9; i++) {
            MTeam t = teams.get(i);
            boolean full = t.getMembers().size() >= size;
            List<String> lore = new ArrayList<String>();
            lore.add("§7Spieler: " + (full ? "§c" : "§a") + t.getMembers().size() + "§7/" + size);
            for (UUID u : t.getMembers()) lore.add("§8• " + t.getColor() + plugin.stats().name(u));
            lore.add("");
            lore.add(t == own ? "§a✔ Dein Team" : full ? "§cVoll" : "§eKlick: §7beitreten");
            inv.setItem(i, new ItemBuilder(Material.WOOL, Math.max(1, t.getMembers().size()), t.getWoolData())
                    .name(t.getColor() + "§l" + t.getName() + " §8#" + t.getNumber()).lore(lore).glow(t == own).build());
        }
        int base = (rows - 1) * 9;
        for (int i = base; i < base + 9; i++) inv.setItem(i, new ItemBuilder(Material.STAINED_GLASS_PANE, 1, (short) 15).name(" ").build());
        inv.setItem(base + 3, new ItemBuilder(Material.NETHER_STAR).name("§e§lZufälliges Team").lore("§7Füllt angefangene Teams auf").build());
        inv.setItem(base + 5, new ItemBuilder(Material.BARRIER).name("§c§lTeam verlassen").build());
    }

    public void click(Player p, int slot) {
        if (!plugin.game().getState().isLobby()) {
            p.closeInventory();
            p.sendMessage(plugin.settings().prefix + "§cTeams können nur in der Lobby gewählt werden.");
            return;
        }
        if (plugin.game().isOptedOut(p)) {
            p.sendMessage(plugin.settings().prefix + "§cDu schaust zu. §7(/odv join)");
            return;
        }
        List<MTeam> teams = plugin.teams().getTeams();
        int base = (rows - 1) * 9;
        if (slot >= 0 && slot < base && slot < teams.size()) {
            MTeam t = teams.get(slot);
            if (plugin.teams().join(p, t)) {
                p.sendMessage(plugin.settings().prefix + "§7Du bist jetzt in " + t.getDisplayName() + "§7.");
                p.playSound(p.getLocation(), Sound.CLICK, 1f, 1.5f);
            } else {
                p.sendMessage(plugin.settings().prefix + "§cDieses Team ist voll.");
                p.playSound(p.getLocation(), Sound.VILLAGER_NO, 1f, 1f);
            }
        } else if (slot == base + 3) {
            if (!plugin.teams().joinRandom(p)) p.sendMessage(plugin.settings().prefix + "§cAlle Teams sind voll.");
            else p.sendMessage(plugin.settings().prefix + "§7Du bist jetzt in " + plugin.teams().getTeam(p).getDisplayName() + "§7.");
        } else if (slot == base + 5) {
            plugin.teams().leave(p.getUniqueId());
            p.sendMessage(plugin.settings().prefix + "§7Team verlassen.");
        } else {
            return;
        }
        refreshAll(plugin);
    }

    public static void refreshAll(OdvPlugin plugin) {
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.getOpenInventory() != null && o.getOpenInventory().getTopInventory().getHolder() instanceof TeamGui) {
                ((TeamGui) o.getOpenInventory().getTopInventory().getHolder()).render();
            }
        }
    }
}
