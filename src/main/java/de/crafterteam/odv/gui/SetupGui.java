package de.crafterteam.odv.gui;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.Settings;
import de.crafterteam.odv.data.Kits;
import de.crafterteam.odv.feature.Feature;
import de.crafterteam.odv.game.GameState;
import de.crafterteam.odv.scenario.Scenario;
import de.crafterteam.odv.util.Txt;
import de.crafterteam.odv.world.ChunkPregen;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/** Setup: Startseite (Schnellsetup, Modus, Start) + 12 Kategorien. Zahlen: Links +, Rechts −, Shift = großer Schritt. */
public class SetupGui implements InventoryHolder {

    public enum Page {
        MAIN("§8» §b§lODV-Setup"), TEAMS("§8» §bTeams"), TIMES("§8» §bZeiten"), BORDER("§8» §bBorder"), ARENA("§8» §bArena & Lobby"),
        SPAWNS("§8» §bSpawns"), KIT("§8» §bKits"), COMBAT("§8» §bKampf (PvP)"), SCENARIOS("§8» §bSzenarien"), FEATURES("§8» §bFeatures"),
        WORLD("§8» §bWelt & Anti-Xray"), RULES("§8» §bRegeln & Backup"), STORAGE("§8» §bSpeicher");
        final String title;

        Page(String title) {
            this.title = title;
        }
    }

    private final OdvPlugin plugin;
    private final Page page;
    private final Inventory inv;
    private final Map<Integer, BiConsumer<Player, ClickType>> actions = new HashMap<>();

    public SetupGui(OdvPlugin plugin, Page page) {
        this.plugin = plugin;
        this.page = page;
        this.inv = Bukkit.createInventory(this, 54, Txt.c(page.title));
        render();
    }

