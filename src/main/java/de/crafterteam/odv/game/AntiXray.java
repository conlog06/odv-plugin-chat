package de.crafterteam.odv.game;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.util.Txt;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Anti-Xray in zwei Stufen:
 * 1) Paper-Engine (Erze werden für den Client versteckt) – per Klick aktivierbar, braucht Neustart.
 * 2) Eigene Erkennung: auffällig viele Dia-/Debris-Adern pro abgebautem Stein -> Meldung an Admins.
 */
public class AntiXray implements Listener {

    private static final Set<Material> STONE = EnumSet.of(Material.STONE, Material.DEEPSLATE, Material.TUFF, Material.GRANITE,
            Material.DIORITE, Material.ANDESITE, Material.NETHERRACK, Material.BASALT, Material.BLACKSTONE, Material.CALCITE);
    private static final Set<Material> VALUABLE = EnumSet.of(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, Material.ANCIENT_DEBRIS);

    public static final class Data {
        public int stone, diamonds, debris, gold, veins, alerted;
        final List<Location> recent = new ArrayList<>();
    }

    private final OdvPlugin plugin;
    private final Map<UUID, Data> data = new HashMap<>();

    public AntiXray(OdvPlugin plugin) {
        this.plugin = plugin;
    }

    public void reset() {
        data.clear();
    }

    public Map<UUID, Data> all() {
        return data;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!plugin.game().isPlaying(p) || p.getGameMode() != GameMode.SURVIVAL) return;
        Material m = e.getBlock().getType();
        Data d = data.computeIfAbsent(p.getUniqueId(), k -> new Data());
        if (STONE.contains(m)) {
            d.stone++;
            return;
        }
        if (m == Material.GOLD_ORE || m == Material.DEEPSLATE_GOLD_ORE) d.gold++;
        if (!VALUABLE.contains(m)) return;
        if (m == Material.ANCIENT_DEBRIS) d.debris++;
        else d.diamonds++;
        Location l = e.getBlock().getLocation();
        boolean sameVein = false;
        for (Location r : d.recent) if (r.getWorld().equals(l.getWorld()) && r.distanceSquared(l) <= 16) sameVein = true;
        d.recent.add(l);
        if (d.recent.size() > 12) d.recent.remove(0);
        if (sameVein) return;
        d.veins++;
        if (!plugin.settings().xrayDetection) return;
        // Legit: grob 1 Ader auf 250+ Stein (bei erhöhter Rate entsprechend weniger)
        double expected = 250.0 / Math.max(1, plugin.settings().diamondRate);
        double perVein = d.stone / (double) d.veins;
        if (d.veins >= plugin.settings().xrayAlertVeins && perVein < expected / 4 && d.veins > d.alerted) {
            d.alerted = d.veins + 2;
            alert(p, d, perVein);
        }
    }

    private void alert(Player p, Data d, double perVein) {
        var msg = Txt.c("§c§l[Anti-Xray] §f" + p.getName() + " §7– §b" + d.veins + " Adern §7auf §f" + d.stone
                        + " Stein §8(§e" + (int) perVein + " Stein/Ader§8) §8[§eansehen§8]")
                .clickEvent(ClickEvent.runCommand("/odv xray " + p.getName()))
                .hoverEvent(HoverEvent.showText(Txt.c("§7Dias: §b" + d.diamonds + "\n§7Debris: §6" + d.debris + "\n§7Gold: §e" + d.gold)));
        for (Player a : Bukkit.getOnlinePlayers()) if (a.hasPermission("odv.admin")) a.sendMessage(msg);
        plugin.getLogger().warning("[Anti-Xray] " + p.getName() + ": " + d.veins + " Adern / " + d.stone + " Stein");
    }

    public void show(CommandSender to, String name) {
        Player t = name == null ? null : Bukkit.getPlayerExact(name);
        Txt.send(to, "§8§m      §r §c§lAnti-Xray §8§m      ");
        for (Map.Entry<UUID, Data> e : data.entrySet()) {
            if (t != null && !e.getKey().equals(t.getUniqueId())) continue;
            Data d = e.getValue();
            Txt.send(to, "§f" + plugin.stats().name(e.getKey()) + " §8» §7Stein §f" + d.stone + " §8| §bDia " + d.diamonds
                    + " §8| §6Debris " + d.debris + " §8| §eGold " + d.gold + " §8| §7Adern §f" + d.veins);
        }
        if (data.isEmpty()) Txt.send(to, "§7Noch keine Daten in dieser Runde.");
        if (t != null && to instanceof Player a && a.getGameMode() == GameMode.SPECTATOR) a.teleport(t);
    }

    // ------------------------------------------------------------ Paper-Engine

    private File paperFile() {
        File f = new File(Bukkit.getWorldContainer(), "config/paper-world-defaults.yml");
        return f.isFile() ? f : new File("config/paper-world-defaults.yml");
    }

    public String paperStatus() {
        File f = paperFile();
        if (!f.isFile()) return "§7unbekannt";
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        return y.getBoolean("anticheat.anti-xray.enabled", false)
                ? "§aaktiv §8(Modus " + y.getInt("anticheat.anti-xray.engine-mode", 1) + ")" : "§caus";
    }

    public void enablePaper(CommandSender sender) {
        File f = paperFile();
        if (!f.isFile()) {
            Txt.send(sender, plugin.settings().prefix + "§cconfig/paper-world-defaults.yml nicht gefunden.");
            return;
        }
        try {
            Files.copy(f.toPath(), new File(f.getParentFile(), f.getName() + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            String b = "anticheat.anti-xray.";
            y.set(b + "enabled", true);
            y.set(b + "engine-mode", 2);
            y.set(b + "max-block-height", 64);
            y.set(b + "update-radius", 2);
            y.set(b + "lava-obscures", false);
            y.set(b + "use-permission", false);
            y.set(b + "hidden-blocks", List.of("copper_ore", "deepslate_copper_ore", "raw_copper_block", "gold_ore", "deepslate_gold_ore",
                    "iron_ore", "deepslate_iron_ore", "raw_iron_block", "coal_ore", "deepslate_coal_ore", "lapis_ore", "deepslate_lapis_ore",
                    "mossy_cobblestone", "obsidian", "chest", "diamond_ore", "deepslate_diamond_ore", "redstone_ore",
                    "deepslate_redstone_ore", "clay", "emerald_ore", "deepslate_emerald_ore", "ender_chest", "ancient_debris",
                    "nether_gold_ore", "nether_quartz_ore"));
            y.set(b + "replacement-blocks", List.of("stone", "oak_planks", "deepslate", "netherrack"));
            y.save(f);
            Txt.send(sender, plugin.settings().prefix + "§aPaper-Anti-Xray aktiviert (Modus 2, alle Erze). §eServer neu starten!");
            Txt.send(sender, "§8Sicherung: " + f.getName() + ".bak");
        } catch (IOException e) {
            Txt.send(sender, plugin.settings().prefix + "§cFehler: " + e.getMessage());
        }
    }
}
