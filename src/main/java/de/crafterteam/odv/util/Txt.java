package de.crafterteam.odv.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Texte mit §-Farbcodes -> Adventure-Components. */
public final class Txt {

    /** §-Codes inkl. Hex-Farben (§x§R§R§G§G§B§B) – damit Farbverläufe überall funktionieren. */
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private static final net.kyori.adventure.text.minimessage.MiniMessage MINI = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();

    /** MiniMessage (&lt;gradient&gt;…) oder alte &amp;-Codes -> Component. */
    public static Component rich(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        if (text.indexOf('<') >= 0 && text.indexOf('>') > text.indexOf('<')) return MINI.deserialize(text);
        return c(color(text));
    }

    public static String legacy(Component c) {
        return LEGACY.serialize(c);
    }

    private Txt() {
    }

    public static Component c(String legacy) {
        return LEGACY.deserialize(legacy == null ? "" : legacy);
    }

    /** Für Item-Namen/-Lore: ohne Kursiv. */
    public static Component item(String legacy) {
        return c(legacy).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> items(List<String> lines) {
        List<Component> out = new ArrayList<>();
        for (String s : lines) out.add(item(s));
        return out;
    }

    public static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    public static String color(String amp) {
        return ChatColor.translateAlternateColorCodes('&', amp == null ? "" : amp);
    }

    public static void send(CommandSender to, String msg) {
        to.sendMessage(c(msg));
    }

    public static void actionBar(Player p, String msg) {
        p.sendActionBar(c(msg));
    }

    public static void title(Player p, String title, String sub) {
        p.showTitle(Title.title(c(title), c(sub),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1600), Duration.ofMillis(400))));
    }

    /** Für die Konsole (Windows zeigt Umlaute/Sonderzeichen oft falsch an). */
    public static String ascii(String s) {
        return s.replaceAll("§.", "").replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("Ä", "Ae")
                .replace("Ö", "Oe").replace("Ü", "Ue").replace("ß", "ss").replace("–", "-").replace("→", "->")
                .replace("✔", "OK").replace("×", "x").replaceAll("[^\\x00-\\x7F]", "");
    }

    public static String time(int seconds) {
        seconds = Math.max(0, seconds);
        if (seconds >= 3600) return seconds / 3600 + "h " + (seconds % 3600) / 60 + "m";
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }
}
