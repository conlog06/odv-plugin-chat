package de.crafterteam.odv.util;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Item-Builder + Erkennung eigener Menü-Items per PersistentDataContainer. */
public class Items {

    private final NamespacedKey key;

    public Items(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "item");
    }

    public NamespacedKey key() {
        return key;
    }

    public Builder of(Material m) {
        return new Builder(m, 1);
    }

    public Builder of(Material m, int amount) {
        return new Builder(m, amount);
    }

    /** Lobby-/Menü-Item mit fester ID (wird nie ins Kit übernommen). */
    public ItemStack menu(Material m, String id, String name, String... lore) {
        return of(m).name(name).lore(lore).tag(id).build();
    }

    public String id(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public boolean isMenuItem(ItemStack it) {
        return id(it) != null;
    }

    public final class Builder {
        private final ItemStack item;
        private final ItemMeta meta;
        private final List<String> lore = new ArrayList<>();

        Builder(Material m, int amount) {
            item = new ItemStack(m, Math.max(1, Math.min(m.getMaxStackSize(), amount)));
            meta = item.getItemMeta();
        }

        public Builder name(String n) {
            meta.displayName(Txt.item(n));
            return this;
        }

        public Builder lore(String... lines) {
            lore.addAll(Arrays.asList(lines));
            return this;
        }

        public Builder lore(List<String> lines) {
            lore.addAll(lines);
            return this;
        }

        public Builder glow(boolean g) {
            if (g) meta.setEnchantmentGlintOverride(true);
            return this;
        }

        public Builder tag(String id) {
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
            return this;
        }

        public ItemStack build() {
            if (!lore.isEmpty()) meta.lore(Txt.items(lore));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            item.setItemMeta(meta);
            return item;
        }
    }
}
