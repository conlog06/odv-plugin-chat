package de.crafterteam.odv.gui;

import de.crafterteam.odv.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/** Einfaches Klick-Menü: Slot → Aktion. */
public class ActionGui implements InventoryHolder {

    private final Inventory inv;
    private final Map<Integer, BiConsumer<Player, ClickType>> actions = new HashMap<>();

    public ActionGui(int rows, String title) {
        inv = Bukkit.createInventory(this, rows * 9, Txt.c(title));
    }

    public ActionGui fill(ItemStack it) {
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, it);
        return this;
    }

    public ActionGui item(int slot, ItemStack it) {
        inv.setItem(slot, it);
        return this;
    }

    public ActionGui set(int slot, ItemStack it, BiConsumer<Player, ClickType> a) {
        inv.setItem(slot, it);
        actions.put(slot, a);
        return this;
    }

    public void open(Player p) {
        p.openInventory(inv);
    }

    public void click(Player p, int slot, ClickType c) {
        BiConsumer<Player, ClickType> a = actions.get(slot);
        if (a != null) a.accept(p, c);
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
