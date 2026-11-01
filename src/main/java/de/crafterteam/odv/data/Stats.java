package de.crafterteam.odv.data;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.Settings;
import de.crafterteam.odv.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Statistik in SQLite (lokal) oder MySQL. Alles läuft über einen eigenen DB-Thread,
 * der Spiel-Thread arbeitet nur mit dem Cache.
 */
public class Stats {

    public static final String[] COLUMNS = {"kills", "deaths", "wins", "games", "bets", "p_kills", "p_deaths"};

    public static final class Entry {
        public volatile String name;
        public final int[] v = new int[COLUMNS.length];   // Anzeige (gesamt)
        final int[] d = new int[COLUMNS.length];          // noch nicht gespeichert
        volatile boolean dirty;

        Entry(String name) {
            this.name = name;
        }
    }

    private final OdvPlugin plugin;
    private final ExecutorService db = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ODVMeetup-DB");
        t.setDaemon(true);
        return t;
    });
    private final Map<UUID, Entry> cache = new ConcurrentHashMap<>();
    private Connection connection;
    private boolean mysql;
    private String table;
    private boolean backupBusy;

    public Stats(OdvPlugin plugin) {
        this.plugin = plugin;
    }

    // ================================================================= Verbindung

    public void open() {
        Settings s = plugin.settings();
        mysql = s.mysql;
        table = (mysql ? s.dbPrefix : "odv_") + "players";
        try {
            connect();
            createTable();
            plugin.getLogger().info("Statistik: " + (mysql ? "MySQL " + s.dbHost + "/" + s.dbName : "lokal (stats.db)"));
        } catch (SQLException e) {
            if (mysql) {
                plugin.getLogger().severe("MySQL nicht erreichbar (" + e.getMessage() + ") – nutze lokale Datei.");
                mysql = false;
                table = "odv_players";
                try {
                    connect();
                    createTable();
                } catch (SQLException e2) {
                    plugin.getLogger().severe("Statistik deaktiviert: " + e2.getMessage());
                }
            } else {
                plugin.getLogger().severe("Statistik deaktiviert: " + e.getMessage());
            }
        }
        importYaml();
        for (Player p : Bukkit.getOnlinePlayers()) load(p);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> flush(false), 600L, 600L);
    }

    private void connect() throws SQLException {
        Settings s = plugin.settings();
        if (mysql) {
            String url = "jdbc:mysql://" + s.dbHost + ":" + s.dbPort + "/" + s.dbName
                    + "?useSSL=" + s.dbSsl + "&autoReconnect=true&characterEncoding=utf8&allowPublicKeyRetrieval=true";
            connection = DriverManager.getConnection(url, s.dbUser, s.dbPassword);
        } else {
            plugin.getDataFolder().mkdirs();
            connection = DriverManager.getConnection("jdbc:sqlite:" + new File(plugin.getDataFolder(), "stats.db").getAbsolutePath());
        }
    }

    private Connection conn() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(2)) connect();
        return connection;
    }

    private void createTable() throws SQLException {
        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS " + table
                + " (uuid VARCHAR(36) NOT NULL PRIMARY KEY, name VARCHAR(16) NOT NULL");
        for (String c : COLUMNS) sql.append(", ").append(c).append(" INT NOT NULL DEFAULT 0");
        sql.append(")");
        try (Statement st = conn().createStatement()) {
            st.executeUpdate(sql.toString());
        }
    }

    public boolean isMysql() {
        return mysql;
    }

    public void close() {
        flush(true);
        db.shutdown();
        try {
            db.awaitTermination(5, TimeUnit.SECONDS);
            if (connection != null) connection.close();
        } catch (Exception ignored) {
        }
    }

    // ================================================================= Cache

    public void load(Player p) {
        UUID id = p.getUniqueId();
        String name = p.getName();
        Entry e = cache.computeIfAbsent(id, k -> new Entry(name));
        e.name = name;
        db.execute(() -> {
            try (PreparedStatement ps = conn().prepareStatement("SELECT * FROM " + table + " WHERE uuid=?")) {
                ps.setString(1, id.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        synchronized (e) {
                            for (int i = 0; i < COLUMNS.length; i++) e.v[i] += rs.getInt(COLUMNS[i]);
                        }
                    }
                }
            } catch (SQLException ex) {
                plugin.getLogger().warning("Statistik laden fehlgeschlagen: " + ex.getMessage());
            }
        });
    }

    public void unload(UUID id) {
        Entry e = cache.get(id);
        if (e != null && e.dirty) save(id, e);
        cache.remove(id);
    }

    public Entry get(UUID id) {
        return cache.computeIfAbsent(id, k -> {
            String n = Bukkit.getOfflinePlayer(id).getName();
            return new Entry(n == null ? id.toString().substring(0, 8) : n);
        });
    }

    public int get(UUID id, String col) {
        Entry e = cache.get(id);
        if (e == null) return 0;
        int i = idx(col);
        return i < 0 ? 0 : e.v[i];
    }

    public String name(UUID id) {
        Entry e = cache.get(id);
        if (e != null) return e.name;
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? id.toString().substring(0, 8) : n;
    }

    public void add(UUID id, String col) {
        int i = idx(col);
        if (i < 0) return;
        Entry e = get(id);
        synchronized (e) {
            e.v[i]++;
            e.d[i]++;
        }
        e.dirty = true;
    }

    private static int idx(String col) {
        for (int i = 0; i < COLUMNS.length; i++) if (COLUMNS[i].equals(col)) return i;
        return -1;
    }

    // ================================================================= Schreiben / Lesen

    public void flush(boolean sync) {
        for (Map.Entry<UUID, Entry> en : cache.entrySet()) {
            if (en.getValue().dirty) {
                if (sync) write(en.getKey(), en.getValue());
                else save(en.getKey(), en.getValue());
            }
        }
    }

    private void save(UUID id, Entry e) {
        e.dirty = false;
        db.execute(() -> write(id, e));
    }

    private synchronized void write(UUID id, Entry e) {
        StringBuilder cols = new StringBuilder("uuid, name"), qs = new StringBuilder("?, ?"), upd = new StringBuilder();
        for (String c : COLUMNS) {
            cols.append(", ").append(c);
            qs.append(", ?");
        }
        String sql;
        if (mysql) {
            upd.append("name=VALUES(name)");
            for (String c : COLUMNS) upd.append(", ").append(c).append("=").append(c).append("+VALUES(").append(c).append(")");
            sql = "INSERT INTO " + table + " (" + cols + ") VALUES (" + qs + ") ON DUPLICATE KEY UPDATE " + upd;
        } else {
            upd.append("name=excluded.name");
            for (String c : COLUMNS) upd.append(", ").append(c).append("=").append(c).append("+excluded.").append(c);
            sql = "INSERT INTO " + table + " (" + cols + ") VALUES (" + qs + ") ON CONFLICT(uuid) DO UPDATE SET " + upd;
        }
        int[] delta = new int[COLUMNS.length];
        synchronized (e) {
            System.arraycopy(e.d, 0, delta, 0, delta.length);
        }
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, id.toString());
            ps.setString(2, e.name == null ? "?" : e.name);
            for (int i = 0; i < COLUMNS.length; i++) ps.setInt(3 + i, delta[i]);
            ps.executeUpdate();
            synchronized (e) {
                for (int i = 0; i < COLUMNS.length; i++) e.d[i] -= delta[i];
            }
        } catch (SQLException ex) {
            e.dirty = true;
            plugin.getLogger().warning("Statistik speichern fehlgeschlagen: " + ex.getMessage());
        }
    }

    /** Top-Liste (Name, Wert) – Ergebnis kommt im Haupt-Thread an. */
    public void top(String col, int n, Consumer<List<Map.Entry<String, Integer>>> callback) {
        if (idx(col) < 0) return;
        flush(false);
        db.execute(() -> {
            List<Map.Entry<String, Integer>> list = new ArrayList<>();
            try (PreparedStatement ps = conn().prepareStatement(
                    "SELECT name, " + col + " FROM " + table + " WHERE " + col + " > 0 ORDER BY " + col + " DESC LIMIT " + n)) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(new AbstractMap.SimpleEntry<>(rs.getString(1), rs.getInt(2)));
                }
            } catch (SQLException ex) {
                plugin.getLogger().warning("Top-Liste fehlgeschlagen: " + ex.getMessage());
            }
            if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> callback.accept(list));
        });
    }

    /** Werte eines (auch offline) Spielers nach Namen. */
    public void lookup(String name, Consumer<int[]> callback) {
        db.execute(() -> {
            int[] out = null;
            try (PreparedStatement ps = conn().prepareStatement("SELECT * FROM " + table + " WHERE LOWER(name)=LOWER(?)")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        out = new int[COLUMNS.length];
                        for (int i = 0; i < COLUMNS.length; i++) out[i] = rs.getInt(COLUMNS[i]);
                    }
                }
            } catch (SQLException ex) {
                plugin.getLogger().warning("Statistik-Abfrage fehlgeschlagen: " + ex.getMessage());
            }
            final int[] r = out;
            if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> callback.accept(r));
        });
    }

    /** stats.yml aus Version 1/2 einmalig übernehmen. */
    private void importYaml() {
        File f = new File(plugin.getDataFolder(), "stats.yml");
        if (!f.isFile()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        Set<String> keys = y.getKeys(false);
        int n = 0;
        for (String k : keys) {
            try {
                UUID id = UUID.fromString(k);
                Entry e = new Entry(y.getString(k + ".name", k.substring(0, 8)));
                e.d[0] = y.getInt(k + ".kills");
                e.d[1] = y.getInt(k + ".deaths");
                e.d[2] = y.getInt(k + ".wins");
                e.d[3] = y.getInt(k + ".games");
                db.execute(() -> write(id, e));
                n++;
            } catch (IllegalArgumentException ignored) {
            }
        }
        f.renameTo(new File(plugin.getDataFolder(), "stats.yml.importiert"));
        plugin.getLogger().info(n + " Spieler aus stats.yml übernommen.");
    }

    // ================================================================= Backups

    public void backup(CommandSender sender) {
        if (backupBusy) {
            if (sender != null) Txt.send(sender, plugin.settings().prefix + "§eBackup läuft bereits.");
            return;
        }
        backupBusy = true;
        flush(false);
        plugin.saveConfig();
        int keep = plugin.settings().backupKeep;
        File dir = new File(plugin.getDataFolder(), "backups");
        db.execute(() -> { // nach dem Flush, im DB-Thread
            String msg;
            try {
                dir.mkdirs();
                String base = "backup-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
                File partial = new File(dir, base + ".partial");
                try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(partial))) {
                    for (String n : List.of("config.yml", "arena.yml", "kits.yml", "stats.db")) {
                        File f = new File(plugin.getDataFolder(), n);
                        if (!f.isFile()) continue;
                        zip.putNextEntry(new ZipEntry(n));
                        zip.write(Files.readAllBytes(f.toPath()));
                        zip.closeEntry();
                    }
                    zip.putNextEntry(new ZipEntry("LIESMICH.txt"));
                    zip.write("ODVMeetup-Datenbackup (keine Welten, MySQL-Daten liegen in deiner Datenbank).\n".getBytes());
                    zip.closeEntry();
                }
                File target = new File(dir, base + ".zip");
                Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                File[] all = dir.listFiles((d, name) -> name.startsWith("backup-") && name.endsWith(".zip"));
                if (all != null) {
                    List<File> l = new ArrayList<>(List.of(all));
                    l.sort(Collections.reverseOrder());
                    for (int i = keep; i < l.size(); i++) Files.deleteIfExists(l.get(i).toPath());
                }
                msg = "§aBackup erstellt: §f" + target.getName();
            } catch (IOException e) {
                msg = "§cBackup fehlgeschlagen: " + e.getMessage();
            }
            String m = msg;
            if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> {
                backupBusy = false;
                if (sender != null) Txt.send(sender, plugin.settings().prefix + m);
                else plugin.getLogger().info(m.replaceAll("§.", ""));
            });
        });
    }
}
