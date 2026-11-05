package de.crafterteam.odv.game;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.Settings;
import de.crafterteam.odv.gui.HostGui;
import de.crafterteam.odv.gui.ScenarioGui;
import de.crafterteam.odv.team.MTeam;
import de.crafterteam.odv.util.Txt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Host-Befehle wie bei Mega-Events:
 * /host /start /border /finale /freeze /latejoin /revive /teamchange /remove /setkit /createworld /worlds /announce
 * und das Report-System (/report, /reports).
 * Funktionieren auch als /meetup &lt;befehl&gt; (z. B. /meetup add, /meetup setstarterkit).
 */
public class HostCommands implements CommandExecutor, TabCompleter, Listener {

    public static final String[] COMMANDS = {"host", "start", "border", "finale", "freeze", "latejoin", "revive", "teamchange",
            "remove", "setkit", "createworld", "worlds", "announce", "report", "reports"};

    /** /odv- bzw. /meetup-Unterbefehle → Host-Befehl. */
    public static final Map<String, String> SUB_ALIASES = Map.ofEntries(
            Map.entry("border", "border"), Map.entry("finale", "finale"), Map.entry("freeze", "freeze"),
            Map.entry("add", "latejoin"), Map.entry("latejoin", "latejoin"), Map.entry("revive", "revive"),
            Map.entry("team", "teamchange"), Map.entry("teamchange", "teamchange"), Map.entry("remove", "remove"),
            Map.entry("setstarterkit", "setkit"), Map.entry("setkit", "setkit"), Map.entry("createworld", "createworld"),
            Map.entry("worlds", "worlds"));

    private static final Pattern TIME = Pattern.compile("^\\d{1,2}[:.]\\d{2}$");

    private final OdvPlugin plugin;
    private final Set<UUID> frozen = new HashSet<>();

    private record Report(int id, UUID reporter, String reporterName, UUID target, String targetName, String reason, long time) {}

    private final List<Report> reports = new ArrayList<>();
    private final Set<UUID> reportLogout = new HashSet<>();
    private final Map<UUID, Long> reportCooldown = new HashMap<>();
    private int nextReport = 1;

