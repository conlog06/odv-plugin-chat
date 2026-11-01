package de.crafterteam.odv.game;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.Settings;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BlocksAttacks;
import io.papermc.paper.datacomponent.item.blocksattacks.DamageReduction;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Kampfsystem: umschaltbar zwischen 1.8.9-PvP (kein Cooldown, kein Sweep, Schwert-Blocken,
 * alte Notch-Äpfel, alter Schaden) und normalem 26.3-PvP.
 */
public class CombatManager implements Listener {

    /** Faktor: 1.8-Schaden / 26.x-Schaden (inkl. 1 Grundschaden). */
    private static final Map<Material, Double> OLD_DAMAGE = new EnumMap<>(Material.class);

    static {
        OLD_DAMAGE.put(Material.WOODEN_SWORD, 5.0 / 4);
        OLD_DAMAGE.put(Material.STONE_SWORD, 6.0 / 5);
        OLD_DAMAGE.put(Material.COPPER_SWORD, 6.0 / 5);
        OLD_DAMAGE.put(Material.GOLDEN_SWORD, 5.0 / 4);
        OLD_DAMAGE.put(Material.IRON_SWORD, 7.0 / 6);
        OLD_DAMAGE.put(Material.DIAMOND_SWORD, 8.0 / 7);
        OLD_DAMAGE.put(Material.NETHERITE_SWORD, 9.0 / 8);
        OLD_DAMAGE.put(Material.WOODEN_AXE, 4.0 / 7);
        OLD_DAMAGE.put(Material.STONE_AXE, 5.0 / 9);
        OLD_DAMAGE.put(Material.COPPER_AXE, 5.0 / 9);
        OLD_DAMAGE.put(Material.GOLDEN_AXE, 4.0 / 7);
        OLD_DAMAGE.put(Material.IRON_AXE, 6.0 / 9);
        OLD_DAMAGE.put(Material.DIAMOND_AXE, 7.0 / 9);
        OLD_DAMAGE.put(Material.NETHERITE_AXE, 8.0 / 10);
    }

    private final OdvPlugin plugin;

    public CombatManager(OdvPlugin plugin) {
        this.plugin = plugin;
    }

    private Settings s() {
        return plugin.settings();
    }

    /** Spieler auf das aktuelle Kampfsystem einstellen (Cooldown, Schwerter, Schilde). */
    public void prepare(Player p) {
        AttributeInstance speed = p.getAttribute(Attribute.ATTACK_SPEED);
        if (speed != null) speed.setBaseValue(s().oldPvp ? 1024.0 : 4.0);
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it == null) continue;
            if (it.getType() == Material.SHIELD && !s().shields) {
                inv.setItem(i, null);
                continue;
            }
            apply(it);
        }
    }

    public void prepareAll() {
        for (Player p : Bukkit.getOnlinePlayers()) prepare(p);
    }

    /** Schwert-Blocken wie in 1.8 (Rechtsklick hält das Schwert vor sich, −50 % Schaden). */
    public void apply(ItemStack it) {
        if (it == null || !Tag.ITEMS_SWORDS.isTagged(it.getType())) return;
        boolean want = s().oldPvp && s().swordBlocking;
        boolean has = it.hasData(DataComponentTypes.BLOCKS_ATTACKS);
        if (want && !has) {
            it.setData(DataComponentTypes.BLOCKS_ATTACKS, BlocksAttacks.blocksAttacks()
                    .blockDelaySeconds(0f)
                    .disableCooldownScale(0f)
                    .addDamageReduction(DamageReduction.damageReduction().base(0f).factor(0.5f).horizontalBlockingAngle(90f).build())
                    .build());
        } else if (!want && has) {
            it.resetData(DataComponentTypes.BLOCKS_ATTACKS);
        }
    }

    // ------------------------------------------------------------ Events

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        prepare(e.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> prepare(e.getPlayer()));
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        prepare(e.getPlayer());
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent e) {
        apply(e.getPlayer().getInventory().getItem(e.getNewSlot()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        ItemStack it = e.getItem().getItemStack();
        if (Tag.ITEMS_SWORDS.isTagged(it.getType())) {
            apply(it);
            e.getItem().setItemStack(it);
        }
        if (it.getType() == Material.SHIELD && !s().shields && e.getEntity() instanceof Player) e.setCancelled(true);
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        ItemStack r = e.getInventory().getResult();
        if (r == null) return;
        if (r.getType() == Material.SHIELD && !s().shields) {
            e.getInventory().setResult(null);
            return;
        }
        apply(r);
        e.getInventory().setResult(r);
    }

    @EventHandler(ignoreCancelled = true)
    public void onShield(PlayerInteractEvent e) {
        if (!s().shields && e.getItem() != null && e.getItem().getType() == Material.SHIELD) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!s().oldPvp) return;
        if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            e.setCancelled(true);
            return;
        }
        if (s().oldDamage && e.getDamager() instanceof Player p && e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            Double f = OLD_DAMAGE.get(p.getInventory().getItemInMainHand().getType());
            if (f != null) e.setDamage(e.getDamage() * f);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (!s().oldPvp || !s().oldGoldenApples || e.getItem().getType() != Material.ENCHANTED_GOLDEN_APPLE) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (PotionEffectType t : new PotionEffectType[]{PotionEffectType.REGENERATION, PotionEffectType.ABSORPTION,
                    PotionEffectType.RESISTANCE, PotionEffectType.FIRE_RESISTANCE}) p.removePotionEffect(t);
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 30 * 20, 4));
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 120 * 20, 0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 300 * 20, 0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 300 * 20, 0));
        });
    }
}
