package de.crafterteam.meetup.spawn;

import de.crafterteam.meetup.MeetupPlugin;
import de.crafterteam.meetup.Settings;
import de.crafterteam.meetup.schematic.PasteQueue;
import de.crafterteam.meetup.schematic.Schematic;
import de.crafterteam.meetup.util.Nms;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generiert Spawns im Kreis um den Mittelpunkt und setzt an jeden Punkt die Spawn-Schematic.
 */
public class SpawnManager {

    private static final Set<Material> IGNORED_GROUND = EnumSet.of(
            Material.AIR, Material.LEAVES, Material.LEAVES_2, Material.LOG, Material.LOG_2,
            Material.LONG_GRASS, Material.DOUBLE_PLANT, Material.YELLOW_FLOWER, Material.RED_ROSE,
            Material.SNOW, Material.VINE, Material.DEAD_BUSH, Material.RED_MUSHROOM, Material.BROWN_MUSHROOM,
            Material.SUGAR_CANE_BLOCK, Material.CACTUS, Material.HUGE_MUSHROOM_1, Material.HUGE_MUSHROOM_2,
            Material.WATER_LILY);

    private final MeetupPlugin plugin;
    private final File schematicFolder;
    private final List<Location> spawns = new ArrayList<Location>();
    private final List<PasteQueue.Change> undo = new ArrayList<PasteQueue.Change>();
    private boolean busy;

    public SpawnManager(MeetupPlugin plugin) {
        this.plugin = plugin;
        this.schematicFolder = new File(plugin.getDataFolder(), "schematics");
        schematicFolder.mkdirs();
    }

    public File getSchematicFolder() {
        return schematicFolder;
    }

    public List<Location> getSpawns() {
        return Collections.unmodifiableList(spawns);
    }

    public boolean isBusy() {
        return busy;
    }

    public boolean hasUndo() {
        return !undo.isEmpty();
    }

    // ---------------- Schematics ----------------

