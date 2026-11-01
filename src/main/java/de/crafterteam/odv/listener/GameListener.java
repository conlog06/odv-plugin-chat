package de.crafterteam.odv.listener;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.game.GameManager;
import de.crafterteam.odv.game.GameState;
import de.crafterteam.odv.gui.Menus;
import de.crafterteam.odv.gui.SetupGui;
import de.crafterteam.odv.team.MTeam;
import de.crafterteam.odv.util.Txt;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Set;
import java.util.UUID;

public class GameListener implements Listener {

    private static final Set<PotionEffectType> HARMFUL = Set.of(PotionEffectType.INSTANT_DAMAGE, PotionEffectType.POISON,
            PotionEffectType.WEAKNESS, PotionEffectType.SLOWNESS, PotionEffectType.MINING_FATIGUE, PotionEffectType.BLINDNESS,
            PotionEffectType.NAUSEA, PotionEffectType.HUNGER, PotionEffectType.WITHER, PotionEffectType.DARKNESS);

    private final OdvPlugin plugin;

    public GameListener(OdvPlugin plugin) {
        this.plugin = plugin;
    }

    private GameManager game() {
        return plugin.game();
    }

    private boolean locked() {
        GameState s = game().getState();
        return s.isLobby() || s == GameState.ENDING;
    }

    private boolean builder(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && p.hasPermission("odv.admin");
    }

    private boolean practice(Player p) {
        return plugin.practice().isIn(p);
    }

    // ------------------------------------------------------------ Join / Quit

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        e.joinMessage(game().getState().isLobby() ? Txt.c("§8[§a+§8] §7" + p.getName()) : null);
        game().onJoin(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        e.quitMessage(game().getState().isLobby() ? Txt.c("§8[§c-§8] §7" + e.getPlayer().getName()) : null);
        game().onQuit(e.getPlayer());
        Menus.refreshAllTeams(plugin);
    }

    // ------------------------------------------------------------ Tod

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        e.deathMessage(null);
        if (practice(p)) {
            e.getDrops().clear();
            e.setDroppedExp(0);
            plugin.practice().onKill(p, p.getKiller());
            return;
        }
        if (game().getState().isIngame() && game().isAlive(p)) {
            game().eliminate(p.getUniqueId(), p.getKiller(), p.getLocation(), "ist gestorben");
        } else {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (practice(p) && plugin.arena().getPractice() != null) {
            e.setRespawnLocation(plugin.arena().getPractice());
            later(() -> plugin.practice().respawn(p));
            return;
        }
        if (game().getState().isLobby()) {
            e.setRespawnLocation(plugin.arena().getLobbyOrDefault());
            later(() -> game().toLobby(p));
            return;
        }
        Location death = game().getDeathLocation(p.getUniqueId());
        if (death != null) e.setRespawnLocation(death);
        later(() -> {
            if (!game().isAlive(p)) game().toSpectator(p, death);
        });
    }

    private void later(Runnable r) {
        Bukkit.getScheduler().runTaskLater(plugin, r, 1L);
    }

    // ------------------------------------------------------------ Schaden

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (practice(p)) {
            if (e.getCause() == EntityDamageEvent.DamageCause.FALL || e.getCause() == EntityDamageEvent.DamageCause.VOID) e.setCancelled(true);
            return;
        }
        if (locked() || game().getState() == GameState.FREEZE) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = attacker(e.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        if (practice(victim) || practice(attacker)) {
            if (!(practice(victim) && practice(attacker))) e.setCancelled(true);
            return;
        }
        if (game().getState() != GameState.RUNNING) {
            e.setCancelled(true);
            return;
        }
        if (!plugin.settings().friendlyFire && plugin.teams().sameTeam(attacker.getUniqueId(), victim.getUniqueId())) e.setCancelled(true);
    }

