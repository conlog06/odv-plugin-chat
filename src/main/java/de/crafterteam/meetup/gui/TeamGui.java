package de.crafterteam.meetup.gui;

import de.crafterteam.meetup.MeetupPlugin;
import de.crafterteam.meetup.team.MTeam;
import de.crafterteam.meetup.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Teamauswahl: Wolle pro Team, unten Zufall/Verlassen.
 */
public class TeamGui implements InventoryHolder {

    private final MeetupPlugin plugin;
    private final Inventory inv;
    private final int rows;

    public TeamGui(MeetupPlugin plugin) {
        this.plugin = plugin;
        int teamRows = (int) Math.ceil(plugin.teams().getTeams().size() / 9.0);
        this.rows = Math.min(6, Math.max(1, teamRows) + 1);
        this.inv = Bukkit.createInventory(this, rows * 9, "§bTeams");
        render();
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public void open(Player p) {
        p.openInventory(inv);
    }

    @SuppressWarnings("deprecation")
    public void render() {
        inv.clear();
        List<MTeam> teams = plugin.teams().getTeams();
        int size = plugin.settings().teamSize;
        for (int i = 0; i < teams.size() && i < (rows - 1) * 9; i++) {
            MTeam t = teams.get(i);
            List<String> lore = new ArrayList<String>();
            lore.add("§7Spieler: " + (t.getMembers().size() >= size ? "§c" : "§a") + t.getMembers().size() + "§7/" + size);
            lore.add("");
            for (UUID u : t.getMembers()) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(u);
                lore.add("§8• " + t.getColor() + (op.getName() == null ? "?" : op.getName()));
            }
            lore.add("");
            lore.add(t.getMembers().size() >= size ? "§cTeam ist voll" : "§eKlicke zum Beitreten");
            int amount = Math.max(1, t.getMembers().size());
            inv.setItem(i, new ItemBuilder(Material.WOOL, amount, t.getWoolData())
                    .name(t.getColor() + "§lTeam " + t.getName()).lore(lore).build());
        }
        int base = (rows - 1) * 9;
        for (int i = base; i < base + 9; i++) {
            inv.setItem(i, new ItemBuilder(Material.STAINED_GLASS_PANE, 1, (short) 15).name(" ").build());
        }
        inv.setItem(base + 3, new ItemBuilder(Material.NETHER_STAR).name("§e§lZufälliges Team").lore("§7Tritt einem freien Team bei").build());
        inv.setItem(base + 5, new ItemBuilder(Material.BARRIER).name("§c§lTeam verlassen").build());
    }

    public void click(Player p, int slot) {
        List<MTeam> teams = plugin.teams().getTeams();
        int base = (rows - 1) * 9;
        if (!plugin.game().getState().isLobby()) {
            p.closeInventory();
            p.sendMessage(plugin.settings().prefix + "§cTeams können nur in der Lobby gewählt werden.");
            return;
        }
        if (slot >= 0 && slot < teams.size() && slot < base) {
            MTeam t = teams.get(slot);
            if (plugin.teams().join(p, t)) {
                p.sendMessage(plugin.settings().prefix + "§7Du bist jetzt in Team " + t.getDisplayName() + "§7.");
                p.playSound(p.getLocation(), Sound.CLICK, 1f, 1.5f);
            } else {
                p.sendMessage(plugin.settings().prefix + "§cDieses Team ist voll.");
                p.playSound(p.getLocation(), Sound.VILLAGER_NO, 1f, 1f);
            }
        } else if (slot == base + 3) {
            if (plugin.teams().joinRandom(p)) {
                p.sendMessage(plugin.settings().prefix + "§7Du bist jetzt in Team " + plugin.teams().getTeam(p).getDisplayName() + "§7.");
            } else {
                p.sendMessage(plugin.settings().prefix + "§cAlle Teams sind voll.");
            }
        } else if (slot == base + 5) {
            plugin.teams().leave(p);
            p.sendMessage(plugin.settings().prefix + "§7Du hast dein Team verlassen.");
        } else {
            return;
        }
        // alle offenen Team-Menüs aktualisieren
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.getOpenInventory() != null && o.getOpenInventory().getTopInventory().getHolder() instanceof TeamGui) {
                ((TeamGui) o.getOpenInventory().getTopInventory().getHolder()).render();
            }
        }
    }
}
