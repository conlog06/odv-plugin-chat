package de.crafterteam.odv.data;

import de.crafterteam.odv.OdvPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * stats.yml (Kills, Tode, Siege, Runden) + ZIP-Backups der Plugin-Daten.
 */
public class Stats {

    private final OdvPlugin plugin;
    private final File file;
    private final YamlConfiguration y;
    private boolean backupBusy;

    public Stats(OdvPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
        this.y = YamlConfiguration.loadConfiguration(file);
    }

    public void name(Player p) {
        y.set(p.getUniqueId() + ".name", p.getName());
    }

    public String name(UUID id) {
        return y.getString(id + ".name", id.toString().substring(0, 8));
    }

    public int get(UUID id, String key) {
        return y.getInt(id + "." + key);
    }

    public void add(UUID id, String key) {
        y.set(id + "." + key, get(id, key) + 1);
    }

    public UUID find(String name) {
        for (String k : y.getKeys(false)) {
            if (name.equalsIgnoreCase(y.getString(k + ".name"))) {
                try {
                    return UUID.fromString(k);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return null;
    }

    /** Top-Liste nach key (z. B. "wins" oder "kills"). */
    public List<Map.Entry<String, Integer>> top(final String key, int n) {
        List<Map.Entry<String, Integer>> list = new ArrayList<Map.Entry<String, Integer>>();
        for (String k : y.getKeys(false)) {
            int v = y.getInt(k + "." + key);
            if (v > 0) list.add(new java.util.AbstractMap.SimpleEntry<String, Integer>(y.getString(k + ".name", k), v));
        }
        Collections.sort(list, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return b.getValue() - a.getValue();
            }
        });
        return list.subList(0, Math.min(n, list.size()));
    }

    public void save() {
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("stats.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    // ---------------- Backups ----------------

    public void backup(final CommandSender sender) {
        if (backupBusy) {
            if (sender != null) sender.sendMessage(plugin.settings().prefix + "§eBackup läuft bereits.");
            return;
        }
        save();
        plugin.saveConfig();
        final Map<String, byte[]> snapshot = new LinkedHashMap<String, byte[]>();
        try {
            for (String n : Arrays.asList("config.yml", "arena.yml", "kits.yml", "stats.yml")) {
                File f = new File(plugin.getDataFolder(), n);
                if (f.isFile()) snapshot.put(n, Files.readAllBytes(f.toPath()));
            }
            snapshot.put("LIESMICH.txt", ("ODVMeetup-Datenbackup (keine Welten). Server stoppen, ZIP in plugins/ODVMeetup entpacken, starten.\r\n")
                    .getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            if (sender != null) sender.sendMessage(plugin.settings().prefix + "§cBackup fehlgeschlagen: " + e.getMessage());
            return;
        }
        backupBusy = true;
        final int keep = plugin.settings().backupKeep;
        final File dir = new File(plugin.getDataFolder(), "backups");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                String result;
                try {
                    dir.mkdirs();
                    String base = "backup-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
                    File partial = new File(dir, base + ".partial");
                    ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(partial));
                    try {
                        for (Map.Entry<String, byte[]> e : snapshot.entrySet()) {
                            zip.putNextEntry(new ZipEntry(e.getKey()));
                            zip.write(e.getValue());
                            zip.closeEntry();
                        }
                    } finally {
                        zip.close();
                    }
                    File target = new File(dir, base + ".zip");
                    Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    File[] files = dir.listFiles();
                    if (files != null) {
                        List<File> zips = new ArrayList<File>();
                        for (File f : files) if (f.getName().startsWith("backup-") && f.getName().endsWith(".zip")) zips.add(f);
                        Collections.sort(zips, Collections.reverseOrder());
                        for (int i = keep; i < zips.size(); i++) Files.deleteIfExists(zips.get(i).toPath());
                    }
                    result = "§aBackup erstellt: §f" + target.getName();
                } catch (IOException e) {
                    result = "§cBackup fehlgeschlagen: " + e.getMessage();
                }
                final String msg = result;
                if (plugin.isEnabled()) {
                    Bukkit.getScheduler().runTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            backupBusy = false;
                            if (sender != null) sender.sendMessage(plugin.settings().prefix + msg);
                            else plugin.getLogger().info(msg.replaceAll("§.", ""));
                        }
                    });
                }
            }
        });
    }
}
