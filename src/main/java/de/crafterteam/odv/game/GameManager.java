package de.crafterteam.odv.game;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.Settings;
import de.crafterteam.odv.data.Arena;
import de.crafterteam.odv.spawn.SpawnManager;
import de.crafterteam.odv.team.MTeam;
import de.crafterteam.odv.util.ItemBuilder;
import de.crafterteam.odv.util.Nms;
import de.crafterteam.odv.util.Sidebar;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GameManager {

    // Namen der Menü-Items (zum Wiedererkennen)
    public static final String ITEM_TEAMS = "§b§lTeams §8(Rechtsklick)";
    public static final String ITEM_STATS = "§e§lStatistik §8(Rechtsklick)";
    public static final String ITEM_SETUP = "§c§lSetup §8(Rechtsklick)";
    public static final String ITEM_START = "§a§lRunde starten §8(Rechtsklick)";
    public static final String ITEM_PLAY = "§a§lDu spielst mit §8(Klick: zuschauen)";
    public static final String ITEM_WATCH = "§7§lDu schaust zu §8(Klick: mitspielen)";
    public static final Set<String> MENU_ITEMS = new HashSet<String>(Arrays.asList(
            ITEM_TEAMS, ITEM_STATS, ITEM_SETUP, ITEM_START, ITEM_PLAY, ITEM_WATCH));

    private final OdvPlugin plugin;
    private GameState state = GameState.LOBBY;
    private int countdown, phase, elapsed;
    private boolean forced, shrinking, testRound;

    private final Set<UUID> alive = new HashSet<UUID>();
    private final Set<UUID> optedOut = new HashSet<UUID>();
    private final Map<UUID, Integer> kills = new HashMap<UUID, Integer>();
    private final Map<UUID, Location> deathLocations = new HashMap<UUID, Location>();
    private final Map<UUID, Integer> disconnected = new HashMap<UUID, Integer>();
    private final Map<UUID, Location> returnLocations = new HashMap<UUID, Location>();
    private final Map<UUID, ItemStack[]> quitInventories = new HashMap<UUID, ItemStack[]>();
    private final Map<UUID, Sidebar> boards = new HashMap<UUID, Sidebar>();

    public GameManager(OdvPlugin plugin) {
        this.plugin = plugin;
    }

    private Settings s() {
        return plugin.settings();
    }

    private Arena arena() {
        return plugin.arena();
    }

    public GameState getState() { return state; }
    public boolean isAlive(Player p) { return alive.contains(p.getUniqueId()); }
    public boolean isAlive(UUID id) { return alive.contains(id); }
    public Set<UUID> getAlive() { return Collections.unmodifiableSet(alive); }
    public boolean isOptedOut(Player p) { return optedOut.contains(p.getUniqueId()); }
    public int getKills(UUID u) { Integer k = kills.get(u); return k == null ? 0 : k; }
    public Location getDeathLocation(Player p) { return deathLocations.get(p.getUniqueId()); }

    public List<Player> candidates() {
        List<Player> list = new ArrayList<Player>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("odv.play") && !optedOut.contains(p.getUniqueId())) list.add(p);
        }
        return list;
    }

    public void startTicker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    tick();
                } catch (RuntimeException e) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Fehler im Spieltakt – Runde wird zurückgesetzt.", e);
                    resetToLobby();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    // =====================================================================
    //  Takt (jede Sekunde)
    // =====================================================================

    private void tick() {
        switch (state) {
            case LOBBY:
                if (s().autoStart && arena().isReady() && candidates().size() >= Math.max(2, s().minPlayers)) {
                    beginCountdown(s().countdown, false);
                }
                break;
            case STARTING:
                int c = candidates().size();
                if (c == 0 || (!forced && c < s().minPlayers)) {
                    state = GameState.LOBBY;
                    forced = false;
                    broadcast("§cZu wenige Spieler – Countdown abgebrochen.");
                    setLevels(0);
                    break;
                }
                setLevels(countdown);
                if (countdown == 60 || countdown == 30 || countdown == 15 || countdown == 10 || (countdown <= 5 && countdown > 0)) {
                    broadcast("§7Die Runde startet in §e" + countdown + " §7Sekunde" + (countdown == 1 ? "" : "n") + ".");
                    sound(Sound.NOTE_PLING, 1f);
                    if (countdown <= 5) for (Player p : Bukkit.getOnlinePlayers()) Nms.title(p, "§e" + countdown, s().mode.title);
                }
                if (countdown <= 0) startGame();
                else countdown--;
                break;
            case FREEZE:
                if (phase > 0) {
                    for (Player p : Bukkit.getOnlinePlayers()) Nms.title(p, "§e" + phase, "§7Gleich geht's los...");
                    sound(Sound.NOTE_STICKS, 1f);
                    phase--;
                } else {
                    release();
                }
                break;
            case PROTECTION:
                elapsed++;
                phase--;
                if (phase == 60 || phase == 30 || phase == 10 || (phase <= 3 && phase > 0)) {
                    broadcast("§7PvP in §e" + Settings.time(phase) + "§7.");
                }
                if (phase <= 0) {
                    state = GameState.RUNNING;
                    broadcast("§c§lDie Schutzzeit ist vorbei – PvP ist aktiv!");
                    for (Player p : Bukkit.getOnlinePlayers()) Nms.title(p, "§c§lPvP", "§7ist jetzt aktiv");
                    sound(Sound.ENDERDRAGON_GROWL, 0.8f);
                }
                tickRunning();
                break;
            case RUNNING:
                elapsed++;
                tickRunning();
                break;
            default:
                break;
        }
        updateDisplays();
    }

    private void tickRunning() {
        // Wiederverbindungs-Fristen
        for (UUID id : new ArrayList<UUID>(disconnected.keySet())) {
            int left = disconnected.get(id) - 1;
            if (left <= 0) {
                disconnected.remove(id);
                Location l = returnLocations.remove(id);
                ItemStack[] inv = quitInventories.remove(id);
                if (l != null && inv != null) {
                    for (ItemStack it : inv) if (it != null && it.getType() != Material.AIR) l.getWorld().dropItemNaturally(l, it);
                }
                eliminate(id, null, l, "ist nach dem Disconnect ausgeschieden");
            } else {
                disconnected.put(id, left);
            }
        }
        // Border
        if (!shrinking) {
            int left = s().shrinkAfter - elapsed;
            if (left == 60) broadcast("§7Die Border schrumpft in §e1 Minute§7.");
            if (left <= 0) {
                shrinking = true;
                World w = arena().getWorld();
                if (w != null) w.getWorldBorder().setSize(s().borderFinal, s().shrinkDuration);
                broadcast("§c§lDie Border schrumpft! §7Auf §b" + s().borderFinal + " §7Blöcke in §e" + Settings.time(s().shrinkDuration) + "§7.");
                sound(Sound.WITHER_SPAWN, 0.6f);
            }
        }
    }

    private void beginCountdown(int seconds, boolean force) {
        state = GameState.STARTING;
        countdown = seconds;
        forced = force;
        broadcast("§aGenug Spieler! §7Start in §e" + seconds + " §7Sekunden.");
    }

    /** Start erzwingen / Countdown verkürzen. */
    public String forceStart(int seconds) {
        if (!arena().isReady()) return "§cDie Arena ist noch nicht eingerichtet. §7(/odv → Schnellsetup)";
        if (spawnsBusy()) return "§cEs wird gerade gebaut – kurz warten.";
        if (candidates().isEmpty()) return "§cKeine Teilnehmer online.";
        if (!plugin.teams().hasCapacity(candidates().size())) return "§cNicht genug Teamplätze – Teamanzahl oder -größe erhöhen.";
        if (state == GameState.LOBBY) {
            beginCountdown(seconds, true);
            return null;
        }
        if (state == GameState.STARTING) {
            forced = true;
            if (countdown > seconds) countdown = seconds;
            return null;
        }
        return "§cEs läuft bereits eine Runde.";
    }

    private boolean spawnsBusy() {
        return plugin.spawns().isBusy();
    }

    // =====================================================================
    //  Start
    // =====================================================================

    private void startGame() {
        List<Player> players = candidates();
        if (players.isEmpty() || !arena().isReady()) {
            state = GameState.LOBBY;
            return;
        }
        if (s().backupOnStart) plugin.stats().backup(null);
        state = GameState.PREPARING;
        forced = false;
        plugin.teams().finalizeTeams(players);
        testRound = plugin.teams().getTeams().size() < 2;
        applyWorldSettings();

        int needed = plugin.teams().getTeams().size();
        boolean regenerate = s().regenerateOnStart || arena().getSpawns().size() < needed;
        if (regenerate) {
            broadcast("§7Spawns werden vorbereitet...");
            for (Player p : players) Nms.title(p, s().mode.title, "§7Spawns werden gebaut...");
            boolean started = plugin.spawns().generate(needed, null, new Runnable() {
                @Override
                public void run() {
                    teleportAndFreeze();
                }
            });
            if (!started) teleportAndFreeze();
        } else {
            teleportAndFreeze();
        }
    }

    private void teleportAndFreeze() {
        if (state != GameState.PREPARING) return;
        World world = arena().getWorld();
        List<MTeam> teams = plugin.teams().getTeams();
        List<Location> spawns = new ArrayList<Location>(arena().getSpawns());
        if (spawns.size() < teams.size()) {
            spawns.addAll(plugin.spawns().ring(world, arena().getCenter(), teams.size() - spawns.size(),
                    plugin.spawns().autoRadius(teams.size()) + 12));
        }
        Collections.shuffle(spawns);

        alive.clear();
        kills.clear();
        deathLocations.clear();
        disconnected.clear();
        returnLocations.clear();
        quitInventories.clear();
        elapsed = 0;
        shrinking = false;

        for (int i = 0; i < teams.size(); i++) {
            MTeam team = teams.get(i);
            List<Player> members = new ArrayList<Player>();
            for (UUID u : team.getMembers()) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) members.add(p);
            }
            Location base = spawns.get(i).clone();
            if (base.getWorld() == null) base.setWorld(world);
            List<Location> spots = plugin.spawns().memberSpots(base, members.size());
            for (int m = 0; m < members.size(); m++) {
                Player p = members.get(m);
                p.closeInventory();
                resetPlayer(p, GameMode.SURVIVAL);
                Location spot = spots.get(m);
                spot.setYaw(base.getYaw());
                p.teleport(spot);
                plugin.kits().give(p, s().mode);
                alive.add(p.getUniqueId());
                kills.put(p.getUniqueId(), 0);
                plugin.stats().name(p);
                plugin.stats().add(p.getUniqueId(), "games");
            }
        }
        plugin.stats().save();
        if (alive.isEmpty()) {
            broadcast("§cStart abgebrochen – keine Teilnehmer.");
            resetToLobby();
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!alive.contains(p.getUniqueId())) toSpectator(p, null);
        }
        broadcast("§a" + alive.size() + " Spieler §7in §a" + teams.size() + (s().isSolo() ? "" : " Teams") + " §7– viel Glück!"
                + (testRound ? " §8(Testrunde – endet mit /odv stop)" : ""));
        state = GameState.FREEZE;
        phase = s().freeze;
        if (phase <= 0) release();
    }

    private void release() {
        for (Player p : Bukkit.getOnlinePlayers()) Nms.title(p, "§a§lLos!", s().grace > 0 ? "§7Schutzzeit: §e" + Settings.time(s().grace) : "§cPvP ist aktiv");
        sound(Sound.LEVEL_UP, 1f);
        if (s().grace > 0) {
            state = GameState.PROTECTION;
            phase = s().grace;
            broadcast("§7Schutzzeit: §e" + Settings.time(phase) + " §7– kein PvP.");
        } else {
            state = GameState.RUNNING;
            broadcast("§c§lPvP ist aktiv!");
        }
    }

    // =====================================================================
    //  Ausscheiden / Sieg
    // =====================================================================

    /** @param where Ort für Effekte (bei Offline-Spielern die letzte Position) */
    public void eliminate(UUID id, Player killer, Location where, String reason) {
        if (!alive.remove(id)) return;
        disconnected.remove(id);
        returnLocations.remove(id);
        quitInventories.remove(id);
        if (where != null) deathLocations.put(id, where);
        MTeam team = plugin.teams().getTeam(id);
        String name = color(team, "§c") + plugin.stats().name(id);
        plugin.stats().add(id, "deaths");

        if (killer != null && !killer.getUniqueId().equals(id)) {
            kills.put(killer.getUniqueId(), getKills(killer.getUniqueId()) + 1);
            MTeam kt = plugin.teams().getTeam(killer);
            if (kt != null) kt.addKill();
            if (!plugin.teams().sameTeam(id, killer.getUniqueId())) plugin.stats().add(killer.getUniqueId(), "kills");
            String hp = String.format(java.util.Locale.ROOT, "%.1f", killer.getHealth() / 2.0);
            broadcast(name + " §7wurde von " + color(kt, "§a") + killer.getName() + " §8(§c❤ " + hp + "§8) §7getötet. §8[" + alive.size() + " übrig]");
            killer.playSound(killer.getLocation(), Sound.ORB_PICKUP, 1f, 1f);
        } else {
            broadcast(name + " §7" + reason + ". §8[" + alive.size() + " übrig]");
        }
        plugin.stats().save();

        if (where != null && where.getWorld() != null) {
            if (s().lightning) where.getWorld().strikeLightningEffect(where);
            if (s().goldenApple) where.getWorld().dropItemNaturally(where, new ItemStack(Material.GOLDEN_APPLE));
        }
        if (team != null && !s().isSolo()) {
            boolean any = false;
            for (UUID u : team.getMembers()) if (alive.contains(u)) any = true;
            if (!any) broadcast("§7Team " + team.getDisplayName() + " §7wurde §cvernichtet§7!");
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                checkWin();
            }
        }.runTaskLater(plugin, 2L);
    }

    private static String color(MTeam t, String def) {
        return t != null && t.getColor() != org.bukkit.ChatColor.WHITE ? t.getColor().toString() : def;
    }

    private void checkWin() {
        if (!state.isIngame()) return;
        List<MTeam> left = new ArrayList<MTeam>();
        for (MTeam t : plugin.teams().getTeams()) {
            for (UUID u : t.getMembers()) {
                if (alive.contains(u)) {
                    left.add(t);
                    break;
                }
            }
        }
        if (left.isEmpty() || (!testRound && left.size() == 1)) endGame(left.isEmpty() ? null : left.get(0));
    }

    private void endGame(final MTeam winner) {
        state = GameState.ENDING;
        String title;
        if (winner == null) title = "§7Unentschieden";
        else if (s().isSolo()) title = "§6" + winner.getName() + " §ahat gewonnen!";
        else title = "§7Team " + winner.getDisplayName() + " §ahat gewonnen!";
        if (winner != null) {
            for (UUID u : winner.getMembers()) plugin.stats().add(u, "wins");
            plugin.stats().save();
        }
        broadcast("§8§m                                        ");
        broadcast(title);
        List<Map.Entry<UUID, Integer>> top = topKillers(3);
        for (int i = 0; i < top.size(); i++) {
            broadcast("§e#" + (i + 1) + " §f" + plugin.stats().name(top.get(i).getKey()) + " §8- §c" + top.get(i).getValue() + " Kills");
        }
        broadcast("§8§m                                        ");
        for (Player p : Bukkit.getOnlinePlayers()) {
            Nms.title(p, title, "§7GG!");
            p.playSound(p.getLocation(), Sound.LEVEL_UP, 1f, 1f);
        }
        if (winner != null) {
            new BukkitRunnable() {
                int i = 0;

                @Override
                public void run() {
                    if (i++ >= 6 || state != GameState.ENDING) {
                        cancel();
                        return;
                    }
                    for (UUID u : winner.getMembers()) {
                        Player p = Bukkit.getPlayer(u);
                        if (p != null && p.getGameMode() != GameMode.SPECTATOR) firework(p.getLocation());
                    }
                }
            }.runTaskTimer(plugin, 10L, 25L);
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.ENDING) return;
                if (s().stopOnEnd) {
                    for (Player p : Bukkit.getOnlinePlayers()) p.kickPlayer("§bDie Runde ist vorbei!\n§7Der Server startet neu – bis gleich.");
                    Bukkit.shutdown();
                } else {
                    resetToLobby();
                    broadcast("§bNeue Lobby bereit.");
                }
            }
        }.runTaskLater(plugin, s().endDelay * 20L);
    }

    public void stop() {
        if (state == GameState.LOBBY) return;
        if (state == GameState.STARTING) {
            state = GameState.LOBBY;
            forced = false;
            setLevels(0);
            broadcast("§cDer Countdown wurde abgebrochen.");
            return;
        }
        broadcast("§cDie Runde wurde beendet.");
        resetToLobby();
    }

    public void resetToLobby() {
        state = GameState.LOBBY;
        forced = false;
        testRound = false;
        alive.clear();
        kills.clear();
        deathLocations.clear();
        disconnected.clear();
        returnLocations.clear();
        quitInventories.clear();
        elapsed = 0;
        shrinking = false;
        plugin.teams().reset();
        applyWorldSettings();
        World w = arena().getWorld();
        if (w != null) {
            for (Entity e : w.getEntities()) {
                if (e instanceof Item || e instanceof Projectile || e instanceof TNTPrimed || e instanceof ExperienceOrb || e instanceof FallingBlock) {
                    e.remove();
                }
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) toLobby(p);
    }

    // =====================================================================
    //  Join / Quit / Teilnahme
    // =====================================================================

    public void onJoin(Player p) {
        plugin.stats().name(p);
        boards.put(p.getUniqueId(), new Sidebar(p, s().mode.title));
        UUID id = p.getUniqueId();
        if (state.isIngame() && alive.contains(id) && disconnected.containsKey(id)) {
            disconnected.remove(id);
            quitInventories.remove(id);
            Location back = returnLocations.remove(id);
            p.setGameMode(GameMode.SURVIVAL);
            if (back != null) p.teleport(back);
            broadcast("§a" + p.getName() + " §7ist zurück.");
            return;
        }
        if (state.isLobby()) {
            toLobby(p);
            if (!arena().isReady() && p.hasPermission("odv.admin")) {
                p.sendMessage(s().prefix + "§eDie Arena ist noch nicht eingerichtet. §7Öffne §b/odv §7→ §aSchnellsetup§7.");
            }
        } else {
            alive.remove(id);
            resetPlayer(p, GameMode.SPECTATOR);
            toSpectator(p, null);
        }
    }

    public void onQuit(Player p) {
        boards.remove(p.getUniqueId());
        UUID id = p.getUniqueId();
        if (state.isLobby()) {
            plugin.teams().leave(id);
            return;
        }
        if (!state.isIngame() || !alive.contains(id)) return;
        if (s().disconnectGrace > 0) {
            disconnected.put(id, s().disconnectGrace);
            returnLocations.put(id, p.getLocation().clone());
            List<ItemStack> inv = new ArrayList<ItemStack>(Arrays.asList(p.getInventory().getContents()));
            inv.addAll(Arrays.asList(p.getInventory().getArmorContents()));
            quitInventories.put(id, inv.toArray(new ItemStack[0]));
            broadcast("§e" + p.getName() + " §7hat §e" + Settings.time(s().disconnectGrace) + " §7Zeit zum Wiederverbinden.");
        } else {
            Location l = p.getLocation();
            for (ItemStack it : p.getInventory().getContents()) if (it != null) l.getWorld().dropItemNaturally(l, it);
            for (ItemStack it : p.getInventory().getArmorContents()) {
                if (it != null && it.getType() != Material.AIR) l.getWorld().dropItemNaturally(l, it);
            }
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
            eliminate(id, null, l, "hat das Spiel verlassen");
        }
    }

    /** Lobby-Item / Befehl: mitspielen oder zuschauen. */
    public void toggleParticipation(Player p) {
        UUID id = p.getUniqueId();
        if (optedOut.remove(id)) {
            p.sendMessage(s().prefix + "§aDu spielst in der nächsten Runde mit.");
            if (state.isLobby()) toLobby(p);
            return;
        }
        optedOut.add(id);
        plugin.teams().leave(id);
        if (state.isIngame() && alive.contains(id)) {
            eliminate(id, null, p.getLocation(), "hat aufgegeben");
        }
        p.sendMessage(s().prefix + "§7Du schaust zu. §8(/odv join zum Mitspielen)");
        if (state.isLobby()) giveLobbyItems(p);
        else toSpectator(p, null);
    }

    // =====================================================================
    //  Spieler-Hilfen
    // =====================================================================

    public void applyWorldSettings() {
        World w = arena().getWorld();
        Location c = arena().getCenter();
        if (w == null || c == null) return;
        WorldBorder b = w.getWorldBorder();
        b.setCenter(c.getBlockX() + 0.5, c.getBlockZ() + 0.5);
        b.setSize(s().borderInitial);
        b.setDamageAmount(s().borderDamage);
        b.setDamageBuffer(1);
        b.setWarningDistance(10);
        w.setGameRuleValue("naturalRegeneration", String.valueOf(s().naturalRegen));
        w.setPVP(true);
    }

    public void toLobby(Player p) {
        resetPlayer(p, GameMode.ADVENTURE);
        p.teleport(arena().getLobbyOrDefault());
        giveLobbyItems(p);
    }

    public void toSpectator(Player p, Location at) {
        p.closeInventory();
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.setGameMode(GameMode.SPECTATOR);
        if (at == null) {
            Location c = arena().getCenter();
            if (c != null) at = c.clone().add(0, 15, 0);
        }
        if (at != null) p.teleport(at);
        if (!state.isLobby()) {
            p.sendMessage(s().prefix + "§7Du bist §cZuschauer§7. §b/odv spec §7→ Spieler beobachten.");
        }
    }

    public void giveLobbyItems(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        if (!s().isSolo() && !optedOut.contains(p.getUniqueId())) {
            inv.setItem(0, new ItemBuilder(Material.NAME_TAG).name(ITEM_TEAMS).lore("§7Wähle dein Team").build());
        }
        inv.setItem(1, new ItemBuilder(Material.BOOK).name(ITEM_STATS).lore("§7Kills, Tode, Siege").build());
        if (p.hasPermission("odv.admin")) {
            inv.setItem(4, new ItemBuilder(Material.BEACON).name(ITEM_SETUP).lore("§7Alle Einstellungen").build());
            inv.setItem(5, new ItemBuilder(Material.EMERALD).name(ITEM_START).lore("§7Startet in 10 Sekunden").build());
        }
        boolean watching = optedOut.contains(p.getUniqueId());
        inv.setItem(8, new ItemBuilder(Material.INK_SACK, 1, (short) (watching ? 8 : 10)).name(watching ? ITEM_WATCH : ITEM_PLAY).build());
        p.updateInventory();
    }

    @SuppressWarnings("deprecation")
    public void resetPlayer(Player p, GameMode mode) {
        p.setGameMode(mode);
        p.setMaxHealth(20.0);
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setSaturation(10f);
        p.setExhaustion(0f);
        p.setExp(0f);
        p.setLevel(0);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        p.setAllowFlight(false);
        p.setFlying(false);
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.setItemOnCursor(null);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        p.updateInventory();
    }

    private void firework(Location l) {
        Firework fw = l.getWorld().spawn(l, Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.AQUA, Color.LIME, Color.YELLOW).withFade(Color.WHITE).trail(true).flicker(true).build());
        meta.setPower(1);
        fw.setFireworkMeta(meta);
    }

    public void broadcast(String msg) {
        Bukkit.broadcastMessage(s().prefix + msg);
    }

    private void sound(Sound sound, float pitch) {
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), sound, 1f, pitch);
    }

    private void setLevels(int level) {
        for (Player p : Bukkit.getOnlinePlayers()) if (state.isLobby()) p.setLevel(level);
    }

    // =====================================================================
    //  Anzeige: Scoreboard, Tablist, Actionbar
    // =====================================================================

    private void updateDisplays() {
        String time = Settings.time(elapsed);
        World w = arena().getWorld();
        int border = w == null ? s().borderInitial : (int) w.getWorldBorder().getSize();

        // Actionbar mit Border-Abstand
        if (state.isIngame() && w != null) {
            double half = w.getWorldBorder().getSize() / 2.0;
            Location bc = w.getWorldBorder().getCenter();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld() != w) continue;
                double dx = Math.abs(p.getLocation().getX() - bc.getX());
                double dz = Math.abs(p.getLocation().getZ() - bc.getZ());
                int dist = (int) Math.max(0, half - Math.max(dx, dz));
                String col = dist < 15 ? "§c" : dist < 50 ? "§e" : "§a";
                Nms.actionBar(p, "§7Mitte §bX " + bc.getBlockX() + " §8| §bZ " + bc.getBlockZ() + " §8┃ §7Border: " + col + dist + " §7Blöcke");
            }
        }

        // Nametags
        Map<String, String> prefixes = new LinkedHashMap<String, String>();
        Map<String, Set<String>> members = new LinkedHashMap<String, Set<String>>();
        Set<String> specs = new HashSet<String>();
        for (Player p : Bukkit.getOnlinePlayers()) if (p.getGameMode() == GameMode.SPECTATOR) specs.add(p.getName());
        if (!s().isSolo()) {
            for (MTeam t : plugin.teams().getTeams()) {
                Set<String> set = new HashSet<String>();
                for (UUID u : t.getMembers()) {
                    Player p = Bukkit.getPlayer(u);
                    if (p != null && !specs.contains(p.getName())) set.add(p.getName());
                }
                String key = (t.getNumber() < 10 ? "0" : "") + t.getNumber();
                prefixes.put(key, t.getColor().toString());
                members.put(key, set);
            }
        }
        prefixes.put("zspec", "§7§o");
        members.put("zspec", specs);

        List<Map.Entry<UUID, Integer>> top = topKillers(3);
        int online = Bukkit.getOnlinePlayers().size();

        for (Player p : Bukkit.getOnlinePlayers()) {
            Sidebar sb = boards.get(p.getUniqueId());
            if (sb == null) continue;
            if (p.getScoreboard() != sb.getBoard()) p.setScoreboard(sb.getBoard());
            sb.setTitle(s().mode.title);
            MTeam team = plugin.teams().getTeam(p);
            String teamName = team == null ? "§8-" : s().isSolo() ? "§fSolo" : team.getDisplayName();
            List<String> lines = new ArrayList<String>();
            lines.add("§8" + new java.text.SimpleDateFormat("dd.MM.yy").format(new java.util.Date()));
            if (state.isLobby() || state == GameState.ENDING) {
                lines.add("§7Spieler: §a" + candidates().size() + " §8/ §7min. §e" + s().minPlayers);
                if (state == GameState.STARTING) lines.add("§7Start in: §a" + countdown + "s");
                else if (state == GameState.PREPARING) lines.add("§7Status: §eVorbereitung");
                else if (state == GameState.ENDING) lines.add("§7Status: §6Rundenende");
                else if (!arena().isReady()) lines.add("§7Status: §cSetup fehlt");
                else lines.add("§7Status: §eWarten...");
                lines.add(" ");
                lines.add("§7Modus: §f" + s().mode.display + (s().isSolo() ? " Solo" : " " + s().teamSize + "er"));
                if (!s().isSolo()) lines.add("§7Team: " + teamName);
                lines.add("§7Border: §b" + s().borderInitial);
                lines.add("§7Schutz: §e" + Settings.time(s().grace));
                lines.add("  ");
                lines.add("§7Siege: §6" + plugin.stats().get(p.getUniqueId(), "wins") + " §8| §7Kills: §c" + plugin.stats().get(p.getUniqueId(), "kills"));
            } else {
                lines.add("§7Team: " + teamName);
                lines.add("§7Kills: §c" + getKills(p.getUniqueId()) + (s().isSolo() || team == null ? "" : " §8(" + team.getKills() + ")"));
                lines.add("§7Lebende: §a" + alive.size());
                lines.add(" ");
                if (state == GameState.FREEZE) lines.add("§7Start in: §e" + phase + "s");
                else if (state == GameState.PROTECTION) lines.add("§7PvP in: §e" + Settings.time(phase));
                else if (!shrinking) lines.add("§7Border in: §e" + Settings.time(s().shrinkAfter - elapsed));
                else lines.add("§7Border: §cschrumpft");
                lines.add("§7Zeit: §a" + time);
                lines.add("§7Border: §b" + border);
                lines.add("  ");
                for (int i = 0; i < 3; i++) {
                    if (i < top.size()) {
                        lines.add("§c" + top.get(i).getValue() + " §8- §e" + plugin.stats().name(top.get(i).getKey()) + "§" + i);
                    } else {
                        lines.add("§80 §8- -§" + i);
                    }
                }
            }
            lines.add("   ");
            lines.add("§b" + s().serverIp);
            sb.setLines(lines);
            sb.setNametags(prefixes, members);

            String header = "\n" + s().mode.title + "\n§7" + s().serverIp + "\n";
            String footer = state.isIngame()
                    ? "\n§7Lebende: §a" + alive.size() + " §8┃ §7Zeit: §a" + time + " §8┃ §7Border: §b" + border + "\n"
                    : "\n§7Online: §a" + online + " §8┃ §7Status: §e" + state.display + "\n";
            Nms.tab(p, header, footer);
        }
    }

    private List<Map.Entry<UUID, Integer>> topKillers(int n) {
        List<Map.Entry<UUID, Integer>> list = new ArrayList<Map.Entry<UUID, Integer>>(kills.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<UUID, Integer>>() {
            @Override
            public int compare(Map.Entry<UUID, Integer> a, Map.Entry<UUID, Integer> b) {
                return b.getValue() - a.getValue();
            }
        });
        List<Map.Entry<UUID, Integer>> out = new ArrayList<Map.Entry<UUID, Integer>>();
        for (Map.Entry<UUID, Integer> e : list) {
            if (e.getValue() <= 0) continue;
            out.add(e);
            if (out.size() >= n) break;
        }
        return out;
    }

    public SpawnManager spawns() {
        return plugin.spawns();
    }
}
