package de.crafterteam.odv.scenario;

import org.bukkit.Material;

public enum Scenario {
    CUTCLEAN("CutClean", Material.IRON_INGOT, "Erze & Fleisch droppen direkt geschmolzen/gebraten."),
    BOWLESS("BowLess", Material.BOW, "Keine Bögen & Armbrüste."),
    TRIPLE_ORES("Triple Ores", Material.DIAMOND, "Erze droppen dreifach."),
    TIMEBOMB("Timebomb", Material.TNT, "Items toter Spieler liegen in einer Kiste, die nach kurzer Zeit explodiert."),
    XTRA_APPLES("Xtra Apples", Material.APPLE, "Blätter droppen deutlich öfter Äpfel."),
    TIMBER("Timber", Material.OAK_LOG, "Ein Stamm abbauen fällt den ganzen Baum."),
    RODLESS("RodLess", Material.FISHING_ROD, "Keine Angeln."),
    BACKPACK("Backpack", Material.BUNDLE, "Jedes Team hat einen Rucksack: /backpack"),
    DIAMONDLESS("Diamondless", Material.DIAMOND_ORE, "Diamanterz droppt nichts – Spieler droppen beim Tod 1 Diamant."),
    GOLDLESS("Goldless", Material.GOLD_ORE, "Golderz droppt nichts – Spieler droppen beim Tod 4 Goldbarren."),
    NETHERITELESS("Netheriteless", Material.NETHERITE_INGOT, "Kein Netherite: Ancient Debris droppt nichts, kein Netherite-Upgrade."),
    SHIELDLESS("Shieldless", Material.SHIELD, "Keine Schilde."),
    MACELESS("Maceless", Material.MACE, "Kein Streitkolben (Mace)."),
    ABSORPTIONLESS("Absorptionless", Material.GOLDEN_APPLE, "Goldäpfel geben keine Absorption-Herzen."),
    FIRELESS("Fireless", Material.LAVA_BUCKET, "Kein Feuer- und Lavaschaden."),
    HASTEY_BOYS("Hastey Boys", Material.GOLDEN_PICKAXE, "Gecraftete Werkzeuge haben Effizienz III + Haltbarkeit III.");

    public final String display;
    public final Material icon;
    public final String description;

    Scenario(String display, Material icon, String description) {
        this.display = display;
        this.icon = icon;
        this.description = description;
    }

    public String key() {
        return name().toLowerCase().replace('_', '-');
    }
}
