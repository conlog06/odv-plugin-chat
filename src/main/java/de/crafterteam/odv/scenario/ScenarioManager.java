package de.crafterteam.odv.scenario;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.Settings;
import de.crafterteam.odv.team.MTeam;
import de.crafterteam.odv.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Die 8 Szenarien: CutClean, BowLess, Triple Ores, Timebomb, Xtra Apples, Timber, RodLess, Backpack. */
public class ScenarioManager implements Listener {

    private static final Map<Material, Material> SMELT = new EnumMap<>(Material.class);

    static {
        SMELT.put(Material.RAW_IRON, Material.IRON_INGOT);
        SMELT.put(Material.RAW_GOLD, Material.GOLD_INGOT);
        SMELT.put(Material.RAW_COPPER, Material.COPPER_INGOT);
        SMELT.put(Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP);
        SMELT.put(Material.GRAVEL, Material.FLINT);
        SMELT.put(Material.BEEF, Material.COOKED_BEEF);
        SMELT.put(Material.PORKCHOP, Material.COOKED_PORKCHOP);
        SMELT.put(Material.CHICKEN, Material.COOKED_CHICKEN);
        SMELT.put(Material.MUTTON, Material.COOKED_MUTTON);
        SMELT.put(Material.RABBIT, Material.COOKED_RABBIT);
        SMELT.put(Material.COD, Material.COOKED_COD);
        SMELT.put(Material.SALMON, Material.COOKED_SALMON);
        SMELT.put(Material.POTATO, Material.BAKED_POTATO);
    }

    private final OdvPlugin plugin;
    private final Random random = new Random();
    private final Map<UUID, Inventory> soloBackpacks = new HashMap<>();
    private final Set<UUID> timberBusy = new HashSet<>();

    public ScenarioManager(OdvPlugin plugin) {
        this.plugin = plugin;
    }

    private Settings s() {
        return plugin.settings();
    }

    private boolean on(Scenario sc) {
        return s().has(sc) && plugin.game().getState().isIngame();
    }

    public String activeList() {
        List<String> l = new ArrayList<>();
        for (Scenario sc : Scenario.values()) if (s().has(sc)) l.add(sc.display);
        return l.isEmpty() ? "keine" : String.join("§7, §e", l);
    }

    public void onRoundStart() {
        soloBackpacks.clear();
        for (MTeam t : plugin.teams().getTeams()) t.setBackpack(null);
        if (s().has(Scenario.BACKPACK)) {
            plugin.game().broadcast("§7Szenario §eBackpack§7: §b/backpack §7öffnet den Team-Rucksack.");
        }
    }

    public void reset() {
        soloBackpacks.clear();
    }

    // ============================================================ Backpack

    public void openBackpack(Player p) {
        if (!s().has(Scenario.BACKPACK)) {
            Txt.send(p, s().prefix + "§cDas Szenario Backpack ist nicht aktiv.");
            return;
        }
        if (!plugin.game().isPlaying(p)) {
            Txt.send(p, s().prefix + "§cNur während der Runde.");
            return;
        }
        MTeam t = plugin.teams().getTeam(p);
        Inventory inv;
        if (t == null || s().isSolo()) {
            inv = soloBackpacks.computeIfAbsent(p.getUniqueId(), k -> Bukkit.createInventory(null, 27, Txt.c("§8» §6Rucksack")));
        } else {
            if (t.getBackpack() == null) t.setBackpack(Bukkit.createInventory(null, 27, Txt.c("§8» §6Rucksack " + t.getDisplayName())));
            inv = t.getBackpack();
        }
        p.openInventory(inv);
    }

    /** Wenn ein Team rausfliegt, fällt der Rucksack-Inhalt heraus. */
    public void dropBackpack(MTeam team, Location at) {
        if (at == null || at.getWorld() == null) return;
        Inventory inv = team.getBackpack();
        if (s().isSolo() && !team.getMembers().isEmpty()) inv = soloBackpacks.remove(team.getMembers().iterator().next());
        if (inv == null) return;
        for (ItemStack it : inv.getContents()) if (it != null) at.getWorld().dropItemNaturally(at, it);
        inv.clear();
    }

