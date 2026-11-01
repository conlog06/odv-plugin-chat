package de.crafterteam.odv.team;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class MTeam {

    private final int number;
    private final String name;
    private final ChatColor color;
    private final Material wool;
    private final Set<UUID> members = new LinkedHashSet<>();
    private int kills;
    private Inventory backpack;

    public MTeam(int number, String name, ChatColor color, Material wool) {
        this.number = number;
        this.name = name;
        this.color = color;
        this.wool = wool;
    }

    public int getNumber() { return number; }
    public String getName() { return name; }
    public ChatColor getColor() { return color; }
    public Material getWool() { return wool; }
    public Set<UUID> getMembers() { return members; }
    public int getKills() { return kills; }
    public void addKill() { kills++; }
    public Inventory getBackpack() { return backpack; }
    public void setBackpack(Inventory inv) { backpack = inv; }

    public String getDisplayName() {
        return color + name;
    }

    public NamedTextColor textColor() {
        NamedTextColor c = NamedTextColor.NAMES.value(color.name().toLowerCase());
        return c == null ? NamedTextColor.WHITE : c;
    }
}
