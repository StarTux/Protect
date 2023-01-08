package com.winthier.protect;

import com.cavetale.area.struct.Area;
import com.cavetale.area.struct.AreasFile;
import com.cavetale.core.struct.Vec3i;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Represent a loaded world.  A world is either:
 * - Fully protected (worlds array in config) with optional whitelisted farm areas
 * - Not fully protected with optional protected areas
 */
@Getter
public final class ProtectWorld {
    protected final ProtectPlugin plugin;
    protected final String name;
    @Setter protected boolean fullyProtected;
    protected final boolean nether;
    // Farm stuff
    protected final Map<Material, Set<Vec3i>> crops = new HashMap<>();
    protected final List<Vec3i> tickBlocks = new ArrayList<>();
    protected final Set<Vec3i> canPlantBlocks = new HashSet<>();
    protected int tickBlockIndex = 0;
    private BukkitTask tickTask;
    // Protection stuff
    private final List<Area> protectedAreas = new ArrayList<>();
    private final List<Area> useAreas = new ArrayList<>();

    protected ProtectWorld(final ProtectPlugin plugin, final World world) {
        this.plugin = plugin;
        this.name = world.getName();
        this.nether = world.getEnvironment() == World.Environment.NETHER;
    }

    protected void enable(World world) {
        AreasFile areasFile = AreasFile.load(getWorld(), "Protect");
        if (areasFile == null) return;
        Set<Vec3i> tickBlockSet = new HashSet<>();
        for (Area area : areasFile.find("farm")) {
            if (area.getName() == null) continue;
            final Material material;
            try {
                material = Material.valueOf(area.getName().toUpperCase());
            } catch (IllegalArgumentException iae) {
                plugin.getLogger().severe("[" + name + "] Invalid farm area: " + area);
                continue;
            }
            if (!material.isBlock()) {
                plugin.getLogger().severe("[" + name + "] Not a block: " + area);
                continue;
            }
            crops.computeIfAbsent(material, mat -> new HashSet<>()).addAll(area.enumerate());
            tickBlockSet.addAll(area.enumerate());
            canPlantBlocks.addAll(area.enumerate());
        }
        for (Area area : areasFile.find("protect")) {
            protectedAreas.add(area);
        }
        for (Area area : areasFile.find("use")) {
            useAreas.add(area);
        }
        tickBlocks.addAll(tickBlockSet);
        if (!tickBlocks.isEmpty()) {
            Collections.shuffle(tickBlocks);
            tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
            plugin.getLogger().info("[" + name + "] World tick task enabled"
                                    + " tickBlocks:" + tickBlocks.size());
        }
        if (fullyProtected) {
            plugin.getLogger().info("[" + name + "] Fully protected");
        }
    }

    protected void disable() {
        if (tickTask != null) {
            tickTask.cancel();
            return;
        }
    }

    public World getWorld() {
        return Bukkit.getWorld(name);
    }

    private void tick() {
        World world = getWorld();
        if (world == null) {
            plugin.getLogger().severe("[" + name + "] World not found!");
            disable();
            return;
        }
        Vec3i vec = tickBlocks.get(tickBlockIndex);
        if (!world.isChunkLoaded(vec.x >> 4, vec.z >> 4)) return;
        Block block = vec.toBlock(world);
        if (block.getBlockData().isRandomlyTicked()) {
            block.randomTick();
        } else if (block.isEmpty() && block.getRelative(0, -1, 0).getType() == Material.FARMLAND
                   && ThreadLocalRandom.current().nextInt(10) == 0) {
            for (Map.Entry<Material, Set<Vec3i>> entry : crops.entrySet()) {
                if (entry.getValue().contains(vec)) {
                    Material mat = entry.getKey();
                    if (mat.createBlockData().isRandomlyTicked()) {
                        block.setType(mat);
                    }
                    break;
                }
            }
        }
        tickBlockIndex += 1;
        if (tickBlockIndex >= tickBlocks.size()) {
            Collections.shuffle(tickBlocks);
            tickBlockIndex = 0;
        }
    }

    protected boolean canPlant(Block block, Material material) {
        Set<Vec3i> cropSet = crops.get(material);
        if (cropSet != null && cropSet.contains(Vec3i.of(block))) {
            return true;
        }
        return false;
    }

    protected boolean canHarvest(Block block, Material material) {
        Set<Vec3i> cropSet = crops.get(material);
        if (cropSet != null && cropSet.contains(Vec3i.of(block))) {
            if (block.getBlockData() instanceof Ageable age) {
                return age.getAge() >= age.getMaximumAge();
            } else {
                return true;
            }
        }
        return false;
    }

    protected boolean canPlant(Block block) {
        return canPlantBlocks.contains(Vec3i.of(block));
    }

    /**
     * Generic player event handler.
     * @param player the player
     * @param block the block if available or null
     * @param event the event if it should be automatically cancelled or null
     * @return false if player is denied, true otherwise.
     */
    public boolean onProtectEvent(Player player, @NonNull Block block, Cancellable event) {
        // Respect some general player overrides.
        if (player.hasPermission(ProtectPlugin.PERM_OVERRIDE)) return true;
        // Protect the nether ceiling
        if (nether) {
            if (block != null && block.getY() >= 127) {
                if (event != null) event.setCancelled(true);
                return false;
            }
        }
        for (Area area : useAreas) {
            if (area.contains(block)) return true;
        }
        // Full or partial protection
        if (fullyProtected || isProtected(block)) {
            if (event != null) event.setCancelled(true);
            return false;
        }
        return true;
    }

    public boolean isProtected(Block block) {
        if (fullyProtected) {
            return true;
        } else {
            for (Area area : protectedAreas) {
                if (area.contains(block)) return true;
            }
            return false;
        }
    }

    public boolean isProtected(Location location) {
        return isProtected(location.getBlock());
    }
}
