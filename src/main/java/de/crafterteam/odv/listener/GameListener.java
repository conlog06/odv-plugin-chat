package de.crafterteam.odv.listener;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.game.GameManager;
import de.crafterteam.odv.game.GameState;
import de.crafterteam.odv.gui.SetupGui;
import de.crafterteam.odv.gui.SpectatorGui;
import de.crafterteam.odv.gui.TeamGui;
import de.crafterteam.odv.team.MTeam;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

public class GameListener implements Listener {

    private static final Set<PotionEffectType> HARMFUL = new HashSet<PotionEffectType>(Arrays.asList(
            PotionEffectType.HARM, PotionEffectType.POISON, PotionEffectType.WEAKNESS, PotionEffectType.SLOW,
            PotionEffectType.SLOW_DIGGING, PotionEffectType.BLINDNESS, PotionEffectType.CONFUSION,
            PotionEffectType.HUNGER, PotionEffectType.WITHER));

    private final OdvPlugin plugin;

    public GameListener(OdvPlugin plugin) {
        this.plugin = plugin;
    }

    private GameManager game() {
        return plugin.game();
    }

    /** Lobby, Vorbereitung, Rundenende: alles gesperrt. */
    private boolean locked() {
        GameState s = game().getState();
        return s.isLobby() || s == GameState.ENDING;
    }

    private boolean builder(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && p.hasPermission("odv.admin");
    }

    // ------------------------------------------------------------ Join / Quit

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        e.setJoinMessage(game().getState().isLobby() ? "§8[§a+§8] §7" + p.getName() : null);
        game().onJoin(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        e.setQuitMessage(game().getState().isLobby() ? "§8[§c-§8] §7" + p.getName() : null);
        game().onQuit(p);
        TeamGui.refreshAll(plugin);
    }

