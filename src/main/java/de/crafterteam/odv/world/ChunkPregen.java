package de.crafterteam.odv.world;

import de.crafterteam.odv.util.Txt;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.atomic.AtomicInteger;

/** Generiert alle Chunks im Radius asynchron vor (Paper getChunkAtAsync). */
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

    public static void start(Plugin plugin, World world, int cx, int cz, int radius, Player feedback) {
        stop();
        int minX = (cx - radius) >> 4, maxX = (cx + radius) >> 4, minZ = (cz - radius) >> 4, maxZ = (cz + radius) >> 4;
        int total = (maxX - minX + 1) * (maxZ - minZ + 1);
        AtomicInteger done = new AtomicInteger(), inFlight = new AtomicInteger();
        if (feedback != null) Txt.send(feedback, "§7Generiere §e" + total + " §7Chunks im Hintergrund...");
        task = new BukkitRunnable() {
            int x = minX, z = minZ;

            @Override
            public void run() {
                while (inFlight.get() < 32 && x <= maxX) {
                    inFlight.incrementAndGet();
                    world.getChunkAtAsync(x, z, true).thenAccept(c -> {
                        inFlight.decrementAndGet();
                        done.incrementAndGet();
                    });
                    if (++z > maxZ) {
                        z = minZ;
                        x++;
                    }
                }
                int d = done.get();
                if (feedback != null && feedback.isOnline()) {
                    Txt.actionBar(feedback, "§7Map wird generiert: §a" + (d * 100 / Math.max(1, total)) + "% §8(" + d + "/" + total + ")");
                }
                if (d >= total) {
                    if (feedback != null && feedback.isOnline()) Txt.send(feedback, "§a✔ Map vorgeneriert (" + total + " Chunks).");
                    stop();
                }
            }
        };
        task.runTaskTimer(plugin, 1L, 2L);
    }
}
