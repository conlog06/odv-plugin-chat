package de.crafterteam.odv.world;

import de.crafterteam.odv.schematic.PasteQueue.Change;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Eingebaute Bauten, damit ohne eigene Schematics alles sofort funktioniert. */
public final class Structures {

    private Structures() {
    }

    private static BlockData bd(Material m) {
        return m.createBlockData();
    }

    private static BlockData bd(String s) {
        return Bukkit.createBlockData(s);
    }

    private static void put(List<Change> l, World w, int x, int y, int z, BlockData d) {
        l.add(new Change(w, x, y, z, d));
    }

    /**
     * Mittel-Spawn wie bei Mega-ODV: Plattform, Netherportal, Beacon mit Lichtstrahl.
     * @param stand Position, an der man auf der Plattform steht (Boden = stand.y - 1)
     */
    public static List<Change> centerSpawn(Location stand) {
        List<Change> l = new ArrayList<>();
        World w = stand.getWorld();
        int cx = stand.getBlockX(), fy = stand.getBlockY() - 1, cz = stand.getBlockZ();
        BlockData air = bd(Material.AIR), bricks = bd(Material.STONE_BRICKS), mossy = bd(Material.MOSSY_STONE_BRICKS),
                rim = bd(Material.POLISHED_DEEPSLATE), iron = bd(Material.IRON_BLOCK), obsidian = bd(Material.OBSIDIAN);
        Random r = new Random(cx * 31L + cz);
        // Fundament + Luft darüber
        for (int dx = -5; dx <= 5; dx++)
            for (int dz = -5; dz <= 5; dz++) {
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                for (int dy = 1; dy <= 7; dy++) put(l, w, cx + dx, fy + dy, cz + dz, air);
                if (ring <= 5) {
                    put(l, w, cx + dx, fy - 1, cz + dz, bricks);
                    put(l, w, cx + dx, fy, cz + dz, ring == 5 ? rim : (r.nextInt(5) == 0 ? mossy : bricks));
                }
            }
        // Beacon (Pyramide Stufe 1 unter dem Boden)
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) put(l, w, cx + dx, fy - 1, cz + dz, iron);
        put(l, w, cx, fy, cz, bd(Material.BEACON));
        // Netherportal im Norden (Achse x)
        int pz = cz - 4;
        for (int dx = -2; dx <= 1; dx++)
            for (int dy = 0; dy <= 4; dy++) {
                boolean frame = dx == -2 || dx == 1 || dy == 0 || dy == 4;
                put(l, w, cx + dx, fy + dy, pz, frame ? obsidian : bd("minecraft:nether_portal[axis=x]"));
            }
        // Laternen an den Ecken
        for (int sx : new int[]{-4, 4})
            for (int sz : new int[]{-4, 4}) {
                if (sz == -4) continue; // Portalseite frei lassen
                put(l, w, cx + sx, fy + 1, cz + sz, bd(Material.STONE_BRICK_WALL));
                put(l, w, cx + sx, fy + 2, cz + sz, bd(Material.STONE_BRICK_WALL));
                put(l, w, cx + sx, fy + 3, cz + sz, bd(Material.LANTERN));
            }
        return l;
    }

    /** Schwebende Lobby-Insel (Radius 13) – Mitte = Spawn. */
    public static List<Change> lobbyIsland(Location stand) {
        List<Change> l = new ArrayList<>();
        World w = stand.getWorld();
        int cx = stand.getBlockX(), ty = stand.getBlockY() - 1, cz = stand.getBlockZ();
        Random r = new Random(7);
        int R = 13;
        for (int dx = -R - 1; dx <= R + 1; dx++)
            for (int dz = -R - 1; dz <= R + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > R + 0.5) continue;
                // Unterseite: umgedrehter Kegel
                int depth = (int) Math.max(1, (R - d) * 0.7 + r.nextInt(2));
                for (int dy = 1; dy <= depth; dy++) {
                    Material m = dy == 1 ? Material.DIRT : (dy < 3 ? Material.STONE : (r.nextInt(3) == 0 ? Material.ANDESITE : Material.STONE));
                    put(l, w, cx + dx, ty - dy, cz + dz, bd(m));
                }
                Material top;
                if (d <= 4.5) top = (dx + dz) % 2 == 0 ? Material.SMOOTH_QUARTZ : Material.QUARTZ_BRICKS;
                else if (Math.abs(dx) <= 1 || Math.abs(dz) <= 1) top = Material.SMOOTH_SANDSTONE;
                else top = Material.GRASS_BLOCK;
                put(l, w, cx + dx, ty, cz + dz, bd(top));
                for (int dy = 1; dy <= 5; dy++) put(l, w, cx + dx, ty + dy, cz + dz, bd(Material.AIR));
                if (d > R - 0.5) {
                    put(l, w, cx + dx, ty + 1, cz + dz, bd(Material.SPRUCE_FENCE));
                    if ((Math.abs(dx) + Math.abs(dz)) % 7 == 0) put(l, w, cx + dx, ty + 2, cz + dz, bd(Material.LANTERN));
                } else if (top == Material.GRASS_BLOCK && r.nextInt(9) == 0) {
                    Material[] flowers = {Material.POPPY, Material.DANDELION, Material.OXEYE_DAISY, Material.CORNFLOWER, Material.SHORT_GRASS};
                    put(l, w, cx + dx, ty + 1, cz + dz, bd(flowers[r.nextInt(flowers.length)]));
                }
            }
        put(l, w, cx, ty, cz, bd(Material.SEA_LANTERN));
        return l;
    }

    /** Practice-Arena wie ein kleines Kolosseum (Radius 16). */
    public static List<Change> practiceArena(Location stand) {
        List<Change> l = new ArrayList<>();
        World w = stand.getWorld();
        int cx = stand.getBlockX(), fy = stand.getBlockY() - 1, cz = stand.getBlockZ();
        Random r = new Random(3);
        int R = 16;
        for (int dx = -R - 2; dx <= R + 2; dx++)
            for (int dz = -R - 2; dz <= R + 2; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > R + 1.5) continue;
                put(l, w, cx + dx, fy - 1, cz + dz, bd(Material.SANDSTONE));
                put(l, w, cx + dx, fy, cz + dz, bd(r.nextInt(12) == 0 ? Material.SPRUCE_PLANKS : Material.SMOOTH_SANDSTONE));
                for (int dy = 1; dy <= 7; dy++) {
                    Material m = Material.AIR;
                    if (d > R - 0.5) {
                        boolean window = dy >= 3 && dy <= 4 && (Math.round(Math.toDegrees(Math.atan2(dz, dx))) % 20 + 20) % 20 < 4;
                        m = dy == 7 ? ((Math.abs(dx) + Math.abs(dz)) % 2 == 0 ? Material.CUT_SANDSTONE : Material.AIR)
                                : window ? Material.AIR : (dy == 2 || dy == 5 ? Material.STRIPPED_SPRUCE_LOG : Material.CUT_SANDSTONE);
                    }
                    put(l, w, cx + dx, fy + dy, cz + dz, bd(m));
                }
            }
        return l;
    }

    /** Standard-Teamplattform 7×7 (für Spawn-Stil PLATFORM, falls keine eigene Schematic da ist). */
    public static List<Change> platform(Location stand) {
        List<Change> l = new ArrayList<>();
        World w = stand.getWorld();
        int cx = stand.getBlockX(), fy = stand.getBlockY() - 1, cz = stand.getBlockZ();
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3, corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
                put(l, w, cx + dx, fy, cz + dz, bd(corner || (dx == 0 && dz == 0) ? Material.SEA_LANTERN
                        : edge ? Material.CHISELED_STONE_BRICKS : Material.STONE_BRICKS));
                for (int dy = 1; dy <= 4; dy++) put(l, w, cx + dx, fy + dy, cz + dz, bd(Material.AIR));
            }
        return l;
    }

    /**
     * Varo-Spawn: 1 Block tiefes Loch, Slab-Kreuz drumherum, Pflanzen darüber weg.
     * @param stand Position der Füße im Loch
     */
    public static List<Change> hole(Location stand, BlockData slab) {
        List<Change> l = new ArrayList<>();
        World w = stand.getWorld();
        int x = stand.getBlockX(), y = stand.getBlockY(), z = stand.getBlockZ();
        BlockData air = bd(Material.AIR);
        if (!w.getBlockAt(x, y - 1, z).getType().isSolid()) put(l, w, x, y - 1, z, bd(Material.DIRT));
        put(l, w, x, y, z, air);
        int[][] cross = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] c : cross) put(l, w, x + c[0], y, z + c[1], slab);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                for (int dy = 1; dy <= 3; dy++) put(l, w, x + dx, y + dy, z + dz, air);
        return l;
    }
}
