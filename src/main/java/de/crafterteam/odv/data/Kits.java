package de.crafterteam.odv.data;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.Settings;
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

/**
 * kits.yml – ein Kit pro Spielmodus (MEETUP / ODV). Ohne gespeichertes Kit gilt das Standard-Kit.
 */
public class Kits {

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

    public boolean hasCustom(Settings.Mode mode) {
        return y.contains(mode.name() + ".inventory");
    }

    /** Speichert das Inventar (ohne Menü-Items). */
    public void save(Player p, Settings.Mode mode, java.util.Set<String> ignoreNames) throws IOException {
        y.set(mode.name() + ".inventory", list(p.getInventory().getContents(), ignoreNames));
        y.set(mode.name() + ".armor", list(p.getInventory().getArmorContents(), ignoreNames));
        y.save(file);
    }

    public void reset(Settings.Mode mode) throws IOException {
        y.set(mode.name(), null);
        y.save(file);
    }

    @SuppressWarnings("deprecation")
    public void give(Player p, Settings.Mode mode) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setArmorContents(new ItemStack[4]);
        if (hasCustom(mode)) {
            inv.setContents(items(y.getList(mode.name() + ".inventory"), 36));
            inv.setArmorContents(items(y.getList(mode.name() + ".armor"), 4));
        } else if (mode == Settings.Mode.MEETUP) {
            inv.setItem(0, ench(Material.DIAMOND_SWORD, Enchantment.DAMAGE_ALL, 2));
            inv.setItem(1, new ItemStack(Material.FISHING_ROD));
            inv.setItem(2, ench(Material.BOW, Enchantment.ARROW_DAMAGE, 2));
            inv.setItem(3, new ItemStack(Material.COOKED_BEEF, 32));
            inv.setItem(4, new ItemStack(Material.GOLDEN_APPLE, 6));
            inv.setItem(5, new ItemStack(Material.WATER_BUCKET));
            inv.setItem(6, new ItemStack(Material.LAVA_BUCKET));
            inv.setItem(7, new ItemStack(Material.COBBLESTONE, 64));
            inv.setItem(8, new ItemStack(Material.WOOD, 64));
            inv.setItem(9, new ItemStack(Material.ARROW, 64));
            inv.setItem(10, ench(Material.DIAMOND_PICKAXE, Enchantment.DIG_SPEED, 2));
            inv.setItem(11, new ItemStack(Material.DIAMOND_AXE));
            inv.setItem(12, new ItemStack(Material.WATER_BUCKET));
            inv.setItem(13, new ItemStack(Material.ANVIL));
            inv.setItem(14, new ItemStack(Material.EXP_BOTTLE, 32));
            inv.setItem(15, new ItemStack(Material.COBBLESTONE, 64));
            inv.setArmorContents(new ItemStack[]{
                    ench(Material.DIAMOND_BOOTS, Enchantment.PROTECTION_ENVIRONMENTAL, 1),
                    ench(Material.IRON_LEGGINGS, Enchantment.PROTECTION_ENVIRONMENTAL, 2),
                    ench(Material.DIAMOND_CHESTPLATE, Enchantment.PROTECTION_ENVIRONMENTAL, 1),
                    ench(Material.IRON_HELMET, Enchantment.PROTECTION_ENVIRONMENTAL, 2)});
        } else {
            inv.setItem(0, new ItemStack(Material.STONE_SWORD));
            inv.setItem(1, new ItemStack(Material.STONE_PICKAXE));
            inv.setItem(2, new ItemStack(Material.STONE_AXE));
            inv.setItem(3, new ItemStack(Material.COOKED_BEEF, 16));
            inv.setItem(4, new ItemStack(Material.WORKBENCH));
        }
        p.updateInventory();
    }

    private static List<Object> list(ItemStack[] items, java.util.Set<String> ignore) {
        List<Object> l = new ArrayList<Object>();
        for (ItemStack it : items) {
            boolean skip = it != null && it.hasItemMeta() && it.getItemMeta().hasDisplayName()
                    && ignore.contains(it.getItemMeta().getDisplayName());
            l.add(skip ? null : it);
        }
        return l;
    }

    private static ItemStack[] items(List<?> list, int len) {
        ItemStack[] out = new ItemStack[len];
        if (list != null) {
            for (int i = 0; i < Math.min(len, list.size()); i++) {
                if (list.get(i) instanceof ItemStack) out[i] = ((ItemStack) list.get(i)).clone();
            }
        }
        return out;
    }

    private static ItemStack ench(Material m, Enchantment e, int lvl) {
        ItemStack it = new ItemStack(m);
        it.addEnchantment(e, lvl);
        return it;
    }
}
