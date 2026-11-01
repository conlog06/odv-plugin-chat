package de.crafterteam.odv.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Actionbar, Titles und Tablist-Header/Footer für 1.8.x per Reflection.
 */
public final class Nms {

    private static String version;
    private static Class<?> componentClass;
    private static Method serializer;
    private static Constructor<?> chatPacket;
    private static boolean actionbarFailed, tabFailed;

    private Nms() {
    }

    private static void init() throws Exception {
        if (serializer != null) return;
        String pkg = Bukkit.getServer().getClass().getPackage().getName();
        version = pkg.substring(pkg.lastIndexOf('.') + 1);
        componentClass = nms("IChatBaseComponent");
        Class<?> serial;
        try {
            serial = nms("IChatBaseComponent$ChatSerializer");
        } catch (ClassNotFoundException e) {
            serial = nms("ChatSerializer");
        }
        serializer = serial.getMethod("a", String.class);
        chatPacket = nms("PacketPlayOutChat").getConstructor(componentClass, byte.class);
    }

    private static Class<?> nms(String name) throws ClassNotFoundException {
        return Class.forName("net.minecraft.server." + version + "." + name);
    }

    private static Object component(String text) throws Exception {
        return serializer.invoke(null, "{\"text\":\"" + escape(text) + "\"}");
    }

    public static void actionBar(Player p, String text) {
        if (actionbarFailed || p == null) return;
        try {
            init();
            send(p, chatPacket.newInstance(component(text), (byte) 2));
        } catch (Throwable t) {
            actionbarFailed = true;
            Bukkit.getLogger().warning("[ODVMeetup] Actionbar nicht verfügbar: " + t);
        }
    }

    public static void tab(Player p, String header, String footer) {
        if (tabFailed || p == null) return;
        try {
            init();
            Class<?> packetClass = nms("PacketPlayOutPlayerListHeaderFooter");
            Object packet = packetClass.getConstructor(componentClass).newInstance(component(header));
            Field b = packetClass.getDeclaredField("b");
            b.setAccessible(true);
            b.set(packet, component(footer));
            send(p, packet);
        } catch (Throwable t) {
            tabFailed = true;
            Bukkit.getLogger().warning("[ODVMeetup] Tablist-Header nicht verfügbar: " + t);
        }
    }

    @SuppressWarnings("deprecation")
    public static void title(Player p, String title, String subtitle) {
        try {
            p.sendTitle(title, subtitle);
        } catch (Throwable ignored) {
            p.sendMessage(title + " §8- " + subtitle);
        }
    }

    private static void send(Player p, Object packet) throws Exception {
        Object handle = p.getClass().getMethod("getHandle").invoke(p);
        Object conn = handle.getClass().getField("playerConnection").get(handle);
        conn.getClass().getMethod("sendPacket", nms("Packet")).invoke(conn, packet);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
