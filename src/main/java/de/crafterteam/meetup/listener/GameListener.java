package de.crafterteam.meetup.listener;

import de.crafterteam.meetup.MeetupPlugin;
import de.crafterteam.meetup.game.GameManager;
import de.crafterteam.meetup.game.GameState;
import de.crafterteam.meetup.gui.SetupGui;
import de.crafterteam.meetup.gui.TeamGui;
import de.crafterteam.meetup.team.MTeam;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;

public class GameListener implements Listener {

    private final MeetupPlugin plugin;

    public GameListener(MeetupPlugin plugin) {
        this.plugin = plugin;
    }

    private GameManager game() {
        return plugin.game();
    }

    private boolean lobby() {
        return game().getState().isLobby() || game().getState() == GameState.ENDING;
    }

    private boolean isBuilder(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && p.hasPermission("meetup.admin");
    }

    // ------------------------------------------------ Join / Quit

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        final Player p = e.getPlayer();
        e.setJoinMessage(game().getState().isLobby() ? plugin.settings().prefix + "§a" + p.getName() + " §7ist beigetreten." : null);
        game().createBoard(p);
        if (game().getState().isLobby()) {
            game().toLobby(p);
        } else {
            game().resetPlayer(p, GameMode.SPECTATOR);
            game().toSpectator(p, null);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        e.setQuitMessage(game().getState().isLobby() ? plugin.settings().prefix + "§c" + p.getName() + " §7hat verlassen." : null);
        game().removeBoard(p);
        if (game().getState().isLobby()) {
            plugin.teams().leave(p);
        } else if (game().getState().isIngame() && game().isAlive(p)) {
            Location l = p.getLocation();
            for (ItemStack it : p.getInventory().getContents()) if (it != null) l.getWorld().dropItemNaturally(l, it);
            for (ItemStack it : p.getInventory().getArmorContents()) {
                if (it != null && it.getType() != org.bukkit.Material.AIR) l.getWorld().dropItemNaturally(l, it);
            }
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
            game().eliminate(p, null, true);
        }
    }

    // ------------------------------------------------ Tod

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        final Player p = e.getEntity();
        e.setDeathMessage(null);
        if (game().getState().isIngame() && game().isAlive(p)) {
            game().eliminate(p, p.getKiller(), false);
        } else {
            e.getDrops().clear();
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
            e.setRespawnLocation(plugin.settings().getLobbyOrSpawn());
            new BukkitRunnable() {
                @Override
                public void run() {
                    game().toLobby(p);
                }
            }.runTaskLater(plugin, 1L);
            return;
        }
        final Location death = game().getDeathLocation(p);
        if (death != null) e.setRespawnLocation(death);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!game().isAlive(p)) game().toSpectator(p, death);
            }
        }.runTaskLater(plugin, 1L);
    }

    // ------------------------------------------------ Schaden

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        GameState st = game().getState();
        if (lobby() || st == GameState.FREEZE) {
            e.setCancelled(true);
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID && st.isLobby()) {
                e.getEntity().teleport(plugin.settings().getLobbyOrSpawn());
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player victim = (Player) e.getEntity();
        Player attacker = null;
        Entity d = e.getDamager();
        if (d instanceof Player) {
            attacker = (Player) d;
        } else if (d instanceof Projectile) {
            ProjectileSource src = ((Projectile) d).getShooter();
            if (src instanceof Player) attacker = (Player) src;
        }
        if (attacker == null || attacker.equals(victim)) return;
        if (game().getState() != GameState.RUNNING) {
            e.setCancelled(true);
            return;
        }
        if (!plugin.settings().friendlyFire && !plugin.settings().isSolo()) {
            MTeam a = plugin.teams().getTeam(attacker);
            if (a != null && a == plugin.teams().getTeam(victim)) e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (lobby() || game().getState() == GameState.FREEZE) e.setCancelled(true);
    }

    // ------------------------------------------------ Freeze

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

    // ------------------------------------------------ Lobby-Schutz

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if ((lobby() || game().getState() == GameState.FREEZE) && !isBuilder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if ((lobby() || game().getState() == GameState.FREEZE) && !isBuilder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (lobby() && !isBuilder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(PlayerPickupItemEvent e) {
        if (lobby() && !isBuilder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (!game().getState().isLobby()) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it == null || !it.hasItemMeta() || !it.getItemMeta().hasDisplayName()) return;
        String name = it.getItemMeta().getDisplayName();
        Player p = e.getPlayer();
        if (name.equals(GameManager.ITEM_TEAMS)) {
            e.setCancelled(true);
            new TeamGui(plugin).open(p);
        } else if (name.equals(GameManager.ITEM_SETUP) && p.hasPermission("meetup.admin")) {
            e.setCancelled(true);
            new SetupGui(plugin).open(p);
        } else if (name.equals(GameManager.ITEM_START) && p.hasPermission("meetup.admin")) {
            e.setCancelled(true);
            if (game().forceStart(10)) game().broadcast("§aDie Runde wurde von §e" + p.getName() + " §agestartet.");
        }
    }

    // ------------------------------------------------ GUIs

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        Object holder = e.getInventory().getHolder();
        if (holder instanceof TeamGui) {
            e.setCancelled(true);
            if (e.getRawSlot() < e.getInventory().getSize()) ((TeamGui) holder).click(p, e.getRawSlot());
            return;
        }
        if (holder instanceof SetupGui) {
            e.setCancelled(true);
            if (e.getRawSlot() < e.getInventory().getSize() && p.hasPermission("meetup.admin")) {
                ((SetupGui) holder).click(p, e.getRawSlot(), e.getClick());
            }
            return;
        }
        if (lobby() && !isBuilder(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        Object holder = e.getInventory().getHolder();
        if (holder instanceof TeamGui || holder instanceof SetupGui) e.setCancelled(true);
    }

    // ------------------------------------------------ Chat

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        MTeam team = plugin.teams().getTeam(p);
        boolean spectator = !game().getState().isLobby() && !game().isAlive(p);
        String msg = e.getMessage();

        // Teamchat mit @
        if (msg.startsWith("@") && team != null && !plugin.settings().isSolo() && msg.length() > 1) {
            e.setCancelled(true);
            String out = "§8[§bTeam§8] " + team.getColor() + p.getName() + " §8» §f" + msg.substring(1).trim();
            for (java.util.UUID u : team.getMembers()) {
                Player o = Bukkit.getPlayer(u);
                if (o != null) o.sendMessage(out);
            }
            return;
        }
        String prefix;
        if (spectator) prefix = "§7[Zuschauer] §7";
        else if (team != null && !plugin.settings().isSolo()) prefix = team.getColor() + "[" + team.getName() + "] ";
        else prefix = "§f";
        e.setFormat(prefix + "%1$s §8» §f%2$s");
    }
}
