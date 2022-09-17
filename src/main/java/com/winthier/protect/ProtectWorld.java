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
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;

@RequiredArgsConstructor
public final class ProtectWorld {
    protected final ProtectPlugin plugin;
    protected final String name;
    protected final Map<Material, Set<Vec3i>> crops = new HashMap<>();
    protected final List<Vec3i> tickBlocks = new ArrayList<>();
    protected final Set<Vec3i> canBuildBlocks = new HashSet<>();
    protected int tickBlockIndex = 0;
    private BukkitTask tickTask;

    protected void enable() {
        World world = getWorld();
        if (world == null) {
            plugin.getLogger().warning("[" + name + "] World not found!");
            return;
        }
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
            if (material.createBlockData().isRandomlyTicked()) {
                tickBlockSet.addAll(area.enumerate());
            }
            canBuildBlocks.addAll(area.enumerate());
        }
        tickBlocks.addAll(tickBlockSet);
        if (!tickBlocks.isEmpty()) {
            Collections.shuffle(tickBlocks);
            tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
            plugin.getLogger().info("[" + name + "] World tick task enabled"
                                    + " tickBlocks:" + tickBlocks.size());
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
        }
        tickBlockIndex += 1;
        if (tickBlockIndex >= tickBlocks.size()) {
            tickBlockIndex = 0;
        }
    }

    protected boolean canBuild(Block block, Material material) {
        Set<Vec3i> cropSet = crops.get(material);
        if (cropSet != null && cropSet.contains(Vec3i.of(block))) {
            return true;
        }
        return false;
    }

    protected boolean canBuild(Block block) {
        return canBuildBlocks.contains(Vec3i.of(block));
    }
}
