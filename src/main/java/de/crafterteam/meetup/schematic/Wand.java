package de.crafterteam.meetup.schematic;

import de.crafterteam.meetup.MeetupPlugin;
import de.crafterteam.meetup.util.ItemBuilder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Auswahl-Werkzeug: Linksklick = Pos1, Rechtsklick = Pos2.
 */
public class Wand implements Listener {

    public static final String NAME = "§6§lSpawn-Wand §7(Links: Pos1 | Rechts: Pos2)";

    private final MeetupPlugin plugin;
    private final Map<UUID, Location> pos1 = new HashMap<UUID, Location>();
    private final Map<UUID, Location> pos2 = new HashMap<UUID, Location>();

    public Wand(MeetupPlugin plugin) {
        this.plugin = plugin;
    }

    public static ItemStack item() {
        return new ItemBuilder(Material.GOLD_AXE).name(NAME).lore(
                "§7Markiere deine Spawn-Plattform.",
                "§7Stell dich dann dorthin, wo der",
                "§7Spieler spawnen soll, und nutze:",
                "§e/meetup schem save <Name>").glow(true).build();
    }

    public Location getPos1(Player p) {
        return pos1.get(p.getUniqueId());
    }

    public Location getPos2(Player p) {
        return pos2.get(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        ItemStack it = e.getItem();
        if (it == null || it.getType() != Material.GOLD_AXE || !it.hasItemMeta()
                || !NAME.equals(it.getItemMeta().getDisplayName())) return;
        if (!e.getPlayer().hasPermission("meetup.admin") || e.getClickedBlock() == null) return;
        Location l = e.getClickedBlock().getLocation();
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            pos1.put(e.getPlayer().getUniqueId(), l);
            e.getPlayer().sendMessage(plugin.settings().prefix + "§dPos1 §7gesetzt: §f" + fmt(l) + size(e.getPlayer()));
            e.setCancelled(true);
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            pos2.put(e.getPlayer().getUniqueId(), l);
            e.getPlayer().sendMessage(plugin.settings().prefix + "§dPos2 §7gesetzt: §f" + fmt(l) + size(e.getPlayer()));
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pos1.remove(e.getPlayer().getUniqueId());
        pos2.remove(e.getPlayer().getUniqueId());
    }

    private String size(Player p) {
        Location a = getPos1(p), b = getPos2(p);
        if (a == null || b == null || a.getWorld() != b.getWorld()) return "";
        int x = Math.abs(a.getBlockX() - b.getBlockX()) + 1;
        int y = Math.abs(a.getBlockY() - b.getBlockY()) + 1;
        int z = Math.abs(a.getBlockZ() - b.getBlockZ()) + 1;
        return " §8(" + x + "x" + y + "x" + z + " = " + (x * y * z) + " Blöcke)";
    }

    private static String fmt(Location l) {
        return l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ();
    }
}
