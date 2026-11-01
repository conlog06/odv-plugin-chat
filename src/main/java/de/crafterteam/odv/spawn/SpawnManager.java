package de.crafterteam.odv.spawn;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.Settings;
import de.crafterteam.odv.data.Arena;
import de.crafterteam.odv.schematic.PasteQueue;
import de.crafterteam.odv.schematic.Schematic;
import de.crafterteam.odv.util.ChunkPregen;
import de.crafterteam.odv.util.Nms;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Schematics, Schnellsetup (Arena + Lobby) und Team-Spawns (Plattformen im Kreis oder sicherer Scatter).
 */
public class SpawnManager {

    private static final Set<Material> IGNORED_GROUND = EnumSet.of(
            Material.AIR, Material.LEAVES, Material.LEAVES_2, Material.LOG, Material.LOG_2,
            Material.LONG_GRASS, Material.DOUBLE_PLANT, Material.YELLOW_FLOWER, Material.RED_ROSE,
            Material.SNOW, Material.VINE, Material.DEAD_BUSH, Material.RED_MUSHROOM, Material.BROWN_MUSHROOM,
            Material.SUGAR_CANE_BLOCK, Material.CACTUS, Material.HUGE_MUSHROOM_1, Material.HUGE_MUSHROOM_2,
            Material.WATER_LILY);
    private static final Set<Material> BAD_GROUND = EnumSet.of(
            Material.WATER, Material.STATIONARY_WATER, Material.LAVA, Material.STATIONARY_LAVA,
            Material.ICE, Material.PACKED_ICE, Material.CACTUS, Material.FIRE);