    // ------------------------------------------------------------ Tod

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        final Player p = e.getEntity();
        e.setDeathMessage(null);
        e.setKeepLevel(false);
        if (game().getState().isIngame() && game().isAlive(p)) {
            game().eliminate(p.getUniqueId(), p.getKiller(), p.getLocation(), "ist gestorben");
        } else {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                if (p.isOnline() && p.isDead()) p.spigot().respawn();
            }
        }.runTaskLater(plugin, 2L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        final Player p = e.getPlayer();
        if (game().getState().isLobby()) {
            e.setRespawnLocation(plugin.arena().getLobbyOrDefault());
            later(new Runnable() {
                public void run() { game().toLobby(p); }
            });
            return;
        }
        final Location death = game().getDeathLocation(p);
        if (death != null) e.setRespawnLocation(death);
        later(new Runnable() {
            public void run() { if (!game().isAlive(p)) game().toSpectator(p, death); }
        });
    }

    private void later(Runnable r) {
        Bukkit.getScheduler().runTaskLater(plugin, r, 1L);
    }

    // ------------------------------------------------------------ Schaden

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        if (locked() || game().getState() == GameState.FREEZE) {
            e.setCancelled(true);
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID && game().getState().isLobby()) {
                e.getEntity().teleport(plugin.arena().getLobbyOrDefault());
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player victim = (Player) e.getEntity();
        Player attacker = attacker(e.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        if (game().getState() != GameState.RUNNING) {
            e.setCancelled(true);
            return;
        }
        if (!plugin.settings().friendlyFire && plugin.teams().sameTeam(attacker.getUniqueId(), victim.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    private static Player attacker(Entity d) {
        if (d instanceof Player) return (Player) d;
        if (d instanceof Projectile) {
            ProjectileSource src = ((Projectile) d).getShooter();
            if (src instanceof Player) return (Player) src;
        }
        if (d instanceof TNTPrimed && ((TNTPrimed) d).getSource() instanceof Player) return (Player) ((TNTPrimed) d).getSource();
        if (d instanceof Tameable) {
            AnimalTamer owner = ((Tameable) d).getOwner();
            if (owner instanceof Player) return (Player) owner;
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPotion(PotionSplashEvent e) {
        if (!(e.getPotion().getShooter() instanceof Player)) return;
        Player thrower = (Player) e.getPotion().getShooter();
        boolean harmful = false;
        for (PotionEffect pe : e.getPotion().getEffects()) if (HARMFUL.contains(pe.getType())) harmful = true;
        if (!harmful) return;
        for (LivingEntity le : e.getAffectedEntities()) {
            if (!(le instanceof Player) || le.equals(thrower)) continue;
            boolean block = game().getState() != GameState.RUNNING
                    || (!plugin.settings().friendlyFire && plugin.teams().sameTeam(thrower.getUniqueId(), le.getUniqueId()));
            if (block) e.setIntensity(le, 0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (locked() || game().getState() == GameState.FREEZE) e.setCancelled(true);
    }

    // ------------------------------------------------------------ Freeze

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (game().getState() != GameState.FREEZE || !game().isAlive(e.getPlayer())) return;
        Location from = e.getFrom(), to = e.getTo();
        if (from.getX() != to.getX() || from.getZ() != to.getZ() || to.getY() > from.getY()) {
            Location back = from.clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            e.setTo(back);
        }
    }

    // ------------------------------------------------------------ Bauen / Lobby-Schutz

    private boolean denyBuild(Player p, Location l) {
        if (builder(p)) return false;
        if (locked() || game().getState() == GameState.FREEZE) return true;
        return plugin.arena().isProtected(l);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (denyBuild(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (denyBuild(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (denyBuild(e.getPlayer(), e.getBlockClicked().getRelative(e.getBlockFace()).getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        Iterator<Block> it = e.blockList().iterator();
        while (it.hasNext()) if (plugin.arena().isProtected(it.next().getLocation())) it.remove();
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (locked() && !builder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(PlayerPickupItemEvent e) {
        if (locked() && !builder(e.getPlayer())) e.setCancelled(true);
    }

    // ------------------------------------------------------------ Lobby-Items

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (!game().getState().isLobby()) return;
        if (e.getAction() == Action.PHYSICAL) {
            e.setCancelled(!builder(e.getPlayer()));
            return;
        }
        ItemStack it = e.getItem();
        if (it == null || !it.hasItemMeta() || !it.getItemMeta().hasDisplayName()) return;
        String name = it.getItemMeta().getDisplayName();
        if (!GameManager.MENU_ITEMS.contains(name)) return;
        e.setCancelled(true);
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (name.equals(GameManager.ITEM_TEAMS)) {
            TeamGui.open(plugin, p);
        } else if (name.equals(GameManager.ITEM_STATS)) {
            plugin.command().showStats(p, p.getUniqueId());
        } else if (name.equals(GameManager.ITEM_SETUP) && p.hasPermission("odv.admin")) {
            SetupGui.open(plugin, p, SetupGui.Page.MAIN);
        } else if (name.equals(GameManager.ITEM_START) && p.hasPermission("odv.admin")) {
            String err = game().forceStart(10);
            if (err != null) p.sendMessage(plugin.settings().prefix + err);
            else game().broadcast("§aRunde gestartet von §e" + p.getName() + "§a.");
        } else if (name.equals(GameManager.ITEM_PLAY) || name.equals(GameManager.ITEM_WATCH)) {
            game().toggleParticipation(p);
        }
    }

    // ------------------------------------------------------------ GUIs

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        InventoryHolder holder = e.getInventory().getHolder();
        boolean top = e.getRawSlot() >= 0 && e.getRawSlot() < e.getInventory().getSize();
        if (holder instanceof TeamGui) {
            e.setCancelled(true);
            if (top) ((TeamGui) holder).click(p, e.getRawSlot());
            return;
        }
        if (holder instanceof SetupGui) {
            e.setCancelled(true);
            if (top && p.hasPermission("odv.admin")) ((SetupGui) holder).click(p, e.getRawSlot(), e.getClick());
            return;
        }
        if (holder instanceof SpectatorGui) {
            e.setCancelled(true);
            if (top) ((SpectatorGui) holder).click(p, e.getRawSlot());
            return;
        }
        if (locked() && !builder(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder h = e.getInventory().getHolder();
        if (h instanceof TeamGui || h instanceof SetupGui || h instanceof SpectatorGui) e.setCancelled(true);
        else if (locked() && e.getWhoClicked() instanceof Player && !builder((Player) e.getWhoClicked())) e.setCancelled(true);
    }

    // ------------------------------------------------------------ Chat

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        MTeam team = plugin.teams().getTeam(p);
        boolean spectator = !game().getState().isLobby() && !game().isAlive(p);
        String msg = e.getMessage();
        if (msg.startsWith("@") && msg.length() > 1 && team != null && !plugin.settings().isSolo()) {
            e.setCancelled(true);
            String out = "§8[§bTeam§8] " + team.getColor() + p.getName() + " §8» §f" + msg.substring(1).trim();
            for (UUID u : team.getMembers()) {
                Player o = Bukkit.getPlayer(u);
                if (o != null) o.sendMessage(out);
            }
            return;
        }
        String prefix;
        if (spectator) prefix = "§7[Zuschauer] §7";
        else if (team != null && !plugin.settings().isSolo()) prefix = team.getColor() + "[" + team.getName() + "] ";
        else prefix = "§f";
        if (p.hasPermission("odv.admin")) prefix = "§c[A] " + prefix;
        e.setFormat(prefix + "%1$s §8» §f%2$s");
    }
}
