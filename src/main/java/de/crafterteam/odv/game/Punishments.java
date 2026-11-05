package de.crafterteam.odv.game;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.Settings;
import de.crafterteam.odv.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Combat-Log, Strikes und Bans.
 * <ul>
 *     <li>Treffer zwischen zwei Spielern = beide „im Kampf" (combat-log.seconds)</li>
 *     <li>Ausloggen im Kampf = sofort raus, Gegner bekommt den Kill, Strike</li>
 *     <li>Runde verlassen (nicht zurückgekommen) = Strike</li>
 *     <li>Genug aktive Strikes = automatischer Ban (punishments.auto-ban)</li>
 * </ul>
 * Gespeichert in derselben Datenbank wie die Statistik (SQLite oder MySQL).
 */
public class Punishments implements Listener, CommandExecutor, TabCompleter {

    public static final String[] COMMANDS = {"ban", "tempban", "unban", "kick", "banlist", "strike", "strikes", "unstrike"};

    public record Tag(UUID enemy, long until) {}

    public record Ban(UUID id, String name, String reason, String by, long created, long expires) {
        boolean active() {
            return expires == 0 || expires > System.currentTimeMillis();
        }
    }

    private final OdvPlugin plugin;
    private final Map<UUID, Tag> combat = new ConcurrentHashMap<>();
    private final Map<UUID, Ban> bans = new ConcurrentHashMap<>(); // Cache, falls die DB beim Login nicht antwortet
    private String tStrikes, tBans;

    public Punishments(OdvPlugin plugin) {
        this.plugin = plugin;
    }

    private Settings s() {
        return plugin.settings();
    }

    // ================================================================= Datenbank