    private static final int[][] MEMBER_OFFSETS = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}, {2, 0}};

    private final OdvPlugin plugin;
    private final File folder;
    private final List<PasteQueue.Change> undo = new ArrayList<PasteQueue.Change>();
    private final Random random = new Random();
    private boolean busy;

    public SpawnManager(OdvPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "schematics");
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

    // =============================================================== Schematics

    /** Legt Lobby- und Plattform-Schematic an, falls sie fehlen. */
    public void ensureDefaults() {
        folder.mkdirs();
        File lobby = file("lobby");
        if (!lobby.exists()) {
            InputStream in = plugin.getResource("schematics/lobby.schematic");
            if (in != null) {
                try {
                    Files.copy(in, lobby.toPath());
                } catch (IOException e) {
                    plugin.getLogger().warning("lobby.schematic konnte nicht entpackt werden: " + e.getMessage());
                } finally {
                    try {
                        in.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
        File platform = file("plattform");
        if (!platform.exists()) {
            try {
                Schematic.defaultPlatform().save(platform);
            } catch (IOException e) {
                plugin.getLogger().warning("plattform.schematic konnte nicht erstellt werden: " + e.getMessage());
            }
        }
    }

    public File getFolder() {
        return folder;
    }

    public File file(String name) {
        return new File(folder, name + ".schematic");
    }

    public List<String> list() {
        List<String> names = new ArrayList<String>();
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                if (n.toLowerCase().endsWith(".schematic")) names.add(n.substring(0, n.length() - 10));
            }
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public Schematic load(String name) throws IOException {
        return Schematic.load(file(name));
    }

    // =============================================================== Schnellsetup

    /**
     * Neue Arena-Welt erstellen, Lobby-Schematic in die Mitte setzen, Border setzen, Map vorgenerieren.
     */
    public void quickSetup(final Player actor) {
        if (busy) {
            actor.sendMessage(s().prefix + "§cEs läuft bereits ein Setup.");
            return;
        }
        final Schematic lobby;
        try {
            lobby = load(s().lobbySchematic);
        } catch (IOException e) {
            actor.sendMessage(s().prefix + "§cLobby-Schematic: " + e.getMessage());
            return;
        }
        String name = "odv_arena";
        int n = 1;
        while (new File(Bukkit.getWorldContainer(), name).exists() || Bukkit.getWorld(name) != null) name = "odv_arena_" + n++;
        actor.sendMessage(s().prefix + "§7Erstelle Welt §e" + name + "§7... (kann kurz laggen)");
        final World w = Bukkit.createWorld(new WorldCreator(name).environment(World.Environment.NORMAL).generateStructures(false));
        if (w == null) {
            actor.sendMessage(s().prefix + "§cWelt konnte nicht erstellt werden.");
            return;
        }
        w.setGameRuleValue("doDaylightCycle", "false");
        w.setGameRuleValue("doFireTick", "false");
        w.setTime(6000);
        w.setStorm(false);

        // Platz an Land suchen (kein Ozean)
        Location spot = null;
        Location base = w.getSpawnLocation();
        for (int i = 0; i < 40 && spot == null; i++) {
            int x = base.getBlockX() + (i == 0 ? 0 : random.nextInt(800) - 400);
            int z = base.getBlockZ() + (i == 0 ? 0 : random.nextInt(800) - 400);
            Location g = safeGround(w, x, z);
            if (g != null) spot = g;
        }
        if (spot == null) spot = new Location(w, base.getBlockX() + 0.5, findGround(w, base.getBlockX(), base.getBlockZ()) + 1, base.getBlockZ() + 0.5);
        final Location target = spot;
        busy = true;
        actor.sendMessage(s().prefix + "§7Setze Lobby §e" + s().lobbySchematic + " §7bei §f" + target.getBlockX() + ", " + target.getBlockZ() + "§7...");
        undo.clear();
        PasteQueue.run(plugin, lobby.createChanges(target, s().pasteAir), null, s().blocksPerTick, progress(actor, "Lobby"), new Runnable() {
            @Override
            public void run() {
                busy = false;
                arena().setLobbyAndCenter(target);
                w.setSpawnLocation(target.getBlockX(), target.getBlockY(), target.getBlockZ());
                plugin.game().applyWorldSettings();
                if (plugin.game().getState().isLobby()) {
                    for (Player o : Bukkit.getOnlinePlayers()) plugin.game().toLobby(o);
                }
                if (actor.isOnline()) {
                    actor.sendMessage(s().prefix + "§a✔ Arena fertig! §7Lobby & Mitte gesetzt.");
                    actor.sendMessage(s().prefix + "§7Map wird jetzt vorgeneriert – danach §a/odv start§7.");
                }
                ChunkPregen.start(plugin, w, target.getBlockX(), target.getBlockZ(), s().borderInitial / 2 + 32, actor);
            }
        });
    }

    /** Eine Schematic an einer Stelle einfügen (Lobby von Hand, Tests). */
    public void pasteAt(Schematic schem, Location target, final CommandSender feedback) {
        PasteQueue.run(plugin, schem.createChanges(target, s().pasteAir), undo, s().blocksPerTick,
                feedback instanceof Player ? progress((Player) feedback, "Schematic") : null, new Runnable() {
                    @Override
                    public void run() {
                        if (feedback != null) feedback.sendMessage(s().prefix + "§aEingefügt. §7(/odv spawns undo entfernt es wieder)");
                    }
                });
    }

    // =============================================================== Team-Spawns

    public int autoRadius(int count) {
        int min = s().protectionRadius + 25;
        int max = Math.max(min, s().borderInitial / 2 - 25);
        return Math.min(max, Math.max(min, count * 9));
    }

    /**
     * Erzeugt count Team-Spawns. PLATFORM: Kreis + Schematic, SCATTER: zufällige sichere Punkte.
     * @return false, wenn nicht gestartet (done wird dann nicht aufgerufen)
     */
    public boolean generate(final int count, final CommandSender feedback, final Runnable done) {
        if (busy) {
            msg(feedback, "§cEs läuft bereits eine Generierung.");
            return false;
        }
        final Location center = arena().getCenter();
        if (center == null) {
            msg(feedback, "§cZuerst Arena einrichten (Schnellsetup oder Lobby setzen).");
            return false;
        }
        final World world = center.getWorld();
        Schematic schem = null;
        if (s().spawnMode == Settings.SpawnMode.PLATFORM) {
            try {
                schem = load(s().teamSchematic);
            } catch (IOException e) {
                msg(feedback, "§cTeam-Schematic: " + e.getMessage() + " §7– verteile ohne Plattform.");
            }
        }
        final Schematic schematic = schem;
        busy = true;
        Runnable afterUndo = new Runnable() {
            @Override
            public void run() {
                List<Location> points = s().spawnMode == Settings.SpawnMode.SCATTER
                        ? scatter(world, center, count)
                        : ring(world, center, count, s().spawnRadius > 0 ? s().spawnRadius : autoRadius(count));
                arena().setSpawns(points);
                List<PasteQueue.Change> changes = new ArrayList<PasteQueue.Change>();
                if (schematic != null) for (Location l : points) changes.addAll(schematic.createChanges(l, s().pasteAir));
                msg(feedback, "§a" + points.size() + " Spawns §7berechnet" + (changes.isEmpty() ? "." : ", setze §e" + changes.size() + " §7Blöcke..."));
                PasteQueue.run(plugin, changes, undo, s().blocksPerTick,
                        feedback instanceof Player ? progress((Player) feedback, "Spawns") : null, new Runnable() {
                            @Override
                            public void run() {
                                busy = false;
                                msg(feedback, "§a✔ Spawns fertig.");
                                if (done != null) done.run();
                            }
                        });
            }
        };
        if (!undo.isEmpty()) {
            PasteQueue.undo(plugin, undo, s().blocksPerTick, afterUndo);
        } else {
            afterUndo.run();
        }
        return true;
    }

    public void removePasted(final CommandSender feedback) {
        if (busy) {
            msg(feedback, "§cEs läuft bereits eine Generierung.");
            return;
        }
        if (undo.isEmpty()) {
            msg(feedback, "§cNichts zum Entfernen (nur seit dem letzten Neustart möglich).");
            return;
        }
        busy = true;
        PasteQueue.undo(plugin, undo, s().blocksPerTick, new Runnable() {
            @Override
            public void run() {
                busy = false;
                msg(feedback, "§aPlattformen entfernt.");
            }
        });
    }

    /** Gleichmäßig auf einem Kreis, Boden-Höhe, Blick zur Mitte. */
    public List<Location> ring(World world, Location center, int count, int radius) {
        List<Location> list = new ArrayList<Location>();
        double start = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < count; i++) {
            double a = start + 2 * Math.PI * i / Math.max(1, count);
            int x = center.getBlockX() + (int) Math.round(Math.cos(a) * radius);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(a) * radius);
            Location l = new Location(world, x + 0.5, findGround(world, x, z) + 1 + s().spawnYOffset, z + 0.5);
            l.setYaw(yawTo(l, center));
            list.add(l);
        }
        return list;
    }

    /** Zufällige, sichere Punkte mit Mindestabstand; Rest notfalls auf einem Kreis. */
    public List<Location> scatter(World world, Location center, int count) {
        List<Location> list = new ArrayList<Location>();
        int half = s().borderInitial / 2 - 15;
        int minFromCenter = s().protectionRadius + 20;
        int dist = s().minTeamDistance;
        for (int pass = 0; pass < 3 && list.size() < count; pass++) {
            int attempts = count * 60;
            while (list.size() < count && attempts-- > 0) {
                int x = center.getBlockX() + random.nextInt(half * 2 + 1) - half;
                int z = center.getBlockZ() + random.nextInt(half * 2 + 1) - half;
                if (Math.hypot(x - center.getX(), z - center.getZ()) < minFromCenter) continue;
                boolean near = false;
                for (Location o : list) if (Math.hypot(o.getX() - x, o.getZ() - z) < dist) near = true;
                if (near) continue;
                Location l = safeGround(world, x, z);
                if (l == null) continue;
                l.setYaw(yawTo(l, center));
                list.add(l);
            }
            dist = Math.max(10, dist / 2); // bei vollen Maps Abstand lockern
        }
        if (list.size() < count) list.addAll(ring(world, center, count - list.size(), autoRadius(count)));
        return list;
    }

    /** Positionen für Teammitglieder rund um den Team-Spawn (nebeneinander, sicher). */
    public List<Location> memberSpots(Location base, int members) {
        List<Location> out = new ArrayList<Location>();
        for (int i = 0; i < members; i++) {
            Location spot = base.clone();
            if (i < MEMBER_OFFSETS.length && i > 0) {
                Location c = base.clone().add(MEMBER_OFFSETS[i][0], 0, MEMBER_OFFSETS[i][1]);
                Block below = c.clone().add(0, -1, 0).getBlock();
                if (below.getType().isSolid() && c.getBlock().getType() == Material.AIR
                        && c.clone().add(0, 1, 0).getBlock().getType() == Material.AIR) {
                    spot = c;
                }
            }
            out.add(spot);
        }
        return out;
    }

    public static int findGround(World world, int x, int z) {
        int y = Math.min(254, world.getHighestBlockYAt(x, z));
        while (y > 1 && IGNORED_GROUND.contains(world.getBlockAt(x, y, z).getType())) y--;
        return y;
    }

    /** Fester, trockener Boden mit 2 Luftblöcken darüber – sonst null. */
    public static Location safeGround(World world, int x, int z) {
        int y = findGround(world, x, z);
        if (y < 3 || y > 250) return null;
        Material ground = world.getBlockAt(x, y, z).getType();
        if (BAD_GROUND.contains(ground) || !ground.isSolid()) return null;
        // Wasser direkt daneben/oben ausschließen
        Material above = world.getBlockAt(x, y + 1, z).getType();
        if (above == Material.WATER || above == Material.STATIONARY_WATER) return null;
        return new Location(world, x + 0.5, y + 1, z + 0.5);
    }

    public static float yawTo(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private PasteQueue.Progress progress(final Player p, final String what) {
        return new PasteQueue.Progress() {
            @Override
            public void update(int done, int total) {
                if (p.isOnline() && total > 0) Nms.actionBar(p, "§7" + what + " wird gesetzt: §a" + (done * 100 / total) + "%");
            }
        };
    }

    private void msg(CommandSender s, String m) {
        if (s != null) s.sendMessage(s().prefix + m);
        else plugin.getLogger().info(m.replaceAll("§.", ""));
    }
}
