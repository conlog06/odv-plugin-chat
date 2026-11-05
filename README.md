# ODVMeetup 3.4 – ODV & Meetup für Paper 26.3

## ⬇️ Download
**[ODVMeetup-3.4.0.jar (Paper 26.3)](https://github.com/conlog06/odv-plugin-chat/releases/latest/download/ODVMeetup-3.4.0.jar)**
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
- **Mittel-Spawn**: sucht über das Biom-Rauschen eine **große Plains-Ebene** (rundherum Plains) und nimmt den
  flachsten Punkt – keine Hügel, Berge, Bäume oder Wasser.
- **Varo-Löcher** (Standard): im Kreis um den Spawn, jedes Loch mit Slab-Kreuz – **nur in Plains** und
  **nur auf flachem, natürlichem Boden** (max. 1 Block Höhenunterschied, keine Bäume darüber, kein Wasser). Teamnamen schweben über den Spawns.
- **Plattformen**: eigene `.schem` (WorldEdit 7) oder eingebaute Plattform.
- **Zufällig**: sichere Orte mit Mindestabstand.

## Teams & Hoster
- **Smartes Auffüllen**: Wer kein Team wählt, kommt immer ins Team mit den wenigsten Spielern; es werden so viele
  Teams aufgemacht wie nötig (mindestens 2). Sind trotzdem alle im selben Team, wird es automatisch geteilt.
- **/mod**: Hoster und Team schalten den Mod-Modus an – sie spielen nicht mit, sind Zuschauer und stehen mit **MOD** im Tab.
- **Host-Menü**: Runde starten/stoppen, Modus, Teams, PvP, Szenarien, Features, Abstimmung, **Map erstellen** und Mod-Modus.

## Host-Ablauf (wie Mega-Events)
**Vorbereitung:** `/host` (öffnet das Host-Menü, man spielt automatisch nicht mit) → Teams, Szenarien, Features, PvP
einstellen → `/createworld` (Plains-Spawn mit Portal + Spawnlöchern) → `/worlds` zum Nachschauen (Nether-Portal checken)
→ `/announce 20:00 [Text]`.
**Runde:** `/start 60` · `/border 300 [Sekunden]` · `/finale 30` (ab 4–6 Spielern).
**Specen & Support:** `/gm 3` · `/tp` · `/freeze <Spieler>` (Ausloggen = Ban) · `/latejoin <Spieler> [Team]` ·
`/setkit <Spieler>` · `/teamchange <Spieler> <Team>` (bis Ende der Schutzzeit) · `/remove <Spieler>` ·
`/revive <Spieler>` (nur mit Clip – gibt das Inventar vom Tod zurück) · `/mod <Spieler>` · `/invsee` · `/v` · `/fly`.
Alles geht auch als `/meetup <befehl>` (`reset`, `setlobbyspawn`, `border`, `add`, `setstarterkit`, `team`, `remove`, `revive`, `freeze`).

**Ban-System:** `/ban` zeigt die Ban-IDs, `/ban <Spieler> <ID>` bannt/mutet passend (IDs in `punishments.ids`),
`/check <Spieler>`, `/history <Spieler>`.
**Reports:** `/report <Spieler> <Grund>` · Team: `/reports` (mit [TP] und [✔]), `/report login|logout` (immer ausloggen, wenn du selbst mitspielst).

## Neue Welt nach jeder Runde
`world.reset-after-round: LIVE` (Standard): Nach jeder Runde – auch nach `/odv stop` – wird die Arena gelöscht und mit
neuem Seed neu erstellt (Spawn, Lobby, Practice, Podest). Spieler warten solange in der Hauptwelt.
`RESTART` = erst beim nächsten Serverstart, `OFF` = nie. Von Hand: `/odv resetworld`.
Es werden nur Welten gelöscht, die das Plugin selbst angelegt hat (`odv_arena…`).

## Combat-Log, Strikes & Bans
- Treffer zwischen zwei Gegnern = beide **im Kampf** (15 s, Anzeige in der Actionbar).
- **Ausloggen im Kampf** = sofort raus, Gegner bekommt den Kill, **Strike**.
- **Runde verlassen** und nicht rechtzeitig zurück = **Strike**.
- Strikes verfallen nach 30 Tagen; **Auto-Ban** ab 3 Strikes (1h → 1d → 7d → perm), alles in `punishments` einstellbar.
- Gespeichert in SQLite oder MySQL, Ban-Bildschirm beim Login.

```
/kick <Spieler> [Grund]              /strike <Spieler> <Grund>
/tempban <Spieler> 7d [Grund]        /strikes [Spieler]
/ban <Spieler> [Grund]               /unstrike <Spieler> <Nr|all>
/unban <Spieler>   /banlist
```
Rechte: `odv.kick`, `odv.strike`, `odv.tempban` (alle in `odv.mute` enthalten → Moderatoren), `odv.ban` (in `odv.admin`).

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
| Kampf-Heatmap | Nach der Runde: Geländekarte mit Kampf-Hitze, Kills und Hotspots (auch als PNG in `plugins/ODVMeetup/heatmaps`) |
| Showdown | Nach dem Zeitlimit: alle in die Mitte, alle leuchten |
| Zuschauer-Wetten | `/odv bet` – Sieger tippen, Wettpunkte sammeln |

Dazu: **Practice-Arena** in der Lobby (`/practice`), Bossbar mit Timern, Scoreboard wie Mega-ODV, Hologramme mit Top-5.

## Szenarien, Features & Abstimmung
Im Menü (Leitung- oder Host-Item → Szenarien/Features) steht jedes Szenario und Feature auf **An**, **Aus** oder
**Abstimmung**. Steht etwas auf Abstimmung, bekommen Spieler in der Lobby ein Abstimmungs-Item; die meistgewählten
(Anzahl einstellbar) sind beim Start aktiv.

## Das 26.3-Konzept
- **Netherite-Meteor**: wird 60 s vorher mit Koordinaten angekündigt, schlägt sichtbar ein, Krater mit Ancient Debris + Meteorkern.
- **End-Finale**: statt Showdown kämpfen die Letzten auf einer schwebenden End-Insel – runterfallen = raus.
- **Zeitlupen-Finale**: beim entscheidenden Kill läuft der ganze Server 3 Sekunden in Zeitlupe.
- **Siegerpodest**: die Sieger stehen danach als Mannequins (mit Skin & Rüstung) in der Lobby.

## Rangsystem
Eigene Ränge ohne LuckPerms: Prefix mit **Farbverlauf** in **Chat, Tab und über dem Kopf**, Tab nach Rang sortiert, Rechte pro Rang.
**Owner, Leitung und Admin sind geschützt**: am Anfang nur über die Konsole vergebbar, danach nur von jemandem, der selbst so einen Rang hat.
Vorgegeben (aus eurer Teamliste): Owner, Administrator, SrDeveloper, Developer, SrModerator, Moderator, Builder,
Content, Premium, Spieler – alles in `plugins/ODVMeetup/ranks.yml` anpassbar. Gespeichert in SQLite oder MySQL.

```
/rank set Joniwoch owner      (geht auch in der Konsole)
/teamliste                    Teamliste wie im Discord, automatisch aus den Rängen
#Nachricht  oder  /sc         Staff-Chat
/mute <Spieler> [Min]         Chat-Slowmode & Spam-Schutz sind eingebaut
```

## Speicher & Anti-Xray
- Statistik **lokal (SQLite)** oder **MySQL** – `storage.type` in der config.yml.
- **Anti-Xray**: eigene Erkennung (meldet Admins auffälliges Abbauen, `/odv xray`) + Paper-Anti-Xray per Klick aktivierbar.
- Mehr Diamanten: `world.diamond-rate` (Standard 2,5×), gilt für neu generierte Welten.

## Befehle
| Spieler | |
|---|---|
| `/mod` | Hoster/Team: Runde moderieren statt mitspielen |
| `/team` · `/practice` · `/szenarios` · `/backpack` · `/ping` · `/coords` | Lobby & Runde |
| `/lb` · `/teamliste` · `/changelog` · `/rank info` | Leaderboard, Team, Neuerungen |
| `/odv join` · `/odv leave` · `/odv spec` · `/odv bet` | Teilnahme & Zuschauen |
| `/odv stats [Name]` · `/odv top [kills\|wetten\|practice]` | Statistik |

| Admin (`odv.admin`) | |
|---|---|
| `/odv` · `/odv quick` · `/odv resetworld` | Setup-Menü · Schnellsetup · Welt neu erstellen |
| `/odv start [Sek]` · `/odv stop` · `/odv host` · `/odv preset odv\|meetup` | Runde |
| `/odv setlobby\|setcenter\|setpractice\|build` | Arena von Hand |
| `/odv wand` · `/odv schem save\|list\|paste\|delete` | Schematics (`lobby` als Name = eigene Lobby) |
| `/odv spawns build\|tp\|clear\|undo` | Spawns |
| `/odv kit save\|give\|reset [meetup\|odv\|practice]` | Kits |
| `/odv xray [Name]` · `/odv antixray paper` · `/odv supply` · `/odv pregen\|backup\|reload` | Werkzeuge |
| `/rank set\|remove\|list\|reload` · `/sc` · `/mute` · `/unmute` | Ränge & Moderation |
| `/kick` · `/ban` · `/tempban` · `/unban` · `/banlist` · `/strike` · `/strikes` · `/unstrike` | Strafen |
| `/invsee` · `/ecsee` · `/armorsee` · `/gm 0-3` · `/give` · `/i` · `/tp` · `/tphere` · `/heal` · `/feed` · `/fly` · `/clear` · `/vanish` · `/speed` | Admin (`odv.cmd.<befehl>`) |
| `/odv announce <Text>` · `/odv changelog post` | Chat + Discord-Webhook |

## Selbst bauen
`mvn package` (JDK 21+) → `target/ODVMeetup-3.4.0.jar`. Bei jedem Push baut GitHub Actions automatisch ein Release.