    public static void open(OdvPlugin plugin, Player p, Page page) {
        p.openInventory(new SetupGui(plugin, page).getInventory());
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    private Settings s() {
        return plugin.settings();
    }

    // ===================================================================== Seiten

    private void render() {
        inv.clear();
        actions.clear();
        ItemStack glass = plugin.items().of(page == Page.MAIN ? Material.LIGHT_BLUE_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 9; i++) inv.setItem(i, glass);
        for (int i = 45; i < 54; i++) inv.setItem(i, glass);
        if (page != Page.MAIN) set(45, plugin.items().of(Material.ARROW).name("§7« Zurück").build(), (p, c) -> open(plugin, p, Page.MAIN));
        set(53, plugin.items().of(Material.BARRIER).name("§cSchließen").build(), (p, c) -> p.closeInventory());
        switch (page) {
            case MAIN -> main();
            case TEAMS -> teams();
            case TIMES -> times();
            case BORDER -> border();
            case ARENA -> arena();
            case SPAWNS -> spawns();
            case KIT -> kits();
            case COMBAT -> combat();
            case SCENARIOS -> scenarios();
            case FEATURES -> features();
            case WORLD -> world();
            case RULES -> rules();
            case STORAGE -> storage();
        }
    }

    private void main() {
        boolean ready = plugin.arena().isReady();
        GameState st = plugin.game().getState();
        inv.setItem(4, plugin.items().of(Material.BOOK).name("§b§lStatus").lore(
                ok(ready) + " §7Arena" + (ready ? " §8(" + plugin.arena().getWorld().getName() + ")" : " §cfehlt"),
                ok(plugin.arena().getPractice() != null) + " §7Practice-Arena",
                "§7Spawns: §f" + s().spawnStyle.display + (s().plainsOnly ? " §8(nur Plains)" : ""),
                "§7Kampf: §f" + (s().oldPvp ? "1.8.9-PvP" : "26.3-PvP"),
                "§7Szenarien: §e" + plugin.scenarios().activeList(),
                "§7Speicher: §f" + (plugin.stats().isMysql() ? "MySQL" : "lokal"),
                "", "§7Phase: §e" + st.display + " §8| §7Teilnehmer: §a" + plugin.game().candidates().size()).build());

        set(20, plugin.items().of(Material.GRASS_BLOCK).name(ready ? "§a§lSchnellsetup §8(neue Arena)" : "§a§l① Schnellsetup").lore(
                "§7Mit einem Klick:",
                "§8• §7neue Welt mit §b" + s().diamondRate + "x Diamanten",
                "§8• §7Spawn mit Netherportal in Plains",
                "§8• §7schwebende Lobby + Practice-Arena",
                "§8• §7Border + Map-Vorgenerierung",
                "", ready ? "§7Die alte Welt bleibt erhalten." : "§eDanach ist alles spielbereit!", "§eKlick: §7starten").glow(!ready).build(), (p, c) -> {
            if (!lobbyOnly(p)) return;
            p.closeInventory();
            plugin.spawns().quickSetup(p);
        });

        boolean meetup = s().mode == Settings.Mode.MEETUP;
        set(22, plugin.items().of(meetup ? Material.DIAMOND_SWORD : Material.IRON_PICKAXE).name("§e§l② Modus: " + s().mode.title).lore(
                meetup ? "§7Startkit, kurze Schutzzeit, kleine Map" : "§7Farmen, Nether, lange Schutzzeit",
                "§7Schutz §f" + Txt.time(s().grace) + " §8| §7Border §f" + s().borderInitial,
                "", "§eKlick: §7zu " + (meetup ? "§a§lODV" : "§b§lMEETUP") + " §7wechseln").build(), (p, c) -> {
            if (!lobbyOnly(p)) return;
            s().preset(meetup ? Settings.Mode.ODV : Settings.Mode.MEETUP);
            plugin.game().applyWorldSettings();
            plugin.game().refreshHolograms();
            refresh(p);
        });

        if (st == GameState.LOBBY) {
            set(24, plugin.items().of(Material.EMERALD_BLOCK).name("§a§l③ Runde starten").lore(
                    "§7Teilnehmer: §a" + plugin.game().candidates().size(), "§7Countdown: §f10 Sekunden").glow(true).build(), (p, c) -> {
                p.closeInventory();
                String err = plugin.game().forceStart(10);
                if (err != null) Txt.send(p, s().prefix + err);
                else {
                    if (plugin.game().getHost() == null) plugin.game().setHost(p.getUniqueId());
                    plugin.game().broadcast("§aRunde gestartet von §e" + p.getName() + "§a.");
                }
            });
        } else {
            set(24, plugin.items().of(Material.REDSTONE_BLOCK).name("§c§lRunde stoppen").lore("§7Phase: §e" + st.display).build(), (p, c) -> {
                p.closeInventory();
                plugin.game().stop();
            });
        }
        category(28, Material.RED_WOOL, "§b§lTeams", Page.TEAMS, "§7Größe, Anzahl, Friendly Fire, Auto-Start");
        category(29, Material.CLOCK, "§b§lZeiten", Page.TIMES, "§7Countdown, Freeze, Schutz, Showdown");
        category(30, Material.IRON_BARS, "§b§lBorder", Page.BORDER, "§7Start/Ende, wann & wie schnell");
        category(31, Material.RED_BED, "§b§lArena & Lobby", Page.ARENA, "§7Lobby, Mitte, Practice, Vorgenerierung");
        category(32, Material.ENDER_PEARL, "§b§lSpawns", Page.SPAWNS, "§7Varo-Löcher, Plattform oder Zufall");
        category(33, Material.CHEST, "§b§lKits", Page.KIT, "§7Meetup-, ODV- & Practice-Kit");
        category(34, Material.NETHERITE_SWORD, "§b§lKampf (PvP)", Page.COMBAT, "§71.8.9-PvP oder 26.3-PvP");
        category(38, Material.TNT, "§b§lSzenarien", Page.SCENARIOS, "§7CutClean, Timebomb, Timber & mehr");
        category(39, Material.NETHER_STAR, "§b§lFeatures", Page.FEATURES, "§7Die 10 neuen Features");
        category(40, Material.DIAMOND_ORE, "§b§lWelt & Anti-Xray", Page.WORLD, "§7Dia-Rate, Plains, Xray-Schutz");
        category(41, Material.GOLDEN_APPLE, "§b§lRegeln & Backup", Page.RULES, "§7Regeneration, Nether, Backups");
        category(42, Material.ENDER_CHEST, "§b§lSpeicher", Page.STORAGE, "§7MySQL oder lokal");
        set(49, plugin.items().of(Material.GOLDEN_HELMET).name("§6§lHost werden").lore("§7Host: §f" + (plugin.game().getHost() == null ? "-"
                : plugin.stats().name(plugin.game().getHost())), "§eKlick: §7du bist Host").build(), (p, c) -> {
            plugin.game().setHost(p.getUniqueId());
            refresh(p);
        });
    }

    private void teams() {
        number(20, Material.PLAYER_HEAD, "Teamgröße", 1, 1, 1, 10, () -> s().teamSize, v -> {
            s().teamSize = v;
            plugin.teams().reset();
        }, v -> v <= 1 ? "Solo" : v + " Spieler", true);
        number(22, Material.BLUE_WOOL, "Teamanzahl", 1, 5, 2, 45, () -> s().teamCount, v -> {
            s().teamCount = v;
            plugin.teams().reset();
        }, null, true);
        toggle(24, Material.IRON_SWORD, "Friendly Fire", () -> s().friendlyFire, b -> s().friendlyFire = b, "§7Schaden unter Teammitgliedern");
        number(30, Material.EMERALD, "Mindestspieler", 1, 5, 1, 500, () -> s().minPlayers, v -> s().minPlayers = v, null, false);
        toggle(32, Material.REPEATER, "Auto-Start", () -> s().autoStart, b -> s().autoStart = b, "§7Countdown startet automatisch");
        number(34, Material.OAK_SIGN, "Max. Spieler (Anzeige)", 5, 25, 2, 1000, () -> s().maxPlayers, v -> s().maxPlayers = v, null, false);
    }

    private void times() {
        IntFunction<String> t = Txt::time;
        number(20, Material.CLOCK, "Lobby-Countdown", 5, 30, 5, 600, () -> s().countdown, v -> s().countdown = v, t, false);
        number(22, Material.ICE, "Freeze auf dem Spawn", 1, 5, 0, 120, () -> s().freeze, v -> s().freeze = v, t, false);
        number(24, Material.IRON_CHESTPLATE, "Schutzzeit (kein PvP)", 10, 60, 0, 7200, () -> s().grace, v -> s().grace = v, t, false);
        number(30, Material.LEAD, "Reconnect-Frist", 10, 60, 0, 600, () -> s().disconnectGrace, v -> s().disconnectGrace = v, t, false);
        number(32, Material.FIREWORK_ROCKET, "Wartezeit nach Sieg", 5, 15, 3, 300, () -> s().endDelay, v -> s().endDelay = v, t, false);
        number(34, Material.NETHERITE_SWORD, "Showdown nach (Min.)", 1, 5, 5, 600, () -> s().timeLimit, v -> s().timeLimit = v, v -> v + " min", false);
    }

    private void border() {
        IntFunction<String> t = Txt::time, b = v -> v + " Blöcke";
        number(20, Material.IRON_BARS, "Start-Durchmesser", 50, 250, 100, 20000, () -> s().borderInitial, v -> {
            s().borderInitial = v;
            plugin.game().applyWorldSettings();
        }, b, true);
        number(22, Material.NETHER_BRICK_FENCE, "End-Durchmesser", 10, 50, 10, 20000, () -> s().borderFinal, v -> s().borderFinal = v, b, false);
        number(24, Material.COMPASS, "Schrumpfen ab", 30, 300, 0, 14400, () -> s().shrinkAfter, v -> s().shrinkAfter = v, t, false);
        number(31, Material.HOPPER, "Schrumpf-Dauer", 30, 300, 30, 14400, () -> s().shrinkDuration, v -> s().shrinkDuration = v, t, false);
    }

    private void arena() {
        Location lobby = plugin.arena().getLobby(), center = plugin.arena().getCenter(), practice = plugin.arena().getPractice();
        set(20, plugin.items().of(Material.RED_BED).name("§e§lLobby hier setzen").lore("§7Aktuell: §f" + loc(lobby), "§eKlick: §7deine Position").build(), (p, c) -> {
            if (!lobbyOnly(p)) return;
            plugin.arena().setLobby(p.getLocation());
            plugin.game().refreshHolograms();
            refresh(p);
        });
        set(21, plugin.items().of(Material.BEACON).name("§e§lMitte (Spawn) hier setzen").lore("§7Aktuell: §f" + loc(center), "§7Border & Spawnkreis drehen sich darum").build(), (p, c) -> {
            if (!lobbyOnly(p)) return;
            plugin.arena().setCenter(p.getLocation());
            plugin.game().applyWorldSettings();
            plugin.game().refreshHolograms();
            refresh(p);
        });
        set(22, plugin.items().of(Material.IRON_SWORD).name("§e§lPractice hier setzen").lore("§7Aktuell: §f" + loc(practice), "§eShift-Rechts: §7Practice entfernen").build(), (p, c) -> {
            plugin.arena().setPractice(c == ClickType.SHIFT_RIGHT ? null : p.getLocation());
            plugin.game().resetToLobbyItems();
            refresh(p);
        });
        set(24, plugin.items().of(Material.FILLED_MAP).name("§b§lMap vorgenerieren").lore("§7Alle Chunks innerhalb der Border",
                ChunkPregen.isRunning() ? "§eLäuft – Klick: stoppen" : "§eKlick: §7starten").build(), (p, c) -> {
            p.closeInventory();
            if (ChunkPregen.isRunning()) {
                ChunkPregen.stop();
                return;
            }
            World w = plugin.arena().getWorld();
            if (w == null) Txt.send(p, s().prefix + "§cZuerst Arena einrichten.");
            else ChunkPregen.start(plugin, w, center.getBlockX(), center.getBlockZ(), s().borderInitial / 2 + 48, p);
        });
        number(30, Material.OAK_FENCE, "Spawn-Schutzradius", 2, 10, 0, 200, () -> s().protectionRadius, v -> s().protectionRadius = v, v -> v == 0 ? "aus" : v + " Blöcke", false);
        set(32, plugin.items().of(Material.LEVER).name("§e§lBaumodus").lore("§7Creative an/aus (nur Lobby)").build(), (p, c) -> {
            if (!lobbyOnly(p)) return;
            p.closeInventory();
            p.setGameMode(p.getGameMode() == GameMode.CREATIVE ? GameMode.ADVENTURE : GameMode.CREATIVE);
        });
        set(34, plugin.items().of(Material.ARMOR_STAND).name("§e§lHologramme neu laden").build(), (p, c) -> {
            plugin.game().refreshHolograms();
            refresh(p);
        });
    }

    private void spawns() {
        Settings.SpawnStyle st = s().spawnStyle;
        set(19, plugin.items().of(st == Settings.SpawnStyle.HOLES ? Material.SMOOTH_STONE_SLAB : st == Settings.SpawnStyle.PLATFORM ? Material.STONE_BRICKS : Material.OAK_SAPLING)
                .name("§d§lSpawn-Art: §f" + st.display).lore(
                        "§8• §fVaro-Löcher: §7Loch + Slab-Kreuz im Kreis",
                        "§8• §fPlattformen: §7Schematic pro Team",
                        "§8• §fZufällig: §7sichere Orte mit Abstand", "", "§eKlick: §7wechseln").build(), (p, c) -> {
            s().spawnStyle = Settings.SpawnStyle.values()[(st.ordinal() + 1) % Settings.SpawnStyle.values().length];
            save(p);
        });
        toggle(20, Material.GRASS_BLOCK, "Nur Plains", () -> s().plainsOnly, b -> s().plainsOnly = b, "§7Spawns nur in Plains-Biomen");
        toggle(21, Material.MOSS_BLOCK, "Nur natürlicher Boden", () -> s().naturalOnly, b -> s().naturalOnly = b, "§7flach, kein Wasser, keine Bäume");
        toggle(22, Material.NAME_TAG, "Teamnamen über Spawns", () -> s().nameTags, b -> s().nameTags = b);
        number(23, Material.ENDER_EYE, "Kreis-Radius", 10, 50, 0, 10000, () -> s().spawnRadius, v -> s().spawnRadius = v,
                v -> v == 0 ? "Auto (" + plugin.spawns().autoRadius(previewCount()) + ")" : v + " Blöcke", false);
        number(24, Material.COMPASS, "Mindestabstand (Zufall)", 5, 25, 5, 1000, () -> s().minTeamDistance, v -> s().minTeamDistance = v, v -> v + " Blöcke", false);
        toggle(25, Material.REDSTONE_TORCH, "Bei Start neu bauen", () -> s().regenerateOnStart, b -> s().regenerateOnStart = b, "§7Passend zur Teamanzahl");
        List<String> files = plugin.spawns().list();
        List<String> lore = new ArrayList<>();
        lore.add("§7Für Spawn-Art §fPlattformen");
        for (String n : files) lore.add((n.equals(s().teamSchematic) ? "§a» " : "§8• ") + "§7" + n);
        if (files.isEmpty()) lore.add("§8keine – Standard-Plattform");
        lore.add("§eKlick: §7nächste §8(Ordner schematics/, .schem)");
        set(29, plugin.items().of(Material.PAPER).name("§d§lPlattform: §f" + s().teamSchematic).lore(lore).build(), (p, c) -> {
            if (files.isEmpty()) return;
            int i = files.indexOf(s().teamSchematic);
            s().teamSchematic = files.get(((i + (c.isLeftClick() ? 1 : -1)) % files.size() + files.size()) % files.size());
            save(p);
        });
        set(31, plugin.items().of(Material.GOLDEN_AXE).name("§6§lWand holen").lore("§7Ecken markieren, auf Spawnpunkt stellen,", "§e/odv schem save <Name>").build(), (p, c) -> {
            p.closeInventory();
            p.getInventory().addItem(plugin.wand().item());
        });
        set(38, plugin.items().of(Material.EMERALD_BLOCK).name("§a§lSpawns jetzt bauen").lore("§7Anzahl: §f" + previewCount(), "§7Alte Spawns werden entfernt.").build(), (p, c) -> {
            if (!lobbyOnly(p)) return;
            p.closeInventory();
            plugin.spawns().generate(previewCount(), p, null);
        });
        set(40, plugin.items().of(Material.ENDER_PEARL).name("§d§lZu einem Spawn").lore("§7Gespeichert: §f" + plugin.arena().getSpawns().size()).build(), (p, c) -> {
            List<Location> l = plugin.arena().getSpawns();
            if (l.isEmpty()) return;
            p.closeInventory();
            p.teleport(l.get((int) (Math.random() * l.size())).clone().add(0, 0.2, 0));
        });
        set(42, plugin.items().of(Material.REDSTONE_BLOCK).name("§c§lSpawns entfernen").lore(plugin.spawns().hasUndo() ? "§7Rückgängig möglich" : "§8nichts zu entfernen").build(), (p, c) -> {
            p.closeInventory();
            plugin.spawns().removePasted(p);
        });
    }

    private void kits() {
        int slot = 20;
        for (Kits.Type t : Kits.Type.values()) {
            Kits.Type type = t;
            set(slot, plugin.items().of(t == Kits.Type.MEETUP ? Material.DIAMOND_CHESTPLATE : t == Kits.Type.ODV ? Material.STONE_PICKAXE : Material.IRON_SWORD)
                    .name("§e§lKit: " + t.name()).lore("§7Status: §f" + (plugin.kits().hasCustom(t) ? "eigenes Kit" : "Standard"), "",
                            "§eLinks: §7Inventar als Kit speichern", "§eRechts: §7Kit ansehen", "§eShift-Rechts: §7Standard verwenden").build(), (p, c) -> {
                try {
                    if (c == ClickType.SHIFT_RIGHT) {
                        plugin.kits().reset(type);
                        Txt.send(p, s().prefix + "§7Standard-Kit " + type + " aktiv.");
                    } else if (c.isRightClick()) {
                        if (!lobbyOnly(p)) return;
                        p.closeInventory();
                        plugin.kits().give(p, type);
                        return;
                    } else {
                        plugin.kits().save(p, type);
                        Txt.send(p, s().prefix + "§aKit " + type + " gespeichert.");
                    }
                } catch (IOException e) {
                    Txt.send(p, s().prefix + "§c" + e.getMessage());
                }
                refresh(p);
            });
            slot += 2;
        }
    }

    private void combat() {
        toggle(20, Material.WOODEN_SWORD, "1.8.9-PvP", () -> s().oldPvp, b -> {
            s().oldPvp = b;
            Bukkit.getScheduler().runTask(plugin, () -> plugin.combat().prepareAll());
        }, "§7An: kein Angriffs-Cooldown, kein Sweep", "§7Aus: normales 26.3-PvP");
        toggle(21, Material.DIAMOND_SWORD, "Schwert-Blocken", () -> s().swordBlocking, b -> {
            s().swordBlocking = b;
            Bukkit.getScheduler().runTask(plugin, () -> plugin.combat().prepareAll());
        }, "§7Rechtsklick blockt 50 % (wie 1.8)");
        toggle(22, Material.ENCHANTED_GOLDEN_APPLE, "Alte Notch-Äpfel", () -> s().oldGoldenApples, b -> s().oldGoldenApples = b, "§7Regeneration V wie in 1.8");
        toggle(23, Material.NETHERITE_AXE, "1.8-Waffenschaden", () -> s().oldDamage, b -> s().oldDamage = b, "§7Äxte schwächer, Schwerter wie 1.8");
        toggle(24, Material.SHIELD, "Schilde erlaubt", () -> s().shields, b -> {
            s().shields = b;
            Bukkit.getScheduler().runTask(plugin, () -> plugin.combat().prepareAll());
        });
        inv.setItem(31, plugin.items().of(Material.BOOK).name("§7Hinweis").lore("§7Mit §fViaVersion + ViaRewind §7können", "§7auch 1.8.9-Clients joinen.").build());
    }

    private void scenarios() {
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 31};
        Scenario[] all = Scenario.values();
        for (int i = 0; i < all.length; i++) {
            Scenario sc = all[i];
            toggle(slots[i], sc.icon, sc.display, () -> s().has(sc), b -> {
                if (b) s().scenarios.add(sc);
                else s().scenarios.remove(sc);
            }, "§7" + sc.description);
        }
        number(38, Material.TNT_MINECART, "Timebomb-Zeit", 5, 15, 5, 300, () -> s().timebombSeconds, v -> s().timebombSeconds = v, v -> v + "s", false);
        number(42, Material.APPLE, "Xtra-Apples Chance (%)", 1, 5, 1, 100, () -> (int) s().xtraAppleChance, v -> s().xtraAppleChance = v, v -> v + " %", false);
    }

