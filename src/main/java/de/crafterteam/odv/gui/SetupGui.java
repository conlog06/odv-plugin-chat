package de.crafterteam.odv.gui;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.Settings;
import de.crafterteam.odv.game.GameManager;
import de.crafterteam.odv.game.GameState;
import de.crafterteam.odv.schematic.Schematic;
import de.crafterteam.odv.schematic.Wand;
import de.crafterteam.odv.util.ChunkPregen;
import de.crafterteam.odv.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
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
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/**
 * Setup-Menü: Hauptseite mit Schnellsetup, Modus, Start – plus 7 übersichtliche Kategorien.
 * Zahlen: Linksklick +, Rechtsklick −, Shift = großer Schritt.
 */
public class SetupGui implements InventoryHolder {

    public enum Page {
        MAIN("§8» §b§lODV-Setup"), TEAMS("§8» §bTeams"), TIMES("§8» §bZeiten"), BORDER("§8» §bBorder"),
        ARENA("§8» §bArena & Lobby"), SPAWNS("§8» §bSpawns"), KIT("§8» §bKit"), RULES("§8» §bRegeln");

        final String title;

        Page(String title) {
            this.title = title;
        }
    }

    private interface Action {
        void run(Player p, ClickType click);
    }

    private final OdvPlugin plugin;
    private final Page page;
    private final Inventory inv;
    private final Map<Integer, Action> actions = new HashMap<Integer, Action>();

