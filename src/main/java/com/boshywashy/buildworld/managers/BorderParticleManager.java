package com.boshywashy.buildworld.managers;

import com.boshywashy.buildworld.BuildWorld;
import com.boshywashy.buildworld.utils.BedrockUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Bedrock players (joining through GeyserMC) can't see the vanilla world border, so this draws it
 * for them as red particles. Only Bedrock players get the particles, and only the part of the
 * border within ViewDistance blocks of them is drawn.
 */
public class BorderParticleManager {
    /** How far above and below the player the wall is drawn, in blocks. */
    private static final double VERTICAL_RANGE = 10.0;

    private final BuildWorld plugin;
    private BukkitTask task;

    public BorderParticleManager(BuildWorld plugin) {
        this.plugin = plugin;
    }

    /** Reads the settings and (re)starts the task. Safe to call again after a config reload. */
    public void start() {
        stop();

        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("BedrockBorderParticles.Enabled", true)) return;

        final double viewDistance = Math.max(1.0, config.getDouble("BedrockBorderParticles.ViewDistance", 40.0));
        final double spacing = Math.max(1.0, config.getDouble("BedrockBorderParticles.Spacing", 3.0));
        final long interval = Math.max(1L, config.getLong("BedrockBorderParticles.UpdateTicks", 10L));

        final Particle particle = findDustParticle();
        if (particle == null) {
            plugin.getLogger().warning("Could not find the dust particle on this server version; Bedrock border particles are off.");
            return;
        }
        final Particle.DustOptions red = new Particle.DustOptions(Color.RED, 1.5f);

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                drawBorderFor(player, particle, red, viewDistance, spacing);
            }
        }, interval, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * The particle was renamed REDSTONE -> DUST in 1.20.5, so look it up by name to work on both.
     */
    private Particle findDustParticle() {
        for (String name : new String[]{"DUST", "REDSTONE"}) {
            try {
                return (Particle) Particle.class.getField(name).get(null);
            } catch (ReflectiveOperationException ignored) {
                // try the next name
            }
        }
        return null;
    }

    private void drawBorderFor(Player player, Particle particle, Particle.DustOptions dust, double view, double spacing) {
        World world = player.getWorld();
        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        double half = border.getSize() / 2.0;

        double minX = center.getX() - half;
        double maxX = center.getX() + half;
        double minZ = center.getZ() - half;
        double maxZ = center.getZ() + half;

        Location at = player.getLocation();
        double px = at.getX();
        double py = at.getY();
        double pz = at.getZ();

        boolean nearMinX = Math.abs(px - minX) <= view;
        boolean nearMaxX = Math.abs(px - maxX) <= view;
        boolean nearMinZ = Math.abs(pz - minZ) <= view;
        boolean nearMaxZ = Math.abs(pz - maxZ) <= view;

        // Most players are nowhere near a border, so do this cheap check before anything else
        if (!(nearMinX || nearMaxX || nearMinZ || nearMaxZ)) return;
        if (!BedrockUtils.isBedrock(player)) return;

        double minY = world.getMinHeight();
        double maxY = world.getMaxHeight();

        if (nearMinX) drawWall(player, particle, dust, true, minX, minZ, maxZ, px, py, pz, view, spacing, minY, maxY);
        if (nearMaxX) drawWall(player, particle, dust, true, maxX, minZ, maxZ, px, py, pz, view, spacing, minY, maxY);
        if (nearMinZ) drawWall(player, particle, dust, false, minZ, minX, maxX, px, py, pz, view, spacing, minY, maxY);
        if (nearMaxZ) drawWall(player, particle, dust, false, maxZ, minX, maxX, px, py, pz, view, spacing, minY, maxY);
    }

    /**
     * Draws one flat wall of the border.
     *
     * @param wallIsXPlane true if the wall sits at a fixed X (it runs along Z), false if it sits at a fixed Z
     * @param wallCoord    the X (or Z) the wall is at
     * @param alongMin     where the wall starts along its length (the border's edge)
     * @param alongMax     where the wall ends along its length
     */
    private void drawWall(Player player, Particle particle, Particle.DustOptions dust, boolean wallIsXPlane,
                          double wallCoord, double alongMin, double alongMax,
                          double px, double py, double pz, double view, double spacing,
                          double worldMinY, double worldMaxY) {
        double across = wallIsXPlane ? px : pz;   // player's position perpendicular to the wall
        double along = wallIsXPlane ? pz : px;    // player's position along the wall

        double distance = Math.abs(across - wallCoord);
        // Radius of the circle of wall that lies within viewing distance of the player
        double reach = Math.sqrt(Math.max(0.0, view * view - distance * distance));
        double verticalReach = Math.min(reach, VERTICAL_RANGE);

        double startAlong = Math.max(along - reach, alongMin);
        double endAlong = Math.min(along + reach, alongMax);
        double startY = Math.max(py - verticalReach, worldMinY);
        double endY = Math.min(py + verticalReach, worldMaxY);

        // Snap to a fixed grid so the dots hold still as the player moves
        for (double a = Math.ceil(startAlong / spacing) * spacing; a <= endAlong; a += spacing) {
            for (double y = Math.ceil(startY / spacing) * spacing; y <= endY; y += spacing) {
                double da = a - along;
                double dy = y - py;
                if (distance * distance + da * da + dy * dy > view * view) continue;

                double x = wallIsXPlane ? wallCoord : a;
                double z = wallIsXPlane ? a : wallCoord;
                player.spawnParticle(particle, x, y, z, 1, 0.0, 0.0, 0.0, dust);
            }
        }
    }
}
