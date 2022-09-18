package com.winthier.protect;

import com.cavetale.core.event.block.PlayerBlockAbilityQuery;
import com.cavetale.core.event.block.PlayerBreakBlockEvent;
import com.cavetale.core.event.entity.PlayerEntityAbilityQuery;
import com.destroystokyo.paper.MaterialTags;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProtectPlugin extends JavaPlugin implements Listener {
    private final List<String> worlds = new ArrayList<>();
    private final Map<String, ProtectWorld> worldMap = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        importConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && args[0].equals("reload")) {
            importConfig();
            sender.sendMessage("Config reloaded");
            return true;
        }
        return false;
    }

    private void importConfig() {
        reloadConfig();
        worlds.clear();
        worlds.addAll(getConfig().getStringList("worlds"));
        for (String world : worlds) {
            ProtectWorld pworld = new ProtectWorld(this, world);
            worldMap.put(world, pworld);
            pworld.enable();
        }
    }

    /**
     * Generic player event handler.
     * @param player the player
     * @param block the block if available or null
     * @param event the event if it should be automatically cancelled or null
     * @return false if player is denied, true otherwise.
     */
    public boolean onProtectEvent(Player player, Block block, Cancellable event) {
        if (player.isOp()) return true;
        if (player.hasPermission("protect.override")) return true;
        if (worlds.contains(player.getWorld().getName())) {
            if (event != null) event.setCancelled(true);
            return false;
        } else if (player.getWorld().getEnvironment() == World.Environment.NETHER) {
            if (block != null && block.getY() >= 127) {
                if (event != null) event.setCancelled(true);
                return false;
            }
        }
        return true;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onPlayerInteract(PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        if (!worlds.contains(player.getWorld().getName())) return;
        Block block = event.getClickedBlock();
        if (event.getAction() == Action.PHYSICAL) {
            Material mat = block.getType();
            if (mat == Material.FARMLAND || mat == Material.TURTLE_EGG) {
                event.setCancelled(true);
                return;
            } else {
                return;
            }
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Material mat = block.getType();
            if (Tag.DOORS.isTagged(mat)) return;
            if (Tag.BUTTONS.isTagged(mat)) return;
            if (MaterialTags.FENCE_GATES.isTagged(mat)) return;
            switch (mat) {
            case ENCHANTING_TABLE:
            case ENDER_CHEST:
            case CRAFTING_TABLE:
            case LECTERN:
                return;
            case CHEST:
            case TRAPPED_CHEST:
            case BARREL:
                // Temp solutions to protect containers outside spawn
                if (block.getWorld().getName().equals("spawn")) return;
                break;
            default: break;
            }
            if (player.hasPermission("protect.farm")) {
                if (event.hasItem() && !event.isBlockInHand() && event.getItem().getType() == Material.BONE_MEAL) {
                    ProtectWorld pworld = worldMap.get(block.getWorld().getName());
                    if (pworld != null && pworld.canBuild(block)) {
                        return;
                    }
                }
                ProtectWorld pworld = worldMap.get(block.getWorld().getName());
                if (pworld != null && pworld.canBuild(block.getRelative(event.getBlockFace()))) {
                    return;
                }
            }
            if (!onProtectEvent(player, block, null)) {
                event.setUseInteractedBlock(Event.Result.DENY);
            }
            return;
        }
        if (player.hasPermission("protect.farm")) {
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                ProtectWorld pworld = worldMap.get(block.getWorld().getName());
                if (pworld != null && pworld.canBuild(block)) {
                    return;
                }
            }
        }
        onProtectEvent(event.getPlayer(), block, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onBlockPlace(BlockPlaceEvent event) {
        final Player player = event.getPlayer();
        if (player.hasPermission("protect.farm")) {
            ProtectWorld pworld = worldMap.get(event.getBlock().getWorld().getName());
            if (pworld != null && pworld.canBuild(event.getBlock(), event.getBlock().getType())) return;
        }
        onProtectEvent(event.getPlayer(), event.getBlock(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onBlockBreak(BlockBreakEvent event) {
        final Player player = event.getPlayer();
        if (player.hasPermission("protect.farm")) {
            ProtectWorld pworld = worldMap.get(event.getBlock().getWorld().getName());
            if (pworld != null && pworld.canBuild(event.getBlock(), event.getBlock().getType())) return;
        }
        onProtectEvent(event.getPlayer(), event.getBlock(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onBlockDamage(BlockDamageEvent event) {
        final Player player = event.getPlayer();
        if (player.hasPermission("protect.farm")) {
            ProtectWorld pworld = worldMap.get(event.getBlock().getWorld().getName());
            if (pworld != null && pworld.canBuild(event.getBlock(), event.getBlock().getType())) return;
        }
        onProtectEvent(event.getPlayer(), event.getBlock(), event);
    }

    // Frost Walker
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onEntityBlockForm(EntityBlockFormEvent event) {
        Player player = event.getEntity() instanceof Player ? (Player) event.getEntity() : null;
        if (player == null) return;
        onProtectEvent(player, event.getBlock(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked().getType() == EntityType.PIG) return;
        if (event.getRightClicked().getType() == EntityType.BOAT) return;
        onProtectEvent(event.getPlayer(), null, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getRightClicked().getType() == EntityType.PIG) return;
        onProtectEvent(event.getPlayer(), null, event);
    }

    private Player getPlayerDamager(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof Projectile) {
            Projectile proj = (Projectile) damager;
            if (!(proj.getShooter() instanceof Player)) return null;
            return (Player) proj.getShooter();
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Player player = getPlayerDamager(event.getDamager());
        if (player == null) return;
        onProtectEvent(player, null, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onVehicleDamage(VehicleDamageEvent event) {
        if (event.getAttacker() == null) return;
        Player player = getPlayerDamager(event.getAttacker());
        if (player == null) return;
        onProtectEvent(player, null, event);
    }

    /**
     * We extinguish launched arrows. Flame arrows cause a visual
     * flame effect on the client side which we cannot seem to cancel
     * otherwise (including ProjectileHit and Collide).
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onProjectileLaunch(ProjectileLaunchEvent event) {
        Player player = getPlayerDamager(event.getEntity());
        if (player == null) return;
        if (!onProtectEvent(player, null, null)) {
            if (event.getEntity() instanceof AbstractArrow) {
                AbstractArrow arrow = (AbstractArrow) event.getEntity();
                arrow.setFireTicks(0);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onEntityCombustByEntity(EntityCombustByEntityEvent event) {
        Player player = getPlayerDamager(event.getCombuster());
        if (player == null) return;
        onProtectEvent(player, null, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onEntityExplode(EntityExplodeEvent event) {
        if (!worlds.contains(event.getEntity().getWorld().getName())) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onBlockExplode(BlockExplodeEvent event) {
        if (!worlds.contains(event.getBlock().getWorld().getName())) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onBlockIgnite(BlockIgniteEvent event) {
        if (!worlds.contains(event.getBlock().getWorld().getName())) return;
        switch (event.getCause()) {
        case FLINT_AND_STEEL: return;
        default: break;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onCauldronLevelChange(CauldronLevelChangeEvent event) {
        if (!worlds.contains(event.getBlock().getWorld().getName())) return;
        switch (event.getReason()) {
        case EVAPORATE:
        case EXTINGUISH:
            event.setCancelled(true);
        default: break;
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onHangingBreak(HangingBreakEvent event) {
        if (event instanceof HangingBreakByEntityEvent) return;
        if (!worlds.contains(event.getEntity().getWorld().getName())) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player) {
            onProtectEvent((Player) event.getRemover(), null, event);
        } else {
            if (!worlds.contains(event.getEntity().getWorld().getName())) return;
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onHangingPlace(HangingPlaceEvent event) {
        onProtectEvent(event.getPlayer(), event.getBlock(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        onProtectEvent(event.getPlayer(), event.getBlock(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onPlayerBucketFill(PlayerBucketFillEvent event) {
        onProtectEvent(event.getPlayer(), event.getBlock(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    private void onPlayerBreakBlock(PlayerBreakBlockEvent event) {
        onProtectEvent(event.getPlayer(), event.getBlock(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onPlayerEggThrow(PlayerEggThrowEvent event) {
        final Player player = event.getPlayer();
        if (player.isOp()) return;
        if (player.hasPermission("protect.override")) return;
        if (worlds.contains(player.getWorld().getName())) {
            event.setHatching(false);
            event.setNumHatches((byte) 0);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (worlds.contains(event.getBlock().getWorld().getName())) {
            if (event.getEntity() instanceof Player
                && event.getBlock().getType() == Material.BIG_DRIPLEAF
                && event.getTo() == Material.BIG_DRIPLEAF) {
                return;
            }
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onPlayerFish(PlayerFishEvent event) {
        if (event.getCaught() == null) return;
        onProtectEvent(event.getPlayer(), null, event);
    }

    @EventHandler
    private void onPlayerTakeLecternBook(PlayerTakeLecternBookEvent event) {
        onProtectEvent(event.getPlayer(), null, event);
    }

    @EventHandler(ignoreCancelled = true)
    private void onPlayerBlockAbility(PlayerBlockAbilityQuery query) {
        switch (query.getAction()) {
        case USE: // Buttons: Doors
        case READ: // Lectern
        case OPEN: // Chests
        case INVENTORY:
        case FLY:
            return;
        case BUILD:
            if (query.getPlayer().hasPermission("protect.farm")) {
                ProtectWorld pworld = worldMap.get(query.getBlock().getWorld().getName());
                if (pworld != null && pworld.canBuild(query.getBlock())) {
                    return;
                }
            }
        case PLACE_ENTITY:
        case SPAWN_MOB: // PocketMob
        default:
            onProtectEvent(query.getPlayer(), query.getBlock(), query);
        }
    }

    @EventHandler(ignoreCancelled = true)
    private void onPlayerEntityAbility(PlayerEntityAbilityQuery query) {
        switch (query.getAction()) {
        case MOUNT:
        case DISMOUNT:
        case SIT:
        case SHEAR:
        case FEED:
        case BREED:
        case LEASH:
        case PICKUP:
        case INVENTORY:
        case DAMAGE:
        case POTION:
        case CATCH:
        case OPEN:
        case MOVE:
        case PLACE:
        case GIMMICK:
        default:
            onProtectEvent(query.getPlayer(), null, query);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    private void onBlockForm(BlockFormEvent event) {
        String worldName = event.getBlock().getWorld().getName();
        if (worlds.contains(worldName)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    private void onBlockFade(BlockFadeEvent event) {
        String worldName = event.getBlock().getWorld().getName();
        if (worlds.contains(worldName)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    private void onBlockFromTo(BlockFromToEvent event) {
        String worldName = event.getBlock().getWorld().getName();
        if (worlds.contains(worldName)) event.setCancelled(true);
    }
}
