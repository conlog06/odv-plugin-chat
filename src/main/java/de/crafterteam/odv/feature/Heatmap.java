package de.crafterteam.odv.feature;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.scheduler.BukkitTask;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kampf-Heatmap mit echtem Gelände:
 * - Gelände wird während der Runde im Hintergrund aus bereits generierten Chunks gelesen (kein Lag)
 * - Treffer werden nach Schaden gewichtet und weich verteilt (Farbverlauf blau → rot)
 * - Kills als Kreuze, Spawn, Start- und End-Border eingezeichnet
 * - Hotspots in der Item-Beschreibung und im Chat, zusätzlich PNG in plugins/ODVMeetup/heatmaps
 */
public class Heatmap {

    private static final int N = 128;

    private final OdvPlugin plugin;
    private final float[] heat = new float[N * N];
    private final int[] terrain = new int[N * N];
    private final List<int[]> kills = new ArrayList<>();
    private int hits;
    private double originX, originZ, blocksPerPixel;
    private World world;
    private BukkitTask scan;

    public Heatmap(OdvPlugin plugin) {
        this.plugin = plugin;
    }

    // ================================================================= Runde

    public void start(World w, Location center, int borderSize) {
        reset();
        world = w;
        blocksPerPixel = Math.max(1, borderSize / (double) N);
        originX = center.getX() - borderSize / 2.0;
        originZ = center.getZ() - borderSize / 2.0;
        java.util.Arrays.fill(terrain, 0xFF1E1E1E);
        scanTerrain();
    }

    public void reset() {
        if (scan != null) scan.cancel();
        scan = null;
        java.util.Arrays.fill(heat, 0f);
        kills.clear();
        hits = 0;
        world = null;
    }

    public boolean hasData() {
        return world != null && (hits > 0 || !kills.isEmpty());
    }

    /** Treffer: Gewicht = Schaden. */
    public void hit(Location at, double damage) {
        if (world == null || !at.getWorld().equals(world)) return;
        hits++;
        splat(px(at.getX()), py(at.getZ()), (float) Math.max(0.5, damage), 3);
    }

    public void kill(Location at) {
        if (world == null || at == null || !at.getWorld().equals(world)) return;
        int x = px(at.getX()), y = py(at.getZ());
        kills.add(new int[]{x, y, at.getBlockX(), at.getBlockZ()});
        splat(x, y, 12f, 4);
    }

    private int px(double worldX) {
        return (int) ((worldX - originX) / blocksPerPixel);
    }

    private int py(double worldZ) {
        return (int) ((worldZ - originZ) / blocksPerPixel);
    }

    private void splat(int cx, int cy, float weight, int r) {
        for (int dx = -r; dx <= r; dx++)
            for (int dy = -r; dy <= r; dy++) {
                int x = cx + dx, y = cy + dy;
                if (x < 0 || y < 0 || x >= N || y >= N) continue;
                float d2 = dx * dx + dy * dy;
                if (d2 > r * r) continue;
                heat[y * N + x] += weight * (float) Math.exp(-d2 / (r * r * 0.5));
            }
    }

    // ================================================================= Gelände (asynchron)

