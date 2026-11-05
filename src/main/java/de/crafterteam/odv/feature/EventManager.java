package de.crafterteam.odv.feature;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.schematic.PasteQueue;
import de.crafterteam.odv.team.MTeam;
import de.crafterteam.odv.util.Txt;
import de.crafterteam.odv.world.SpawnManager;
import de.crafterteam.odv.world.Structures;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Das 26.3-Konzept:
 * <ul>
 *     <li>Netherite-Meteor: angekündigter Einschlag mit Krater voller Ancient Debris + Meteorkern</li>
 *     <li>End-Finale: schwebende End-Insel als letzte Arena, wer runterfällt, ist raus</li>
 *     <li>Zeitlupen-Finale: beim entscheidenden Kill läuft der Server 3 Sekunden mit 4 TPS</li>
 *     <li>Siegerpodest: Sieger als Mannequins (lebensechte Figuren) in der Lobby</li>
 * </ul>
 */
public class EventManager {

    private final OdvPlugin plugin;
    private final Random random = new Random();

    private Location meteorTarget;
    private boolean meteorDone;
    private Location endIsland;
    private boolean endBuilt, endActive;
    private final List<UUID> podium = new ArrayList<>();

    public EventManager(OdvPlugin plugin) {
        this.plugin = plugin;
    }

    public void reset() {
        meteorTarget = null;
        meteorDone = false;
        endActive = false;
        plugin.holograms().remove("meteor");
        restoreTickRate();
    }

    public boolean isEndActive() {
        return endActive;
    }

