package de.crafterteam.odv.gui;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.team.MTeam;
import de.crafterteam.odv.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Zuschauer: lebende Spieler als Köpfe, Klick = Teleport. */
public class SpectatorGui implements InventoryHolder {

    private final OdvPlugin plugin;
    private final Inventory inv;
    private final int page;
    private final java.util.Map<Integer, UUID> shown = new java.util.HashMap<Integer, UUID>();

    public SpectatorGui(OdvPlugin plugin, int page) {
        this.plugin = plugin;
        this.page = Math.max(0, page);
        this.inv = Bukkit.createInventory(this, 54, "§8» §b§lSpieler beobachten");
        render();
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public static void open(OdvPlugin plugin, Player p, int page) {
        if (plugin.game().isAlive(p) && plugin.game().getState().isIngame()) {
            p.sendMessage(plugin.settings().prefix + "§cNur für Zuschauer.");
            return;
        }
        p.openInventory(new SpectatorGui(plugin, page).getInventory());
    }

    private void render() {
        List<UUID> alive = new ArrayList<UUID>(plugin.game().getAlive());
        for (int slot = 0; slot < 45; slot++) {
            int idx = page * 45 + slot;
            if (idx >= alive.size()) break;
            UUID id = alive.get(idx);
            Player t = Bukkit.getPlayer(id);
            if (t == null) continue;
            MTeam team = plugin.teams().getTeam(id);
            ItemStack skull = new ItemBuilder(Material.SKULL_ITEM, 1, (short) 3)
                    .name((team != null && !plugin.settings().isSolo() ? team.getColor() : "§b") + t.getName())
                    .lore("§7❤ §c" + String.format(java.util.Locale.ROOT, "%.1f", t.getHealth() / 2.0),
                            "§7Kills: §c" + plugin.game().getKills(id),
                            team != null && !plugin.settings().isSolo() ? "§7Team: " + team.getDisplayName() : "",
                            "", "§eKlick: §7hinteleportieren").build();
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setOwner(t.getName());
            skull.setItemMeta(meta);
            inv.setItem(slot, skull);
            shown.put(slot, id);
        }
        for (int i = 45; i < 54; i++) inv.setItem(i, new ItemBuilder(Material.STAINED_GLASS_PANE, 1, (short) 15).name(" ").build());
        if (page > 0) inv.setItem(45, new ItemBuilder(Material.ARROW).name("§7« Zurück").build());
        if ((page + 1) * 45 < alive.size()) inv.setItem(53, new ItemBuilder(Material.ARROW).name("§7Weiter »").build());
        inv.setItem(49, new ItemBuilder(Material.BARRIER).name("§cSchließen").build());
    }

    public void click(Player p, int slot) {
        if (slot == 45 && page > 0) {
            open(plugin, p, page - 1);
        } else if (slot == 53 && inv.getItem(53) != null && inv.getItem(53).getType() == Material.ARROW) {
            open(plugin, p, page + 1);
        } else if (slot == 49) {
            p.closeInventory();
        } else if (shown.containsKey(slot)) {
            Player t = Bukkit.getPlayer(shown.get(slot));
            if (t != null && plugin.game().isAlive(t)) {
                p.closeInventory();
                p.teleport(t.getLocation().add(0, 2, 0));
            }
        }
    }
}
