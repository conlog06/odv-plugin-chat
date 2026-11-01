package de.crafterteam.meetup.command;

import de.crafterteam.meetup.MeetupPlugin;
import de.crafterteam.meetup.Settings;
import de.crafterteam.meetup.gui.SetupGui;
import de.crafterteam.meetup.gui.TeamGui;
import de.crafterteam.meetup.schematic.Schematic;
import de.crafterteam.meetup.schematic.Wand;
import de.crafterteam.meetup.util.ChunkPregen;
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
import java.util.Collections;
import java.util.List;

public class MeetupCommand implements TabExecutor {

    private final MeetupPlugin plugin;

    public MeetupCommand(MeetupPlugin plugin) {
        this.plugin = plugin;
    }

    private Settings s() {
        return plugin.settings();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("teams")) {
            if (!(sender instanceof Player)) return true;
            if (s().isSolo()) {
                sender.sendMessage(s().prefix + "§cAktuell ist Solo-Modus – keine Teams.");
            } else {
                new TeamGui(plugin).open((Player) sender);
            }
            return true;
        }
        if (!sender.hasPermission("meetup.admin")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("start") && sender.hasPermission("meetup.forcestart")) {
                start(sender, args);
            } else {
                sender.sendMessage(s().prefix + "§7Meetup by CrafterTeam. Teams: §e/teams");
            }
            return true;
        }
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        Player p = sender instanceof Player ? (Player) sender : null;
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "setup":
                if (requirePlayer(sender)) new SetupGui(plugin).open(p);
                break;
            case "start":
                start(sender, args);
                break;
            case "stop":
                plugin.game().stop();
                break;
            case "setlobby":
                if (!requirePlayer(sender)) break;
                s().lobby = p.getLocation().clone();
                s().save();
                sender.sendMessage(s().prefix + "§aLobby gesetzt.");
                break;
            case "setworld":
                if (args.length >= 2) {
                    s().worldName = args[1];
                } else if (p != null) {
                    s().worldName = p.getWorld().getName();
                }
                s().save();
                sender.sendMessage(s().prefix + "§aSpielwelt: §e" + s().worldName + (s().getGameWorld() == null ? " §c(nicht gefunden)" : ""));
                break;
            case "setcenter":
                if (!requirePlayer(sender)) break;
                s().centerX = p.getLocation().getBlockX();
                s().centerZ = p.getLocation().getBlockZ();
                s().save();
                sender.sendMessage(s().prefix + "§aMittelpunkt: §e" + s().centerX + ", " + s().centerZ);
                break;
            case "wand":
                if (requirePlayer(sender)) {
                    p.getInventory().addItem(Wand.item());
                    sender.sendMessage(s().prefix + "§7Wand erhalten. §eLinks §7= Pos1, §eRechts §7= Pos2.");
                }
                break;
            case "schem":
            case "schematic":
                schem(sender, p, args);
                break;
            case "spawns":
            case "spawn":
                spawns(sender, p, args);
                break;
            case "pregen":
                World w = s().getGameWorld();
                if (ChunkPregen.isRunning()) {
                    ChunkPregen.stop();
                    sender.sendMessage(s().prefix + "§cVorgenerierung gestoppt.");
                } else if (w != null) {
                    ChunkPregen.start(plugin, w, s().centerX, s().centerZ, s().borderStart / 2 + 32, p);
                }
                break;
            case "reload":
                plugin.reloadAll();
                sender.sendMessage(s().prefix + "§aConfig neu geladen.");
                break;
            default:
                help(sender, label);
        }
        return true;
    }

    private void start(CommandSender sender, String[] args) {
        int sec = 10;
        if (args.length >= 2) {
            try {
                sec = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException ignored) {
            }
        }
        if (plugin.game().forceStart(sec)) {
            plugin.game().broadcast("§aDie Runde startet in §e" + sec + " §aSekunden. §8(" + sender.getName() + ")");
        } else {
            sender.sendMessage(s().prefix + "§cEs läuft bereits eine Runde.");
        }
    }

    private void schem(CommandSender sender, Player p, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(s().prefix + "§e/meetup schem <save|load|paste|list|delete> [Name]");
            return;
        }
        String action = args[1].toLowerCase();
        String name = args.length >= 3 ? args[2].replaceAll("[^a-zA-Z0-9_\\-]", "") : null;
        switch (action) {
            case "list": {
                List<String> list = plugin.spawns().listSchematics();
                sender.sendMessage(s().prefix + "§7Schematics (" + list.size() + "): §f" + (list.isEmpty() ? "-" : String.join("§7, §f", list)));
                sender.sendMessage("§8Ordner: plugins/CrafterMeetup/schematics (WorldEdit-.schematic geht auch)");
                break;
            }
            case "save": {
                if (!requirePlayer(sender) || name == null || name.isEmpty()) {
                    sender.sendMessage(s().prefix + "§e/meetup schem save <Name>");
                    return;
                }
                Location a = plugin.wand().getPos1(p), b = plugin.wand().getPos2(p);
                if (a == null || b == null || a.getWorld() != b.getWorld()) {
                    sender.sendMessage(s().prefix + "§cSetze zuerst Pos1 und Pos2 mit §e/meetup wand§c.");
                    return;
                }
                long vol = (long) (Math.abs(a.getBlockX() - b.getBlockX()) + 1) * (Math.abs(a.getBlockY() - b.getBlockY()) + 1)
                        * (Math.abs(a.getBlockZ() - b.getBlockZ()) + 1);
                if (vol > 250000) {
                    sender.sendMessage(s().prefix + "§cAuswahl zu groß (" + vol + " Blöcke, max. 250.000).");
                    return;
                }
                try {
                    Schematic sc = Schematic.copy(a, b, p.getLocation());
                    sc.save(plugin.spawns().schematicFile(name));
                    s().schematic = name;
                    s().save();
                    sender.sendMessage(s().prefix + "§aSchematic §e" + name + " §agespeichert §7(" + sc.getWidth() + "x" + sc.getHeight() + "x" + sc.getLength() + ") §aund als Spawn-Schematic gesetzt.");
                    sender.sendMessage("§7Spieler spawnen genau dort, wo du gerade stehst (relativ zur Plattform).");
                } catch (IOException e) {
                    sender.sendMessage(s().prefix + "§cFehler beim Speichern: " + e.getMessage());
                }
                break;
            }
            case "load":
            case "use": {
                if (name == null || !plugin.spawns().schematicFile(name).exists()) {
                    sender.sendMessage(s().prefix + "§cSchematic nicht gefunden. §7/meetup schem list");
                    return;
                }
                s().schematic = name;
                s().save();
                sender.sendMessage(s().prefix + "§aSpawn-Schematic: §e" + name);
                break;
            }
            case "paste": {
                if (!requirePlayer(sender)) return;
                String n = name != null ? name : s().schematic;
                try {
                    Schematic sc = plugin.spawns().loadSchematic(n);
                    plugin.spawns().pasteSingle(sc, p.getLocation(), sender);
                } catch (IOException e) {
                    sender.sendMessage(s().prefix + "§c" + e.getMessage());
                }
                break;
            }
            case "delete": {
                File f = name == null ? null : plugin.spawns().schematicFile(name);
                if (f != null && f.exists() && f.delete()) {
                    if (name.equals(s().schematic)) {
                        s().schematic = "";
                        s().save();
                    }
                    sender.sendMessage(s().prefix + "§aSchematic gelöscht.");
                } else {
                    sender.sendMessage(s().prefix + "§cSchematic nicht gefunden.");
                }
                break;
            }
            default:
                sender.sendMessage(s().prefix + "§e/meetup schem <save|load|paste|list|delete> [Name]");
        }
    }

    private void spawns(CommandSender sender, Player p, String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase() : "list";
        switch (action) {
            case "generate":
            case "gen": {
                int count = plugin.spawns().autoCount();
                int radius = s().spawnRadius;
                try {
                    if (args.length >= 3 && !args[2].equalsIgnoreCase("auto")) count = Integer.parseInt(args[2]);
                    if (args.length >= 4 && !args[3].equalsIgnoreCase("auto")) radius = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(s().prefix + "§e/meetup spawns generate [Anzahl|auto] [Radius|auto]");
                    return;
                }
                if (count < 1 || count > 500) {
                    sender.sendMessage(s().prefix + "§cAnzahl muss zwischen 1 und 500 liegen.");
                    return;
                }
                plugin.spawns().generate(count, radius, sender, null);
                break;
            }
            case "add":
                if (!requirePlayer(sender)) return;
                plugin.spawns().addSpawn(p.getLocation());
                sender.sendMessage(s().prefix + "§aSpawn #" + plugin.spawns().getSpawns().size() + " hinzugefügt.");
                break;
            case "clear":
                plugin.spawns().clearSpawns();
                sender.sendMessage(s().prefix + "§aAlle gespeicherten Spawnpunkte gelöscht. §7(Blöcke bleiben – §e/meetup spawns undo§7)");
                break;
            case "undo":
            case "remove":
                plugin.spawns().removePasted(sender);
                break;
            case "tp": {
                if (!requirePlayer(sender)) return;
                int nr = 1;
                try {
                    if (args.length >= 3) nr = Integer.parseInt(args[2]);
                } catch (NumberFormatException ignored) {
                }
                List<Location> list = plugin.spawns().getSpawns();
                if (nr < 1 || nr > list.size()) {
                    sender.sendMessage(s().prefix + "§cSpawn #" + nr + " existiert nicht (" + list.size() + " vorhanden).");
                    return;
                }
                p.teleport(list.get(nr - 1));
                break;
            }
            default: {
                List<Location> list = plugin.spawns().getSpawns();
                sender.sendMessage(s().prefix + "§7Spawns: §e" + list.size());
                for (int i = 0; i < list.size() && i < 30; i++) {
                    Location l = list.get(i);
                    sender.sendMessage("§8#" + (i + 1) + " §7" + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ());
                }
            }
        }
    }

    private boolean requirePlayer(CommandSender s) {
        if (s instanceof Player) return true;
        s.sendMessage("Nur ingame.");
        return false;
    }

    private void help(CommandSender s, String label) {
        String l = "§e/" + label + " ";
        s.sendMessage("§8§m------§r §b§lMeetup §8§m------");
        s.sendMessage(l + "setup §8- §7Setup-Menü (alles per Klick)");
        s.sendMessage(l + "start [Sek] §8- §7Countdown erzwingen");
        s.sendMessage(l + "stop §8- §7Runde/Countdown stoppen");
        s.sendMessage(l + "setlobby | setworld [Welt] | setcenter");
        s.sendMessage(l + "wand §8- §7Auswahl-Werkzeug");
        s.sendMessage(l + "schem save <Name> §8- §7Auswahl speichern (deine Pos = Spawnpunkt)");
        s.sendMessage(l + "schem load|paste|list|delete [Name]");
        s.sendMessage(l + "spawns generate [Anzahl|auto] [Radius|auto]");
        s.sendMessage(l + "spawns list|tp <Nr>|add|clear|undo");
        s.sendMessage(l + "pregen §8- §7Map innerhalb der Border vorgenerieren");
        s.sendMessage(l + "reload");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (cmd.getName().equalsIgnoreCase("teams") || !sender.hasPermission("meetup.admin")) return Collections.emptyList();
        List<String> opts;
        if (args.length == 1) {
            opts = Arrays.asList("setup", "start", "stop", "setlobby", "setworld", "setcenter", "wand", "schem", "spawns", "pregen", "reload");
        } else if (args.length == 2 && args[0].toLowerCase().startsWith("schem")) {
            opts = Arrays.asList("save", "load", "paste", "list", "delete");
        } else if (args.length == 2 && args[0].toLowerCase().startsWith("spawn")) {
            opts = Arrays.asList("generate", "list", "tp", "add", "clear", "undo");
        } else if (args.length == 3 && args[0].toLowerCase().startsWith("schem") && !args[1].equalsIgnoreCase("save")) {
            opts = plugin.spawns().listSchematics();
        } else {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<String>();
        for (String o : opts) if (o.toLowerCase().startsWith(args[args.length - 1].toLowerCase())) out.add(o);
        return out;
    }
}