    private void scanTerrain() {
        World w = world;
        int minCX = (int) Math.floor(originX) >> 4, minCZ = (int) Math.floor(originZ) >> 4;
        int maxCX = (int) Math.floor(originX + N * blocksPerPixel) >> 4, maxCZ = (int) Math.floor(originZ + N * blocksPerPixel) >> 4;
        List<int[]> chunks = new ArrayList<>();
        for (int cx = minCX; cx <= maxCX; cx++) for (int cz = minCZ; cz <= maxCZ; cz++) chunks.add(new int[]{cx, cz});
        AtomicInteger next = new AtomicInteger(), inFlight = new AtomicInteger();
        int[] heights = new int[N * N];
        scan = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            while (inFlight.get() < 6 && next.get() < chunks.size()) {
                int[] c = chunks.get(next.getAndIncrement());
                inFlight.incrementAndGet();
                w.getChunkAtAsync(c[0], c[1], false).thenAccept(chunk -> {
                    inFlight.decrementAndGet();
                    if (chunk == null || world != w) return;
                    sample(chunk.getChunkSnapshot(true, false, false), c[0], c[1], heights);
                });
            }
            if (next.get() >= chunks.size() && inFlight.get() == 0) {
                shade(heights);
                if (scan != null) scan.cancel();
                scan = null;
            }
        }, 40L, 2L);
    }

    private void sample(ChunkSnapshot snap, int cx, int cz, int[] heights) {
        for (int y = 0; y < N; y++) {
            double wz = originZ + (y + 0.5) * blocksPerPixel;
            if (((int) Math.floor(wz) >> 4) != cz) continue;
            for (int x = 0; x < N; x++) {
                double wx = originX + (x + 0.5) * blocksPerPixel;
                if (((int) Math.floor(wx) >> 4) != cx) continue;
                int lx = (int) Math.floor(wx) & 15, lz = (int) Math.floor(wz) & 15;
                int h = snap.getHighestBlockYAt(lx, lz);
                BlockData bd = snap.getBlockData(lx, h, lz);
                if (bd.getMaterial().isAir() && h > world.getMinHeight()) bd = snap.getBlockData(lx, --h, lz);
                org.bukkit.Color c = bd.getMapColor();
                terrain[y * N + x] = 0xFF000000 | c.asRGB();
                heights[y * N + x] = h;
            }
        }
    }

    /** Relief wie bei echten Karten: Nordhang dunkler, Südhang heller. */
    private void shade(int[] heights) {
        for (int y = 1; y < N; y++)
            for (int x = 0; x < N; x++) {
                int i = y * N + x, d = heights[i] - heights[i - N];
                float f = d > 0 ? 1.12f : d < 0 ? 0.82f : 1f;
                terrain[i] = scale(terrain[i], f);
            }
    }

    private static int scale(int argb, float f) {
        int r = Math.min(255, (int) (((argb >> 16) & 255) * f)), g = Math.min(255, (int) (((argb >> 8) & 255) * f)), b = Math.min(255, (int) ((argb & 255) * f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // ================================================================= Bild

    private static final int[][] GRADIENT = {{40, 70, 255}, {0, 200, 255}, {60, 230, 60}, {255, 230, 0}, {255, 120, 0}, {230, 20, 20}};

    private static int gradient(float t) {
        t = Math.max(0, Math.min(1, t)) * (GRADIENT.length - 1);
        int i = Math.min(GRADIENT.length - 2, (int) t);
        float f = t - i;
        int[] a = GRADIENT[i], b = GRADIENT[i + 1];
        return 0xFF000000 | ((int) (a[0] + (b[0] - a[0]) * f) << 16) | ((int) (a[1] + (b[1] - a[1]) * f) << 8) | (int) (a[2] + (b[2] - a[2]) * f);
    }

    private static int blend(int base, int over, float alpha) {
        int r = (int) (((base >> 16) & 255) * (1 - alpha) + ((over >> 16) & 255) * alpha);
        int g = (int) (((base >> 8) & 255) * (1 - alpha) + ((over >> 8) & 255) * alpha);
        int b = (int) ((base & 255) * (1 - alpha) + (over & 255) * alpha);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** 128×128 Pixel (ARGB). */
    private int[] render() {
        int[] img = new int[N * N];
        float max = 0;
        for (float v : heat) max = Math.max(max, v);
        for (int i = 0; i < img.length; i++) {
            int base = scale(terrain[i], 0.7f);
            float t = max <= 0 ? 0 : (float) Math.sqrt(heat[i] / max);
            img[i] = t < 0.04f ? base : blend(base, gradient(t), Math.min(0.85f, 0.25f + t * 0.7f));
        }
        // End-Border (gestrichelt weiß) und Rand
        int finalPx = (int) Math.round(plugin.settings().borderFinal / blocksPerPixel / 2);
        int c = N / 2;
        for (int k = -finalPx; k <= finalPx; k++) {
            if ((k & 2) != 0) continue;
            for (int[] p : new int[][]{{c + k, c - finalPx}, {c + k, c + finalPx}, {c - finalPx, c + k}, {c + finalPx, c + k}}) set(img, p[0], p[1], 0xFFFFFFFF);
        }
        for (int k = 0; k < N; k++) {
            set(img, k, 0, 0xFF101010);
            set(img, k, N - 1, 0xFF101010);
            set(img, 0, k, 0xFF101010);
            set(img, N - 1, k, 0xFF101010);
        }
        // Spawn
        for (int k = -2; k <= 2; k++) {
            set(img, c + k, c, 0xFFFFFFFF);
            set(img, c, c + k, 0xFFFFFFFF);
        }
        // Kills: schwarzes Kreuz mit roter Mitte
        for (int[] kl : kills) {
            for (int k = -2; k <= 2; k++) {
                set(img, kl[0] + k, kl[1] + k, 0xFF000000);
                set(img, kl[0] + k, kl[1] - k, 0xFF000000);
            }
            set(img, kl[0], kl[1], 0xFFFF2020);
        }
        // Legende unten links
        for (int x = 0; x < 40; x++) {
            int col = gradient(x / 39f);
            for (int y = N - 6; y < N - 3; y++) set(img, 3 + x, y, col);
        }
        return img;
    }

    private static void set(int[] img, int x, int y, int argb) {
        if (x >= 0 && y >= 0 && x < N && y < N) img[y * N + x] = argb;
    }

    /** Die 3 heißesten 32×32-Block-Zonen. */
    private List<String> hotspots() {
        int cell = Math.max(1, (int) Math.round(32 / blocksPerPixel));
        int cells = (N + cell - 1) / cell;
        float[] sum = new float[cells * cells];
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) sum[(y / cell) * cells + x / cell] += heat[y * N + x];
        List<String> out = new ArrayList<>();
        boolean[] used = new boolean[sum.length];
        for (int k = 0; k < 3; k++) {
            int best = -1;
            for (int i = 0; i < sum.length; i++) if (!used[i] && sum[i] > 0 && (best < 0 || sum[i] > sum[best])) best = i;
            if (best < 0) break;
            used[best] = true;
            int wx = (int) (originX + ((best % cells) * cell + cell / 2.0) * blocksPerPixel);
            int wz = (int) (originZ + ((best / cells) * cell + cell / 2.0) * blocksPerPixel);
            out.add((k == 0 ? "§c" : k == 1 ? "§6" : "§e") + "#" + (k + 1) + " §7bei §fX " + wx + " §7/ §fZ " + wz);
        }
        return out;
    }

    // ================================================================= Ausgabe

    public void give(long delayTicks) {
        if (!hasData()) return;
        if (scan != null) { // Gelände noch nicht fertig -> mit dem arbeiten, was da ist
            scan.cancel();
            scan = null;
        }
        int[] img = render();
        List<String> spots = hotspots();
        World w = world;
        MapView view = Bukkit.createMap(Bukkit.getWorlds().get(0)); // Hauptwelt: bleibt auch nach Welt-Reset gültig
        view.getRenderers().clear();
        view.setTrackingPosition(false);
        view.addRenderer(new MapRenderer() {
            boolean done;

            @Override
            public void render(MapView map, MapCanvas canvas, Player player) {
                if (done) return;
                done = true;
                for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) canvas.setPixelColor(x, y, new Color(img[y * N + x]));
            }
        });
        ItemStack map = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) map.getItemMeta();
        meta.setMapView(view);
        meta.displayName(Txt.item("§6§lKampf-Heatmap"));
        List<String> lore = new ArrayList<>();
        lore.add("§7Treffer: §f" + hits + " §8| §7Kills: §c" + kills.size());
        lore.add("");
        lore.add("§7Hotspots:");
        lore.addAll(spots);
        lore.add("");
        lore.add("§9blau §7wenig §8→ §cRot §7viel Kampf");
        lore.add("§8✕ Kill · + Spawn · ┅ End-Border");
        meta.lore(Txt.items(lore));
        map.setItemMeta(meta);
        savePng(img);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) p.getInventory().setItem(6, map.clone());
            plugin.game().broadcast("§6§lKampf-Heatmap §7liegt in deinem Inventar." + (spots.isEmpty() ? "" : " §7Hotspot: " + spots.get(0)));
        }, delayTicks);
    }

    private void savePng(int[] img) {
        File dir = new File(plugin.getDataFolder(), "heatmaps");
        String name = "heatmap-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".png";
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                dir.mkdirs();
                int scaleUp = 4;
                BufferedImage out = new BufferedImage(N * scaleUp, N * scaleUp, BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < N * scaleUp; y++) for (int x = 0; x < N * scaleUp; x++) out.setRGB(x, y, img[(y / scaleUp) * N + x / scaleUp]);
                ImageIO.write(out, "png", new File(dir, name));
            } catch (IOException e) {
                plugin.getLogger().warning("Heatmap-PNG: " + e.getMessage());
            }
        });
    }
}
