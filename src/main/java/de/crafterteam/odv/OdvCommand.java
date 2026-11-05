package de.crafterteam.odv;

import de.crafterteam.odv.data.Kits;
import de.crafterteam.odv.data.Stats;
import de.crafterteam.odv.game.GameState;
import de.crafterteam.odv.gui.Menus;
import de.crafterteam.odv.gui.SetupGui;
import de.crafterteam.odv.schematic.Schematic;
import de.crafterteam.odv.team.MTeam;
import de.crafterteam.odv.util.Txt;
import de.crafterteam.odv.world.ChunkPregen;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OdvCommand implements TabExecutor {

    private final OdvPlugin plugin;

    public OdvCommand(OdvPlugin plugin) {
        this.plugin = plugin;
    }

    private Settings s() {
        return plugin.settings();
    }

    private void tell(CommandSender to, String msg) {
        Txt.send(to, s().prefix + msg);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Player p = sender instanceof Player pl ? pl : null;
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "team" -> {
                if (p != null) team(p, args);
                return true;
            }
            case "practice" -> {
                if (p != null) plugin.practice().toggle(p);
                return true;
            }
            case "szenarios" -> {
                if (p != null) Menus.scenarios(plugin, p);
                else tell(sender, "§7Szenarien: §e" + plugin.scenarios().activeList());
                return true;
            }
            case "backpack" -> {
                if (p != null) plugin.scenarios().openBackpack(p);
                return true;
            }
            case "ping" -> {
                if (p != null) plugin.features().ping(p);
                return true;
            }
            case "coords" -> {
                if (p != null) coords(p);
                return true;
            }
            case "lb" -> {
                if (p != null) Menus.leaderboard(plugin, p, 0);
                return true;
            }
            case "teamliste" -> {
                plugin.ranks().teamList(sender);
                return true;
            }
            case "changelog" -> {
                showChangelog(sender, args.length >= 1 ? parse(args[0], 1) : 1);
                return true;
            }
            case "sc" -> {
                if (!(p == null || plugin.ranks().isStaff(p))) {
                    tell(sender, "§cNur für Team-Mitglieder.");
                } else if (args.length == 0) {
                    tell(sender, "§e/sc <Nachricht> §7oder §e#Nachricht §7im Chat");
                } else staffChat(sender, String.join(" ", args));
                return true;
            }
            case "mute", "unmute" -> {
                if (!sender.hasPermission("odv.mute")) {
                    tell(sender, "§cKeine Berechtigung.");
                } else if (args.length == 0) {
                    tell(sender, "§e/mute <Spieler> [Minuten] §8| §e/unmute <Spieler>");
                } else {
                    int min = cmd.getName().equalsIgnoreCase("unmute") ? 0 : args.length >= 2 ? Math.max(1, parse(args[1], 10)) : 10;
                    plugin.ranks().mute(sender, args[0], min);
                }
                return true;
            }
            case "rank" -> {
                rank(sender, args);
                return true;
            }
            default -> {
            }
        }
        if (args.length == 0) {
            if (p != null && p.hasPermission("odv.admin")) SetupGui.open(plugin, p, SetupGui.Page.MAIN);
            else help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);

        // ---------------- für alle
        switch (sub) {
            case "help", "hilfe" -> {
                help(sender);
                return true;
            }
            case "teams" -> {
                if (p != null) Menus.teams(plugin, p);
                return true;
            }
            case "join", "leave" -> {
                if (p == null) return true;
                if (sub.equals("join") == plugin.game().isOptedOut(p)) plugin.game().toggleParticipation(p);
                else tell(p, sub.equals("join") ? "§7Du spielst bereits mit." : "§7Du schaust bereits zu.");
                return true;
            }
            case "spec", "spectate" -> {
                if (p != null) Menus.spectate(plugin, p, 0);
                return true;
            }
            case "bet", "wette" -> {
                if (p != null) Menus.bet(plugin, p);
                return true;
            }
            case "vote" -> {
                if (p != null && args.length >= 2) plugin.features().vote(p, parse(args[1], 0));
                return true;
            }
            case "stats" -> {
                showStats(sender, args.length >= 2 ? args[1] : p != null ? p.getName() : null);
                return true;
            }
            case "top" -> {
                String col = args.length >= 2 ? switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "kills" -> "kills";
                    case "wetten", "bets" -> "bets";
                    case "practice" -> "p_kills";
                    default -> "wins";
                } : "wins";
                plugin.stats().top(col, 10, list -> {
                    Txt.send(sender, "§8§m        §r §b§lTop 10 – " + switch (col) {
                        case "kills" -> "Kills";
                        case "bets" -> "Wettpunkte";
                        case "p_kills" -> "Practice-Kills";
                        default -> "Siege";
                    } + " §8§m        ");
                    int i = 1;
                    for (Map.Entry<String, Integer> e : list) Txt.send(sender, (i <= 3 ? "§6" : "§7") + "#" + i++ + " §f" + e.getKey() + " §8- §e" + e.getValue());
                    if (list.isEmpty()) Txt.send(sender, "§7Noch keine Einträge.");
                });
                return true;
            }
            default -> {
            }
        }
        if (!sender.hasPermission("odv.admin")) {
            help(sender);
            return true;
        }

        // ---------------- Admin
        switch (sub) {
            case "setup", "menu" -> {
                if (needPlayer(sender)) SetupGui.open(plugin, p, SetupGui.Page.MAIN);
            }
            case "quick", "schnellsetup" -> {
                if (lobby(sender)) plugin.spawns().quickSetup(sender);
            }
            case "resetworld", "neuewelt" -> {
                if (!lobby(sender)) return true;
                if (plugin.arena().getWorld() == null) tell(sender, "§cKeine Arena vorhanden – nutze /odv quick.");
                else plugin.spawns().resetArena(sender);
            }
            case "start" -> {
                int sec = args.length >= 2 ? Math.max(1, parse(args[1], 10)) : 10;
                String err = plugin.game().forceStart(sec);
                if (err != null) tell(sender, err);
                else {
                    if (plugin.game().getHost() == null && p != null) plugin.game().setHost(p.getUniqueId());
                    plugin.game().broadcast("§aRunde startet in §e" + sec + " §aSekunden. §8(" + sender.getName() + ")");
                }
            }
            case "stop", "reset" -> plugin.game().stop();
            case "host" -> {
                if (p != null) {
                    plugin.game().setHost(p.getUniqueId());
                    tell(sender, "§aDu bist jetzt Host.");
                }
            }
            case "preset" -> {
                if (!lobby(sender) || args.length < 2) {
                    tell(sender, "§e/odv preset odv|meetup");
                    return true;
                }
                s().preset(args[1].equalsIgnoreCase("odv") ? Settings.Mode.ODV : Settings.Mode.MEETUP);
                plugin.game().applyWorldSettings();
                plugin.game().refreshHolograms();
                tell(sender, "§aPreset " + s().mode.display + " geladen.");
            }
            case "setlobby" -> {
                if (needPlayer(sender) && lobby(sender)) {
                    plugin.arena().setLobby(p.getLocation());
                    plugin.game().refreshHolograms();
                    tell(sender, "§aLobby gesetzt.");
                }
            }
            case "setcenter" -> {
                if (needPlayer(sender) && lobby(sender)) {
                    plugin.arena().setCenter(p.getLocation());
                    plugin.game().applyWorldSettings();
                    plugin.game().refreshHolograms();
                    tell(sender, "§aMitte gesetzt.");
                }
            }
            case "setpractice" -> {
                if (needPlayer(sender)) {
                    plugin.arena().setPractice(p.getLocation());
                    plugin.game().resetToLobbyItems();
                    tell(sender, "§aPractice-Spawn gesetzt.");
                }
            }
            case "build" -> {
                if (needPlayer(sender) && lobby(sender)) {
                    p.setGameMode(p.getGameMode() == GameMode.CREATIVE ? GameMode.ADVENTURE : GameMode.CREATIVE);
                    tell(sender, "§7Baumodus: " + (p.getGameMode() == GameMode.CREATIVE ? "§aan" : "§caus"));
                }
            }
            case "kit" -> kit(sender, p, args);
            case "wand" -> {
                if (needPlayer(sender)) p.getInventory().addItem(plugin.wand().item());
            }
            case "schem", "schematic" -> schem(sender, p, args);
            case "spawns" -> spawns(sender, p, args);
            case "announce" -> {
                if (args.length < 2) {
                    tell(sender, "§e/odv announce <Text> §7– Chat + Discord");
                } else {
                    String text = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                    for (Player o : Bukkit.getOnlinePlayers()) {
                        Txt.title(o, "§6§lAnkündigung", "§f" + text);
                    }
                    plugin.game().broadcast("§6§lAnkündigung: §f" + text);
                    if (plugin.discord().enabled()) plugin.discord().announce(text, sender);
                }
            }
            case "changelog" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("post")) {
                    var v = changelog().get(0);
                    plugin.discord().changelog(v.getKey(), v.getValue(), sender);
                } else showChangelog(sender, 1);
            }
            case "supply" -> {
                if (plugin.game().getState().isIngame()) plugin.features().supplyDrop();
                else tell(sender, "§cNur während einer Runde.");
            }
            case "xray" -> plugin.antiXray().show(sender, args.length >= 2 ? args[1] : null);
            case "antixray" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("paper")) plugin.antiXray().enablePaper(sender);
                else tell(sender, "§7Paper-Anti-Xray: " + plugin.antiXray().paperStatus() + " §8| §e/odv antixray paper §7zum Aktivieren");
            }
            case "pregen" -> {
                World w = plugin.arena().getWorld();
                if (ChunkPregen.isRunning()) ChunkPregen.stop();
                else if (w != null) {
                    Location c = plugin.arena().getCenter();
                    ChunkPregen.start(plugin, w, c.getBlockX(), c.getBlockZ(), s().borderInitial / 2 + 48, p);
                } else tell(sender, "§cZuerst Arena einrichten.");
            }
            case "backup" -> plugin.stats().backup(sender);
            case "reload" -> {
                if (lobby(sender)) {
                    plugin.reloadAll();
                    tell(sender, "§aKonfiguration neu geladen.");
                }
            }
            default -> help(sender);
        }
        return true;
    }

    // ------------------------------------------------------------ Teile

    private void team(Player p, String[] args) {
        if (args.length == 0) {
            Menus.teams(plugin, p);
            return;
        }
        if (!plugin.game().getState().isLobby()) {
            tell(p, "§cTeams nur in der Lobby.");
            return;
        }
        if (args[0].equalsIgnoreCase("leave")) plugin.teams().leave(p.getUniqueId());
        else if (args[0].equalsIgnoreCase("random")) {
            if (!plugin.teams().joinRandom(p)) tell(p, "§cAlle Teams sind voll.");
        } else {
            MTeam t = plugin.teams().byNumber(parse(args[0], -1));
            if (t == null) tell(p, "§e/team <1-" + plugin.teams().getTeams().size() + "|random|leave>");
            else if (!plugin.teams().join(p, t)) tell(p, "§cDieses Team ist voll.");
        }
        MTeam now = plugin.teams().getTeam(p);
        tell(p, now == null ? "§7Kein Team." : "§7Dein Team: " + now.getDisplayName());
        Menus.refreshAllTeams(plugin);
    }

    public void showStats(CommandSender to, String name) {
        if (name == null) return;
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            Stats.Entry e = plugin.stats().get(online.getUniqueId());
            print(to, online.getName(), e.v);
            return;
        }
        plugin.stats().lookup(name, v -> {
            if (v == null) tell(to, "§cSpieler nicht gefunden.");
            else print(to, name, v);
        });
    }

    private void print(CommandSender to, String name, int[] v) {
        int k = v[0], d = v[1];
        String kd = d == 0 ? String.valueOf(k) : String.format(Locale.ROOT, "%.2f", k / (double) d);
        Txt.send(to, "§8§m        §r §b§l" + name + " §8§m        ");
        Txt.send(to, "§7Siege: §6" + v[2] + " §8| §7Runden: §f" + v[3] + " §8| §7Wettpunkte: §a" + v[4]);
        Txt.send(to, "§7Kills: §c" + k + " §8| §7Tode: §7" + d + " §8| §7K/D: §e" + kd);
        Txt.send(to, "§7Practice: §a" + v[5] + " Kills §8/ §c" + v[6] + " Tode");
    }

    private void kit(CommandSender sender, Player p, String[] args) {
        if (!needPlayer(sender) || !lobby(sender)) return;
        if (args.length < 2) {
            tell(sender, "§e/odv kit save|give|reset [meetup|odv|practice]");
            return;
        }
        Kits.Type t = s().mode == Settings.Mode.ODV ? Kits.Type.ODV : Kits.Type.MEETUP;
        if (args.length >= 3) {
            try {
                t = Kits.Type.valueOf(args[2].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        try {
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "save" -> {
                    plugin.kits().save(p, t);
                    tell(sender, "§aKit " + t + " gespeichert.");
                }
                case "give" -> plugin.kits().give(p, t);
                case "reset" -> {
                    plugin.kits().reset(t);
                    tell(sender, "§7Standard-Kit " + t + " aktiv.");
                }
                default -> tell(sender, "§e/odv kit save|give|reset [meetup|odv|practice]");
            }
        } catch (IOException e) {
            tell(sender, "§c" + e.getMessage());
        }
    }

    private void schem(CommandSender sender, Player p, String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "list";
        String name = args.length >= 3 ? args[2].replace(".schem", "").replaceAll("[^a-zA-Z0-9_\\-]", "") : null;
        switch (action) {
            case "list" -> {
                List<String> l = plugin.spawns().list();
                tell(sender, "§7Schematics: §f" + (l.isEmpty() ? "-" : String.join("§7, §f", l)));
                tell(sender, "§8Ordner: plugins/ODVMeetup/schematics (.schem aus WorldEdit 7)");
            }
            case "save" -> {
                if (!needPlayer(sender) || name == null || name.isEmpty()) {
                    tell(sender, "§e/odv schem save <Name> §7– \"lobby\" als Name ersetzt die Lobby");
                    return;
                }
                Location a = plugin.wand().getPos1(p), b = plugin.wand().getPos2(p);
                if (a == null || b == null || a.getWorld() != b.getWorld()) {
                    tell(sender, "§cZuerst Pos1 & Pos2 mit §e/odv wand §csetzen.");
                    return;
                }
                try {
                    Schematic sc = Schematic.copy(a, b, p.getLocation());
                    sc.save(plugin.spawns().file(name));
                    if (!name.equalsIgnoreCase("lobby")) {
                        s().teamSchematic = name;
                        s().save();
                    }
                    tell(sender, "§aGespeichert: §e" + name + " §7(" + sc.getWidth() + "×" + sc.getHeight() + "×" + sc.getLength() + ")"
                            + (name.equalsIgnoreCase("lobby") ? " §7– wird beim nächsten Schnellsetup als Lobby genutzt." : " §7– als Team-Plattform gesetzt."));
                } catch (IOException e) {
                    tell(sender, "§c" + e.getMessage());
                }
            }
            case "paste" -> {
                if (!needPlayer(sender)) return;
                try {
                    plugin.spawns().pasteAt(plugin.spawns().load(name != null ? name : s().teamSchematic), p.getLocation(), p);
                } catch (IOException e) {
                    tell(sender, "§c" + e.getMessage());
                }
            }
            case "delete" -> {
                File f = name == null ? null : plugin.spawns().file(name);
                tell(sender, f != null && f.delete() ? "§aGelöscht." : "§cNicht gefunden.");
            }
            default -> tell(sender, "§e/odv schem <list|save|paste|delete> [Name]");
        }
    }

    private void spawns(CommandSender sender, Player p, String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "list";
        switch (action) {
            case "build", "generate" -> {
                if (!lobby(sender)) return;
                int count = args.length >= 3 ? parse(args[2], 0) : 0;
                if (count <= 0) {
                    int c = plugin.game().candidates().size();
                    count = Math.max(2, s().isSolo() ? c : plugin.teams().countTeamsAfterFill(c));
                }
                plugin.spawns().generate(Math.min(500, count), sender, null);
            }
            case "undo", "remove" -> plugin.spawns().removePasted(sender);
            case "tp" -> {
                if (!needPlayer(sender)) return;
                int nr = args.length >= 3 ? parse(args[2], 1) : 1;
                List<Location> l = plugin.arena().getSpawns();
                if (nr < 1 || nr > l.size()) tell(sender, "§cSpawn #" + nr + " gibt es nicht (" + l.size() + ").");
                else p.teleport(l.get(nr - 1).clone().add(0, 0.2, 0));
            }
            case "clear" -> {
                plugin.arena().setSpawns(new ArrayList<>());
                tell(sender, "§aGespeicherte Spawnpunkte gelöscht.");
            }
            default -> tell(sender, "§7Spawns: §e" + plugin.arena().getSpawns().size() + " §8| §7Art: §f" + s().spawnStyle.display
                    + " §8| §e/odv spawns build [Anzahl] | tp <Nr> | clear | undo");
        }
    }

    // ------------------------------------------------------------ Rang / Staff / Coords / Changelog

    private void rank(CommandSender sender, String[] args) {
        String a = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        switch (a) {
            case "set" -> {
                if (!sender.hasPermission("odv.rank")) {
                    tell(sender, "§cKeine Berechtigung.");
                } else if (args.length < 3) {
                    tell(sender, "§e/rank set <Spieler> <Rang>");
                } else plugin.ranks().set(sender, args[1], args[2]);
            }
            case "remove" -> {
                if (!sender.hasPermission("odv.rank")) tell(sender, "§cKeine Berechtigung.");
                else if (args.length < 2) tell(sender, "§e/rank remove <Spieler>");
                else plugin.ranks().set(sender, args[1], plugin.ranks().all().keySet().stream()
                            .reduce((x, y) -> y).orElse("spieler")); // niedrigster Rang
            }
            case "list" -> {
                Txt.send(sender, "§8§m        §r §b§lRänge §8§m        ");
                plugin.ranks().ranksList(line -> Txt.send(sender, line));
            }
            case "reload" -> {
                if (!sender.hasPermission("odv.rank")) {
                    tell(sender, "§cKeine Berechtigung.");
                    return;
                }
                plugin.ranks().load();
                for (Player o : Bukkit.getOnlinePlayers()) {
                    plugin.ranks().apply(o);
                    plugin.game().onRankChanged(o);
                }
                tell(sender, "§aranks.yml neu geladen.");
            }
            default -> {
                Player t = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player pl ? pl : null;
                if (t == null) {
                    tell(sender, "§e/rank info <Spieler> §8| §e/rank list §8| §e/rank set <Spieler> <Rang> §8| §e/rank remove <Spieler>");
                    return;
                }
                var r = plugin.ranks().of(t);
                tell(sender, "§f" + t.getName() + " §7hat den Rang " + r.prefix() + r.name() + (r.staff() ? " §8(Team)" : ""));
            }
        }
    }

    public void staffChat(CommandSender from, String msg) {
        String name = from instanceof Player p ? plugin.ranks().of(p).prefix() + p.getName() : "§4Konsole";
        var out = Txt.c("§8[§c§lStaff§8] " + name + " §8» §f").append(net.kyori.adventure.text.Component.text(msg));
        for (Player o : Bukkit.getOnlinePlayers()) if (plugin.ranks().isStaff(o)) o.sendMessage(out);
        plugin.getLogger().info("[Staff] " + Txt.ascii(name) + ": " + msg);
    }

    private void coords(Player p) {
        if (!plugin.game().isPlaying(p)) {
            tell(p, "§cNur für lebende Spieler in einer Runde.");
            return;
        }
        MTeam t = plugin.teams().getTeam(p);
        Location l = p.getLocation();
        String world = switch (l.getWorld().getEnvironment()) {
            case NETHER -> "§cNether";
            case THE_END -> "§5End";
            default -> "§aOberwelt";
        };
        String msg = "§8[§bCoords§8] " + (t != null ? t.getColor() : "§f") + p.getName() + " §8» §e"
                + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + " §8(" + world + "§8)";
        if (t == null || s().isSolo()) {
            Txt.send(p, msg);
            return;
        }
        for (java.util.UUID u : t.getMembers()) {
            Player o = Bukkit.getPlayer(u);
            if (o != null) Txt.send(o, msg);
        }
    }

    /** Changelog: eigene Datei in plugins/ODVMeetup/changelog.yml, sonst die mitgelieferte. */
    private List<Map.Entry<String, List<String>>> changelog() {
        org.bukkit.configuration.file.YamlConfiguration y;
        File f = new File(plugin.getDataFolder(), "changelog.yml");
        if (f.isFile()) y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
        else {
            var in = plugin.getResource("changelog.yml");
            y = in == null ? new org.bukkit.configuration.file.YamlConfiguration()
                    : org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        }
        List<Map.Entry<String, List<String>>> out = new ArrayList<>();
        for (Map<?, ?> m : y.getMapList("versions")) {
            Object v = m.get("version"), d = m.get("date"), c = m.get("changes");
            List<String> changes = new ArrayList<>();
            if (c instanceof List<?> l) for (Object o : l) changes.add(String.valueOf(o));
            out.add(new java.util.AbstractMap.SimpleEntry<>(v + (d != null ? " – " + d : ""), changes));
        }
        if (out.isEmpty()) out.add(new java.util.AbstractMap.SimpleEntry<>(plugin.getPluginMeta().getVersion(), List.of("Keine Einträge")));
        return out;
    }

    private void showChangelog(CommandSender to, int count) {
        List<Map.Entry<String, List<String>>> all = changelog();
        for (int i = 0; i < Math.min(Math.max(1, count), all.size()); i++) {
            Txt.send(to, "§8§m      §r §b§lChangelog §7[" + all.get(i).getKey() + "] §8§m      ");
            for (String c : all.get(i).getValue()) Txt.send(to, " §8• §7" + c);
        }
        if (all.size() > 1 && count <= 1) Txt.send(to, "§8Ältere Versionen: §7/changelog " + all.size());
    }

    private boolean needPlayer(CommandSender s) {
        if (s instanceof Player) return true;
        s.sendMessage("Nur ingame.");
        return false;
    }

    private boolean lobby(CommandSender s) {
        if (plugin.game().getState() == GameState.LOBBY && !plugin.spawns().isBusy()) return true;
        tell(s, "§cGeht nur in der Lobby (und wenn gerade nichts gebaut wird).");
        return false;
    }

    private static int parse(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void help(CommandSender s) {
        Txt.send(s, "§8§m          §r " + s().mode.title + " §8§m          ");
        Txt.send(s, "§b/team §7[Nr|random|leave] §8| §b/practice §8| §b/szenarios");
        Txt.send(s, "§b/odv join §8| §b/odv leave §8| §b/odv spec §8| §b/odv bet");
        Txt.send(s, "§b/odv stats §7[Name] §8| §b/odv top §7[kills|wetten|practice]");
        Txt.send(s, "§b/ping §7oder §bSchleichen + F §8| §b/backpack §8| §b/coords §8| §b/lb");
        Txt.send(s, "§b/teamliste §8| §b/changelog §8| §b/rank info");
        if (s.hasPermission("odv.admin")) {
            Txt.send(s, "§c/odv §8- §7Setup-Menü §8| §c/odv quick §8- §7Schnellsetup §8| §c/odv resetworld §8- §7Welt neu");
            Txt.send(s, "§c/odv start §7[Sek] §8| §c/odv stop §8| §c/odv host §8| §c/odv preset odv|meetup");
            Txt.send(s, "§c/odv setlobby|setcenter|setpractice|build");
            Txt.send(s, "§c/odv wand §8→ §c/odv schem save <Name> §8| §c/odv schem list|paste|delete");
            Txt.send(s, "§c/odv spawns build|tp|clear|undo §8| §c/odv kit save|give|reset §7[typ]");
            Txt.send(s, "§c/odv xray §7[Name] §8| §c/odv antixray paper §8| §c/odv supply §8| §c/odv pregen|backup|reload");
            Txt.send(s, "§c/rank set <Spieler> <Rang> §8| §c/sc §7oder §c#Text §8| §c/mute §8| §c/odv announce §8| §c/odv changelog post");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> o = new ArrayList<>();
        String c = cmd.getName().toLowerCase(Locale.ROOT);
        if (c.equals("team")) {
            if (args.length == 1) {
                o.add("random");
                o.add("leave");
                for (MTeam t : plugin.teams().getTeams()) o.add(String.valueOf(t.getNumber()));
            }
        } else if (c.equals("rank")) {
            if (args.length == 1) o.addAll(List.of("info", "list", "set", "remove", "reload"));
            else if (args.length == 2) Bukkit.getOnlinePlayers().forEach(pl -> o.add(pl.getName()));
            else if (args.length == 3 && args[0].equalsIgnoreCase("set")) o.addAll(plugin.ranks().all().keySet());
        } else if (c.equals("mute") || c.equals("unmute")) {
            if (args.length == 1) Bukkit.getOnlinePlayers().forEach(pl -> o.add(pl.getName()));
        } else if (c.equals("odv")) {
            if (args.length == 1) {
                o.addAll(List.of("help", "teams", "join", "leave", "spec", "bet", "stats", "top"));
                if (sender.hasPermission("odv.admin")) o.addAll(List.of("setup", "quick", "resetworld", "start", "stop", "host", "preset", "setlobby",
                        "setcenter", "setpractice", "build", "kit", "wand", "schem", "spawns", "supply", "xray", "antixray", "pregen", "backup", "reload",
                        "announce", "changelog"));
            } else if (args.length == 2) {
                switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "preset" -> o.addAll(List.of("odv", "meetup"));
                    case "kit" -> o.addAll(List.of("save", "give", "reset"));
                    case "schem", "schematic" -> o.addAll(List.of("list", "save", "paste", "delete"));
                    case "spawns" -> o.addAll(List.of("build", "tp", "clear", "undo"));
                    case "top" -> o.addAll(List.of("kills", "wetten", "practice"));
                    case "antixray" -> o.add("paper");
                    case "stats", "xray" -> Bukkit.getOnlinePlayers().forEach(pl -> o.add(pl.getName()));
                    default -> {
                    }
                }
            } else if (args.length == 3) {
                if (args[0].equalsIgnoreCase("kit")) o.addAll(List.of("meetup", "odv", "practice"));
                if (args[0].toLowerCase(Locale.ROOT).startsWith("schem") && !args[1].equalsIgnoreCase("save")) o.addAll(plugin.spawns().list());
            }
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        o.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return o;
    }
}
