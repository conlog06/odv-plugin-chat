package de.crafterteam.odv.schematic;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.util.Txt;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Auswahl-Werkzeug: Linksklick = Pos1, Rechtsklick = Pos2. */
public class Wand implements Listener {

    public static final String ID = "wand";

    private final OdvPlugin plugin;
    private final Map<UUID, Location> pos1 = new HashMap<>(), pos2 = new HashMap<>();

    public Wand(OdvPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack item() {
        return plugin.items().of(Material.GOLDEN_AXE).name("§6§lSpawn-Wand §7(Links: Pos1 | Rechts: Pos2)").lore(
                "§7Markiere deine Plattform/Lobby.",
                "§7Stell dich auf den Spawnpunkt und:",
                "§e/odv schem save <Name>").glow(true).tag(ID).build();
    }

    public Location getPos1(Player p) {
        return pos1.get(p.getUniqueId());
    }

    public Location getPos2(Player p) {
        return pos2.get(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !ID.equals(plugin.items().id(e.getItem()))) return;
        if (!e.getPlayer().hasPermission("odv.admin") || e.getClickedBlock() == null) return;
        Location l = e.getClickedBlock().getLocation();
        boolean first = e.getAction() == Action.LEFT_CLICK_BLOCK;
        if (!first && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        (first ? pos1 : pos2).put(e.getPlayer().getUniqueId(), l);
        e.setCancelled(true);
        Txt.send(e.getPlayer(), plugin.settings().prefix + "§d" + (first ? "Pos1" : "Pos2") + " §7gesetzt: §f"
                + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + size(e.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pos1.remove(e.getPlayer().getUniqueId());
        pos2.remove(e.getPlayer().getUniqueId());
    }

    private String size(Player p) {
        Location a = getPos1(p), b = getPos2(p);
        if (a == null || b == null || a.getWorld() != b.getWorld()) return "";
        int x = Math.abs(a.getBlockX() - b.getBlockX()) + 1, y = Math.abs(a.getBlockY() - b.getBlockY()) + 1, z = Math.abs(a.getBlockZ() - b.getBlockZ()) + 1;
        return " §8(" + x + "×" + y + "×" + z + ")";
    }
}
