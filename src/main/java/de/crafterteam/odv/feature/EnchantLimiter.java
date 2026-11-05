package de.crafterteam.odv.feature;

import de.crafterteam.odv.OdvPlugin;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Erlaubte Verzauberungen (config: enchantments.&lt;name&gt;: max-Level, 0 = verboten).
 * Greift am Zaubertisch, Amboss, bei Dorfbewohnern und in Truhen-Loot.
 */
public class EnchantLimiter implements Listener {

    private final OdvPlugin plugin;

    public EnchantLimiter(OdvPlugin plugin) {
        this.plugin = plugin;
    }

    public static String key(Enchantment e) {
        return e.getKey().getKey();
    }

    public int max(Enchantment e) {
        Integer l = plugin.settings().enchantLimits.get(key(e));
        return l == null ? e.getMaxLevel() : l;
    }

    private boolean active() {
        return !plugin.settings().enchantLimits.isEmpty();
    }

    /** Verzauberungen auf das Erlaubte kürzen. null = unverändert. */
    public ItemStack cap(ItemStack it) {
        if (it == null || !active()) return null;
        boolean changed = false;
        ItemStack out = it.clone();
        for (Map.Entry<Enchantment, Integer> en : new ArrayList<>(out.getEnchantments().entrySet())) {
            int m = max(en.getKey());
            if (en.getValue() > m) {
                out.removeEnchantment(en.getKey());
                if (m > 0) out.addUnsafeEnchantment(en.getKey(), m);
                changed = true;
            }
        }
        if (out.getItemMeta() instanceof EnchantmentStorageMeta book) {
            boolean bc = false;
            for (Map.Entry<Enchantment, Integer> en : new ArrayList<>(book.getStoredEnchants().entrySet())) {
                int m = max(en.getKey());
                if (en.getValue() > m) {
                    book.removeStoredEnchant(en.getKey());
                    if (m > 0) book.addStoredEnchant(en.getKey(), m, true);
                    bc = true;
                }
            }
            if (bc) {
                out.setItemMeta(book);
                changed = true;
            }
        }
        return changed ? out : null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareItemEnchantEvent e) {
        if (!active()) return;
        EnchantmentOffer[] offers = e.getOffers();
        for (int i = 0; i < offers.length; i++) {
            EnchantmentOffer o = offers[i];
            if (o == null) continue;
            int m = max(o.getEnchantment());
            if (m <= 0) offers[i] = null;
            else if (o.getEnchantmentLevel() > m) o.setEnchantmentLevel(m);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent e) {
        if (!active()) return;
        Map<Enchantment, Integer> add = e.getEnchantsToAdd();
        for (Enchantment en : new ArrayList<>(add.keySet())) {
            int m = max(en);
            if (m <= 0) add.remove(en);
            else if (add.get(en) > m) add.put(en, m);
        }
        if (add.isEmpty()) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent e) {
        ItemStack fixed = cap(e.getResult());
        if (fixed != null) e.setResult(fixed);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTrade(VillagerAcquireTradeEvent e) {
        MerchantRecipe r = e.getRecipe();
        ItemStack fixed = cap(r.getResult());
        if (fixed == null) return;
        MerchantRecipe n = new MerchantRecipe(fixed, r.getUses(), r.getMaxUses(), r.hasExperienceReward(), r.getVillagerExperience(), r.getPriceMultiplier());
        n.setIngredients(r.getIngredients());
        e.setRecipe(n);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLoot(LootGenerateEvent e) {
        if (!active()) return;
        List<ItemStack> loot = e.getLoot();
        for (int i = 0; i < loot.size(); i++) {
            ItemStack fixed = cap(loot.get(i));
            if (fixed != null) loot.set(i, fixed);
        }
    }
}
