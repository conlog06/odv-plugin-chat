package de.crafterteam.odv.util;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.team.MTeam;
import org.bukkit.command.CommandSender;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Discord-Webhook: Rundenergebnisse und Changelogs (läuft komplett asynchron). */
public class Discord {

    private final OdvPlugin plugin;
    private HttpClient client;

    public Discord(OdvPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        String url = plugin.settings().discordWebhook;
        return url != null && url.startsWith("https://");
    }

    public void roundResult(MTeam winner, String titleLegacy, List<Map.Entry<UUID, Integer>> top, int seconds) {
        if (!enabled() || !plugin.settings().discordResults) return;
        StringBuilder desc = new StringBuilder();
        desc.append("**").append(strip(titleLegacy)).append("**\\n");
        if (winner != null && !plugin.settings().isSolo()) {
            desc.append("Team: ");
            int i = 0;
            for (UUID u : winner.getMembers()) desc.append(i++ > 0 ? ", " : "").append(esc(plugin.stats().name(u)));
            desc.append("\\n");
        }
        desc.append("Dauer: ").append(Txt.time(seconds)).append("\\n\\n");
        for (int i = 0; i < top.size(); i++) {
            desc.append(i == 0 ? "🥇" : i == 1 ? "🥈" : "🥉").append(' ').append(esc(plugin.stats().name(top.get(i).getKey())))
                    .append(" – ").append(top.get(i).getValue()).append(" Kills\\n");
        }
        send(plugin.settings().mode.display + " beendet", desc.toString(), 0x2ECC71, null);
    }

    public void changelog(String version, List<String> entries, CommandSender feedback) {
        if (!enabled()) {
            if (feedback != null) Txt.send(feedback, plugin.settings().prefix + "§cKein Discord-Webhook in der config.yml (discord.webhook-url).");
            return;
        }
        StringBuilder desc = new StringBuilder();
        for (String e : entries) desc.append("• ").append(esc(e)).append("\\n");
        send("Changelog [" + esc(version) + "]", desc.toString(), 0x3498DB, feedback);
    }

    public void announce(String text, CommandSender feedback) {
        if (!enabled()) {
            if (feedback != null) Txt.send(feedback, plugin.settings().prefix + "§cKein Discord-Webhook in der config.yml.");
            return;
        }
        send("Ankündigung", esc(text), 0xF1C40F, feedback);
    }

    private void send(String title, String descriptionJson, int color, CommandSender feedback) {
        if (client == null) client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        String body = "{\"username\":\"" + esc(strip(plugin.settings().prefix).replace("»", "").trim()) + " System\",\"embeds\":[{"
                + "\"title\":\"" + esc(title) + "\",\"description\":\"" + descriptionJson + "\",\"color\":" + color
                + ",\"footer\":{\"text\":\"" + esc(plugin.settings().serverIp) + "\"},\"timestamp\":\"" + Instant.now() + "\"}]}";
        HttpRequest req = HttpRequest.newBuilder(URI.create(plugin.settings().discordWebhook))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        client.sendAsync(req, HttpResponse.BodyHandlers.discarding()).whenComplete((res, err) -> {
            String msg = err != null ? "§cDiscord nicht erreichbar: " + err.getMessage()
                    : res.statusCode() / 100 == 2 ? "§aAn Discord gesendet." : "§cDiscord-Fehler " + res.statusCode();
            if (err != null || res.statusCode() / 100 != 2) plugin.getLogger().warning(Txt.ascii(msg));
            if (feedback != null && plugin.isEnabled()) plugin.getServer().getScheduler().runTask(plugin, () -> Txt.send(feedback, plugin.settings().prefix + msg));
        });
    }

    private static String strip(String s) {
        return s.replaceAll("§.", "");
    }

    /** JSON-Escape (für Text, der in einen JSON-String kommt). */
    private static String esc(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : strip(s).toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> {
                }
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.toString();
    }
}
