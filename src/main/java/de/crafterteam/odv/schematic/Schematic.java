package de.crafterteam.odv.schematic;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Klassisches MCEdit-/WorldEdit-6-.schematic (1.8).
 *
 * "Origin" = Stelle relativ zur kleinsten Ecke, an der später der Spieler steht.
 * - Mit WEOffset (WorldEdit //copy oder /odv schem save): genau der Kopierpunkt.
 * - Ohne Offset: horizontal mittig, auf dem ersten festen Boden mit 2 Luftblöcken darüber.
 */
public class Schematic {

    public static final int MAX_VOLUME = 500000;

    private final int width, height, length;
    private final short[] blocks;
    private final byte[] data;
    private final int offX, offY, offZ;

    public Schematic(int width, int height, int length, short[] blocks, byte[] data, int offX, int offY, int offZ) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.blocks = blocks;
        this.data = data;
        this.offX = offX;
        this.offY = offY;
        this.offZ = offZ;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getLength() { return length; }
    public int getVolume() { return width * height * length; }

    private int index(int x, int y, int z) {
        return (y * length + z) * width + x;
    }

    // ---------------- Laden / Speichern ----------------

    public static Schematic load(File file) throws IOException {
        if (!file.isFile()) throw new IOException("Datei " + file.getName() + " nicht gefunden.");
        if (file.getName().toLowerCase().endsWith(".schem")) {
            throw new IOException(".schem (WorldEdit 7) wird nicht unterstützt – bitte .schematic aus 1.8 verwenden.");
        }
        DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(file))));
        try {
            Map<String, Object> root = Nbt.readRoot(in);
            if (!(root.get("Blocks") instanceof byte[])) {
                throw new IOException("Kein klassisches .schematic-Format (MCEdit/WorldEdit 6).");
            }
            int w = Nbt.asInt(root.get("Width"), 0);
            int h = Nbt.asInt(root.get("Height"), 0);
            int l = Nbt.asInt(root.get("Length"), 0);
            if (w <= 0 || h <= 0 || l <= 0 || (long) w * h * l > MAX_VOLUME) {
                throw new IOException("Ungültige Größe " + w + "x" + h + "x" + l + " (max. " + MAX_VOLUME + " Blöcke).");
            }
            byte[] ids = (byte[]) root.get("Blocks");
            if (ids.length < w * h * l) throw new IOException("Blockdaten unvollständig.");
            byte[] dat = root.get("Data") instanceof byte[] ? (byte[]) root.get("Data") : new byte[ids.length];
            byte[] add = root.get("AddBlocks") instanceof byte[] ? (byte[]) root.get("AddBlocks") : null;

            short[] full = new short[w * h * l];
            for (int i = 0; i < full.length; i++) {
                int id = ids[i] & 0xFF;
                if (add != null && (i >> 1) < add.length) {
                    int nibble = (i & 1) == 0 ? (add[i >> 1] & 0x0F) : ((add[i >> 1] & 0xF0) >> 4);
                    id |= nibble << 8;
                }
                full[i] = (short) id;
            }
            byte[] d2 = new byte[full.length];
            System.arraycopy(dat, 0, d2, 0, Math.min(dat.length, d2.length));

            if (root.containsKey("WEOffsetX")) {
                return new Schematic(w, h, l, full, d2,
                        -Nbt.asInt(root.get("WEOffsetX"), 0),
                        -Nbt.asInt(root.get("WEOffsetY"), 0),
                        -Nbt.asInt(root.get("WEOffsetZ"), 0));
            }
            Schematic tmp = new Schematic(w, h, l, full, d2, w / 2, 0, l / 2);
            return new Schematic(w, h, l, full, d2, w / 2, tmp.findStandY(), l / 2);
        } finally {
            in.close();
        }
    }

    /** Erste Höhe (von oben) in der Mittelsäule mit festem Boden und 2 Luftblöcken darüber. */
    @SuppressWarnings("deprecation")
    private int findStandY() {
        int mx = width / 2, mz = length / 2;
        for (int y = height - 3; y >= 0; y--) {
            Material m = Material.getMaterial(blocks[index(mx, y, mz)]);
            boolean solid = m != null && m.isSolid();
            boolean air1 = y + 1 >= height || blocks[index(mx, y + 1, mz)] == 0;
            boolean air2 = y + 2 >= height || blocks[index(mx, y + 2, mz)] == 0;
            if (solid && air1 && air2) return y + 1;
        }
        return 0;
    }

    public void save(File file) throws IOException {
        file.getParentFile().mkdirs();
        byte[] ids = new byte[blocks.length];
        byte[] add = null;
        for (int i = 0; i < blocks.length; i++) {
            ids[i] = (byte) (blocks[i] & 0xFF);
            if (blocks[i] > 255) {
                if (add == null) add = new byte[(blocks.length >> 1) + 1];
                int nibble = (blocks[i] >> 8) & 0x0F;
                add[i >> 1] = (byte) ((i & 1) == 0 ? (add[i >> 1] & 0xF0) | nibble : (add[i >> 1] & 0x0F) | (nibble << 4));
            }
        }
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("Width", (short) width);
        root.put("Height", (short) height);
        root.put("Length", (short) length);
        root.put("Materials", "Alpha");
        root.put("Blocks", ids);
        root.put("Data", data);
        if (add != null) root.put("AddBlocks", add);
        root.put("Entities", new ArrayList<Object>());
        root.put("TileEntities", new ArrayList<Object>());
        root.put("WEOriginX", 0);
        root.put("WEOriginY", 0);
        root.put("WEOriginZ", 0);
        root.put("WEOffsetX", -offX);
        root.put("WEOffsetY", -offY);
        root.put("WEOffsetZ", -offZ);
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(file))));
        try {
            Nbt.writeRoot(out, "Schematic", root);
        } finally {
            out.close();
        }
    }

    /** Kopiert eine Region. origin = Stelle, an der später der Spieler steht. */
    @SuppressWarnings("deprecation")
    public static Schematic copy(Location a, Location b, Location origin) {
        World w = a.getWorld();
        int minX = Math.min(a.getBlockX(), b.getBlockX()), maxX = Math.max(a.getBlockX(), b.getBlockX());
        int minY = Math.min(a.getBlockY(), b.getBlockY()), maxY = Math.max(a.getBlockY(), b.getBlockY());
        int minZ = Math.min(a.getBlockZ(), b.getBlockZ()), maxZ = Math.max(a.getBlockZ(), b.getBlockZ());
        int wd = maxX - minX + 1, h = maxY - minY + 1, l = maxZ - minZ + 1;
        short[] ids = new short[wd * h * l];
        byte[] dat = new byte[wd * h * l];
        for (int y = 0; y < h; y++)
            for (int z = 0; z < l; z++)
                for (int x = 0; x < wd; x++) {
                    Block bl = w.getBlockAt(minX + x, minY + y, minZ + z);
                    int i = (y * l + z) * wd + x;
                    ids[i] = (short) bl.getTypeId();
                    dat[i] = bl.getData();
                }
        return new Schematic(wd, h, l, ids, dat,
                origin.getBlockX() - minX, origin.getBlockY() - minY, origin.getBlockZ() - minZ);
    }

    /**
     * Standard-Teamplattform 7x5x7: Steinziegel-Boden mit Rand, Seelaternen, 4 Blöcke Luft darüber
     * (räumt Bäume/Gras weg). Der Spieler steht mittig auf dem Boden.
     */
    public static Schematic defaultPlatform() {
        int w = 7, h = 5, l = 7;
        short[] ids = new short[w * h * l];
        byte[] dat = new byte[w * h * l];
        for (int z = 0; z < l; z++)
            for (int x = 0; x < w; x++) {
                int i = z * w + x; // y = 0
                int dx = Math.abs(x - 3), dz = Math.abs(z - 3);
                boolean edge = dx == 3 || dz == 3;
                ids[i] = 98;                     // Steinziegel
                dat[i] = (byte) (edge ? 3 : 0);  // Rand: gemeißelt
                if ((dx == 3 && dz == 3) || (dx == 0 && dz == 0)) {
                    ids[i] = 169;                // Seelaterne
                    dat[i] = 0;
                }
            }
        return new Schematic(w, h, l, ids, dat, 3, 1, 3);
    }

    /**
     * Blockänderungen, damit der Origin genau auf target liegt.
     * Unterste Schicht zuerst, damit Fackeln/Leitern Halt haben.
     */
    public List<PasteQueue.Change> createChanges(Location target, boolean pasteAir) {
        List<PasteQueue.Change> changes = new ArrayList<PasteQueue.Change>(getVolume());
        World w = target.getWorld();
        int bx = target.getBlockX() - offX, by = target.getBlockY() - offY, bz = target.getBlockZ() - offZ;
        for (int y = 0; y < height; y++) {
            int wy = by + y;
            if (wy < 0 || wy > 255) continue;
            for (int z = 0; z < length; z++)
                for (int x = 0; x < width; x++) {
                    int i = index(x, y, z);
                    if (!pasteAir && blocks[i] == 0) continue;
                    changes.add(new PasteQueue.Change(w, bx + x, wy, bz + z, blocks[i], data[i]));
                }
        }
        return changes;
    }

    /** Höhe über dem Origin (für die Platzwahl). */
    public int getHeightAboveOrigin() {
        return height - offY;
    }
}
