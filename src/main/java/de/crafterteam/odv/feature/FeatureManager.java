package de.crafterteam.odv.feature;

import de.crafterteam.odv.OdvPlugin;
import de.crafterteam.odv.Settings;
import de.crafterteam.odv.game.GameState;
import de.crafterteam.odv.team.MTeam;
import de.crafterteam.odv.util.Txt;
import de.crafterteam.odv.world.SpawnManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Die 10 neuen Features:
 * Zuschauer-Voting, Death-Recap, Team-Revive, Supply-Drops, Team-Ping,
 * Kopfgeld, Adaptive Border (GameManager), Kampf-Heatmap, Showdown (GameManager), Zuschauer-Wetten.
 */
public class FeatureManager implements Listener {

    private record Hit(long time, String source, double damage) {
    }

    private enum VoteEvent {
        GLOW("§eGlow-Out", "Alle Lebenden leuchten 30 Sekunden"),
        SPEED("§bSpeed-Rausch", "Speed II für alle – 30 Sekunden"),
        SUPPLY("§6Supply-Drop", "Sofort ein Supply-Drop"),
        MOON("§dMondschwerkraft", "Sprungkraft & langsames Fallen – 30 Sekunden"),
        STORM("§9Gewitter", "Blitze & Dunkelheit"),
        HUNGER("§cHungersnot", "Hunger III – 20 Sekunden"),
        HEAL("§aHeilwelle", "Regeneration für alle – 10 Sekunden");

        final String display, description;

        VoteEvent(String display, String description) {
            this.display = display;
            this.description = description;
        }
    }

    private final OdvPlugin plugin;
    private final Random random = new Random();

    // Death-Recap
    private final Map<UUID, List<Hit>> hits = new HashMap<>();
    // Revive
    private final Set<UUID> revived = new HashSet<>();
    private final Map<UUID, Long> reviveDeadline = new HashMap<>();
    // Supply
    private int supplyTimer;
    // Ping
    private final Map<UUID, Long> pingCooldown = new HashMap<>();
    // Bounty
    private UUID bounty;
    // Heatmap
    private final List<int[]> heat = new ArrayList<>();
    // Voting
    private List<VoteEvent> voteOptions = null;
    private final Map<UUID, Integer> votes = new HashMap<>();
    private int voteTimer, voteEndsIn;
    // Wetten
    private final Map<UUID, Integer> bets = new HashMap<>();

    public FeatureManager(OdvPlugin plugin) {
        this.plugin = plugin;
    }

    private Settings s() {
        return plugin.settings();
    }

    private boolean on(Feature f) {
        return s().has(f);
    }

    public UUID bountyTarget() {
        return on(Feature.BOUNTY) ? bounty : null;
    }

    public void onRoundStart() {
        reset();
        supplyTimer = s().supplyInterval;
        voteTimer = 300;
        if (on(Feature.TEAM_PING) && !s().isSolo()) plugin.game().broadcast("§7Tipp: §eSchleichen + F §7oder §e/ping §7markiert einen Ort für dein Team.");
    }

    public void reset() {
        hits.clear();
        revived.clear();
        reviveDeadline.clear();
        pingCooldown.clear();
        heat.clear();
        votes.clear();
        voteOptions = null;
        bets.clear();
        if (bounty != null) {
            Player p = Bukkit.getPlayer(bounty);
            if (p != null) p.removePotionEffect(PotionEffectType.GLOWING);
        }
        bounty = null;
        plugin.holograms().removePrefix("supply");
        plugin.holograms().removePrefix("ping-");
    }

    /** Jede Sekunde vom GameManager aufgerufen. */
    public void tick() {
        GameState st = plugin.game().getState();
        if (st != GameState.RUNNING && st != GameState.PROTECTION) return;
        if (on(Feature.SUPPLY_DROPS) && st == GameState.RUNNING && --supplyTimer <= 0) {
            supplyTimer = s().supplyInterval;
            supplyDrop();
        }
        if (on(Feature.BOUNTY)) updateBounty();
        if (on(Feature.SPECTATOR_VOTE) && st == GameState.RUNNING) tickVote();
    }

