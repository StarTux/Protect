package com.winthier.protect;

import com.destroystokyo.paper.MaterialTags;
import com.winthier.generic_events.PlayerCanBuildEvent;
import com.winthier.generic_events.PlayerCanDamageEntityEvent;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
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
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProtectPlugin extends JavaPlugin implements Listener {
    private final List<String> worlds = new ArrayList<>();
    private final Set<Block> farmBlocks = new HashSet<>();
    private final Set<Material> farmMaterials = EnumSet.noneOf(Material.class);
    private static final String META_FARM = "protect.farm";

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
        if (farmBlocks.contains(event.getClickedBlock())) return;
        if (farmBlocks.contains(event.getClickedBlock().getRelative(0, 1, 0))) return;
        if (event.isBlockInHand()) return;
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Material mat = event.getClickedBlock().getType();
            if (Tag.DOORS.isTagged(mat)) return;
            if (Tag.BUTTONS.isTagged(mat)) return;
            if (MaterialTags.FENCE_GATES.isTagged(mat)) return;
            switch (mat) {
            case ENCHANTING_TABLE:
            case ENDER_CHEST:
            case CHEST:
            case TRAPPED_CHEST:
            case CRAFTING_TABLE:
            case LECTERN:
            case BARREL:
                return;
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
        }
        onProtectEvent(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPlayerMove(PlayerMoveEvent event) {
        final Player player = event.getPlayer();
        if (player.isOp()) return;
        if (!worlds.contains(player.getWorld().getName())) return;
        Block block = player.getLocation().getBlock();
        if (farmBlocks.contains(block) || farmBlocks.contains(block.getRelative(0, 1, 0))) {
            // Enter farms
            if (!player.hasMetadata(META_FARM)) {
                player.sendActionBar("Entering Spawn Farm Area");
                if (player.getGameMode() != GameMode.SURVIVAL) {
                    player.setGameMode(GameMode.SURVIVAL);
                }
                setMeta(player, META_FARM, true);
            }
        } else if (player.hasMetadata(META_FARM)) {
            // Leave farms
            player.removeMetadata(META_FARM, this);
            player.sendActionBar("Leaving Spawn Farm Area");
            if (player.getGameMode() != GameMode.ADVENTURE) {
                player.setGameMode(GameMode.ADVENTURE);
            }
        }
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

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPlayerCanBuild(PlayerCanBuildEvent event) {
        if (farmBlocks.contains(event.getBlock())) return;
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
            event.setNumHatches((byte) 0);
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

    // --- Meta Utility

    int getSelectionMeta(Player player, String key) {
        if (!player.hasMetadata(key)) throw new NullPointerException();
        return player.getMetadata(key).get(0).asInt();
    }

    void setMeta(Player player, String key, Object value) {
        player.setMetadata(key, new FixedMetadataValue(this, value));
    }
}
