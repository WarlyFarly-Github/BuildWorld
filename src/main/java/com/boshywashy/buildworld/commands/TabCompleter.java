package com.boshywashy.buildworld.commands;

import com.boshywashy.buildworld.BuildWorld;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class TabCompleter implements org.bukkit.command.TabCompleter {
    private final BuildWorld plugin;
    private final List colors = Arrays.asList("black", "dark_blue", "dark_green", "dark_aqua",
            "dark_red", "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua",
            "red", "light_purple", "yellow", "white");
    private final List materials = Arrays.stream(Material.values())
            .map(Material::name)
            .collect(Collectors.toList());

    public TabCompleter(BuildWorld plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("buildworld")) {
            if (args.length == 1) {
                List<String> subs = new ArrayList<>(Arrays.asList(
                        "spawn", "setspawn", "removespawn", "create_world",
                        "nickname", "members", "open", "close", "item",
                        "invite", "join", "retractinvite", "delete",
                        "kick", "leave", "menu", "visit", "home", "info", "help",
                        "ExpandWorld", "ExpansionBlockAmount", "MoveWorldSpawn"));

                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    boolean isOwner = !plugin.getDatabaseManager().getOwnedWorlds(p.getUniqueId()).isEmpty();
                    if (isOwner || p.hasPermission("buildworld.admin")) {
                        subs.add("HideWorld");
                        subs.add("ShowWorld");
                    }
                }
                completions.addAll(subs);

            } else if (args.length == 2) {
                switch (args[0].toLowerCase()) {
                    case "nickname":
                    case "open":
                    case "close":
                    case "item":
                    case "delete":
                    case "info":
                    case "members":
                    case "expandworld":
                    case "moveworldspawn":
                        if (sender instanceof Player) {
                            Player p = (Player) sender;
                            List<String> worlds = plugin.getDatabaseManager().getOwnedWorlds(p.getUniqueId());
                            worlds.addAll(plugin.getDatabaseManager().getMemberWorlds(p.getUniqueId()));
                            for (String worldName : worlds) {
                                String nick = plugin.getDatabaseManager().getNickname(worldName);
                                completions.add(nick != null ? nick : worldName);
                            }
                        }
                        break;

                    case "hideworld":
                        if (sender instanceof Player) {
                            Player p = (Player) sender;
                            List<String> ownedWorlds = plugin.getDatabaseManager().getOwnedWorlds(p.getUniqueId());
                            if (p.hasPermission("buildworld.admin")) {
                                ownedWorlds = plugin.getDatabaseManager().getAllWorlds();
                            }
                            for (String worldName : ownedWorlds) {
                                if (!plugin.getDatabaseManager().isHidden(worldName)) {
                                    String nick = plugin.getDatabaseManager().getNickname(worldName);
                                    completions.add(nick != null ? nick : worldName);
                                }
                            }
                        }
                        break;

                    case "showworld":
                        if (sender instanceof Player) {
                            Player p = (Player) sender;
                            List<String> ownedWorlds = plugin.getDatabaseManager().getOwnedWorlds(p.getUniqueId());
                            if (p.hasPermission("buildworld.admin")) {
                                ownedWorlds = plugin.getDatabaseManager().getAllWorlds();
                            }
                            for (String worldName : ownedWorlds) {
                                if (plugin.getDatabaseManager().isHidden(worldName)) {
                                    String nick = plugin.getDatabaseManager().getNickname(worldName);
                                    completions.add(nick != null ? nick : worldName);
                                }
                            }
                        }
                        break;

                    case "kick":
                        if (sender instanceof Player) {
                            Player p = (Player) sender;
                            for (String worldName : plugin.getDatabaseManager().getOwnedWorlds(p.getUniqueId())) {
                                String nick = plugin.getDatabaseManager().getNickname(worldName);
                                completions.add(nick != null ? nick : worldName);
                            }
                        }
                        break;

                    case "leave":
                        if (sender instanceof Player) {
                            Player p = (Player) sender;
                            List<String> worlds = plugin.getDatabaseManager().getOwnedWorlds(p.getUniqueId());
                            worlds.addAll(plugin.getDatabaseManager().getMemberWorlds(p.getUniqueId()));
                            for (String worldName : worlds) {
                                String nick = plugin.getDatabaseManager().getNickname(worldName);
                                completions.add(nick != null ? nick : worldName);
                            }
                        }
                        break;

                    case "invite":
                    case "retractinvite":
                        completions.addAll(Arrays.asList("member", "owner"));
                        break;

                    case "join":
                    case "visit":
                        for (String worldName : plugin.getDatabaseManager().getAllWorlds()) {
                            if (plugin.getDatabaseManager().isOpen(worldName) || plugin.getDatabaseManager().isInactive(worldName)) {
                                String nick = plugin.getDatabaseManager().getNickname(worldName);
                                completions.add(nick != null ? nick : worldName);
                            }
                        }
                        break;

                    case "home":
                        if (sender instanceof Player) {
                            Player p = (Player) sender;
                            List<String> worlds = plugin.getDatabaseManager().getOwnedWorlds(p.getUniqueId());
                            worlds.addAll(plugin.getDatabaseManager().getMemberWorlds(p.getUniqueId()));
                            for (String worldName : worlds) {
                                String nick = plugin.getDatabaseManager().getNickname(worldName);
                                completions.add(nick != null ? nick : worldName);
                            }
                        }
                        break;

                    case "help":
                        completions.add("commands");
                        plugin.getConfigManager().getTutorialMessages().keySet().forEach(completions::add);
                        break;
                }

            } else if (args.length == 3) {
                switch (args[0].toLowerCase()) {
                    case "nickname":
                        completions.add("<nickname>");
                        break;
                    case "invite":
                    case "retractinvite":
                        if (args[1].equalsIgnoreCase("member") || args[1].equalsIgnoreCase("owner")) {
                            if (sender instanceof Player) {
                                Player p = (Player) sender;
                                for (String worldName : plugin.getDatabaseManager().getOwnedWorlds(p.getUniqueId())) {
                                    String nick = plugin.getDatabaseManager().getNickname(worldName);
                                    completions.add(nick != null ? nick : worldName);
                                }
                            }
                        }
                        break;
                    case "item":
                        completions.addAll(materials);
                        break;
                    case "delete":
                        completions.add("confirm");
                        break;
                    case "kick":
                        if (sender instanceof Player) {
                            Player p = (Player) sender;
                            String worldName = resolveWorldName(args[1]);
                            if (worldName != null && plugin.getDatabaseManager().isBuildWorld(worldName)) {
                                for (UUID memberUUID : plugin.getDatabaseManager().getMembers(worldName)) {
                                    if (plugin.getDatabaseManager().isOwner(worldName, memberUUID)) continue;
                                    String name = Bukkit.getOfflinePlayer(memberUUID).getName();
                                    if (name != null) completions.add(name);
                                }
                            }
                        }
                        break;
                }

            } else if (args.length == 4) {
                switch (args[0].toLowerCase()) {
                    case "invite":
                        Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
                        break;
                    case "retractinvite":
                        String worldName = resolveWorldName(args[2]);
                        if (worldName != null) {
                            for (UUID uuid : plugin.getDatabaseManager().getInvitedPlayers(worldName)) {
                                String name = Bukkit.getOfflinePlayer(uuid).getName();
                                if (name != null) completions.add(name);
                            }
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                if (plugin.getDatabaseManager().getInviteRole(worldName, p.getUniqueId()) != null) {
                                    if (!completions.contains(p.getName())) completions.add(p.getName());
                                }
                            }
                        }
                        break;
                    case "delete":
                        if (args[2].equalsIgnoreCase("confirm")) completions.add("confirm");
                        break;
                }
            }

        } else if (command.getName().equalsIgnoreCase("buildworldadmin")) {
            if (args.length == 1) {
                completions.addAll(Arrays.asList(
                        "SetMaxWorld", "SetWorldDiameter", "MemberEdit",
                        "colour", "TutorialAdd", "TutorialRemove", "TutorialEdit",
                        "GiveBlocksForExpansion", "RemoveBlocksForExpansion",
                        "OnlineWorldExpansion", "BlockGainTime", "CheckInterval",
                        "GiveWorldCredits", "DeleteWorld", "Maintenance", "visit", "inactiveworld"));

            } else if (args.length == 2) {
                switch (args[0].toLowerCase()) {
                    case "setmaxworld":
                        completions.addAll(Arrays.asList("1", "2", "3", "5", "10"));
                        break;
                    case "setworlddiameter":
                        completions.addAll(Arrays.asList("100", "200", "500", "1000"));
                        break;
                    case "memberedit":
                        completions.addAll(Arrays.asList("member", "owner"));
                        break;
                    case "colour":
                    case "color":
                        completions.addAll(Arrays.asList("primary", "secondary"));
                        break;
                    case "onlineworldexpansion":
                    case "maintenance":
                        completions.addAll(Arrays.asList("true", "false"));
                        break;
                    case "blockgaintime":
                        completions.addAll(Arrays.asList("5", "10", "20", "50", "100"));
                        break;
                    case "checkinterval":
                        completions.addAll(Arrays.asList("30", "60", "120", "300"));
                        break;
                    case "giveblocksforexpansion":
                    case "removeblocksforexpansion":
                    case "giveworldcredits":
                        for (Player p : Bukkit.getOnlinePlayers()) completions.add(p.getName());
                        break;
                    case "deleteworld":
                    case "visit":
                        for (String worldName : plugin.getDatabaseManager().getAllWorlds()) {
                            String nick = plugin.getDatabaseManager().getNickname(worldName);
                            completions.add(nick != null ? nick : worldName);
                        }
                        break;
                    case "inactiveworld":
                        completions.addAll(Arrays.asList("set", "timer", "activate"));
                        break;
                    case "tutorialadd":
                        completions.addAll(Arrays.asList("build", "worldedit", "axiom", "members"));
                        break;
                    case "tutorialedit":
                        completions.addAll(plugin.getConfigManager().getTutorialMessages().keySet());
                        break;
                    case "tutorialremove":
                        completions.addAll(plugin.getConfigManager().getTutorialMessages().keySet().stream()
                                .filter(k -> !k.equals("commands"))
                                .collect(Collectors.toList()));
                        break;
                }

            } else if (args.length == 3) {
                switch (args[0].toLowerCase()) {
                    case "memberedit":
                        completions.addAll(Arrays.asList("add", "remove"));
                        break;
                    case "colour":
                    case "color":
                        completions.addAll(colors);
                        break;
                    case "giveblocksforexpansion":
                    case "removeblocksforexpansion":
                    case "giveworldcredits":
                        completions.addAll(Arrays.asList("1", "2", "3", "5", "10"));
                        break;
                    case "inactiveworld":
                        String subAction = args[1].toLowerCase();
                        if (subAction.equals("set")) {
                            for (String w : plugin.getDatabaseManager().getActiveWorlds()) {
                                String nick = plugin.getDatabaseManager().getNickname(w);
                                completions.add(nick != null ? nick : w);
                            }
                        } else if (subAction.equals("timer")) {
                            for (String w : plugin.getDatabaseManager().getAllWorlds()) {
                                String nick = plugin.getDatabaseManager().getNickname(w);
                                completions.add(nick != null ? nick : w);
                            }
                        } else if (subAction.equals("activate")) {
                            for (String w : plugin.getDatabaseManager().getInactiveWorlds()) {
                                String nick = plugin.getDatabaseManager().getNickname(w);
                                completions.add(nick != null ? nick : w);
                            }
                        }
                        break;
                }

            } else if (args.length == 4) {
                switch (args[0].toLowerCase()) {
                    case "memberedit":
                        if (args[2].equalsIgnoreCase("add")) {
                            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
                        } else if (args[2].equalsIgnoreCase("remove")) {
                            if (sender instanceof Player) {
                                String world = ((Player) sender).getWorld().getName();
                                if (plugin.getDatabaseManager().isBuildWorld(world)) {
                                    if (args[1].equalsIgnoreCase("member")) {
                                        completions.addAll(plugin.getDatabaseManager().getMembers(world).stream()
                                                .map(uuid -> Bukkit.getOfflinePlayer(uuid).getName())
                                                .collect(Collectors.toList()));
                                    } else if (args[1].equalsIgnoreCase("owner")) {
                                        completions.addAll(plugin.getDatabaseManager().getOwners(world).stream()
                                                .map(uuid -> Bukkit.getOfflinePlayer(uuid).getName())
                                                .collect(Collectors.toList()));
                                    }
                                }
                            }
                        }
                        break;
                }
            }
        }

        return completions.stream()
                .filter(s -> s != null && s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }

    private String resolveWorldName(String input) {
        String byNickname = plugin.getDatabaseManager().getWorldByNickname(input);
        if (byNickname != null) return byNickname;
        if (plugin.getDatabaseManager().isBuildWorld(input)) return input;
        return null;
    }


}