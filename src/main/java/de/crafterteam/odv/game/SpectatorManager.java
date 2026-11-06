package de.crafterteam.odv.game;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.Settings;
import de.crafterteam.odv.feature.Feature;
import de.crafterteam.odv.gui.ActionGui;
import de.crafterteam.odv.gui.Menus;
import de.crafterteam.odv.team.MTeam;
import de.crafterteam.odv.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Zuschauer-System: Tote/Mods schweben unsichtbar (Abenteuer + Fliegen) und haben Items:
 * Kompass (Spieler-Teleporter), letzter Kampf, Wetten, Optionen, Lobby. Optional Noclip (gm3) mit /spec zurück.
 * Lebende Spieler sehen Zuschauer nicht, Zuschauer stören nicht (kein Schaden, keine Items, keine Kollision).
 */
public class SpectatorManager implements Listener {

    public static final String I_COMPASS = "spec_compass", I_FIGHT = "spec_fight", I_BET = "spec_bet",
            I_OPTIONS = "spec_options", I_LOBBY = "spec_lobby";

    private final OdvPlugin plugin;
    private final Set<UUID> specs = new HashSet<>();
    private final Set<UUID> noclip = new HashSet<>();
    private final Set<UUID> noNightVision = new HashSet<>(), showSpecs = new HashSet<>();
    private final Map<UUID, Float> speed = new HashMap<>();
    private Location lastFight;
    private long lastFightTime;

    public SpectatorManager(OdvPlugin plugin) {
        this.plugin = plugin;
    }

    private Settings s() {
        return plugin.settings();
    }

    public boolean isSpec(Player p) {
        return specs.contains(p.getUniqueId());
    }

    public Set<UUID> all() {
        return specs;
    }

    // ================================================================ rein / raus