    private static Player attacker(Entity d) {
        if (d instanceof Player p) return p;
        if (d instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        if (d instanceof TNTPrimed t && t.getSource() instanceof Player p) return p;
        if (d instanceof Tameable t) {
            AnimalTamer owner = t.getOwner();
            if (owner instanceof Player p) return p;
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPotion(PotionSplashEvent e) {
        if (!(e.getPotion().getShooter() instanceof Player thrower)) return;
        boolean harmful = false;
        for (PotionEffect pe : e.getPotion().getEffects()) if (HARMFUL.contains(pe.getType())) harmful = true;
        if (!harmful) return;
        for (LivingEntity le : e.getAffectedEntities()) {
            if (!(le instanceof Player v) || v.equals(thrower)) continue;
            boolean block = (game().getState() != GameState.RUNNING && !(practice(v) && practice(thrower)))
                    || (!plugin.settings().friendlyFire && plugin.teams().sameTeam(thrower.getUniqueId(), v.getUniqueId()));
            if (block) e.setIntensity(v, 0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && (practice(p) || locked() || game().getState() == GameState.FREEZE)) e.setCancelled(true);
    }

    // ------------------------------------------------------------ Bewegung

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Location from = e.getFrom(), to = e.getTo();
        if (game().getState() == GameState.FREEZE && game().isAlive(p)) {
            if (from.getX() != to.getX() || from.getZ() != to.getZ() || to.getY() > from.getY()) {
                Location back = from.clone();
                back.setYaw(to.getYaw());
                back.setPitch(to.getPitch());
                e.setTo(back);
            }
            return;
        }
        // Von der Lobby-Insel gefallen?
        if (from.getBlockY() == to.getBlockY()) return;
        Location lobby = plugin.arena().getLobby();
        if (lobby == null || p.getGameMode() == GameMode.SPECTATOR || builder(p) || !p.getWorld().equals(lobby.getWorld())) return;
        boolean inLobbyPhase = locked() && !game().isAlive(p);
        if ((inLobbyPhase || practice(p)) && to.getY() < lobby.getY() - 12) {
            if (practice(p)) plugin.practice().respawn(p);
            else p.teleport(lobby);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent e) {
        if (!game().isPlaying(e.getPlayer()) || !plugin.settings().nether) {
            e.setCancelled(true);
            Txt.actionBar(e.getPlayer(), "§cDas Netherportal ist gerade gesperrt.");
        }
    }

    // ------------------------------------------------------------ Bauen / Schutz

    private boolean denyBuild(Player p, Location l) {
        if (builder(p)) return false;
        if (locked() || practice(p) || game().getState() == GameState.FREEZE) return true;
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
        e.blockList().removeIf(b -> plugin.arena().isProtected(b.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if ((locked() || practice(p)) && !builder(p)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && (locked() || practice(p)) && !builder(p)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (locked() && !practice(e.getPlayer()) && !builder(e.getPlayer())) e.setCancelled(true);
    }

    // ------------------------------------------------------------ Lobby-Items

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!game().getState().isLobby() || practice(p)) return;
        if (e.getAction() == Action.PHYSICAL) {
            if (!builder(p)) e.setCancelled(true);
            return;
        }
        String id = plugin.items().id(e.getItem());
        if (id == null || e.getHand() != EquipmentSlot.HAND) return;
        e.setCancelled(true);
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        switch (id) {
            case GameManager.I_TEAMS -> Menus.teams(plugin, p);
            case GameManager.I_STATS -> plugin.command().showStats(p, p.getName());
            case GameManager.I_SCENARIOS -> Menus.scenarios(plugin, p);
            case GameManager.I_PRACTICE -> plugin.practice().join(p);
            case GameManager.I_PLAY -> game().toggleParticipation(p);
            case GameManager.I_SETUP -> {
                if (p.hasPermission("odv.admin")) SetupGui.open(plugin, p, SetupGui.Page.MAIN);
            }
            case GameManager.I_START -> {
                if (!p.hasPermission("odv.admin")) return;
                String err = game().forceStart(10);
                if (err != null) Txt.send(p, plugin.settings().prefix + err);
                else {
                    if (game().getHost() == null) game().setHost(p.getUniqueId());
                    game().broadcast("§aRunde gestartet von §e" + p.getName() + "§a.");
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------ GUIs

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        InventoryHolder h = e.getInventory().getHolder(false);
        boolean top = e.getRawSlot() >= 0 && e.getRawSlot() < e.getInventory().getSize();
        if (h instanceof SetupGui g) {
            e.setCancelled(true);
            if (top && p.hasPermission("odv.admin")) g.click(p, e.getRawSlot(), e.getClick());
            return;
        }
        if (h instanceof Menus m) {
            e.setCancelled(true);
            if (top) m.click(p, e.getRawSlot());
            return;
        }
        if (locked() && !practice(p) && !builder(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder h = e.getInventory().getHolder(false);
        if (h instanceof SetupGui || h instanceof Menus) e.setCancelled(true);
        else if (locked() && e.getWhoClicked() instanceof Player p && !practice(p) && !builder(p)) e.setCancelled(true);
    }

    // ------------------------------------------------------------ Chat

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        MTeam team = plugin.teams().getTeam(p);
        String msg = Txt.plain(e.message());
        if (msg.startsWith("@") && msg.length() > 1 && team != null && !plugin.settings().isSolo()) {
            e.setCancelled(true);
            Component out = Txt.c("§8[§bTeam§8] " + team.getColor() + p.getName() + " §8» §f").append(Component.text(msg.substring(1).trim()));
            for (UUID u : team.getMembers()) {
                Player o = Bukkit.getPlayer(u);
                if (o != null) o.sendMessage(out);
            }
            return;
        }
        boolean spectator = !game().getState().isLobby() && !game().isAlive(p);
        String prefix = spectator ? "§7[Zuschauer] §7" : team != null && !plugin.settings().isSolo() ? team.getColor() + "[" + team.getName() + "] " : "§f";
        if (p.hasPermission("odv.admin")) prefix = "§c[A] " + prefix;
        Component pre = Txt.c(prefix);
        e.renderer((source, name, message, viewer) -> pre.append(Component.text(source.getName())).append(Txt.c(" §8» §f")).append(message));
    }
}