    // ===================================================================== Death-Recap + Heatmap

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !plugin.game().isPlaying(p)) return;
        String src = cause(e);
        hits.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>()).add(new Hit(System.currentTimeMillis(), src, e.getFinalDamage()));
        if (e instanceof EntityDamageByEntityEvent ev && attacker(ev.getDamager()) != null && heat.size() < 50000) {
            heat.add(new int[]{p.getLocation().getBlockX(), p.getLocation().getBlockZ()});
        }
    }

    private static Player attacker(Entity d) {
        if (d instanceof Player p) return p;
        if (d instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    private static String cause(EntityDamageEvent e) {
        if (e instanceof EntityDamageByEntityEvent ev) {
            Player a = attacker(ev.getDamager());
            if (a != null) {
                String weapon = ev.getDamager() instanceof Projectile ? "Projektil" : nice(a.getInventory().getItemInMainHand().getType());
                return "§c" + a.getName() + " §8(" + weapon + ")";
            }
            return "§6" + nice(ev.getDamager().getType().name());
        }
        return "§7" + switch (e.getCause()) {
            case FALL -> "Fallschaden";
            case LAVA, FIRE, FIRE_TICK -> "Feuer/Lava";
            case WORLD_BORDER -> "Border";
            case DROWNING -> "Ertrinken";
            case STARVATION -> "Hunger";
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> "Explosion";
            case POISON, WITHER, MAGIC -> "Effekte";
            default -> nice(e.getCause().name());
        };
    }

    private static String nice(Object o) {
        String s = o.toString().toLowerCase().replace('_', ' ');
        return s.isEmpty() ? "?" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void sendRecap(Player p) {
        List<Hit> list = hits.remove(p.getUniqueId());
        if (list == null || list.isEmpty()) return;
        long since = System.currentTimeMillis() - 30_000;
        Map<String, double[]> sum = new LinkedHashMap<>();
        for (Hit h : list) {
            if (h.time() < since) continue;
            double[] v = sum.computeIfAbsent(h.source(), k -> new double[2]);
            v[0] += h.damage();
            v[1]++;
        }
        if (sum.isEmpty()) return;
        Txt.send(p, "§8§m      §r §c§lDeath-Recap §7(letzte 30s) §8§m      ");
        sum.entrySet().stream().sorted((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0])).forEach(e ->
                Txt.send(p, "§8» " + e.getKey() + " §7– §c" + String.format(java.util.Locale.ROOT, "%.1f", e.getValue()[0] / 2) + "❤ §8(" + (int) e.getValue()[1] + " Treffer)"));
    }

    // ===================================================================== Ausscheiden / Ende

    public void onEliminate(UUID victim, Player killer, Location where) {
        Player vp = Bukkit.getPlayer(victim);
        if (on(Feature.DEATH_RECAP) && vp != null) sendRecap(vp);
        hits.remove(victim);
        if (where != null) heat.add(new int[]{where.getBlockX(), where.getBlockZ(), 1});

        if (on(Feature.BOUNTY) && victim.equals(bounty)) {
            bounty = null;
            if (killer != null && !killer.getUniqueId().equals(victim)) {
                killer.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 2), new ItemStack(Material.ENDER_PEARL, 2));
                killer.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 120, 1));
                plugin.game().broadcast("§6☠ §e" + killer.getName() + " §7hat das §6Kopfgeld §7kassiert!");
            }
        }

        if (on(Feature.REVIVE) && !s().isSolo() && where != null && !revived.contains(victim)) {
            MTeam t = plugin.teams().getTeam(victim);
            boolean teamAlive = false;
            if (t != null) for (UUID u : t.getMembers()) if (plugin.game().isAlive(u)) teamAlive = true;
            if (teamAlive) {
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(victim));
                meta.displayName(Txt.item("§eKopf von " + plugin.stats().name(victim)));
                meta.lore(Txt.items(List.of("§7Ein Teammitglied kann", "§7mit Rechtsklick wiederbeleben.", "§cNur 2 Minuten lang!")));
                meta.getPersistentDataContainer().set(reviveKey(), PersistentDataType.STRING, victim.toString());
                head.setItemMeta(meta);
                where.getWorld().dropItemNaturally(where, head);
                reviveDeadline.put(victim, System.currentTimeMillis() + 120_000);
                if (t != null) for (UUID u : t.getMembers()) {
                    Player m = Bukkit.getPlayer(u);
                    if (m != null && plugin.game().isAlive(u)) Txt.send(m, s().prefix + "§eHol den Kopf von §f" + plugin.stats().name(victim) + " §e– Rechtsklick belebt wieder! §7(2 Min.)");
                }
            }
        }
    }

    public void onRoundEnd(MTeam winner) {
        if (on(Feature.BETTING) && winner != null && !bets.isEmpty()) {
            for (Map.Entry<UUID, Integer> e : bets.entrySet()) {
                Player p = Bukkit.getPlayer(e.getKey());
                if (e.getValue() == winner.getNumber()) {
                    plugin.stats().add(e.getKey(), "bets");
                    if (p != null) Txt.send(p, s().prefix + "§a§lRichtig getippt! §7+1 Wettpunkt §8(gesamt " + plugin.stats().get(e.getKey(), "bets") + ")");
                } else if (p != null) Txt.send(p, s().prefix + "§7Deine Wette lag daneben.");
            }
        }
        if (on(Feature.HEATMAP) && !heat.isEmpty()) giveHeatmap();
    }

    // ===================================================================== Team-Revive

    private org.bukkit.NamespacedKey reviveKey() {
        return new org.bukkit.NamespacedKey(plugin, "revive");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        ItemStack it = e.getItem();
        if (it == null || it.getType() != Material.PLAYER_HEAD || !it.hasItemMeta()) return;
        String id = it.getItemMeta().getPersistentDataContainer().get(reviveKey(), PersistentDataType.STRING);
        if (id == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        UUID victim = UUID.fromString(id);
        if (!plugin.game().isPlaying(p) || !plugin.teams().sameTeam(p.getUniqueId(), victim)) {
            Txt.actionBar(p, "§cNur Teammitglieder können wiederbeleben.");
            return;
        }
        Long until = reviveDeadline.get(victim);
        if (until == null || System.currentTimeMillis() > until || revived.contains(victim)) {
            Txt.actionBar(p, "§cZu spät – der Kopf ist kalt.");
            it.setAmount(it.getAmount() - 1);
            return;
        }
        if (plugin.game().revive(victim, p.getLocation())) {
            revived.add(victim);
            it.setAmount(it.getAmount() - 1);
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.5, 1, 0.5, 0.3);
            p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
            plugin.game().broadcast("§a" + p.getName() + " §7hat §a" + plugin.stats().name(victim) + " §7wiederbelebt!");
        } else Txt.actionBar(p, "§cDein Teammitglied ist nicht online.");
    }

    // ===================================================================== Supply-Drops

    public void supplyDrop() {
        World w = plugin.arena().getWorld();
        if (w == null) return;
        double half = w.getWorldBorder().getSize() / 2 - 20;
        if (half < 15) return;
        Location c = w.getWorldBorder().getCenter();
        int x = c.getBlockX() + (int) ((random.nextDouble() * 2 - 1) * half), z = c.getBlockZ() + (int) ((random.nextDouble() * 2 - 1) * half);
        int y = SpawnManager.ground(w, x, z) + 1;
        Block b = w.getBlockAt(x, y, z);
        b.setType(Material.CHEST);
        if (b.getState() instanceof Chest chest) {
            List<ItemStack> loot = loot();
            Collections.shuffle(loot);
            for (ItemStack it : loot) chest.getBlockInventory().setItem(random.nextInt(27), it);
        }
        String group = "supply-" + x + "_" + z;
        plugin.holograms().spawn(group, b.getLocation().add(0.5, 2.2, 0.5), "§6§l✦ Supply-Drop ✦\n§7" + x + " / " + z, 1.6f, null)
                .setViewRange(16f);
        plugin.game().broadcast("§6§l✦ Supply-Drop ✦ §7gelandet bei §eX " + x + " §7/ §eZ " + z + "§7!");
        plugin.game().sound(Sound.BLOCK_BEACON_ACTIVATE, 1.2f);
        Location beam = b.getLocation().add(0.5, 1, 0.5);
        int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (++t[0] > 90 || b.getType() != Material.CHEST || !plugin.game().getState().isIngame()) {
                task.cancel();
                plugin.holograms().remove(group);
                return;
            }
            for (int i = 0; i < 40; i += 2) w.spawnParticle(Particle.END_ROD, beam.clone().add(0, i, 0), 2, 0.05, 0.4, 0.05, 0);
        }, 0L, 20L);
    }

    private List<ItemStack> loot() {
        List<ItemStack> l = new ArrayList<>();
        l.add(new ItemStack(Material.GOLDEN_APPLE, 2 + random.nextInt(2)));
        l.add(new ItemStack(Material.DIAMOND, 3 + random.nextInt(4)));
        l.add(new ItemStack(Material.ENDER_PEARL, 2));
        l.add(new ItemStack(Material.EXPERIENCE_BOTTLE, 16));
        l.add(new ItemStack(Material.ARROW, 16));
        if (random.nextBoolean()) l.add(new ItemStack(Material.NETHERITE_SCRAP, 1 + random.nextInt(2)));
        if (random.nextBoolean()) l.add(new ItemStack(Material.WIND_CHARGE, 6));
        if (random.nextInt(3) == 0) l.add(book(random.nextBoolean() ? Enchantment.PROTECTION : Enchantment.SHARPNESS, 3));
        if (random.nextInt(10) == 0) l.add(new ItemStack(Material.MACE));
        if (random.nextInt(12) == 0) l.add(new ItemStack(Material.TOTEM_OF_UNDYING));
        return l;
    }

    private static ItemStack book(Enchantment e, int lvl) {
        ItemStack b = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta m = (EnchantmentStorageMeta) b.getItemMeta();
        m.addStoredEnchant(e, lvl, true);
        b.setItemMeta(m);
        return b;
    }

    // ===================================================================== Team-Ping

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (on(Feature.TEAM_PING) && e.getPlayer().isSneaking() && plugin.game().isPlaying(e.getPlayer())) {
            e.setCancelled(true);
            ping(e.getPlayer());
        }
    }

    public void ping(Player p) {
        if (!on(Feature.TEAM_PING) || !plugin.game().isPlaying(p) || s().isSolo()) {
            Txt.actionBar(p, "§cPing geht nur im Team während der Runde.");
            return;
        }
        long now = System.currentTimeMillis();
        if (pingCooldown.getOrDefault(p.getUniqueId(), 0L) > now) return;
        pingCooldown.put(p.getUniqueId(), now + 4000);
        Block target = p.getTargetBlockExact(150);
        Location at = target != null ? target.getLocation().add(0.5, 1.8, 0.5) : p.getEyeLocation().add(p.getLocation().getDirection().multiply(25));
        MTeam t = plugin.teams().getTeam(p);
        if (t == null) return;
        List<Player> team = new ArrayList<>();
        for (UUID u : t.getMembers()) {
            Player m = Bukkit.getPlayer(u);
            if (m != null) team.add(m);
        }
        String group = "ping-" + p.getUniqueId();
        plugin.holograms().remove(group);
        int dist = (int) p.getLocation().distance(at);
        var d = plugin.holograms().spawn(group, at, t.getColor() + "§l⬇ " + p.getName() + "\n§7" + at.getBlockX() + " / " + at.getBlockZ(), 1.8f, team);
        if (d != null) {
            d.setViewRange(12f);
            d.setGlowing(true);
            d.setGlowColorOverride(Color.YELLOW);
        }
        for (Player m : team) {
            m.playSound(m.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.6f);
            Txt.send(m, "§8[§bPing§8] " + t.getColor() + p.getName() + " §7markiert §e" + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ() + " §8(" + dist + "m)");
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.holograms().remove(group), 20L * 15);
    }

    // ===================================================================== Kopfgeld

    private void updateBounty() {
        List<Map.Entry<UUID, Integer>> top = plugin.game().topKillers(2);
        UUID next = null;
        if (!top.isEmpty() && top.get(0).getValue() >= 2 && (top.size() < 2 || top.get(0).getValue() > top.get(1).getValue())
                && plugin.game().isAlive(top.get(0).getKey())) next = top.get(0).getKey();
        if (next == null || next.equals(bounty)) {
            if (bounty != null) {
                Player p = Bukkit.getPlayer(bounty);
                if (p != null && !p.hasPotionEffect(PotionEffectType.GLOWING)) p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 3600, 0, false, false));
            }
            return;
        }
        if (bounty != null) {
            Player old = Bukkit.getPlayer(bounty);
            if (old != null) old.removePotionEffect(PotionEffectType.GLOWING);
        }
        bounty = next;
        Player p = Bukkit.getPlayer(next);
        if (p != null) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 3600, 0, false, false));
            plugin.game().broadcast("§6☠ Kopfgeld §7auf §e" + p.getName() + " §8(" + plugin.game().getKills(next) + " Kills)§7! Wer ihn erledigt, wird belohnt.");
            plugin.game().sound(Sound.ENTITY_EVOKER_PREPARE_WOLOLO, 1f);
        }
    }

    // ===================================================================== Zuschauer-Voting

    private List<Player> spectators() {
        List<Player> l = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) if (!plugin.game().isAlive(p)) l.add(p);
        return l;
    }

    private void tickVote() {
        if (voteOptions != null) {
            if (--voteEndsIn <= 0) finishVote();
            return;
        }
        if (--voteTimer > 0) return;
        voteTimer = 300;
        List<Player> specs = spectators();
        if (specs.isEmpty()) return;
        List<VoteEvent> all = new ArrayList<>(List.of(VoteEvent.values()));
        Collections.shuffle(all);
        voteOptions = all.subList(0, 3);
        votes.clear();
        voteEndsIn = 30;
        for (Player p : specs) {
            Txt.send(p, "§8§m      §r §d§lZuschauer-Voting §8§m      ");
            Txt.send(p, "§7Wähle ein Event für die Lebenden §8(30s)§7:");
            for (int i = 0; i < 3; i++) {
                VoteEvent ev = voteOptions.get(i);
                p.sendMessage(Component.text("  ").append(Txt.c("§8[§a" + (i + 1) + "§8] " + ev.display + " §7– " + ev.description))
                        .clickEvent(ClickEvent.runCommand("/odv vote " + (i + 1)))
                        .hoverEvent(Txt.c("§eKlicken zum Abstimmen")));
            }
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.2f);
        }
    }

    public void vote(Player p, int option) {
        if (voteOptions == null || plugin.game().isAlive(p)) {
            Txt.send(p, s().prefix + "§cGerade läuft kein Voting für dich.");
            return;
        }
        if (option < 1 || option > voteOptions.size()) return;
        votes.put(p.getUniqueId(), option - 1);
        Txt.send(p, s().prefix + "§7Stimme für " + voteOptions.get(option - 1).display + " §7gezählt.");
    }

    private void finishVote() {
        int[] count = new int[voteOptions.size()];
        for (int v : votes.values()) count[v]++;
        int best = 0;
        for (int i = 1; i < count.length; i++) if (count[i] > count[best]) best = i;
        VoteEvent ev = votes.isEmpty() ? voteOptions.get(random.nextInt(voteOptions.size())) : voteOptions.get(best);
        voteOptions = null;
        plugin.game().broadcast("§d§lZuschauer-Event: " + ev.display + " §8(" + (votes.isEmpty() ? "Zufall" : count[best] + " Stimmen") + ")");
        List<Player> alive = new ArrayList<>();
        for (UUID u : plugin.game().getAlive()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) alive.add(p);
        }
        switch (ev) {
            case GLOW -> alive.forEach(p -> p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 30, 0)));
            case SPEED -> alive.forEach(p -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 30, 1)));
            case SUPPLY -> supplyDrop();
            case MOON -> alive.forEach(p -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 20 * 30, 2));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 30, 0));
            });
            case STORM -> alive.forEach(p -> {
                for (int i = 0; i < 3; i++) p.getWorld().strikeLightningEffect(p.getLocation().add(random.nextInt(9) - 4, 0, random.nextInt(9) - 4));
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20 * 8, 0));
            });
            case HUNGER -> alive.forEach(p -> p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 20 * 20, 2)));
            case HEAL -> alive.forEach(p -> p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 10, 0)));
        }
        votes.clear();
    }

    // ===================================================================== Wetten

    public boolean canBet(Player p) {
        return on(Feature.BETTING) && plugin.game().getState().isIngame() && !plugin.game().isAlive(p)
                && !bets.containsKey(p.getUniqueId()) && plugin.game().aliveTeams().size() >= 3;
    }

    public String betBlockedReason(Player p) {
        if (!on(Feature.BETTING)) return "Wetten sind ausgeschaltet.";
        if (!plugin.game().getState().isIngame()) return "Gerade läuft keine Runde.";
        if (plugin.game().isAlive(p)) return "Nur Zuschauer können wetten.";
        if (bets.containsKey(p.getUniqueId())) return "Du hast schon gewettet.";
        return "Zu spät – nur solange noch mind. 3 Teams leben.";
    }

    public void placeBet(Player p, MTeam t) {
        if (!canBet(p)) {
            Txt.send(p, s().prefix + "§c" + betBlockedReason(p));
            return;
        }
        bets.put(p.getUniqueId(), t.getNumber());
        Txt.send(p, s().prefix + "§aWette platziert auf " + (s().isSolo() ? "§f" + t.getName() : t.getDisplayName()) + "§a!");
    }

    // ===================================================================== Heatmap

    private void giveHeatmap() {
        World w = plugin.arena().getWorld();
        Location c = plugin.arena().getCenter();
        if (w == null || c == null) return;
        int size = s().borderInitial;
        int[][] grid = new int[128][128];
        int max = 1;
        for (int[] h : heat) {
            int px = (int) ((h[0] - (c.getX() - size / 2.0)) * 128 / size), pz = (int) ((h[1] - (c.getZ() - size / 2.0)) * 128 / size);
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    int x = px + dx, z = pz + dz;
                    if (x < 0 || z < 0 || x > 127 || z > 127) continue;
                    grid[x][z] += (dx == 0 && dz == 0) ? (h.length > 2 ? 6 : 3) : 1;
                    max = Math.max(max, grid[x][z]);
                }
        }
        final int maxV = max;
        MapView view = Bukkit.createMap(w);
        view.getRenderers().clear();
        view.setCenterX(c.getBlockX());
        view.setCenterZ(c.getBlockZ());
        view.addRenderer(new MapRenderer() {
            boolean done;

            @Override
            public void render(MapView map, MapCanvas canvas, Player player) {
                if (done) return;
                done = true;
                for (int x = 0; x < 128; x++)
                    for (int z = 0; z < 128; z++) {
                        int v = grid[x][z];
                        java.awt.Color col;
                        if (v == 0) col = (x == 0 || z == 0 || x == 127 || z == 127) ? new java.awt.Color(60, 60, 60) : new java.awt.Color(214, 205, 180);
                        else {
                            float f = Math.min(1f, v / (float) maxV);
                            col = new java.awt.Color(255, (int) (220 * (1 - f)), 0);
                        }
                        canvas.setPixelColor(x, z, col);
                    }
                canvas.setPixelColor(64, 64, java.awt.Color.BLUE);
            }
        });
        ItemStack map = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) map.getItemMeta();
        meta.setMapView(view);
        meta.displayName(Txt.item("§6§lKampf-Heatmap"));
        meta.lore(Txt.items(List.of("§7Rot = hier wurde am meisten gekämpft", "§7Mitte = Spawn")));
        map.setItemMeta(meta);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.getInventory().setItem(6, map.clone());
                Txt.send(p, s().prefix + "§6Die Kampf-Heatmap §7liegt in deinem Inventar.");
            }
        }, s().endDelay * 20L + 10L);
    }
}
