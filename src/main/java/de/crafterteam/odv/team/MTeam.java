package de.crafterteam.odv.team;

import org.bukkit.ChatColor;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class MTeam {

    private final int number;          // 1..36 (in Solo: laufende Nummer)
    private final String name;
    private final ChatColor color;
    private final short woolData;
    private final Set<UUID> members = new LinkedHashSet<UUID>();
    private int kills;

    public MTeam(int number, String name, ChatColor color, short woolData) {
        this.number = number;
        this.name = name;
        this.color = color;
        this.woolData = woolData;
    }

    public int getNumber() { return number; }
    public String getName() { return name; }
    public ChatColor getColor() { return color; }
    public short getWoolData() { return woolData; }
    public Set<UUID> getMembers() { return members; }
    public int getKills() { return kills; }
    public void addKill() { kills++; }

    public String getDisplayName() {
        return color + name;
    }
}
