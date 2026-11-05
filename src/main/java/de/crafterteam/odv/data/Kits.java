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

/**
 * kits.yml – beliebig viele eigene Kits (kits.&lt;name&gt;), je Modus ist eins aktiv (active.ODV / active.MEETUP).
 * Ohne aktives Kit gilt das Standard-Kit.
 */
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

    // ---------------------------------------------------------------- eigene Kits

    public static String clean(String name) {
        return name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }

    public List<String> names() {
        var sec = y.getConfigurationSection("kits");
        return sec == null ? new ArrayList<>() : new ArrayList<>(sec.getKeys(false));
    }

    public boolean exists(String name) {
        return y.contains("kits." + clean(name) + ".inventory");
    }

    /** Aktuelles Inventar als neues Kit speichern. */
    public void create(Player p, String name) throws IOException {
        String k = "kits." + clean(name);
        List<ItemStack> inv = new ArrayList<>();
        for (ItemStack it : p.getInventory().getStorageContents()) inv.add(plugin.items().isMenuItem(it) ? null : it);
        y.set(k + ".inventory", inv);
        y.set(k + ".armor", new ArrayList<>(List.of(p.getInventory().getArmorContents())));
        y.set(k + ".offhand", p.getInventory().getItemInOffHand());
        ItemStack icon = null;
        for (ItemStack it : p.getInventory().getStorageContents()) if (it != null && !plugin.items().isMenuItem(it)) {
            icon = it;
            break;
        }
        y.set(k + ".icon", icon == null ? "CHEST" : icon.getType().name());
        y.set(k + ".by", p.getName());
        y.save(file);
    }

    public void delete(String name) throws IOException {
        String n = clean(name);
        y.set("kits." + n, null);
        for (Type t : Type.values()) if (n.equals(y.getString("active." + t.name()))) y.set("active." + t.name(), null);
        y.save(file);
    }

    /** name = null → Standard-Kit. */
    public void use(Type t, String name) throws IOException {
        y.set("active." + t.name(), name == null ? null : clean(name));
        y.save(file);
    }

    public String active(Type t) {
        String n = y.getString("active." + t.name());
        return n != null && exists(n) ? n : null;
    }

    public Material icon(String name) {
        Material m = Material.matchMaterial(y.getString("kits." + clean(name) + ".icon", "CHEST"));
        return m == null || m.isAir() ? Material.CHEST : m;
    }

    public String creator(String name) {
        return y.getString("kits." + clean(name) + ".by", "-");
    }

    public void give(Player p, Type t) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        String active = active(t);
        if (active != null) {
            String k = "kits." + active;
            inv.setStorageContents(items(y.getList(k + ".inventory"), inv.getStorageContents().length));
            inv.setArmorContents(items(y.getList(k + ".armor"), 4));
            ItemStack off = y.getItemStack(k + ".offhand");
            if (off != null) inv.setItemInOffHand(off.clone());
        } else if (hasCustom(t)) {
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
        // Netheriteless: Netherite → Diamant
        if (plugin.settings().has(Scenario.NETHERITELESS)) {
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack it = inv.getItem(i);
                if (it == null || !it.getType().name().startsWith("NETHERITE_")) continue;
                Material d = Material.matchMaterial("DIAMOND_" + it.getType().name().substring(10));
                if (d != null) inv.setItem(i, it.withType(d));
            }
        }
        // Szenarien filtern (auch Rüstung/Offhand)
        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < armor.length; i++) if (armor[i] != null && plugin.scenarios().forbidden(armor[i].getType())) armor[i] = null;
        inv.setArmorContents(armor);
        if (plugin.scenarios().forbidden(inv.getItemInOffHand().getType())) inv.setItemInOffHand(null);
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it == null) continue;
            Material m = it.getType();
            boolean arrow = m == Material.ARROW || m == Material.SPECTRAL_ARROW;
            if ((arrow && plugin.settings().has(Scenario.BOWLESS)) || plugin.scenarios().forbidden(m)) {
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

    /** ODV-Standard wie bei Mega-Events. */
    private void odv(PlayerInventory inv) {
        inv.setItem(0, new ItemStack(Material.DIAMOND_AXE));
        inv.setItem(1, new ItemStack(Material.DIAMOND_PICKAXE));
        inv.setItem(2, new ItemStack(Material.COOKED_BEEF, 64));
        inv.setItem(3, new ItemStack(Material.BOOK, 3));
        inv.setItem(4, new ItemStack(Material.ENCHANTING_TABLE));
        inv.setItem(5, new ItemStack(Material.ANVIL));
        inv.setItem(6, new ItemStack(Material.OAK_LOG, 32));
        inv.setItem(7, new ItemStack(Material.WATER_BUCKET));
        inv.setItem(8, new ItemStack(Material.LAVA_BUCKET));
        inv.setItem(9, new ItemStack(Material.LAPIS_LAZULI, 64));
        inv.setItem(10, new ItemStack(Material.APPLE, 16));
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
