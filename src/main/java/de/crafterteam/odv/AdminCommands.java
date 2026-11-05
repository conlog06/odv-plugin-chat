package de.crafterteam.odv;

import de.crafterteam.odv.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Admin-Befehle: /invsee /ecsee /armorsee /gamemode (/gm) /give /i /tp /tphere /heal /feed /fly /clear /vanish /speed.
 * Rechte: odv.cmd.&lt;befehl&gt; (odv.cmd.* = alle).
 */
public class AdminCommands implements TabExecutor, Listener {

    public static final String[] COMMANDS = {"invsee", "ecsee", "armorsee", "gamemode", "give", "i", "tp", "tphere", "heal", "feed", "fly", "clear", "vanish", "speed"};

    private final OdvPlugin plugin;
    private final Set<UUID> vanished = new HashSet<>();

    public AdminCommands(OdvPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isVanished(Player p) {
        return vanished.contains(p.getUniqueId());
    }

    private void tell(CommandSender to, String msg) {
        Txt.send(to, plugin.settings().prefix + msg);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String c = cmd.getName().toLowerCase(Locale.ROOT);
        if (!sender.hasPermission("odv.cmd." + c)) {
            tell(sender, "§cKeine Berechtigung §8(odv.cmd." + c + ")");
            return true;
        }
        Player self = sender instanceof Player p ? p : null;
        switch (c) {
            case "invsee", "ecsee", "armorsee" -> {
                if (self == null || args.length < 1) {
                    tell(sender, "§e/" + c + " <Spieler>");
                    return true;
                }
                Player t = target(sender, args[0]);
                if (t == null) return true;
                switch (c) {
                    case "invsee" -> self.openInventory(t.getInventory());
                    case "ecsee" -> self.openInventory(t.getEnderChest());
                    default -> self.openInventory(new ArmorView(t).inv);
                }
                tell(sender, "§7Du siehst " + (c.equals("ecsee") ? "die Endertruhe" : c.equals("armorsee") ? "die Rüstung" : "das Inventar")
                        + " von §f" + t.getName() + "§7. Änderungen werden übernommen.");
            }
            case "gamemode" -> {
                if (args.length < 1) {
                    tell(sender, "§e/gm <0|1|2|3> [Spieler]");
                    return true;
                }
                GameMode gm = switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "0", "s", "survival", "überleben" -> GameMode.SURVIVAL;
                    case "1", "c", "creative", "kreativ" -> GameMode.CREATIVE;
                    case "2", "a", "adventure", "abenteuer" -> GameMode.ADVENTURE;
                    case "3", "sp", "spectator", "zuschauer" -> GameMode.SPECTATOR;
                    default -> null;
                };
                if (gm == null) {
                    tell(sender, "§cUnbekannter Spielmodus.");
                    return true;
                }
                Player t = args.length >= 2 ? target(sender, args[1]) : self;
                if (t == null) return true;
                if (plugin.game().isPlaying(t) && !sender.hasPermission("odv.admin")) {
                    tell(sender, "§cSpieler in einer laufenden Runde kann nur die Leitung umstellen.");
                    return true;
                }
                t.setGameMode(gm);
                tell(sender, "§f" + t.getName() + " §7ist jetzt im Modus §e" + gm.name().toLowerCase(Locale.ROOT) + "§7.");
            }
            case "give", "i" -> {
                int base = c.equals("give") ? 1 : 0;
                if (args.length < base + 1) {
                    tell(sender, c.equals("give") ? "§e/give <Spieler> <Item> [Anzahl]" : "§e/i <Item> [Anzahl]");
                    return true;
                }
                Player t = c.equals("give") ? target(sender, args[0]) : self;
                if (t == null) return true;
                Material m = Material.matchMaterial(args[base]);
                if (m == null || !m.isItem() || m.isAir()) {
                    tell(sender, "§cUnbekanntes Item: §f" + args[base]);
                    return true;
                }
                int amount = args.length > base + 1 ? Math.max(1, Math.min(2304, parse(args[base + 1], 1))) : m.getMaxStackSize();
                giveItems(t, m, amount);
                tell(sender, "§f" + t.getName() + " §7hat §e" + amount + "x " + m.getKey().getKey() + " §7erhalten.");
            }
            case "tp" -> {
                if (args.length == 1 && self != null) {
                    Player t = target(sender, args[0]);
                    if (t != null) self.teleport(t);
                } else if (args.length >= 2) {
                    Player a = target(sender, args[0]), b = target(sender, args[1]);
                    if (a != null && b != null) a.teleport(b);
                } else tell(sender, "§e/tp <Spieler> §8| §e/tp <Spieler> <Ziel>");
            }
            case "tphere" -> {
                if (self == null || args.length < 1) {
                    tell(sender, "§e/tphere <Spieler>");
                    return true;
                }
                Player t = target(sender, args[0]);
                if (t != null) t.teleport(self);
            }
            case "heal", "feed" -> {
                Player t = args.length >= 1 ? target(sender, args[0]) : self;
                if (t == null) return true;
                if (c.equals("heal")) {
                    var max = t.getAttribute(Attribute.MAX_HEALTH);
                    t.setHealth(max == null ? 20 : max.getValue());
                    t.setFireTicks(0);
                    for (PotionEffect e : t.getActivePotionEffects()) t.removePotionEffect(e.getType());
                }
                t.setFoodLevel(20);
                t.setSaturation(10f);
                tell(sender, "§f" + t.getName() + " §7wurde " + (c.equals("heal") ? "geheilt" : "gesättigt") + ".");
            }
            case "fly" -> {
                Player t = args.length >= 1 ? target(sender, args[0]) : self;
                if (t == null) return true;
                if (plugin.game().isPlaying(t)) {
                    tell(sender, "§cNicht während einer laufenden Runde.");
                    return true;
                }
                t.setAllowFlight(!t.getAllowFlight());
                if (!t.getAllowFlight()) t.setFlying(false);
                tell(sender, "§7Fliegen für §f" + t.getName() + "§7: " + (t.getAllowFlight() ? "§aan" : "§caus"));
            }
            case "clear" -> {
                Player t = args.length >= 1 ? target(sender, args[0]) : self;
                if (t == null) return true;
                t.getInventory().clear();
                tell(sender, "§7Inventar von §f" + t.getName() + " §7geleert.");
            }
            case "vanish" -> {
                if (self == null) return true;
                toggleVanish(self);
            }
            case "speed" -> {
                if (self == null || args.length < 1) {
                    tell(sender, "§e/speed <1-10>");
                    return true;
                }
                float v = Math.max(1, Math.min(10, parse(args[0], 1))) / 10f;
                if (self.isFlying()) self.setFlySpeed(v);
                else self.setWalkSpeed(Math.min(1f, v + 0.1f));
                tell(sender, "§7" + (self.isFlying() ? "Flug" : "Lauf") + "-Geschwindigkeit: §e" + args[0]);
            }
            default -> {
            }
        }
        return true;
    }

