package de.crafterteam.meetup.schematic;

import org.bukkit.Location;
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
 * MCEdit-.schematic (Format von WorldEdit 6 / 1.8). Kompatibel mit //copy + //schem save.
 *
 * Der "Offset" ist die Position des Kopierpunktes relativ zur kleinsten Ecke (wie WEOffset).
 * Beim Einfügen landet der Kopierpunkt genau auf der Zielposition -> dort steht der Spieler.
 */
public class Schematic {

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
        DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(file))));
        try {
            Map<String, Object> root = Nbt.readRoot(in);
            if (!root.containsKey("Blocks")) {
                throw new IOException("Nur das alte .schematic-Format (MCEdit/WorldEdit 6) wird unterstützt.");
            }
            int w = Nbt.asInt(root.get("Width"), 0);
            int h = Nbt.asInt(root.get("Height"), 0);
            int l = Nbt.asInt(root.get("Length"), 0);
            byte[] ids = (byte[]) root.get("Blocks");
            byte[] dat = root.get("Data") instanceof byte[] ? (byte[]) root.get("Data") : new byte[ids.length];
            byte[] add = root.get("AddBlocks") instanceof byte[] ? (byte[]) root.get("AddBlocks") : null;

            short[] full = new short[ids.length];
            for (int i = 0; i < ids.length; i++) {
                int id = ids[i] & 0xFF;
                if (add != null && (i >> 1) < add.length) {
                    int nibble = (i & 1) == 0 ? (add[i >> 1] & 0x0F) : ((add[i >> 1] & 0xF0) >> 4);
                    id |= nibble << 8;
                }
                full[i] = (short) id;
            }
            // WEOffset ist negativ (min - origin) -> wir speichern origin - min
            int ox = -Nbt.asInt(root.get("WEOffsetX"), -(w / 2));
            int oy = -Nbt.asInt(root.get("WEOffsetY"), 0);
            int oz = -Nbt.asInt(root.get("WEOffsetZ"), -(l / 2));
            return new Schematic(w, h, l, full, dat, ox, oy, oz);
        } finally {
            in.close();
        }
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
     * Baut die Liste der Blockänderungen, damit der Kopierpunkt auf target liegt.
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
}
