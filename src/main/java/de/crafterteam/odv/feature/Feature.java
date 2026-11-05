package de.crafterteam.odv.feature;

import org.bukkit.Material;

/** Die 10 neuen Features (+ Practice). Alle einzeln schaltbar. */
public enum Feature {
    SPECTATOR_VOTE("Zuschauer-Voting", Material.SPYGLASS, "Tote & Zuschauer stimmen alle 5 Min. über ein Event ab."),
    DEATH_RECAP("Death-Recap", Material.WRITABLE_BOOK, "Nach dem Tod: wer dir wie viel Schaden gemacht hat."),
    REVIVE("Team-Revive", Material.PLAYER_HEAD, "Teammitglieder bringen Gefallene mit ihrem Kopf zurück (1x pro Runde)."),
    SUPPLY_DROPS("Supply-Drops", Material.CHEST, "Regelmäßig Loot-Kisten mit Lichtstrahl in der Border."),
    TEAM_PING("Team-Ping", Material.BELL, "Schleichen + F oder /ping markiert einen Ort für dein Team."),
    BOUNTY("Kopfgeld", Material.GOLD_INGOT, "Der Top-Killer leuchtet – wer ihn tötet, kassiert eine Belohnung."),
    ADAPTIVE_BORDER("Adaptive Border", Material.COMPASS, "Die Border schrumpft schneller, je weniger Spieler leben."),
    HEATMAP("Kampf-Heatmap", Material.FILLED_MAP, "Nach der Runde: Karte, wo gekämpft wurde."),
    SHOWDOWN("Showdown", Material.NETHERITE_SWORD, "Nach dem Zeitlimit: alle in die Mitte, letzte Border."),
    BETTING("Zuschauer-Wetten", Material.EMERALD, "Zuschauer tippen den Sieger und sammeln Wettpunkte."),
    PRACTICE("Practice-Arena", Material.IRON_SWORD, "In der Lobby kämpfen, bis die Runde startet: /practice"),
    METEOR("Netherite-Meteor", Material.ANCIENT_DEBRIS, "Mitten in der Runde schlägt ein Meteor ein – voller Ancient Debris."),
    END_FINALE("End-Finale", Material.END_PORTAL_FRAME, "Statt Showdown: Das Finale findet auf einer schwebenden End-Insel statt."),
    SLOWMO("Zeitlupen-Finale", Material.CLOCK, "Beim entscheidenden Kill läuft der ganze Server kurz in Zeitlupe."),
    PODIUM("Siegerpodest", Material.ARMOR_STAND, "Die Sieger stehen danach als lebensechte Figuren in der Lobby.");

    public final String display;
    public final Material icon;
    public final String description;

    Feature(String display, Material icon, String description) {
        this.display = display;
        this.icon = icon;
        this.description = description;
    }

    /** Kann darüber abgestimmt werden? (Practice betrifft nur die Lobby) */
    public boolean votable() {
        return this != PRACTICE && this != PODIUM;
    }

    public String key() {
        return name().toLowerCase().replace('_', '-');
    }
}
