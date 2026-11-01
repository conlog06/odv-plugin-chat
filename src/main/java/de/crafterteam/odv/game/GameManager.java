package de.crafterteam.odv.game;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.Settings;
import de.crafterteam.odv.data.Arena;
import de.crafterteam.odv.data.Kits;
import de.crafterteam.odv.feature.Feature;
import de.crafterteam.odv.scenario.Scenario;
import de.crafterteam.odv.team.MTeam;
import de.crafterteam.odv.util.Sidebar;
import de.crafterteam.odv.util.Txt;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attribute;
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
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GameManager {

    // IDs der Lobby-Items
    public static final String I_TEAMS = "teams", I_STATS = "stats", I_SETUP = "setup", I_START = "start",
            I_PLAY = "play", I_PRACTICE = "practice", I_SCENARIOS = "scenarios";

    private final OdvPlugin plugin;
    private GameState state = GameState.LOBBY;
    private int countdown, phase, elapsed, shrinkTotal;
    private boolean forced, shrinking, testRound, showdownDone;
    private UUID host;
    private int aliveAtShrink;

    private final Set<UUID> alive = new HashSet<>();
    private final Set<UUID> optedOut = new HashSet<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Map<UUID, Location> deathLocations = new HashMap<>();
    private final Map<UUID, Integer> disconnected = new HashMap<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();
    private final Map<UUID, ItemStack[]> quitInventories = new HashMap<>();
    private final Map<UUID, Sidebar> boards = new HashMap<>();
    private final BossBar bar = BossBar.bossBar(Txt.c(""), 1f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);

    public GameManager(OdvPlugin plugin) {
        this.plugin = plugin;
    }

    private Settings s() { return plugin.settings(); }
    private Arena arena() { return plugin.arena(); }

    public GameState getState() { return state; }
    public boolean isAlive(Player p) { return alive.contains(p.getUniqueId()); }
    public boolean isAlive(UUID id) { return alive.contains(id); }
    public Set<UUID> getAlive() { return Collections.unmodifiableSet(alive); }
    public boolean isOptedOut(Player p) { return optedOut.contains(p.getUniqueId()); }
    public int getKills(UUID u) { return kills.getOrDefault(u, 0); }
    public Location getDeathLocation(UUID id) { return deathLocations.get(id); }
    public int getElapsed() { return elapsed; }
    public boolean isShrinking() { return shrinking; }
    public UUID getHost() { return host; }

    public void setHost(UUID id) {
        host = id;
    }

    /** Ist der Spieler (lebend, nicht Practice) in der laufenden Runde? */
    public boolean isPlaying(Player p) {
        return state.isIngame() && alive.contains(p.getUniqueId());
    }

    public List<Player> candidates() {
        List<Player> list = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) if (p.hasPermission("odv.play") && !optedOut.contains(p.getUniqueId())) list.add(p);
        return list;
    }

    public List<MTeam> aliveTeams() {
        List<MTeam> l = new ArrayList<>();
        for (MTeam t : plugin.teams().getTeams()) for (UUID u : t.getMembers()) if (alive.contains(u)) { l.add(t); break; }
        return l;
    }

    public void startTicker() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                tick();
            } catch (RuntimeException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Fehler im Spieltakt – Runde wird zurückgesetzt.", e);
                resetToLobby();
            }
        }, 20L, 20L);
    }

    // ===================================================================== Takt

    private void tick() {
        switch (state) {
            case LOBBY -> {
                if (s().autoStart && arena().isReady() && candidates().size() >= Math.max(2, s().minPlayers)) beginCountdown(s().countdown, false);
            }
            case STARTING -> {
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
                    sound(Sound.BLOCK_NOTE_BLOCK_PLING, 1f);
                    if (countdown <= 5) for (Player p : Bukkit.getOnlinePlayers()) Txt.title(p, "§e" + countdown, s().mode.title);
                }
                if (countdown <= 0) startGame();
                else countdown--;
            }
            case FREEZE -> {
                if (phase > 0) {
                    for (Player p : Bukkit.getOnlinePlayers()) Txt.title(p, "§e" + phase, "§7Gleich geht's los...");
                    sound(Sound.BLOCK_NOTE_BLOCK_HAT, 1f);
                    phase--;
                } else release();
            }
            case PROTECTION -> {
                elapsed++;
                phase--;
                if (phase == 60 || phase == 30 || phase == 10 || (phase <= 3 && phase > 0)) broadcast("§7PvP in §e" + Txt.time(phase) + "§7.");
                if (phase <= 0) {
                    state = GameState.RUNNING;
                    broadcast("§c§lDie Schutzzeit ist vorbei – PvP ist aktiv!");
                    for (Player p : Bukkit.getOnlinePlayers()) Txt.title(p, "§c§lPvP", "§7ist jetzt aktiv");
                    sound(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f);
                }
                tickRunning();
            }
            case RUNNING -> {
                elapsed++;
                tickRunning();
            }
            default -> {
            }
        }
        plugin.features().tick();
        updateDisplays();
    }

    private void tickRunning() {
        for (UUID id : new ArrayList<>(disconnected.keySet())) {
            int left = disconnected.get(id) - 1;
            if (left > 0) {
                disconnected.put(id, left);
                continue;
            }
            disconnected.remove(id);
            Location l = returnLocations.remove(id);
            ItemStack[] inv = quitInventories.remove(id);
            if (l != null && inv != null) for (ItemStack it : inv) if (it != null && !it.getType().isAir()) l.getWorld().dropItemNaturally(l, it);
            eliminate(id, null, l, "ist nach dem Disconnect ausgeschieden");
        }
        if (!shrinking) {
            int left = s().shrinkAfter - elapsed;
            if (left == 60) broadcast("§7Die Border schrumpft in §e1 Minute§7.");
            if (left <= 0) startShrink(s().shrinkDuration);
        }
        if (s().has(Feature.SHOWDOWN) && !showdownDone && state == GameState.RUNNING && elapsed >= s().timeLimit * 60) showdown();
    }

    private void startShrink(int seconds) {
        shrinking = true;
        shrinkTotal = seconds;
        aliveAtShrink = Math.max(1, alive.size());
        World w = arena().getWorld();
        if (w != null) w.getWorldBorder().setSize(s().borderFinal, seconds);
        broadcast("§c§lDie Border schrumpft! §7Auf §b" + s().borderFinal + " §7Blöcke in §e" + Txt.time(seconds) + "§7.");
        sound(Sound.ENTITY_WITHER_SPAWN, 0.6f);
    }

    /** Feature „Adaptive Border": weniger Spieler -> Border schneller. */
    private void adaptBorder() {
        if (!shrinking || !s().has(Feature.ADAPTIVE_BORDER)) return;
        World w = arena().getWorld();
        if (w == null) return;
        WorldBorder b = w.getWorldBorder();
        double size = b.getSize();
        if (size <= s().borderFinal + 1) return;
        double ratio = Math.max(0.3, alive.size() / (double) aliveAtShrink);
        long remaining = (long) Math.max(45, (size - s().borderFinal) / Math.max(1, s().borderInitial - s().borderFinal) * shrinkTotal * ratio);
        b.setSize(s().borderFinal, remaining);
    }

    /** Feature „Showdown": Zeitlimit erreicht -> alle in die Mitte. */
    private void showdown() {
        showdownDone = true;
        World w = arena().getWorld();
        Location c = arena().getCenter();
        if (w == null || c == null) return;
        broadcast("§4§l☠ SHOWDOWN ☠ §7Zeitlimit erreicht – alle in die Mitte!");
        List<UUID> ids = new ArrayList<>(alive);
        int n = ids.size(), r = Math.max(12, s().borderFinal / 2 - 6);
        for (int i = 0; i < n; i++) {
            Player p = Bukkit.getPlayer(ids.get(i));
            if (p == null) continue;
            double a = 2 * Math.PI * i / Math.max(1, n);
            int x = c.getBlockX() + (int) (Math.cos(a) * r), z = c.getBlockZ() + (int) (Math.sin(a) * r);
            Location l = new Location(w, x + 0.5, de.crafterteam.odv.world.SpawnManager.ground(w, x, z) + 1, z + 0.5);
            l.setYaw(de.crafterteam.odv.world.SpawnManager.yawTo(l, c));
            p.teleport(l);
            p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 60 * 60, 0, false, false));
            Txt.title(p, "§4§lSHOWDOWN", "§7Nur einer kommt hier raus");
        }
        w.getWorldBorder().setSize(Math.max(s().borderFinal, 2 * r + 20));
        shrinking = true;
        sound(Sound.ENTITY_WITHER_DEATH, 0.7f);
    }

    private void beginCountdown(int seconds, boolean force) {
        state = GameState.STARTING;
        countdown = seconds;
        forced = force;
        broadcast("§aGenug Spieler! §7Start in §e" + seconds + " §7Sekunden.");
    }

    public String forceStart(int seconds) {
        if (!arena().isReady()) return "§cDie Arena ist noch nicht eingerichtet. §7(/odv → Schnellsetup)";
        if (plugin.spawns().isBusy()) return "§cEs wird gerade gebaut – kurz warten.";
        if (candidates().isEmpty()) return "§cKeine Teilnehmer online.";
        if (!plugin.teams().hasCapacity(candidates().size())) return "§cNicht genug Teamplätze – Teamanzahl oder -größe erhöhen.";
        if (state == GameState.LOBBY) {
            beginCountdown(seconds, true);
            return null;
        }
        if (state == GameState.STARTING) {
            forced = true;
            countdown = Math.min(countdown, seconds);
            return null;
        }
        return "§cEs läuft bereits eine Runde.";
    }

    // ===================================================================== Start

    private void startGame() {
        List<Player> players = candidates();
        if (players.isEmpty() || !arena().isReady()) {
            state = GameState.LOBBY;
            return;
        }
        if (s().backupOnStart) plugin.stats().backup(null);
        plugin.practice().leaveAll();
        state = GameState.PREPARING;
        forced = false;
        plugin.teams().finalizeTeams(players);
        testRound = plugin.teams().getTeams().size() < 2;
        applyWorldSettings();
        int needed = plugin.teams().getTeams().size();
        boolean regenerate = s().regenerateOnStart || arena().getSpawns().size() < needed;
        if (regenerate) {
            broadcast("§7Spawns werden vorbereitet...");
            for (Player p : players) Txt.title(p, s().mode.title, "§7Spawns werden gebaut...");
            if (!plugin.spawns().generate(needed, null, this::teleportAndFreeze)) teleportAndFreeze();
        } else teleportAndFreeze();
    }

    private void teleportAndFreeze() {
        if (state != GameState.PREPARING) return;
        World world = arena().getWorld();
        List<MTeam> teams = plugin.teams().getTeams();
        List<Location> spawns = new ArrayList<>(arena().getSpawns());
        if (spawns.size() < teams.size()) {
            for (Location l : plugin.spawns().ring(world, arena().getCenter(), teams.size() - spawns.size(), plugin.spawns().autoRadius(teams.size()) + 15)) spawns.add(l);
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
        showdownDone = false;
        plugin.holograms().remove("spawns");
        Kits.Type kit = s().mode == Settings.Mode.ODV ? Kits.Type.ODV : Kits.Type.MEETUP;

        for (int i = 0; i < teams.size(); i++) {
            MTeam team = teams.get(i);
            List<Player> members = new ArrayList<>();
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
                if (m > 0 && s().spawnStyle == Settings.SpawnStyle.HOLES && !spot.equals(base)) plugin.spawns().placeHole(spot);
                spot.setYaw(base.getYaw());
                p.teleport(spot);
                plugin.kits().give(p, kit);
                alive.add(p.getUniqueId());
                kills.put(p.getUniqueId(), 0);
                plugin.stats().add(p.getUniqueId(), "games");
            }
            if (s().nameTags && !members.isEmpty()) {
                String label = s().isSolo() ? "§f" + members.get(0).getName() : team.getDisplayName();
                plugin.holograms().spawn("spawns", base.clone().add(0, 2.6, 0), label, 1.2f, null);
            }
        }
        if (alive.isEmpty()) {
            broadcast("§cStart abgebrochen – keine Teilnehmer.");
            resetToLobby();
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) if (!alive.contains(p.getUniqueId())) toSpectator(p, null);
        plugin.scenarios().onRoundStart();
        plugin.features().onRoundStart();
        plugin.antiXray().reset();
        broadcast("§a" + alive.size() + " Spieler §7in §a" + teams.size() + (s().isSolo() ? "" : " Teams") + " §7– viel Glück!"
                + (testRound ? " §8(Testrunde – endet mit /odv stop)" : ""));
        if (!s().scenarios.isEmpty()) broadcast("§7Szenarien: §e" + plugin.scenarios().activeList());
        state = GameState.FREEZE;
        phase = s().freeze;
        if (phase <= 0) release();
    }

    private void release() {
        plugin.holograms().remove("spawns");
        for (Player p : Bukkit.getOnlinePlayers()) Txt.title(p, "§a§lLos!", s().grace > 0 ? "§7Schutzzeit: §e" + Txt.time(s().grace) : "§cPvP ist aktiv");
        sound(Sound.ENTITY_PLAYER_LEVELUP, 1f);
        if (s().grace > 0) {
            state = GameState.PROTECTION;
            phase = s().grace;
            broadcast("§7Schutzzeit: §e" + Txt.time(phase) + " §7– kein PvP.");
        } else {
            state = GameState.RUNNING;
            broadcast("§c§lPvP ist aktiv!");
        }
    }

    // ===================================================================== Ausscheiden / Sieg

    public void eliminate(UUID id, Player killer, Location where, String reason) {
        if (!alive.remove(id)) return;
        disconnected.remove(id);
        returnLocations.remove(id);
        quitInventories.remove(id);
        if (where != null) deathLocations.put(id, where.clone());
        MTeam team = plugin.teams().getTeam(id);
        String name = color(team, "§c") + plugin.stats().name(id);
        plugin.stats().add(id, "deaths");

        if (killer != null && !killer.getUniqueId().equals(id)) {
            kills.merge(killer.getUniqueId(), 1, Integer::sum);
            MTeam kt = plugin.teams().getTeam(killer);
            if (kt != null) kt.addKill();
            if (!plugin.teams().sameTeam(id, killer.getUniqueId())) plugin.stats().add(killer.getUniqueId(), "kills");
            String hp = String.format(java.util.Locale.ROOT, "%.1f", killer.getHealth() / 2.0);
            broadcast(name + " §7wurde von " + color(kt, "§a") + killer.getName() + " §8(§c❤ " + hp + "§8) §7getötet. §8[" + alive.size() + " übrig]");
            killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        } else {
            broadcast(name + " §7" + reason + ". §8[" + alive.size() + " übrig]");
        }
        if (where != null && where.getWorld() != null) {
            if (s().lightning) where.getWorld().strikeLightningEffect(where);
            if (s().goldenApple) where.getWorld().dropItemNaturally(where, new ItemStack(Material.GOLDEN_APPLE));
        }
        plugin.features().onEliminate(id, killer, where);
        if (team != null && !s().isSolo()) {
            boolean any = false;
            for (UUID u : team.getMembers()) if (alive.contains(u)) any = true;
            if (!any) {
                broadcast("§7Team " + team.getDisplayName() + " §7wurde §cvernichtet§7!");
                plugin.scenarios().dropBackpack(team, where);
            }
        }
        adaptBorder();
        Bukkit.getScheduler().runTaskLater(plugin, this::checkWin, 2L);
    }

    /** Feature „Team-Revive": toten Spieler zurückholen. */
    public boolean revive(UUID id, Location at) {
        Player p = Bukkit.getPlayer(id);
        if (p == null || !state.isIngame() || alive.contains(id)) return false;
        alive.add(id);
        resetPlayer(p, GameMode.SURVIVAL);
        p.teleport(at);
        plugin.kits().give(p, Kits.Type.ODV);
        p.setHealth(10.0);
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 10, 1));
        Txt.title(p, "§a§lWIEDERBELEBT", "§7Dein Team hat dich zurückgeholt");
        return true;
    }

    private static String color(MTeam t, String def) {
        return t != null && t.getColor() != org.bukkit.ChatColor.WHITE ? t.getColor().toString() : def;
    }

    private void checkWin() {
        if (!state.isIngame()) return;
        List<MTeam> left = aliveTeams();
        if (left.isEmpty() || (!testRound && left.size() == 1)) endGame(left.isEmpty() ? null : left.get(0));
    }

    private void endGame(MTeam winner) {
        state = GameState.ENDING;
        String title = winner == null ? "§7Unentschieden" : s().isSolo() ? "§6" + winner.getName() + " §ahat gewonnen!"
                : "§7Team " + winner.getDisplayName() + " §ahat gewonnen!";
        if (winner != null) for (UUID u : winner.getMembers()) plugin.stats().add(u, "wins");
        broadcast("§8§m                                        ");
        broadcast(title);
        List<Map.Entry<UUID, Integer>> top = topKillers(3);
        for (int i = 0; i < top.size(); i++) broadcast("§e#" + (i + 1) + " §f" + plugin.stats().name(top.get(i).getKey()) + " §8- §c" + top.get(i).getValue() + " Kills");
        broadcast("§8§m                                        ");
        for (Player p : Bukkit.getOnlinePlayers()) {
            Txt.title(p, title, "§7GG!");
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        plugin.features().onRoundEnd(winner);
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
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state != GameState.ENDING) return;
            if (s().stopOnEnd) {
                plugin.stats().flush(true);
                for (Player p : Bukkit.getOnlinePlayers()) p.kick(Txt.c("§bDie Runde ist vorbei!\n§7Der Server startet neu – bis gleich."));
                Bukkit.shutdown();
            } else {
                resetToLobby();
                broadcast("§bNeue Lobby bereit.");
            }
        }, s().endDelay * 20L);
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
        forced = testRound = shrinking = showdownDone = false;
        alive.clear();
        kills.clear();
        deathLocations.clear();
        disconnected.clear();
        returnLocations.clear();
        quitInventories.clear();
        elapsed = 0;
        plugin.teams().reset();
        plugin.scenarios().reset();
        plugin.features().reset();
        plugin.holograms().remove("spawns");
        applyWorldSettings();
        World w = arena().getWorld();
        if (w != null) {
            for (Entity e : w.getEntities()) {
                if (e instanceof Item || e instanceof Projectile || e instanceof TNTPrimed || e instanceof ExperienceOrb || e instanceof FallingBlock) e.remove();
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) toLobby(p);
    }

    // ===================================================================== Join / Quit / Teilnahme

    public void onJoin(Player p) {
        plugin.stats().load(p);
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
                Txt.send(p, s().prefix + "§eDie Arena ist noch nicht eingerichtet. §7Öffne §b/odv §7→ §aSchnellsetup§7.");
            }
        } else {
            alive.remove(id);
            resetPlayer(p, GameMode.SPECTATOR);
            toSpectator(p, null);
        }
    }

    public void onQuit(Player p) {
        boards.remove(p.getUniqueId());
        plugin.practice().leave(p, false);
        UUID id = p.getUniqueId();
        if (state.isLobby()) {
            plugin.teams().leave(id);
        } else if (state.isIngame() && alive.contains(id)) {
            if (s().disconnectGrace > 0) {
                disconnected.put(id, s().disconnectGrace);
                returnLocations.put(id, p.getLocation().clone());
                List<ItemStack> inv = new ArrayList<>(List.of(p.getInventory().getContents()));
                quitInventories.put(id, inv.toArray(new ItemStack[0]));
                broadcast("§e" + p.getName() + " §7hat §e" + Txt.time(s().disconnectGrace) + " §7Zeit zum Wiederverbinden.");
            } else {
                Location l = p.getLocation();
                for (ItemStack it : p.getInventory().getContents()) if (it != null && !it.getType().isAir()) l.getWorld().dropItemNaturally(l, it);
                p.getInventory().clear();
                eliminate(id, null, l, "hat das Spiel verlassen");
            }
        }
        plugin.stats().unload(id);
    }

    public void toggleParticipation(Player p) {
        UUID id = p.getUniqueId();
        if (optedOut.remove(id)) {
            Txt.send(p, s().prefix + "§aDu spielst in der nächsten Runde mit.");
            if (state.isLobby()) giveLobbyItems(p);
            return;
        }
        optedOut.add(id);
        plugin.teams().leave(id);
        if (state.isIngame() && alive.contains(id)) eliminate(id, null, p.getLocation(), "hat aufgegeben");
        Txt.send(p, s().prefix + "§7Du schaust zu. §8(/odv join zum Mitspielen)");
        if (state.isLobby()) giveLobbyItems(p);
        else toSpectator(p, null);
    }

    // ===================================================================== Spieler-Hilfen

    public void applyWorldSettings() {
        World w = arena().getWorld();
        Location c = arena().getCenter();
        if (w == null || c == null) return;
        WorldBorder b = w.getWorldBorder();
        b.setCenter(c.getBlockX() + 0.5, c.getBlockZ() + 0.5);
        b.setSize(s().borderInitial);
        b.setDamageAmount(s().borderDamage);
        b.setDamageBuffer(1);
        b.setWarningDistance(12);
        w.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, s().naturalRegen);
        w.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        w.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        w.setPVP(true);
        plugin.combat().prepareAll();
    }

    public void toLobby(Player p) {
        resetPlayer(p, GameMode.ADVENTURE);
        p.teleport(arena().getLobbyOrDefault());
        giveLobbyItems(p);
    }

    public void toSpectator(Player p, Location at) {
        p.closeInventory();
        p.getInventory().clear();
        p.setGameMode(GameMode.SPECTATOR);
        if (at == null) {
            Location c = arena().getCenter();
            if (c != null) at = c.clone().add(0, 20, 0);
        }
        if (at != null) p.teleport(at);
        if (!state.isLobby()) {
            Txt.send(p, s().prefix + "§7Du bist §cZuschauer§7. §b/odv spec §7beobachten"
                    + (s().has(Feature.BETTING) ? " §8| §b/odv bet §7wetten" : ""));
        }
    }

    public void giveLobbyItems(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        var it = plugin.items();
        boolean watching = optedOut.contains(p.getUniqueId());
        if (!s().isSolo() && !watching) inv.setItem(0, it.menu(Material.RED_BED, I_TEAMS, "§b§lTeams §8(Rechtsklick)", "§7Wähle dein Team"));
        if (s().has(Feature.PRACTICE) && arena().getPractice() != null) {
            inv.setItem(2, it.menu(Material.DIAMOND_SWORD, I_PRACTICE, "§c§lPractice §8(Rechtsklick)", "§7Kämpfen bis die Runde startet"));
        }
        inv.setItem(3, it.menu(Material.PAPER, I_SCENARIOS, "§e§lSzenarien §8(Rechtsklick)", "§7Was ist diese Runde aktiv?"));
        if (p.hasPermission("odv.admin")) {
            inv.setItem(4, it.menu(Material.BEACON, I_SETUP, "§c§lSetup §8(Rechtsklick)", "§7Alle Einstellungen"));
            inv.setItem(5, it.menu(Material.EMERALD, I_START, "§a§lRunde starten §8(Rechtsklick)", "§7Startet in 10 Sekunden"));
        }
        inv.setItem(7, it.menu(Material.BOOK, I_STATS, "§e§lStatistik §8(Rechtsklick)", "§7Siege, Kills, K/D"));
        inv.setItem(8, it.menu(watching ? Material.GRAY_DYE : Material.LIME_DYE, I_PLAY,
                watching ? "§7§lDu schaust zu §8(Klick: mitspielen)" : "§a§lDu spielst mit §8(Klick: zuschauen)"));
    }

    /** Lobby-Items neu verteilen (z. B. wenn Practice an/aus geht). */
    public void resetToLobbyItems() {
        if (!state.isLobby()) return;
        for (Player p : Bukkit.getOnlinePlayers()) if (!plugin.practice().isIn(p)) giveLobbyItems(p);
    }

    public void resetPlayer(Player p, GameMode mode) {
        p.setGameMode(mode);
        var maxHp = p.getAttribute(Attribute.MAX_HEALTH);
        if (maxHp != null) maxHp.setBaseValue(20.0);
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
        p.setGlowing(false);
        p.getInventory().clear();
        p.setItemOnCursor(null);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
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
        Bukkit.broadcast(Txt.c(s().prefix + msg));
    }

    public void sound(Sound sound, float pitch) {
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), sound, 1f, pitch);
    }

    private void setLevels(int level) {
        if (state.isLobby()) for (Player p : Bukkit.getOnlinePlayers()) if (!plugin.practice().isIn(p)) p.setLevel(level);
    }

    // ===================================================================== Hologramme

    /** Schriftzüge am Mittel-Spawn und Bestenliste in der Lobby. */
    public void refreshHolograms() {
        plugin.holograms().remove("center");
        plugin.holograms().remove("lobby");
        Location c = arena().getCenter(), l = arena().getLobby();
        // Chunks von Spawn, Lobby und Practice geladen halten (Hologramme bleiben sichtbar)
        for (Location k : new Location[]{c, l, arena().getPractice()}) {
            if (k != null) k.getWorld().addPluginChunkTicket(k.getBlockX() >> 4, k.getBlockZ() >> 4, plugin);
        }
        if (c != null) {
            plugin.holograms().spawn("center", c.clone().add(0, 3.2, 0),
                    "§b§lSpawn §8(§7" + c.getBlockX() + "§8/§7" + c.getBlockZ() + "§8)\n§7Netherportal " + (s().nether ? "§aoffen" : "§cgesperrt"), 1.4f, null);
        }
        if (l != null) {
            plugin.holograms().spawn("lobby", l.clone().add(0, 3.5, -6), s().mode.title + "\n§7" + s().serverIp, 2.2f, null);
            plugin.stats().top("wins", 5, list -> {
                StringBuilder sb = new StringBuilder("§6§lTop 5 – Siege");
                int i = 1;
                for (Map.Entry<String, Integer> e : list) sb.append("\n§e#").append(i++).append(" §f").append(e.getKey()).append(" §8- §6").append(e.getValue());
                if (list.isEmpty()) sb.append("\n§7Noch keine Sieger");
                plugin.holograms().spawn("lobby", l.clone().add(6, 2.2, 0), sb.toString(), 1f, null);
            });
        }
    }

    // ===================================================================== Anzeige

    private void updateDisplays() {
        World w = arena().getWorld();
        int border = w == null ? s().borderInitial : (int) w.getWorldBorder().getSize();
        String time = Txt.time(elapsed);

        // Bossbar
        if (state == GameState.STARTING) bar(("§7Start in §a" + countdown + "s"), countdown / (float) Math.max(1, s().countdown), BossBar.Color.GREEN);
        else if (state == GameState.FREEZE) bar("§eGleich geht's los...", 1f, BossBar.Color.YELLOW);
        else if (state == GameState.PROTECTION) bar("§7PvP in §e" + Txt.time(phase), phase / (float) Math.max(1, s().grace), BossBar.Color.YELLOW);
        else if (state == GameState.RUNNING && !shrinking) bar("§7Border schrumpft in §e" + Txt.time(s().shrinkAfter - elapsed),
                (s().shrinkAfter - elapsed) / (float) Math.max(1, s().shrinkAfter - s().grace), BossBar.Color.BLUE);
        else if (state == GameState.RUNNING) bar("§cBorder: §f" + border + " §8┃ §7Lebende: §a" + alive.size()
                + (s().has(Feature.SHOWDOWN) && !showdownDone ? " §8┃ §4Showdown in §f" + Txt.time(s().timeLimit * 60 - elapsed) : ""),
                (float) Math.min(1, (border - s().borderFinal) / (double) Math.max(1, s().borderInitial - s().borderFinal)), BossBar.Color.RED);
        boolean barVisible = state == GameState.STARTING || state.isIngame();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (barVisible) p.showBossBar(bar);
            else p.hideBossBar(bar);
        }

        // Actionbar
        if (state.isIngame() && w != null) {
            double half = w.getWorldBorder().getSize() / 2.0;
            Location bc = w.getWorldBorder().getCenter();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld() != w) continue;
                int dist = (int) Math.max(0, half - Math.max(Math.abs(p.getX() - bc.getX()), Math.abs(p.getZ() - bc.getZ())));
                String col = dist < 15 ? "§c" : dist < 50 ? "§e" : "§a";
                Txt.actionBar(p, "§7Der §bSpawn §7ist bei §bX: " + bc.getBlockX() + " §7und §bZ: " + bc.getBlockZ()
                        + " §8┃ §7Distanz zur §bBorder§7: " + col + dist + " §7Blöcke");
            }
        } else if (state.isLobby()) {
            String sc = s().scenarios.isEmpty() ? "§7keine" : "§e/szenarios";
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (plugin.practice().isIn(p)) continue;
                Txt.actionBar(p, "§8| §7Aktuell in der §aLobbyphase §8| §7Practice: "
                        + (s().has(Feature.PRACTICE) && arena().getPractice() != null ? "§aaktiviert" : "§caus") + " §8| §7Szenarios: " + sc + " §8|");
            }
        }

        // Nametags
        Map<String, NamedTextColor> colors = new LinkedHashMap<>();
        Map<String, String> prefixes = new LinkedHashMap<>();
        Map<String, Set<String>> members = new LinkedHashMap<>();
        Set<String> specs = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) if (p.getGameMode() == GameMode.SPECTATOR) specs.add(p.getName());
        UUID bounty = plugin.features().bountyTarget();
        if (!s().isSolo()) {
            for (MTeam t : plugin.teams().getTeams()) {
                Set<String> set = new HashSet<>();
                for (UUID u : t.getMembers()) {
                    Player p = Bukkit.getPlayer(u);
                    if (p != null && !specs.contains(p.getName()) && !u.equals(bounty)) set.add(p.getName());
                }
                String key = String.format("%02d", t.getNumber());
                colors.put(key, t.textColor());
                prefixes.put(key, t.getColor() + "[" + t.getNumber() + "] ");
                members.put(key, set);
            }
        }
        if (bounty != null) {
            Player bp = Bukkit.getPlayer(bounty);
            colors.put("zbounty", NamedTextColor.GOLD);
            prefixes.put("zbounty", "§6☠ ");
            members.put("zbounty", bp == null ? Set.of() : Set.of(bp.getName()));
        }
        Set<String> admins = new HashSet<>();
        if (state.isLobby()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("odv.admin") && plugin.teams().getTeam(p) == null && !specs.contains(p.getName())) admins.add(p.getName());
            }
            colors.put("yadmin", NamedTextColor.RED);
            prefixes.put("yadmin", "§c[A] ");
            members.put("yadmin", admins);
        }
        colors.put("zspec", NamedTextColor.GRAY);
        prefixes.put("zspec", "§7");
        members.put("zspec", specs);

        List<Map.Entry<UUID, Integer>> top = topKillers(3);
        String date = new SimpleDateFormat("dd.MM.yy").format(new Date());
        int online = Bukkit.getOnlinePlayers().size();
        String hostName = host == null ? "-" : plugin.stats().name(host);

        for (Player p : Bukkit.getOnlinePlayers()) {
            Sidebar sb = boards.get(p.getUniqueId());
            if (sb == null) continue;
            if (p.getScoreboard() != sb.getBoard()) p.setScoreboard(sb.getBoard());
            sb.setTitle(s().mode.title);
            MTeam team = plugin.teams().getTeam(p);
            String teamName = team == null ? "§8-" : s().isSolo() ? "§fSolo" : team.getDisplayName();
            List<String> lines = new ArrayList<>();
            lines.add("§8" + date);
            if (plugin.practice().isIn(p)) {
                UUID id = p.getUniqueId();
                int k = plugin.stats().get(id, "p_kills"), d = plugin.stats().get(id, "p_deaths");
                lines.add("§c§lPractice");
                lines.add("§7Kills: §a" + k);
                lines.add("§7Tode: §c" + d);
                lines.add("§7K/D: §e" + (d == 0 ? k : String.format(java.util.Locale.ROOT, "%.2f", k / (double) d)));
                lines.add(" ");
                lines.add("§7/practice §8→ §7zurück");
            } else if (state.isLobby() || state == GameState.ENDING) {
                lines.add("§7Host:");
                lines.add(" §8» §b" + hostName);
                lines.add("§7Team:");
                lines.add(" §8» " + teamName);
                lines.add("§7Spieler:");
                lines.add(" §8» §a" + candidates().size() + "§7/" + s().maxPlayers
                        + (state == GameState.STARTING ? " §8| §e" + countdown + "s" : ""));
                lines.add("§7Modus:");
                lines.add(" §8» §f" + s().mode.display + (s().isSolo() ? " Solo" : " " + s().teamSize + "er") + (s().oldPvp ? " §8(1.8-PvP)" : ""));
            } else {
                lines.add("§7Team: " + teamName);
                lines.add("§7Kills: §c" + getKills(p.getUniqueId()) + (s().isSolo() || team == null ? "" : " §8(" + team.getKills() + ")"));
                lines.add("§7Lebende: §a" + alive.size() + " §8(" + aliveTeams().size() + (s().isSolo() ? ")" : " Teams)"));
                lines.add(" ");
                lines.add("§7Zeit: §a" + time);
                lines.add("§7Border: §b" + border);
                lines.add("  ");
                for (int i = 0; i < 3; i++) {
                    if (i < top.size()) lines.add("§c" + top.get(i).getValue() + " §8- §e" + plugin.stats().name(top.get(i).getKey()));
                    else lines.add("§80 §8- §8Unknown");
                }
            }
            lines.add("   ");
            lines.add("§7Server:");
            lines.add(" §8» §b" + s().serverIp);
            sb.setLines(lines);
            sb.setNametags(colors, prefixes, members);

            String footer = state.isIngame()
                    ? "\n§7Lebende: §a" + alive.size() + " §8┃ §7Zeit: §a" + time + " §8┃ §7Border: §b" + border + "\n"
                    : "\n§7Online: §a" + online + " §8┃ §7Phase: §e" + state.display + "\n";
            p.sendPlayerListHeaderAndFooter(Txt.c("\n" + s().mode.title + " §8✦ §7" + s().serverIp + "\n"), Txt.c(footer));
        }
    }

    private void bar(String text, float progress, BossBar.Color color) {
        bar.name(Txt.c(text));
        bar.progress(Math.max(0f, Math.min(1f, progress)));
        bar.color(color);
    }

    public List<Map.Entry<UUID, Integer>> topKillers(int n) {
        List<Map.Entry<UUID, Integer>> list = new ArrayList<>(kills.entrySet());
        list.removeIf(e -> e.getValue() <= 0);
        list.sort((a, b) -> b.getValue() - a.getValue());
        return list.subList(0, Math.min(n, list.size()));
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
    }
}