    private void features() {
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 29, 30, 31, 33};
        Feature[] all = Feature.values();
        for (int i = 0; i < all.length && i < slots.length; i++) {
            Feature f = all[i];
            toggle(slots[i], f.icon, f.display, () -> s().has(f), b -> {
                if (b) s().features.add(f);
                else s().features.remove(f);
                if (f == Feature.PRACTICE) plugin.game().resetToLobbyItems();
            }, "§7" + f.description);
        }
        number(40, Material.CHEST, "Supply-Drop alle", 30, 120, 60, 3600, () -> s().supplyInterval, v -> s().supplyInterval = v, Txt::time, false);
    }

    private void world() {
        number(20, Material.DIAMOND, "Diamant-Rate (x10)", 1, 5, 0, 100, () -> (int) Math.round(s().diamondRate * 10), v -> s().diamondRate = v / 10.0,
                v -> (v / 10.0) + "x", false);
        number(22, Material.GOLD_INGOT, "Gold-Rate (x10)", 1, 5, 0, 100, () -> (int) Math.round(s().goldRate * 10), v -> s().goldRate = v / 10.0,
                v -> (v / 10.0) + "x", false);
        inv.setItem(24, plugin.items().of(Material.PAPER).name("§7Hinweis").lore("§7Die Rate gilt für §fneu generierte", "§7Chunks – also nach dem Schnellsetup.").build());
        toggle(29, Material.SPYGLASS, "Xray-Erkennung", () -> s().xrayDetection, b -> s().xrayDetection = b, "§7Meldet auffälliges Abbauen an Admins");
        number(31, Material.DEEPSLATE_DIAMOND_ORE, "Meldung ab Adern", 1, 3, 1, 100, () -> s().xrayAlertVeins, v -> s().xrayAlertVeins = v, null, false);
        set(33, plugin.items().of(Material.BEDROCK).name("§c§lPaper-Anti-Xray").lore("§7Status: " + plugin.antiXray().paperStatus(),
                "§7Versteckt Erze komplett vor dem Client.", "", "§eShift-Klick: §7aktivieren §8(Neustart nötig)").build(), (p, c) -> {
            if (!c.isShiftClick()) return;
            plugin.antiXray().enablePaper(p);
            refresh(p);
        });
    }

    private void rules() {
        toggle(19, Material.GOLDEN_APPLE, "Natürliche Regeneration", () -> s().naturalRegen, b -> {
            s().naturalRegen = b;
            plugin.game().applyWorldSettings();
        }, "§7Aus = UHC-Regeln");
        toggle(20, Material.GOLD_INGOT, "Goldapfel bei Tod", () -> s().goldenApple, b -> s().goldenApple = b);
        toggle(21, Material.LIGHTNING_ROD, "Blitz bei Tod", () -> s().lightning, b -> s().lightning = b);
        toggle(22, Material.OBSIDIAN, "Nether erlaubt", () -> s().nether, b -> {
            s().nether = b;
            plugin.game().refreshHolograms();
        }, "§7Netherportal am Spawn nutzbar");
        toggle(23, Material.TNT, "Server-Stop nach Runde", () -> s().stopOnEnd, b -> s().stopOnEnd = b, "§7Für frische Map per Restart-Script");
        toggle(29, Material.ENDER_CHEST, "Backup vor jeder Runde", () -> s().backupOnStart, b -> s().backupOnStart = b);
        set(31, plugin.items().of(Material.WRITABLE_BOOK).name("§b§lBackup jetzt").lore("§7ZIP in plugins/ODVMeetup/backups").build(), (p, c) -> plugin.stats().backup(p));
        set(33, plugin.items().of(Material.COMPARATOR).name("§7§lConfig neu laden").build(), (p, c) -> {
            if (!lobbyOnly(p)) return;
            plugin.reloadAll();
            refresh(p);
        });
    }

    private void storage() {
        boolean my = plugin.stats().isMysql();
        inv.setItem(20, plugin.items().of(my ? Material.ENDER_CHEST : Material.CHEST).name("§b§lAktiv: " + (my ? "MySQL" : "Lokal (SQLite)")).lore(
                my ? "§7Server: §f" + s().dbHost + ":" + s().dbPort + "/" + s().dbName : "§7Datei: §fplugins/ODVMeetup/stats.db").build());
        set(22, plugin.items().of(Material.REPEATER).name("§e§lSpeicher-Typ: §f" + (s().mysql ? "MYSQL" : "LOCAL")).lore(
                "§7MySQL-Zugangsdaten in der config.yml", "§7unter §fstorage.mysql §7eintragen.", "", "§eKlick: §7umschalten §8(Neustart nötig)").build(), (p, c) -> {
            s().mysql = !s().mysql;
            plugin.getConfig().set("storage.type", s().mysql ? "MYSQL" : "LOCAL");
            plugin.saveConfig();
            Txt.send(p, s().prefix + "§7Speicher-Typ: §f" + (s().mysql ? "MySQL" : "lokal") + " §8– wird beim nächsten Neustart aktiv.");
            refresh(p);
        });
        inv.setItem(24, plugin.items().of(Material.BOOK).name("§7Was wird gespeichert?").lore("§7Siege, Kills, Tode, Runden,", "§7Wettpunkte, Practice-Kills/-Tode").build());
    }

    // ===================================================================== Bausteine

    private void set(int slot, ItemStack item, BiConsumer<Player, ClickType> action) {
        inv.setItem(slot, item);
        actions.put(slot, action);
    }

    private void category(int slot, Material m, String name, Page target, String desc) {
        set(slot, plugin.items().of(m).name(name).lore(desc, "", "§eKlick: §7öffnen").build(), (p, c) -> open(plugin, p, target));
    }

    private void number(int slot, Material m, String name, int step, int bigStep, int min, int max, IntSupplier get, IntConsumer setter,
                        IntFunction<String> fmt, boolean lobbyOnly) {
        int v = get.getAsInt();
        set(slot, plugin.items().of(m, Math.max(1, Math.min(64, v))).name("§b§l" + name + ": §f" + (fmt == null ? v : fmt.apply(v))).lore(
                "", "§8Links §a+" + step + "  §8Rechts §c-" + step, "§8Shift §7= ±" + bigStep).build(), (p, c) -> {
            if (lobbyOnly && !lobbyOnly(p)) return;
            int d = c.isShiftClick() ? bigStep : step;
            setter.accept(Math.max(min, Math.min(max, get.getAsInt() + (c.isLeftClick() ? d : -d))));
            save(p);
        });
    }

    private void toggle(int slot, Material m, String name, BooleanSupplier get, Consumer<Boolean> setter, String... lore) {
        boolean on = get.getAsBoolean();
        List<String> l = new ArrayList<>(List.of(lore));
        l.add("");
        l.add("§eKlick: §7" + (on ? "ausschalten" : "einschalten"));
        set(slot, plugin.items().of(m).name("§b§l" + name + ": " + (on ? "§aAn" : "§cAus")).lore(l).glow(on).build(), (p, c) -> {
            setter.accept(!get.getAsBoolean());
            save(p);
        });
    }

    private int previewCount() {
        int c = plugin.game().candidates().size();
        return Math.max(2, s().isSolo() ? c : plugin.teams().countTeamsAfterFill(c));
    }

    private boolean lobbyOnly(Player p) {
        if (plugin.game().getState() == GameState.LOBBY && !plugin.spawns().isBusy()) return true;
        Txt.send(p, s().prefix + "§cGeht nur in der Lobby, wenn gerade nichts gebaut wird.");
        return false;
    }

    private void save(Player p) {
        s().save();
        refresh(p);
    }

    private void refresh(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
        render();
    }

    public void click(Player p, int slot, ClickType click) {
        BiConsumer<Player, ClickType> a = actions.get(slot);
        if (a != null) a.accept(p, click);
    }

    private static String ok(boolean b) {
        return b ? "§a✔" : "§c✘";
    }

    private static String loc(Location l) {
        return l == null ? "§cnicht gesetzt" : l.getWorld().getName() + " " + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ();
    }
}