    public SetupGui(OdvPlugin plugin, Page page) {
        this.plugin = plugin;
        this.page = page;
        this.inv = Bukkit.createInventory(this, 54, page.title);
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

    // ===================================================================== Rendering

    private void render() {
        inv.clear();
        actions.clear();
        ItemStack glass = new ItemBuilder(Material.STAINED_GLASS_PANE, 1, (short) (page == Page.MAIN ? 3 : 7)).name(" ").build();
        for (int i = 0; i < 9; i++) inv.setItem(i, glass);
        for (int i = 45; i < 54; i++) inv.setItem(i, glass);
        if (page != Page.MAIN) {
            set(45, new ItemBuilder(Material.ARROW).name("§7« Zurück").build(), new Action() {
                @Override
                public void run(Player p, ClickType c) {
                    open(plugin, p, Page.MAIN);
                }
            });
        }
        set(53, new ItemBuilder(Material.BARRIER).name("§cSchließen").build(), new Action() {
            @Override
            public void run(Player p, ClickType c) {
                p.closeInventory();
            }
        });
        switch (page) {
            case MAIN: main(); break;
            case TEAMS: teams(); break;
            case TIMES: times(); break;
            case BORDER: border(); break;
            case ARENA: arena(); break;
            case SPAWNS: spawns(); break;
            case KIT: kit(); break;
            case RULES: rules(); break;
        }
    }

    private void main() {
        boolean ready = plugin.arena().isReady();
        GameState st = plugin.game().getState();
        boolean customKit = plugin.kits().hasCustom(s().mode);
        boolean schem = plugin.spawns().file(s().teamSchematic).exists();

        inv.setItem(4, new ItemBuilder(Material.BOOK).name("§b§lStatus").lore(
                ok(ready) + " §7Arena eingerichtet" + (ready ? " §8(" + plugin.arena().getWorld().getName() + ")" : ""),
                ok(s().spawnMode == Settings.SpawnMode.SCATTER || schem) + " §7Spawns: §f" + s().spawnMode.display,
                ok(true) + " §7Kit: §f" + (customKit ? "eigenes" : "Standard"),
                "",
                "§7Modus: §f" + s().mode.display + " §8| §7Teams: §f" + (s().isSolo() ? "Solo" : s().teamCount + " × " + s().teamSize),
                "§7Phase: §e" + st.display + " §8| §7Teilnehmer: §a" + plugin.game().candidates().size()).build());

        set(20, new ItemBuilder(Material.GRASS).name(ready ? "§a§lSchnellsetup §8(neue Arena)" : "§a§l① Schnellsetup").lore(
                "§7Erstellt mit einem Klick:",
                "§8• §7neue Welt (ohne Dörfer/Tempel)",
                "§8• §7Lobby-Bau §f" + s().lobbySchematic + " §7in der Mitte",
                "§8• §7Border + Map-Vorgenerierung",
                "",
                ready ? "§7Alte Welt bleibt erhalten." : "§eDanach ist alles spielbereit!",
                "§eKlick: §7starten").glow(!ready).build(), new Action() {
            @Override
            public void run(Player p, ClickType c) {
                if (!lobbyOnly(p)) return;
                p.closeInventory();
                plugin.spawns().quickSetup(p);
            }
        });

        final boolean meetup = s().mode == Settings.Mode.MEETUP;
        set(22, new ItemBuilder(meetup ? Material.DIAMOND_SWORD : Material.IRON_PICKAXE).name("§e§l② Modus: " + s().mode.title).lore(
                meetup ? "§7Startkit, kurze Schutzzeit, kleine Map" : "§7Farmen, lange Schutzzeit, große Map",
                "§7Schutz §f" + Settings.time(s().grace) + " §8| §7Border §f" + s().borderInitial,
                "",
                "§eKlick: §7zu " + (meetup ? "§a§lODV" : "§b§lMEETUP") + " §7wechseln",
                "§8(setzt Zeiten & Border auf das Preset)").build(), new Action() {
            @Override
            public void run(Player p, ClickType c) {
                if (!lobbyOnly(p)) return;
                s().preset(meetup ? Settings.Mode.ODV : Settings.Mode.MEETUP);
                plugin.game().applyWorldSettings();
                for (Player o : Bukkit.getOnlinePlayers()) if (plugin.game().getState().isLobby()) plugin.game().giveLobbyItems(o);
                refresh(p);
            }
        });

        if (st == GameState.LOBBY) {
            set(24, new ItemBuilder(Material.EMERALD_BLOCK).name("§a§l③ Runde starten").lore(
                    "§7Teilnehmer: §a" + plugin.game().candidates().size(),
                    "§7Countdown: §f10 Sekunden", "", "§eKlick: §7los geht's").glow(true).build(), new Action() {
                @Override
                public void run(Player p, ClickType c) {
                    p.closeInventory();
                    String err = plugin.game().forceStart(10);
                    if (err != null) p.sendMessage(s().prefix + err);
                    else plugin.game().broadcast("§aRunde gestartet von §e" + p.getName() + "§a.");
                }
            });
        } else {
            set(24, new ItemBuilder(Material.REDSTONE_BLOCK).name("§c§lRunde stoppen").lore("§7Phase: §e" + st.display, "§7Alle zurück in die Lobby").build(), new Action() {
                @Override
                public void run(Player p, ClickType c) {
                    p.closeInventory();
                    plugin.game().stop();
                }
            });
        }

        category(37, Material.WOOL, (short) 14, "§b§lTeams", Page.TEAMS, "§7Größe, Anzahl, Friendly Fire,", "§7Mindestspieler, Auto-Start");
        category(38, Material.WATCH, (short) 0, "§b§lZeiten", Page.TIMES, "§7Countdown, Freeze, Schutzzeit,", "§7Reconnect-Frist");
        category(39, Material.IRON_FENCE, (short) 0, "§b§lBorder", Page.BORDER, "§7Start/Ende, wann & wie schnell");
        category(40, Material.BED, (short) 0, "§b§lArena & Lobby", Page.ARENA, "§7Lobby/Mitte setzen, Lobby-Bau,", "§7Map vorgenerieren");
        category(41, Material.ENDER_PEARL, (short) 0, "§b§lSpawns", Page.SPAWNS, "§7Plattformen oder Zufall,", "§7Schematic, Radius, Wand");
        category(42, Material.CHEST, (short) 0, "§b§lKit", Page.KIT, "§7Startkit für " + s().mode.display);
        category(43, Material.GOLDEN_APPLE, (short) 0, "§b§lRegeln & Backup", Page.RULES, "§7Regeneration, Goldapfel, Blitz,", "§7Server-Stop, Backups");
    }

    private void teams() {
        number(20, Material.SKULL_ITEM, (short) 3, "Teamgröße", 1, 1, 1, 10,
                new IntSupplier() { public int getAsInt() { return s().teamSize; } },
                new IntConsumer() { public void accept(int v) { s().teamSize = v; plugin.teams().reset(); } },
                new IntFunction<String>() { public String apply(int v) { return v <= 1 ? "Solo" : v + " Spieler"; } }, true);
        number(22, Material.WOOL, (short) 11, "Teamanzahl", 1, 5, 2, 36,
                new IntSupplier() { public int getAsInt() { return s().teamCount; } },
                new IntConsumer() { public void accept(int v) { s().teamCount = v; plugin.teams().reset(); } }, null, true);
        toggle(24, Material.DIAMOND_SWORD, "Friendly Fire",
                new BooleanSupplier() { public boolean getAsBoolean() { return s().friendlyFire; } },
                new Consumer<Boolean>() { public void accept(Boolean b) { s().friendlyFire = b; } }, "§7Schaden unter Teammitgliedern");
        number(30, Material.EMERALD, (short) 0, "Mindestspieler", 1, 5, 1, 200,
                new IntSupplier() { public int getAsInt() { return s().minPlayers; } },
                new IntConsumer() { public void accept(int v) { s().minPlayers = v; } }, null, false);
        toggle(32, Material.DIODE, "Auto-Start",
                new BooleanSupplier() { public boolean getAsBoolean() { return s().autoStart; } },
                new Consumer<Boolean>() { public void accept(Boolean b) { s().autoStart = b; } }, "§7Countdown startet automatisch", "§7sobald genug Spieler da sind");
    }

    private void times() {
        IntFunction<String> t = new IntFunction<String>() { public String apply(int v) { return Settings.time(v); } };
        number(20, Material.WATCH, (short) 0, "Lobby-Countdown", 5, 30, 5, 600,
                new IntSupplier() { public int getAsInt() { return s().countdown; } },
                new IntConsumer() { public void accept(int v) { s().countdown = v; } }, t, false);
        number(22, Material.ICE, (short) 0, "Freeze auf dem Spawn", 1, 5, 0, 120,
                new IntSupplier() { public int getAsInt() { return s().freeze; } },
                new IntConsumer() { public void accept(int v) { s().freeze = v; } }, t, false);
        number(24, Material.IRON_CHESTPLATE, (short) 0, "Schutzzeit (kein PvP)", 10, 60, 0, 7200,
                new IntSupplier() { public int getAsInt() { return s().grace; } },
                new IntConsumer() { public void accept(int v) { s().grace = v; } }, t, false);
        number(30, Material.LEASH, (short) 0, "Reconnect-Frist", 10, 60, 0, 600,
                new IntSupplier() { public int getAsInt() { return s().disconnectGrace; } },
                new IntConsumer() { public void accept(int v) { s().disconnectGrace = v; } }, t, false);
        number(32, Material.FIREWORK, (short) 0, "Wartezeit nach Sieg", 5, 15, 3, 300,
                new IntSupplier() { public int getAsInt() { return s().endDelay; } },
                new IntConsumer() { public void accept(int v) { s().endDelay = v; } }, t, false);
    }

    private void border() {
        IntFunction<String> t = new IntFunction<String>() { public String apply(int v) { return Settings.time(v); } };
        IntFunction<String> b = new IntFunction<String>() { public String apply(int v) { return v + " Blöcke"; } };
        number(20, Material.IRON_FENCE, (short) 0, "Start-Durchmesser", 50, 250, 100, 10000,
                new IntSupplier() { public int getAsInt() { return s().borderInitial; } },
                new IntConsumer() { public void accept(int v) { s().borderInitial = v; plugin.game().applyWorldSettings(); } }, b, false);
        number(22, Material.NETHER_FENCE, (short) 0, "End-Durchmesser", 10, 50, 10, 10000,
                new IntSupplier() { public int getAsInt() { return s().borderFinal; } },
                new IntConsumer() { public void accept(int v) { s().borderFinal = v; } }, b, false);
        number(24, Material.COMPASS, (short) 0, "Schrumpfen ab", 30, 300, 0, 14400,
                new IntSupplier() { public int getAsInt() { return s().shrinkAfter; } },
                new IntConsumer() { public void accept(int v) { s().shrinkAfter = v; } }, t, false);
        number(31, Material.HOPPER, (short) 0, "Schrumpf-Dauer", 30, 300, 30, 14400,
                new IntSupplier() { public int getAsInt() { return s().shrinkDuration; } },
                new IntConsumer() { public void accept(int v) { s().shrinkDuration = v; } }, t, false);
    }

    private void arena() {
        final boolean ready = plugin.arena().isReady();
        set(20, new ItemBuilder(Material.BED).name("§e§lLobby hier setzen").lore(
                ready ? "§7Aktuell: §f" + loc(plugin.arena().getLobby()) : "§cnoch nicht gesetzt",
                "§7(neue Welt = Mitte wird mitgesetzt)", "", "§eKlick: §7deine Position").build(), new Action() {
            @Override
            public void run(Player p, ClickType c) {
                if (!lobbyOnly(p)) return;
                plugin.arena().setLobby(p.getLocation());
                plugin.game().applyWorldSettings();
                p.sendMessage(s().prefix + "§aLobby gesetzt.");
                refresh(p);
            }
        });
        set(22, new ItemBuilder(Material.BEACON).name("§e§lMitte hier setzen").lore(
                "§7Aktuell: §f" + (plugin.arena().getCenter() == null ? "-" : loc(plugin.arena().getCenter())),
                "§7Border & Spawnkreis drehen sich darum", "", "§eKlick: §7deine Position").build(), new Action() {
            @Override
            public void run(Player p, ClickType c) {
                if (!lobbyOnly(p)) return;
                try {
                    plugin.arena().setCenter(p.getLocation());
                    plugin.game().applyWorldSettings();
                    p.sendMessage(s().prefix + "§aMitte gesetzt.");
                } catch (IllegalArgumentException e) {
                    p.sendMessage(s().prefix + "§c" + e.getMessage());
                }
                refresh(p);
            }
        });
        cycleSchematic(24, "Lobby-Bau", s().lobbySchematic, new Consumer<String>() {
            public void accept(String v) { s().lobbySchematic = v; }
        }, "§eShift-Klick: §7hier einfügen §8(Mitte = du)");
        set(30, new ItemBuilder(Material.MAP).name("§b§lMap vorgenerieren").lore(
                "§7Lädt alle Chunks innerhalb der Border.", "§7Weniger Lag beim Start.",
                ChunkPregen.isRunning() ? "§eLäuft gerade – Klick: stoppen" : "§eKlick: §7starten").build(), new Action() {
            @Override
            public void run(Player p, ClickType c) {
                p.closeInventory();
                if (ChunkPregen.isRunning()) {
                    ChunkPregen.stop();
                    p.sendMessage(s().prefix + "§cVorgenerierung gestoppt.");
                    return;
                }
                World w = plugin.arena().getWorld();
                if (w == null) {
                    p.sendMessage(s().prefix + "§cZuerst Arena einrichten.");
                    return;
                }
                ChunkPregen.start(plugin, w, plugin.arena().getCenter().getBlockX(), plugin.arena().getCenter().getBlockZ(), s().borderInitial / 2 + 32, p);
            }
        });
        number(32, Material.FENCE, (short) 0, "Lobby-Schutzradius", 5, 25, 0, 200,
                new IntSupplier() { public int getAsInt() { return s().protectionRadius; } },
                new IntConsumer() { public void accept(int v) { s().protectionRadius = v; } },
                new IntFunction<String>() { public String apply(int v) { return v == 0 ? "aus" : v + " Blöcke"; } }, false);
        set(34, new ItemBuilder(Material.LEVER).name("§e§lBaumodus").lore("§7Creative an/aus (nur Lobby)", "§7Zum Bauen und Kit vorbereiten").build(), new Action() {
            @Override
            public void run(Player p, ClickType c) {
                if (!lobbyOnly(p)) return;
                p.closeInventory();
                p.setGameMode(p.getGameMode() == GameMode.CREATIVE ? GameMode.ADVENTURE : GameMode.CREATIVE);
                p.sendMessage(s().prefix + "§7Baumodus: " + (p.getGameMode() == GameMode.CREATIVE ? "§aan" : "§caus"));
            }
        });
    }

    private void spawns() {
        final boolean platform = s().spawnMode == Settings.SpawnMode.PLATFORM;
        set(19, new ItemBuilder(platform ? Material.SMOOTH_BRICK : Material.SAPLING).name("§d§lSpawn-Art: §f" + s().spawnMode.display).lore(
                "§8• §fPlattformen: §7Schematic pro Team im Kreis",
                "§8• §fZufällig: §7sichere Orte, Mindestabstand",
                "", "§eKlick: §7umschalten").build(), new Action() {
            @Override
            public void run(Player p, ClickType c) {
                s().spawnMode = platform ? Settings.SpawnMode.SCATTER : Settings.SpawnMode.PLATFORM;
                save(p);
            }
        });
        cycleSchematic(21, "Team-Plattform", s().teamSchematic, new Consumer<String>() {
            public void accept(String v) { s().teamSchematic = v; }
        }, null);
        number(23, Material.EYE_OF_ENDER, (short) 0, "Kreis-Radius", 10, 50, 0, 5000,
                new IntSupplier() { public int getAsInt() { return s().spawnRadius; } },
                new IntConsumer() { public void accept(int v) { s().spawnRadius = v; } },
                new IntFunction<String>() { public String apply(int v) { return v == 0 ? "Auto (" + plugin.spawns().autoRadius(previewCount()) + ")" : v + " Blöcke"; } }, false);
        number(25, Material.LADDER, (short) 0, "Höhe über Boden", 1, 5, -20, 50,
                new IntSupplier() { public int getAsInt() { return s().spawnYOffset; } },
                new IntConsumer() { public void accept(int v) { s().spawnYOffset = v; } }, null, false);
        toggle(28, Material.GLASS, "Luft mit einfügen",
                new BooleanSupplier() { public boolean getAsBoolean() { return s().pasteAir; } },
                new Consumer<Boolean>() { public void accept(Boolean b) { s().pasteAir = b; } }, "§7Räumt Bäume/Gras im Bereich weg");
        toggle(30, Material.REDSTONE_TORCH_ON, "Bei Start neu bauen",
                new BooleanSupplier() { public boolean getAsBoolean() { return s().regenerateOnStart; } },
                new Consumer<Boolean>() { public void accept(Boolean b) { s().regenerateOnStart = b; } }, "§7Passend zur Teamanzahl");
        number(32, Material.COMPASS, (short) 0, "Mindestabstand (Zufall)", 5, 25, 5, 500,
                new IntSupplier() { public int getAsInt() { return s().minTeamDistance; } },
                new IntConsumer() { public void accept(int v) { s().minTeamDistance = v; } },
                new IntFunction<String>() { public String apply(int v) { return v + " Blöcke"; } }, false);
        set(34, new ItemBuilder(Material.GOLD_AXE).name("§6§lWand holen").lore(
                "§71. Plattform bauen, Ecken markieren", "§72. auf den Spawnpunkt stellen", "§73. §e/odv schem save <Name>").build(), new Action() {
            @Override
            public void run(Player p, ClickType c) {
                p.closeInventory();
                p.getInventory().addItem(Wand.item());
            }
        });
        set(38, new ItemBuilder(Material.EMERALD_BLOCK).name("§a§lSpawns jetzt bauen").lore(
                "§7Anzahl: §f" + previewCount() + " §8(Teams bei aktuellem Stand)", "§7Alte Plattformen werden entfernt.", "", "§eKlick: §7bauen").build(), new Action() {
            @Override
            public void run(Player p, ClickType c) {
                if (!lobbyOnly(p)) return;
                p.closeInventory();
                plugin.spawns().generate(previewCount(), p, null);
            }
        });
        set(40, new ItemBuilder(Material.ENDER_PEARL).name("§d§lZu einem Spawn").lore("§7Gespeichert: §f" + plugin.arena().getSpawns().size(), "§eKlick: §7zufälliger Spawn").build(), new Action() {
            @Override
            public void run(Player p, ClickType c) {
                if (plugin.arena().getSpawns().isEmpty()) {
                    p.sendMessage(s().prefix + "§cNoch keine Spawns.");
                    return;
                }
                p.closeInventory();
                List<org.bukkit.Location> l = plugin.arena().getSpawns();
                p.teleport(l.get((int) (Math.random() * l.size())));
            }
        });
        set(42, new ItemBuilder(Material.REDSTONE_BLOCK).name("§c§lPlattformen entfernen").lore(
                plugin.spawns().hasUndo() ? "§7Rückgängig verfügbar" : "§8Nichts zum Entfernen").build(), new Action() {
            @Override
            public void run(Player p, ClickType c) {
                p.closeInventory();
                plugin.spawns().removePasted(p);
            }
        });
    }

    private void kit() {
        final Settings.Mode m = s().mode;
        set(20, new ItemBuilder(Material.CHEST).name("§a§lInventar als Kit speichern").lore(
                "§7Speichert dein Inventar + Rüstung", "§7als Kit für §f" + m.display + "§7.", "§8Tipp: vorher Baumodus (Creative)").build(), new Action() {
            @Override
            public void run(Player p, ClickType c) {
                try {
                    plugin.kits().save(p, m, GameManager.MENU_ITEMS);
                    p.sendMessage(s().prefix + "§aKit für " + m.display + " gespeichert.");
                } catch (IOException e) {
                    p.sendMessage(s().prefix + "§c" + e.getMessage());
                }
                refresh(p);
            }
        });
        set(22, new ItemBuilder(Material.DIAMOND_CHESTPLATE).name("§e§lKit ansehen").lore("§7Ersetzt dein Inventar mit dem Kit.").build(), new Action() {
            @Override
            public void run(Player p, ClickType c) {
                if (!lobbyOnly(p)) return;
                p.closeInventory();
                plugin.kits().give(p, m);
            }
        });
        set(24, new ItemBuilder(Material.LAVA_BUCKET).name("§c§lStandard-Kit verwenden").lore(
                "§7Status: " + (plugin.kits().hasCustom(m) ? "§feigenes Kit" : "§fStandard"), "§eKlick: §7eigenes Kit löschen").build(), new Action() {
            @Override
            public void run(Player p, ClickType c) {
                try {
                    plugin.kits().reset(m);
                    p.sendMessage(s().prefix + "§7Standard-Kit für " + m.display + " aktiv.");
                } catch (IOException e) {
                    p.sendMessage(s().prefix + "§c" + e.getMessage());
                }
                refresh(p);
            }
        });
    }

    private void rules() {
        toggle(20, Material.GOLDEN_APPLE, "Natürliche Regeneration",
                new BooleanSupplier() { public boolean getAsBoolean() { return s().naturalRegen; } },
                new Consumer<Boolean>() { public void accept(Boolean b) { s().naturalRegen = b; plugin.game().applyWorldSettings(); } }, "§7Aus = UHC-Regeln");
        toggle(22, Material.GOLD_INGOT, "Goldapfel bei Tod",
                new BooleanSupplier() { public boolean getAsBoolean() { return s().goldenApple; } },
                new Consumer<Boolean>() { public void accept(Boolean b) { s().goldenApple = b; } });
        toggle(24, Material.BLAZE_ROD, "Blitz bei Tod",
                new BooleanSupplier() { public boolean getAsBoolean() { return s().lightning; } },
                new Consumer<Boolean>() { public void accept(Boolean b) { s().lightning = b; } });
        toggle(30, Material.TNT, "Server-Stop nach Runde",
                new BooleanSupplier() { public boolean getAsBoolean() { return s().stopOnEnd; } },
                new Consumer<Boolean>() { public void accept(Boolean b) { s().stopOnEnd = b; } }, "§7Für frische Map per Restart-Script");
        toggle(32, Material.ENDER_CHEST, "Backup vor jeder Runde",
                new BooleanSupplier() { public boolean getAsBoolean() { return s().backupOnStart; } },
                new Consumer<Boolean>() { public void accept(Boolean b) { s().backupOnStart = b; } }, "§7Config, Arena, Kits, Statistik");
        set(34, new ItemBuilder(Material.PAPER).name("§b§lBackup jetzt").lore("§7ZIP in plugins/ODVMeetup/backups").build(), new Action() {
            @Override
            public void run(Player p, ClickType c) {
                plugin.stats().backup(p);
            }
        });
    }

    // ===================================================================== Bausteine

    private void set(int slot, ItemStack item, Action action) {
        inv.setItem(slot, item);
        actions.put(slot, action);
    }

    private void category(int slot, Material m, short data, String name, final Page target, String... lore) {
        List<String> l = new ArrayList<String>();
        for (String s : lore) l.add(s);
        l.add("");
        l.add("§eKlick: §7öffnen");
        set(slot, new ItemBuilder(m, 1, data).name(name).lore(l).build(), new Action() {
            @Override
            public void run(Player p, ClickType c) {
                open(plugin, p, target);
            }
        });
    }

    private void number(int slot, Material m, short data, String name, final int step, final int bigStep,
                        final int min, final int max, final IntSupplier get, final IntConsumer setter,
                        IntFunction<String> fmt, final boolean lobbyOnly) {
        int v = get.getAsInt();
        String shown = fmt == null ? String.valueOf(v) : fmt.apply(v);
        set(slot, new ItemBuilder(m, Math.max(1, Math.min(64, v)), data).name("§b§l" + name + ": §f" + shown).lore(
                "", "§8Links §a+" + step + "  §8Rechts §c-" + step, "§8Shift §7= ±" + bigStep).build(), new Action() {
            @Override
            public void run(Player p, ClickType c) {
                if (lobbyOnly && !lobbyOnly(p)) return;
                int d = c.isShiftClick() ? bigStep : step;
                int next = get.getAsInt() + (c.isLeftClick() ? d : -d);
                setter.accept(Math.max(min, Math.min(max, next)));
                save(p);
            }
        });
    }

    private void toggle(int slot, Material m, String name, final BooleanSupplier get, final Consumer<Boolean> setter, String... lore) {
        boolean on = get.getAsBoolean();
        List<String> l = new ArrayList<String>();
        for (String s : lore) l.add(s);
        l.add("");
        l.add("§eKlick: §7" + (on ? "ausschalten" : "einschalten"));
        set(slot, new ItemBuilder(m).name("§b§l" + name + ": " + (on ? "§aAn" : "§cAus")).lore(l).glow(on).build(), new Action() {
            @Override
            public void run(Player p, ClickType c) {
                setter.accept(!get.getAsBoolean());
                save(p);
            }
        });
    }

    private void cycleSchematic(int slot, final String what, final String current, final Consumer<String> setter, final String shiftHint) {
        final List<String> all = plugin.spawns().list();
        List<String> lore = new ArrayList<String>();
        for (String n : all) lore.add((n.equals(current) ? "§a» " : "§8• ") + "§7" + n);
        if (all.isEmpty()) lore.add("§8keine Dateien");
        lore.add("");
        lore.add("§eLinks/Rechts: §7nächste/vorherige");
        if (shiftHint != null) lore.add(shiftHint);
        lore.add("§8Ordner: plugins/ODVMeetup/schematics");
        set(slot, new ItemBuilder(Material.PAPER).name("§d§l" + what + ": §f" + current).lore(lore).build(), new Action() {
            @Override
            public void run(Player p, ClickType c) {
                if (c.isShiftClick() && shiftHint != null) {
                    p.closeInventory();
                    try {
                        Schematic sc = plugin.spawns().load(current);
                        plugin.spawns().pasteAt(sc, p.getLocation(), p);
                    } catch (IOException e) {
                        p.sendMessage(s().prefix + "§c" + e.getMessage());
                    }
                    return;
                }
                if (all.isEmpty()) return;
                int i = all.indexOf(current);
                int d = c.isLeftClick() ? 1 : -1;
                i = ((i + d) % all.size() + all.size()) % all.size();
                setter.accept(all.get(i));
                save(p);
            }
        });
    }

    private int previewCount() {
        if (s().isSolo()) return Math.max(2, plugin.game().candidates().size());
        int t = plugin.teams().countTeamsAfterFill(plugin.game().candidates().size());
        return Math.max(2, t);
    }

    private boolean lobbyOnly(Player p) {
        if (plugin.game().getState() == GameState.LOBBY && !plugin.spawns().isBusy()) return true;
        p.sendMessage(s().prefix + "§cGeht nur in der Lobby, wenn gerade nichts gebaut wird.");
        return false;
    }

    private void save(Player p) {
        s().validate();
        s().save();
        refresh(p);
    }

    private void refresh(Player p) {
        p.playSound(p.getLocation(), Sound.CLICK, 0.7f, 1.4f);
        render();
    }

    public void click(Player p, int slot, ClickType click) {
        Action a = actions.get(slot);
        if (a != null) a.run(p, click);
    }

    private static String ok(boolean b) {
        return b ? "§a✔" : "§c✘";
    }

    private static String loc(org.bukkit.Location l) {
        return l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ();
    }
}
