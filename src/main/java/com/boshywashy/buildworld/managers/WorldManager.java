package com.boshywashy.buildworld.managers;

import com.boshywashy.buildworld.BuildWorld;
import com.boshywashy.buildworld.utils.MessageUtils;
import com.boshywashy.buildworld.world.VoidWorldGenerator;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WorldManager {
    private final BuildWorld plugin;
    private BukkitTask unloadTask;
    private BukkitTask inactiveCheckTask;
    private final Map<String, Long> emptySinceMap = new HashMap<>();

    public WorldManager(BuildWorld plugin) {
        this.plugin = plugin;
        startAutoUnloadTask();
        startInactiveCheckTask();
    }

    public void startInactiveCheckTask() {
        stopInactiveCheckTask();
        // Run check every 30 seconds
        inactiveCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkInactiveWorlds, 600L, 600L);
    }

    public void stopInactiveCheckTask() {
        if (inactiveCheckTask != null) {
            inactiveCheckTask.cancel();
            inactiveCheckTask = null;
        }
    }

    private void checkInactiveWorlds() {
        int inactiveMinutes = plugin.getConfigManager().getInactiveMinutes();
        long inactiveMillis = inactiveMinutes * 60 * 1000L;
        long now = System.currentTimeMillis();

        String spawnWorld = plugin.getDatabaseManager().getSpawnWorld();

        for (String worldName : plugin.getDatabaseManager().getActiveWorlds()) {
            // Spawn world cannot go inactive
            if (spawnWorld != null && worldName.equalsIgnoreCase(spawnWorld)) {
                emptySinceMap.remove(worldName);
                continue;
            }

            World world = Bukkit.getWorld(worldName);
            boolean hasPlayers = world != null && !world.getPlayers().isEmpty();

            if (!hasPlayers) {
                emptySinceMap.putIfAbsent(worldName, now);
                long emptyDuration = now - emptySinceMap.get(worldName);
                if (emptyDuration >= inactiveMillis) {
                    emptySinceMap.remove(worldName);
                    moveToInactive(worldName, null);
                }
            } else {
                emptySinceMap.remove(worldName);
            }
        }
    }

    public long getTimeUntilInactiveSeconds(String worldName) {
        if (!plugin.getDatabaseManager().isBuildWorld(worldName)) return -1;
        if (plugin.getDatabaseManager().isInactive(worldName)) return 0;

        String spawnWorld = plugin.getDatabaseManager().getSpawnWorld();
        if (spawnWorld != null && worldName.equalsIgnoreCase(spawnWorld)) {
            return -1; // Spawn world never goes inactive
        }

        World world = Bukkit.getWorld(worldName);
        if (world != null && !world.getPlayers().isEmpty()) {
            return -1;
        }

        long inactiveMillis = plugin.getConfigManager().getInactiveMinutes() * 60 * 1000L;
        Long emptySince = emptySinceMap.get(worldName);
        if (emptySince == null) {
            emptySinceMap.put(worldName, System.currentTimeMillis());
            return inactiveMillis / 1000L;
        }

        long elapsed = System.currentTimeMillis() - emptySince;
        long remaining = inactiveMillis - elapsed;
        return Math.max(0, remaining / 1000L);
    }

    public void moveToInactive(String worldName, Runnable callback) {
        String spawnWorld = plugin.getDatabaseManager().getSpawnWorld();
        if (spawnWorld != null && worldName.equalsIgnoreCase(spawnWorld)) {
            plugin.getLogger().warning("[WorldManager] Attempted to set spawn world (" + worldName + ") as inactive! Action cancelled.");
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            if (!world.getPlayers().isEmpty()) {
                Location serverSpawn = plugin.getDatabaseManager().getSpawnLocation();
                if (serverSpawn == null) serverSpawn = Bukkit.getWorlds().get(0).getSpawnLocation();
                for (Player p : world.getPlayers()) {
                    p.teleport(serverSpawn);
                    p.sendMessage(MessageUtils.colorize("&cThe world you were in is going inactive."));
                }
            }
            world.save();
            Bukkit.getScheduler().runTask(plugin, () -> {
                boolean unloaded = Bukkit.unloadWorld(world, true);
                if (!unloaded) return;

                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        File source = new File(Bukkit.getWorldContainer(), worldName);
                        File destDir = new File(plugin.getDataFolder(), "inactive");
                        if (!destDir.exists()) destDir.mkdirs();
                        File dest = new File(destDir, worldName);
                        if (dest.exists()) {
                            deleteDirectory(dest);
                        }

                        copyAndDeleteDirectory(source, dest);

                        plugin.getDatabaseManager().setInactive(worldName, true);
                        plugin.getLogger().info("[WorldManager] World " + worldName + " moved to inactive folder.");
                        if (callback != null) {
                            Bukkit.getScheduler().runTask(plugin, callback);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            });
        } else {
            File source = new File(Bukkit.getWorldContainer(), worldName);
            if (source.exists()) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        File destDir = new File(plugin.getDataFolder(), "inactive");
                        if (!destDir.exists()) destDir.mkdirs();
                        File dest = new File(destDir, worldName);
                        if (dest.exists()) {
                            deleteDirectory(dest);
                        }
                        copyAndDeleteDirectory(source, dest);
                        plugin.getDatabaseManager().setInactive(worldName, true);
                        plugin.getLogger().info("[WorldManager] World " + worldName + " moved to inactive folder.");
                        if (callback != null) {
                            Bukkit.getScheduler().runTask(plugin, callback);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        }
    }

    public void activateWorld(String worldName, Runnable onSuccess) {
        if (!plugin.getDatabaseManager().isInactive(worldName)) {
            if (onSuccess != null) {
                World world = ensureWorldLoaded(worldName);
                if (world != null) onSuccess.run();
            }
            return;
        }

        plugin.getLogger().info("[WorldManager] Activating inactive world: " + worldName);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                File source = new File(new File(plugin.getDataFolder(), "inactive"), worldName);
                File dest = new File(Bukkit.getWorldContainer(), worldName);
                if (dest.exists()) {
                    deleteDirectory(dest);
                }
                if (source.exists()) {
                    copyAndDeleteDirectory(source, dest);
                    source.delete();
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getDatabaseManager().setInactive(worldName, false);
                    World world = ensureWorldLoaded(worldName);
                    if (onSuccess != null && world != null) {
                        onSuccess.run();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void copyAndDeleteDirectory(File source, File dest) throws IOException {
        if (source.isDirectory()) {
            if (!dest.exists()) {
                dest.mkdirs();
            }
            String[] files = source.list();
            if (files != null) {
                for (String file : files) {
                    copyAndDeleteDirectory(new File(source, file), new File(dest, file));
                }
            }
            source.delete();
        } else {
            Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            source.delete();
        }
    }

    public void createVoidWorld(String worldName, Player creator) {
        int speedPercent = plugin.getConfig().getInt("World.CreationSpeedPercent", 100);
        speedPercent = Math.max(1, Math.min(100, speedPercent));
        long delayTicks = (long) ((100 - speedPercent) / 1.0);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                File worldContainer = Bukkit.getWorldContainer();
                File worldFolder = new File(worldContainer, worldName);
                worldFolder.mkdirs();

                Bukkit.getScheduler().runTask(plugin, () -> doCreateWorld(worldName, creator));
            });
        }, delayTicks);
    }

    private void doCreateWorld(String worldName, Player creator) {
        if (creator != null && !creator.isOnline()) {
            plugin.getLogger().info("[WorldManager] Creator " + worldName + " disconnected; aborting world creation.");
            return;
        }

        WorldCreator wc = new WorldCreator(worldName);
        wc.generator(new VoidWorldGenerator());
        wc.generateStructures(false);
        wc.type(WorldType.FLAT);

        World world = Bukkit.createWorld(wc);
        if (world == null) {
            if (creator != null && creator.isOnline()) {
                creator.sendMessage(MessageUtils.colorize("&cFailed to create world!"));
            }
            return;
        }

        world.setSpawnLocation(0, 64, 0);
        world.setKeepSpawnInMemory(false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        world.setTime(6000);

        int diameter = plugin.getConfig().getInt("World.DefaultDiameter", 100);
        world.getWorldBorder().setCenter(0, 0);
        world.getWorldBorder().setSize(diameter);

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.getBlockAt(x, 63, z).setType(Material.STONE);
            }
        }

        for (Chunk chunk : world.getLoadedChunks()) {
            int cx = chunk.getX();
            int cz = chunk.getZ();
            if (cx != 0 || cz != 0) {
                chunk.unload(true);
            }
        }

        plugin.getDatabaseManager().createWorld(worldName, creator.getUniqueId(), worldName);
        plugin.getDatabaseManager().setDiameter(worldName, diameter);
        plugin.getDatabaseManager().setInactive(worldName, false);

        setupWorldGuard(world);

        Location spawnLoc = new Location(world, 0.5, 64, 0.5);
        creator.teleport(spawnLoc);
    }

    public void deleteWorld(String worldName, UUID deleterUUID) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            Location serverSpawn = Bukkit.getWorlds().get(0).getSpawnLocation();

            for (Player p : world.getPlayers()) {
                p.teleport(serverSpawn);
                if (deleterUUID == null || !p.getUniqueId().equals(deleterUUID)) {
                    p.sendMessage(MessageUtils.colorize("&cThe world you were in has been deleted!"));
                }
            }

            Bukkit.unloadWorld(world, false);
        }

        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        deleteDirectory(worldFolder);

        File inactiveFolder = new File(new File(plugin.getDataFolder(), "inactive"), worldName);
        if (inactiveFolder.exists()) {
            deleteDirectory(inactiveFolder);
        }

        plugin.getDatabaseManager().deleteWorld(worldName);
    }

    private void deleteDirectory(File directory) {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        directory.delete();
    }

    public void startAutoUnloadTask() {
        stopAutoUnloadTask();
        unloadTask = Bukkit.getScheduler().runTaskTimer(plugin, this::unloadAllEmptyWorlds, 400L, 400L);
    }

    public void stopAutoUnloadTask() {
        if (unloadTask != null) {
            unloadTask.cancel();
            unloadTask = null;
        }
    }

    public void unloadAllEmptyWorlds() {
        for (World world : Bukkit.getWorlds().toArray(new World[0])) {
            tryUnloadWorld(world.getName());
        }
    }

    public void tryUnloadWorld(String worldName) {
        if (!plugin.getDatabaseManager().isBuildWorld(worldName)) return;
        if (plugin.getDatabaseManager().isInactive(worldName)) return;

        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        if (!world.getPlayers().isEmpty()) return;

        world.save();
        boolean unloaded = Bukkit.unloadWorld(world, true);
        if (unloaded) {
            plugin.getLogger().info("[AutoUnload] Unloaded empty build world: " + worldName);
        }
    }

    public World ensureWorldLoaded(String worldName) {
        if (plugin.getDatabaseManager().isInactive(worldName)) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world != null) return world;

        WorldCreator wc = new WorldCreator(worldName);
        wc.generator(new VoidWorldGenerator());
        wc.generateStructures(false);
        world = Bukkit.createWorld(wc);

        if (world != null) {
            world.setKeepSpawnInMemory(false);
            int diameter = plugin.getDatabaseManager().getDiameter(worldName);
            world.getWorldBorder().setCenter(0, 0);
            world.getWorldBorder().setSize(diameter);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
            world.setTime(6000);
            plugin.getLogger().info("[AutoLoad] Loaded build world on demand: " + worldName);
        }

        return world;
    }

    private void setupWorldGuard(World world) {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) return;

        try {
            com.sk89q.worldguard.WorldGuard wg = com.sk89q.worldguard.WorldGuard.getInstance();
            com.sk89q.worldguard.protection.managers.RegionManager rm = wg.getPlatform().getRegionContainer()
                    .get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(world));

            if (rm != null) {
                com.sk89q.worldguard.protection.regions.GlobalProtectedRegion global =
                        new com.sk89q.worldguard.protection.regions.GlobalProtectedRegion("__global__");

                global.setFlag(com.sk89q.worldguard.protection.flags.Flags.PASSTHROUGH,
                        com.sk89q.worldguard.protection.flags.StateFlag.State.DENY);
                global.setFlag(com.sk89q.worldguard.protection.flags.Flags.BUILD,
                        com.sk89q.worldguard.protection.flags.StateFlag.State.DENY);

                rm.addRegion(global);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}