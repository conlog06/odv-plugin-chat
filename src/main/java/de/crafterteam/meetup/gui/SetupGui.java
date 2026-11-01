package de.crafterteam.meetup.gui;

import de.crafterteam.meetup.MeetupPlugin;
import de.crafterteam.meetup.Settings;
import de.crafterteam.meetup.schematic.Wand;
import de.crafterteam.meetup.util.ChunkPregen;
import de.crafterteam.meetup.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Setup-Menü: alle wichtigen Einstellungen per Klick.
 * Linksklick = +, Rechtsklick = -, Shift = größere Schritte.
 */
public class SetupGui implements InventoryHolder {

    private static final String ADJ = "§8Links §a+  §8Rechts §c-  §8Shift §7= x5";

    private final MeetupPlugin plugin;
    private final Inventory inv;

    public SetupGui(MeetupPlugin plugin) {
        this.plugin = plugin;
        this.inv = Bukkit.createInventory(this, 54, "§b§lMeetup Setup");
        render();
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public void open(Player p) {
        p.openInventory(inv);
    }

    private Settings s() {
        return plugin.settings();
    }

    private static String ok(boolean b) {
        return b ? "§a✔" : "§c✘";
    }

    private static String onOff(boolean b) {
        return b ? "§aAn" : "§cAus";
    }

    public void render() {
        inv.clear();
        Settings s = s();
        ItemStack glass = new ItemBuilder(Material.STAINED_GLASS_PANE, 1, (short) 7).name(" ").build();
        for (int i = 0; i < 9; i++) inv.setItem(i, glass);
        for (int i = 45; i < 54; i++) inv.setItem(i, glass);

        // ---------- Checkliste ----------
        boolean lobby = s.lobby != null;
        boolean world = Bukkit.getWorld(s.worldName) != null;
        boolean schem = s.schematic != null && !s.schematic.isEmpty() && plugin.spawns().schematicFile(s.schematic).exists();
        inv.setItem(4, new ItemBuilder(Material.BOOK).name("§b§lCheckliste").lore(
                ok(lobby) + " §7Lobby gesetzt",
                ok(world) + " §7Spielwelt §f" + s.worldName,
                ok(schem) + " §7Spawn-Schematic §8(optional)",
                ok(s.kitContents != null) + " §7Eigenes Kit §8(optional, sonst Standard)",
                "§7Gespeicherte Spawns: §e" + plugin.spawns().getSpawns().size(),
                "",
                "§7Status: §e" + plugin.game().getState().name()).build());

        // ---------- Reihe 2: Lobby / Welt / Teams ----------
        inv.setItem(10, new ItemBuilder(Material.BED).name("§e§lLobby setzen").lore(
                "§7Aktuell: " + (lobby ? "§f" + s.lobby.getWorld().getName() + " " + s.lobby.getBlockX() + ", " + s.lobby.getBlockY() + ", " + s.lobby.getBlockZ() : "§cnicht gesetzt"),
                "", "§eKlick: §7deine Position").build());
        inv.setItem(11, new ItemBuilder(Material.GRASS).name("§a§lSpielwelt").lore(
                "§7Aktuell: §f" + s.worldName + " " + ok(world),
                "§7Mitte: §f" + s.centerX + ", " + s.centerZ,
                "", "§eLinks: §7deine aktuelle Welt nehmen",
                "§eRechts: §7Mitte = deine Position",
                "§eShift-Klick: §7neue Welt generieren").build());
        inv.setItem(12, new ItemBuilder(Material.SKULL_ITEM, s.teamSize, (short) 3).name("§b§lTeamgröße: §f" + s.teamSize)
                .lore(s.isSolo() ? "§7Modus: §fSolo" : "§7Modus: §fTeams", "", "§8Links §a+  §8Rechts §c-").build());
        inv.setItem(13, new ItemBuilder(Material.WOOL, Math.min(64, s.teamCount), (short) 14).name("§b§lTeamanzahl: §f" + s.teamCount)
                .lore("§7Nur bei Teamgröße > 1", "", "§8Links §a+  §8Rechts §c-").build());
        inv.setItem(14, new ItemBuilder(Material.EMERALD, Math.min(64, s.minPlayers), (short) 0).name("§a§lMin. Spieler: §f" + s.minPlayers)
                .lore("", ADJ).build());
        inv.setItem(15, new ItemBuilder(Material.WATCH).name("§e§lCountdown: §f" + s.lobbyCountdown + "s").lore("", ADJ).build());
        inv.setItem(16, new ItemBuilder(Material.ICE).name("§b§lFreeze-Zeit: §f" + s.freezeTime + "s")
                .lore("§7Spieler stehen eingefroren", "§7auf ihren Spawns", "", ADJ).build());

        // ---------- Reihe 3: Border / Kampf ----------
        inv.setItem(19, new ItemBuilder(Material.IRON_FENCE).name("§c§lBorder Start: §f" + s.borderStart)
                .lore("§7Durchmesser in Blöcken", "", "§8Links §a+50  §8Rechts §c-50  §8Shift §7x5").build());
        inv.setItem(20, new ItemBuilder(Material.NETHER_FENCE).name("§c§lBorder Ende: §f" + s.borderEnd)
                .lore("", "§8Links §a+10  §8Rechts §c-10  §8Shift §7x5").build());
        inv.setItem(21, new ItemBuilder(Material.COMPASS).name("§c§lShrink-Start: §f" + s.shrinkStart + " min")
                .lore("§7Wann die Border schrumpft", "", "§8Links §a+  §8Rechts §c-").build());
        inv.setItem(22, new ItemBuilder(Material.HOPPER).name("§c§lShrink-Dauer: §f" + s.shrinkDuration + " min")
                .lore("", "§8Links §a+  §8Rechts §c-").build());
        inv.setItem(23, new ItemBuilder(Material.IRON_CHESTPLATE).name("§6§lSchutzzeit: §f" + s.pvpProtection + "s")
                .lore("§7Kein PvP nach dem Freeze", "", "§8Links §a+10  §8Rechts §c-10").build());
        inv.setItem(24, new ItemBuilder(Material.CHEST).name("§6§lKit").lore(
                "§7Status: " + (s.kitContents != null ? "§aeigenes Kit" : "§eStandard-Kit"),
                "", "§eLinks: §7dein Inventar als Kit speichern",
                "§eRechts: §7Kit in dein Inventar laden",
                "§eShift-Rechts: §7auf Standard zurücksetzen").build());
        inv.setItem(25, new ItemBuilder(Material.GOLDEN_APPLE).name("§6§lNatürliche Regeneration: " + onOff(s.naturalRegen))
                .lore("§7Aus = UHC-Regeln", "", "§eKlick: §7umschalten").build());

        // ---------- Reihe 4: Spawns ----------
        List<String> schemLore = new ArrayList<String>();
        schemLore.add("§7Aktuell: " + (s.schematic == null || s.schematic.isEmpty() ? "§ckeine §8(nur Teleport)" : "§f" + s.schematic + " " + ok(schem)));
        schemLore.add("");
        List<String> all = plugin.spawns().listSchematics();
        if (all.isEmpty()) {
            schemLore.add("§8Keine Dateien in plugins/CrafterMeetup/schematics");
        } else {
            for (String n : all) schemLore.add((n.equals(s.schematic) ? "§a» " : "§8• ") + "§7" + n);
        }
        schemLore.add("");
        schemLore.add("§eLinks: §7nächste  §eRechts: §7vorherige");
        schemLore.add("§eShift-Klick: §7keine Schematic");
        inv.setItem(28, new ItemBuilder(Material.PAPER).name("§d§lSpawn-Schematic").lore(schemLore).glow(schem).build());
        inv.setItem(29, new ItemBuilder(Material.GOLD_AXE).name("§6§lWand holen").lore(
                "§7Plattform markieren, hinstellen,",
                "§e/meetup schem save <Name>").build());
        inv.setItem(30, new ItemBuilder(Material.ARROW).name("§d§lSpawn-Anzahl: §f" + (s.spawnCount <= 0 ? "Auto" : s.spawnCount))
                .lore("§7Auto = Anzahl Teams beim Start", "§7(Setup-Vorschau: §f" + plugin.spawns().autoCount() + "§7)", "", "§8Links §a+  §8Rechts §c-  §8(0 = Auto)").build());
        inv.setItem(31, new ItemBuilder(Material.EYE_OF_ENDER).name("§d§lSpawn-Radius: §f" + (s.spawnRadius <= 0 ? "Auto" : s.spawnRadius))
                .lore("§7Auto = §f" + plugin.spawns().autoRadius(plugin.spawns().autoCount()) + " §7Blöcke", "", "§8Links §a+10  §8Rechts §c-10  §8(0 = Auto)").build());
        inv.setItem(32, new ItemBuilder(Material.LADDER).name("§d§lHöhen-Offset: §f" + s.spawnYOffset)
                .lore("§7Abstand Spawnpunkt zum Boden", "", "§8Links §a+  §8Rechts §c-").build());
        inv.setItem(33, new ItemBuilder(Material.GLASS).name("§d§lLuft einfügen: " + onOff(s.pasteAir))
                .lore("§7An = Bäume/Gras im Bereich", "§7der Schematic werden entfernt", "", "§eKlick: §7umschalten").build());
        inv.setItem(34, new ItemBuilder(Material.REDSTONE_TORCH_ON).name("§d§lBeim Start neu generieren: " + onOff(s.generateOnStart))
                .lore("§7Erzeugt passend zur Teamanzahl", "§7frische Spawns beim Rundenstart", "", "§eKlick: §7umschalten").build());

        // ---------- Reihe 5: Aktionen / Optionen ----------
        inv.setItem(37, new ItemBuilder(Material.EMERALD_BLOCK).name("§a§lSpawns jetzt generieren").lore(
                "§7Anzahl: §f" + plugin.spawns().autoCount() + "  §7Radius: §f" + (s.spawnRadius > 0 ? s.spawnRadius : plugin.spawns().autoRadius(plugin.spawns().autoCount())),
                "§7Alte Plattformen werden entfernt", "", "§eKlick: §7generieren").build());
        inv.setItem(38, new ItemBuilder(Material.REDSTONE_BLOCK).name("§c§lGesetzte Plattformen entfernen")
                .lore(plugin.spawns().hasUndo() ? "§7Undo verfügbar" : "§8Nichts zum Entfernen", "", "§eKlick: §7entfernen").build());
        inv.setItem(39, new ItemBuilder(Material.ENDER_PEARL).name("§d§lZu Spawn #1 teleportieren")
                .lore("§7Zum Prüfen der Plattform").build());
        inv.setItem(40, new ItemBuilder(Material.MAP).name("§b§lMap vorgenerieren").lore(
                "§7Lädt alle Chunks innerhalb der", "§7Border vor (weniger Lag beim Start)",
                ChunkPregen.isRunning() ? "§eLäuft gerade..." : "", "§eKlick: §7starten / stoppen").build());
        inv.setItem(41, new ItemBuilder(Material.DIAMOND_SWORD).name("§c§lFriendly Fire: " + onOff(s.friendlyFire))
                .lore("", "§eKlick: §7umschalten").build());
        inv.setItem(42, new ItemBuilder(Material.GOLD_INGOT).name("§6§lGoldapfel bei Tod: " + onOff(s.goldenAppleOnDeath))
                .lore("", "§eKlick: §7umschalten").build());
        inv.setItem(43, new ItemBuilder(Material.TNT).name("§c§lServer-Stop nach Runde: " + onOff(s.restartOnEnd))
                .lore("§7An = Server stoppt nach dem Sieg", "§7(für frische Map via Restart-Script)", "", "§eKlick: §7umschalten").build());

        // ---------- Unterste Reihe ----------
        inv.setItem(48, new ItemBuilder(Material.BOOK_AND_QUILL).name("§7§lConfig neu laden").build());
        inv.setItem(49, new ItemBuilder(Material.WOOL, 1, (short) 5).name("§a§lRunde starten").lore("§7Startet einen 10s-Countdown").build());
        inv.setItem(50, new ItemBuilder(Material.WOOL, 1, (short) 14).name("§c§lRunde stoppen").build());
    }

    public void click(final Player p, int slot, ClickType type) {
        Settings s = s();
        boolean left = type.isLeftClick();
        boolean shift = type.isShiftClick();
        int dir = left ? 1 : -1;
        int mul = shift ? 5 : 1;
        boolean close = false;

        switch (slot) {
            case 10:
                s.lobby = p.getLocation().clone();
                p.sendMessage(s.prefix + "§aLobby gesetzt.");
                break;
            case 11:
                if (shift) {
                    p.closeInventory();
                    String name = "meetup_" + (System.currentTimeMillis() / 1000L % 1000000L);
                    p.sendMessage(s.prefix + "§7Generiere neue Welt §e" + name + "§7... (kann kurz laggen)");
                    World w = Bukkit.createWorld(new WorldCreator(name));
                    if (w != null) {
                        String old = s.worldName;
                        s.worldName = name;
                        s.save();
                        w.getWorldBorder().setCenter(s.centerX + 0.5, s.centerZ + 0.5);
                        w.getWorldBorder().setSize(Math.max(10, s.borderStart));
                        p.sendMessage(s.prefix + "§aNeue Spielwelt §e" + name + " §aaktiv §7(vorher: " + old + ").");
                        p.sendMessage(s.prefix + "§7Tipp: §eMap vorgenerieren §7im Setup drücken.");
                    } else {
                        p.sendMessage(s.prefix + "§cWelt konnte nicht erstellt werden.");
                    }
                    return;
                } else if (left) {
                    s.worldName = p.getWorld().getName();
                    p.sendMessage(s.prefix + "§aSpielwelt: §e" + s.worldName);
                } else {
                    s.centerX = p.getLocation().getBlockX();
                    s.centerZ = p.getLocation().getBlockZ();
                    p.sendMessage(s.prefix + "§aMittelpunkt: §e" + s.centerX + ", " + s.centerZ);
                }
                break;
            case 12:
                if (!plugin.game().getState().isLobby()) break;
                s.teamSize = Settings.clamp(s.teamSize + dir, 1, 8);
                s.save();
                plugin.teams().reset();
                break;
            case 13:
                if (!plugin.game().getState().isLobby()) break;
                s.teamCount = Settings.clamp(s.teamCount + dir, 2, 16);
                s.save();
                plugin.teams().reset();
                break;
            case 14: s.minPlayers = Settings.clamp(s.minPlayers + dir * mul, 1, 200); break;
            case 15: s.lobbyCountdown = Settings.clamp(s.lobbyCountdown + dir * 5 * mul, 5, 600); break;
            case 16: s.freezeTime = Settings.clamp(s.freezeTime + dir * mul, 0, 120); break;
            case 19: s.borderStart = Settings.clamp(s.borderStart + dir * 50 * mul, 50, 10000); break;
            case 20: s.borderEnd = Settings.clamp(s.borderEnd + dir * 10 * mul, 10, s.borderStart); break;
            case 21: s.shrinkStart = Settings.clamp(s.shrinkStart + dir * mul, 0, 120); break;
            case 22: s.shrinkDuration = Settings.clamp(s.shrinkDuration + dir * mul, 1, 120); break;
            case 23: s.pvpProtection = Settings.clamp(s.pvpProtection + dir * 10 * mul, 0, 1200); break;
            case 24:
                if (shift && !left) {
                    s.kitContents = null;
                    s.kitArmor = null;
                    plugin.getConfig().set("kit", null);
                    plugin.getConfig().set("kit.saved", false);
                    p.sendMessage(s.prefix + "§eKit auf Standard zurückgesetzt.");
                } else if (left) {
                    saveKit(p);
                } else {
                    p.closeInventory();
                    plugin.game().giveKit(p);
                    p.sendMessage(s.prefix + "§7Kit geladen. Ändern und mit Linksklick wieder speichern.");
                    return;
                }
                break;
            case 25: s.naturalRegen = !s.naturalRegen; break;
            case 28: {
                List<String> all = plugin.spawns().listSchematics();
                if (shift) {
                    s.schematic = "";
                } else if (!all.isEmpty()) {
                    int idx = all.indexOf(s.schematic);
                    idx = ((idx + dir) % all.size() + all.size()) % all.size();
                    s.schematic = all.get(idx);
                }
                break;
            }
            case 29:
                p.getInventory().addItem(Wand.item());
                close = true;
                break;
            case 30: s.spawnCount = Settings.clamp(s.spawnCount + dir * mul, 0, 200); break;
            case 31: s.spawnRadius = Settings.clamp(s.spawnRadius + dir * 10 * mul, 0, 5000); break;
            case 32: s.spawnYOffset = Settings.clamp(s.spawnYOffset + dir, -20, 50); break;
            case 33: s.pasteAir = !s.pasteAir; break;
            case 34: s.generateOnStart = !s.generateOnStart; break;
            case 37:
                s.save();
                p.closeInventory();
                plugin.spawns().generate(plugin.spawns().autoCount(), s.spawnRadius, p, null);
                return;
            case 38:
                p.closeInventory();
                plugin.spawns().removePasted(p);
                return;
            case 39:
                if (plugin.spawns().getSpawns().isEmpty()) {
                    p.sendMessage(s.prefix + "§cKeine Spawns vorhanden.");
                } else {
                    p.closeInventory();
                    p.teleport(plugin.spawns().getSpawns().get(0));
                    return;
                }
                break;
            case 40:
                p.closeInventory();
                if (ChunkPregen.isRunning()) {
                    ChunkPregen.stop();
                    p.sendMessage(s.prefix + "§cVorgenerierung gestoppt.");
                } else {
                    World w = s.getGameWorld();
                    if (w == null) {
                        p.sendMessage(s.prefix + "§cSpielwelt nicht gefunden.");
                    } else {
                        ChunkPregen.start(plugin, w, s.centerX, s.centerZ, s.borderStart / 2 + 32, p);
                    }
                }
                return;
            case 41: s.friendlyFire = !s.friendlyFire; break;
            case 42: s.goldenAppleOnDeath = !s.goldenAppleOnDeath; break;
            case 43: s.restartOnEnd = !s.restartOnEnd; break;
            case 48:
                plugin.reloadAll();
                p.sendMessage(s.prefix + "§aConfig neu geladen.");
                break;
            case 49:
                p.closeInventory();
                if (plugin.game().forceStart(10)) {
                    plugin.game().broadcast("§aDie Runde wurde von §e" + p.getName() + " §agestartet.");
                } else {
                    p.sendMessage(s.prefix + "§cEs läuft bereits eine Runde.");
                }
                return;
            case 50:
                p.closeInventory();
                plugin.game().stop();
                return;
            default:
                return;
        }
        s.save();
        p.playSound(p.getLocation(), Sound.CLICK, 1f, 1.2f);
        if (close) {
            p.closeInventory();
        } else {
            render();
        }
    }

    private void saveKit(Player p) {
        Settings s = s();
        ItemStack[] c = p.getInventory().getContents();
        ItemStack[] a = p.getInventory().getArmorContents();
        s.kitContents = new ItemStack[c.length];
        s.kitArmor = new ItemStack[4];
        for (int i = 0; i < c.length; i++) s.kitContents[i] = c[i] == null || isLobbyItem(c[i]) ? null : c[i].clone();
        for (int i = 0; i < Math.min(4, a.length); i++) s.kitArmor[i] = a[i] == null ? null : a[i].clone();
        p.sendMessage(s.prefix + "§aKit aus deinem Inventar gespeichert.");
    }

    private static boolean isLobbyItem(ItemStack it) {
        if (!it.hasItemMeta() || !it.getItemMeta().hasDisplayName()) return false;
        String n = it.getItemMeta().getDisplayName();
        return n.equals(de.crafterteam.meetup.game.GameManager.ITEM_TEAMS) || n.equals(de.crafterteam.meetup.game.GameManager.ITEM_SETUP)
                || n.equals(de.crafterteam.meetup.game.GameManager.ITEM_START) || n.equals(Wand.NAME);
    }
}
