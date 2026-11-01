package de.crafterteam.odv.world;

import de.crafterteam.odv.OdvPlugin;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

/**
 * Normale Vanilla-Welt – aber mit zusätzlichen Diamant- und Goldadern (Rate in der Config).
 */
public class ArenaGenerator extends ChunkGenerator {

    private final OdvPlugin plugin;

    public ArenaGenerator(OdvPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public boolean shouldGenerateNoise() { return true; }
    @Override public boolean shouldGenerateSurface() { return true; }
    @Override public boolean shouldGenerateCaves() { return true; }
    @Override public boolean shouldGenerateDecorations() { return true; }
    @Override public boolean shouldGenerateMobs() { return true; }
    @Override public boolean shouldGenerateStructures() { return true; }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return List.of(new OrePopulator(plugin));
    }

    /** Zusätzliche Erzadern. Läuft im Weltgenerator-Thread – nur LimitedRegion benutzen. */
    public static class OrePopulator extends BlockPopulator {

        private final OdvPlugin plugin;

        public OrePopulator(OdvPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public void populate(WorldInfo info, Random random, int chunkX, int chunkZ, LimitedRegion region) {
            if (info.getEnvironment() != World.Environment.NORMAL) return;
            double dia = plugin.settings().diamondRate, gold = plugin.settings().goldRate;
            veins(random, chunkX, chunkZ, region, (dia - 1.0) * 3.0, -60, 14, Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, 3, 8);
            veins(random, chunkX, chunkZ, region, (gold - 1.0) * 2.0, -60, 32, Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, 4, 9);
        }

        private void veins(Random r, int cx, int cz, LimitedRegion region, double amount, int minY, int maxY,
                           Material stoneOre, Material deepOre, int minSize, int maxSize) {
            if (amount <= 0) return;
            int count = (int) amount + (r.nextDouble() < amount - (int) amount ? 1 : 0);
            for (int v = 0; v < count; v++) {
                int x = (cx << 4) + r.nextInt(16);
                int z = (cz << 4) + r.nextInt(16);
                // dreiecksverteilt, tiefer = häufiger
                int y = minY + Math.min(r.nextInt(maxY - minY + 1), r.nextInt(maxY - minY + 1));
                int size = minSize + r.nextInt(maxSize - minSize + 1);
                for (int i = 0; i < size; i++) {
                    if (region.isInRegion(x, y, z)) {
                        Material m = region.getType(x, y, z);
                        if (m == Material.STONE || m == Material.GRANITE || m == Material.DIORITE || m == Material.ANDESITE) {
                            region.setType(x, y, z, stoneOre);
                        } else if (m == Material.DEEPSLATE || m == Material.TUFF) {
                            region.setType(x, y, z, deepOre);
                        }
                    }
                    switch (r.nextInt(6)) {
                        case 0 -> x++;
                        case 1 -> x--;
                        case 2 -> y++;
                        case 3 -> y--;
                        case 4 -> z++;
                        default -> z--;
                    }
                }
            }
        }
    }
}
