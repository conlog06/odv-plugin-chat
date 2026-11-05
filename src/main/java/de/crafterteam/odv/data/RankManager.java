package de.crafterteam.odv.data;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.util.Txt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
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

    public record Rank(String id, String name, String prefix, String nameColorCode, NamedTextColor color, int weight,
                       boolean staff, boolean protect, List<String> permissions) {
        /** Farbcode für den Namen (auch Hex). */
        public String nameColor() {
            return nameColorCode;
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
        if (f.isFile() && !YamlConfiguration.loadConfiguration(f).contains("format")) {
            // altes Format (3.1) sichern und neues mit Farbverläufen anlegen – Zuordnungen bleiben in der Datenbank
            f.renameTo(new File(plugin.getDataFolder(), "ranks-alt-3.1.yml"));
            plugin.getLogger().info("ranks.yml auf das neue Format (Farbverlaeufe) umgestellt, alte Datei: ranks-alt-3.1.yml");
        }
        if (!f.isFile()) plugin.saveResource("ranks.yml", false);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        if (y.getInt("format", 2) < 3) {
            // 3.5: Spieler-Rang bekommt einen grauen Farbverlauf (nur wenn noch unverändert)
            if (y.getString("ranks.spieler.prefix", "").isEmpty()) {
                y.set("ranks.spieler.prefix", "<gradient:#5C6670:#B8C0C8>SPIELER</gradient> <dark_gray>┃</dark_gray> ");
                y.set("ranks.spieler.name-color", "#C3CAD2");
            }
            y.set("format", 3);
            try {
                y.save(f);
            } catch (java.io.IOException ignored) {
            }
        }
        ranks.clear();
        ConfigurationSection sec = y.getConfigurationSection("ranks");
        if (sec != null) {
            for (String rawId : sec.getKeys(false)) {
                ConfigurationSection r = sec.getConfigurationSection(rawId);
                if (r == null) continue;
                String id = rawId.toLowerCase(Locale.ROOT);
                String prefix = Txt.legacy(Txt.rich(r.getString("prefix", "")));
                TextColor nc = parseColor(r.getString("name-color", r.getString("color", "gray")));
                String nameCode = Txt.legacy(Component.text("x", nc));
                nameCode = nameCode.substring(0, nameCode.length() - 1);
                boolean protect = r.getBoolean("protected", id.equals("owner") || id.equals("leitung") || id.equals("admin"));
                ranks.put(id, new Rank(id, r.getString("name", rawId), prefix, nameCode, NamedTextColor.nearestTo(nc), r.getInt("weight", 0),
                        r.getBoolean("staff", false), protect, r.getStringList("permissions")));
            }
        }
        defaultRank = y.getString("default-rank", "spieler").toLowerCase(Locale.ROOT);
        if (!ranks.containsKey(defaultRank)) {
            ranks.put(defaultRank, new Rank(defaultRank, "Spieler", "", "§7", NamedTextColor.GRAY, 0, false, false, List.of()));
        }
        List<Rank> sorted = new ArrayList<>(ranks.values());
        sorted.sort((a, b) -> b.weight() - a.weight());
        ranks.clear();
        for (Rank r : sorted) ranks.put(r.id(), r);
    }

    private static TextColor parseColor(String s) {
        if (s == null) return NamedTextColor.GRAY;
        s = s.trim();
        if (s.startsWith("#")) {
            TextColor c = TextColor.fromHexString(s);
            return c == null ? NamedTextColor.GRAY : c;
        }
        NamedTextColor n = NamedTextColor.NAMES.value(s.toLowerCase(Locale.ROOT));
        return n == null ? NamedTextColor.GRAY : n;
    }

    /**
     * Darf "by" diesem Spieler diesen Rang geben?
     * Konsole immer. Geschützte Ränge (Owner/Leitung/Admin) nur, wer selbst einen geschützten Rang
     * mit mindestens diesem Gewicht hat – oder die Permission odv.rank.&lt;rang&gt; ausdrücklich gesetzt bekam.
     * Spieler mit geschütztem Rang kann man nur mit eben solchen Rechten ändern.
     */
    public String denyReason(CommandSender by, Rank target, UUID targetPlayer) {
        if (!(by instanceof Player p)) return null;
        if (!p.hasPermission("odv.rank")) return "Du darfst keine Ränge vergeben.";
        Rank own = of(p);
        Rank current = of(targetPlayer);
        boolean explicit = p.isPermissionSet("odv.rank." + target.id()) && p.hasPermission("odv.rank." + target.id());
        if (current.protect() && !(own.protect() && own.weight() >= current.weight())) {
            return current.name() + " ist ein geschützter Rang – nur die Konsole oder höhere Leitung kann ihn ändern.";
        }
        if (target.protect() && !explicit && !(own.protect() && own.weight() >= target.weight())) {
            return target.name() + " kann am Anfang nur über die Konsole vergeben werden (/rank set <Spieler> " + target.id() + ").";
        }
        if (!target.protect() && !explicit && !own.protect() && own.weight() > 0 && target.weight() >= own.weight()) {
            return "Du kannst nur Ränge unter deinem eigenen vergeben.";
        }
        return null;
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
        String deny = denyReason(by, r, id);
        if (deny != null) {
            Txt.send(by, plugin.settings().prefix + "§c" + deny);
            return;
        }
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
        if (by instanceof Player bp && !bp.getUniqueId().equals(id) && of(id).weight() > 0 && of(id).weight() >= of(bp).weight()) {
            Txt.send(by, plugin.settings().prefix + "§cDu kannst niemanden mit gleichem oder höherem Rang muten.");
            return;
        }
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
        for (Rank r : ranks.values()) out.accept("§8» §f" + r.id() + " §8- " + r.prefix() + r.name() + " §8(Gewicht " + r.weight() + (r.staff() ? ", Team" : "") + (r.protect() ? ", §cgeschützt§8" : "") + ")");
    }
}
