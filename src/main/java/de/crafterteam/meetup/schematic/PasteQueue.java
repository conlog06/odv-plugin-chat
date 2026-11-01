package de.crafterteam.meetup.schematic;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Setzt Blöcke verteilt über mehrere Ticks (kein Lag-Spike) und merkt sich die alten Blöcke für Undo.
 */
public class PasteQueue {

    public static class Change {
        final World world;
        final int x, y, z;
        final int id;
        final byte data;

        public Change(World world, int x, int y, int z, int id, byte data) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.id = id;
            this.data = data;
        }
    }

    public interface Progress {
        void update(int done, int total);
    }

    /**
     * @param undo  hier landen die vorherigen Blöcke (null = kein Undo)
     * @param done  wird im Main-Thread aufgerufen, wenn alles gesetzt ist
     */
    public static void run(Plugin plugin, final List<Change> changes, final List<Change> undo,
                           final int perTick, final Progress progress, final Runnable done) {
        new BukkitRunnable() {
            int pos = 0;
            int tick = 0;

            @SuppressWarnings("deprecation")
            @Override
            public void run() {
                int end = Math.min(changes.size(), pos + perTick);
                for (; pos < end; pos++) {
                    Change c = changes.get(pos);
                    Block b = c.world.getBlockAt(c.x, c.y, c.z);
                    if (b.getTypeId() == c.id && b.getData() == c.data) continue;
                    if (undo != null) undo.add(new Change(c.world, c.x, c.y, c.z, b.getTypeId(), b.getData()));
                    b.setTypeIdAndData(c.id, c.data, false);
                }
                if (progress != null && (++tick % 10 == 0 || pos >= changes.size())) {
                    progress.update(pos, changes.size());
                }
                if (pos >= changes.size()) {
                    cancel();
                    if (done != null) done.run();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Macht Änderungen rückgängig (rückwärts). */
    public static void undo(Plugin plugin, List<Change> undo, int perTick, Runnable done) {
        List<Change> reversed = new ArrayList<Change>(undo);
        java.util.Collections.reverse(reversed);
        undo.clear();
        run(plugin, reversed, null, perTick, null, done);
    }
}
