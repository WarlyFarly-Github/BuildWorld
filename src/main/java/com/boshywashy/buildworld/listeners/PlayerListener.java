package com.boshywashy.buildworld.listeners;

import com.boshywashy.buildworld.BuildWorld;
import com.boshywashy.buildworld.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Arrays;
import java.util.List;

public class PlayerListener implements Listener {
    private final BuildWorld plugin;

    public PlayerListener(BuildWorld plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World fromWorld = event.getFrom();
        World toWorld = player.getWorld();

        if (plugin.getDatabaseManager().isBuildWorld(fromWorld.getName())) {
            plugin.getPermissionManager().removePermissions(player);

            final String leftWorldName = fromWorld.getName();
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    plugin.getWorldManager().tryUnloadWorld(leftWorldName), 1L);
        }

        String spawnWorldName = plugin.getDatabaseManager().getSpawnWorld();
        boolean isSpawnWorld = toWorld.getName().equals(spawnWorldName);

        if (isSpawnWorld) {
            String primary = plugin.getConfigManager().getPrimaryColor();
            int fadeIn = plugin.getConfig().getInt("Messages.Title.FadeIn", 20);
            int stay = plugin.getConfig().getInt("Messages.Title.Stay", 60);
            int fadeOut = plugin.getConfig().getInt("Messages.Title.FadeOut", 20);

            player.sendTitle(
                    MessageUtils.colorize("&" + primary + "&lWelcome to Spawn!"),
                    "",
                    fadeIn, stay, fadeOut
            );
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) sendSpawnWorldWelcomeMessage(player);
            }, 2L);
            return;
        }

        if (!plugin.getDatabaseManager().isBuildWorld(toWorld.getName())) {
            return;
        }

        if (plugin.getConfig().getBoolean("Maintenance.Enabled", false)) {
            if (!player.hasPermission("buildworld.admin")) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Location serverSpawn = plugin.getDatabaseManager().getSpawnLocation();
                    if (serverSpawn == null) serverSpawn = Bukkit.getWorlds().get(0).getSpawnLocation();
                    player.teleport(serverSpawn);
                    player.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("Maintenance.KickMessage",
                            "&cThe BuildWorld system is currently under maintenance. Please try again later.")));
                });
                return;
            }
        }

        String worldName = toWorld.getName();
        String nickname = plugin.getDatabaseManager().getNickname(worldName);

        String primary = plugin.getConfigManager().getPrimaryColor();
        String secondary = plugin.getConfigManager().getSecondaryColor();

        int fadeIn = plugin.getConfig().getInt("Messages.Title.FadeIn", 20);
        int stay = plugin.getConfig().getInt("Messages.Title.Stay", 60);
        int fadeOut = plugin.getConfig().getInt("Messages.Title.FadeOut", 20);

        plugin.getPermissionManager().applyWorldPermissions(player, worldName);

        boolean isOwner = plugin.getDatabaseManager().isOwner(worldName, player.getUniqueId());
        boolean isMember = plugin.getDatabaseManager().isMember(worldName, player.getUniqueId());

        if (isOwner || isMember) {
            player.sendTitle(
                    MessageUtils.colorize("&" + primary + "&lWelcome to &" + secondary + "&l" + nickname),
                    MessageUtils.colorize("&fUse /bw help to learn how to build!"),
                    fadeIn, stay, fadeOut
            );
            final String nick = nickname;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) sendWelcomeMessage(player, nick);
            }, 2L);
        } else {
            player.sendTitle(
                    MessageUtils.colorize("&" + primary + "&lWelcome to &" + secondary + "&l" + nickname),
                    MessageUtils.colorize("&fEnjoy your visit!"),
                    fadeIn, stay, fadeOut
            );
            final String nick = nickname;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) sendVisitorWelcomeMessage(player, nick);
            }, 2L);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        List<String> invites = plugin.getDatabaseManager().getPendingInvites(player.getUniqueId());

        if (!invites.isEmpty()) {
            player.sendMessage(MessageUtils.colorize("%primary%You have pending invites to worlds!"));
            for (String world : invites) {
                String nick = plugin.getDatabaseManager().getNickname(world);
                player.sendMessage(MessageUtils.colorize("%secondary%/bw join " + nick));
            }
        }

        World joinWorld = player.getWorld();
        if (plugin.getDatabaseManager().isBuildWorld(joinWorld.getName())) {
            plugin.getPermissionManager().applyWorldPermissions(player, joinWorld.getName());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String worldName = player.getWorld().getName();

        plugin.getPermissionManager().removePermissions(player);

        if (plugin.getDatabaseManager().isBuildWorld(worldName)) {
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    plugin.getWorldManager().tryUnloadWorld(worldName), 1L);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();

        if (!plugin.getDatabaseManager().isBuildWorld(world.getName())) return;

        if (plugin.getConfig().getBoolean("Maintenance.Enabled", false)) {
            if (!player.hasPermission("buildworld.admin")) {
                Location serverSpawn = plugin.getDatabaseManager().getSpawnLocation();
                if (serverSpawn == null) serverSpawn = Bukkit.getWorlds().get(0).getSpawnLocation();
                player.teleport(serverSpawn);
                player.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("Maintenance.KickMessage",
                        "&cThe BuildWorld system is currently under maintenance. Please try again later.")));
                return;
            }
        }

        Location loc = player.getLocation();
        if (!world.getWorldBorder().isInside(loc)) {
            Location spawnLoc = plugin.getDatabaseManager().getWorldSpawn(world.getName());
            player.teleport(spawnLoc);
            player.sendMessage(MessageUtils.colorize("&cYou reached the world border!"));
        }

        if (loc.getY() < -75) {
            Location spawnLoc = plugin.getDatabaseManager().getWorldSpawn(world.getName());
            player.teleport(spawnLoc);
            player.sendMessage(MessageUtils.colorize("&cYou fell too far! Teleporting to safety..."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        World world = player.getWorld();

        if (!plugin.getDatabaseManager().isBuildWorld(world.getName())) return;

        event.setCancelled(true);
    }

    private void sendWelcomeMessage(Player player, String worldName) {
        List<String> defaultMsg = Arrays.asList(
                "&l%primary%&m= = = = = = = = = = = = = = = = = = = = = = = =",
                "%primary%Welcome to %secondary%" + worldName + "%primary%!",
                "",
                "%primary%World Edit and Axiom are recommended skills to learn to build.",
                "",
                "%primary%Use %secondary%/bw help %primary%to add members to help build!",
                "",
                "%primary%&lHappy Building!",
                "",
                "&l%primary%&m= = = = = = = = = = = = = = = = = = = = = = = ="
        );

        List<String> messages = plugin.getConfig().getStringList("Messages.Welcome");
        if (messages.isEmpty()) messages = defaultMsg;

        for (String line : messages) {
            player.sendMessage(MessageUtils.colorize(line.replace("<world>", worldName)));
        }
    }

    private void sendVisitorWelcomeMessage(Player player, String worldName) {
        List<String> defaultMsg = Arrays.asList(
                "&l%primary%&m= = = = = = = = = = = = = = = = = = = = = = = =",
                " ",
                "%primary%Welcome to %secondary%" + worldName + "%primary%!",
                " ",
                "%primary%&lEnjoy the visit!!",
                " ",
                "&l%primary%&m= = = = = = = = = = = = = = = = = = = = = = = ="
        );

        List<String> messages = plugin.getConfig().getStringList("Messages.VisitorWelcome");
        if (messages.isEmpty()) messages = defaultMsg;

        for (String line : messages) {
            player.sendMessage(MessageUtils.colorize(line.replace("<world>", worldName)));
        }
    }

    private void sendSpawnWorldWelcomeMessage(Player player) {
        List<String> defaultMsg = Arrays.asList(
                "&l%primary%&m= = = = = = = = = = = = = = = = = = = = = = = =",
                " ",
                "%primary%&lWelcome to the BuildWorld System!",
                " ",
                "%secondary%Create your own world with /bw create_world",
                "%secondary%Visit other worlds with /bw menu",
                " ",
                "&l%primary%&m= = = = = = = = = = = = = = = = = = = = = = = ="
        );

        List<String> messages = plugin.getConfig().getStringList("Messages.SpawnWorldWelcome");
        if (messages.isEmpty()) messages = defaultMsg;

        for (String line : messages) {
            player.sendMessage(MessageUtils.colorize(line));
        }
    }


}