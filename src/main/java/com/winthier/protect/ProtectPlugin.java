package com.winthier.protect;

import com.winthier.generic_events.PlayerCanBuildEvent;
import com.winthier.generic_events.PlayerCanDamageEntityEvent;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProtectPlugin extends JavaPlugin implements Listener {
    private final List<String> worlds = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        importConfig();
        getServer().getPluginManager().registerEvents(this, this);
    }

    void importConfig() {
        reloadConfig();
        worlds.clear();
        worlds.addAll(getConfig().getStringList("worlds"));
        System.out.println("Protecting worlds: " + worlds);
    }

    public void onProtectEvent(Player player, Cancellable event) {
        if (player.isOp()) return;
        if (player.hasPermission("protect.override")) return;
        if (worlds.contains(player.getWorld().getName())) {
            event.setCancelled(true);
        } else if (player.getWorld().getEnvironment() == World.Environment.NETHER) {
            if (player.getLocation().getBlockY() >= 128) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) {
            if (event.getClickedBlock().getType() == Material.FARMLAND) {
                event.setCancelled(true);
                return;
            } else {
                return;
            }
        }
        if (event.isBlockInHand()) return;
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            switch (event.getClickedBlock().getType()) {
            case ENCHANTING_TABLE:
            case ENDER_CHEST:
            case CHEST:
            case TRAPPED_CHEST:
            case CRAFTING_TABLE:
            case ACACIA_DOOR:
            case ACACIA_TRAPDOOR:
            case BIRCH_DOOR:
            case BIRCH_TRAPDOOR:
            case DARK_OAK_DOOR:
            case DARK_OAK_TRAPDOOR:
            case IRON_DOOR:
            case IRON_TRAPDOOR:
            case JUNGLE_DOOR:
            case JUNGLE_TRAPDOOR:
            case OAK_DOOR:
            case OAK_TRAPDOOR:
            case SPRUCE_DOOR:
            case SPRUCE_TRAPDOOR:
            case ACACIA_BUTTON:
            case BIRCH_BUTTON:
            case DARK_OAK_BUTTON:
            case JUNGLE_BUTTON:
            case OAK_BUTTON:
            case SPRUCE_BUTTON:
            case STONE_BUTTON:
            case ACACIA_FENCE_GATE:
            case BIRCH_FENCE_GATE:
            case DARK_OAK_FENCE_GATE:
            case JUNGLE_FENCE_GATE:
            case OAK_FENCE_GATE:
            case SPRUCE_FENCE_GATE:
                return;
            default:
                break;
            }
        }
        onProtectEvent(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onBlockPlace(BlockPlaceEvent event) {
        onProtectEvent(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onBlockBreak(BlockBreakEvent event) {
        onProtectEvent(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onBlockDamage(BlockDamageEvent event) {
        onProtectEvent(event.getPlayer(), event);
    }

    // Frost Walker
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onEntityBlockForm(EntityBlockFormEvent event) {
        Player player = event.getEntity() instanceof Player ? (Player)event.getEntity() : null;
        if (player == null) return;
        onProtectEvent(player, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        onProtectEvent(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        onProtectEvent(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Player player;
        if (event.getDamager() instanceof Player) {
            player = (Player)event.getDamager();
        } else if (event.getDamager() instanceof Projectile && ((Projectile)event.getDamager()).getShooter() instanceof Player) {
            player = (Player)((Projectile)event.getDamager()).getShooter();
        } else {
            return;
        }
        onProtectEvent(player, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onEntityCombustByEntity(EntityCombustByEntityEvent event) {
        Player player;
        if (event.getCombuster() instanceof Player) {
            player = (Player)event.getCombuster();
        } else if (event.getCombuster() instanceof Projectile && ((Projectile)event.getCombuster()).getShooter() instanceof Player) {
            player = (Player)((Projectile)event.getCombuster()).getShooter();
        } else {
            return;
        }
        onProtectEvent(player, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!worlds.contains(event.getEntity().getWorld().getName())) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!worlds.contains(event.getBlock().getWorld().getName())) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player) {
            onProtectEvent((Player)event.getRemover(), event);
        } else {
            if (!worlds.contains(event.getEntity().getWorld().getName())) return;
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onHangingPlace(HangingPlaceEvent event) {
        onProtectEvent(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        onProtectEvent(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPlayerBucketFill(PlayerBucketFillEvent event) {
        onProtectEvent(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPlayerCanBuild(PlayerCanBuildEvent event) {
        onProtectEvent(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPlayerCanDamageEntity(PlayerCanDamageEntityEvent event) {
        onProtectEvent(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPlayerEggThrow(PlayerEggThrowEvent event) {
        final Player player = event.getPlayer();
        if (player.isOp()) return;
        if (player.hasPermission("protect.override")) return;
        if (worlds.contains(player.getWorld().getName())) {
            event.setHatching(false);
            event.setNumHatches((byte)0);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getEntity().getType() == EntityType.PHANTOM
            && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            event.setCancelled(true);
        }
    }
}
