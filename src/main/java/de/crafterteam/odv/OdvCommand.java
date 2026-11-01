package de.crafterteam.odv;

import de.crafterteam.odv.game.GameManager;
import de.crafterteam.odv.game.GameState;
import de.crafterteam.odv.gui.SetupGui;
import de.crafterteam.odv.gui.SpectatorGui;
import de.crafterteam.odv.gui.TeamGui;
import de.crafterteam.odv.schematic.Schematic;
import de.crafterteam.odv.schematic.Wand;
import de.crafterteam.odv.team.MTeam;
import de.crafterteam.odv.util.ChunkPregen;
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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class OdvCommand implements TabExecutor {

    private final OdvPlugin plugin;

    public OdvCommand(OdvPlugin plugin) {
        this.plugin = plugin;
    }

    private Settings s() {
        return plugin.settings();
    }

    private void tell(CommandSender to, String msg) {
        to.sendMessage(s().prefix + msg);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Player p = sender instanceof Player ? (Player) sender : null;
        if (cmd.getName().equalsIgnoreCase("team")) {
            if (p != null) team(p, args);
            return true;
        }
        if (args.length == 0) {
            if (p != null && p.hasPermission("odv.admin")) SetupGui.open(plugin, p, SetupGui.Page.MAIN);
            else help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);

        // ---------- für alle ----------
        switch (sub) {
            case "help":
            case "hilfe":
                help(sender);
                return true;
            case "teams":
                if (p != null) TeamGui.open(plugin, p);
                return true;
            case "join":
            case "leave":
                if (p == null) return true;
                boolean watching = plugin.game().isOptedOut(p);
                if (sub.equals("join") == watching) plugin.game().toggleParticipation(p);
                else tell(p, sub.equals("join") ? "§7Du spielst bereits mit." : "§7Du schaust bereits zu.");
                return true;
            case "spec":
            case "spectate":
                if (p != null) SpectatorGui.open(plugin, p, 0);
                return true;
            case "stats": {
                UUID id = p != null ? p.getUniqueId() : null;
                if (args.length >= 2) id = plugin.stats().find(args[1]);
                if (id == null) tell(sender, "§cSpieler nicht gefunden.");
                else showStats(sender, id);
                return true;
            }
            case "top": {
                String key = args.length >= 2 && args[1].equalsIgnoreCase("kills") ? "kills" : "wins";
                sender.sendMessage("§8§m        §r §b§lTop 10 – " + (key.equals("wins") ? "Siege" : "Kills") + " §8§m        ");
                int i = 1;
                for (Map.Entry<String, Integer> e : plugin.stats().top(key, 10)) {
                    sender.sendMessage((i <= 3 ? "§6" : "§7") + "#" + i++ + " §f" + e.getKey() + " §8- §e" + e.getValue());
                }
                if (i == 1) sender.sendMessage("§7Noch keine Einträge.");
                return true;
            }
            default:
                break;
        }

        if (!sender.hasPermission("odv.admin")) {
            help(sender);
            return true;
        }

        // ---------- Admin ----------
        switch (sub) {
            case "setup":
            case "menu":
                if (needPlayer(sender)) SetupGui.open(plugin, p, SetupGui.Page.MAIN);
                break;
            case "quick":
            case "schnellsetup":
                if (needPlayer(sender) && lobby(sender)) plugin.spawns().quickSetup(p);
                break;
            case "start": {
                int sec = 10;
                if (args.length >= 2) sec = parse(args[1], 10);
                String err = plugin.game().forceStart(Math.max(1, sec));
                if (err != null) tell(sender, err);
                else plugin.game().broadcast("§aRunde startet in §e" + sec + " §aSekunden. §8(" + sender.getName() + ")");
                break;
            }
            case "stop":
            case "reset":
                plugin.game().stop();
                break;
            case "preset":
                if (!lobby(sender)) break;
                if (args.length < 2) {
                    tell(sender, "§e/odv preset odv|meetup");
                    break;
                }
                s().preset(args[1].equalsIgnoreCase("odv") ? Settings.Mode.ODV : Settings.Mode.MEETUP);
                plugin.game().applyWorldSettings();
                tell(sender, "§aPreset " + s().mode.display + " geladen.");
                break;
            case "setlobby":
                if (needPlayer(sender) && lobby(sender)) {
                    plugin.arena().setLobby(p.getLocation());
                    plugin.game().applyWorldSettings();
                    tell(sender, "§aLobby gesetzt.");
                }
                break;
            case "setcenter":
                if (needPlayer(sender) && lobby(sender)) {
                    try {
                        plugin.arena().setCenter(p.getLocation());
                        plugin.game().applyWorldSettings();
                        tell(sender, "§aMitte gesetzt.");
                    } catch (IllegalArgumentException e) {
                        tell(sender, "§c" + e.getMessage());
                    }
                }
                break;
            case "build":
                if (needPlayer(sender) && lobby(sender)) {
                    p.setGameMode(p.getGameMode() == GameMode.CREATIVE ? GameMode.ADVENTURE : GameMode.CREATIVE);
                    tell(sender, "§7Baumodus: " + (p.getGameMode() == GameMode.CREATIVE ? "§aan" : "§caus"));
                }
                break;
            case "kit":
                kit(sender, p, args);
                break;
            case "wand":
                if (needPlayer(sender)) {
                    p.getInventory().addItem(Wand.item());
                    tell(sender, "§7Wand erhalten: §eLinks §7= Pos1, §eRechts §7= Pos2.");
                }
                break;
            case "schem":
            case "schematic":
                schem(sender, p, args);
                break;
            case "spawns":
                spawns(sender, p, args);
                break;
            case "pregen": {
                World w = plugin.arena().getWorld();
                if (ChunkPregen.isRunning()) {
                    ChunkPregen.stop();
                    tell(sender, "§cVorgenerierung gestoppt.");
                } else if (w == null) {
                    tell(sender, "§cZuerst Arena einrichten.");
                } else {
                    Location c = plugin.arena().getCenter();
                    ChunkPregen.start(plugin, w, c.getBlockX(), c.getBlockZ(), s().borderInitial / 2 + 32, p);
                }
                break;
            }
            case "backup":
                plugin.stats().backup(sender);
                break;
            case "reload":
                if (!lobby(sender)) break;
                plugin.reloadAll();
                tell(sender, "§aKonfiguration neu geladen.");
                break;
            default:
                help(sender);
        }
        return true;
    }

    // ------------------------------------------------------------------ Teile

    private void team(Player p, String[] args) {
        if (args.length == 0) {
            TeamGui.open(plugin, p);
            return;
        }
        if (!plugin.game().getState().isLobby()) {
            tell(p, "§cTeams nur in der Lobby.");
            return;
        }
        if (args[0].equalsIgnoreCase("leave")) {
            plugin.teams().leave(p.getUniqueId());
            tell(p, "§7Team verlassen.");
        } else if (args[0].equalsIgnoreCase("random")) {
            if (plugin.teams().joinRandom(p)) tell(p, "§7Du bist in " + plugin.teams().getTeam(p).getDisplayName() + "§7.");
            else tell(p, "§cAlle Teams sind voll.");
        } else {
            MTeam t = plugin.teams().byNumber(parse(args[0], -1));
            if (t == null) tell(p, "§e/team <1-" + plugin.teams().getTeams().size() + "|random|leave>");
            else if (plugin.teams().join(p, t)) tell(p, "§7Du bist in " + t.getDisplayName() + "§7.");
            else tell(p, "§cDieses Team ist voll.");
        }
        TeamGui.refreshAll(plugin);
    }

    public void showStats(CommandSender to, UUID id) {
        int k = plugin.stats().get(id, "kills"), d = plugin.stats().get(id, "deaths");
        String kd = d == 0 ? String.valueOf(k) : String.format(Locale.ROOT, "%.2f", k / (double) d);
        to.sendMessage("§8§m        §r §b§l" + plugin.stats().name(id) + " §8§m        ");
        to.sendMessage("§7Siege: §6" + plugin.stats().get(id, "wins") + " §8| §7Runden: §f" + plugin.stats().get(id, "games"));
        to.sendMessage("§7Kills: §c" + k + " §8| §7Tode: §7" + d + " §8| §7K/D: §e" + kd);
    }

    private void kit(CommandSender sender, Player p, String[] args) {
        if (!needPlayer(sender) || !lobby(sender)) return;
        if (args.length < 2) {
            tell(sender, "§e/odv kit save|give|reset [odv|meetup]");
            return;
        }
        Settings.Mode m = args.length >= 3 && args[2].equalsIgnoreCase("odv") ? Settings.Mode.ODV
                : args.length >= 3 ? Settings.Mode.MEETUP : s().mode;
        try {
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "save":
                    plugin.kits().save(p, m, GameManager.MENU_ITEMS);
                    tell(sender, "§aKit " + m.display + " gespeichert.");
                    break;
                case "give":
                    plugin.kits().give(p, m);
                    break;
                case "reset":
                    plugin.kits().reset(m);
                    tell(sender, "§7Standard-Kit für " + m.display + " aktiv.");
                    break;
                default:
                    tell(sender, "§e/odv kit save|give|reset [odv|meetup]");
            }
        } catch (IOException e) {
            tell(sender, "§c" + e.getMessage());
        }
    }

    private void schem(CommandSender sender, Player p, String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "list";
        String name = args.length >= 3 ? args[2].replace(".schematic", "").replaceAll("[^a-zA-Z0-9_\\-]", "") : null;
        switch (action) {
            case "list":
                tell(sender, "§7Schematics: §f" + (plugin.spawns().list().isEmpty() ? "-" : join(plugin.spawns().list())));
                tell(sender, "§7Team: §e" + s().teamSchematic + " §8| §7Lobby: §e" + s().lobbySchematic);
                break;
            case "save": {
                if (!needPlayer(sender) || name == null || name.isEmpty()) {
                    tell(sender, "§e/odv schem save <Name>");
                    return;
                }
                Location a = plugin.wand().getPos1(p), b = plugin.wand().getPos2(p);
                if (a == null || b == null || a.getWorld() != b.getWorld()) {
                    tell(sender, "§cZuerst Pos1 & Pos2 mit §e/odv wand §csetzen.");
                    return;
                }
                long vol = (long) (Math.abs(a.getBlockX() - b.getBlockX()) + 1) * (Math.abs(a.getBlockY() - b.getBlockY()) + 1)
                        * (Math.abs(a.getBlockZ() - b.getBlockZ()) + 1);
                if (vol > Schematic.MAX_VOLUME) {
                    tell(sender, "§cAuswahl zu groß (" + vol + " Blöcke).");
                    return;
                }
                try {
                    Schematic sc = Schematic.copy(a, b, p.getLocation());
                    sc.save(plugin.spawns().file(name));
                    boolean asLobby = args.length >= 4 && args[3].equalsIgnoreCase("lobby");
                    if (asLobby) s().lobbySchematic = name;
                    else s().teamSchematic = name;
                    s().save();
                    tell(sender, "§aGespeichert: §e" + name + " §7(" + sc.getWidth() + "×" + sc.getHeight() + "×" + sc.getLength()
                            + ") §a→ als " + (asLobby ? "Lobby" : "Team-Plattform") + " gesetzt.");
                    tell(sender, "§7Spawnpunkt = deine aktuelle Position relativ zum Bau.");
                } catch (IOException e) {
                    tell(sender, "§c" + e.getMessage());
                }
                break;
            }
            case "use":
            case "load": {
                if (name == null || !plugin.spawns().file(name).exists()) {
                    tell(sender, "§cNicht gefunden. §7/odv schem list");
                    return;
                }
                if (args.length >= 4 && args[3].equalsIgnoreCase("lobby")) s().lobbySchematic = name;
                else s().teamSchematic = name;
                s().save();
                tell(sender, "§aSchematic gesetzt.");
                break;
            }
            case "paste": {
                if (!needPlayer(sender)) return;
                try {
                    plugin.spawns().pasteAt(plugin.spawns().load(name != null ? name : s().teamSchematic), p.getLocation(), p);
                } catch (IOException e) {
                    tell(sender, "§c" + e.getMessage());
                }
                break;
            }
            case "delete": {
                File f = name == null ? null : plugin.spawns().file(name);
                tell(sender, f != null && f.delete() ? "§aGelöscht." : "§cNicht gefunden.");
                break;
            }
            default:
                tell(sender, "§e/odv schem <list|save|use|paste|delete> [Name] [lobby]");
        }
    }

    private void spawns(CommandSender sender, Player p, String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "list";
        switch (action) {
            case "build":
            case "generate":
                if (!lobby(sender)) return;
                int count = args.length >= 3 ? parse(args[2], 0) : 0;
                if (count <= 0) count = Math.max(2, s().isSolo() ? plugin.game().candidates().size() : plugin.teams().countTeamsAfterFill(plugin.game().candidates().size()));
                plugin.spawns().generate(Math.min(500, count), sender, null);
                break;
            case "undo":
            case "remove":
                plugin.spawns().removePasted(sender);
                break;
            case "add":
                if (needPlayer(sender)) {
                    plugin.arena().addSpawn(p.getLocation());
                    tell(sender, "§aSpawn #" + plugin.arena().getSpawns().size() + " hinzugefügt. §7(Tipp: 'Bei Start neu bauen' ausschalten)");
                }
                break;
            case "clear":
                plugin.arena().setSpawns(new ArrayList<Location>());
                tell(sender, "§aGespeicherte Spawnpunkte gelöscht.");
                break;
            case "tp":
                if (!needPlayer(sender)) return;
                int nr = args.length >= 3 ? parse(args[2], 1) : 1;
                List<Location> l = plugin.arena().getSpawns();
                if (nr < 1 || nr > l.size()) tell(sender, "§cSpawn #" + nr + " gibt es nicht (" + l.size() + ").");
                else p.teleport(l.get(nr - 1));
                break;
            default:
                tell(sender, "§7Spawns: §e" + plugin.arena().getSpawns().size() + " §8| §7Art: §f" + s().spawnMode.display);
                tell(sender, "§e/odv spawns build [Anzahl] | tp <Nr> | add | clear | undo");
        }
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

    private static String join(List<String> l) {
        StringBuilder b = new StringBuilder();
        for (String s : l) b.append(b.length() == 0 ? "" : "§7, §f").append(s);
        return b.toString();
    }

    private void help(CommandSender s) {
        s.sendMessage("§8§m          §r " + s().mode.title + " §8§m          ");
        s.sendMessage("§b/team §7[Nr|random|leave] §8- §7Team wählen");
        s.sendMessage("§b/odv join §8| §b/odv leave §8- §7mitspielen / zuschauen");
        s.sendMessage("§b/odv spec §8- §7Spieler beobachten");
        s.sendMessage("§b/odv stats §7[Name] §8| §b/odv top §7[kills]");
        if (s.hasPermission("odv.admin")) {
            s.sendMessage("§c/odv §8- §7Setup-Menü §8(alles per Klick)");
            s.sendMessage("§c/odv quick §8- §7Schnellsetup: neue Arena + Lobby");
            s.sendMessage("§c/odv start §7[Sek] §8| §c/odv stop §8| §c/odv preset odv|meetup");
            s.sendMessage("§c/odv setlobby §8| §c/odv setcenter §8| §c/odv build");
            s.sendMessage("§c/odv wand §8→ §c/odv schem save <Name> §7[lobby]");
            s.sendMessage("§c/odv schem list|use|paste|delete §8| §c/odv spawns build|tp|undo");
            s.sendMessage("§c/odv kit save|give|reset §7[odv|meetup] §8| §c/odv pregen|backup|reload");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> o = new ArrayList<String>();
        if (cmd.getName().equalsIgnoreCase("team")) {
            if (args.length == 1) {
                o.add("random");
                o.add("leave");
                for (MTeam t : plugin.teams().getTeams()) o.add(String.valueOf(t.getNumber()));
            }
        } else if (args.length == 1) {
            o.addAll(Arrays.asList("help", "teams", "join", "leave", "spec", "stats", "top"));
            if (sender.hasPermission("odv.admin")) {
                o.addAll(Arrays.asList("setup", "quick", "start", "stop", "preset", "setlobby", "setcenter", "build",
                        "kit", "wand", "schem", "spawns", "pregen", "backup", "reload"));
            }
        } else if (sender.hasPermission("odv.admin")) {
            String a = args[0].toLowerCase(Locale.ROOT);
            if (args.length == 2) {
                if (a.equals("preset")) o.addAll(Arrays.asList("odv", "meetup"));
                if (a.equals("kit")) o.addAll(Arrays.asList("save", "give", "reset"));
                if (a.startsWith("schem")) o.addAll(Arrays.asList("list", "save", "use", "paste", "delete"));
                if (a.equals("spawns")) o.addAll(Arrays.asList("build", "tp", "add", "clear", "undo"));
                if (a.equals("top")) o.add("kills");
            } else if (args.length == 3) {
                if (a.equals("kit")) o.addAll(Arrays.asList("odv", "meetup"));
                if (a.startsWith("schem") && !args[1].equalsIgnoreCase("save")) o.addAll(plugin.spawns().list());
            } else if (args.length == 4 && a.startsWith("schem")) {
                o.add("lobby");
            }
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<String>();
        for (String s : o) if (s.toLowerCase(Locale.ROOT).startsWith(last)) out.add(s);
        return out;
    }
}
