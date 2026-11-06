package de.crafterteam.odv.gui;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.Settings;
import de.crafterteam.odv.data.Kits;
import de.crafterteam.odv.feature.EnchantLimiter;
import de.crafterteam.odv.team.MTeam;
import de.crafterteam.odv.util.Txt;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Kit-Auswahl, erlaubte Verzauberungen und Latejoin-Teamauswahl. */
public final class ExtraMenus {

    private ExtraMenus() {
    }

    private static ItemStack glass(OdvPlugin plugin, Material m) {
        return plugin.items().of(m).name(" ").build();
    }

    private static void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.3f);
    }

    // ================================================================ Kits

    public static void kits(OdvPlugin plugin, Player p) {
        Settings s = plugin.settings();
        Kits kits = plugin.kits();
        Kits.Type type = s.mode == Settings.Mode.ODV ? Kits.Type.ODV : Kits.Type.MEETUP;
        String active = kits.active(type);
        boolean admin = p.hasPermission("odv.admin");
        ActionGui g = new ActionGui(5, "§8» §6§lKits §8(" + s.mode.title + "§8)").fill(glass(plugin, Material.BLACK_STAINED_GLASS_PANE));
        for (int i = 0; i < 9; i++) g.item(i, glass(plugin, Material.ORANGE_STAINED_GLASS_PANE));
        g.item(4, plugin.items().of(Material.CHEST).name("§6§lKit für " + s.mode.title).lore("§7Aktiv: §f" + (active == null ? "Standard" : active),
                "", "§eKlick auf ein Kit: §7aktivieren", admin ? "§eShift+Rechts: §7löschen" : "").build());
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        g.set(slots[0], plugin.items().of(type == Kits.Type.ODV ? Material.DIAMOND_PICKAXE : Material.DIAMOND_SWORD)
                .name((active == null ? "§a§l" : "§f§l") + "Standard-Kit").lore(
                        type == Kits.Type.ODV ? "§7Dia-Axt & -Spitzhacke, 64 Steak, 3 Bücher," : "§7Netherite/Dia-Rüstung, Schwert, Bogen,",
                        type == Kits.Type.ODV ? "§7Zaubertisch, Amboss, 32 Holz, Eimer," : "§7Goldäpfel, Eimer, Blöcke …",
                        type == Kits.Type.ODV ? "§764 Lapis, 16 Äpfel" : "", "",
                        active == null ? "§a✔ Aktiv" : "§eKlick: §7aktivieren").glow(active == null).build(), (pl, c) -> {
            if (!ScenarioGui.canEdit(plugin, pl)) return;
            try {
                kits.use(type, null);
            } catch (IOException ignored) {
            }
            click(pl);
            kits(plugin, pl);
        });
        List<String> names = kits.names();
        for (int i = 0; i < names.size() && i + 1 < slots.length; i++) {
            String n = names.get(i);
            boolean on = n.equals(active);
            g.set(slots[i + 1], plugin.items().of(kits.icon(n)).name((on ? "§a§l" : "§f§l") + n)
                    .lore("§7Erstellt von §f" + kits.creator(n), "", on ? "§a✔ Aktiv" : "§eKlick: §7aktivieren",
                            admin ? "§8Shift+Rechts: löschen" : "").glow(on).build(), (pl, c) -> {
                try {
                    if (c.isShiftClick() && c.isRightClick()) {
                        if (!pl.hasPermission("odv.admin")) return;
                        kits.delete(n);
                        Txt.send(pl, s.prefix + "§7Kit §f" + n + " §7gelöscht.");
                    } else {
                        if (!ScenarioGui.canEdit(plugin, pl)) return;
                        kits.use(type, n);
                        Txt.send(pl, s.prefix + "§aKit §f" + n + " §aist jetzt für " + s.mode.title + " §aaktiv.");
                    }
                } catch (IOException e) {
                    Txt.send(pl, s.prefix + "§c" + e.getMessage());
                }
                click(pl);
                kits(plugin, pl);
            });
        }
        if (admin) {
            g.set(40, plugin.items().of(Material.WRITABLE_BOOK).name("§a§lNeues Kit aus deinem Inventar").lore(
                    "§7Lege die Items in dein Inventar und", "§7speichere sie mit:", "§e/odv kit create <Name>").build(), (pl, c) -> {
                pl.closeInventory();
                Txt.send(pl, s.prefix + "§7Kit-Items ins Inventar legen, dann §e/odv kit create <Name>§7. §8(/gm 1 hilft)");
            });
        }
        g.set(36, plugin.items().of(Material.ARROW).name("§7Zurück").build(), (pl, c) -> HostGui.open(plugin, pl));
        g.open(p);
    }

    // ================================================================ Verzauberungen

    public static List<Enchantment> allEnchantments() {
        List<Enchantment> list = new ArrayList<>();
        RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).forEach(list::add);
        list.sort(Comparator.comparing(EnchantLimiter::key));
        return list;
    }

    public static void enchants(OdvPlugin plugin, Player p, int page) {
        Settings s = plugin.settings();
        List<Enchantment> all = allEnchantments();
        int per = 45, pages = Math.max(1, (all.size() + per - 1) / per);
        int pg = Math.max(0, Math.min(page, pages - 1));
        ActionGui g = new ActionGui(6, "§8» §d§lVerzauberungen §8(" + (pg + 1) + "/" + pages + ")").fill(glass(plugin, Material.BLACK_STAINED_GLASS_PANE));
        for (int i = 0; i < per && pg * per + i < all.size(); i++) {
            Enchantment e = all.get(pg * per + i);
            String key = EnchantLimiter.key(e);
            Integer lim = s.enchantLimits.get(key);
            int max = lim == null ? e.getMaxLevel() : lim;
            boolean off = max <= 0, changed = lim != null;
            ItemStack it = plugin.items().of(off ? Material.BOOK : Material.ENCHANTED_BOOK, Math.max(1, Math.min(64, max))).lore(
                    off ? "§c✘ Verboten" : "§7Max. Level: §a" + max + (changed ? " §8(Vanilla " + e.getMaxLevel() + ")" : " §8(Vanilla)"), "",
                    "§8Links §a+1  §8Rechts §c-1", "§8Shift+Links §7Vanilla  §8Shift+Rechts §cverbieten").glow(!off && changed).build();
            ItemMeta meta = it.getItemMeta();
            meta.customName(e.description().color(off ? NamedTextColor.RED : changed ? NamedTextColor.YELLOW : NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
            it.setItemMeta(meta);
            g.set(i, it, (pl, c) -> {
                if (!ScenarioGui.canEdit(plugin, pl)) return;
                int now = s.enchantLimits.getOrDefault(key, e.getMaxLevel());
                int next;
                if (c.isShiftClick()) next = c.isLeftClick() ? e.getMaxLevel() : 0;
                else next = Math.max(0, Math.min(10, now + (c.isLeftClick() ? 1 : -1)));
                if (next == e.getMaxLevel()) s.enchantLimits.remove(key);
                else s.enchantLimits.put(key, next);
                s.save();
                click(pl);
                enchants(plugin, pl, pg);
            });
        }
        if (pg > 0) g.set(45, plugin.items().of(Material.ARROW).name("§7« Seite " + pg).build(), (pl, c) -> enchants(plugin, pl, pg - 1));
        if (pg < pages - 1) g.set(53, plugin.items().of(Material.ARROW).name("§7Seite " + (pg + 2) + " »").build(), (pl, c) -> enchants(plugin, pl, pg + 1));
        g.set(48, plugin.items().of(Material.MILK_BUCKET).name("§e§lAlles auf Vanilla").lore("§7Setzt alle Limits zurück").build(), (pl, c) -> {
            if (!ScenarioGui.canEdit(plugin, pl)) return;
            s.enchantLimits.clear();
            s.save();
            click(pl);
            enchants(plugin, pl, pg);
        });
        g.set(49, plugin.items().of(Material.IRON_SWORD).name("§6§lODV-Preset").lore("§7Schärfe/Schutz max. III, Power III,",
                "§7kein Feueraspekt, Flame, Dornen, Mending").build(), (pl, c) -> {
            if (!ScenarioGui.canEdit(plugin, pl)) return;
            s.enchantLimits.clear();
            for (String k : new String[]{"sharpness", "protection", "power", "projectile_protection", "blast_protection", "fire_protection"}) s.enchantLimits.put(k, 3);
            for (String k : new String[]{"fire_aspect", "flame", "thorns", "mending"}) s.enchantLimits.put(k, 0);
            s.save();
            click(pl);
            enchants(plugin, pl, pg);
        });
        g.set(50, plugin.items().of(Material.BARRIER).name("§7Zurück").build(), (pl, c) -> HostGui.open(plugin, pl));
        g.open(p);
    }

    // ================================================================ Statistik

    /** v: kills, deaths, wins, games, bet, p_kills, p_deaths */
    public static void stats(OdvPlugin plugin, Player viewer, String name, int[] v, Player online) {
        int k = v[0], d = v[1], w = v[2], games = v[3];
        String kd = d == 0 ? String.valueOf(k) : String.format(java.util.Locale.ROOT, "%.2f", k / (double) d);
        String wr = games == 0 ? "0%" : Math.round(w * 100.0 / games) + "%";
        ActionGui g = new ActionGui(4, "§8» §b§lStatistik: " + name).fill(glass(plugin, Material.BLACK_STAINED_GLASS_PANE));
        for (int i = 0; i < 9; i++) g.item(i, glass(plugin, Material.LIGHT_BLUE_STAINED_GLASS_PANE));
        ItemStack head = plugin.items().of(Material.PLAYER_HEAD).name("§b§l" + name).lore("§7Siegquote: §a" + wr, "§7K/D: §e" + kd).build();
        if (online != null && head.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta sm) {
            sm.setPlayerProfile(online.getPlayerProfile());
            head.setItemMeta(sm);
        }
        g.item(4, head);
        g.item(19, plugin.items().of(Material.GOLD_BLOCK, Math.max(1, Math.min(64, w))).name("§6§lSiege: §f" + w).lore("§7Siegquote: §a" + wr).build());
        g.item(20, plugin.items().of(Material.CLOCK).name("§f§lRunden: §f" + games).build());
        g.item(21, plugin.items().of(Material.DIAMOND_SWORD, Math.max(1, Math.min(64, k))).name("§c§lKills: §f" + k).build());
        g.item(22, plugin.items().of(Material.SKELETON_SKULL).name("§7§lTode: §f" + d).build());
        g.item(23, plugin.items().of(Material.EXPERIENCE_BOTTLE).name("§e§lK/D: §f" + kd).build());
        g.item(24, plugin.items().of(Material.IRON_SWORD).name("§a§lPractice").lore("§7Kills: §a" + v[5], "§7Tode: §c" + v[6]).build());
        g.item(25, plugin.items().of(Material.EMERALD).name("§2§lWettpunkte: §f" + v[4]).build());
        g.set(31, plugin.items().of(Material.BOOK).name("§e§lBestenliste").lore("§eKlick: §7/lb").build(), (pl, c) -> pl.performCommand("lb"));
        g.open(viewer);
    }

    // ================================================================ Spielerübersicht

    private static int group(OdvPlugin plugin, Player t) {
        var g = plugin.game();
        if (g.isMod(t)) return 3;
        if (g.getState().isIngame() || g.getState() == de.crafterteam.odv.game.GameState.ENDING) {
            if (g.isAlive(t)) return 0;
            return plugin.teams().getTeam(t) != null ? 1 : 2;
        }
        return g.isOptedOut(t) ? 2 : 0;
    }

    /** Geordnete Übersicht aller Spieler: Lebende/Teilnehmer nach Team, dann Tote, Zuschauer, Mods. */
    public static void players(OdvPlugin plugin, Player viewer, int page) {
        List<Player> list = new ArrayList<>(Bukkit.getOnlinePlayers());
        list.sort(Comparator.<Player>comparingInt(t -> group(plugin, t)).thenComparingInt(t -> {
            MTeam tm = plugin.teams().getTeam(t);
            return tm == null ? 999 : tm.getNumber();
        }).thenComparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        int per = 45, pages = Math.max(1, (list.size() + per - 1) / per);
        int pg = Math.max(0, Math.min(page, pages - 1));
        boolean ingame = plugin.game().getState().isIngame();
        long alive = list.stream().filter(t -> group(plugin, t) == 0).count();
        ActionGui g = new ActionGui(6, "§8» §a§lSpieler §8(" + alive + (ingame ? " leben" : " spielen mit") + " · " + list.size() + " online)")
                .fill(glass(plugin, Material.BLACK_STAINED_GLASS_PANE));
        String[] groups = ingame ? new String[]{"§a● Lebt", "§c✘ Ausgeschieden", "§7◌ Zuschauer", "§6★ Mod"}
                : new String[]{"§a● Spielt mit", "§c✘", "§7◌ Schaut zu", "§6★ Mod"};
        for (int i = 0; i < per && pg * per + i < list.size(); i++) {
            Player t = list.get(pg * per + i);
            MTeam team = plugin.teams().getTeam(t);
            int gr = group(plugin, t);
            var rank = plugin.ranks().of(t);
            List<String> lore = new ArrayList<>();
            lore.add(groups[gr]);
            lore.add("§7Rang: " + rank.prefix() + "§r" + rank.nameColor() + rank.name());
            if (team != null && !plugin.settings().isSolo()) lore.add("§7Team: " + team.getDisplayName());
            if (ingame && gr == 0) {
                lore.add("§7Leben: §c" + String.format(java.util.Locale.ROOT, "%.1f", t.getHealth() / 2.0) + " ❤ §8┃ §7Kills: §c" + plugin.game().getKills(t.getUniqueId()));
                if (plugin.punish().inCombat(t)) lore.add("§c⚔ Im Kampf");
            }
            lore.add("§7Ping: §f" + t.getPing() + "ms");
            if (plugin.host().isFrozen(t)) lore.add("§b❄ Eingefroren");
            lore.add("");
            lore.add("§eKlick: §7Aktionen");
            ItemStack head = plugin.items().of(gr == 0 ? Material.PLAYER_HEAD : Material.SKELETON_SKULL).name(rank.nameColor() + t.getName()).lore(lore).build();
            if (gr == 0 && head.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta sm) {
                sm.setPlayerProfile(t.getPlayerProfile());
                head.setItemMeta(sm);
            }
            g.set(i, head, (v, c) -> playerActions(plugin, v, t));
        }
        if (pg > 0) g.set(45, plugin.items().of(Material.ARROW).name("§7« Seite " + pg).build(), (v, c) -> players(plugin, v, pg - 1));
        if (pg < pages - 1) g.set(53, plugin.items().of(Material.ARROW).name("§7Seite " + (pg + 2) + " »").build(), (v, c) -> players(plugin, v, pg + 1));
        g.set(49, plugin.items().of(Material.CLOCK).name("§b§lAktualisieren").build(), (v, c) -> players(plugin, v, pg));
        g.set(48, plugin.items().of(Material.ARROW).name("§7Zurück zum Host-Menü").build(), (v, c) -> HostGui.open(plugin, v));
        g.open(viewer);
    }

    /** Host-Aktionen für einen Spieler. */
    public static void playerActions(OdvPlugin plugin, Player viewer, Player t) {
        if (!t.isOnline()) {
            players(plugin, viewer, 0);
            return;
        }
        var game = plugin.game();
        boolean ingame = game.getState().isIngame();
        boolean alive = game.isAlive(t);
        boolean host = plugin.host().isHost(viewer);
        ActionGui g = new ActionGui(4, "§8» §a§l" + t.getName()).fill(glass(plugin, Material.GRAY_STAINED_GLASS_PANE));
        ItemStack head = plugin.items().of(Material.PLAYER_HEAD).name(plugin.ranks().of(t).nameColor() + "§l" + t.getName())
                .lore("§7Ping: §f" + t.getPing() + "ms", "§7Welt: §f" + t.getWorld().getName()).build();
        if (head.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta sm) {
            sm.setPlayerProfile(t.getPlayerProfile());
            head.setItemMeta(sm);
        }
        g.item(4, head);
        g.set(10, plugin.items().of(Material.ENDER_PEARL).name("§b§lHinteleportieren").build(), (v, c) -> {
            v.closeInventory();
            v.teleport(t.getLocation());
        });
        g.set(11, plugin.items().of(Material.CHEST).name("§6§lInventar ansehen").build(), (v, c) -> v.performCommand("invsee " + t.getName()));
        g.set(12, plugin.items().of(Material.PACKED_ICE).name(plugin.host().isFrozen(t) ? "§a§lAuftauen" : "§b§lEinfrieren").lore("§7Support-Freeze").build(), (v, c) -> {
            v.performCommand("freeze " + t.getName());
            playerActions(plugin, v, t);
        });
        g.set(13, plugin.items().of(Material.BOOK).name("§d§lStatus (/check)").build(), (v, c) -> {
            v.closeInventory();
            v.performCommand("check " + t.getName());
        });
        if (host) {
            g.set(14, plugin.items().of(Material.SPYGLASS).name(game.isMod(t) ? "§c§lMod-Modus beenden" : "§6§lZum Mod/Zuschauer machen").build(), (v, c) -> {
                v.performCommand("mod " + t.getName());
                playerActions(plugin, v, t);
            });
            if (ingame && !alive) {
                if (plugin.teams().getTeam(t) != null) {
                    g.set(19, plugin.items().of(Material.TOTEM_OF_UNDYING).name("§a§lWiederbeleben").lore("§7Nur mit Clip-Beweis!", "§7Gibt das Inventar vom Tod zurück").build(), (v, c) -> {
                        v.closeInventory();
                        v.performCommand("revive " + t.getName());
                    });
                }
                g.set(20, plugin.items().of(Material.LIME_DYE).name("§a§lNachjoinen lassen").lore("§7Team auswählen").build(), (v, c) -> {
                    if (plugin.settings().isSolo()) {
                        v.closeInventory();
                        v.performCommand("latejoin " + t.getName());
                    } else latejoin(plugin, v, t);
                });
            }
            if (ingame && alive) {
                g.set(19, plugin.items().of(Material.IRON_SWORD).name("§e§lKit geben").build(), (v, c) -> v.performCommand("setkit " + t.getName()));
                g.set(20, plugin.items().of(Material.LEAD).name("§6§lTeam wechseln").lore("§7Nur bis Ende der Schutzzeit").build(), (v, c) -> teamPicker(plugin, v, t));
                g.set(21, plugin.items().of(Material.BARRIER).name("§c§lAus der Runde nehmen").lore("§7Kein Kill, kein Strike").build(), (v, c) -> {
                    v.closeInventory();
                    v.performCommand("remove " + t.getName());
                });
            }
            if (!ingame && !plugin.settings().isSolo()) {
                g.set(19, plugin.items().of(Material.LEAD).name("§6§lTeam zuweisen").build(), (v, c) -> teamPicker(plugin, v, t));
            }
        }
        if (viewer.hasPermission("odv.kick")) {
            g.set(25, plugin.items().of(Material.IRON_DOOR).name("§c§lKicken").build(), (v, c) -> {
                v.closeInventory();
                v.performCommand("kick " + t.getName() + " Vom Host gekickt");
            });
        }
        if (viewer.hasPermission("odv.tempban")) {
            g.set(16, plugin.items().of(Material.ANVIL).name("§4§lBannen…").lore("§7Ban-ID auswählen").build(), (v, c) -> banPicker(plugin, v, t.getName()));
        }
        g.set(31, plugin.items().of(Material.ARROW).name("§7Zurück").build(), (v, c) -> players(plugin, v, 0));
        g.open(viewer);
    }

    private static void teamPicker(OdvPlugin plugin, Player viewer, Player t) {
        int count = Math.min(45, Math.max(2, plugin.settings().teamCount));
        int rows = Math.min(6, (int) Math.ceil(count / 9.0) + 1);
        ActionGui g = new ActionGui(rows, "§8» §6§lTeam für " + t.getName()).fill(glass(plugin, Material.BLACK_STAINED_GLASS_PANE));
        for (int n = 1; n <= count; n++) {
            MTeam tm = plugin.teams().peek(n);
            int num = n;
            g.set(n - 1, plugin.items().of(tm.getWool(), Math.max(1, tm.getMembers().size())).name(tm.getDisplayName() + " §8#" + n)
                    .lore("§7Spieler: §f" + tm.getMembers().size() + "/" + plugin.settings().teamSize).build(), (v, c) -> {
                v.closeInventory();
                v.performCommand("teamchange " + t.getName() + " " + num);
            });
        }
        g.set(rows * 9 - 1, plugin.items().of(Material.ARROW).name("§7Zurück").build(), (v, c) -> playerActions(plugin, v, t));
        g.open(viewer);
    }

    public static void banPicker(OdvPlugin plugin, Player viewer, String target) {
        var ids = plugin.settings().banIds;
        ActionGui g = new ActionGui(4, "§8» §4§lBannen: " + target).fill(glass(plugin, Material.BLACK_STAINED_GLASS_PANE));
        int i = 0;
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        for (var en : ids.entrySet()) {
            if (i >= slots.length) break;
            var bp = en.getValue();
            int id = en.getKey();
            g.set(slots[i++], plugin.items().of(bp.mute() ? Material.ORANGE_DYE : bp.duration() == 0 ? Material.NETHERITE_SWORD : Material.IRON_SWORD)
                    .name("§c§l#" + id + " §f" + bp.reason())
                    .lore((bp.mute() ? "§6Mute " : "§cBan ") + (bp.duration() == 0 ? "§4permanent" : "§e" + de.crafterteam.odv.game.Punishments.formatDuration(bp.duration())),
                            "", "§eKlick: §7ausführen").build(), (v, c) -> {
                v.closeInventory();
                v.performCommand("ban " + target + " " + id);
            });
        }
        g.set(31, plugin.items().of(Material.ARROW).name("§7Abbrechen").build(), (v, c) -> v.closeInventory());
        g.open(viewer);
    }

    // ================================================================ Latejoin

    /**
     * Teamauswahl für einen Latejoin. viewer = wer klickt, target = wer nachjoint (gleich bei Selbst-Latejoin).
     */
    public static void latejoin(OdvPlugin plugin, Player viewer, Player target) {
        Settings s = plugin.settings();
        boolean self = viewer.equals(target);
        int count = Math.max(2, s.teamCount);
        int rows = Math.min(6, (int) Math.ceil(Math.min(45, count) / 9.0) + 1);
        ActionGui g = new ActionGui(rows, "§8» §a§l" + (self ? "Team wählen" : "Team für " + target.getName()))
                .fill(glass(plugin, Material.BLACK_STAINED_GLASS_PANE));
        UUID tid = target.getUniqueId();
        for (int n = 1; n <= Math.min(45, count); n++) {
            MTeam t = plugin.teams().peek(n);
            int alive = 0;
            List<String> lore = new ArrayList<>();
            for (UUID u : t.getMembers()) {
                boolean on = plugin.game().isAlive(u);
                if (on) alive++;
                lore.add((on ? "§a● " : "§8✘ ") + plugin.stats().name(u));
            }
            boolean full = t.getMembers().size() >= s.teamSize;
            List<String> l = new ArrayList<>();
            l.add("§7Spieler: " + (full ? "§c" : "§a") + t.getMembers().size() + "§7/§f" + s.teamSize + " §8(" + alive + " leben)");
            l.addAll(lore);
            l.add("");
            l.add(full ? (self ? "§cVoll" : "§eKlick: §7trotzdem hinzufügen (Host)") : "§eKlick: §7beitreten");
            int num = n;
            g.set(n - 1, plugin.items().of(t.getWool(), Math.max(1, t.getMembers().size())).name(t.getDisplayName() + " §8#" + n).lore(l).build(), (pl, c) -> {
                MTeam real = plugin.teams().find(String.valueOf(num));
                if (real == null) return;
                if (self && real.getMembers().size() >= s.teamSize) {
                    Txt.send(pl, s.prefix + "§cDieses Team ist voll.");
                    return;
                }
                finish(plugin, pl, target, real);
            });
        }
        int last = rows * 9;
        g.set(last - 5, plugin.items().of(Material.NETHER_STAR).name("§b§lAutomatisch").lore("§7Team mit den wenigsten Spielern").build(),
                (pl, c) -> finish(plugin, pl, target, null));
        g.set(last - 1, plugin.items().of(Material.BARRIER).name("§cAbbrechen").build(), (pl, c) -> pl.closeInventory());
        if (Bukkit.getPlayer(tid) != null) g.open(viewer);
    }

    private static void finish(OdvPlugin plugin, Player viewer, Player target, MTeam team) {
        viewer.closeInventory();
        if (viewer.equals(target)) {
            String deny = plugin.game().selfLateJoinDenied(target);
            if (deny != null) {
                Txt.send(viewer, plugin.settings().prefix + deny);
                return;
            }
        }
        String err = plugin.game().lateJoin(target, team);
        if (err != null) Txt.send(viewer, plugin.settings().prefix + err);
    }
}
