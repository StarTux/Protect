package com.winthier.protect;

import com.cavetale.core.event.block.PlayerBlockAbilityQuery;
import com.cavetale.core.event.block.PlayerBreakBlockEvent;
import com.cavetale.core.event.entity.PlayerEntityAbilityQuery;
import com.destroystokyo.paper.MaterialTags;
import io.papermc.paper.event.block.PlayerShearBlockEvent;
import java.util.HashMap;
import java.util.Map;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
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
import org.bukkit.event.block.BlockGrowEvent;
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
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProtectPlugin extends JavaPlugin implements Listener {
    private final Map<String, ProtectWorld> worldMap = new HashMap<>();
    public static final String PERM_FARM = "protect.farm";
    public static final String PERM_OVERRIDE = "protect.override";

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        importConfig();
    }

    @Override
    public void onDisable() {
        for (ProtectWorld protectWorld : worldMap.values()) {
            protectWorld.disable();
        }
        worldMap.clear();
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
        for (World world : Bukkit.getWorlds()) {
            loadWorld(world);
        }
    }

    private ProtectWorld loadWorld(World world) {
        ProtectWorld old = worldMap.remove(world.getName());
        if (old != null) old.disable();
        ProtectWorld pworld = new ProtectWorld(this, world);
        if (getConfig().getBoolean("AllWorlds") || getConfig().getStringList("worlds").contains(world.getName())) {
            pworld.setFullyProtected(true);
        }
        worldMap.put(pworld.name, pworld);
        pworld.enable(world);
        return pworld;
    }

    public ProtectWorld getProtectWorld(World world) {
        ProtectWorld result = worldMap.get(world.getName());
        return result != null
            ? result
            : loadWorld(world);
    }

    private boolean onProtectEvent(Player player, @NonNull Block block, Cancellable event) {
        return getProtectWorld(block.getWorld()).onProtectEvent(player, block, event);
    }

    private boolean isHostileMob(Entity entity) {
        switch (entity.getType()) {
        case GHAST:
        case SLIME:
        case PHANTOM:
        case MAGMA_CUBE:
        case ENDER_DRAGON:
        case SHULKER:
        case SHULKER_BULLET:
        case HOGLIN:
            return true;
        default:
            return entity instanceof Monster;
        }
    }

    /**
     * Cancel most interact events in fully protected worlds but allow
     * some of them in farm areas.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onPlayerInteract(PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        final Block block = event.getClickedBlock();
        final ProtectWorld pworld = getProtectWorld(block.getWorld());
        if (event.getAction() == Action.PHYSICAL) {
            // Protect farmland and turtle eggs from trampling but
            // allow anything else which is physical.
            Material mat = block.getType();
            if (mat == Material.FARMLAND || mat == Material.TURTLE_EGG) {
                pworld.onProtectEvent(player, block, event);
            }
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Whitelist certain non-invasive right click actions.
            Material mat = block.getType();
            if (Tag.DOORS.isTagged(mat) || Tag.BUTTONS.isTagged(mat) || MaterialTags.FENCE_GATES.isTagged(mat) || Tag.BEDS.isTagged(mat)) {
                if (pworld.isProtectedArea(block)) {
                    event.setUseInteractedBlock(Event.Result.DENY);
                }
                return;
            }
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
            if (player.hasPermission(PERM_FARM)) {
                if (event.hasItem() && !event.isBlockInHand() && event.getItem().getType() == Material.BONE_MEAL) {
                    if (pworld.canPlant(block)) return;
                } else if (pworld.canPlant(block.getRelative(event.getBlockFace()))) {
                    return;
                }
            }
            // Supply the null event because we do not want this one
            // to get cancelled.
            if (!pworld.onProtectEvent(player, block, null)) {
                event.setUseInteractedBlock(Event.Result.DENY);
            }
        } else if (player.hasPermission(PERM_FARM)) {
            // Whitelist right or left click actions in farming areas.
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                if (pworld.canPlant(block)) return;
            }
        } else {
            // Fall back to the generic event checking.
            if (!pworld.onProtectEvent(event.getPlayer(), block, null)) {
                event.setUseInteractedBlock(Event.Result.DENY);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onBlockPlace(BlockPlaceEvent event) {
        final Player player = event.getPlayer();
        final ProtectWorld pworld = getProtectWorld(event.getBlock().getWorld());
        if (player.hasPermission(PERM_FARM) && pworld.canPlant(event.getBlock(), event.getBlock().getType())) {
            return;
        }
        pworld.onProtectEvent(event.getPlayer(), event.getBlock(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onBlockBreak(BlockBreakEvent event) {
        final Player player = event.getPlayer();
        final ProtectWorld pworld = getProtectWorld(event.getBlock().getWorld());
        if (player.hasPermission(PERM_FARM) && pworld.canHarvest(event.getBlock(), event.getBlock().getType())) {
            return;
        }
        pworld.onProtectEvent(event.getPlayer(), event.getBlock(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onPlayerShearBlock(PlayerShearBlockEvent event) {
        onProtectEvent(event.getPlayer(), event.getBlock(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onBlockDamage(BlockDamageEvent event) {
        final Player player = event.getPlayer();
        final ProtectWorld pworld = getProtectWorld(event.getBlock().getWorld());
        if (player.hasPermission(PERM_FARM) && pworld.canHarvest(event.getBlock(), event.getBlock().getType())) {
            return;
        }
        pworld.onProtectEvent(event.getPlayer(), event.getBlock(), event);
    }

    /**
     * Guard against the Frost Walker enchantment.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onEntityBlockForm(EntityBlockFormEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        onProtectEvent(player, event.getBlock(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        final Entity entity = event.getRightClicked();
        final EntityType entityType = entity.getType();
        if (entityType == EntityType.PIG) return;
        if (entityType == EntityType.BOAT) return;
        onProtectEvent(event.getPlayer(), entity.getLocation().getBlock(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getRightClicked().getType() == EntityType.PIG) return;
        if (isHostileMob(event.getRightClicked())) return;
        onProtectEvent(event.getPlayer(), event.getRightClicked().getLocation().getBlock(), event);
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
        if (isHostileMob(event.getEntity())) return;
        onProtectEvent(player, event.getEntity().getLocation().getBlock(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onVehicleDamage(VehicleDamageEvent event) {
        if (event.getAttacker() == null) return;
        Player player = getPlayerDamager(event.getAttacker());
        if (player == null) return;
        onProtectEvent(player, event.getVehicle().getLocation().getBlock(), event);
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
        if (event.getEntity() instanceof AbstractArrow arrow) {
            if (arrow.getWorld().getName().equals("spawn") && !onProtectEvent(player, arrow.getLocation().getBlock(), null)) {
                arrow.setFireTicks(0);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onEntityCombustByEntity(EntityCombustByEntityEvent event) {
        Player player = getPlayerDamager(event.getCombuster());
        if (player == null) return;
        if (isHostileMob(event.getEntity())) return;
        onProtectEvent(player, event.getEntity().getLocation().getBlock(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onEntityExplode(EntityExplodeEvent event) {
        final Entity entity = event.getEntity();
        final ProtectWorld pworld = getProtectWorld(entity.getWorld());
        if (pworld.isProtected(entity.getLocation())) {
            event.blockList().clear();
            return;
        } else {
            event.blockList().removeIf(pworld::isProtected);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onBlockExplode(BlockExplodeEvent event) {
        final Block block = event.getBlock();
        final ProtectWorld pworld = getProtectWorld(block.getWorld());
        if (pworld.isProtected(block)) {
            event.blockList().clear();
            return;
        } else {
            event.blockList().removeIf(pworld::isProtected);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onBlockIgnite(BlockIgniteEvent event) {
        final Block block = event.getBlock();
        if (getProtectWorld(block.getWorld()).isProtected(block)) {
            switch (event.getCause()) {
            case FLINT_AND_STEEL: return;
            default: break;
            }
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onCauldronLevelChange(CauldronLevelChangeEvent event) {
        final Block block = event.getBlock();
        if (getProtectWorld(block.getWorld()).isProtected(block)) {
            switch (event.getReason()) {
            case EVAPORATE:
            case EXTINGUISH:
                event.setCancelled(true);
            default: break;
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onHangingBreak(HangingBreakEvent event) {
        if (event instanceof HangingBreakByEntityEvent) return;
        final Entity entity = event.getEntity();
        if (getProtectWorld(entity.getWorld()).isProtected(entity.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player player) {
            onProtectEvent(player, event.getEntity().getLocation().getBlock(), event);
        } else {
            final Entity entity = event.getEntity();
            if (!getProtectWorld(entity.getWorld()).isProtected(entity.getLocation())) {
                event.setCancelled(true);
            }
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
        if (player.hasPermission(PERM_OVERRIDE)) return;
        if (!onProtectEvent(player, event.getEgg().getLocation().getBlock(), null)) {
            event.setHatching(false);
            event.setNumHatches((byte) 0);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onEntityChangeBlock(EntityChangeBlockEvent event) {
        final Block block = event.getBlock();
        if (event.getEntity() instanceof Player player) {
            if (block.getType() == Material.BIG_DRIPLEAF && event.getTo() == Material.BIG_DRIPLEAF) {
                return;
            } else {
                onProtectEvent(player, block, event);
            }
        } else if (getProtectWorld(block.getWorld()).isProtected(block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    private void onPlayerTakeLecternBook(PlayerTakeLecternBookEvent event) {
        onProtectEvent(event.getPlayer(), event.getLectern().getBlock(), event);
    }

    @EventHandler(ignoreCancelled = true)
    private void onPlayerBlockAbility(PlayerBlockAbilityQuery query) {
        if (query.getPlayer().hasPermission(ProtectPlugin.PERM_OVERRIDE)) return;
        switch (query.getAction()) {
        case USE: // Buttons: Doors
        case READ: // Lectern
        case OPEN: // Chests
        case INVENTORY:
        case FLY:
            return;
        case BUILD:
            if (query.getPlayer().hasPermission(PERM_FARM)) {
                ProtectWorld pworld = worldMap.get(query.getBlock().getWorld().getName());
                if (pworld != null && pworld.canPlant(query.getBlock())) {
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
        if (query.getPlayer().hasPermission(ProtectPlugin.PERM_OVERRIDE)) return;
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
            if (isHostileMob(query.getEntity())) return;
            onProtectEvent(query.getPlayer(), query.getEntity().getLocation().getBlock(), query);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    private void onBlockGrow(BlockGrowEvent event) {
        String worldName = event.getBlock().getWorld().getName();
        ProtectWorld pworld = worldMap.get(worldName);
        if (pworld != null && pworld.canPlant(event.getBlock(), event.getNewState().getType())) return;
        if (pworld.isFullyProtected()) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    private void onBlockForm(BlockFormEvent event) {
        final Block block = event.getBlock();
        if (getProtectWorld(block.getWorld()).isProtected(block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    private void onBlockFade(BlockFadeEvent event) {
        final Block block = event.getBlock();
        if (getProtectWorld(block.getWorld()).isProtected(block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    private void onBlockFromTo(BlockFromToEvent event) {
        final Block block = event.getBlock();
        if (getProtectWorld(block.getWorld()).isProtected(block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    private void onWorldLoad(WorldLoadEvent event) {
        loadWorld(event.getWorld());
    }

    @EventHandler
    private void onWorldUnload(WorldUnloadEvent event) {
        ProtectWorld pworld = worldMap.remove(event.getWorld().getName());
        if (pworld != null) pworld.disable();
    }
}
