package de.crafterteam.meetup.game;

import de.crafterteam.meetup.MeetupPlugin;
import de.crafterteam.meetup.Settings;
import de.crafterteam.meetup.scoreboard.Sidebar;
import de.crafterteam.meetup.spawn.SpawnManager;
import de.crafterteam.meetup.team.MTeam;
import de.crafterteam.meetup.util.ItemBuilder;
import de.crafterteam.meetup.util.Nms;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
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

    public static final String ITEM_TEAMS = "§b§lTeamauswahl §7(Rechtsklick)";
    public static final String ITEM_SETUP = "§c§lSetup §7(Rechtsklick)";
    public static final String ITEM_START = "§a§lStart erzwingen §7(Rechtsklick)";

    private final MeetupPlugin plugin;
    private GameState state = GameState.LOBBY;
    private int countdown;
    private int phaseTimer;
    private int gameSeconds;
    private boolean forced;
    private boolean shrinkStarted;

    private final Set<UUID> alive = new HashSet<UUID>();
    private final Map<UUID, Integer> kills = new HashMap<UUID, Integer>();
    private final Map<UUID, String> names = new HashMap<UUID, String>();
    private final Map<UUID, Location> deathLocations = new HashMap<UUID, Location>();
    private final Map<UUID, Sidebar> boards = new HashMap<UUID, Sidebar>();

    public GameManager(MeetupPlugin plugin) {
        this.plugin = plugin;
    }

    private Settings s() {
        return plugin.settings();
    }

    public GameState getState() { return state; }
    public boolean isAlive(Player p) { return alive.contains(p.getUniqueId()); }
    public int getAliveCount() { return alive.size(); }
    public int getKills(UUID u) { Integer k = kills.get(u); return k == null ? 0 : k; }
    public int getGameSeconds() { return gameSeconds; }

    public void startTicker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    // =====================================================================
    //  Tick
    // =====================================================================

    private void tick() {
        int online = Bukkit.getOnlinePlayers().size();
        switch (state) {
            case LOBBY:
                if (online >= s().minPlayers && online > 0) beginCountdown(s().lobbyCountdown, false);
                break;
            case STARTING:
                if (!forced && online < s().minPlayers) {
                    state = GameState.LOBBY;
                    broadcast("§cZu wenige Spieler – Countdown abgebrochen.");
                    setLevels(0);
                    break;
                }
                if (online == 0) {
                    state = GameState.LOBBY;
                    forced = false;
                    break;
                }
                setLevels(countdown);
                if (countdown == 60 || countdown == 30 || countdown == 20 || countdown == 10 || (countdown <= 5 && countdown > 0)) {
                    broadcast("§7Die Runde startet in §e" + countdown + " §7Sekunde" + (countdown == 1 ? "" : "n") + ".");
                    playSound(Sound.NOTE_PLING, 1f);
                }
                if (countdown <= 0) {
                    startGame();
                } else {
                    countdown--;
                }
                break;
            case FREEZE:
                if (phaseTimer > 0) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        Nms.title(p, "§e" + phaseTimer, "§7Gleich geht's los...");
                    }
                    playSound(Sound.NOTE_STICKS, 1f);
                    phaseTimer--;
                } else {
                    for (Player p : Bukkit.getOnlinePlayers()) Nms.title(p, "§a§lLos!", "§7Viel Glück");
                    playSound(Sound.LEVEL_UP, 1f);
                    if (s().pvpProtection > 0) {
                        state = GameState.PROTECTION;
                        phaseTimer = s().pvpProtection;
                        broadcast("§7Schutzzeit: §e" + phaseTimer + " Sekunden §7– kein PvP.");
                    } else {
                        state = GameState.RUNNING;
                        broadcast("§c§lPvP ist aktiv!");
                    }
                }
                break;
            case PROTECTION:
                gameSeconds++;
                phaseTimer--;
                if (phaseTimer == 10 || (phaseTimer <= 3 && phaseTimer > 0)) {
                    broadcast("§7Die Schutzzeit endet in §e" + phaseTimer + " §7Sekunde" + (phaseTimer == 1 ? "" : "n") + ".");
                }
                if (phaseTimer <= 0) {
                    state = GameState.RUNNING;
                    broadcast("§c§lDie Schutzzeit ist vorbei – PvP ist aktiv!");
                    playSound(Sound.ENDERDRAGON_GROWL, 0.6f);
                }
                checkBorder();
                break;
            case RUNNING:
                gameSeconds++;
                checkBorder();
                break;
            default:
                break;
        }
        if (state.isIngame() || state == GameState.ENDING) sendActionBars();
        updateScoreboards();
    }

    private void beginCountdown(int seconds, boolean force) {
        state = GameState.STARTING;
        countdown = seconds;
        forced = force;
    }

    /** /meetup start – Countdown erzwingen/verkürzen. */
    public boolean forceStart(int seconds) {
        if (state == GameState.LOBBY) {
            beginCountdown(seconds, true);
            return true;
        }
        if (state == GameState.STARTING) {
            forced = true;
            if (countdown > seconds) countdown = seconds;
            return true;
        }
        return false;
    }

    private void checkBorder() {
        if (shrinkStarted) return;
        int warn = s().shrinkStart * 60 - 60;
        if (warn > 0 && gameSeconds == warn) {
            broadcast("§7Die Border beginnt in §e1 Minute §7zu schrumpfen.");
        }
        if (gameSeconds >= s().shrinkStart * 60) {
            shrinkStarted = true;
            World w = s().getGameWorld();
            if (w == null) return;
            long seconds = Math.max(1, s().shrinkDuration * 60L);
            w.getWorldBorder().setSize(Math.max(1, s().borderEnd), seconds);
            broadcast("§c§lDie Border schrumpft! §7Auf §b" + s().borderEnd + " §7Blöcke in §e" + s().shrinkDuration + " Minuten§7.");
            playSound(Sound.WITHER_SPAWN, 0.5f);
        }
    }

    // =====================================================================
    //  Start
    // =====================================================================

    public void startGame() {
        List<Player> players = new ArrayList<Player>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            state = GameState.LOBBY;
            return;
        }
        final World world = s().getGameWorld();
        if (world == null) {
            broadcast("§cSpielwelt '" + s().worldName + "' nicht gefunden – Start abgebrochen. §7(/meetup setup)");
            state = GameState.LOBBY;
            forced = false;
            return;
        }
        state = GameState.PREPARING;
        forced = false;
        plugin.teams().finalizeTeams(players);

        world.setGameRuleValue("naturalRegeneration", String.valueOf(s().naturalRegen));
        world.setStorm(false);
        world.setThundering(false);
        world.setTime(1000);
        WorldBorder wb = world.getWorldBorder();
        wb.setCenter(s().centerX + 0.5, s().centerZ + 0.5);
        wb.setSize(Math.max(10, s().borderStart));
        wb.setDamageAmount(s().borderDamage);
        wb.setDamageBuffer(s().borderBuffer);
        wb.setWarningDistance(10);

        final int needed = plugin.teams().getTeams().size();
        SpawnManager sm = plugin.spawns();
        boolean regenerate = s().generateOnStart || sm.getSpawns().size() < needed;
        if (regenerate) {
            int count = s().spawnCount > 0 ? Math.max(s().spawnCount, needed) : needed;
            broadcast("§7Spawns werden vorbereitet...");
            for (Player p : players) Nms.title(p, "§b§lMeetup", "§7Spawns werden generiert...");
            boolean started = sm.generate(count, s().spawnRadius, null, new Runnable() {
                @Override
                public void run() {
                    teleportAndFreeze(world);
                }
            });
            if (!started) teleportAndFreeze(world);
        } else {
            teleportAndFreeze(world);
        }
    }

    private void teleportAndFreeze(World world) {
        if (state != GameState.PREPARING) return;
        List<Location> spawns = new ArrayList<Location>(plugin.spawns().getSpawns());
        List<MTeam> teams = plugin.teams().getTeams();
        if (spawns.size() < teams.size()) {
            // Notfall: fehlende Punkte ohne Schematic berechnen
            spawns.addAll(plugin.spawns().computePoints(world, teams.size() - spawns.size(),
                    plugin.spawns().autoRadius(teams.size()) - 7, s().spawnYOffset));
        }
        Collections.shuffle(spawns);

        alive.clear();
        kills.clear();
        deathLocations.clear();
        gameSeconds = 0;
        shrinkStarted = false;

        for (int i = 0; i < teams.size(); i++) {
            MTeam team = teams.get(i);
            Location loc = spawns.get(i).clone();
            if (loc.getWorld() == null) loc.setWorld(world);
            for (UUID u : team.getMembers()) {
                Player p = Bukkit.getPlayer(u);
                if (p == null) continue;
                resetPlayer(p, GameMode.SURVIVAL);
                p.teleport(loc);
                giveKit(p);
                alive.add(u);
                kills.put(u, 0);
                names.put(u, p.getName());
            }
        }
        // Wer kein Team hat (z. B. während der Vorbereitung gejoint) -> Zuschauer
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!alive.contains(p.getUniqueId())) toSpectator(p, null);
        }
        state = GameState.FREEZE;
        phaseTimer = Math.max(0, s().freezeTime);
        broadcast("§a" + alive.size() + " Spieler §7in §a" + teams.size() + " Teams §7sind gestartet. Viel Erfolg!");
    }

    // =====================================================================
    //  Tod / Sieg
    // =====================================================================

    public void eliminate(Player p, Player killer, boolean quit) {
        if (!alive.remove(p.getUniqueId())) return;
        deathLocations.put(p.getUniqueId(), p.getLocation());
        MTeam team = plugin.teams().getTeam(p);
        String name = (team != null && !s().isSolo() ? team.getColor() : "§c") + p.getName();

        if (killer != null && !killer.equals(p)) {
            kills.put(killer.getUniqueId(), getKills(killer.getUniqueId()) + 1);
            MTeam kt = plugin.teams().getTeam(killer);
            if (kt != null) kt.addKill();
            String kname = (kt != null && !s().isSolo() ? kt.getColor() : "§a") + killer.getName();
            broadcast(name + " §7wurde von " + kname + " §7getötet. §8(" + alive.size() + " übrig)");
        } else if (quit) {
            broadcast(name + " §7hat das Spiel verlassen und ist ausgeschieden. §8(" + alive.size() + " übrig)");
        } else {
            broadcast(name + " §7ist gestorben. §8(" + alive.size() + " übrig)");
        }

        if (s().lightningOnDeath) p.getWorld().strikeLightningEffect(p.getLocation());
        if (s().goldenAppleOnDeath) p.getWorld().dropItemNaturally(p.getLocation(), new ItemStack(Material.GOLDEN_APPLE));

        if (team != null && !s().isSolo()) {
            boolean anyAlive = false;
            for (UUID u : team.getMembers()) if (alive.contains(u)) anyAlive = true;
            if (!anyAlive) broadcast("§7Team " + team.getDisplayName() + " §7wurde §cvernichtet§7!");
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                checkWin();
            }
        }.runTaskLater(plugin, 2L);
    }

    public Location getDeathLocation(Player p) {
        return deathLocations.get(p.getUniqueId());
    }

    private void checkWin() {
        if (!state.isIngame()) return;
        List<MTeam> aliveTeams = new ArrayList<MTeam>();
        for (MTeam t : plugin.teams().getTeams()) {
            for (UUID u : t.getMembers()) {
                if (alive.contains(u)) {
                    aliveTeams.add(t);
                    break;
                }
            }
        }
        if (aliveTeams.size() <= 1) endGame(aliveTeams.isEmpty() ? null : aliveTeams.get(0));
    }

    private void endGame(final MTeam winner) {
        state = GameState.ENDING;
        String title;
        if (winner == null) {
            title = "§7Kein Gewinner";
        } else if (s().isSolo()) {
            title = "§6" + winner.getName() + " §ahat gewonnen!";
        } else {
            title = "§7Team " + winner.getDisplayName() + " §ahat gewonnen!";
        }
        broadcast("§8§m-----------------------------");
        broadcast(title);
        List<Map.Entry<UUID, Integer>> top = topKillers(3);
        for (int i = 0; i < top.size(); i++) {
            broadcast("§e" + (i + 1) + ". §f" + names.get(top.get(i).getKey()) + " §7- §c" + top.get(i).getValue() + " Kills");
        }
        broadcast("§8§m-----------------------------");
        for (Player p : Bukkit.getOnlinePlayers()) {
            Nms.title(p, title, "§7GG!");
            p.playSound(p.getLocation(), Sound.LEVEL_UP, 1f, 1f);
        }
        if (winner != null) {
            new BukkitRunnable() {
                int i = 0;

                @Override
                public void run() {
                    if (i++ >= 5 || state != GameState.ENDING) {
                        cancel();
                        return;
                    }
                    for (UUID u : winner.getMembers()) {
                        Player p = Bukkit.getPlayer(u);
                        if (p != null) firework(p.getLocation());
                    }
                }
            }.runTaskTimer(plugin, 10L, 30L);
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                finish();
            }
        }.runTaskLater(plugin, Math.max(3, s().endDelay) * 20L);
    }

    private void finish() {
        if (s().restartOnEnd) {
            for (Player p : Bukkit.getOnlinePlayers()) p.kickPlayer("§bMeetup §7ist vorbei.\n§7Der Server startet neu – bis gleich!");
            Bukkit.shutdown();
            return;
        }
        resetToLobby();
    }

    /** /meetup stop */
    public void stop() {
        if (state == GameState.STARTING) {
            state = GameState.LOBBY;
            forced = false;
            setLevels(0);
            broadcast("§cDer Countdown wurde abgebrochen.");
            return;
        }
        if (state.isIngame() || state == GameState.PREPARING || state == GameState.ENDING) {
            broadcast("§cDie Runde wurde von einem Admin beendet.");
            resetToLobby();
        }
    }

    public void resetToLobby() {
        state = GameState.LOBBY;
        forced = false;
        alive.clear();
        kills.clear();
        deathLocations.clear();
        gameSeconds = 0;
        shrinkStarted = false;
        plugin.teams().reset();
        World w = s().getGameWorld();
        if (w != null) {
            w.getWorldBorder().setSize(Math.max(10, s().borderStart));
            w.getWorldBorder().setCenter(s().centerX + 0.5, s().centerZ + 0.5);
        }
        for (Player p : Bukkit.getOnlinePlayers()) toLobby(p);
    }

    // =====================================================================
    //  Spieler-Hilfen
    // =====================================================================

    public void toLobby(Player p) {
        resetPlayer(p, GameMode.ADVENTURE);
        p.teleport(s().getLobbyOrSpawn());
        giveLobbyItems(p);
    }

    public void toSpectator(Player p, Location at) {
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.setGameMode(GameMode.SPECTATOR);
        if (at == null) {
            Location c = s().getCenter();
            if (c != null) {
                int y = SpawnManager.findGround(c.getWorld(), c.getBlockX(), c.getBlockZ());
                at = new Location(c.getWorld(), c.getX(), y + 20, c.getZ());
            }
        }
        if (at != null) p.teleport(at);
        p.sendMessage(s().prefix + "§7Du bist jetzt §cZuschauer§7.");
    }

    public void giveLobbyItems(Player p) {
        PlayerInventory inv = p.getInventory();
        if (!s().isSolo()) {
            inv.setItem(0, new ItemBuilder(Material.NAME_TAG).name(ITEM_TEAMS).lore("§7Wähle dein Team").build());
        }
        if (p.hasPermission("meetup.admin")) {
            inv.setItem(4, new ItemBuilder(Material.REDSTONE_COMPARATOR).name(ITEM_SETUP).lore("§7Öffnet das Setup-Menü").build());
            inv.setItem(8, new ItemBuilder(Material.EMERALD).name(ITEM_START).lore("§7Startet den 10s-Countdown").build());
        }
    }

    @SuppressWarnings("deprecation")
    public void resetPlayer(Player p, GameMode mode) {
        p.setGameMode(mode);
        p.setMaxHealth(20.0);
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
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

    @SuppressWarnings("deprecation")
    public void giveKit(Player p) {
        PlayerInventory inv = p.getInventory();
        if (s().kitContents != null) {
            ItemStack[] c = new ItemStack[inv.getSize()];
            for (int i = 0; i < Math.min(c.length, s().kitContents.length); i++) {
                c[i] = s().kitContents[i] == null ? null : s().kitContents[i].clone();
            }
            inv.setContents(c);
            ItemStack[] a = new ItemStack[4];
            for (int i = 0; i < 4 && s().kitArmor != null && i < s().kitArmor.length; i++) {
                a[i] = s().kitArmor[i] == null ? null : s().kitArmor[i].clone();
            }
            inv.setArmorContents(a);
        } else {
            defaultKit(inv);
        }
        p.updateInventory();
    }

    private void defaultKit(PlayerInventory inv) {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.addEnchantment(Enchantment.DAMAGE_ALL, 1);
        ItemStack bow = new ItemStack(Material.BOW);
        bow.addEnchantment(Enchantment.ARROW_DAMAGE, 1);
        inv.setItem(0, sword);
        inv.setItem(1, new ItemStack(Material.FISHING_ROD));
        inv.setItem(2, bow);
        inv.setItem(3, new ItemStack(Material.COOKED_BEEF, 32));
        inv.setItem(4, new ItemStack(Material.GOLDEN_APPLE, 3));
        inv.setItem(5, new ItemStack(Material.WATER_BUCKET));
        inv.setItem(6, new ItemStack(Material.LAVA_BUCKET));
        inv.setItem(7, new ItemStack(Material.COBBLESTONE, 64));
        inv.setItem(8, new ItemStack(Material.WOOD, 64));
        inv.setItem(9, new ItemStack(Material.ARROW, 32));
        inv.setItem(10, new ItemStack(Material.DIAMOND_PICKAXE));
        inv.setItem(11, new ItemStack(Material.DIAMOND_AXE));
        inv.setItem(12, new ItemStack(Material.WATER_BUCKET));
        inv.setItem(13, new ItemStack(Material.ANVIL));
        inv.setItem(14, new ItemStack(Material.EXP_BOTTLE, 16));
        ItemStack helm = new ItemStack(Material.DIAMOND_HELMET);
        ItemStack chest = new ItemStack(Material.IRON_CHESTPLATE);
        ItemStack legs = new ItemStack(Material.IRON_LEGGINGS);
        ItemStack boots = new ItemStack(Material.DIAMOND_BOOTS);
        chest.addEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 1);
        legs.addEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 1);
        inv.setHelmet(helm);
        inv.setChestplate(chest);
        inv.setLeggings(legs);
        inv.setBoots(boots);
    }

    private void firework(Location l) {
        Firework fw = l.getWorld().spawn(l, Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.AQUA, Color.LIME).withFade(Color.WHITE).trail(true).build());
        meta.setPower(1);
        fw.setFireworkMeta(meta);
    }

    public void broadcast(String msg) {
        Bukkit.broadcastMessage(s().prefix + msg);
    }

    private void playSound(Sound sound, float pitch) {
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), sound, 1f, pitch);
    }

    private void setLevels(int level) {
        for (Player p : Bukkit.getOnlinePlayers()) p.setLevel(level);
    }

    // =====================================================================
    //  Anzeige
    // =====================================================================

    private void sendActionBars() {
        World w = s().getGameWorld();
        if (w == null) return;
        double half = w.getWorldBorder().getSize() / 2.0;
        Location c = w.getWorldBorder().getCenter();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld() != w) continue;
            double dx = Math.abs(p.getLocation().getX() - c.getX());
            double dz = Math.abs(p.getLocation().getZ() - c.getZ());
            int dist = (int) Math.max(0, half - Math.max(dx, dz));
            Nms.actionBar(p, "§7Der §bSpawn §7ist bei §bX: " + s().centerX + " §7und §bZ: " + s().centerZ
                    + " §8┃ §7Distanz zur §bBorder§7: §e" + dist + " §7Blöcke");
        }
    }

    public void createBoard(Player p) {
        boards.put(p.getUniqueId(), new Sidebar(p, s().scoreboardTitle));
        names.put(p.getUniqueId(), p.getName());
    }

    public void removeBoard(Player p) {
        boards.remove(p.getUniqueId());
    }

    public void updateScoreboards() {
        // Nametags einmal berechnen
        Map<String, String> prefixes = new LinkedHashMap<String, String>();
        Map<String, Set<String>> members = new LinkedHashMap<String, Set<String>>();
        Set<String> specs = new HashSet<String>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) specs.add(p.getName());
        }
        if (!s().isSolo()) {
            for (MTeam t : plugin.teams().getTeams()) {
                String key = (t.getId() < 10 ? "0" : "") + t.getId();
                Set<String> set = new HashSet<String>();
                for (UUID u : t.getMembers()) {
                    Player p = Bukkit.getPlayer(u);
                    if (p != null && !specs.contains(p.getName())) set.add(p.getName());
                }
                prefixes.put(key, t.getColor().toString());
                members.put(key, set);
            }
        }
        prefixes.put("zspec", "§7§o");
        members.put("zspec", specs);

        String time = formatTime(gameSeconds);
        World w = s().getGameWorld();
        int border = w == null ? s().borderStart : (int) w.getWorldBorder().getSize();
        List<Map.Entry<UUID, Integer>> top = topKillers(3);

        for (Player p : Bukkit.getOnlinePlayers()) {
            Sidebar sb = boards.get(p.getUniqueId());
            if (sb == null) continue;
            if (p.getScoreboard() != sb.getBoard()) p.setScoreboard(sb.getBoard());
            sb.setTitle(s().scoreboardTitle);
            MTeam team = plugin.teams().getTeam(p);
            String teamName = team == null ? "§7-" : (s().isSolo() ? "§fSolo" : team.getDisplayName());
            List<String> lines = new ArrayList<String>();
            if (state.isLobby()) {
                lines.add("§7Spieler: §a" + Bukkit.getOnlinePlayers().size() + "§7/§a" + Bukkit.getMaxPlayers());
                lines.add("§7Benötigt: §e" + s().minPlayers);
                if (state == GameState.STARTING) lines.add("§7Start in: §a" + countdown + "s");
                else if (state == GameState.PREPARING) lines.add("§7Status: §eVorbereitung");
                else lines.add("§7Status: §eWarten...");
                lines.add(" ");
                lines.add("§7Modus: §b" + (s().isSolo() ? "Solo" : "Teams à " + s().teamSize));
                if (!s().isSolo()) lines.add("§7Team: " + teamName);
                lines.add("§7Border: §b" + s().borderStart);
            } else {
                lines.add("§7Team: " + teamName);
                int teamKills = team == null ? 0 : team.getKills();
                lines.add("§7Kills: §c" + getKills(p.getUniqueId()) + (s().isSolo() ? "" : " §8(" + teamKills + ")"));
                lines.add("§7Lebende: §a" + alive.size());
                lines.add("  ");
                lines.add("§7Zeit: §a" + time);
                lines.add("§7Border: §b" + border);
                lines.add("   ");
                for (int i = 0; i < 3; i++) {
                    if (i < top.size()) {
                        Map.Entry<UUID, Integer> e = top.get(i);
                        String n = names.get(e.getKey());
                        lines.add("§c" + e.getValue() + " §7- §e" + (n == null ? "?" : n) + "§" + Integer.toHexString(i));
                    } else {
                        lines.add("§80 §7- §8-" + "§" + Integer.toHexString(i));
                    }
                }
            }
            lines.add("    ");
            lines.add("§7Server-IP:");
            lines.add("§b" + s().serverIp);
            sb.setLines(lines);
            sb.setNametags(prefixes, members);
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

    private static String formatTime(int sec) {
        return (sec / 3600) + "h " + ((sec % 3600) / 60) + "m " + (sec % 60) + "s";
    }
}