    public HostCommands(OdvPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (UUID id : frozen) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) Txt.title(p, "§c§lEINGEFROREN", "§7Komm in den §bSupport§7 – nicht ausloggen!");
            }
        }, 40L, 60L);
    }

    private Settings s() {
        return plugin.settings();
    }

    private GameManager g() {
        return plugin.game();
    }

    private void tell(CommandSender to, String msg) {
        Txt.send(to, s().prefix + msg);
    }

    /** Host = Admin, Rang mit odv.host oder der aktuelle Host der Runde. Konsole immer. */
    public boolean isHost(CommandSender s) {
        if (!(s instanceof Player p)) return true;
        return ScenarioGui.canEdit(plugin, p);
    }

    private boolean needHost(CommandSender s) {
        if (isHost(s)) return true;
        tell(s, "§cNur für den Host der Runde.");
        return false;
    }

    private Player online(CommandSender s, String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p == null) p = Bukkit.getPlayer(name);
        if (p == null) tell(s, "§c" + name + " ist nicht online.");
        return p;
    }

    private static int num(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    // ================================================================ Befehle

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        run(sender, cmd.getName().toLowerCase(Locale.ROOT), a);
        return true;
    }

    public void run(CommandSender sender, String c, String[] a) {
        Player me = sender instanceof Player p ? p : null;
        switch (c) {
            case "host" -> {
                if (me == null) return;
                if (!me.hasPermission("odv.host") && !me.hasPermission("odv.admin") && !me.getUniqueId().equals(g().getHost())) {
                    tell(me, "§cDu darfst nicht hosten.");
                    return;
                }
                UUID cur = g().getHost();
                if (cur != null && !cur.equals(me.getUniqueId()) && Bukkit.getPlayer(cur) != null && !me.hasPermission("odv.admin")) {
                    tell(me, "§c" + Bukkit.getPlayer(cur).getName() + " hostet bereits.");
                    return;
                }
                if (!me.getUniqueId().equals(cur)) {
                    g().setHost(me.getUniqueId());
                    g().broadcast("§e" + me.getName() + " §7ist jetzt §aHost§7.");
                    if (!g().isMod(me) && s().hostAutoMod) g().setMod(me, true);
                }
                HostGui.open(plugin, me);
            }
            case "start" -> {
                if (!needHost(sender)) return;
                int sec = a.length >= 1 ? Math.max(1, num(a[0], 10)) : 10;
                String err = g().forceStart(sec);
                if (err != null) tell(sender, err);
                else {
                    if (g().getHost() == null && me != null) g().setHost(me.getUniqueId());
                    g().broadcast("§aRunde startet in §e" + sec + " §aSekunden. §8(" + sender.getName() + ")");
                }
            }
            case "border" -> {
                if (!needHost(sender)) return;
                if (a.length < 1) {
                    tell(sender, "§e/border <Größe> [Sekunden] §7– ohne Sekunden sofort, in der Lobby = Startgröße");
                    return;
                }
                int size = num(a[0], -1);
                if (size < 10 || size > 30000) {
                    tell(sender, "§cGröße zwischen 10 und 30000.");
                    return;
                }
                int sec = a.length >= 2 ? Math.max(0, num(a[1], 0)) : 0;
                String err = g().hostBorder(size, sec);
                if (err != null) tell(sender, err);
                else if (!g().getState().isIngame()) tell(sender, "§aStart-Border: §f" + size);
            }
            case "finale" -> {
                if (!needHost(sender)) return;
                String err = g().startFinale(a.length >= 1 ? Math.max(0, num(a[0], 30)) : 30);
                if (err != null) tell(sender, err);
            }
            case "freeze" -> {
                if (!sender.hasPermission("odv.staff") && !needHost(sender)) return;
                if (a.length < 1) {
                    tell(sender, "§e/freeze <Spieler>");
                    return;
                }
                Player t = online(sender, a[0]);
                if (t == null) return;
                if (frozen.remove(t.getUniqueId())) {
                    t.removePotionEffect(PotionEffectType.BLINDNESS);
                    t.resetTitle();
                    Txt.title(t, "§a§lAufgetaut", "§7Du kannst weiterspielen");
                    staff("§a" + t.getName() + " §7wurde von §f" + sender.getName() + " §7aufgetaut.");
                } else {
                    frozen.add(t.getUniqueId());
                    t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, PotionEffect.INFINITE_DURATION, 0, false, false));
                    Txt.title(t, "§c§lEINGEFROREN", "§7Komm in den §bSupport§7 – nicht ausloggen!");
                    Txt.send(t, s().prefix + "§cDu wurdest eingefroren. §7Komm sofort in den Support-Talk im Discord. §cAusloggen = Ban!");
                    staff("§c" + t.getName() + " §7wurde von §f" + sender.getName() + " §7eingefroren.");
                }
            }
            case "latejoin" -> {
                if (!needHost(sender)) return;
                if (a.length < 1) {
                    tell(sender, "§e/latejoin <Spieler> [Team]");
                    return;
                }
                Player t = online(sender, a[0]);
                if (t == null) return;
                MTeam team = null;
                if (a.length >= 2) {
                    team = plugin.teams().find(a[1]);
                    if (team == null) {
                        tell(sender, "§cTeam §f" + a[1] + " §cgibt es nicht.");
                        return;
                    }
                }
                String err = g().lateJoin(t, team);
                tell(sender, err != null ? err : "§a" + t.getName() + " spielt jetzt mit.");
            }
            case "revive" -> {
                if (!needHost(sender)) return;
                if (a.length < 1) {
                    tell(sender, "§e/revive <Spieler> §8– §7nur mit Clip-Beweis!");
                    return;
                }
                Player t = online(sender, a[0]);
                if (t == null) return;
                String err = g().hostRevive(t);
                if (err != null) tell(sender, err);
                else staff("§a" + t.getName() + " §7wurde von §f" + sender.getName() + " §7wiederbelebt.");
            }
            case "teamchange" -> {
                if (!needHost(sender)) return;
                if (a.length < 2) {
                    tell(sender, "§e/teamchange <Spieler> <Team-Nr|Name>");
                    return;
                }
                Player t = online(sender, a[0]);
                if (t == null) return;
                MTeam team = plugin.teams().find(a[1]);
                if (team == null) {
                    tell(sender, "§cTeam §f" + a[1] + " §cgibt es nicht.");
                    return;
                }
                String err = g().teamChange(t, team);
                tell(sender, err != null ? err : "§a" + t.getName() + " §7ist jetzt in Team " + team.getDisplayName() + "§7.");
            }
            case "remove" -> {
                if (!needHost(sender)) return;
                if (a.length < 1) {
                    tell(sender, "§e/remove <Spieler>");
                    return;
                }
                Player t = online(sender, a[0]);
                if (t == null) return;
                String err = g().removeFromRound(t);
                tell(sender, err != null ? err : "§a" + t.getName() + " wurde aus der Runde genommen.");
            }
            case "setkit" -> {
                if (!needHost(sender)) return;
                if (a.length < 1) {
                    tell(sender, "§e/setkit <Spieler>");
                    return;
                }
                Player t = online(sender, a[0]);
                if (t == null) return;
                plugin.kits().give(t, s().mode == Settings.Mode.ODV ? de.crafterteam.odv.data.Kits.Type.ODV : de.crafterteam.odv.data.Kits.Type.MEETUP);
                tell(sender, "§a" + t.getName() + " hat das Kit bekommen.");
            }
            case "createworld" -> {
                if (!needHost(sender)) return;
                if (!g().getState().isLobby()) {
                    tell(sender, "§cGeht nur in der Lobby.");
                    return;
                }
                if (plugin.spawns().isBusy()) {
                    tell(sender, "§cEs wird bereits eine Welt erstellt.");
                    return;
                }
                g().broadcast("§e" + sender.getName() + " §7erstellt eine neue Spielwelt …");
                if (plugin.arena().getWorld() != null) plugin.spawns().resetArena(sender);
                else plugin.spawns().quickSetup(sender);
            }
            case "worlds" -> worlds(sender, a);
            case "announce" -> announce(sender, a);
            case "report" -> report(sender, a);
            case "reports" -> reports(sender, a);
            default -> {
            }
        }
    }

    // ================================================================ /worlds

    private void worlds(CommandSender sender, String[] a) {
        if (!(sender instanceof Player p)) return;
        if (!p.hasPermission("odv.staff") && !needHost(p)) return;
        World arena = plugin.arena().getWorld();
        if (a.length >= 1) {
            Location to = switch (a[0].toLowerCase(Locale.ROOT)) {
                case "lobby" -> plugin.arena().getLobbyOrDefault();
                case "spawn", "arena" -> plugin.arena().getCenter();
                case "practice" -> plugin.arena().getPractice();
                default -> {
                    World w = Bukkit.getWorld(a[0]);
                    if (w == null) yield null;
                    if (w.equals(arena) && plugin.arena().getCenter() != null) yield plugin.arena().getCenter();
                    yield w.getSpawnLocation();
                }
            };
            if (to == null) {
                tell(p, "§cUnbekannte Welt.");
                return;
            }
            if (!g().isAlive(p) && p.getGameMode() != org.bukkit.GameMode.CREATIVE && g().getState().isIngame()) p.setGameMode(org.bukkit.GameMode.SPECTATOR);
            p.teleport(to);
            tell(p, "§7Teleportiert: §f" + a[0]);
            return;
        }
        tell(p, "§7Welten §8(klicken zum Teleportieren)§7:");
        List<String[]> entries = new ArrayList<>();
        entries.add(new String[]{"lobby", "§b✦ Lobby"});
        if (plugin.arena().getCenter() != null) entries.add(new String[]{"spawn", "§a✦ Spawn / Portal"});
        if (plugin.arena().getPractice() != null) entries.add(new String[]{"practice", "§e✦ Practice"});
        for (World w : Bukkit.getWorlds()) {
            String type = switch (w.getEnvironment()) {
                case NETHER -> "§c";
                case THE_END -> "§d";
                default -> "§f";
            };
            entries.add(new String[]{w.getName(), type + w.getName() + " §8(" + w.getPlayers().size() + ")"});
        }
        for (String[] e : entries) {
            p.sendMessage(Txt.c("§8» " + e[1])
                    .clickEvent(ClickEvent.runCommand("/worlds " + e[0]))
                    .hoverEvent(HoverEvent.showText(Txt.c("§7Klick: §fhinteleportieren"))));
        }
    }

    // ================================================================ /announce

    private void announce(CommandSender sender, String[] a) {
        if (!needHost(sender)) return;
        if (a.length == 0) {
            tell(sender, "§e/announce <Uhrzeit> [Anzahl] [Biom/Text] §8| §e/announce <Text>");
            return;
        }
        String text;
        if (TIME.matcher(a[0]).matches()) {
            String time = a[0].replace('.', ':');
            String extra = a.length > 1 ? String.join(" ", Arrays.copyOfRange(a, 1, a.length)) : "";
            String sc = s().scenarios.isEmpty() ? "" : " · " + plugin.scenarios().activeList();
            text = s().mode.display + " um " + time + " Uhr" + (s().isSolo() ? " · Solo" : " · To" + s().teamSize) + sc
                    + (extra.isEmpty() ? "" : " · " + extra) + (s().serverIp.isBlank() ? "" : " · " + s().serverIp);
        } else {
            text = String.join(" ", a);
        }
        for (Player o : Bukkit.getOnlinePlayers()) Txt.title(o, "§6§lAnkündigung", "§f" + text);
        g().broadcast("§6§lAnkündigung: §f" + text);
        if (plugin.discord().enabled()) plugin.discord().announce(text, sender);
    }

    // ================================================================ Reports

    private void report(CommandSender sender, String[] a) {
        if (!(sender instanceof Player p)) return;
        if (a.length == 1 && (a[0].equalsIgnoreCase("login") || a[0].equalsIgnoreCase("logout"))) {
            if (!p.hasPermission("odv.staff")) {
                tell(p, "§cNur für das Team.");
                return;
            }
            boolean in = a[0].equalsIgnoreCase("login");
            if (in) reportLogout.remove(p.getUniqueId());
            else reportLogout.add(p.getUniqueId());
            tell(p, in ? "§aReports eingeloggt – du bekommst neue Reports." : "§7Reports ausgeloggt. §8(Immer ausloggen, wenn du selbst mitspielst!)");
            return;
        }
        if (a.length < 2) {
            tell(p, "§e/report <Spieler> <Grund>" + (p.hasPermission("odv.staff") ? " §8| §e/report login|logout §8| §e/reports" : ""));
            return;
        }
        Player t = online(p, a[0]);
        if (t == null) return;
        if (t.equals(p)) {
            tell(p, "§cDu kannst dich nicht selbst melden.");
            return;
        }
        long now = System.currentTimeMillis();
        Long last = reportCooldown.get(p.getUniqueId());
        if (last != null && now - last < 60_000 && !p.hasPermission("odv.staff")) {
            tell(p, "§cBitte warte kurz vor dem nächsten Report.");
            return;
        }
        reportCooldown.put(p.getUniqueId(), now);
        String reason = String.join(" ", Arrays.copyOfRange(a, 1, a.length));
        Report r = new Report(nextReport++, p.getUniqueId(), p.getName(), t.getUniqueId(), t.getName(), reason, now);
        reports.add(r);
        if (reports.size() > 100) reports.remove(0);
        tell(p, "§aDanke! Dein Report gegen §f" + t.getName() + " §awurde ans Team geschickt.");
        int online = 0;
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!o.hasPermission("odv.staff") || reportLogout.contains(o.getUniqueId())) continue;
            online++;
            o.sendMessage(Txt.c(s().prefix + "§c§lREPORT #" + r.id() + " §f" + t.getName() + " §7– " + reason + " §8(von " + p.getName() + ") ")
                    .append(button("§b[TP]", "/tp " + t.getName(), "Zum Spieler"))
                    .append(Txt.c(" "))
                    .append(button("§a[Erledigt]", "/reports done " + r.id(), "Report schließen")));
            o.playSound(o.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 2f);
        }
        if (online == 0) tell(p, "§7Gerade ist kein Teammitglied eingeloggt – der Report bleibt gespeichert.");
    }

    private static Component button(String text, String command, String hover) {
        return Txt.c(text).clickEvent(ClickEvent.runCommand(command)).hoverEvent(HoverEvent.showText(Txt.c("§7" + hover)));
    }

    private void reports(CommandSender sender, String[] a) {
        if (!sender.hasPermission("odv.staff")) {
            tell(sender, "§cNur für das Team.");
            return;
        }
        if (a.length >= 2 && a[0].equalsIgnoreCase("done")) {
            int id = num(a[1], -1);
            boolean removed = reports.removeIf(r -> r.id() == id);
            if (removed) staff("§aReport #" + id + " §7wurde von §f" + sender.getName() + " §7geschlossen.");
            else tell(sender, "§cReport #" + id + " gibt es nicht.");
            return;
        }
        if (a.length >= 1 && a[0].equalsIgnoreCase("clear")) {
            reports.clear();
            tell(sender, "§aAlle Reports geschlossen.");
            return;
        }
        tell(sender, "§7Offene Reports: §c" + reports.size());
        SimpleDateFormat f = new SimpleDateFormat("HH:mm");
        for (int i = reports.size() - 1; i >= Math.max(0, reports.size() - 15); i--) {
            Report r = reports.get(i);
            boolean on = Bukkit.getPlayer(r.target()) != null;
            sender.sendMessage(Txt.c("§8» §c#" + r.id() + " §8" + f.format(new Date(r.time())) + " " + (on ? "§f" : "§7") + r.targetName()
                            + " §7– " + r.reason() + " §8(" + r.reporterName() + ") ")
                    .append(on ? button("§b[TP]", "/tp " + r.targetName(), "Zum Spieler") : Txt.c("§8[offline]"))
                    .append(Txt.c(" "))
                    .append(button("§a[✔]", "/reports done " + r.id(), "Report schließen")));
        }
    }

    private void staff(String msg) {
        for (Player p : Bukkit.getOnlinePlayers()) if (p.hasPermission("odv.staff")) Txt.send(p, s().prefix + msg);
        plugin.getLogger().info(Txt.ascii(msg.replaceAll("§.", "")));
    }

    // ================================================================ Freeze

    public boolean isFrozen(Player p) {
        return frozen.contains(p.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (frozen.isEmpty() || !e.hasChangedPosition() || !frozen.contains(e.getPlayer().getUniqueId())) return;
        Location to = e.getFrom().clone();
        to.setYaw(e.getTo().getYaw());
        to.setPitch(e.getTo().getPitch());
        e.setTo(to);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (frozen.isEmpty()) return;
        if (e.getEntity() instanceof Player p && frozen.contains(p.getUniqueId())) e.setCancelled(true);
        if (e instanceof EntityDamageByEntityEvent be && be.getDamager() instanceof Player d && frozen.contains(d.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (!frozen.isEmpty() && frozen.contains(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (frozen.isEmpty() || !frozen.contains(e.getPlayer().getUniqueId())) return;
        String m = e.getMessage().toLowerCase(Locale.ROOT);
        if (!m.startsWith("/msg") && !m.startsWith("/r ") && !m.startsWith("/report")) {
            e.setCancelled(true);
            tell(e.getPlayer(), "§cDu bist eingefroren.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (!frozen.remove(p.getUniqueId())) return;
        p.removePotionEffect(PotionEffectType.BLINDNESS);
        staff("§4" + p.getName() + " §chat sich im Freeze ausgeloggt!");
        if (s().freezeLogoutBan != null && !s().freezeLogoutBan.isBlank()) {
            long dur = Punishments.parseDuration(s().freezeLogoutBan);
            if (dur != Long.MIN_VALUE) {
                UUID id = p.getUniqueId();
                String name = p.getName();
                Bukkit.getScheduler().runTask(plugin, () -> plugin.punish().ban(null, id, name, dur, "Ausloggen im Freeze"));
            }
        }
    }

    // ================================================================ Tab

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] a) {
        return tab(cmd.getName().toLowerCase(Locale.ROOT), a);
    }

    public List<String> tab(String c, String[] a) {
        List<String> out = new ArrayList<>();
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        switch (c) {
            case "freeze", "revive", "remove", "setkit", "latejoin", "teamchange", "report" -> {
                if (a.length == 1) for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
                else if (a.length == 2 && (c.equals("latejoin") || c.equals("teamchange"))) {
                    for (int i = 1; i <= s().teamCount; i++) out.add(String.valueOf(i));
                }
            }
            case "worlds" -> {
                if (a.length == 1) {
                    out.addAll(List.of("lobby", "spawn", "practice"));
                    for (World w : Bukkit.getWorlds()) out.add(w.getName());
                }
            }
            case "border" -> {
                if (a.length == 1) out.addAll(List.of("100", "300", "500", "1000"));
                else if (a.length == 2) out.addAll(List.of("0", "60", "180", "300"));
            }
            case "start", "finale" -> {
                if (a.length == 1) out.addAll(List.of("10", "30", "60"));
            }
            case "reports" -> {
                if (a.length == 1) out.addAll(List.of("done", "clear"));
            }
            case "announce" -> {
                if (a.length == 1) out.addAll(List.of("20:00", "20:30", "21:00"));
            }
            default -> {
            }
        }
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
