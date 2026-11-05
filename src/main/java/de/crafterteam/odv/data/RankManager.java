package de.crafterteam.odv.data;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.util.Txt;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Eigenes Rangsystem: Ränge aus ranks.yml, Zuordnung in SQLite/MySQL.
 * Liefert Prefix für Chat/Tab/Nametag, Tab-Reihenfolge und Rechte.
 */
public class RankManager {

    public record Rank(String id, String name, String prefix, NamedTextColor color, int weight, boolean staff, List<String> permissions) {
        /** Farbcode passend zur Rangfarbe (für Namen). */
        public String nameColor() {
            String p = prefix;
            int i = p.lastIndexOf('§');
            return i >= 0 && i + 1 < p.length() ? p.substring(i, i + 2) : "§7";
        }
    }

    private final OdvPlugin plugin;
    private final Map<String, Rank> ranks = new LinkedHashMap<>();
    private final Map<UUID, String> assigned = new ConcurrentHashMap<>();
    private final Map<UUID, Long> mutedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();
    private String defaultRank = "spieler";
    private String table;

    public RankManager(OdvPlugin plugin) {
        this.plugin = plugin;
    }

    // ================================================================= Laden

    public void load() {
        File f = new File(plugin.getDataFolder(), "ranks.yml");
        if (!f.isFile()) plugin.saveResource("ranks.yml", false);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ranks.clear();
        ConfigurationSection sec = y.getConfigurationSection("ranks");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection r = sec.getConfigurationSection(id);
                if (r == null) continue;
                NamedTextColor color = NamedTextColor.NAMES.value(r.getString("color", "gray").toLowerCase(Locale.ROOT));
                ranks.put(id.toLowerCase(Locale.ROOT), new Rank(id.toLowerCase(Locale.ROOT), r.getString("name", id), Txt.color(r.getString("prefix", "&7")),
                        color == null ? NamedTextColor.GRAY : color, r.getInt("weight", 0), r.getBoolean("staff", false), r.getStringList("permissions")));
            }
        }
        defaultRank = y.getString("default-rank", "spieler").toLowerCase(Locale.ROOT);
        if (!ranks.containsKey(defaultRank)) {
            ranks.put(defaultRank, new Rank(defaultRank, "Spieler", "§7", NamedTextColor.GRAY, 0, false, List.of()));
        }
        // nach Gewicht sortieren (höchster zuerst)
        List<Rank> sorted = new ArrayList<>(ranks.values());
        sorted.sort((a, b) -> b.weight() - a.weight());
        ranks.clear();
        for (Rank r : sorted) ranks.put(r.id(), r);
    }

    public void openTable() {
        table = plugin.stats().tablePrefix() + "ranks";
        plugin.stats().async(c -> {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + table
                        + " (uuid VARCHAR(36) NOT NULL PRIMARY KEY, name VARCHAR(16) NOT NULL, rank_id VARCHAR(32) NOT NULL, muted_until BIGINT NOT NULL DEFAULT 0)");
            }
        });
    }

    // ================================================================= Abfragen

    public Map<String, Rank> all() {
        return ranks;
    }

    public Rank byId(String id) {
        return id == null ? null : ranks.get(id.toLowerCase(Locale.ROOT));
    }

    public Rank of(UUID id) {
        Rank r = ranks.get(assigned.getOrDefault(id, defaultRank));
        return r != null ? r : ranks.get(defaultRank);
    }

    public Rank of(Player p) {
        return of(p.getUniqueId());
    }

    public boolean isStaff(Player p) {
        return of(p).staff() || p.hasPermission("odv.staff");
    }

    // ================================================================= Join / Quit

    /** Rang + Mute laden, danach Rechte & Anzeige anwenden. */
    public void load(Player p) {
        UUID id = p.getUniqueId();
        apply(p);
        plugin.stats().async(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT rank_id, muted_until FROM " + table + " WHERE uuid=?")) {
                ps.setString(1, id.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String r = rs.getString(1);
                        if (ranks.containsKey(r)) assigned.put(id, r);
                        long m = rs.getLong(2);
                        if (m > System.currentTimeMillis()) mutedUntil.put(id, m);
                    }
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline()) {
                    apply(p);
                    plugin.game().onRankChanged(p);
                }
            });
        });
    }

    public void unload(Player p) {
        PermissionAttachment a = attachments.remove(p.getUniqueId());
        if (a != null) {
            try {
                p.removeAttachment(a);
            } catch (IllegalArgumentException ignored) {
            }
        }
        assigned.remove(p.getUniqueId());
        mutedUntil.remove(p.getUniqueId());
    }

    /** Rechte des Rangs setzen + Tab-Reihenfolge. */
    public void apply(Player p) {
        Rank r = of(p);
        PermissionAttachment old = attachments.remove(p.getUniqueId());
        if (old != null) {
            try {
                p.removeAttachment(old);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (!r.permissions().isEmpty()) {
            PermissionAttachment a = p.addAttachment(plugin);
            for (String perm : r.permissions()) {
                if (perm.startsWith("-")) a.setPermission(perm.substring(1), false);
                else a.setPermission(perm, true);
            }
            attachments.put(p.getUniqueId(), a);
        }
        p.setPlayerListOrder(Math.max(1, r.weight() + 1));
        p.updateCommands();
    }

    // ================================================================= Setzen

    public void set(CommandSender by, String playerName, String rankId) {
        Rank r = byId(rankId);
        if (r == null) {
            Txt.send(by, plugin.settings().prefix + "§cUnbekannter Rang. §7Ränge: §f" + String.join(", ", ranks.keySet()));
            return;
        }
        Player online = Bukkit.getPlayerExact(playerName);
        var offline = online != null ? online : Bukkit.getOfflinePlayerIfCached(playerName);
        if (offline == null) {
            Txt.send(by, plugin.settings().prefix + "§c" + playerName + " war noch nie auf dem Server.");
            return;
        }
        UUID id = offline.getUniqueId();
        String name = offline.getName() != null ? offline.getName() : playerName;
        if (r.id().equals(defaultRank)) assigned.remove(id);
        else assigned.put(id, r.id());
        upsert(id, name, r.id(), mutedUntil.getOrDefault(id, 0L));
        if (online != null) {
            apply(online);
            plugin.game().onRankChanged(online);
            Txt.send(online, plugin.settings().prefix + "§7Dein Rang ist jetzt " + r.prefix() + r.name() + "§7.");
        }
        Txt.send(by, plugin.settings().prefix + "§f" + name + " §7hat jetzt den Rang " + r.prefix() + r.name() + "§7.");
    }

    private void upsert(UUID id, String name, String rank, long muted) {
        boolean mysql = plugin.stats().isMysql();
        String sql = mysql
                ? "INSERT INTO " + table + " (uuid, name, rank_id, muted_until) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE name=VALUES(name), rank_id=VALUES(rank_id), muted_until=VALUES(muted_until)"
                : "INSERT INTO " + table + " (uuid, name, rank_id, muted_until) VALUES (?, ?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, rank_id=excluded.rank_id, muted_until=excluded.muted_until";
        plugin.stats().async(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, id.toString());
                ps.setString(2, name.length() > 16 ? name.substring(0, 16) : name);
                ps.setString(3, rank);
                ps.setLong(4, muted);
                ps.executeUpdate();
            }
        });
    }

    // ================================================================= Mute

    public long mutedUntil(UUID id) {
        Long m = mutedUntil.get(id);
        if (m == null) return 0;
        if (m <= System.currentTimeMillis()) {
            mutedUntil.remove(id);
            return 0;
        }
        return m;
    }

    public void mute(CommandSender by, String playerName, int minutes) {
        Player online = Bukkit.getPlayerExact(playerName);
        var offline = online != null ? online : Bukkit.getOfflinePlayerIfCached(playerName);
        if (offline == null) {
            Txt.send(by, plugin.settings().prefix + "§c" + playerName + " war noch nie auf dem Server.");
            return;
        }
        UUID id = offline.getUniqueId();
        String name = offline.getName() != null ? offline.getName() : playerName;
        long until = minutes <= 0 ? 0 : System.currentTimeMillis() + minutes * 60_000L;
        if (until == 0) mutedUntil.remove(id);
        else mutedUntil.put(id, until);
        upsert(id, name, assigned.getOrDefault(id, defaultRank), until);
        Txt.send(by, plugin.settings().prefix + (until == 0 ? "§a" + name + " ist nicht mehr gemutet." : "§c" + name + " §7ist für §e" + minutes + " Min. §7gemutet."));
        if (online != null) Txt.send(online, plugin.settings().prefix + (until == 0 ? "§aDu darfst wieder schreiben." : "§cDu wurdest für " + minutes + " Minuten gemutet."));
    }

    // ================================================================= Teamliste

    /** Alle Teammitglieder (staff-Ränge) aus der Datenbank, im Stil der Mega-Events-Teamliste. */
    public void teamList(CommandSender to) {
        List<String> staffRanks = new ArrayList<>();
        for (Rank r : ranks.values()) if (r.staff()) staffRanks.add(r.id());
        if (staffRanks.isEmpty()) {
            Txt.send(to, "§7Keine Team-Ränge in der ranks.yml.");
            return;
        }
        plugin.stats().async(c -> {
            Map<String, List<String>> members = new LinkedHashMap<>();
            for (String r : staffRanks) members.put(r, new ArrayList<>());
            try (PreparedStatement ps = c.prepareStatement("SELECT name, rank_id FROM " + table + " ORDER BY name")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        List<String> l = members.get(rs.getString(2));
                        if (l != null) l.add(rs.getString(1));
                    }
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                String line = "§8§m──────────────────────";
                int total = 0;
                Txt.send(to, "§b§lTeamliste");
                for (Map.Entry<String, List<String>> e : members.entrySet()) {
                    Rank r = ranks.get(e.getKey());
                    Txt.send(to, line);
                    Txt.send(to, " §8| " + r.nameColor() + "§l" + r.name());
                    Txt.send(to, line);
                    if (e.getValue().isEmpty()) Txt.send(to, "§8➥ §7???");
                    for (String n : e.getValue()) {
                        boolean on = Bukkit.getPlayerExact(n) != null;
                        Txt.send(to, "§8➥ " + r.nameColor() + n + (on ? " §a●" : ""));
                        total++;
                    }
                    Txt.send(to, "");
                }
                Txt.send(to, "§8§m────────────────────────────────────────────────");
                Txt.send(to, "§8➤ §7Teammitglieder §8➤ §b" + total);
            });
        });
    }

    public void ranksList(Consumer<String> out) {
        for (Rank r : ranks.values()) out.accept("§8» §f" + r.id() + " §8- " + r.prefix() + r.name() + " §8(Gewicht " + r.weight() + (r.staff() ? ", Team" : "") + ")");
    }
}
