package de.crafterteam.odv.data;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.scenario.Scenario;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** kits.yml – je ein Kit für MEETUP, ODV und PRACTICE. Ohne eigenes Kit gilt das Standard-Kit. */
public class Kits {

    public enum Type { MEETUP, ODV, PRACTICE }

    private final OdvPlugin plugin;
    private final File file;
    private YamlConfiguration y;

    public Kits(OdvPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "kits.yml");
        load();
    }

    public void load() {
        y = YamlConfiguration.loadConfiguration(file);
    }

    public boolean hasCustom(Type t) {
        return y.contains(t.name() + ".inventory");
    }

    public void save(Player p, Type t) throws IOException {
        List<ItemStack> inv = new ArrayList<>();
        for (ItemStack it : p.getInventory().getStorageContents()) inv.add(plugin.items().isMenuItem(it) ? null : it);
        List<ItemStack> armor = new ArrayList<>(List.of(p.getInventory().getArmorContents()));
        y.set(t.name() + ".inventory", inv);
        y.set(t.name() + ".armor", armor);
        y.set(t.name() + ".offhand", p.getInventory().getItemInOffHand());
        y.save(file);
    }

    public void reset(Type t) throws IOException {
        y.set(t.name(), null);
        y.save(file);
    }

    public void give(Player p, Type t) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        if (hasCustom(t)) {
            inv.setStorageContents(items(y.getList(t.name() + ".inventory"), inv.getStorageContents().length));
            inv.setArmorContents(items(y.getList(t.name() + ".armor"), 4));
            ItemStack off = y.getItemStack(t.name() + ".offhand");
            if (off != null) inv.setItemInOffHand(off.clone());
        } else {
            switch (t) {
                case MEETUP -> meetup(inv);
                case ODV -> odv(inv);
                case PRACTICE -> practice(inv);
            }
        }
        // Szenarien filtern
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it == null) continue;
            Material m = it.getType();
            boolean bow = m == Material.BOW || m == Material.CROSSBOW || m == Material.ARROW || m == Material.SPECTRAL_ARROW;
            if ((bow && plugin.settings().has(Scenario.BOWLESS)) || (m == Material.FISHING_ROD && plugin.settings().has(Scenario.RODLESS))) {
                inv.setItem(i, null);
            }
        }
        plugin.combat().prepare(p);
    }

    private void meetup(PlayerInventory inv) {
        inv.setItem(0, ench(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 2));
        inv.setItem(1, new ItemStack(Material.FISHING_ROD));
        inv.setItem(2, ench(Material.BOW, Enchantment.POWER, 2));
        inv.setItem(3, new ItemStack(Material.COOKED_BEEF, 32));
        inv.setItem(4, new ItemStack(Material.GOLDEN_APPLE, 6));
        inv.setItem(5, new ItemStack(Material.WATER_BUCKET));
        inv.setItem(6, new ItemStack(Material.LAVA_BUCKET));
        inv.setItem(7, new ItemStack(Material.COBBLESTONE, 64));
        inv.setItem(8, new ItemStack(Material.WIND_CHARGE, 8));
        inv.setItem(9, new ItemStack(Material.ARROW, 64));
        inv.setItem(10, ench(Material.DIAMOND_PICKAXE, Enchantment.EFFICIENCY, 3));
        inv.setItem(11, ench(Material.DIAMOND_AXE, Enchantment.EFFICIENCY, 2));
        inv.setItem(12, new ItemStack(Material.WATER_BUCKET));
        inv.setItem(13, new ItemStack(Material.ANVIL));
        inv.setItem(14, new ItemStack(Material.EXPERIENCE_BOTTLE, 32));
        inv.setItem(15, new ItemStack(Material.OAK_PLANKS, 64));
        inv.setItem(16, new ItemStack(Material.DIAMOND_SPEAR));
        inv.setItem(17, new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
        inv.setItem(18, new ItemStack(Material.NETHERITE_INGOT));
        inv.setHelmet(ench(Material.NETHERITE_HELMET, Enchantment.PROTECTION, 1));
        inv.setChestplate(ench(Material.DIAMOND_CHESTPLATE, Enchantment.PROTECTION, 2));
        inv.setLeggings(ench(Material.IRON_LEGGINGS, Enchantment.PROTECTION, 2));
        inv.setBoots(ench(Material.NETHERITE_BOOTS, Enchantment.PROTECTION, 1));
    }

    private void odv(PlayerInventory inv) {
        inv.setItem(0, new ItemStack(Material.STONE_SWORD));
        inv.setItem(1, new ItemStack(Material.STONE_PICKAXE));
        inv.setItem(2, new ItemStack(Material.STONE_AXE));
        inv.setItem(3, new ItemStack(Material.COOKED_BEEF, 16));
        inv.setItem(4, new ItemStack(Material.CRAFTING_TABLE));
        inv.setItem(5, new ItemStack(Material.TORCH, 16));
    }

    private void practice(PlayerInventory inv) {
        inv.setItem(0, ench(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 1));
        inv.setItem(1, new ItemStack(Material.FISHING_ROD));
        inv.setItem(2, new ItemStack(Material.BOW));
        inv.setItem(3, new ItemStack(Material.GOLDEN_APPLE, 2));
        inv.setItem(8, new ItemStack(Material.COOKED_BEEF, 16));
        inv.setItem(9, new ItemStack(Material.ARROW, 16));
        inv.setHelmet(new ItemStack(Material.IRON_HELMET));
        inv.setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
        inv.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        inv.setBoots(new ItemStack(Material.DIAMOND_BOOTS));
    }

    private static ItemStack[] items(List<?> list, int len) {
        ItemStack[] out = new ItemStack[len];
        if (list != null) {
            for (int i = 0; i < Math.min(len, list.size()); i++) {
                if (list.get(i) instanceof ItemStack it) out[i] = it.clone();
            }
        }
        return out;
    }

    private static ItemStack ench(Material m, Enchantment e, int lvl) {
        ItemStack it = new ItemStack(m);
        it.addUnsafeEnchantment(e, lvl);
        return it;
    }
}
