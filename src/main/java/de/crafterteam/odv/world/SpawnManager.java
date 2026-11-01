package de.crafterteam.odv.world;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.Settings;
import de.crafterteam.odv.data.Arena;
import de.crafterteam.odv.schematic.PasteQueue;
import de.crafterteam.odv.schematic.Schematic;
import de.crafterteam.odv.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.IntConsumer;

/** Schnellsetup (Arena, Mittel-Spawn, Lobby, Practice) und Team-Spawns. */
public class SpawnManager {

    private static final Set<Material> NATURAL = EnumSet.of(Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT,
            Material.PODZOL, Material.ROOTED_DIRT, Material.MOSS_BLOCK, Material.SAND);
    private static final int[][] MEMBER_OFFSETS = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}, {2, 0}};

    private final OdvPlugin plugin;
    private final File folder;
    private final List<PasteQueue.Change> undo = new ArrayList<>();
    private final Random random = new Random();
    private boolean busy;

    public SpawnManager(OdvPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "schematics");
        folder.mkdirs();
    }

    private Settings s() {
        return plugin.settings();
    }

    private Arena arena() {
        return plugin.arena();
    }

    public boolean isBusy() {
        return busy;
    }

    public boolean hasUndo() {
        return !undo.isEmpty();
    }

    // ============================================================ Schematics

    public File file(String name) {
        return new File(folder, name + ".schem");
    }

    public List<String> list() {
        List<String> names = new ArrayList<>();
        File[] files = folder.listFiles();
        if (files != null) for (File f : files) if (f.getName().endsWith(".schem")) names.add(f.getName().substring(0, f.getName().length() - 6));
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public Schematic load(String name) throws IOException {
        return Schematic.load(file(name));
    }

    public void pasteAt(Schematic schem, Location at, CommandSender feedback) {
        PasteQueue.run(plugin, schem.createChanges(at, true), undo, s().blocksPerTick, progress(feedback, "Schematic"),
                () -> msg(feedback, "§aEingefügt. §7(/odv spawns undo entfernt es wieder)"));
    }

    // ============================================================ Schnellsetup

    public void quickSetup(Player actor) {
        if (busy) {
            msg(actor, "§cEs läuft bereits ein Setup.");
            return;
        }
        String name = "odv_arena";
        int n = 1;
        while (new File(Bukkit.getWorldContainer(), name).exists() || Bukkit.getWorld(name) != null) name = "odv_arena_" + n++;
        msg(actor, "§7Erstelle Welt §e" + name + " §7(mehr Diamanten: §b" + s().diamondRate + "x§7)...");
        World w = Bukkit.createWorld(new WorldCreator(name).generator(new ArenaGenerator(plugin)).generateStructures(false));
        if (w == null) {
            msg(actor, "§cWelt konnte nicht erstellt werden.");
            return;
        }
        w.setGameRule(GameRules.ADVANCE_TIME, false);
        w.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        w.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        w.setTime(6000);
        w.setStorm(false);
        w.setDifficulty(Difficulty.NORMAL);

        msg(actor, "§7Suche eine große Plains-Fläche für den Spawn...");
        Location spot = findCenterSpot(w);
        Location center = spot.clone();
        int lobbyY = Math.min(w.getMaxHeight() - 20, Math.max(center.getBlockY() + 45, 140));
        Location lobby = new Location(w, center.getBlockX() + 0.5, lobbyY + 1, center.getBlockZ() + 0.5, 180f, 0f);
        Location practice = new Location(w, center.getBlockX() + 48.5, lobbyY + 1, center.getBlockZ() + 0.5, 90f, 0f);

        List<PasteQueue.Change> all = new ArrayList<>();
        all.addAll(Structures.centerSpawn(center));
        all.addAll(Structures.lobbyIsland(lobby));
        if (s().has(de.crafterteam.odv.feature.Feature.PRACTICE)) all.addAll(Structures.practiceArena(practice));
        // eigene Lobby-Schematic hat Vorrang
        File custom = file("lobby");
        if (custom.isFile()) {
            try {
                all.addAll(Schematic.load(custom).createChanges(lobby, true));
            } catch (IOException e) {
                msg(actor, "§cLobby-Schematic: " + e.getMessage());
            }
        }
        busy = true;
        PasteQueue.run(plugin, all, null, s().blocksPerTick, progress(actor, "Arena"), () -> {
            busy = false;
            arena().setCenter(center);
            arena().setLobby(lobby);
            arena().setPractice(s().has(de.crafterteam.odv.feature.Feature.PRACTICE) ? practice : null);
            w.setSpawnLocation(lobby);
            plugin.game().applyWorldSettings();
            plugin.game().refreshHolograms();
            if (plugin.game().getState().isLobby()) for (Player o : Bukkit.getOnlinePlayers()) plugin.game().toLobby(o);
            msg(actor, "§a✔ Arena fertig! §7Spawn mit Netherportal bei §f" + center.getBlockX() + " / " + center.getBlockZ()
                    + "§7, Lobby schwebt darüber.");
            msg(actor, "§7Map wird jetzt vorgeneriert – danach §a/odv start§7.");
            ChunkPregen.start(plugin, w, center.getBlockX(), center.getBlockZ(), s().borderInitial / 2 + 48, actor);
        });
    }

    private Location findCenterSpot(World w) {
        Location base = w.getSpawnLocation();
        Location fallback = null;
        for (int i = 0; i < 80; i++) {
            int r = i == 0 ? 0 : 96 + i * 32;
            double a = random.nextDouble() * Math.PI * 2;
            int x = base.getBlockX() + (int) (Math.cos(a) * r), z = base.getBlockZ() + (int) (Math.sin(a) * r);
            Location g = goodSpot(w, x, z, true, true);
            if (g == null) continue;
            if (fallback == null) fallback = g;
            // Umgebung soll auch Plains sein
            int ok = 0;
            for (int[] d : new int[][]{{48, 0}, {-48, 0}, {0, 48}, {0, -48}}) if (isPlains(w, x + d[0], z + d[1])) ok++;
            if (ok >= 3) return g;
        }
        if (fallback != null) return fallback;
        int y = ground(w, base.getBlockX(), base.getBlockZ());
        return new Location(w, base.getBlockX() + 0.5, y + 1, base.getBlockZ() + 0.5);
    }

    // ============================================================ Team-Spawns

    public int autoRadius(int count) {
        int min = s().protectionRadius + 30;
        int max = Math.max(min, s().borderInitial / 2 - 30);
        return Math.min(max, Math.max(min, count * 7));
    }

    /** @return false, wenn nicht gestartet (done wird dann nicht aufgerufen) */
    public boolean generate(int count, CommandSender feedback, Runnable done) {
        if (busy) {
            msg(feedback, "§cEs läuft bereits eine Generierung.");
            return false;
        }
        Location center = arena().getCenter();
        if (center == null) {
            msg(feedback, "§cZuerst Arena einrichten (Schnellsetup).");
            return false;
        }
        World world = center.getWorld();
        Schematic schem = null;
        if (s().spawnStyle == Settings.SpawnStyle.PLATFORM && file(s().teamSchematic).isFile()) {
            try {
                schem = load(s().teamSchematic);
            } catch (IOException e) {
                msg(feedback, "§cTeam-Schematic: " + e.getMessage() + " §7– nutze Standard-Plattform.");
            }
        }
        Schematic schematic = schem;
        busy = true;
        Runnable build = () -> {
            List<Location> points = s().spawnStyle == Settings.SpawnStyle.SCATTER
                    ? scatter(world, center, count)
                    : ring(world, center, count, s().spawnRadius > 0 ? s().spawnRadius : autoRadius(count));
            List<Location> stands = new ArrayList<>();
            List<PasteQueue.Change> changes = new ArrayList<>();
            BlockData slab;
            try {
                slab = Bukkit.createBlockData(s().slab);
            } catch (IllegalArgumentException e) {
                slab = Bukkit.createBlockData("minecraft:smooth_stone_slab[type=top]");
            }
            for (Location p : points) {
                switch (s().spawnStyle) {
                    case HOLES -> {
                        Location stand = p.clone().add(0, -1, 0); // Füße im Loch
                        changes.addAll(Structures.hole(stand, slab));
                        stands.add(stand);
                    }
                    case PLATFORM -> {
                        changes.addAll(schematic != null ? schematic.createChanges(p, true) : Structures.platform(p));
                        stands.add(p);
                    }
                    default -> stands.add(p);
                }
            }
            arena().setSpawns(stands);
            msg(feedback, "§a" + stands.size() + " Spawns §7(" + s().spawnStyle.display + ")" + (changes.isEmpty() ? "." : " – baue..."));
            PasteQueue.run(plugin, changes, undo, s().blocksPerTick, progress(feedback, "Spawns"), () -> {
                busy = false;
                msg(feedback, "§a✔ Spawns fertig.");
                if (done != null) done.run();
            });
        };
        if (!undo.isEmpty()) PasteQueue.undo(plugin, undo, s().blocksPerTick, build);
        else build.run();
        return true;
    }

    /** Zusätzliches Varo-Loch sofort setzen (z. B. für Teammitglieder). */
    public void placeHole(Location stand) {
        BlockData slab;
        try {
            slab = Bukkit.createBlockData(s().slab);
        } catch (IllegalArgumentException e) {
            slab = Bukkit.createBlockData("minecraft:smooth_stone_slab[type=top]");
        }
        for (PasteQueue.Change c : Structures.hole(stand, slab)) {
            Block b = c.world().getBlockAt(c.x(), c.y(), c.z());
            undo.add(new PasteQueue.Change(c.world(), c.x(), c.y(), c.z(), b.getBlockData()));
            b.setBlockData(c.data(), false);
        }
    }

    public void removePasted(CommandSender feedback) {
        if (busy || undo.isEmpty()) {
            msg(feedback, busy ? "§cEs läuft gerade eine Generierung." : "§cNichts zum Entfernen.");
            return;
        }
        busy = true;
        PasteQueue.undo(plugin, undo, s().blocksPerTick, () -> {
            busy = false;
            msg(feedback, "§aSpawns entfernt.");
        });
    }

    /** Kreis um die Mitte; jeder Punkt sucht in der Nähe nach Plains & flachem, natürlichem Boden. */
    public List<Location> ring(World world, Location center, int count, int radius) {
        List<Location> list = new ArrayList<>();
        double start = random.nextDouble() * Math.PI * 2;
        int[] radial = {0, 6, -6, 12, -12, 20, -20, 30};
        double[] angular = {0, 0.04, -0.04, 0.08, -0.08};
        for (int i = 0; i < count; i++) {
            double a0 = start + 2 * Math.PI * i / Math.max(1, count);
            Location found = null;
            outer:
            for (int dr : radial) {
                for (double da : angular) {
                    int r = Math.max(10, radius + dr);
                    int x = center.getBlockX() + (int) Math.round(Math.cos(a0 + da) * r);
                    int z = center.getBlockZ() + (int) Math.round(Math.sin(a0 + da) * r);
                    found = goodSpot(world, x, z, s().plainsOnly, s().naturalOnly);
                    if (found != null && !tooClose(found, list, 8)) break outer;
                    found = null;
                }
            }
            if (found == null) { // lockern: nur trocken
                int x = center.getBlockX() + (int) Math.round(Math.cos(a0) * radius);
                int z = center.getBlockZ() + (int) Math.round(Math.sin(a0) * radius);
                found = goodSpot(world, x, z, false, false);
                if (found == null) found = new Location(world, x + 0.5, ground(world, x, z) + 1, z + 0.5);
            }
            found.setYaw(yawTo(found, center));
            list.add(found);
        }
        return list;
    }

    /** Zufällige Plätze mit Mindestabstand. */
    public List<Location> scatter(World world, Location center, int count) {
        List<Location> list = new ArrayList<>();
        int half = s().borderInitial / 2 - 20, minFromCenter = s().protectionRadius + 25, dist = s().minTeamDistance;
        for (int pass = 0; pass < 4 && list.size() < count; pass++) {
            boolean strict = pass < 2;
            for (int tries = count * 50; tries > 0 && list.size() < count; tries--) {
                int x = center.getBlockX() + random.nextInt(half * 2 + 1) - half;
                int z = center.getBlockZ() + random.nextInt(half * 2 + 1) - half;
                if (Math.hypot(x - center.getX(), z - center.getZ()) < minFromCenter) continue;
                Location l = goodSpot(world, x, z, strict && s().plainsOnly, strict && s().naturalOnly);
                if (l == null || tooClose(l, list, dist)) continue;
                l.setYaw(yawTo(l, center));
                list.add(l);
            }
            dist = Math.max(10, dist * 2 / 3);
        }
        if (list.size() < count) list.addAll(ring(world, center, count - list.size(), autoRadius(count)));
        return list;
    }

    /** Teammitglieder nebeneinander (nur auf sicheren Nachbarblöcken). */
    public List<Location> memberSpots(Location base, int members) {
        List<Location> out = new ArrayList<>();
        boolean hole = s().spawnStyle == Settings.SpawnStyle.HOLES;
        for (int i = 0; i < members; i++) {
            Location spot = base.clone();
            if (i > 0 && i < MEMBER_OFFSETS.length) {
                Location c = base.clone().add(MEMBER_OFFSETS[i][0] * (hole ? 2 : 1), 0, MEMBER_OFFSETS[i][1] * (hole ? 2 : 1));
                int gy = ground(c.getWorld(), c.getBlockX(), c.getBlockZ());
                c.setY(hole ? gy : gy + 1); // bei Löchern: Füße auf Bodenhöhe (eigenes Loch)
                Material at = c.getBlock().getType();
                if (Math.abs(c.getY() - base.getY()) <= 2 && (hole ? at.isSolid() : at.isAir())) spot = c;
            }
            out.add(spot);
        }
        return out;
    }

    // ============================================================ Boden-Prüfung

    public static int ground(World w, int x, int z) {
        return w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
    }

    public static boolean isPlains(World w, int x, int z) {
        Biome b = w.getBiome(x, ground(w, x, z), z);
        return b.equals(Biome.PLAINS) || b.equals(Biome.SUNFLOWER_PLAINS) || b.equals(Biome.MEADOW);
    }

    /** Stehplatz (y über dem Boden) oder null. */
    public static Location goodSpot(World w, int x, int z, boolean plainsOnly, boolean naturalOnly) {
        int y = ground(w, x, z);
        if (y < w.getSeaLevel() - 2 || y > w.getMaxHeight() - 10) return null;
        Block g = w.getBlockAt(x, y, z);
        Material m = g.getType();
        if (!m.isSolid() || Tag.LOGS.isTagged(m) || Tag.LEAVES.isTagged(m) || m == Material.ICE || m == Material.PACKED_ICE) return null;
        if (g.getRelative(0, 1, 0).isLiquid()) return null;
        if (naturalOnly) {
            if (!NATURAL.contains(m)) return null;
            for (int[] d : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
                int gy = ground(w, x + d[0], z + d[1]);
                Material nm = w.getBlockAt(x + d[0], gy, z + d[1]).getType();
                if (Math.abs(gy - y) > 1 || Tag.LOGS.isTagged(nm) || nm == Material.WATER) return null;
            }
        }
        if (plainsOnly) {
            Biome b = w.getBiome(x, y, z);
            if (!(b.equals(Biome.PLAINS) || b.equals(Biome.SUNFLOWER_PLAINS) || b.equals(Biome.MEADOW))) return null;
        }
        return new Location(w, x + 0.5, y + 1, z + 0.5);
    }

    private static boolean tooClose(Location l, List<Location> others, double dist) {
        for (Location o : others) if (Math.hypot(o.getX() - l.getX(), o.getZ() - l.getZ()) < dist) return true;
        return false;
    }

    public static float yawTo(Location from, Location to) {
        return (float) Math.toDegrees(Math.atan2(-(to.getX() - from.getX()), to.getZ() - from.getZ()));
    }

    private IntConsumer progress(CommandSender to, String what) {
        if (!(to instanceof Player p)) return null;
        return pct -> {
            if (p.isOnline()) Txt.actionBar(p, "§7" + what + " wird gebaut: §a" + pct + "%");
        };
    }

    private void msg(CommandSender to, String m) {
        if (to != null) Txt.send(to, s().prefix + m);
        else plugin.getLogger().info(Txt.ascii(m));
    }
}
