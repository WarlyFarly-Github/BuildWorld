package com.boshywashy.buildworld.commands;

import com.boshywashy.buildworld.BuildWorld;
import com.boshywashy.buildworld.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class AdminCommand implements CommandExecutor {
    private final BuildWorld plugin;
    private final List<String> colors = Arrays.asList("BLACK", "DARK_BLUE", "DARK_GREEN", "DARK_AQUA",
            "DARK_RED", "DARK_PURPLE", "GOLD", "GRAY", "DARK_GRAY", "BLUE", "GREEN", "AQUA",
            "RED", "LIGHT_PURPLE", "YELLOW", "WHITE");

    private final java.util.Map<String, String> colorCodes = new java.util.HashMap<String, String>() {{
        put("BLACK", "0");
        put("DARK_BLUE", "1");
        put("DARK_GREEN", "2");
        put("DARK_AQUA", "3");
        put("DARK_RED", "4");
        put("DARK_PURPLE", "5");
        put("GOLD", "6");
        put("GRAY", "7");
        put("DARK_GRAY", "8");
        put("BLUE", "9");
        put("GREEN", "a");
        put("AQUA", "b");
        put("RED", "c");
        put("LIGHT_PURPLE", "d");
        put("YELLOW", "e");
        put("WHITE", "f");
    }};

    public AdminCommand(BuildWorld plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("buildworld.admin")) {
            sender.sendMessage(MessageUtils.colorize("&cNo permission!"));
            return true;
        }

        if (args.length == 0) {
            sendAdminHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "setmaxworld":
                handleSetMaxWorld(sender, args);
                break;
            case "setworlddiameter":
                handleSetWorldDiameter(sender, args);
                break;
            case "memberedit":
                handleMemberEdit(sender, args);
                break;
            case "colour":
            case "color":
                handleColor(sender, args);
                break;
            case "tutorialadd":
                handleTutorialAdd(sender, args);
                break;
            case "tutorialremove":
                handleTutorialRemove(sender, args);
                break;
            case "tutorialedit":
                handleTutorialEdit(sender, args);
                break;
            case "giveblocksforexpansion":
                handleGiveBlocks(sender, args);
                break;
            case "removeblocksforexpansion":
                handleRemoveBlocks(sender, args);
                break;
            case "onlineworldexpansion":
                handleOnlineExpansion(sender, args);
                break;
            case "blockgaintime":
                handleBlockGainTime(sender, args);
                break;
            case "checkinterval":
                handleCheckInterval(sender, args);
                break;
            case "giveworldcredits":
                handleGiveWorldCredits(sender, args);
                break;
            case "deleteworld":
                handleDeleteWorld(sender, args);
                break;
            case "maintenance":
                handleMaintenance(sender, args);
                break;
            case "visit":
                handleAdminVisit(sender, args);
                break;
            case "inactiveworld":
                handleInactiveWorld(sender, args);
                break;
            default:
                sendAdminHelp(sender);
                break;
        }

        return true;
    }

    private void handleInactiveWorld(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa inactiveWorld <set|timer|activate> <world>"));
            return;
        }

        String action = args[1].toLowerCase();
        if (action.equals("set")) {
            if (args.length < 3) {
                sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa inactiveWorld set <activeWorldName>"));
                return;
            }
            String worldName = resolveWorldName(args[2]);
            if (worldName == null || !plugin.getDatabaseManager().isBuildWorld(worldName)) {
                sender.sendMessage(MessageUtils.colorize("&cWorld not found in BuildWorld database!"));
                return;
            }
            String spawnWorld = plugin.getDatabaseManager().getSpawnWorld();
            if (spawnWorld != null && worldName.equalsIgnoreCase(spawnWorld)) {
                sender.sendMessage(MessageUtils.colorize("&cThe server spawn world cannot be set to inactive!"));
                return;
            }
            if (plugin.getDatabaseManager().isInactive(worldName)) {
                sender.sendMessage(MessageUtils.colorize("&cThat world is already inactive!"));
                return;
            }

            World world = Bukkit.getWorld(worldName);
            if (world != null && !world.getPlayers().isEmpty()) {
                Location serverSpawn = plugin.getDatabaseManager().getSpawnLocation();
                if (serverSpawn == null) serverSpawn = Bukkit.getWorlds().get(0).getSpawnLocation();
                for (Player p : world.getPlayers()) {
                    p.teleport(serverSpawn);
                    p.sendMessage(MessageUtils.colorize("&cThe world you were in was set to inactive by an admin."));
                }
            }

            sender.sendMessage(MessageUtils.colorize("&eMoving world to inactive folder..."));
            plugin.getWorldManager().moveToInactive(worldName, () ->
                    sender.sendMessage(MessageUtils.colorize("&aWorld " + worldName + " has been set to inactive.")));
        } else if (action.equals("timer")) {
            if (args.length < 3) {
                sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa inactiveWorld timer <worldName>"));
                return;
            }
            String worldName = resolveWorldName(args[2]);
            if (worldName == null || !plugin.getDatabaseManager().isBuildWorld(worldName)) {
                sender.sendMessage(MessageUtils.colorize("&cWorld not found in BuildWorld database!"));
                return;
            }
            String spawnWorld = plugin.getDatabaseManager().getSpawnWorld();
            if (spawnWorld != null && worldName.equalsIgnoreCase(spawnWorld)) {
                sender.sendMessage(MessageUtils.colorize("&eWorld %secondary%" + worldName + " &eis set as the server spawn world and &bwill never go inactive&e."));
                return;
            }
            if (plugin.getDatabaseManager().isInactive(worldName)) {
                sender.sendMessage(MessageUtils.colorize("&eWorld %secondary%" + worldName + " &eis currently &cINACTIVE&e."));
                return;
            }
            long secondsLeft = plugin.getWorldManager().getTimeUntilInactiveSeconds(worldName);
            if (secondsLeft < 0) {
                sender.sendMessage(MessageUtils.colorize("&eWorld currently has players in it. Timer is paused while occupied."));
            } else {
                long minutes = secondsLeft / 60;
                long seconds = secondsLeft % 60;
                sender.sendMessage(MessageUtils.colorize("&eTime until %secondary%" + worldName + " &egoes inactive: &b" + minutes + "m " + seconds + "s"));
            }
        } else if (action.equals("activate")) {
            if (args.length < 3) {
                sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa inactiveWorld activate <inactiveWorld>"));
                return;
            }
            String worldName = resolveWorldName(args[2]);
            if (worldName == null || !plugin.getDatabaseManager().isBuildWorld(worldName)) {
                sender.sendMessage(MessageUtils.colorize("&cWorld not found in BuildWorld database!"));
                return;
            }
            if (!plugin.getDatabaseManager().isInactive(worldName)) {
                sender.sendMessage(MessageUtils.colorize("&cThat world is already active!"));
                return;
            }
            sender.sendMessage(MessageUtils.colorize("&eActivating world and moving to root... Please wait."));
            plugin.getWorldManager().activateWorld(worldName, () ->
                    sender.sendMessage(MessageUtils.colorize("&aWorld " + worldName + " has been activated successfully!")));
        } else {
            sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa inactiveWorld <set|timer|activate> <world>"));
        }
    }

    private void handleAdminVisit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Player only!");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa visit <world>"));
            return;
        }

        Player player = (Player) sender;
        String worldName = resolveWorldName(args[1]);

        if (worldName == null || !plugin.getDatabaseManager().isBuildWorld(worldName)) {
            sender.sendMessage(MessageUtils.colorize("&cWorld not found in the BuildWorld database!"));
            return;
        }

        if (plugin.getDatabaseManager().isInactive(worldName)) {
            sender.sendMessage(MessageUtils.colorize("&eWorld is inactive and loading up... Please wait."));
            plugin.getWorldManager().activateWorld(worldName, () -> {
                if (!player.isOnline()) return;
                World world = plugin.getWorldManager().ensureWorldLoaded(worldName);
                if (world != null) {
                    Location spawnLoc = plugin.getDatabaseManager().getWorldSpawn(worldName);
                    player.teleport(spawnLoc);
                    String nickname = plugin.getDatabaseManager().getNickname(worldName);
                    sender.sendMessage(MessageUtils.colorize("%primary%Teleported to %secondary%" + nickname +
                            " %primary%(" + worldName + ")"));
                } else {
                    sender.sendMessage(MessageUtils.colorize("&cFailed to load world!"));
                }
            });
            return;
        }

        World world = plugin.getWorldManager().ensureWorldLoaded(worldName);
        if (world == null) {
            sender.sendMessage(MessageUtils.colorize("&cFailed to load world!"));
            return;
        }

        Location spawnLoc = plugin.getDatabaseManager().getWorldSpawn(worldName);
        player.teleport(spawnLoc);

        String nickname = plugin.getDatabaseManager().getNickname(worldName);
        sender.sendMessage(MessageUtils.colorize("%primary%Teleported to %secondary%" + nickname +
                " %primary%(" + worldName + ")"));
    }

    private void handleSetMaxWorld(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa SetMaxWorld <number>"));
            return;
        }

        try {
            int max = Integer.parseInt(args[1]);
            plugin.getConfig().set("World.MaxWorldsPerPlayer", max);
            plugin.saveConfig();
            sender.sendMessage(MessageUtils.colorize("%primary%Max worlds set to: %secondary%" + max));
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtils.colorize("&cInvalid number!"));
        }
    }

    private void handleSetWorldDiameter(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa SetWorldDiameter <blocks>"));
            return;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Player only!");
            return;
        }

        Player player = (Player) sender;
        String worldName = player.getWorld().getName();

        if (!plugin.getDatabaseManager().isBuildWorld(worldName)) {
            sender.sendMessage(MessageUtils.colorize("&cYou must be in a build world!"));
            return;
        }

        try {
            int diameter = Integer.parseInt(args[1]);
            plugin.getDatabaseManager().setDiameter(worldName, diameter);
            World world = Bukkit.getWorld(worldName);
            if (world != null) world.getWorldBorder().setSize(diameter);
            sender.sendMessage(MessageUtils.colorize("%primary%Diameter set to: %secondary%" + diameter));
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtils.colorize("&cInvalid number!"));
        }
    }

    private void handleMemberEdit(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa MemberEdit <member|owner> <add|remove> <player>"));
            return;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Player only!");
            return;
        }

        Player player = (Player) sender;
        String worldName = player.getWorld().getName();

        if (!plugin.getDatabaseManager().isBuildWorld(worldName)) {
            sender.sendMessage(MessageUtils.colorize("&cYou must be in a build world!"));
            return;
        }

        String type = args[1].toLowerCase();
        String action = args[2].toLowerCase();
        String targetName = args[3];

        @SuppressWarnings("deprecation")
        UUID targetUUID = Bukkit.getOfflinePlayer(targetName).getUniqueId();

        if (action.equals("add")) {
            String role = type.equals("owner") ? "OWNER" : "MEMBER";
            plugin.getDatabaseManager().addMember(worldName, targetUUID, role);

            Player targetPlayer = Bukkit.getPlayer(targetUUID);
            if (targetPlayer != null && targetPlayer.isOnline() && targetPlayer.getWorld().getName().equals(worldName)) {
                plugin.getPermissionManager().applyWorldPermissions(targetPlayer, worldName);
            }

            sender.sendMessage(MessageUtils.colorize("%primary%Added %secondary%" + targetName +
                    " %primary%as %secondary%" + role));
        } else if (action.equals("remove")) {
            plugin.getDatabaseManager().removeMember(worldName, targetUUID);

            Player targetPlayer = Bukkit.getPlayer(targetUUID);
            if (targetPlayer != null && targetPlayer.isOnline() && targetPlayer.getWorld().getName().equals(worldName)) {
                plugin.getPermissionManager().removePermissions(targetPlayer);
            }

            sender.sendMessage(MessageUtils.colorize("%primary%Removed %secondary%" + targetName));
        }
    }

    private void handleColor(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa colour <primary|secondary> <color>"));
            return;
        }

        String type = args[1].toLowerCase();
        String colorName = args[2].toUpperCase();

        if (!colors.contains(colorName)) {
            sender.sendMessage(MessageUtils.colorize("&cInvalid color! Valid: " + String.join(", ", colors)));
            return;
        }

        String code = colorCodes.getOrDefault(colorName, "f");

        if (type.equals("primary")) {
            plugin.getConfig().set("Colors.Primary", code);
        } else {
            plugin.getConfig().set("Colors.Secondary", code);
        }
        plugin.saveConfig();
        plugin.getConfigManager().reload();

        sender.sendMessage(MessageUtils.colorize("%primary%" + type + " color set to: %secondary%" + colorName + " (code: " + code + ")"));
    }

    private void handleTutorialAdd(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa TutorialAdd <subcommand> <message>"));
            return;
        }

        String sub = args[1].toLowerCase();
        StringBuilder message = new StringBuilder();
        for (int i = 2; i < args.length; i++) message.append(args[i]).append(" ");

        plugin.getConfig().set("Tutorials." + sub, message.toString().trim());
        plugin.saveConfig();
        sender.sendMessage(MessageUtils.colorize("%primary%Tutorial added for: %secondary%" + sub));
    }

    private void handleTutorialRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa TutorialRemove <subcommand>"));
            return;
        }

        String sub = args[1].toLowerCase();
        if (sub.equals("commands")) {
            sender.sendMessage(MessageUtils.colorize("&cCannot remove default commands help!"));
            return;
        }

        plugin.getConfig().set("Tutorials." + sub, null);
        plugin.saveConfig();
        sender.sendMessage(MessageUtils.colorize("%primary%Tutorial removed for: %secondary%" + sub));
    }

    private void handleTutorialEdit(CommandSender sender, String[] args) {
        handleTutorialAdd(sender, args);
    }

    private void handleGiveBlocks(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa GiveBlocksForExpansion <player> <amount>"));
            return;
        }

        @SuppressWarnings("deprecation")
        UUID targetUUID = Bukkit.getOfflinePlayer(args[1]).getUniqueId();

        try {
            int amount = Integer.parseInt(args[2]);
            plugin.getDatabaseManager().addExpansionBlocks(targetUUID, amount);
            sender.sendMessage(MessageUtils.colorize("%primary%Gave %secondary%" + args[1] + " " + amount +
                    " %primary%expansion blocks"));
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtils.colorize("&cInvalid number!"));
        }
    }

    private void handleRemoveBlocks(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa RemoveBlocksForExpansion <player> <amount>"));
            return;
        }

        @SuppressWarnings("deprecation")
        UUID targetUUID = Bukkit.getOfflinePlayer(args[1]).getUniqueId();

        try {
            int amount = Integer.parseInt(args[2]);
            int current = plugin.getDatabaseManager().getExpansionBlocks(targetUUID);

            if (amount > current) {
                sender.sendMessage(MessageUtils.colorize("&cPlayer only has " + current + " blocks! Removing all..."));
                amount = current;
            }

            plugin.getDatabaseManager().addExpansionBlocks(targetUUID, -amount);
            sender.sendMessage(MessageUtils.colorize("%primary%Removed %secondary%" + amount +
                    " %primary%blocks from %secondary%" + args[1]));
            sender.sendMessage(MessageUtils.colorize("%primary%New balance: %secondary%" +
                    plugin.getDatabaseManager().getExpansionBlocks(targetUUID)));
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtils.colorize("&cInvalid number!"));
        }
    }

    private void handleOnlineExpansion(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa OnlineWorldExpansion <true|false>"));
            return;
        }

        boolean enabled = Boolean.parseBoolean(args[1]);
        plugin.getConfig().set("OnlineWorldExpansion.Enabled", enabled);
        plugin.saveConfig();

        if (enabled) {
            plugin.getExpansionManager().startExpansionTask();
        } else {
            plugin.getExpansionManager().stopExpansionTask();
        }

        sender.sendMessage(MessageUtils.colorize("%primary%Online expansion set to: %secondary%" + enabled));
    }

    private void handleBlockGainTime(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa BlockGainTime <blocks>"));
            return;
        }

        try {
            int blocks = Integer.parseInt(args[1]);
            plugin.getConfig().set("OnlineWorldExpansion.BlockGainPerMin", blocks);
            plugin.saveConfig();
            sender.sendMessage(MessageUtils.colorize("%primary%Block gain per minute set to: %secondary%" + blocks));
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtils.colorize("&cInvalid number!"));
        }
    }

    private void handleCheckInterval(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa CheckInterval <seconds>"));
            return;
        }

        try {
            int seconds = Integer.parseInt(args[1]);
            if (seconds < 10) {
                sender.sendMessage(MessageUtils.colorize("&cInterval must be at least 10 seconds!"));
                return;
            }
            plugin.getConfig().set("OnlineWorldExpansion.CheckInterval", seconds);
            plugin.saveConfig();

            if (plugin.getConfigManager().isOnlineExpansionEnabled()) {
                plugin.getExpansionManager().stopExpansionTask();
                plugin.getExpansionManager().startExpansionTask();
            }

            sender.sendMessage(MessageUtils.colorize("%primary%Check interval set to: %secondary%" + seconds + " seconds"));
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtils.colorize("&cInvalid number!"));
        }
    }

    private void handleGiveWorldCredits(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa GiveWorldCredits <player> <amount>"));
            return;
        }

        @SuppressWarnings("deprecation")
        UUID targetUUID = Bukkit.getOfflinePlayer(args[1]).getUniqueId();

        try {
            int amount = Integer.parseInt(args[2]);
            plugin.getDatabaseManager().addExtraWorldCredits(targetUUID, amount);
            int newTotal = plugin.getDatabaseManager().getExtraWorldCredits(targetUUID);
            sender.sendMessage(MessageUtils.colorize("%primary%Gave %secondary%" + args[1] + " " + amount +
                    " %primary%extra world credits"));
            sender.sendMessage(MessageUtils.colorize("%primary%They can now create %secondary%" + newTotal +
                    " %primary%extra worlds"));
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtils.colorize("&cInvalid number!"));
        }
    }

    private void handleDeleteWorld(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa DeleteWorld <world>"));
            return;
        }

        String worldName = resolveWorldName(args[1]);
        if (worldName == null) {
            sender.sendMessage(MessageUtils.colorize("&cWorld not found!"));
            return;
        }

        if (!plugin.getDatabaseManager().isBuildWorld(worldName)) {
            sender.sendMessage(MessageUtils.colorize("&cThis is not a build world!"));
            return;
        }

        plugin.getWorldManager().deleteWorld(worldName, sender instanceof Player ? ((Player) sender).getUniqueId() : null);
        sender.sendMessage(MessageUtils.colorize("%primary%World %secondary%" + worldName + " %primary%has been deleted!"));
    }

    private void handleMaintenance(CommandSender sender, String[] args) {
        if (args.length < 2) {
            boolean current = plugin.getConfig().getBoolean("Maintenance.Enabled", false);
            sender.sendMessage(MessageUtils.colorize("%primary%Maintenance mode is currently: %secondary%" + (current ? "ENABLED" : "DISABLED")));
            sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa Maintenance <true|false>"));
            return;
        }

        boolean enabled = Boolean.parseBoolean(args[1]);
        plugin.getConfig().set("Maintenance.Enabled", enabled);
        plugin.saveConfig();

        if (enabled) {
            String kickMessage = plugin.getConfig().getString("Maintenance.KickMessage",
                    "&cThe BuildWorld system is currently under maintenance. Please try again later.");
            String spawnWorldName = plugin.getDatabaseManager().getSpawnWorld();
            if (spawnWorldName != null && Bukkit.getWorld(spawnWorldName) == null) {
                Bukkit.createWorld(new org.bukkit.WorldCreator(spawnWorldName));
            }
            for (World world : Bukkit.getWorlds()) {
                if (plugin.getDatabaseManager().isBuildWorld(world.getName())) {
                    Location serverSpawn = plugin.getDatabaseManager().getSpawnLocation();
                    if (serverSpawn == null) serverSpawn = Bukkit.getWorlds().get(0).getSpawnLocation();

                    for (Player p : world.getPlayers()) {
                        if (!p.hasPermission("buildworld.admin")) {
                            p.teleport(serverSpawn);
                            p.sendMessage(MessageUtils.colorize(kickMessage));
                        }
                    }
                }
            }
            Bukkit.broadcastMessage(MessageUtils.colorize("&c&l[BuildWorld] &cMaintenance mode has been ENABLED."));
        } else {
            Bukkit.broadcastMessage(MessageUtils.colorize("&a&l[BuildWorld] &aMaintenance mode has been DISABLED. BuildWorld is now fully operational."));
        }

        sender.sendMessage(MessageUtils.colorize("%primary%Maintenance mode set to: %secondary%" + enabled));
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage(MessageUtils.colorize("%primary%=== Admin Commands ==="));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa SetMaxWorld <num>"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa SetWorldDiameter <blocks>"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa MemberEdit <type> <add|remove> <player>"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa colour <primary|secondary> <color>"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa TutorialAdd <sub> <msg>"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa TutorialRemove <sub>"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa TutorialEdit <sub> <msg>"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa GiveBlocksForExpansion <player> <amount>"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa RemoveBlocksForExpansion <player> <amount>"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa OnlineWorldExpansion <true|false>"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa BlockGainTime <blocks>"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa CheckInterval <seconds>"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa GiveWorldCredits <player> <amount>"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa DeleteWorld <world>"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa Maintenance <true|false>"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa visit <world> &7- Teleport to any world's spawn"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa inactiveWorld set <world> &7- Set world inactive"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa inactiveWorld timer <world> &7- Check inactive timer"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa inactiveWorld activate <world> &7- Activate world"));
    }

    private String resolveWorldName(String input) {
        String byNickname = plugin.getDatabaseManager().getWorldByNickname(input);
        if (byNickname != null) return byNickname;
        if (plugin.getDatabaseManager().isBuildWorld(input)) return input;
        return null;
    }
}