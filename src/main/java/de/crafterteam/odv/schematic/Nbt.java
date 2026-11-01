package de.crafterteam.odv.schematic;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimaler NBT-Reader/Writer (genug für MCEdit/WorldEdit-.schematic-Dateien).
 * Compounds werden als Map, Listen als List, Arrays als byte[]/int[] abgebildet.
 */
public final class Nbt {

    public static final byte END = 0, BYTE = 1, SHORT = 2, INT = 3, LONG = 4, FLOAT = 5, DOUBLE = 6,
            BYTE_ARRAY = 7, STRING = 8, LIST = 9, COMPOUND = 10, INT_ARRAY = 11, LONG_ARRAY = 12;

    private Nbt() {
    }

    /** Liest das Root-Compound (Name wird ignoriert). */
    public static Map<String, Object> readRoot(DataInputStream in) throws IOException {
        byte type = in.readByte();
        if (type != COMPOUND) throw new IOException("Keine gültige NBT-Datei (Root ist kein Compound)");
        in.readUTF();
        return readCompound(in);
    }

    private static Map<String, Object> readCompound(DataInputStream in) throws IOException {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        while (true) {
            byte type = in.readByte();
            if (type == END) return map;
            String name = in.readUTF();
            map.put(name, readPayload(in, type));
        }
    }

    private static Object readPayload(DataInputStream in, byte type) throws IOException {
        switch (type) {
            case BYTE: return in.readByte();
            case SHORT: return in.readShort();
            case INT: return in.readInt();
            case LONG: return in.readLong();
            case FLOAT: return in.readFloat();
            case DOUBLE: return in.readDouble();
            case BYTE_ARRAY: {
                byte[] b = new byte[in.readInt()];
                in.readFully(b);
                return b;
            }
            case STRING: return in.readUTF();
            case LIST: {
                byte elemType = in.readByte();
                int len = in.readInt();
                List<Object> list = new ArrayList<Object>(Math.max(0, len));
                for (int i = 0; i < len; i++) list.add(readPayload(in, elemType));
                return list;
            }
            case COMPOUND: return readCompound(in);
            case INT_ARRAY: {
                int[] a = new int[in.readInt()];
                for (int i = 0; i < a.length; i++) a[i] = in.readInt();
                return a;
            }
            case LONG_ARRAY: {
                long[] a = new long[in.readInt()];
                for (int i = 0; i < a.length; i++) a[i] = in.readLong();
                return a;
            }
            default:
                throw new IOException("Unbekannter NBT-Typ " + type);
        }
    }

    // ---------------- Schreiben (nur die Typen, die wir brauchen) ----------------

    public static void writeRoot(DataOutputStream out, String name, Map<String, Object> root) throws IOException {
        out.writeByte(COMPOUND);
        out.writeUTF(name);
        writeCompound(out, root);
    }

    private static void writeCompound(DataOutputStream out, Map<String, Object> map) throws IOException {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            Object v = e.getValue();
            byte type = typeOf(v);
            out.writeByte(type);
            out.writeUTF(e.getKey());
            writePayload(out, v, type);
        }
        out.writeByte(END);
    }

    @SuppressWarnings("unchecked")
    private static void writePayload(DataOutputStream out, Object v, byte type) throws IOException {
        switch (type) {
            case BYTE: out.writeByte((Byte) v); break;
            case SHORT: out.writeShort((Short) v); break;
            case INT: out.writeInt((Integer) v); break;
            case STRING: out.writeUTF((String) v); break;
            case BYTE_ARRAY: {
                byte[] b = (byte[]) v;
                out.writeInt(b.length);
                out.write(b);
                break;
            }
            case LIST: {
                List<Object> list = (List<Object>) v;
                byte elem = list.isEmpty() ? COMPOUND : typeOf(list.get(0));
                out.writeByte(elem);
                out.writeInt(list.size());
                for (Object o : list) writePayload(out, o, elem);
                break;
            }
            case COMPOUND: writeCompound(out, (Map<String, Object>) v); break;
            case INT_ARRAY: {
                int[] a = (int[]) v;
                out.writeInt(a.length);
                for (int x : a) out.writeInt(x);
                break;
            }
            default: throw new IOException("Typ nicht unterstützt: " + type);
        }
    }

    private static byte typeOf(Object v) {
        if (v instanceof Byte) return BYTE;
        if (v instanceof Short) return SHORT;
        if (v instanceof Integer) return INT;
        if (v instanceof String) return STRING;
        if (v instanceof byte[]) return BYTE_ARRAY;
        if (v instanceof int[]) return INT_ARRAY;
        if (v instanceof List) return LIST;
        if (v instanceof Map) return COMPOUND;
        throw new IllegalArgumentException("NBT-Typ nicht unterstützt: " + v);
    }

    public static int asInt(Object o, int def) {
        return o instanceof Number ? ((Number) o).intValue() : def;
    }
}
