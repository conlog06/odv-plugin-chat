# CrafterMeetup – ODV/Meetup für 1.8.8 / 1.8.9

## ⬇️ Download
**[CrafterMeetup-1.0.0.jar herunterladen](https://github.com/conlog06/odv-plugin-chat/raw/main/download/CrafterMeetup-1.0.0.jar)**
(oder unter *Releases*)

Server: Spigot/Paper **1.8.8** (Java 8+). Spieler joinen mit dem **1.8.9**-Client.

## Installation
1. `CrafterMeetup-1.0.0.jar` in `plugins/` legen, Server starten.
2. Ingame `/meetup setup` (oder Lobby-Item) – alles per Klick.

## Spawn-Schematic in 30 Sekunden
1. Plattform bauen (z. B. 5x5 Stein-Plattform mit Glaswänden).
2. `/meetup wand` → Linksklick Ecke 1, Rechtsklick Ecke 2.
3. **Auf die Stelle stellen, wo der Spieler spawnen soll.**
4. `/meetup schem save spawn1` → wird automatisch als Spawn-Schematic gesetzt.
5. Setup → **Spawns jetzt generieren** (oder automatisch beim Rundenstart).

Alternativ: WorldEdit-6-Schematics (`.schematic`, MCEdit-Format) in
`plugins/CrafterMeetup/schematics/` kopieren. Der WorldEdit-Kopierpunkt (`//copy`-Position) = Spawnpunkt.
Neue `.schem`-Dateien (WorldEdit 7) werden **nicht** unterstützt.

Spawns werden gleichmäßig im Kreis um die Mitte (Standard 0/0) verteilt, auf Bodenhöhe
(Bäume/Gras werden ignoriert), Blick zur Mitte. Beim nächsten Generieren werden die alten
Plattformen automatisch entfernt.

## Ablauf
Lobby → Countdown → Spawns/Teleport → Freeze → Schutzzeit → PvP → Border schrumpft → Sieg.
Tote/Nachzügler werden Zuschauer. Teamchat: Nachricht mit `@` beginnen.

## Befehle (Permission `meetup.admin`)
| Befehl | Funktion |
|---|---|
| `/meetup setup` | Setup-Menü |
| `/meetup start [Sek]` / `stop` | Runde starten/stoppen |
| `/meetup setlobby` / `setworld [Welt]` / `setcenter` | Orte setzen |
| `/meetup wand` | Auswahl-Werkzeug |
| `/meetup schem save/load/paste/list/delete <Name>` | Schematics |
| `/meetup spawns generate [Anzahl/auto] [Radius/auto]` | Spawns generieren |
| `/meetup spawns list/tp <Nr>/add/clear/undo` | Spawns verwalten |
| `/meetup pregen` | Map innerhalb der Border vorgenerieren |
| `/teams` | Teamauswahl (alle Spieler) |

## Selbst bauen
`mvn package` (Java 8+, Spigot-Repo erreichbar) → `target/CrafterMeetup-1.0.0.jar`.
