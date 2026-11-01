package de.crafterteam.meetup.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Actionbar & Titles für 1.8.x per Reflection (keine NMS-Abhängigkeit beim Kompilieren).
 */
public final class Nms {

    private static String version;
    private static Constructor<?> chatPacket;
    private static Method serializer;
    private static boolean failed;

    private Nms() {
    }

    private static void init() throws Exception {
        if (chatPacket != null) return;
        String pkg = Bukkit.getServer().getClass().getPackage().getName();
        version = pkg.substring(pkg.lastIndexOf('.') + 1);
        Class<?> base = Class.forName("net.minecraft.server." + version + ".IChatBaseComponent");
        Class<?> serial;
        try {
            serial = Class.forName("net.minecraft.server." + version + ".IChatBaseComponent$ChatSerializer");
        } catch (ClassNotFoundException e) {
            serial = Class.forName("net.minecraft.server." + version + ".ChatSerializer"); // 1.8 R1
        }
        serializer = serial.getMethod("a", String.class);
        chatPacket = Class.forName("net.minecraft.server." + version + ".PacketPlayOutChat").getConstructor(base, byte.class);
    }

    public static void actionBar(Player p, String text) {
        if (failed || p == null) return;
        try {
            init();
            Object comp = serializer.invoke(null, "{\"text\":\"" + escape(text) + "\"}");
            sendPacket(p, chatPacket.newInstance(comp, (byte) 2));
        } catch (Throwable t) {
            failed = true;
            Bukkit.getLogger().warning("[CrafterMeetup] Actionbar nicht verfügbar: " + t);
        }
    }

    @SuppressWarnings("deprecation")
    public static void title(Player p, String title, String subtitle) {
        try {
            p.sendTitle(title, subtitle); // Spigot 1.8.8 API
        } catch (Throwable ignored) {
            p.sendMessage(title + " " + subtitle);
        }
    }

    private static void sendPacket(Player p, Object packet) throws Exception {
        Object handle = p.getClass().getMethod("getHandle").invoke(p);
        Object conn = handle.getClass().getField("playerConnection").get(handle);
        Class<?> packetClass = Class.forName("net.minecraft.server." + version + ".Packet");
        conn.getClass().getMethod("sendPacket", packetClass).invoke(conn, packet);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