    public void enter(Player p, Location at) {
        UUID id = p.getUniqueId();
        boolean fresh = specs.add(id);
        noclip.remove(id);
        p.closeInventory();
        p.setGameMode(GameMode.ADVENTURE);
        p.getInventory().clear();
        p.setHealth(Math.max(1, p.getHealth()));
        p.setFoodLevel(20);
        p.setFireTicks(0);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        p.setAllowFlight(true);
        p.setFlying(true);
        p.setFlySpeed(speed.getOrDefault(id, 0.1f));
        p.setInvulnerable(true);
        p.setCollidable(false);
        p.setCanPickupItems(false);
        p.setSilent(true);
        p.setSleepingIgnored(true);
        if (!noNightVision.contains(id)) p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false));
        if (at != null) p.teleport(at);
        // Performance: Zuschauer laden weniger Chunks (fliegen schnell herum)
        try {
            int vd = Math.min(p.getWorld().getViewDistance(), 6);
            p.setViewDistance(vd);
            p.setSendViewDistance(vd);
        } catch (Throwable ignored) {
        }
        updateVisibility(p);
        giveItems(p);
        if (fresh && !plugin.game().getState().isLobby()) {
            Txt.send(p, s().prefix + "§7Du bist §bZuschauer§7. §8» §bKompass§7: Spieler beobachten §8┃ §eRedstone§7: Optionen");
        }
    }

    /** Zuschauer-Zustand aufheben (Revive, Latejoin, Lobby). */
    public void leave(Player p) {
        UUID id = p.getUniqueId();
        boolean was = specs.remove(id);
        noclip.remove(id);
        if (!was) return;
        p.setInvulnerable(false);
        p.setCollidable(true);
        p.setCanPickupItems(true);
        p.setSilent(false);
        p.setSleepingIgnored(false);
        p.setFlySpeed(0.1f);
        p.removePotionEffect(PotionEffectType.NIGHT_VISION);
        resetView(p);
        if (!plugin.admin().isVanished(p)) for (Player o : Bukkit.getOnlinePlayers()) if (!o.equals(p)) o.showPlayer(plugin, p);
        // er selbst soll andere Zuschauer wieder nicht sehen
        for (UUID u : specs) {
            Player sp = Bukkit.getPlayer(u);
            if (sp != null) p.hidePlayer(plugin, sp);
        }
    }

    /** Nach der Runde: alle wieder sichtbar. */
    public void clear() {
        for (UUID u : new ArrayList<>(specs)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) leave(p);
        }
        specs.clear();
        noclip.clear();
        for (Player a : Bukkit.getOnlinePlayers())
            for (Player b : Bukkit.getOnlinePlayers()) if (!a.equals(b) && !a.canSee(b) && !plugin.admin().isVanished(b)) a.showPlayer(plugin, b);
        lastFight = null;
    }

    /** Wer sieht wen: Lebende sehen keine Zuschauer, Zuschauer sehen Zuschauer nur mit Option. */
    private void updateVisibility(Player spec) {
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.equals(spec)) continue;
            boolean oSpec = specs.contains(o.getUniqueId());
            if (oSpec && showSpecs.contains(o.getUniqueId())) o.showPlayer(plugin, spec);
            else o.hidePlayer(plugin, spec);
            // spec sieht: alle Lebenden, Zuschauer nur mit Option, Vanish nie
            if (plugin.admin().isVanished(o) && !spec.hasPermission("odv.staff")) spec.hidePlayer(plugin, o);
            else if (!oSpec || showSpecs.contains(spec.getUniqueId())) spec.showPlayer(plugin, o);
            else spec.hidePlayer(plugin, o);
        }
    }

    private void giveItems(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        var it = plugin.items();
        inv.setItem(0, it.menu(Material.COMPASS, I_COMPASS, "§b§lSpieler-Teleporter §8(Rechtsklick)", "§7Alle lebenden Spieler nach Team"));
        inv.setItem(1, it.menu(Material.BLAZE_POWDER, I_FIGHT, "§c§lZum letzten Kampf §8(Rechtsklick)", "§7Teleportiert dich zur letzten Action"));
        if (s().has(Feature.BETTING)) inv.setItem(4, it.menu(Material.GOLD_INGOT, I_BET, "§6§lWetten §8(Rechtsklick)", "§7Tippe auf den Sieger"));
        inv.setItem(7, it.menu(Material.REDSTONE, I_OPTIONS, "§e§lZuschauer-Optionen §8(Rechtsklick)", "§7Nachtsicht, Tempo, Noclip …"));
        inv.setItem(8, it.menu(Material.RED_BED, I_LOBBY, "§a§lZur Lobby §8(Rechtsklick)", "§7Zurück auf die Lobby-Insel"));
        inv.setHeldItemSlot(0);
    }

    // ================================================================ Items

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!isSpec(p)) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() == org.bukkit.event.block.Action.PHYSICAL) return;
        String id = plugin.items().id(e.getItem());
        if (id == null) return;
        switch (id) {
            case I_COMPASS -> teleporter(p, 0);
            case I_FIGHT -> toFight(p);
            case I_BET -> Menus.bet(plugin, p);
            case I_OPTIONS -> options(p);
            case I_LOBBY -> {
                Location l = plugin.arena().getLobbyOrDefault();
                if (l != null) p.teleport(l);
            }
            default -> {
            }
        }
    }

    private void toFight(Player p) {
        if (lastFight != null && System.currentTimeMillis() - lastFightTime < 60_000 && allowed(p, lastFight)) {
            p.teleport(lastFight.clone().add(0, 4, 0));
            Txt.actionBar(p, "§cLetzter Kampf §7vor §f" + (System.currentTimeMillis() - lastFightTime) / 1000 + "s");
            return;
        }
        List<Player> alive = visibleAlive(p);
        if (alive.isEmpty()) {
            Txt.actionBar(p, "§7Gerade kein Kampf.");
            return;
        }
        Player t = alive.get((int) (Math.random() * alive.size()));
        p.teleport(t.getLocation().add(0, 3, 0));
        Txt.actionBar(p, "§7Kein Kampf – zufälliger Spieler: §f" + t.getName());
    }

    /** Darf der Zuschauer dorthin (spectator-radius)? */
    private boolean allowed(Player spec, Location l) {
        int r = s().spectatorRadius;
        Location c = plugin.arena().getCenter();
        if (r <= 0 || c == null || spec.hasPermission("odv.staff")) return true;
        if (!l.getWorld().equals(c.getWorld())) return false;
        double dx = l.getX() - c.getX(), dz = l.getZ() - c.getZ();
        return dx * dx + dz * dz <= (double) r * r;
    }

    private List<Player> visibleAlive(Player viewer) {
        List<Player> out = new ArrayList<>();
        for (UUID u : plugin.game().getAlive()) {
            Player t = Bukkit.getPlayer(u);
            if (t != null && allowed(viewer, t.getLocation())) out.add(t);
        }
        return out;
    }

    // ================================================================ Teleporter

    public void teleporter(Player p, int page) {
        List<Player> alive = visibleAlive(p);
        alive.sort(Comparator.<Player>comparingInt(t -> {
            MTeam tm = plugin.teams().getTeam(t);
            return tm == null ? 999 : tm.getNumber();
        }).thenComparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        int per = 45, pages = Math.max(1, (alive.size() + per - 1) / per);
        int pg = Math.max(0, Math.min(page, pages - 1));
        String limit = s().spectatorRadius > 0 && !p.hasPermission("odv.staff") ? " §8(Spawnbereich)" : "";
        ActionGui g = new ActionGui(6, "§8» §b§lSpieler §8(" + alive.size() + " leben)" + limit)
                .fill(plugin.items().of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        for (int i = 0; i < per && pg * per + i < alive.size(); i++) {
            Player t = alive.get(pg * per + i);
            MTeam team = plugin.teams().getTeam(t);
            boolean solo = s().isSolo() || team == null;
            String col = solo ? "§a" : team.getColor().toString();
            double hp = t.getHealth();
            String hearts = (hp > 14 ? "§a" : hp > 7 ? "§e" : "§c") + String.format(Locale.ROOT, "%.1f", hp / 2.0) + " ❤";
            int dist = p.getWorld().equals(t.getWorld()) ? (int) p.getLocation().distance(t.getLocation()) : -1;
            List<String> lore = new ArrayList<>();
            if (!solo) lore.add("§7Team: " + team.getDisplayName());
            lore.add("§7Leben: " + hearts);
            lore.add("§7Kills: §c" + plugin.game().getKills(t.getUniqueId()));
            lore.add("§7Welt: §f" + worldName(t.getWorld()) + (dist >= 0 ? " §8┃ §7" + dist + " Blöcke" : ""));
            if (plugin.punish().inCombat(t)) lore.add("§c⚔ Im Kampf");
            lore.add("");
            lore.add("§eKlick: §7hinteleportieren");
            ItemStack head = plugin.items().of(Material.PLAYER_HEAD).name(col + t.getName()).lore(lore).build();
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setPlayerProfile(t.getPlayerProfile());
            head.setItemMeta(meta);
            g.set(i, head, (v, c) -> {
                v.closeInventory();
                if (!plugin.game().isAlive(t) || !t.isOnline()) {
                    Txt.actionBar(v, "§c" + t.getName() + " lebt nicht mehr.");
                    return;
                }
                Location to = t.getLocation().clone();
                to.add(to.getDirection().setY(0).normalize().multiply(-3)).add(0, 2, 0);
                to.setDirection(t.getLocation().toVector().subtract(to.toVector()));
                v.teleport(to);
                v.playSound(v.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.4f);
            });
        }
        if (pg > 0) g.set(45, plugin.items().of(Material.ARROW).name("§7« Seite " + pg).build(), (v, c) -> teleporter(v, pg - 1));
        if (pg < pages - 1) g.set(53, plugin.items().of(Material.ARROW).name("§7Seite " + (pg + 2) + " »").build(), (v, c) -> teleporter(v, pg + 1));
        g.set(49, plugin.items().of(Material.CLOCK).name("§b§lAktualisieren").build(), (v, c) -> teleporter(v, pg));
        g.open(p);
    }

    private static String worldName(World w) {
        return switch (w.getEnvironment()) {
            case NETHER -> "§cNether";
            case THE_END -> "§dEnd";
            default -> "§aOberwelt";
        };
    }

    // ================================================================ Optionen

    public void options(Player p) {
        UUID id = p.getUniqueId();
        ActionGui g = new ActionGui(3, "§8» §e§lZuschauer-Optionen").fill(plugin.items().of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        boolean nv = !noNightVision.contains(id);
        g.set(10, plugin.items().of(nv ? Material.ENDER_EYE : Material.ENDER_PEARL).name("§b§lNachtsicht: " + (nv ? "§aAn" : "§cAus")).build(), (v, c) -> {
            if (noNightVision.remove(id)) v.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false));
            else {
                noNightVision.add(id);
                v.removePotionEffect(PotionEffectType.NIGHT_VISION);
            }
            options(v);
        });
        float sp = speed.getOrDefault(id, 0.1f);
        int level = Math.round(sp / 0.1f);
        g.set(12, plugin.items().of(Material.FEATHER, Math.max(1, level)).name("§f§lFlugtempo: §e" + level + "x").lore("§eKlick: §7schneller (1–4)").build(), (v, c) -> {
            int next = level >= 4 ? 1 : level + 1;
            speed.put(id, next * 0.1f);
            v.setFlySpeed(Math.min(1f, next * 0.1f));
            options(v);
        });
        boolean show = showSpecs.contains(id);
        g.set(14, plugin.items().of(show ? Material.GLASS : Material.TINTED_GLASS).name("§7§lAndere Zuschauer: " + (show ? "§aSichtbar" : "§cVersteckt")).build(), (v, c) -> {
            if (!showSpecs.remove(id)) showSpecs.add(id);
            for (UUID u : specs) {
                Player o = Bukkit.getPlayer(u);
                if (o != null) updateVisibility(o);
            }
            options(v);
        });
        g.set(16, plugin.items().of(Material.PHANTOM_MEMBRANE).name("§d§lNoclip (durch Wände)").lore("§7Wechselt in den Zuschauermodus.", "§7Zurück mit §e/spec").build(), (v, c) -> {
            v.closeInventory();
            noclip.add(id);
            v.setGameMode(GameMode.SPECTATOR);
            Txt.send(v, s().prefix + "§7Noclip an. §eZurück mit /spec");
        });
        g.open(p);
    }

    /** /spec: aus Noclip zurück bzw. Teleporter öffnen. */
    public void specCommand(Player p) {
        if (!isSpec(p)) {
            Menus.spectate(plugin, p, 0);
            return;
        }
        if (noclip.remove(p.getUniqueId())) {
            Location l = p.getLocation();
            enter(p, l);
            return;
        }
        teleporter(p, 0);
    }

    // ================================================================ Schutz

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFightTrack(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player v && (e.getDamager() instanceof Player || e.getDamager() instanceof Projectile)) {
            if (plugin.game().isAlive(v)) {
                lastFight = v.getLocation();
                lastFightTime = System.currentTimeMillis();
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent e) {
        if (specs.isEmpty()) return;
        if (e.getEntity() instanceof Player p && isSpec(p)) {
            e.setCancelled(true);
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
                Location l = plugin.arena().getLobbyOrDefault();
                if (l != null) p.teleport(l);
            }
            return;
        }
        if (e instanceof EntityDamageByEntityEvent be) {
            Entity d = be.getDamager();
            if (d instanceof Player p && isSpec(p)) e.setCancelled(true);
            else if (d instanceof Projectile pr && pr.getShooter() instanceof Player p && isSpec(p)) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProjectile(ProjectileHitEvent e) {
        if (!specs.isEmpty() && e.getHitEntity() instanceof Player p && isSpec(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent e) {
        if (isSpec(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent e) {
        if (isSpec(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && isSpec(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent e) {
        if (isSpec(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (isSpec(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        if (isSpec(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (!specs.isEmpty() && e.getTarget() instanceof Player p && isSpec(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onVehicle(VehicleEnterEvent e) {
        if (e.getEntered() instanceof Player p && isSpec(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && isSpec(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInvClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && isSpec(p) && e.getClickedInventory() == p.getInventory()) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        for (UUID u : specs) {
            Player sp = Bukkit.getPlayer(u);
            if (sp != null && !sp.equals(p)) p.hidePlayer(plugin, sp);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (specs.remove(id)) {
            Player p = e.getPlayer();
            p.setInvulnerable(false);
            p.setCollidable(true);
            p.setCanPickupItems(true);
            p.setSilent(false);
            p.setAllowFlight(false);
            p.setFlying(false);
            p.removePotionEffect(PotionEffectType.NIGHT_VISION);
            resetView(p);
        }
        noclip.remove(id);
    }

    private static void resetView(Player p) {
        try {
            p.setViewDistance(p.getWorld().getViewDistance());
            p.setSendViewDistance(p.getWorld().getSendViewDistance());
        } catch (Throwable ignored) {
        }
    }
}