    // ============================================================ CutClean + Triple Ores

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent e) {
        boolean cut = on(Scenario.CUTCLEAN), triple = on(Scenario.TRIPLE_ORES);
        if (!cut && !triple) return;
        Material broken = e.getBlockState().getType();
        boolean ore = broken.name().endsWith("_ORE") || broken == Material.ANCIENT_DEBRIS;
        int xp = 0;
        for (Item item : e.getItems()) {
            ItemStack st = item.getItemStack();
            if (cut) {
                Material to = SMELT.get(st.getType());
                if (to != null && (ore || st.getType() == Material.GRAVEL)) {
                    st = st.withType(to);
                    xp += st.getAmount();
                }
            }
            if (triple && ore && st.getType() != broken) st.setAmount(Math.min(st.getMaxStackSize(), st.getAmount() * 3));
            item.setItemStack(st);
        }
        if (xp > 0) {
            int amount = xp;
            e.getBlock().getWorld().spawn(e.getBlock().getLocation().add(0.5, 0.5, 0.5), ExperienceOrb.class, o -> o.setExperience(amount));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent e) {
        if (!on(Scenario.CUTCLEAN) || e.getEntity() instanceof Player) return;
        List<ItemStack> drops = e.getDrops();
        for (int i = 0; i < drops.size(); i++) {
            Material to = SMELT.get(drops.get(i).getType());
            if (to != null) drops.set(i, drops.get(i).withType(to));
        }
    }

    // ============================================================ BowLess / RodLess

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (on(Scenario.BOWLESS) && e.getEntity() instanceof Player) {
            e.setCancelled(true);
            Txt.actionBar((Player) e.getEntity(), "§cBowLess ist aktiv!");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (on(Scenario.RODLESS)) {
            e.setCancelled(true);
            Txt.actionBar(e.getPlayer(), "§cRodLess ist aktiv!");
        }
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        ItemStack r = e.getInventory().getResult();
        if (r == null) return;
        Material m = r.getType();
        if ((s().has(Scenario.BOWLESS) && (m == Material.BOW || m == Material.CROSSBOW)) || (s().has(Scenario.RODLESS) && m == Material.FISHING_ROD)) {
            e.getInventory().setResult(null);
        }
    }

    // ============================================================ Xtra Apples

    @EventHandler(ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent e) {
        appleChance(e.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeafBreak(BlockBreakEvent e) {
        if (Tag.LEAVES.isTagged(e.getBlock().getType()) && e.getPlayer().getInventory().getItemInMainHand().getType() != Material.SHEARS) {
            appleChance(e.getBlock());
        }
        if (on(Scenario.TIMBER) && Tag.LOGS.isTagged(e.getBlock().getType()) && e.getPlayer().getGameMode() == GameMode.SURVIVAL) timber(e);
    }

    private void appleChance(Block b) {
        if (!on(Scenario.XTRA_APPLES) || !Tag.LEAVES.isTagged(b.getType())) return;
        if (random.nextDouble() * 100 < s().xtraAppleChance) {
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), new ItemStack(Material.APPLE));
        }
    }

    // ============================================================ Timber

    private void timber(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!timberBusy.add(p.getUniqueId())) return;
        try {
            Block start = e.getBlock();
            Deque<Block> queue = new ArrayDeque<>();
            Set<Block> seen = new HashSet<>();
            queue.add(start);
            seen.add(start);
            int broken = 0;
            ItemStack tool = p.getInventory().getItemInMainHand();
            while (!queue.isEmpty() && broken < 256) {
                Block b = queue.poll();
                if (b != start) {
                    b.breakNaturally(tool, true);
                    broken++;
                }
                for (int dx = -1; dx <= 1; dx++)
                    for (int dy = 0; dy <= 1; dy++)
                        for (int dz = -1; dz <= 1; dz++) {
                            Block n = b.getRelative(dx, dy, dz);
                            if (seen.add(n) && Tag.LOGS.isTagged(n.getType()) && !plugin.arena().isProtected(n.getLocation())) queue.add(n);
                        }
            }
        } finally {
            timberBusy.remove(p.getUniqueId());
        }
    }

    // ============================================================ Timebomb

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent e) {
        if (!on(Scenario.TIMEBOMB) || e.getDrops().isEmpty() || e.getKeepInventory()) return;
        Player p = e.getEntity();
        Location loc = p.getLocation().getBlock().getLocation();
        if (plugin.arena().isProtected(loc)) return;
        Block a = loc.getBlock(), b = a.getRelative(BlockFace.EAST);
        a.setType(Material.CHEST);
        b.setType(Material.CHEST);
        // zu einer Doppeltruhe verbinden
        if (a.getBlockData() instanceof org.bukkit.block.data.type.Chest ca && b.getBlockData() instanceof org.bukkit.block.data.type.Chest cb) {
            ca.setFacing(BlockFace.NORTH);
            cb.setFacing(BlockFace.NORTH);
            ca.setType(org.bukkit.block.data.type.Chest.Type.LEFT);
            cb.setType(org.bukkit.block.data.type.Chest.Type.RIGHT);
            a.setBlockData(ca, false);
            b.setBlockData(cb, false);
        }
        List<ItemStack> rest = new ArrayList<>();
        if (a.getState() instanceof Chest chest) {
            rest.addAll(chest.getInventory().addItem(e.getDrops().toArray(new ItemStack[0])).values());
        } else rest.addAll(e.getDrops());
        e.getDrops().clear();
        e.getDrops().addAll(rest);
        String group = "timebomb-" + p.getUniqueId();
        TextDisplay label = plugin.holograms().spawn(group, loc.clone().add(1.0, 1.6, 0.5), "§c§l" + s().timebombSeconds + "s\n§7" + p.getName(), 1f, null);
        int[] left = {s().timebombSeconds};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            left[0]--;
            if (label != null && label.isValid()) label.text(Txt.c("§c§l" + left[0] + "s\n§7" + p.getName()));
            if (left[0] <= 0 || !plugin.game().getState().isIngame()) {
                task.cancel();
                plugin.holograms().remove(group);
                if (left[0] <= 0) {
                    if (a.getState() instanceof Chest c) c.getInventory().clear();
                    loc.getWorld().createExplosion(loc.clone().add(1, 0.5, 0.5), 4.5f, false, true);
                    loc.getWorld().strikeLightningEffect(loc);
                    plugin.game().broadcast("§c" + p.getName() + "s §7Leiche ist explodiert.");
                }
            }
        }, 20L, 20L);
    }
}
