package com.boshywashy.buildworld.commands;

import com.boshywashy.buildworld.BuildWorld;
import com.boshywashy.buildworld.utils.MessageUtils;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class BuildWorldCommand implements CommandExecutor {
    private final BuildWorld plugin;
    private final Map<UUID, String> deleteConfirm = new HashMap<>();
    private final Map<UUID, Long> createCooldowns = new HashMap<>();

    public BuildWorldCommand(BuildWorld plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        if (plugin.getConfig().getBoolean("Maintenance.Enabled", false) && !player.hasPermission("buildworld.admin")) {
            player.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("Maintenance.KickMessage",
                    "&cThe BuildWorld system is currently under maintenance. Please try again later.")));
            return true;
        }

        if (args.length == 0) {
            handleMenu(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "spawn":
                handleSpawn(player);
                break;
            case "setspawn":
                handleSetSpawn(player);
                break;
            case "removespawn":
                handleRemoveSpawn(player);
                break;
            case "create_world":
                handleCreateWorld(player);
                break;
            case "nickname":
                handleNickname(player, args);
                break;
            case "members":
                handleMembers(player, args);
                break;
            case "open":
                handleOpen(player, args);
                break;
            case "close":
                handleClose(player, args);
                break;
            case "item":
                handleItem(player, args);
                break;
            case "invite":
                handleInvite(player, args);
                break;
            case "join":
                handleJoin(player, args);
                break;
            case "retractinvite":
                handleRetractInvite(player, args);
                break;
            case "delete":
                handleDelete(player, args);
                break;
            case "kick":
                handleKick(player, args);
                break;
            case "leave":
                handleLeave(player, args);
                break;
            case "menu":
                handleMenu(player);
                break;
            case "visit":
                handleVisit(player, args);
                break;
            case "home":
                handleHome(player, args);
                break;
            case "info":
                handleInfo(player, args);
                break;
            case "help":
                handleHelp(player, args);
                break;
            case "expandworld":
                handleExpandWorld(player, args);
                break;
            case "expansionblockamount":
                handleExpansionBlockAmount(player);
                break;
            case "moveworldspawn":
                handleMoveWorldSpawn(player, args);
                break;
            case "hideworld":
                handleHideWorld(player, args);
                break;
            case "showworld":
                handleShowWorld(player, args);
                break;
            default:
                sendHelp(player, 1);
                break;
        }

        return true;
    }

    private void handleSpawn(Player player) {
        String spawnWorldName = plugin.getDatabaseManager().getSpawnWorld();
        if (spawnWorldName == null) {
            player.sendMessage(MessageUtils.colorize("&cSpawn world not set! Use /bw setspawn to set one."));
            return;
        }

        World world = Bukkit.getWorld(spawnWorldName);
        if (world == null) {
            WorldCreator wc = new WorldCreator(spawnWorldName);
            world = Bukkit.createWorld(wc);
            if (world == null) {
                player.sendMessage(MessageUtils.colorize("&cFailed to load spawn world!"));
                return;
            }
        }

        Location spawnLoc = plugin.getDatabaseManager().getSpawnLocation();
        if (spawnLoc == null) {
            player.sendMessage(MessageUtils.colorize("&cSpawn location not set! Use /bw setspawn to set one."));
            return;
        }

        player.teleport(spawnLoc);
        player.sendMessage(MessageUtils.colorize("%primary%Teleported to spawn!"));
    }

    private void handleSetSpawn(Player player) {
        if (!player.hasPermission("buildworld.admin")) {
            player.sendMessage(MessageUtils.colorize("&cNo permission!"));
            return;
        }

        Location loc = player.getLocation();
        Location centeredLoc = loc.clone();
        centeredLoc.setX(Math.floor(loc.getX()) + 0.5);
        centeredLoc.setZ(Math.floor(loc.getZ()) + 0.5);

        plugin.getDatabaseManager().setSpawnWorld(loc.getWorld().getName(), centeredLoc);
        player.sendMessage(MessageUtils.colorize("%primary%Spawn set to: %secondary%" +
                loc.getWorld().getName() + " at " +
                String.format("%.1f, %.1f, %.1f", centeredLoc.getX(), centeredLoc.getY(), centeredLoc.getZ()) +
                " facing " + String.format("%.0f, %.0f", centeredLoc.getYaw(), centeredLoc.getPitch())));
    }

    private void handleRemoveSpawn(Player player) {
        if (!player.hasPermission("buildworld.admin")) {
            player.sendMessage(MessageUtils.colorize("&cNo permission!"));
            return;
        }

        plugin.getDatabaseManager().removeSpawnWorld();
        player.sendMessage(MessageUtils.colorize("%primary%Spawn location has been removed!"));
    }

    private void handleCreateWorld(Player player) {
        if (!player.hasPermission("buildworld.create")) {
            player.sendMessage(MessageUtils.colorize("&cNo permission!"));
            return;
        }

        int cooldownSeconds = plugin.getConfig().getInt("World.CreateCooldown", 60);
        if (createCooldowns.containsKey(player.getUniqueId())) {
            long lastCreate = createCooldowns.get(player.getUniqueId());
            long elapsedSeconds = (System.currentTimeMillis() - lastCreate) / 1000;

            if (elapsedSeconds < cooldownSeconds) {
                long remaining = cooldownSeconds - elapsedSeconds;
                player.sendMessage(MessageUtils.colorize("&cYou must wait " + remaining + " seconds before creating another world!"));
                return;
            }
        }

        int maxWorlds = plugin.getConfig().getInt("World.MaxWorldsPerPlayer", 5);
        int extraCredits = plugin.getDatabaseManager().getExtraWorldCredits(player.getUniqueId());
        int currentWorlds = plugin.getDatabaseManager().getOwnedWorlds(player.getUniqueId()).size();

        if (currentWorlds >= (maxWorlds + extraCredits) && !player.hasPermission("buildworld.admin")) {
            player.sendMessage(MessageUtils.colorize("&cYou have reached the maximum amount of worlds!"));
            return;
        }

        int speedPercent = plugin.getConfig().getInt("World.CreationSpeedPercent", 100);
        if (speedPercent < 100) {
            player.sendMessage(MessageUtils.colorize("%primary%Creating world. This may take a while due to server throttling."));
        } else {
            player.sendMessage(MessageUtils.colorize("%primary%Creating world. This may take a while."));
        }

        String worldName = player.getName() + "-" + generateRandomSuffix(6);
        createCooldowns.put(player.getUniqueId(), System.currentTimeMillis());

        plugin.getWorldManager().createVoidWorld(worldName, player);
    }

    private String generateRandomSuffix(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++) sb.append(chars.charAt(random.nextInt(chars.length())));
        return sb.toString();
    }

    private void handleNickname(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MessageUtils.colorize("&cUsage: /bw nickname <world> <nickname>"));
            return;
        }

        String worldName = resolveWorldName(args[1]);
        if (worldName == null || !plugin.getDatabaseManager().isBuildWorld(worldName)) {
            player.sendMessage(MessageUtils.colorize("&cWorld not found!"));
            return;
        }

        if (!plugin.getDatabaseManager().isOwner(worldName, player.getUniqueId()) && !player.hasPermission("buildworld.admin")) {
            player.sendMessage(MessageUtils.colorize("&cYou don't own this world!"));
            return;
        }

        StringBuilder nicknameBuilder = new StringBuilder();
        for (int i = 2; i < args.length; i++) nicknameBuilder.append(args[i]).append(" ");
        String nickname = nicknameBuilder.toString().trim();

        if (nickname.contains(" ")) {
            player.sendMessage(MessageUtils.colorize("&cNicknames cannot contain spaces!"));
            return;
        }

        plugin.getDatabaseManager().setNickname(worldName, nickname);
        player.sendMessage(MessageUtils.colorize("%primary%Nickname set to: %secondary%" + nickname));
    }

    private void handleMembers(Player player, String[] args) {
        if (args.length < 2) {
            List<String> owned = plugin.getDatabaseManager().getOwnedWorlds(player.getUniqueId());
            List<String> memberOf = plugin.getDatabaseManager().getMemberWorlds(player.getUniqueId());

            if (owned.isEmpty() && memberOf.isEmpty()) {
                player.sendMessage(MessageUtils.colorize("&cYou are not part of any worlds!"));
                return;
            }

            sendMembersList(player, owned.isEmpty() ? memberOf.get(0) : owned.get(0));
            return;
        }

        String worldName = resolveWorldName(args[1]);
        if (worldName == null) {
            player.sendMessage(MessageUtils.colorize("&cWorld not found!"));
            return;
        }

        sendMembersList(player, worldName);
    }

    private void sendMembersList(Player player, String worldName) {
        if (!plugin.getDatabaseManager().isBuildWorld(worldName)) {
            player.sendMessage(MessageUtils.colorize("&cThis is not a build world!"));
            return;
        }

        String nickname = plugin.getDatabaseManager().getNickname(worldName);
        List<UUID> owners = plugin.getDatabaseManager().getOwners(worldName);
        List<UUID> members = plugin.getDatabaseManager().getMembers(worldName);

        player.sendMessage(MessageUtils.colorize("&l&m%primary%= = = = = = = = = = ="));
        player.sendMessage(MessageUtils.colorize(nickname + " &7(" + worldName + "&7)"));
        player.sendMessage(MessageUtils.colorize("&l&m%primary%= = = = = = = = = = ="));

        StringBuilder ownerStr = new StringBuilder("%primary%Owner(s): %secondary%");
        for (UUID owner : owners) ownerStr.append(Bukkit.getOfflinePlayer(owner).getName()).append(", ");
        player.sendMessage(MessageUtils.colorize(ownerStr.toString().replaceAll(", \\$", "")));

        if (!members.isEmpty()) {
            StringBuilder memberStr = new StringBuilder("%primary%Member(s): %secondary%");
            for (UUID member : members) memberStr.append(Bukkit.getOfflinePlayer(member).getName()).append(", ");
            player.sendMessage(MessageUtils.colorize(memberStr.toString().replaceAll(", \\$", "")));
        } else {
            player.sendMessage(MessageUtils.colorize("%primary%Member(s): %secondary%None"));
        }

        player.sendMessage(MessageUtils.colorize("&l&m%primary%= = = = = = = = = = ="));
    }

    private void handleOpen(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MessageUtils.colorize("&cUsage: /bw open <world>"));
            return;
        }

        String worldName = resolveWorldName(args[1]);
        if (worldName == null) {
            player.sendMessage(MessageUtils.colorize("&cWorld not found!"));
            return;
        }

        if (!plugin.getDatabaseManager().isOwner(worldName, player.getUniqueId())) {
            player.sendMessage(MessageUtils.colorize("&cOnly owners can open worlds!"));
            return;
        }

        plugin.getDatabaseManager().setOpen(worldName, true);
        player.sendMessage(MessageUtils.colorize("%primary%World is now %secondary%OPEN%primary%!"));
    }

    private void handleClose(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MessageUtils.colorize("&cUsage: /bw close <world>"));
            return;
        }

        String worldName = resolveWorldName(args[1]);
        if (worldName == null) {
            player.sendMessage(MessageUtils.colorize("&cWorld not found!"));
            return;
        }

        if (!plugin.getDatabaseManager().isOwner(worldName, player.getUniqueId())) {
            player.sendMessage(MessageUtils.colorize("&cOnly owners can close worlds!"));
            return;
        }

        plugin.getDatabaseManager().setOpen(worldName, false);
        player.sendMessage(MessageUtils.colorize("%primary%World is now %secondary%CLOSED%primary%!"));

        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            Location serverSpawn = plugin.getDatabaseManager().getSpawnLocation();
            if (serverSpawn == null) serverSpawn = Bukkit.getWorlds().get(0).getSpawnLocation();

            for (Player p : world.getPlayers()) {
                if (!plugin.getDatabaseManager().isOwner(worldName, p.getUniqueId()) &&
                        !plugin.getDatabaseManager().isMember(worldName, p.getUniqueId())) {
                    p.teleport(serverSpawn);
                    p.sendMessage(MessageUtils.colorize("&cThis world has been closed!"));
                }
            }
        }
    }

    private void handleItem(Player player, String[] args) {
        String worldInput;
        Material mat = null;

        if (args.length < 2) {
            if (player.getInventory().getItemInMainHand().getType() == Material.AIR) {
                player.sendMessage(MessageUtils.colorize("&cUsage: /bw item <world> [material]"));
                return;
            }
            worldInput = player.getWorld().getName();
            mat = player.getInventory().getItemInMainHand().getType();
        } else if (args.length < 3) {
            worldInput = args[1];
            if (player.getInventory().getItemInMainHand().getType() == Material.AIR) {
                player.sendMessage(MessageUtils.colorize("&cUsage: /bw item <world> [material] or hold an item!"));
                return;
            }
            mat = player.getInventory().getItemInMainHand().getType();
        } else {
            worldInput = args[1];
            mat = Material.matchMaterial(args[2]);
            if (mat == null) {
                player.sendMessage(MessageUtils.colorize("&cInvalid material!"));
                return;
            }
        }

        String worldName = resolveWorldName(worldInput);
        if (worldName == null) {
            player.sendMessage(MessageUtils.colorize("&cWorld not found!"));
            return;
        }

        if (!plugin.getDatabaseManager().isOwner(worldName, player.getUniqueId())) {
            player.sendMessage(MessageUtils.colorize("&cOnly owners can set the item!"));
            return;
        }

        plugin.getDatabaseManager().setItem(worldName, mat.name());
        player.sendMessage(MessageUtils.colorize("%primary%Item set to: %secondary%" + mat.name()));
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(MessageUtils.colorize("&cUsage: /bw invite <member|owner> <world> <player>"));
            return;
        }

        String role = args[1].toUpperCase();
        String worldName = resolveWorldName(args[2]);
        String targetName = args[3];

        if (!role.equals("MEMBER") && !role.equals("OWNER")) {
            player.sendMessage(MessageUtils.colorize("&cRole must be member or owner!"));
            return;
        }

        if (worldName == null) {
            player.sendMessage(MessageUtils.colorize("&cWorld not found!"));
            return;
        }

        if (!plugin.getDatabaseManager().isOwner(worldName, player.getUniqueId())) {
            player.sendMessage(MessageUtils.colorize("&cOnly owners can invite!"));
            return;
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            player.sendMessage(MessageUtils.colorize("&cPlayer has never joined!"));
            return;
        }

        plugin.getDatabaseManager().addInvite(worldName, target.getUniqueId(), role);
        player.sendMessage(MessageUtils.colorize("%primary%Invited %secondary%" + targetName + " %primary%as %secondary%" + role));

        if (target.isOnline()) {
            target.getPlayer().sendMessage(MessageUtils.colorize("%primary%You have been invited to join %secondary%" +
                    plugin.getDatabaseManager().getNickname(worldName) + " %primary%as %secondary%" + role));
            target.getPlayer().sendMessage(MessageUtils.colorize("%primary%Use /bw join " +
                    plugin.getDatabaseManager().getNickname(worldName) + " %primary%to accept!"));
        }
    }

    private void handleRetractInvite(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(MessageUtils.colorize("&cUsage: /bw RetractInvite <member|owner> <world> <player>"));
            return;
        }

        String worldName = resolveWorldName(args[2]);
        String targetName = args[3];

        if (worldName == null) {
            player.sendMessage(MessageUtils.colorize("&cWorld not found!"));
            return;
        }

        if (!plugin.getDatabaseManager().isOwner(worldName, player.getUniqueId())) {
            player.sendMessage(MessageUtils.colorize("&cOnly owners can retract invites!"));
            return;
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target == null) {
            player.sendMessage(MessageUtils.colorize("&cPlayer not found!"));
            return;
        }

        String role = plugin.getDatabaseManager().getInviteRole(worldName, target.getUniqueId());
        if (role == null) {
            player.sendMessage(MessageUtils.colorize("&cThat player has not been invited to this world!"));
            return;
        }

        plugin.getDatabaseManager().removeInvite(worldName, target.getUniqueId());
        player.sendMessage(MessageUtils.colorize("%primary%Retracted invite for %secondary%" + targetName));

        if (target.isOnline()) {
            target.getPlayer().sendMessage(MessageUtils.colorize("&cYour invite to " +
                    plugin.getDatabaseManager().getNickname(worldName) + " has been retracted."));
        }
    }

    private void handleJoin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MessageUtils.colorize("&cUsage: /bw join <world>"));
            return;
        }

        String worldName = resolveWorldName(args[1]);
        if (worldName == null) {
            player.sendMessage(MessageUtils.colorize("&cWorld not found!"));
            return;
        }

        String role = plugin.getDatabaseManager().getInviteRole(worldName, player.getUniqueId());
        if (role == null && !player.hasPermission("buildworld.admin")) {
            player.sendMessage(MessageUtils.colorize("&cYou haven't been invited to this world!"));
            return;
        }

        plugin.getDatabaseManager().addMember(worldName, player.getUniqueId(), role);
        plugin.getDatabaseManager().removeInvite(worldName, player.getUniqueId());

        if (player.getWorld().getName().equals(worldName)) {
            plugin.getPermissionManager().applyWorldPermissions(player, worldName);
        }

        player.sendMessage(MessageUtils.colorize("%primary%You joined as: %secondary%" + role));
    }

    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MessageUtils.colorize("&cUsage: /bw delete <world> confirm confirm"));
            return;
        }

        String worldName = resolveWorldName(args[1]);
        if (worldName == null) {
            player.sendMessage(MessageUtils.colorize("&cWorld not found!"));
            return;
        }

        if (!plugin.getDatabaseManager().isOwner(worldName, player.getUniqueId()) && !player.hasPermission("buildworld.admin")) {
            player.sendMessage(MessageUtils.colorize("&cOnly owners can delete!"));
            return;
        }

        if (args.length < 4 || !args[2].equalsIgnoreCase("confirm") || !args[3].equalsIgnoreCase("confirm")) {
            player.sendMessage(MessageUtils.colorize("&cAre you sure? Type: /bw delete " + args[1] + " confirm confirm"));
            return;
        }

        plugin.getWorldManager().deleteWorld(worldName, player.getUniqueId());
        player.sendMessage(MessageUtils.colorize("%primary%World deleted!"));
    }

    private void handleKick(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MessageUtils.colorize("&cUsage: /bw kick <world> <player>"));
            return;
        }

        String worldName = resolveWorldName(args[1]);
        String targetName = args[2];

        if (worldName == null) {
            player.sendMessage(MessageUtils.colorize("&cWorld not found!"));
            return;
        }

        if (!plugin.getDatabaseManager().isOwner(worldName, player.getUniqueId())) {
            player.sendMessage(MessageUtils.colorize("&cOnly owners can kick from this world!"));
            return;
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        UUID targetUUID = target.getUniqueId();

        if (plugin.getDatabaseManager().isOwner(worldName, targetUUID)) {
            player.sendMessage(MessageUtils.colorize("&cCannot kick owners!"));
            return;
        }

        if (!plugin.getDatabaseManager().isMember(worldName, targetUUID)) {
            player.sendMessage(MessageUtils.colorize("&cThat player is not a member of this world!"));
            return;
        }

        plugin.getDatabaseManager().removeMember(worldName, targetUUID);

        Player targetPlayer = Bukkit.getPlayer(targetUUID);
        if (targetPlayer != null && targetPlayer.isOnline() && targetPlayer.getWorld().getName().equals(worldName)) {
            plugin.getPermissionManager().removePermissions(targetPlayer);
        }

        player.sendMessage(MessageUtils.colorize("%primary%Kicked %secondary%" + targetName + " %primary%from %secondary%" + plugin.getDatabaseManager().getNickname(worldName)));
    }

    private void handleLeave(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MessageUtils.colorize("&cUsage: /bw leave <world>"));
            return;
        }

        String worldName = resolveWorldName(args[1]);
        if (worldName == null) {
            player.sendMessage(MessageUtils.colorize("&cWorld not found!"));
            return;
        }

        UUID playerUUID = player.getUniqueId();
        String primaryOwner = plugin.getDatabaseManager().getWorldOwner(worldName);
        if (primaryOwner != null && primaryOwner.equals(playerUUID.toString())) {
            List<UUID> owners = plugin.getDatabaseManager().getOwners(worldName);
            if (owners.size() <= 1) {
                player.sendMessage(MessageUtils.colorize("&cYou cannot leave as the sole owner! Delete the world instead."));
                return;
            }
        }

        plugin.getDatabaseManager().removeMember(worldName, playerUUID);

        if (player.getWorld().getName().equals(worldName)) {
            plugin.getPermissionManager().removePermissions(player);
        }

        player.sendMessage(MessageUtils.colorize("%primary%You left the world!"));
    }

    private void handleMenu(Player player) {
        plugin.getMainMenuGUI().openMenu(player);
    }

    private void handleVisit(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MessageUtils.colorize("&cUsage: /bw visit <world>"));
            return;
        }

        String worldName = resolveWorldName(args[1]);
        if (worldName == null) {
            @SuppressWarnings("deprecation")
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            List<String> worlds = plugin.getDatabaseManager().getOwnedWorlds(target.getUniqueId());
            if (!worlds.isEmpty()) worldName = worlds.get(0);
        }

        if (worldName == null) {
            player.sendMessage(MessageUtils.colorize("&cWorld not found!"));
            return;
        }

        if (!player.hasPermission("buildworld.admin")) {
            if (!plugin.getDatabaseManager().isOpen(worldName) &&
                    !plugin.getDatabaseManager().isMember(worldName, player.getUniqueId()) &&
                    !plugin.getDatabaseManager().isOwner(worldName, player.getUniqueId())) {
                player.sendMessage(MessageUtils.colorize("&cThis world is closed!"));
                return;
            }
        }

        if (plugin.getDatabaseManager().isInactive(worldName)) {
            player.sendMessage(MessageUtils.colorize("&eWorld is inactive and loading up... Please wait."));
            String finalWorldName = worldName;
            plugin.getWorldManager().activateWorld(worldName, () -> {
                if (!player.isOnline()) return;
                World world = plugin.getWorldManager().ensureWorldLoaded(finalWorldName);
                if (world != null) {
                    Location spawnLoc = plugin.getDatabaseManager().getWorldSpawn(finalWorldName);
                    player.teleport(spawnLoc);
                    player.sendMessage(MessageUtils.colorize("%primary%Visiting %secondary%" +
                            plugin.getDatabaseManager().getNickname(finalWorldName)));
                } else {
                    player.sendMessage(MessageUtils.colorize("&cWorld could not be loaded!"));
                }
            });
            return;
        }

        World world = plugin.getWorldManager().ensureWorldLoaded(worldName);
        if (world != null) {
            Location spawnLoc = plugin.getDatabaseManager().getWorldSpawn(worldName);
            player.teleport(spawnLoc);
            player.sendMessage(MessageUtils.colorize("%primary%Visiting %secondary%" +
                    plugin.getDatabaseManager().getNickname(worldName)));
        } else {
            player.sendMessage(MessageUtils.colorize("&cWorld could not be loaded!"));
        }
    }

    private void handleHome(Player player, String[] args) {
        String worldName;
        if (args.length < 2) {
            List<String> worlds = plugin.getDatabaseManager().getOwnedWorlds(player.getUniqueId());
            worlds.addAll(plugin.getDatabaseManager().getMemberWorlds(player.getUniqueId()));
            if (worlds.isEmpty()) {
                player.sendMessage(MessageUtils.colorize("&cYou have no worlds!"));
                return;
            }
            worldName = worlds.get(0);
        } else {
            worldName = resolveWorldName(args[1]);
        }

        if (worldName == null) {
            player.sendMessage(MessageUtils.colorize("&cWorld not found!"));
            return;
        }

        if (plugin.getDatabaseManager().isInactive(worldName)) {
            player.sendMessage(MessageUtils.colorize("&eWorld is inactive and loading up... Please wait."));
            plugin.getWorldManager().activateWorld(worldName, () -> {
                if (!player.isOnline()) return;
                World world = plugin.getWorldManager().ensureWorldLoaded(worldName);
                if (world != null) {
                    Location spawnLoc = plugin.getDatabaseManager().getWorldSpawn(worldName);
                    player.teleport(spawnLoc);
                    player.sendMessage(MessageUtils.colorize("%primary%Teleported to %secondary%" +
                            plugin.getDatabaseManager().getNickname(worldName)));
                } else {
                    player.sendMessage(MessageUtils.colorize("&cWorld could not be loaded!"));
                }
            });
            return;
        }

        World world = plugin.getWorldManager().ensureWorldLoaded(worldName);
        if (world != null) {
            Location spawnLoc = plugin.getDatabaseManager().getWorldSpawn(worldName);
            player.teleport(spawnLoc);
            player.sendMessage(MessageUtils.colorize("%primary%Teleported to %secondary%" +
                    plugin.getDatabaseManager().getNickname(worldName)));
        } else {
            player.sendMessage(MessageUtils.colorize("&cWorld could not be loaded!"));
        }
    }

    private void handleInfo(Player player, String[] args) {
        String worldName;
        if (args.length < 2) {
            if (plugin.getDatabaseManager().isBuildWorld(player.getWorld().getName())) {
                worldName = player.getWorld().getName();
            } else {
                List<String> worlds = plugin.getDatabaseManager().getOwnedWorlds(player.getUniqueId());
                if (worlds.isEmpty()) worlds = plugin.getDatabaseManager().getMemberWorlds(player.getUniqueId());
                if (worlds.isEmpty()) {
                    player.sendMessage(MessageUtils.colorize("&cNo world found!"));
                    return;
                }
                worldName = worlds.get(0);
            }
        } else {
            worldName = resolveWorldName(args[1]);
        }

        if (worldName == null || !plugin.getDatabaseManager().isBuildWorld(worldName)) {
            player.sendMessage(MessageUtils.colorize("&cWorld not found!"));
            return;
        }

        String nickname = plugin.getDatabaseManager().getNickname(worldName);
        List<UUID> owners = plugin.getDatabaseManager().getOwners(worldName);
        List<UUID> members = plugin.getDatabaseManager().getMembers(worldName);
        String item = plugin.getDatabaseManager().getItem(worldName);
        int diameter = plugin.getDatabaseManager().getDiameter(worldName);
        boolean isOpen = plugin.getDatabaseManager().isOpen(worldName);
        boolean isHidden = plugin.getDatabaseManager().isHidden(worldName);
        boolean isInactive = plugin.getDatabaseManager().isInactive(worldName);

        player.sendMessage(MessageUtils.colorize("&l&m%primary%= = = = = = = = = = ="));
        player.sendMessage(MessageUtils.colorize(nickname));
        player.sendMessage(MessageUtils.colorize("&l&m%primary%= = = = = = = = = = ="));
        player.sendMessage(MessageUtils.colorize("%primary%(&f" + worldName + "%primary%)"));

        StringBuilder ownerStr = new StringBuilder("%primary%Owner(s): %secondary%");
        for (UUID owner : owners) ownerStr.append(Bukkit.getOfflinePlayer(owner).getName()).append(", ");
        player.sendMessage(MessageUtils.colorize(ownerStr.toString().replaceAll(", \\$", "")));

        StringBuilder memberStr = new StringBuilder("%primary%Member(s): %secondary%");
        if (members.isEmpty()) memberStr.append("None");
        else for (UUID member : members) memberStr.append(Bukkit.getOfflinePlayer(member).getName()).append(", ");
        player.sendMessage(MessageUtils.colorize(memberStr.toString().replaceAll(", \\$", "")));

        player.sendMessage(MessageUtils.colorize("&l&m%primary%= = = = = = = = = = ="));
        player.sendMessage(MessageUtils.colorize("%primary%Item: %secondary%" + item));
        player.sendMessage(MessageUtils.colorize("%primary%World Size: %secondary%" + diameter));
        player.sendMessage(MessageUtils.colorize("%primary%World: " + (isOpen ? "&aOpened" : "&cClosed")));
        // Whether it says if a world status is active or inactive. Shouldn't be sent so is commented out: player.sendMessage(MessageUtils.colorize("%primary%Status: " + (isInactive ? "&cInactive" : "&aActive")));
        if (plugin.getDatabaseManager().isOwner(worldName, player.getUniqueId()) || player.hasPermission("buildworld.admin")) {
            player.sendMessage(MessageUtils.colorize("%primary%Hidden: " + (isHidden ? "&cYes" : "&aNo")));
        }
        player.sendMessage(MessageUtils.colorize("&l&m%primary%= = = = = = = = = = ="));
    }

    private void handleHelp(Player player, String[] args) {
        if (args.length < 2) {
            sendHelp(player, 1);
            return;
        }

        String sub = args[1].toLowerCase();
        Map<String, String> tutorials = plugin.getConfigManager().getTutorialMessages();

        if (tutorials.containsKey(sub)) {
            player.sendMessage(MessageUtils.colorize("%primary%=== Help: " + sub + " ==="));
            String tutorialMessage = tutorials.get(sub).replace("\\n", "\n");
            for (String line : tutorialMessage.split("\n")) {
                player.sendMessage(MessageUtils.colorize(line));
            }
        } else if (sub.equals("commands")) {
            int page = 1;
            if (args.length > 2) {
                try { page = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) {}
            }
            sendHelp(player, page);
        } else {
            player.sendMessage(MessageUtils.colorize("&cNo help found for: " + sub));
        }
    }

    private void sendHelp(Player player, int page) {
        player.sendMessage(MessageUtils.colorize("%primary%=== BuildWorld Help (Page " + page + ") ==="));
        player.sendMessage(MessageUtils.colorize("%secondary%/bw spawn %primary%- Teleport to spawn"));
        player.sendMessage(MessageUtils.colorize("%secondary%/bw removespawn %primary%- Remove spawn (Admin)"));
        player.sendMessage(MessageUtils.colorize("%secondary%/bw create_world %primary%- Create a new world"));
        player.sendMessage(MessageUtils.colorize("%secondary%/bw home [world] %primary%- Go to your world"));
        player.sendMessage(MessageUtils.colorize("%secondary%/bw visit <world> %primary%- Visit a world"));
        player.sendMessage(MessageUtils.colorize("%secondary%/bw invite <role> <world> <player> %primary%- Invite player"));
        player.sendMessage(MessageUtils.colorize("%secondary%/bw join <world> %primary%- Accept invite"));
        player.sendMessage(MessageUtils.colorize("%secondary%/bw retractinvite <role> <world> <player> %primary%- Retract invite"));
        player.sendMessage(MessageUtils.colorize("%secondary%/bw menu %primary%- Open world menu"));
        player.sendMessage(MessageUtils.colorize("%secondary%/bw info [world] %primary%- World info"));
        player.sendMessage(MessageUtils.colorize("%secondary%/bw members [world] %primary%- List members"));
        player.sendMessage(MessageUtils.colorize("%secondary%/bw open/close <world> %primary%- Open/close world"));
        player.sendMessage(MessageUtils.colorize("%secondary%/bw nickname <world> <n> %primary%- Set nickname"));
        player.sendMessage(MessageUtils.colorize("%secondary%/bw item <world> [item] %primary%- Set display item"));
        player.sendMessage(MessageUtils.colorize("%secondary%/bw kick <world> <player> %primary%- Kick member"));
        player.sendMessage(MessageUtils.colorize("%secondary%/bw leave <world> %primary%- Leave world"));
        player.sendMessage(MessageUtils.colorize("%secondary%/bw delete <world> confirm confirm %primary%- Delete"));
        player.sendMessage(MessageUtils.colorize("%secondary%/bw help commands <page> %primary%- Command help"));
        player.sendMessage(MessageUtils.colorize("%secondary%/bw help <topic> %primary%- Specific help"));
        player.sendMessage(MessageUtils.colorize("%secondary%/bw ExpandWorld <world> <blocks> %primary%- Expand border"));
        player.sendMessage(MessageUtils.colorize("%secondary%/bw ExpansionBlockAmount %primary%- Check blocks"));
        player.sendMessage(MessageUtils.colorize("%secondary%/bw MoveWorldSpawn <world> %primary%- Set world spawn"));
        if (!plugin.getDatabaseManager().getOwnedWorlds(player.getUniqueId()).isEmpty() || player.hasPermission("buildworld.admin")) {
            player.sendMessage(MessageUtils.colorize("%secondary%/bw HideWorld <world> %primary%- Hide your world from the menu"));
            player.sendMessage(MessageUtils.colorize("%secondary%/bw ShowWorld <world> %primary%- Make your world visible again"));
        }
    }

    private void handleExpandWorld(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MessageUtils.colorize("&cUsage: /bw ExpandWorld <world> <blocks>"));
            return;
        }

        String worldName = resolveWorldName(args[1]);
        if (worldName == null) {
            player.sendMessage(MessageUtils.colorize("&cWorld not found!"));
            return;
        }

        if (!plugin.getDatabaseManager().isOwner(worldName, player.getUniqueId())) {
            player.sendMessage(MessageUtils.colorize("&cOnly owners can expand!"));
            return;
        }

        int blocks;
        try {
            blocks = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(MessageUtils.colorize("&cInvalid number!"));
            return;
        }

        if (blocks <= 0) {
            player.sendMessage(MessageUtils.colorize("&cYou must expand by at least 1 block!"));
            return;
        }

        int available = plugin.getDatabaseManager().getExpansionBlocks(player.getUniqueId());
        if (available < blocks) {
            player.sendMessage(MessageUtils.colorize("&cNot enough blocks! You have: " + available));
            return;
        }

        // With a Discord bot configured, staff approve expansions instead of them being instant
        if (plugin.getDiscordManager().isConfigured()) {
            plugin.getDiscordManager().submitExpansionRequest(player, worldName, blocks);
            return;
        }

        int currentDiameter = plugin.getDatabaseManager().getDiameter(worldName);
        plugin.getDatabaseManager().setDiameter(worldName, currentDiameter + blocks);
        plugin.getDatabaseManager().addExpansionBlocks(player.getUniqueId(), -blocks);

        World world = Bukkit.getWorld(worldName);
        if (world != null) world.getWorldBorder().setSize(currentDiameter + blocks);

        player.sendMessage(MessageUtils.colorize("%primary%Expanded world by %secondary%" + blocks + " %primary%blocks!"));
        player.sendMessage(MessageUtils.colorize("%primary%New diameter: %secondary%" + (currentDiameter + blocks)));
    }

    private void handleExpansionBlockAmount(Player player) {
        int blocks = plugin.getDatabaseManager().getExpansionBlocks(player.getUniqueId());
        player.sendMessage(MessageUtils.colorize("%primary%You have %secondary%" + blocks +
                " %primary%blocks to expand with!"));
    }

    private void handleMoveWorldSpawn(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MessageUtils.colorize("&cUsage: /bw MoveWorldSpawn <world>"));
            return;
        }

        String worldName = resolveWorldName(args[1]);
        if (worldName == null) {
            player.sendMessage(MessageUtils.colorize("&cWorld not found!"));
            return;
        }

        if (!plugin.getDatabaseManager().isOwner(worldName, player.getUniqueId()) &&
                !plugin.getDatabaseManager().isMember(worldName, player.getUniqueId())) {
            player.sendMessage(MessageUtils.colorize("&cYou must be a member of this world!"));
            return;
        }

        if (!player.getWorld().getName().equals(worldName)) {
            player.sendMessage(MessageUtils.colorize("&cYou must be inside the world to move its spawn!"));
            return;
        }

        Location loc = player.getLocation();
        Location centeredLoc = loc.clone();
        centeredLoc.setX(Math.floor(loc.getX()) + 0.5);
        centeredLoc.setZ(Math.floor(loc.getZ()) + 0.5);

        plugin.getDatabaseManager().setWorldSpawn(worldName, centeredLoc);
        player.sendMessage(MessageUtils.colorize("%primary%World spawn set to: %secondary%" +
                String.format("%.1f, %.1f, %.1f", centeredLoc.getX(), centeredLoc.getY(), centeredLoc.getZ()) +
                " facing " + String.format("%.0f, %.0f", centeredLoc.getYaw(), centeredLoc.getPitch())));
    }

    private void handleHideWorld(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MessageUtils.colorize("&cUsage: /bw HideWorld <world>"));
            return;
        }

        String worldName = resolveWorldName(args[1]);
        if (worldName == null || !plugin.getDatabaseManager().isBuildWorld(worldName)) {
            player.sendMessage(MessageUtils.colorize("&cWorld not found!"));
            return;
        }

        if (!plugin.getDatabaseManager().isOwner(worldName, player.getUniqueId()) &&
                !player.hasPermission("buildworld.admin")) {
            player.sendMessage(MessageUtils.colorize("&cOnly the world owner can hide their world!"));
            return;
        }

        if (plugin.getDatabaseManager().isHidden(worldName)) {
            player.sendMessage(MessageUtils.colorize("&cThis world is already hidden! Use /bw ShowWorld <world> to make it visible again."));
            return;
        }

        plugin.getDatabaseManager().setHidden(worldName, true);
        String nickname = plugin.getDatabaseManager().getNickname(worldName);
        player.sendMessage(MessageUtils.colorize("%primary%World %secondary%" + nickname +
                " %primary%is now %secondary%HIDDEN%primary%!" +
                " It won't appear in the public menu. Your members can still see it in their personal section."));
    }

    private void handleShowWorld(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MessageUtils.colorize("&cUsage: /bw ShowWorld <world>"));
            return;
        }

        String worldName = resolveWorldName(args[1]);
        if (worldName == null || !plugin.getDatabaseManager().isBuildWorld(worldName)) {
            player.sendMessage(MessageUtils.colorize("&cWorld not found!"));
            return;
        }

        if (!plugin.getDatabaseManager().isOwner(worldName, player.getUniqueId()) &&
                !player.hasPermission("buildworld.admin")) {
            player.sendMessage(MessageUtils.colorize("&cOnly the world owner can change world visibility!"));
            return;
        }

        if (!plugin.getDatabaseManager().isHidden(worldName)) {
            player.sendMessage(MessageUtils.colorize("&cThis world is not hidden!"));
            return;
        }

        plugin.getDatabaseManager().setHidden(worldName, false);
        String nickname = plugin.getDatabaseManager().getNickname(worldName);
        player.sendMessage(MessageUtils.colorize("%primary%World %secondary%" + nickname +
                " %primary%is now %secondary%VISIBLE%primary% again in the public menu."));
    }

    private String resolveWorldName(String input) {
        String byNickname = plugin.getDatabaseManager().getWorldByNickname(input);
        if (byNickname != null) return byNickname;
        if (plugin.getDatabaseManager().isBuildWorld(input)) return input;
        return null;
    }
}