    public List<String> listSchematics() {
        List<String> names = new ArrayList<String>();
        File[] files = schematicFolder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().toLowerCase().endsWith(".schematic")) {
                    names.add(f.getName().substring(0, f.getName().length() - ".schematic".length()));
                }
            }
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public File schematicFile(String name) {
        return new File(schematicFolder, name + ".schematic");
    }

    public Schematic loadSchematic(String name) throws IOException {
        File f = schematicFile(name);
        if (!f.exists()) throw new IOException("Schematic '" + name + "' nicht gefunden.");
        return Schematic.load(f);
    }

    // ---------------- Speichern / Laden der Spawnpunkte ----------------

    public void loadFromConfig() {
        spawns.clear();
        for (Map<?, ?> m : plugin.getConfig().getMapList("spawns.locations")) {
            Location l = Settings.readLocation(m);
            if (l != null) spawns.add(l);
        }
    }

    private void saveToConfig() {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (Location l : spawns) list.add(Settings.locToMap(l));
        plugin.getConfig().set("spawns.locations", list);
        plugin.saveConfig();
    }

    public void addSpawn(Location l) {
        spawns.add(l.clone());
        saveToConfig();
    }

    public void clearSpawns() {
        spawns.clear();
        saveToConfig();
    }

    // ---------------- Generierung ----------------

    public int autoRadius(int count) {
        Settings s = plugin.settings();
        int half = s.borderStart / 2;
        int wanted = Math.max(25, count * 10);
        int max = Math.max(15, half - 25);
        return Math.min(wanted, max);
    }

    public int autoCount() {
        Settings s = plugin.settings();
        if (s.spawnCount > 0) return s.spawnCount;
        if (s.isSolo()) return Math.max(2, Bukkit.getMaxPlayers() > 100 ? 24 : Bukkit.getMaxPlayers());
        return s.teamCount;
    }

    /**
     * Generiert count Spawns im Radius radius (0 = auto) und setzt die Schematic.
     * Alte, von uns gesetzte Spawns werden vorher zurückgesetzt.
     */
    public boolean generate(final int count, int radius, final CommandSender feedback, final Runnable done) {
        if (busy) {
            msg(feedback, "§cEs läuft bereits eine Generierung.");
            return false;
        }
        final Settings s = plugin.settings();
        final World world = s.getGameWorld();
        if (world == null) {
            msg(feedback, "§cSpielwelt '" + s.worldName + "' existiert nicht.");
            return false;
        }
        Schematic schem = null;
        if (s.schematic != null && !s.schematic.isEmpty()) {
            try {
                schem = loadSchematic(s.schematic);
            } catch (IOException e) {
                msg(feedback, "§cSchematic-Fehler: " + e.getMessage());
                return false;
            }
        }
        final int r = radius > 0 ? radius : autoRadius(count);
        busy = true;

        final Schematic schematic = schem;
        Runnable afterUndo = new Runnable() {
            @Override
            public void run() {
                spawns.clear();
                spawns.addAll(computePoints(world, count, r, s.spawnYOffset));
                List<PasteQueue.Change> changes = new ArrayList<PasteQueue.Change>();
                if (schematic != null) {
                    for (Location target : spawns) changes.addAll(schematic.createChanges(target, s.pasteAir));
                }
                saveToConfig();
                msg(feedback, plugin.settings().prefix + "§a" + count + " Spawns §7im Radius §e" + r
                        + " §7berechnet" + (schematic != null ? ", setze §e" + changes.size() + " §7Blöcke..." : "."));

                PasteQueue.run(plugin, changes, undo, s.blocksPerTick, new PasteQueue.Progress() {
                    @Override
                    public void update(int doneBlocks, int total) {
                        if (feedback instanceof Player && total > 0) {
                            Nms.actionBar((Player) feedback, "§7Spawns werden gesetzt: §a" + (doneBlocks * 100 / total) + "%");
                        }
                    }
                }, new Runnable() {
                    @Override
                    public void run() {
                        busy = false;
                        msg(feedback, plugin.settings().prefix + "§aSpawns fertig generiert.");
                        if (done != null) done.run();
                    }
                });
            }
        };

        if (!undo.isEmpty()) {
            msg(feedback, plugin.settings().prefix + "§7Entferne alte Spawn-Schematics...");
            PasteQueue.undo(plugin, undo, s.blocksPerTick, afterUndo);
        } else {
            afterUndo.run();
        }
        return true;
    }

    /** Gleichmäßig verteilte Punkte auf einem Kreis, jeweils auf dem Boden, Blick zur Mitte. */
    public List<Location> computePoints(World world, int count, int radius, int yOffset) {
        Settings s = plugin.settings();
        List<Location> list = new ArrayList<Location>();
        double startAngle = Math.random() * Math.PI * 2;
        for (int i = 0; i < count; i++) {
            double angle = startAngle + 2 * Math.PI * i / Math.max(1, count);
            int x = s.centerX + (int) Math.round(Math.cos(angle) * radius);
            int z = s.centerZ + (int) Math.round(Math.sin(angle) * radius);
            int groundY = findGround(world, x, z);
            Location target = new Location(world, x + 0.5, groundY + 1 + yOffset, z + 0.5);
            target.setYaw(yawTo(target, s.centerX + 0.5, s.centerZ + 0.5));
            list.add(target);
        }
        return list;
    }

    /** Entfernt die zuletzt gesetzten Schematics wieder. */
    public void removePasted(final CommandSender feedback) {
        if (busy) {
            msg(feedback, "§cEs läuft bereits eine Generierung.");
            return;
        }
        if (undo.isEmpty()) {
            msg(feedback, plugin.settings().prefix + "§cKeine gesetzten Spawn-Schematics gespeichert (nur seit letztem Neustart möglich).");
            return;
        }
        busy = true;
        PasteQueue.undo(plugin, undo, plugin.settings().blocksPerTick, new Runnable() {
            @Override
            public void run() {
                busy = false;
                msg(feedback, plugin.settings().prefix + "§aSpawn-Schematics entfernt.");
            }
        });
    }

    /** Testweise einzelne Schematic an einer Position einfügen (mit Undo). */
    public void pasteSingle(Schematic schem, Location target, final CommandSender feedback) {
        PasteQueue.run(plugin, schem.createChanges(target, plugin.settings().pasteAir), undo,
                plugin.settings().blocksPerTick, null, new Runnable() {
                    @Override
                    public void run() {
                        msg(feedback, plugin.settings().prefix + "§aSchematic eingefügt. §7(/meetup spawns undo zum Entfernen)");
                    }
                });
    }

    public static int findGround(World world, int x, int z) {
        int y = Math.min(254, world.getHighestBlockYAt(x, z));
        while (y > 1) {
            Block b = world.getBlockAt(x, y, z);
            if (!IGNORED_GROUND.contains(b.getType())) break;
            y--;
        }
        return y;
    }

    public static float yawTo(Location from, double tx, double tz) {
        double dx = tx - from.getX();
        double dz = tz - from.getZ();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static void msg(CommandSender s, String m) {
        if (s != null) s.sendMessage(m);
        else Bukkit.getLogger().info("[CrafterMeetup] " + m.replaceAll("§.", ""));
    }
}