    private void giveItems(Player t, Material m, int amount) {
        int left = amount;
        while (left > 0) {
            int n = Math.min(left, m.getMaxStackSize());
            for (ItemStack rest : t.getInventory().addItem(new ItemStack(m, n)).values()) t.getWorld().dropItemNaturally(t.getLocation(), rest);
            left -= n;
        }
    }

    // ------------------------------------------------------------ Vanish

    public void toggleVanish(Player p) {
        if (vanished.remove(p.getUniqueId())) {
            for (Player o : Bukkit.getOnlinePlayers()) o.showPlayer(plugin, p);
            tell(p, "§7Vanish: §caus §8– du bist wieder sichtbar.");
        } else {
            if (plugin.game().isPlaying(p)) {
                tell(p, "§cNicht während du mitspielst.");
                return;
            }
            vanished.add(p.getUniqueId());
            for (Player o : Bukkit.getOnlinePlayers()) if (!o.equals(p) && !o.hasPermission("odv.staff")) o.hidePlayer(plugin, p);
            plugin.teams().leave(p.getUniqueId());
            tell(p, "§7Vanish: §aan §8– nur das Team sieht dich, du spielst nicht mit.");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player j = e.getPlayer();
        if (j.hasPermission("odv.staff")) return;
        for (UUID id : vanished) {
            Player v = Bukkit.getPlayer(id);
            if (v != null) j.hidePlayer(plugin, v);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        vanished.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------ Armorsee

    private static final class ArmorView implements InventoryHolder {
        final UUID target;
        final Inventory inv;

        ArmorView(Player t) {
            target = t.getUniqueId();
            inv = Bukkit.createInventory(this, 9, Txt.c("§8» §7Rüstung von §f" + t.getName()));
            ItemStack[] a = t.getInventory().getArmorContents(); // Stiefel, Hose, Brust, Helm
            inv.setItem(0, a[3]);
            inv.setItem(1, a[2]);
            inv.setItem(2, a[1]);
            inv.setItem(3, a[0]);
            inv.setItem(4, t.getInventory().getItemInOffHand());
            ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            var meta = glass.getItemMeta();
            meta.displayName(Txt.item("§8Helm · Brust · Hose · Schuhe · Offhand"));
            glass.setItemMeta(meta);
            for (int i = 5; i < 9; i++) inv.setItem(i, glass);
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    @EventHandler
    public void onArmorClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder(false) instanceof ArmorView && e.getRawSlot() >= 5 && e.getRawSlot() < 9) e.setCancelled(true);
    }

    @EventHandler
    public void onArmorClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof ArmorView v)) return;
        Player t = Bukkit.getPlayer(v.target);
        if (t == null) return;
        Inventory i = v.inv;
        t.getInventory().setHelmet(i.getItem(0));
        t.getInventory().setChestplate(i.getItem(1));
        t.getInventory().setLeggings(i.getItem(2));
        t.getInventory().setBoots(i.getItem(3));
        t.getInventory().setItemInOffHand(i.getItem(4));
    }

    // ------------------------------------------------------------ Hilfen

    private Player target(CommandSender s, String name) {
        Player t = Bukkit.getPlayer(name);
        if (t == null) tell(s, "§c" + name + " ist nicht online.");
        return t;
    }

    private static int parse(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        String c = cmd.getName().toLowerCase(Locale.ROOT);
        List<String> o = new ArrayList<>();
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        boolean itemArg = (c.equals("i") && args.length == 1) || (c.equals("give") && args.length == 2);
        if (itemArg) {
            for (Material m : Material.values()) {
                if (!m.isItem() || m.isAir()) continue;
                String k = m.getKey().getKey();
                if (k.startsWith(last)) o.add(k);
                if (o.size() >= 60) break;
            }
            return o;
        }
        if (c.equals("gamemode") && args.length == 1) o.addAll(List.of("0", "1", "2", "3", "survival", "creative", "adventure", "spectator"));
        else if (!c.equals("speed") && !c.equals("vanish") && !c.equals("i")) Bukkit.getOnlinePlayers().forEach(p -> o.add(p.getName()));
        o.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return o;
    }
}
