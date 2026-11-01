package de.crafterteam.odv.schematic;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;

/** Setzt Blöcke verteilt über Ticks (kein Lag) und merkt sich die alten Blöcke fürs Rückgängigmachen. */
public final class PasteQueue {

    public record Change(World world, int x, int y, int z, BlockData data) {
    }

    private PasteQueue() {
    }

    /**
     * @param undo     hier landen die vorherigen Blöcke (null = kein Undo)
     * @param progress Prozent 0..100 (optional)
     */
    public static void run(Plugin plugin, List<Change> changes, List<Change> undo, int perTick, IntConsumer progress, Runnable done) {
        new BukkitRunnable() {
            int pos = 0, tick = 0;

            @Override
            public void run() {
                int end = Math.min(changes.size(), pos + perTick);
                for (; pos < end; pos++) {
                    Change c = changes.get(pos);
                    if (c.y() < c.world().getMinHeight() || c.y() >= c.world().getMaxHeight()) continue;
                    Block b = c.world().getBlockAt(c.x(), c.y(), c.z());
                    BlockData old = b.getBlockData();
                    if (old.equals(c.data())) continue;
                    if (undo != null) undo.add(new Change(c.world(), c.x(), c.y(), c.z(), old));
                    b.setBlockData(c.data(), false);
                }
                if (progress != null && (++tick % 10 == 0 || pos >= changes.size()) && !changes.isEmpty()) {
                    progress.accept(pos * 100 / changes.size());
                }
                if (pos >= changes.size()) {
                    cancel();
                    if (done != null) done.run();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public static void undo(Plugin plugin, List<Change> undo, int perTick, Runnable done) {
        List<Change> reversed = new ArrayList<>(undo);
        Collections.reverse(reversed);
        undo.clear();
        run(plugin, reversed, null, perTick, null, done);
    }
}
