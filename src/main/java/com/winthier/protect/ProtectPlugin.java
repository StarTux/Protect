package com.winthier.protect;

import com.cavetale.core.event.block.PlayerBlockAbilityQuery;
import com.cavetale.core.event.block.PlayerBreakBlockEvent;
import com.cavetale.core.event.entity.PlayerEntityAbilityQuery;
import com.destroystokyo.paper.MaterialTags;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProtectPlugin extends JavaPlugin implements Listener {
    private final List<String> worlds = new ArrayList<>();
    private final Set<Block> farmBlocks = new HashSet<>();
    private final Set<Material> farmMaterials = EnumSet.noneOf(Material.class);

    @Override
    public void onEnable() {
        saveDefaultConfig();
        importConfig();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String alias, String[] args) {
        if (args.length == 1 && args[0].equals("reload")) {
            importConfig();
            sender.sendMessage("Config reloaded");
            return true;
        } else if (args.length == 1
                   && (args[0].equals("addfarmblocks")
                       || args[0].equals("removefarmblocks"))) {
            if (!(sender instanceof Player)) return false;
            Player player = (Player) sender;
            boolean add = args[0].startsWith("add");
            int ax;
            int ay;
            int az;
            int bx;
            int by;
            int bz;
            try {
                ax = getSelectionMeta(player, "SelectionAX");
                ay = getSelectionMeta(player, "SelectionAY");
                az = getSelectionMeta(player, "SelectionAZ");
                bx = getSelectionMeta(player, "SelectionBX");
                by = getSelectionMeta(player, "SelectionBY");
                bz = getSelectionMeta(player, "SelectionBZ");
            } catch (NullPointerException npe) {
                sender.sendMessage("No selection made.");
                return true;
            }
            World world = getServer().getWorld(worlds.get(0));
            int count = 0;
            for (int y = Math.min(ay, by); y <= Math.max(ay, by); y += 1) {
                for (int z = Math.min(az, bz); z <= Math.max(az, bz); z += 1) {
                    for (int x = Math.min(ax, bx); x <= Math.max(ax, bx); x += 1) {
                        Block block = world.getBlockAt(x, y, z);
                        if (add) {
                            farmBlocks.add(block);
                            count += 1;
                        } else {
                            if (farmBlocks.remove(block)) count += 1;
                        }
                    }
                }
            }
            saveFarmBlocks();
            if (add) {
                player.sendMessage("" + count + " farm blocks added.");
            } else {
                player.sendMessage("" + count + " farm blocks removed.");
            }
            return true;
        }
        return false;
    }

    void importConfig() {
        reloadConfig();
        worlds.clear();
        worlds.addAll(getConfig().getStringList("worlds"));
        farmMaterials.clear();
        for (String key: getConfig().getStringList("farm-materials")) {
            try {
                Material mat = Material.valueOf(key.toUpperCase());
                farmMaterials.add(mat);
            } catch (IllegalArgumentException iae) {
                iae.printStackTrace();
            }
        }
        farmBlocks.clear();
        if (worlds.isEmpty()) return;
        World world = getServer().getWorld(worlds.get(0));
        if (world == null) return;
        Iterator<Integer> is = getConfig().getIntegerList("farm-blocks").iterator();
        while (is.hasNext()) {
            int x = is.next();
            int y = is.next();
            int z = is.next();
            farmBlocks.add(world.getBlockAt(x, y, z));
        }
        getLogger().info("Protecting worlds: " + worlds + ", "
                         + farmBlocks.size() + " farm blocks.");
    }

    void saveFarmBlocks() {
        List<Integer> ls = new ArrayList<>();
        for (Block block: farmBlocks) {
            ls.add(block.getX());
            ls.add(block.getY());
            ls.add(block.getZ());
        }
        getConfig().set("farm-blocks", ls);
        saveConfig();
    }

    /**
     * Generic player event handler.
     * @return false if player is denied, true otherwise.
     */
    public boolean onProtectEvent(Player player, Cancellable event) {
        if (player.isOp()) return true;
        if (player.hasPermission("protect.override")) return true;
        if (worlds.contains(player.getWorld().getName())) {
            if (event != null) event.setCancelled(true);
            return false;
        } else if (player.getWorld().getEnvironment() == World.Environment.NETHER) {
            if (player.getLocation().getBlockY() >= 128) {
                if (event != null) event.setCancelled(true);
                return false;
            }
        }
        return true;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!worlds.contains(event.getPlayer().getWorld().getName())) return;
        if (event.getAction() == Action.PHYSICAL) {
            Material mat = event.getClickedBlock().getType();
            if (mat == Material.FARMLAND || mat == Material.TURTLE_EGG) {
                event.setCancelled(true);
                return;
            } else {
                return;
            }
        }
        if (farmBlocks.contains(event.getClickedBlock())) return;
        if (farmBlocks.contains(event.getClickedBlock().getRelative(0, 1, 0))) return;
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
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
            ItemStack item = event.getItem();
            if (item != null) {
                switch (item.getType()) {
                case FIREWORK_ROCKET:
                    return;
                default:
                    break;
                }
            }
            if (!onProtectEvent(event.getPlayer(), null)) {
                event.setUseInteractedBlock(Event.Result.DENY);
            }
            return;
        }
        onProtectEvent(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (farmBlocks.contains(event.getBlock())
            && farmMaterials.contains(event.getBlock().getType())) {
            return;
        }
        onProtectEvent(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onBlockBreak(BlockBreakEvent event) {
        if (farmBlocks.contains(event.getBlock())
            && farmMaterials.contains(event.getBlock().getType())) {
            return;
        }
        onProtectEvent(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onBlockDamage(BlockDamageEvent event) {
        if (farmBlocks.contains(event.getBlock())) return;
        onProtectEvent(event.getPlayer(), event);
    }

    // Frost Walker
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onEntityBlockForm(EntityBlockFormEvent event) {
        Player player = event.getEntity() instanceof Player ? (Player) event.getEntity() : null;
        if (player == null) return;
        onProtectEvent(player, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked().getType() == EntityType.PIG) return;
        onProtectEvent(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getRightClicked().getType() == EntityType.PIG) return;
        onProtectEvent(event.getPlayer(), event);
    }

    Player getPlayerDamager(Entity damager) {
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
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Player player = getPlayerDamager(event.getDamager());
        if (player == null) return;
        onProtectEvent(player, event);
    }

    /**
     * We extinguish launched arrows. Flame arrows cause a visual
     * flame effect on the client side which we cannot seem to cancel
     * otherwise (including ProjectileHit and Collide).
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Player player = getPlayerDamager(event.getEntity());
        if (player == null) return;
        if (!onProtectEvent(player, null)) {
            if (event.getEntity() instanceof AbstractArrow) {
                AbstractArrow arrow = (AbstractArrow) event.getEntity();
                arrow.setFireTicks(0);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onEntityCombustByEntity(EntityCombustByEntityEvent event) {
        Player player = getPlayerDamager(event.getCombuster());
        if (player == null) return;
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
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (!worlds.contains(event.getBlock().getWorld().getName())) return;
        switch (event.getCause()) {
        case FLINT_AND_STEEL: return;
        default: break;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onCauldronLevelChange(CauldronLevelChangeEvent event) {
        if (!worlds.contains(event.getBlock().getWorld().getName())) return;
        switch (event.getReason()) {
        case EVAPORATE:
        case EXTINGUISH:
            event.setCancelled(true);
        default: break;
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onHangingBreak(HangingBreakEvent event) {
        if (event instanceof HangingBreakByEntityEvent) return;
        if (!worlds.contains(event.getEntity().getWorld().getName())) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player) {
            onProtectEvent((Player) event.getRemover(), event);
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

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerBreakBlock(PlayerBreakBlockEvent event) {
        if (farmBlocks.contains(event.getBlock())) return;
        onProtectEvent(event.getPlayer(), event);
    }

    // @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    // public void onPlayerCanDamageEntity(PlayerCanDamageEntityEvent event) {
    //     onProtectEvent(event.getPlayer(), event);
    // }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPlayerEggThrow(PlayerEggThrowEvent event) {
        final Player player = event.getPlayer();
        if (player.isOp()) return;
        if (player.hasPermission("protect.override")) return;
        if (worlds.contains(player.getWorld().getName())) {
            event.setHatching(false);
            event.setNumHatches((byte) 0);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (worlds.contains(event.getBlock().getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getCaught() == null) return;
        onProtectEvent(event.getPlayer(), event);
    }

    @EventHandler
    public void onPlayerTakeLecternBook(PlayerTakeLecternBookEvent event) {
        onProtectEvent(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true)
    void onPlayerBlockAbility(PlayerBlockAbilityQuery query) {
        switch (query.getAction()) {
        case USE: // Buttons: Doors
        case READ: // Lectern
        case OPEN: // Chests
        case INVENTORY:
        case FLY:
            return;
        case BUILD:
            if (farmBlocks.contains(query.getBlock()) && farmMaterials.contains(query.getBlock().getType())) {
                return;
            }
        case PLACE_ENTITY:
        case SPAWN_MOB: // PocketMob
        default:
            onProtectEvent(query.getPlayer(), query);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onPlayerEntityAbility(PlayerEntityAbilityQuery query) {
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
            onProtectEvent(query.getPlayer(), query);
        }
    }

    // --- Meta Utility

    int getSelectionMeta(Player player, String key) {
        if (!player.hasMetadata(key)) throw new NullPointerException();
        return player.getMetadata(key).get(0).asInt();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    void onBlockForm(BlockFormEvent event) {
        String worldName = event.getBlock().getWorld().getName();
        if (worldName.equals("spawn")) return;
        if (!worlds.contains(worldName)) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    void onBlockFromTo(BlockFromToEvent event) {
        String worldName = event.getBlock().getWorld().getName();
        if (worldName.equals("spawn")) return;
        if (!worlds.contains(worldName)) return;
        event.setCancelled(true);
    }
}
