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

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

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

    public static String time(int seconds) {
        seconds = Math.max(0, seconds);
        if (seconds >= 3600) return seconds / 3600 + "h " + (seconds % 3600) / 60 + "m";
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }
}
