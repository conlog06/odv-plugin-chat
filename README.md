# ODVMeetup 3.0 – ODV & Meetup für Paper 26.3

## ⬇️ Download
**[ODVMeetup-3.0.0.jar (Paper 26.3)](https://github.com/conlog06/odv-plugin-chat/releases/latest/download/ODVMeetup-3.0.0.jar)**
· alle Versionen unter [Releases](https://github.com/conlog06/odv-plugin-chat/releases)

Server: **Paper 26.3**, Java 21+ · keine weiteren Plugins nötig (MySQL-/SQLite-Treiber bringt Paper mit).
1.8.9-Clients: mit ViaVersion + ViaRewind möglich.
Ältere Version für Spigot 1.8.8: Branch [`legacy-1.8.8`](https://github.com/conlog06/odv-plugin-chat/tree/legacy-1.8.8)
([JAR](https://github.com/conlog06/odv-plugin-chat/raw/legacy-1.8.8/download/ODVMeetup-2.0.0.jar)).

## In 3 Klicks spielbereit
1. JAR nach `plugins/`, Server starten, als OP joinen.
2. `/odv` → **① Schnellsetup**: neue Welt mit mehr Diamanten, Spawn mit **Netherportal + Beacon** auf einer
   großen Plains-Fläche, **schwebende Lobby** darüber, **Practice-Arena** daneben, Map-Vorgenerierung.
3. **② Modus** (Meetup/ODV) → **③ Runde starten**.

## Spawns
- **Varo-Löcher** (Standard): im Kreis um den Spawn, jedes Loch mit Slab-Kreuz – **nur in Plains** und
  **nur auf flachem, natürlichem Boden** (kein Wasser, keine Bäume). Teamnamen schweben über den Spawns.
- **Plattformen**: eigene `.schem` (WorldEdit 7) oder eingebaute Plattform.
- **Zufällig**: sichere Orte mit Mindestabstand.

## Kampf
Umschaltbar **1.8.9-PvP** ↔ **26.3-PvP**: kein Angriffs-Cooldown, kein Sweep, **Schwert-Blocken** (Rechtsklick),
alte Notch-Äpfel, 1.8-Waffenschaden, Schilde an/aus. Kits nutzen 26.x-Items (Netherite, Speer, Wind Charges …).

## Szenarien
CutClean · BowLess · Triple Ores · Timebomb · Xtra Apples · Timber · RodLess · Backpack (`/backpack`)

## 10 neue Features
| Feature | Was passiert |
|---|---|
| Zuschauer-Voting | Tote stimmen alle 5 Min. per Klick über ein Event ab (Glow, Speed, Gewitter …) |
| Death-Recap | Nach dem Tod: wer dir in den letzten 30 s wie viel Schaden gemacht hat |
| Team-Revive | Der Kopf eines Gefallenen droppt – Teammitglied belebt ihn per Rechtsklick (2 Min.) |
| Supply-Drops | Loot-Kisten mit Lichtstrahl & Koordinaten in der Border |
| Team-Ping | Schleichen + F oder `/ping`: Markierung nur für dein Team |
| Kopfgeld | Der Top-Killer leuchtet, sein Bezwinger bekommt eine Belohnung |
| Adaptive Border | Weniger Lebende → Border schrumpft schneller |
| Kampf-Heatmap | Nach der Runde: Karte, wo gekämpft wurde |
| Showdown | Nach dem Zeitlimit: alle in die Mitte, alle leuchten |
| Zuschauer-Wetten | `/odv bet` – Sieger tippen, Wettpunkte sammeln |

Dazu: **Practice-Arena** in der Lobby (`/practice`), Bossbar mit Timern, Scoreboard wie Mega-ODV, Hologramme mit Top-5.

## Speicher & Anti-Xray
- Statistik **lokal (SQLite)** oder **MySQL** – `storage.type` in der config.yml.
- **Anti-Xray**: eigene Erkennung (meldet Admins auffälliges Abbauen, `/odv xray`) + Paper-Anti-Xray per Klick aktivierbar.
- Mehr Diamanten: `world.diamond-rate` (Standard 2,5×), gilt für neu generierte Welten.

## Befehle
| Spieler | |
|---|---|
| `/team` · `/practice` · `/szenarios` · `/backpack` · `/ping` | Lobby & Runde |
| `/odv join` · `/odv leave` · `/odv spec` · `/odv bet` | Teilnahme & Zuschauen |
| `/odv stats [Name]` · `/odv top [kills\|wetten\|practice]` | Statistik |

| Admin (`odv.admin`) | |
|---|---|
| `/odv` · `/odv quick` | Setup-Menü · Schnellsetup |
| `/odv start [Sek]` · `/odv stop` · `/odv host` · `/odv preset odv\|meetup` | Runde |
| `/odv setlobby\|setcenter\|setpractice\|build` | Arena von Hand |
| `/odv wand` · `/odv schem save\|list\|paste\|delete` | Schematics (`lobby` als Name = eigene Lobby) |
| `/odv spawns build\|tp\|clear\|undo` | Spawns |
| `/odv kit save\|give\|reset [meetup\|odv\|practice]` | Kits |
| `/odv xray [Name]` · `/odv antixray paper` · `/odv supply` · `/odv pregen\|backup\|reload` | Werkzeuge |

## Selbst bauen
`mvn package` (JDK 21+) → `target/ODVMeetup-3.0.0.jar`. Bei jedem Push baut GitHub Actions automatisch ein Release.
