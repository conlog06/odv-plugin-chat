package de.crafterteam.meetup.team;

import org.bukkit.ChatColor;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class MTeam {

    private final int id;
    private final String name;
    private final ChatColor color;
    private final short woolData;
    private final Set<UUID> members = new LinkedHashSet<UUID>();
    private int kills;

    public MTeam(int id, String name, ChatColor color, short woolData) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.woolData = woolData;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public ChatColor getColor() { return color; }
    public short getWoolData() { return woolData; }
    public Set<UUID> getMembers() { return members; }
    public int getKills() { return kills; }
    public void addKill() { kills++; }

    public String getDisplayName() {
        return color + name;
    }

    /** Kurzes Kürzel für Nametags (max. 16 Zeichen Prefix in 1.8). */
    public String getTag() {
        return color + "[" + (name.length() > 8 ? name.substring(0, 8) : name) + "] ";
    }
}