    public void open() {
        String pre = plugin.stats().tablePrefix();
        tStrikes = pre + "strikes";
        tBans = pre + "bans";
        boolean my = plugin.stats().isMysql();
        plugin.stats().async(c -> {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tStrikes + " ("
                        + (my ? "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY" : "id INTEGER PRIMARY KEY AUTOINCREMENT")
                        + ", uuid VARCHAR(36) NOT NULL, name VARCHAR(16) NOT NULL, reason VARCHAR(255) NOT NULL,"
                        + " by_name VARCHAR(32) NOT NULL, created BIGINT NOT NULL, expires BIGINT NOT NULL)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tBans + " (uuid VARCHAR(36) NOT NULL PRIMARY KEY,"
                        + " name VARCHAR(16) NOT NULL, reason VARCHAR(255) NOT NULL, by_name VARCHAR(32) NOT NULL,"
                        + " created BIGINT NOT NULL, expires BIGINT NOT NULL)");
            }
            long now = System.currentTimeMillis();
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + tBans + " WHERE expires > 0 AND expires < ?")) {
                ps.setLong(1, now);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT uuid, name, reason, by_name, created, expires FROM " + tBans);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Ban b = readBan(rs);
                    if (b != null) bans.put(b.id(), b);
                }
            }
        });
        // Combat-Anzeige
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickCombat, 20L, 20L);
    }

    private static Ban readBan(ResultSet rs) throws SQLException {
        try {
            return new Ban(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3), rs.getString(4), rs.getLong(5), rs.getLong(6));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Ban loadBan(Connection c, UUID id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT uuid, name, reason, by_name, created, expires FROM " + tBans + " WHERE uuid = ?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    bans.remove(id);
                    return null;
                }
                Ban b = readBan(rs);
                if (b == null || !b.active()) {
                    deleteBan(c, id);
                    bans.remove(id);
                    return null;
                }
                bans.put(id, b);
                return b;
            }
        }
    }

    private void deleteBan(Connection c, UUID id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + tBans + " WHERE uuid = ?")) {
            ps.setString(1, id.toString());
            ps.executeUpdate();
        }
    }

    private int activeStrikes(Connection c, UUID id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM " + tStrikes + " WHERE uuid = ? AND (expires = 0 OR expires > ?)")) {
            ps.setString(1, id.toString());
            ps.setLong(2, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // ================================================================= Combat-Log

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!s().combatLog || !(e.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (e.getDamager() instanceof Player p) attacker = p;
        else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p) attacker = p;
        if (attacker == null || attacker.equals(victim)) return;
        GameManager g = plugin.game();
        if (!g.getState().isIngame() || !g.isAlive(victim) || !g.isAlive(attacker)) return;
        if (plugin.teams().sameTeam(victim.getUniqueId(), attacker.getUniqueId())) return;
        long until = System.currentTimeMillis() + s().combatSeconds * 1000L;
        tag(victim, attacker.getUniqueId(), until);
        tag(attacker, victim.getUniqueId(), until);
    }

    private void tag(Player p, UUID enemy, long until) {
        Tag old = combat.put(p.getUniqueId(), new Tag(enemy, until));
        if (old == null || old.until() < System.currentTimeMillis()) {
            Txt.actionBar(p, "§c⚔ Im Kampf §8– §7nicht ausloggen!");
        }
    }

    /** Aktiver Kampf-Status oder null. */
    public Tag combatTag(UUID id) {
        if (!s().combatLog) return null;
        Tag t = combat.get(id);
        if (t == null) return null;
        if (t.until() < System.currentTimeMillis()) {
            combat.remove(id);
            return null;
        }
        return t;
    }

    public boolean isBanned(UUID id) {
        Ban b = bans.get(id);
        return b != null && b.active();
    }

    public boolean inCombat(Player p) {
        return combatTag(p.getUniqueId()) != null;
    }

    public void clearCombat() {
        combat.clear();
    }

    private void tickCombat() {
        if (combat.isEmpty()) return;
        long now = System.currentTimeMillis();
        combat.entrySet().removeIf(en -> {
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null || !plugin.game().isAlive(p)) return true;
            long left = (en.getValue().until() - now + 999) / 1000;
            if (left <= 0) {
                Txt.actionBar(p, "§a✔ Nicht mehr im Kampf");
                return true;
            }
            Txt.actionBar(p, "§c⚔ Im Kampf §8– §7nicht ausloggen! §c" + left + "s");
            return false;
        });
    }

    // ================================================================= Strikes

    /** Strike vergeben (by = null → System). Prüft danach die Auto-Bans. */
    public void strike(CommandSender by, UUID id, String name, String reason) {
        String byName = by == null ? "System" : by.getName();
        String n = name != null ? name : plugin.stats().name(id);
        long now = System.currentTimeMillis();
        long expires = s().strikeExpireDays <= 0 ? 0 : now + s().strikeExpireDays * 86_400_000L;
        plugin.stats().async(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + tStrikes + " (uuid, name, reason, by_name, created, expires) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, id.toString());
                ps.setString(2, cut(n, 16));
                ps.setString(3, cut(reason, 255));
                ps.setString(4, cut(byName, 32));
                ps.setLong(5, now);
                ps.setLong(6, expires);
                ps.executeUpdate();
            }
            int count = activeStrikes(c, id);
            Map.Entry<Integer, String> rule = s().autoBan.floorEntry(count);
            boolean exact = rule != null && (rule.getKey() == count || count > s().autoBan.lastKey());
            Bukkit.getScheduler().runTask(plugin, () -> {
                String msg = s().prefix + "§c" + n + " §7hat einen §cStrike §7bekommen §8(§c" + count + "§8) §7– §f" + reason;
                staff(msg);
                if (by != null && !(by instanceof Player bp && bp.hasPermission("odv.staff"))) Txt.send(by, msg);
                Player target = Bukkit.getPlayer(id);
                if (target != null) Txt.send(target, s().prefix + "§cDu hast einen Strike bekommen: §f" + reason + " §8(§c" + count + " aktiv§8)");
                if (exact) {
                    long dur = parseDuration(rule.getValue());
                    if (dur != Long.MIN_VALUE) {
                        ban(null, id, n, dur, "Automatisch: " + count + " Strikes (zuletzt: " + reason + ")");
                    }
                }
            });
        });
    }

    // ================================================================= Bans

    /** dur: 0 = permanent, sonst Millisekunden. */
    public void ban(CommandSender by, UUID id, String name, long dur, String reason) {
        String byName = by == null ? "System" : by.getName();
        long now = System.currentTimeMillis();
        Ban b = new Ban(id, cut(name, 16), cut(reason, 255), cut(byName, 32), now, dur == 0 ? 0 : now + dur);
        bans.put(id, b);
        plugin.stats().async(c -> {
            deleteBan(c, id);
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + tBans + " (uuid, name, reason, by_name, created, expires) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, id.toString());
                ps.setString(2, b.name());
                ps.setString(3, b.reason());
                ps.setString(4, b.by());
                ps.setLong(5, b.created());
                ps.setLong(6, b.expires());
                ps.executeUpdate();
            }
        });
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            // im Spiel: wie Verlassen behandeln (Items droppen, ausscheiden)
            online.kick(screen(b));
        }
        String msg = s().prefix + "§c" + name + " §7wurde " + (dur == 0 ? "§4permanent" : "für §e" + formatDuration(dur)) + " §7gebannt §8(§7" + byName + "§8) §7– §f" + reason;
        staff(msg);
        if (by != null && !(by instanceof Player bp && bp.hasPermission("odv.staff"))) Txt.send(by, msg);
    }

    public net.kyori.adventure.text.Component screen(Ban b) {
        String until = b.expires() == 0 ? "§4Permanent" : "§e" + new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(b.expires()))
                + " §8(§7noch " + formatDuration(b.expires() - System.currentTimeMillis()) + "§8)";
        return Txt.c("§c§lDu bist gebannt\n\n§7Grund: §f" + b.reason() + "\n§7Bis: " + until + "\n§7Von: §f" + b.by()
                + (s().banAppeal.isBlank() ? "" : "\n\n§b" + s().banAppeal));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED || tBans == null) return;
        UUID id = e.getUniqueId();
        Ban b;
        try {
            b = plugin.stats().query(c -> loadBan(c, id)).get(3, TimeUnit.SECONDS);
        } catch (Exception ex) {
            b = bans.get(id); // DB hängt → Cache
            if (b != null && !b.active()) b = null;
        }
        if (b != null) e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, screen(b));
    }

    // ================================================================= Befehle

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        String c = cmd.getName().toLowerCase(Locale.ROOT);
        String pre = s().prefix;
        switch (c) {
            case "kick" -> {
                if (!perm(sender, "odv.kick")) return true;
                if (a.length < 1) return usage(sender, "/kick <Spieler> [Grund]");
                Player t = Bukkit.getPlayerExact(a[0]);
                if (t == null) {
                    Txt.send(sender, pre + "§c" + a[0] + " ist nicht online.");
                    return true;
                }
                if (!canPunish(sender, t.getUniqueId())) return true;
                String reason = rest(a, 1, "Kein Grund angegeben");
                t.kick(Txt.c("§c§lDu wurdest gekickt\n\n§7Grund: §f" + reason + "\n§7Von: §f" + sender.getName()));
                staff(pre + "§e" + t.getName() + " §7wurde von §f" + sender.getName() + " §7gekickt – §f" + reason);
                if (!sender.hasPermission("odv.staff")) Txt.send(sender, pre + "§aGekickt.");
            }
            case "ban", "tempban" -> {
                boolean temp = c.equals("tempban");
                if (!perm(sender, temp ? "odv.tempban" : "odv.ban")) return true;
                if (a.length < (temp ? 2 : 1)) return usage(sender, temp ? "/tempban <Spieler> <Dauer: 30m, 12h, 7d, 2w> [Grund]" : "/ban <Spieler> [Grund]");
                long dur = 0;
                if (temp) {
                    dur = parseDuration(a[1]);
                    if (dur == Long.MIN_VALUE || dur == 0) {
                        Txt.send(sender, pre + "§cUngültige Dauer. Beispiele: §f30m 12h 7d 2w");
                        return true;
                    }
                }
                long d = dur;
                String reason = rest(a, temp ? 2 : 1, "Regelverstoß");
                resolve(sender, a[0], (id, name) -> {
                    if (canPunish(sender, id)) ban(sender, id, name, d, reason);
                });
            }
            case "unban" -> {
                if (!perm(sender, "odv.tempban")) return true;
                if (a.length < 1) return usage(sender, "/unban <Spieler>");
                resolve(sender, a[0], (id, name) -> {
                    boolean had = bans.remove(id) != null;
                    plugin.stats().async(con -> deleteBan(con, id));
                    staff(pre + "§a" + name + " §7wurde von §f" + sender.getName() + " §7entbannt.");
                    if (!sender.hasPermission("odv.staff")) Txt.send(sender, pre + (had ? "§aEntbannt." : "§7" + name + " war nicht gebannt."));
                });
            }
            case "banlist" -> {
                if (!perm(sender, "odv.tempban")) return true;
                List<Ban> list = new ArrayList<>();
                for (Ban b : bans.values()) if (b.active()) list.add(b);
                list.sort((x, y) -> Long.compare(y.created(), x.created()));
                Txt.send(sender, pre + "§7Aktive Bans: §c" + list.size());
                for (int i = 0; i < Math.min(15, list.size()); i++) {
                    Ban b = list.get(i);
                    Txt.send(sender, "§8» §c" + b.name() + " §8- §7" + (b.expires() == 0 ? "§4perm" : "noch " + formatDuration(b.expires() - System.currentTimeMillis()))
                            + " §8- §f" + b.reason() + " §8(" + b.by() + ")");
                }
            }
            case "strike" -> {
                if (!perm(sender, "odv.strike")) return true;
                if (a.length < 2) return usage(sender, "/strike <Spieler> <Grund>");
                String reason = rest(a, 1, "");
                resolve(sender, a[0], (id, name) -> {
                    if (canPunish(sender, id)) strike(sender, id, name, reason);
                });
            }
            case "unstrike" -> {
                if (!perm(sender, "odv.strike")) return true;
                if (a.length < 2) return usage(sender, "/unstrike <Spieler> <Nr|all>");
                String which = a[1];
                resolve(sender, a[0], (id, name) -> plugin.stats().async(con -> {
                    int n;
                    if (which.equalsIgnoreCase("all")) {
                        try (PreparedStatement ps = con.prepareStatement("DELETE FROM " + tStrikes + " WHERE uuid = ?")) {
                            ps.setString(1, id.toString());
                            n = ps.executeUpdate();
                        }
                    } else {
                        int nr;
                        try {
                            nr = Integer.parseInt(which);
                        } catch (NumberFormatException ex) {
                            nr = -1;
                        }
                        try (PreparedStatement ps = con.prepareStatement("DELETE FROM " + tStrikes + " WHERE uuid = ? AND id = ?")) {
                            ps.setString(1, id.toString());
                            ps.setInt(2, nr);
                            n = ps.executeUpdate();
                        }
                    }
                    int left = activeStrikes(con, id);
                    int removed = n;
                    Bukkit.getScheduler().runTask(plugin, () -> Txt.send(sender, pre + (removed > 0
                            ? "§a" + removed + " Strike(s) von " + name + " entfernt. §7Aktiv: §c" + left
                            : "§cKein Strike mit dieser Nummer. §7(/strikes " + name + ")")));
                }));
            }
            case "strikes" -> {
                String targetName = a.length > 0 ? a[0] : sender.getName();
                boolean self = a.length == 0 || targetName.equalsIgnoreCase(sender.getName());
                if (!self && !perm(sender, "odv.strike")) return true;
                if (self && !(sender instanceof Player)) return usage(sender, "/strikes <Spieler>");
                resolve(sender, targetName, (id, name) -> showStrikes(sender, id, name, !self || sender.hasPermission("odv.strike")));
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void showStrikes(CommandSender to, UUID id, String name, boolean staffView) {
        plugin.stats().async(c -> {
            List<String> lines = new ArrayList<>();
            long now = System.currentTimeMillis();
            int active = 0;
            SimpleDateFormat f = new SimpleDateFormat("dd.MM.yy");
            try (PreparedStatement ps = c.prepareStatement("SELECT id, reason, by_name, created, expires FROM " + tStrikes + " WHERE uuid = ? ORDER BY created DESC")) {
                ps.setString(1, id.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long exp = rs.getLong(5);
                        boolean on = exp == 0 || exp > now;
                        if (on) active++;
                        if (lines.size() < 12) {
                            lines.add((on ? "§c" : "§8§m") + "#" + rs.getInt(1) + "§r " + (on ? "§f" : "§8") + rs.getString(2) + " §8(" + f.format(new Date(rs.getLong(4)))
                                    + (staffView ? ", " + rs.getString(3) : "") + (on ? exp == 0 ? "" : ", verfällt " + f.format(new Date(exp)) : ", verfallen") + ")");
                        }
                    }
                }
            }
            Ban ban = loadBan(c, id);
            int a = active;
            Bukkit.getScheduler().runTask(plugin, () -> {
                Txt.send(to, s().prefix + "§7Strikes von §e" + name + "§7: §c" + a + " aktiv");
                if (lines.isEmpty()) Txt.send(to, "§8» §aKeine Strikes.");
                for (String l : lines) Txt.send(to, "§8» " + l);
                Map.Entry<Integer, String> next = s().autoBan.ceilingEntry(a + 1);
                if (next != null) Txt.send(to, "§8» §7Bei §c" + next.getKey() + " §7Strikes: Ban §e" + next.getValue());
                if (ban != null) Txt.send(to, "§8» §4Gebannt §7" + (ban.expires() == 0 ? "permanent" : "noch " + formatDuration(ban.expires() - System.currentTimeMillis())) + " §8– §f" + ban.reason());
            });
        });
    }

    /** Name → UUID (online, Cache oder Datenbank), Callback im Main-Thread. */
    private void resolve(CommandSender sender, String input, java.util.function.BiConsumer<UUID, String> then) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            then.accept(online.getUniqueId(), online.getName());
            return;
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(input);
        if (cached != null) {
            then.accept(cached.getUniqueId(), cached.getName() != null ? cached.getName() : input);
            return;
        }
        String table = plugin.stats().tablePrefix() + "players";
        plugin.stats().async(c -> {
            UUID id = null;
            String name = input;
            try (PreparedStatement ps = c.prepareStatement("SELECT uuid, name FROM " + table + " WHERE LOWER(name) = LOWER(?)")) {
                ps.setString(1, input);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        id = UUID.fromString(rs.getString(1));
                        name = rs.getString(2);
                    }
                }
            } catch (IllegalArgumentException ignored) {
            }
            UUID fid = id;
            String fname = name;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (fid == null) Txt.send(sender, s().prefix + "§c" + input + " war noch nie auf dem Server.");
                else then.accept(fid, fname);
            });
        });
    }

    private boolean canPunish(CommandSender by, UUID target) {
        if (!(by instanceof Player bp) || bp.getUniqueId().equals(target)) return true;
        int tw = plugin.ranks().of(target).weight();
        if (tw > 0 && tw >= plugin.ranks().of(bp).weight()) {
            Txt.send(by, s().prefix + "§cDu kannst niemanden mit gleichem oder höherem Rang bestrafen.");
            return false;
        }
        return true;
    }

    private boolean perm(CommandSender s, String perm) {
        if (s.hasPermission(perm)) return true;
        Txt.send(s, s().prefix + "§cDazu hast du keine Rechte.");
        return false;
    }

    private boolean usage(CommandSender s, String u) {
        Txt.send(s, s().prefix + "§7Benutzung: §e" + u);
        return true;
    }

    private void staff(String msg) {
        for (Player p : Bukkit.getOnlinePlayers()) if (p.hasPermission("odv.staff")) Txt.send(p, msg);
        plugin.getLogger().info(Txt.ascii(msg.replaceAll("§.", "")));
    }

    private static String rest(String[] a, int from, String def) {
        if (a.length <= from) return def;
        return String.join(" ", Arrays.copyOfRange(a, from, a.length));
    }

    private static String cut(String s, int max) {
        return s == null ? "" : s.length() > max ? s.substring(0, max) : s;
    }

    /** "30m", "12h", "7d", "2w", "perm" → Millisekunden (0 = permanent, MIN_VALUE = ungültig). */
    public static long parseDuration(String in) {
        if (in == null) return Long.MIN_VALUE;
        String s = in.trim().toLowerCase(Locale.ROOT);
        if (s.equals("perm") || s.equals("permanent") || s.equals("-1")) return 0;
        if (s.length() < 2) return Long.MIN_VALUE;
        long n;
        try {
            n = Long.parseLong(s.substring(0, s.length() - 1));
        } catch (NumberFormatException e) {
            return Long.MIN_VALUE;
        }
        if (n <= 0) return Long.MIN_VALUE;
        return switch (s.charAt(s.length() - 1)) {
            case 's' -> n * 1000L;
            case 'm' -> n * 60_000L;
            case 'h' -> n * 3_600_000L;
            case 'd' -> n * 86_400_000L;
            case 'w' -> n * 604_800_000L;
            default -> Long.MIN_VALUE;
        };
    }

    public static String formatDuration(long ms) {
        long m = Math.max(1, ms / 60_000L);
        long d = m / 1440, h = (m % 1440) / 60, min = m % 60;
        if (d > 0) return d + "d" + (h > 0 ? " " + h + "h" : "");
        if (h > 0) return h + "h" + (min > 0 ? " " + min + "m" : "");
        return min + "m";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        String c = cmd.getName().toLowerCase(Locale.ROOT);
        if (a.length == 1 && !c.equals("banlist")) {
            if (c.equals("unban")) {
                for (Ban b : bans.values()) if (b.name().toLowerCase(Locale.ROOT).startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(b.name());
            } else {
                for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(p.getName());
            }
        } else if (a.length == 2 && c.equals("tempban")) {
            out.addAll(List.of("30m", "1h", "12h", "1d", "7d", "2w"));
        } else if (a.length == 2 && c.equals("unstrike")) {
            out.add("all");
        }
        return out;
    }
}