    /** Jede Sekunde während der Runde. */
    public void tick(int elapsed, boolean pvp) {
        if (plugin.settings().has(Feature.METEOR) && !meteorDone) {
            int t = plugin.settings().meteorTime;
            if (meteorTarget == null && elapsed >= t - 60) announceMeteor();
            if (meteorTarget != null) {
                int left = t - elapsed;
                World w = meteorTarget.getWorld();
                for (int y = 0; y < 30; y += 3) {
                    w.spawnParticle(Particle.DUST, meteorTarget.clone().add(0, y, 0), 3, 0.2, 0.6, 0.2, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 80, 20), 2f));
                }
                if (left == 30 || left == 10 || (left <= 3 && left > 0)) {
                    plugin.game().broadcast("§6☄ Meteor-Einschlag in §e" + left + "s §7bei §e" + meteorTarget.getBlockX() + " / " + meteorTarget.getBlockZ());
                }
                if (left <= 0) impact();
            }
        }
        if (endActive && endIsland != null) {
            World w = endIsland.getWorld();
            for (UUID u : plugin.game().getAlive()) {
                Player p = Bukkit.getPlayer(u);
                if (p != null && p.getWorld().equals(w) && p.getY() < endIsland.getY() - 18) {
                    Txt.title(p, "§5§lIns Nichts gestürzt", "");
                    p.setHealth(0);
                }
            }
            w.spawnParticle(Particle.REVERSE_PORTAL, endIsland.clone().add(0, 4, 0), 40, 14, 3, 14, 0.02);
        }
        // End-Insel 90 s vor dem Finale vorbereiten
        if (plugin.settings().has(Feature.END_FINALE) && !endBuilt && elapsed >= plugin.settings().timeLimit * 60 - 90) buildEndIsland();
    }

    // ===================================================================== Meteor

    private void announceMeteor() {
        World w = plugin.arena().getWorld();
        if (w == null) {
            meteorDone = true;
            return;
        }
        double half = Math.max(30, w.getWorldBorder().getSize() / 2 - 40);
        Location c = w.getWorldBorder().getCenter();
        for (int i = 0; i < 30 && meteorTarget == null; i++) {
            int x = c.getBlockX() + (int) ((random.nextDouble() * 2 - 1) * half), z = c.getBlockZ() + (int) ((random.nextDouble() * 2 - 1) * half);
            Location l = new Location(w, x + 0.5, SpawnManager.ground(w, x, z), z + 0.5);
            if (!plugin.arena().isProtected(l) && l.distance(plugin.arena().getCenter()) > 40) meteorTarget = l;
        }
        if (meteorTarget == null) {
            meteorDone = true;
            return;
        }
        plugin.holograms().spawn("meteor", meteorTarget.clone().add(0, 4, 0), "§6§l☄ METEOR ☄\n§7Einschlag in Kürze\n§8Ancient Debris & Netherite", 1.6f, null)
                .setViewRange(20f);
        plugin.game().broadcast("§6§l☄ Ein Netherite-Meteor rast auf die Map zu! §7Einschlag in §e60s §7bei §eX "
                + meteorTarget.getBlockX() + " §7/ §eZ " + meteorTarget.getBlockZ());
        plugin.game().sound(Sound.ENTITY_ENDER_DRAGON_AMBIENT, 0.5f);
    }

    private void impact() {
        meteorDone = true;
        plugin.holograms().remove("meteor");
        Location ground = meteorTarget.clone();
        World w = ground.getWorld();
        Location start = ground.clone().add(-25, 70, -25);
        BlockDisplay rock = w.spawn(start, BlockDisplay.class, d -> {
            d.setBlock(Material.MAGMA_BLOCK.createBlockData());
            d.setPersistent(false);
            d.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            d.setTransformation(new Transformation(new Vector3f(-1.5f, -1.5f, -1.5f), new Quaternionf(), new Vector3f(3, 3, 3), new Quaternionf()));
            d.setTeleportDuration(40);
        });
        Bukkit.getScheduler().runTaskLater(plugin, () -> rock.teleport(ground.clone().add(0, 1.5, 0)), 2L);
        int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            t[0] += 2;
            double f = Math.min(1, t[0] / 42.0);
            Location pos = start.clone().add(new Vector(25 * f, -68.5 * f, 25 * f));
            w.spawnParticle(Particle.FLAME, pos, 25, 0.8, 0.8, 0.8, 0.05);
            w.spawnParticle(Particle.LARGE_SMOKE, pos, 10, 0.6, 0.6, 0.6, 0.02);
            if (t[0] % 10 == 0) w.playSound(pos, Sound.ITEM_FIRECHARGE_USE, 3f, 0.5f);
            if (t[0] >= 42) {
                task.cancel();
                rock.remove();
                crater(ground);
            }
        }, 2L, 2L);
    }

    private void crater(Location g) {
        World w = g.getWorld();
        w.spawnParticle(Particle.EXPLOSION_EMITTER, g, 6, 3, 1, 3);
        w.playSound(g, Sound.ENTITY_GENERIC_EXPLODE, 8f, 0.6f);
        w.playSound(g, Sound.ITEM_TRIDENT_THUNDER, 8f, 0.8f);
        int r = 6, cx = g.getBlockX(), cy = g.getBlockY(), cz = g.getBlockZ();
        Material[] shell = {Material.BLACKSTONE, Material.BLACKSTONE, Material.MAGMA_BLOCK, Material.OBSIDIAN, Material.CRYING_OBSIDIAN, Material.BASALT};
        List<Block> shellBlocks = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++)
            for (int dy = -r; dy <= r; dy++)
                for (int dz = -r; dz <= r; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy * 1.6 + dz * dz);
                    if (d > r) continue;
                    Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                    if (b.getType() == Material.BEDROCK || plugin.arena().isProtected(b.getLocation())) continue;
                    if (d > r - 1.3 && dy <= 0) {
                        b.setType(shell[random.nextInt(shell.length)], false);
                        shellBlocks.add(b);
                    } else if (dy > -r + 1) b.setType(Material.AIR, false);
                }
        for (int i = 0; i < 6 && !shellBlocks.isEmpty(); i++) shellBlocks.remove(random.nextInt(shellBlocks.size())).setType(Material.ANCIENT_DEBRIS, false);
        // Meteorkern
        Block core = w.getBlockAt(cx, cy - r + 2, cz);
        core.getRelative(0, -1, 0).setType(Material.CRYING_OBSIDIAN, false);
        core.setType(Material.CHEST, false);
        if (core.getState() instanceof Chest chest) {
            var inv = chest.getBlockInventory();
            inv.setItem(11, new ItemStack(Material.NETHERITE_SCRAP, 2 + random.nextInt(3)));
            inv.setItem(13, new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
            inv.setItem(15, new ItemStack(Material.GOLDEN_APPLE, 2));
            if (random.nextInt(3) == 0) inv.setItem(4, new ItemStack(Material.NETHERITE_INGOT));
            if (random.nextInt(6) == 0) inv.setItem(22, new ItemStack(Material.NETHERITE_SPEAR));
        }
        for (Player p : w.getPlayers()) {
            double d = p.getLocation().distance(g);
            if (d < 12 && plugin.game().isPlaying(p)) {
                p.setVelocity(p.getLocation().toVector().subtract(g.toVector()).normalize().multiply(1.6).setY(0.8));
                p.damage(Math.max(2, 10 - d));
                p.setFireTicks(60);
            }
        }
        plugin.game().broadcast("§6§l☄ Einschlag! §7Im Krater liegen §6Ancient Debris §7und der §cMeteorkern§7.");
    }

    // ===================================================================== End-Finale

    private Location islandSpot() {
        Location c = plugin.arena().getCenter(), lobby = plugin.arena().getLobby();
        World w = c.getWorld();
        int y = Math.min(w.getMaxHeight() - 30, (lobby != null ? lobby.getBlockY() : c.getBlockY() + 60) + 40);
        return new Location(w, c.getBlockX() + 0.5, y + 1, c.getBlockZ() - 70 + 0.5);
    }

    private void buildEndIsland() {
        endBuilt = true;
        if (plugin.arena().getCenter() == null) return;
        endIsland = islandSpot();
        plugin.game().broadcast("§5§lDas Ende naht... §7In 90 Sekunden beginnt das §5End-Finale§7.");
        PasteQueue.run(plugin, Structures.endIsland(endIsland), null, plugin.settings().blocksPerTick, null, null);
    }

    /** Statt Showdown: alle Lebenden auf die End-Insel. */
    public boolean startEndFinale() {
        if (!plugin.settings().has(Feature.END_FINALE) || plugin.arena().getCenter() == null) return false;
        if (!endBuilt || endIsland == null) {
            endIsland = islandSpot();
            for (PasteQueue.Change ch : Structures.endIsland(endIsland)) ch.world().getBlockAt(ch.x(), ch.y(), ch.z()).setBlockData(ch.data(), false);
            endBuilt = true;
        }
        endActive = true;
        World w = endIsland.getWorld();
        w.getWorldBorder().setCenter(endIsland);
        w.getWorldBorder().setSize(70);
        w.setTime(18000);
        List<UUID> ids = new ArrayList<>(plugin.game().getAlive());
        for (int i = 0; i < ids.size(); i++) {
            Player p = Bukkit.getPlayer(ids.get(i));
            if (p == null) continue;
            double a = 2 * Math.PI * i / Math.max(1, ids.size());
            Location l = endIsland.clone().add(Math.cos(a) * 14, 0, Math.sin(a) * 14);
            l.setYaw(SpawnManager.yawTo(l, endIsland));
            p.teleport(l);
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 5, 4));
            Txt.title(p, "§5§lEND-FINALE", "§7Wer fällt, verliert.");
        }
        plugin.game().broadcast("§5§l✦ END-FINALE ✦ §7Die letzten Spieler kämpfen auf der End-Insel – §dwer runterfällt, ist raus!");
        plugin.game().sound(Sound.BLOCK_END_PORTAL_SPAWN, 0.8f);
        return true;
    }

    // ===================================================================== Zeitlupe

    private float oldTickRate = 20f;
    private boolean slowmo;

    /** Startet die Zeitlupe und ruft danach then auf. @return false, wenn aus. */
    public boolean slowmo(Runnable then) {
        if (!plugin.settings().has(Feature.SLOWMO) || slowmo) return false;
        slowmo = true;
        var tm = Bukkit.getServerTickManager();
        oldTickRate = tm.getTickRate();
        tm.setTickRate(4f);
        for (Player p : Bukkit.getOnlinePlayers()) {
            Txt.title(p, "§6§lF I N A L E", "§7...");
            p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.5f);
        }
        // 12 Ticks bei 4 TPS = 3 Sekunden echte Zeit
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            restoreTickRate();
            then.run();
        }, 12L);
        return true;
    }

    public void restoreTickRate() {
        if (!slowmo) return;
        slowmo = false;
        Bukkit.getServerTickManager().setTickRate(oldTickRate <= 0 ? 20f : oldTickRate);
    }

    // ===================================================================== Siegerpodest

    /** Gespeichertes Podest, damit es nach einem Welt-Reset neu aufgebaut werden kann. */
    private record PodiumEntry(UUID id, com.destroystokyo.paper.profile.PlayerProfile profile, org.bukkit.inventory.ItemStack[] gear) {}
    private final List<PodiumEntry> lastPodium = new ArrayList<>();
    private String lastPodiumInfo;

    public void podium(MTeam winner, List<Map.Entry<UUID, Integer>> topKillers) {
        if (!plugin.settings().has(Feature.PODIUM)) return;
        List<UUID> people = new ArrayList<>();
        if (winner != null) people.addAll(winner.getMembers());
        for (Map.Entry<UUID, Integer> e : topKillers) if (!people.contains(e.getKey())) people.add(e.getKey());
        lastPodium.clear();
        for (int i = 0; i < Math.min(3, people.size()); i++) {
            UUID id = people.get(i);
            Player online = Bukkit.getPlayer(id);
            if (online != null) {
                var inv = online.getInventory();
                org.bukkit.inventory.ItemStack[] gear = {clone(inv.getHelmet()), clone(inv.getChestplate()), clone(inv.getLeggings()), clone(inv.getBoots()), clone(inv.getItemInMainHand())};
                lastPodium.add(new PodiumEntry(id, online.getPlayerProfile(), gear));
            } else {
                lastPodium.add(new PodiumEntry(id, null, null));
            }
        }
        lastPodiumInfo = plugin.settings().mode.display + " · " + new java.text.SimpleDateFormat("dd.MM.yy").format(new java.util.Date());
        rebuildPodium();
    }

    private static org.bukkit.inventory.ItemStack clone(org.bukkit.inventory.ItemStack it) {
        return it == null ? null : it.clone();
    }

    /** Podest (neu) in der aktuellen Lobby aufbauen. */
    public void rebuildPodium() {
        if (lastPodiumInfo == null || !plugin.settings().has(Feature.PODIUM)) return;
        Location lobby = plugin.arena().getLobby();
        if (lobby == null || lobby.getWorld() == null) return;
        clearPodium();
        World w = lobby.getWorld();
        int bx = lobby.getBlockX() - 7, by = lobby.getBlockY(), bz = lobby.getBlockZ();
        int[][] spots = {{0, 2}, {-2, 1}, {2, 1}}; // z-Versatz, Höhe
        Material[] blocks = {Material.GOLD_BLOCK, Material.IRON_BLOCK, Material.WAXED_COPPER_BLOCK};
        String[] places = {"§6§l1. Platz", "§f§l2. Platz", "§c§l3. Platz"};
        for (int i = 0; i < 3; i++) {
            for (int h = 0; h < spots[i][1]; h++) w.getBlockAt(bx, by + h, bz + spots[i][0]).setType(blocks[i], false);
            if (i >= lastPodium.size()) continue;
            PodiumEntry pe = lastPodium.get(i);
            UUID id = pe.id();
            Location at = new Location(w, bx + 0.5, by + spots[i][1], bz + spots[i][0] + 0.5, -90f, 0f);
            int idx = i;
            Mannequin m = w.spawn(at, Mannequin.class, man -> {
                man.setPersistent(false);
                man.setImmovable(true);
                man.setInvulnerable(true);
                man.setGravity(false);
                man.customName(Txt.c(places[idx] + " §8┃ " + plugin.ranks().of(id).nameColor() + plugin.stats().name(id)));
                man.setCustomNameVisible(true);
                man.setDescription(Txt.c("§7" + lastPodiumInfo));
                if (pe.profile() != null) {
                    man.setProfile(ResolvableProfile.resolvableProfile(pe.profile()));
                } else {
                    man.setProfile(ResolvableProfile.resolvableProfile().name(plugin.stats().name(id)).build());
                }
                if (pe.gear() != null) {
                    var eq = man.getEquipment();
                    eq.setItem(EquipmentSlot.HEAD, pe.gear()[0]);
                    eq.setItem(EquipmentSlot.CHEST, pe.gear()[1]);
                    eq.setItem(EquipmentSlot.LEGS, pe.gear()[2]);
                    eq.setItem(EquipmentSlot.FEET, pe.gear()[3]);
                    eq.setItem(EquipmentSlot.HAND, pe.gear()[4]);
                }
            });
            podium.add(m.getUniqueId());
        }
        plugin.holograms().remove("podium");
        plugin.holograms().spawn("podium", new Location(w, bx + 0.5, by + 5.2, bz + 0.5), "§6§l✦ Hall of Fame ✦\n§7Letzte Runde · " + lastPodiumInfo, 1.3f, null);
    }

    public void clearPodium() {
        for (UUID id : podium) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        podium.clear();
    }
}
