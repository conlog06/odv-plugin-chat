# ODVMeetup 2.0 – ODV & Meetup für 1.8.8 / 1.8.9

## ⬇️ Download
**[ODVMeetup-2.0.0.jar herunterladen](https://github.com/conlog06/odv-plugin-chat/raw/main/download/ODVMeetup-2.0.0.jar)**

Server: **Spigot/Paper 1.8.8** (Java 8+) · Spieler joinen mit **1.8.9** · keine weiteren Plugins nötig.

## In 3 Klicks spielbereit
1. JAR nach `plugins/`, Server starten, als OP joinen.
2. `/odv` → **① Schnellsetup** – neue Welt, Lobby-Bau in der Mitte, Border, Map-Vorgenerierung.
3. **② Modus** wählen (Meetup oder ODV) → **③ Runde starten**.

Danach startet die Runde automatisch, sobald genug Spieler da sind.

## Ablauf
Lobby → Countdown → **Spawns bauen** → Freeze → Schutzzeit → PvP → Border schrumpft → Sieg (Feuerwerk) → neue Lobby.

## Spawns
- **Plattformen** (Standard): pro Team eine Schematic im Kreis um die Mitte, auf Bodenhöhe, Blick zur Mitte. Alte Plattformen werden automatisch entfernt. Teammitglieder stehen nebeneinander.
- **Zufällig**: sichere Orte (kein Wasser/Lava/Baum), Mindestabstand zwischen Teams.

**Eigene Plattform:** `/odv wand` → Ecken markieren → auf den Spawnpunkt stellen → `/odv schem save meinspawn`.
Eigene Lobby genauso mit `/odv schem save meinelobby lobby`.
WorldEdit-6-Dateien (`.schematic`) einfach nach `plugins/ODVMeetup/schematics/` kopieren. `.schem` (WE 7) geht nicht.

## Presets
| | Meetup | ODV |
|---|---:|---:|
| Schutzzeit | 30 s | 10 min |
| Border Start → Ende | 600 → 40 | 1200 → 40 |
| Schrumpfen ab / Dauer | 5 / 10 min | 15 / 15 min |
| Standard-Kit | PvP-Ausrüstung | Steinwerkzeug + Essen |

## Features
Setup-Menü mit 7 Kategorien · bis 36 Teams (Teamchat mit `@`) · Solo-Modus · eigene Kits pro Modus ·
Scoreboard mit Timern & Top-Killern · Tablist · Actionbar mit Border-Abstand · Titel & Sounds ·
Reconnect-Frist (kein zweites Kit) · Zuschauer-Menü mit Köpfen · Mitspielen/Zuschauen per Item ·
Statistik (Siege, Kills, Tode, K/D, Top 10) · ZIP-Backups vor jeder Runde · Lobby-Schutz ·
Friendly Fire inkl. Pfeile, Tränke, TNT, Wölfe · Server-Stop nach Runde (für frische Map).

## Befehle
| Spieler | |
|---|---|
| `/team` · `/team <Nr>` · `/team random` · `/team leave` | Team wählen |
| `/odv join` · `/odv leave` | mitspielen / zuschauen |
| `/odv spec` | Spieler beobachten |
| `/odv stats [Name]` · `/odv top [kills]` | Statistik |

| Admin (`odv.admin`) | |
|---|---|
| `/odv` | Setup-Menü |
| `/odv quick` | Schnellsetup |
| `/odv start [Sek]` · `/odv stop` | Runde starten / stoppen |
| `/odv preset odv\|meetup` | Modus |
| `/odv setlobby` · `/odv setcenter` · `/odv build` | Arena von Hand / Baumodus |
| `/odv wand` · `/odv schem save <Name> [lobby]` | eigene Schematic |
| `/odv schem list\|use\|paste\|delete` | Schematics verwalten |
| `/odv spawns build\|tp\|add\|clear\|undo` | Spawns |
| `/odv kit save\|give\|reset [odv\|meetup]` | Kits |
| `/odv pregen` · `/odv backup` · `/odv reload` | Werkzeuge |

## Dateien (`plugins/ODVMeetup/`)
`config.yml` Regeln · `arena.yml` Welt/Lobby/Spawns · `kits.yml` · `stats.yml` · `schematics/` · `backups/`
(kompatibel zu ODVMeetup 1.0 – Arena, Kits und Statistik werden übernommen).

Hinweis: Nach einer Runde bleibt die Landschaft verändert. Für frisches Gelände Schnellsetup erneut nutzen
oder „Server-Stop nach Runde“ + Restart-Script mit Welt-Reset.

## Selbst bauen
`mvn package` (JDK 8+, Spigot-Repo erreichbar) → `target/ODVMeetup-2.0.0.jar`.
