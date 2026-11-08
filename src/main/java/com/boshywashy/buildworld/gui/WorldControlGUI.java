package com.boshywashy.buildworld.gui;

import com.boshywashy.buildworld.BuildWorld;
import com.boshywashy.buildworld.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class WorldControlGUI implements Listener {
    private final BuildWorld plugin;
    private final Map<UUID, String> openWorldMap = new HashMap<>();

    public WorldControlGUI(BuildWorld plugin) {
        this.plugin = plugin;
    }

    public void openMenu(Player player, String worldName) {
        openWorldMap.put(player.getUniqueId(), worldName);
        String nickname = plugin.getDatabaseManager().getNickname(worldName);
        boolean isOwner = plugin.getDatabaseManager().isOwner(worldName, player.getUniqueId()) || player.hasPermission("buildworld.admin");
        boolean isMember = plugin.getDatabaseManager().isMember(worldName, player.getUniqueId());

        Inventory inv = Bukkit.createInventory(null, 54, MessageUtils.colorize("&l%primary%" + nickname + " Control"));

        // Fill background
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null));
        }

        // Layout matching reference image:
        // Row 1 (Slots 9, 11, 13, 15, 17)
        if (isOwner) {
            boolean isOpen = plugin.getDatabaseManager().isOpen(worldName);
            inv.setItem(9, createItem(isOpen ? Material.OAK_DOOR : Material.IRON_DOOR,
                    "%primary%Toggle Open Status",
                    Arrays.asList("%secondary%Status: " + (isOpen ? "&aOPEN" : "&cCLOSED"), "%secondary%Click to toggle public access.")));
        }

        if (player.hasPermission("buildworld.use")) {
            inv.setItem(11, createItem(Material.ENDER_PEARL, "%primary%Teleport to World Spawn",
                    Arrays.asList("%secondary%Click to teleport directly to", "%secondary%this world's spawn point.")));
        }

        if (isOwner) {
            inv.setItem(13, createItem(Material.NAME_TAG, "%primary%Set World Nickname",
                    Arrays.asList("%secondary%Click to auto-fill command", "%secondary%in chat to set nickname.")));

            inv.setItem(15, createItem(Material.PLAYER_HEAD, "%primary%Manage Players",
                    Collections.singletonList(MessageUtils.colorize("%secondary%Kick members or change members to be co-owners for this world"))));

            int available = plugin.getDatabaseManager().getExpansionBlocks(player.getUniqueId());
            inv.setItem(17, createItem(Material.BEACON, "%primary%Expand World Border",
                    Arrays.asList("%secondary%Available blocks: &b" + available, "%secondary%Click to auto-fill command", "%secondary%to expand world border.")));
        }

        // Row 3 (Slots 27, 29, 31, 33, 35)
        if (isOwner) {
            boolean isHidden = plugin.getDatabaseManager().isHidden(worldName);
            inv.setItem(27, createItem(Material.ENDER_EYE, "%primary%Toggle Menu Visibility",
                    Arrays.asList("%secondary%Hidden: " + (isHidden ? "&cYES" : "&aNO"), "%secondary%Click to hide/show from public list.")));
        }

        if (isOwner || isMember) {
            inv.setItem(29, createItem(Material.COMPASS, "%primary%Set World Spawn Here",
                    Arrays.asList("%secondary%Set this world's spawn location", "%secondary%to your current position.")));
        }

        if (isOwner) {
            inv.setItem(31, createItem(Material.ITEM_FRAME, "%primary%Set GUI Item",
                    Arrays.asList("%secondary%Set world menu icon to", "%secondary%the item in your hand.")));

            inv.setItem(33, createItem(Material.PAPER, "%primary%Invite Player",
                    Collections.singletonList(MessageUtils.colorize("%secondary%Invite a player to be able to build in your world"))));

            inv.setItem(35, createItem(Material.TNT, "&c&lDelete World",
                    Arrays.asList("%secondary%Click to open confirmation menu", "%secondary%to permanently delete this world.")));
        } else if (isMember) {
            inv.setItem(35, createItem(Material.REDSTONE_BLOCK, "&cLeave World",
                    Collections.singletonList(MessageUtils.colorize("%secondary%Remove yourself from this world."))));
        }

        // Row 5 (Slot 49: Book, Slot 53: Return Door)
        inv.setItem(49, createItem(Material.BOOK, "%primary%View World Info",
                Arrays.asList("%secondary%View detailed information", "%secondary%and members of this world.")));

        ItemStack returnDoor = new ItemStack(Material.SPRUCE_DOOR);
        ItemMeta doorMeta = returnDoor.getItemMeta();
        doorMeta.setDisplayName(MessageUtils.colorize("&cReturn"));
        doorMeta.setLore(Collections.singletonList(MessageUtils.colorize("%secondary%Return to the previous page.")));
        returnDoor.setItemMeta(doorMeta);
        inv.setItem(53, returnDoor);

        player.openInventory(inv);
    }

    private ItemStack createItem(Material mat, String title, List<String> loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(MessageUtils.colorize(title));
        if (loreLines != null && !loreLines.isEmpty()) {
            List<String> coloredLore = new ArrayList<>();
            for (String l : loreLines) coloredLore.add(MessageUtils.colorize(l));
            meta.setLore(coloredLore);
        }
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().contains(" Control")) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        Player player = (Player) event.getWhoClicked();
        String worldName = openWorldMap.get(player.getUniqueId());
        if (worldName == null) return;

        Material type = clicked.getType();

        if (type == Material.SPRUCE_DOOR) {
            plugin.getWorldMenuGUI().openMenu(player, 1);
            return;
        }

        if (type == Material.ENDER_PEARL) {
            player.closeInventory();
            if (plugin.getDatabaseManager().isInactive(worldName)) {
                player.sendMessage(MessageUtils.colorize("&eWorld is inactive and loading up... Please wait."));
                String finalWorldName = worldName;
                plugin.getWorldManager().activateWorld(worldName, () -> {
                    if (!player.isOnline()) return;
                    World world = plugin.getWorldManager().ensureWorldLoaded(finalWorldName);
                    if (world != null) {
                        Location spawnLoc = plugin.getDatabaseManager().getWorldSpawn(finalWorldName);
                        player.teleport(spawnLoc);
                        player.sendMessage(MessageUtils.colorize("%primary%Teleported to world spawn!"));
                    }
                });
            } else {
                World world = plugin.getWorldManager().ensureWorldLoaded(worldName);
                if (world != null) {
                    Location spawnLoc = plugin.getDatabaseManager().getWorldSpawn(worldName);
                    player.teleport(spawnLoc);
                    player.sendMessage(MessageUtils.colorize("%primary%Teleported to world spawn!"));
                }
            }
            return;
        }

        if (type == Material.BOOK) {
            player.performCommand("bw info " + worldName);
            player.closeInventory();
            return;
        }

        if (type == Material.COMPASS) {
            if (player.getWorld().getName().equals(worldName)) {
                player.performCommand("bw moveworldspawn " + worldName);
            } else {
                player.sendMessage(MessageUtils.colorize("&cYou must be inside the world to move its spawn!"));
            }
            player.closeInventory();
            return;
        }

        if (type == Material.OAK_DOOR || type == Material.IRON_DOOR) {
            boolean current = plugin.getDatabaseManager().isOpen(worldName);
            if (current) {
                player.performCommand("bw close " + worldName);
            } else {
                player.performCommand("bw open " + worldName);
            }
            openMenu(player, worldName);
            return;
        }

        if (type == Material.ENDER_EYE) {
            boolean hidden = plugin.getDatabaseManager().isHidden(worldName);
            if (hidden) {
                player.performCommand("bw showworld " + worldName);
            } else {
                player.performCommand("bw hideworld " + worldName);
            }
            openMenu(player, worldName);
            return;
        }

        if (type == Material.ITEM_FRAME) {
            player.performCommand("bw item " + worldName);
            openMenu(player, worldName);
            return;
        }

        if (type == Material.NAME_TAG) {
            player.closeInventory();
            String nickname = plugin.getDatabaseManager().getNickname(worldName);
            net.md_5.bungee.api.chat.TextComponent message = new net.md_5.bungee.api.chat.TextComponent(
                    MessageUtils.colorize("&e[Click here to auto-fill command]")
            );
            message.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.SUGGEST_COMMAND,
                    "/bw nickname " + (nickname != null ? nickname : worldName) + " "
            ));
            player.sendMessage(MessageUtils.colorize("%primary%Click below to auto-fill the nickname command:"));
            player.spigot().sendMessage(message);
            return;
        }

        if (type == Material.PAPER) {
            plugin.getInvitePlayerGUI().openMenu(player, worldName, 1);
            return;
        }

        if (type == Material.PLAYER_HEAD) {
            plugin.getManagePlayersGUI().openMenu(player, worldName);
            return;
        }

        if (type == Material.TNT) {
            plugin.getDeleteConfirmGUI().openMenu(player, worldName);
            return;
        }

        if (type == Material.REDSTONE_BLOCK) {
            player.performCommand("bw leave " + worldName);
            player.closeInventory();
            return;
        }

        if (type == Material.BEACON) {
            player.closeInventory();
            net.md_5.bungee.api.chat.TextComponent message = new net.md_5.bungee.api.chat.TextComponent(
                    MessageUtils.colorize("&e[Click here to auto-fill command]")
            );
            message.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.SUGGEST_COMMAND,
                    "/bw ExpandWorld " + worldName + " "
            ));
            player.sendMessage(MessageUtils.colorize("%primary%Click below to auto-fill the expand world command:"));
            player.spigot().sendMessage(message);
        }
    }
}