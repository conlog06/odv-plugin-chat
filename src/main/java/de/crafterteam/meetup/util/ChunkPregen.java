package de.crafterteam.meetup.util;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Lädt/generiert alle Chunks im Radius um die Mitte – verteilt über Ticks.
 */
public final class ChunkPregen {

    private static BukkitRunnable task;

    private ChunkPregen() {
    }

    public static boolean isRunning() {
        return task != null;
    }

    public static void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public static void start(Plugin plugin, final World world, int centerX, int centerZ, int radiusBlocks, final Player feedback) {
        stop();
        final int minCX = (centerX - radiusBlocks) >> 4, maxCX = (centerX + radiusBlocks) >> 4;
        final int minCZ = (centerZ - radiusBlocks) >> 4, maxCZ = (centerZ + radiusBlocks) >> 4;
        final int total = (maxCX - minCX + 1) * (maxCZ - minCZ + 1);
        if (feedback != null) feedback.sendMessage("§7Generiere §e" + total + " §7Chunks...");
        task = new BukkitRunnable() {
            int cx = minCX, cz = minCZ, done = 0;

            @Override
            public void run() {
                long start = System.currentTimeMillis();
                while (System.currentTimeMillis() - start < 35) { // max ~35 ms pro Tick
                    if (cx > maxCX) {
                        if (feedback != null && feedback.isOnline()) feedback.sendMessage("§aVorgenerierung fertig (" + total + " Chunks).");
                        stop();
                        return;
                    }
                    world.loadChunk(cx, cz, true);
                    world.unloadChunkRequest(cx, cz, true);
                    done++;
                    cz++;
                    if (cz > maxCZ) {
                        cz = minCZ;
                        cx++;
                    }
                }
                if (feedback != null && feedback.isOnline()) {
                    Nms.actionBar(feedback, "§7Map wird generiert: §a" + (done * 100 / Math.max(1, total)) + "% §8(" + done + "/" + total + ")");
                }
            }
        };
        task.runTaskTimer(plugin, 1L, 1L);
    }
}
