package de.crafterteam.odv.schematic;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Sponge-Schematic (.schem, Version 2 und 3) – das Format von WorldEdit 7 / FAWE.
 * "Origin" = Stelle (relativ zur kleinsten Ecke), an der später der Spieler steht.
 */
public class Schematic {

    public static final int MAX_VOLUME = 2_000_000;

    private final int width, height, length;
    private final BlockData[] blocks;
    private final int ox, oy, oz;

    public Schematic(int width, int height, int length, BlockData[] blocks, int ox, int oy, int oz) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.blocks = blocks;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getLength() { return length; }

    private int index(int x, int y, int z) {
        return (y * length + z) * width + x;
    }

    // ================================================================ Laden

    @SuppressWarnings("unchecked")
    public static Schematic load(File file) throws IOException {
        if (!file.isFile()) throw new IOException("Datei " + file.getName() + " nicht gefunden.");
        Map<String, Object> root;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(file))))) {
            root = Nbt.readRoot(in);
        }
        if (root.get("Schematic") instanceof Map<?, ?> inner) root = (Map<String, Object>) inner; // Version 3
        int version = Nbt.asInt(root.get("Version"), 2);
        if (root.containsKey("Blocks") && root.get("Blocks") instanceof byte[]) {
            throw new IOException("Altes 1.8-Format (.schematic) – bitte in 26.x mit WorldEdit als .schem neu speichern.");
        }
        int w = Nbt.asInt(root.get("Width"), 0), h = Nbt.asInt(root.get("Height"), 0), l = Nbt.asInt(root.get("Length"), 0);
        if (w <= 0 || h <= 0 || l <= 0 || (long) w * h * l > MAX_VOLUME) throw new IOException("Ungültige Größe " + w + "x" + h + "x" + l);

        Map<String, Object> palette;
        byte[] data;
        if (version >= 3) {
            Map<String, Object> b = (Map<String, Object>) root.get("Blocks");
            if (b == null) throw new IOException("Keine Blöcke in der Schematic.");
            palette = (Map<String, Object>) b.get("Palette");
            data = (byte[]) b.get("Data");
        } else {
            palette = (Map<String, Object>) root.get("Palette");
            data = (byte[]) root.get("BlockData");
        }
        if (palette == null || data == null) throw new IOException("Palette/Blockdaten fehlen.");

        Map<Integer, BlockData> byId = new HashMap<>();
        BlockData air = Material.AIR.createBlockData();
        for (Map.Entry<String, Object> e : palette.entrySet()) {
            BlockData bd;
            try {
                bd = Bukkit.createBlockData(e.getKey());
            } catch (IllegalArgumentException ex) {
                bd = air; // unbekannter Block (Mod/ältere Version)
            }
            byId.put(Nbt.asInt(e.getValue(), 0), bd);
        }
        BlockData[] blocks = new BlockData[w * h * l];
        int i = 0, pos = 0;
        while (i < blocks.length && pos < data.length) {
            int value = 0, shift = 0, b;
            do {
                b = data[pos++] & 0xFF;
                value |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0 && pos < data.length);
            BlockData bd = byId.get(value);
            blocks[i++] = bd == null ? air : bd;
        }
        while (i < blocks.length) blocks[i++] = air;

        int[] origin = null;
        if (root.get("Metadata") instanceof Map<?, ?> meta && meta.containsKey("WEOffsetX")) {
            origin = new int[]{-Nbt.asInt(meta.get("WEOffsetX"), 0), -Nbt.asInt(meta.get("WEOffsetY"), 0), -Nbt.asInt(meta.get("WEOffsetZ"), 0)};
        } else if (version >= 3 && root.get("Offset") instanceof int[] off && off.length == 3) {
            origin = new int[]{-off[0], -off[1], -off[2]};
        }
        Schematic tmp = new Schematic(w, h, l, blocks, w / 2, 0, l / 2);
        if (origin == null || origin[0] < -1 || origin[0] > w || origin[2] < -1 || origin[2] > l) {
            origin = new int[]{w / 2, tmp.standY(), l / 2};
        }
        return new Schematic(w, h, l, blocks, origin[0], origin[1], origin[2]);
    }

    /** Erste Höhe in der Mittelsäule mit festem Boden und 2 Luftblöcken darüber. */
    private int standY() {
        int mx = width / 2, mz = length / 2;
        for (int y = height - 1; y >= 0; y--) {
            boolean solid = blocks[index(mx, y, mz)].getMaterial().isSolid();
            boolean a1 = y + 1 >= height || blocks[index(mx, y + 1, mz)].getMaterial().isAir();
            boolean a2 = y + 2 >= height || blocks[index(mx, y + 2, mz)].getMaterial().isAir();
            if (solid && a1 && a2) return y + 1;
        }
        return 0;
    }

    // ================================================================ Speichern (Version 2, mit WE-Metadaten)

    public void save(File file) throws IOException {
        file.getParentFile().mkdirs();
        Map<String, Integer> palette = new LinkedHashMap<>();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (BlockData bd : blocks) {
            String key = bd.getAsString();
            Integer id = palette.get(key);
            if (id == null) {
                id = palette.size();
                palette.put(key, id);
            }
            int v = id;
            while ((v & ~0x7F) != 0) {
                bytes.write((v & 0x7F) | 0x80);
                v >>>= 7;
            }
            bytes.write(v);
        }
        Map<String, Object> pal = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : palette.entrySet()) pal.put(e.getKey(), e.getValue());
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("WEOffsetX", -ox);
        meta.put("WEOffsetY", -oy);
        meta.put("WEOffsetZ", -oz);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("Version", 2);
        root.put("DataVersion", Bukkit.getUnsafe().getDataVersion());
        root.put("Width", (short) width);
        root.put("Height", (short) height);
        root.put("Length", (short) length);
        root.put("Offset", new int[]{0, 0, 0});
        root.put("Metadata", meta);
        root.put("PaletteMax", palette.size());
        root.put("Palette", pal);
        root.put("BlockData", bytes.toByteArray());
        root.put("BlockEntities", new ArrayList<>());
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(file))))) {
            Nbt.writeRoot(out, "Schematic", root);
        }
    }

    /** Region kopieren. origin = Stelle, an der später der Spieler steht. */
    public static Schematic copy(Location a, Location b, Location origin) {
        World w = a.getWorld();
        int minX = Math.min(a.getBlockX(), b.getBlockX()), maxX = Math.max(a.getBlockX(), b.getBlockX());
        int minY = Math.min(a.getBlockY(), b.getBlockY()), maxY = Math.max(a.getBlockY(), b.getBlockY());
        int minZ = Math.min(a.getBlockZ(), b.getBlockZ()), maxZ = Math.max(a.getBlockZ(), b.getBlockZ());
        int wd = maxX - minX + 1, h = maxY - minY + 1, l = maxZ - minZ + 1;
        BlockData[] data = new BlockData[wd * h * l];
        for (int y = 0; y < h; y++)
            for (int z = 0; z < l; z++)
                for (int x = 0; x < wd; x++) data[(y * l + z) * wd + x] = w.getBlockAt(minX + x, minY + y, minZ + z).getBlockData();
        return new Schematic(wd, h, l, data, origin.getBlockX() - minX, origin.getBlockY() - minY, origin.getBlockZ() - minZ);
    }

    public List<PasteQueue.Change> createChanges(Location target, boolean pasteAir) {
        List<PasteQueue.Change> list = new ArrayList<>();
        World w = target.getWorld();
        int bx = target.getBlockX() - ox, by = target.getBlockY() - oy, bz = target.getBlockZ() - oz;
        for (int y = 0; y < height; y++)
            for (int z = 0; z < length; z++)
                for (int x = 0; x < width; x++) {
                    BlockData bd = blocks[index(x, y, z)];
                    if (!pasteAir && bd.getMaterial().isAir()) continue;
                    if (bd.getMaterial() == Material.STRUCTURE_VOID) continue;
                    list.add(new PasteQueue.Change(w, bx + x, by + y, bz + z, bd));
                }
        return list;
    }
}
