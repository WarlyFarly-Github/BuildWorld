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

import java.util.Collections;
import java.util.Random;

public class MainMenuGUI implements Listener {
    private final BuildWorld plugin;

    public MainMenuGUI(BuildWorld plugin) {
        this.plugin = plugin;
    }

    public void openMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, MessageUtils.colorize("&l%primary%BuildWorld Main Menu"));

        // Fill background with black glass panes
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, createGlassPane(Material.BLACK_STAINED_GLASS_PANE, " "));
        }

        // Slot 11: Create World (Nether Star)
        ItemStack createWorld = new ItemStack(Material.NETHER_STAR);
        ItemMeta createMeta = createWorld.getItemMeta();
        createMeta.setDisplayName(MessageUtils.colorize("%primary%&lCreate a World"));
        createMeta.setLore(Collections.singletonList(
                MessageUtils.colorize("%secondary%Click to create a new world!")
        ));
        createWorld.setItemMeta(createMeta);
        inv.setItem(11, createWorld);

        // Slot 13: World Menu (Chest)
        ItemStack worldMenu = new ItemStack(Material.CHEST);
        ItemMeta menuMeta = worldMenu.getItemMeta();
        menuMeta.setDisplayName(MessageUtils.colorize("%primary%&lWorld Menu"));
        menuMeta.setLore(Collections.singletonList(
                MessageUtils.colorize("%secondary%Browse all personal & active public worlds.")
        ));
        worldMenu.setItemMeta(menuMeta);
        inv.setItem(13, worldMenu);

        // Slot 15: Teleport to Spawn (Compass)
        ItemStack spawn = new ItemStack(Material.COMPASS);
        ItemMeta spawnMeta = spawn.getItemMeta();
        spawnMeta.setDisplayName(MessageUtils.colorize("%primary%&lTeleport to Spawn"));
        spawnMeta.setLore(Collections.singletonList(
                MessageUtils.colorize("%secondary%Teleport to the BuildWorld Spawn")
        ));
        spawn.setItemMeta(spawnMeta);
        inv.setItem(15, spawn);

        // Slot 26: Return (Spruce Door)
        ItemStack returnDoor = new ItemStack(Material.SPRUCE_DOOR);
        ItemMeta doorMeta = returnDoor.getItemMeta();
        doorMeta.setDisplayName(MessageUtils.colorize("&cReturn"));
        doorMeta.setLore(Collections.singletonList(MessageUtils.colorize("&7Return to the previous page.")));
        returnDoor.setItemMeta(doorMeta);
        inv.setItem(26, returnDoor);

        player.openInventory(inv);
    }

    private ItemStack createGlassPane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().contains("BuildWorld Main Menu")) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        Player player = (Player) event.getWhoClicked();
        Material type = clicked.getType();

        if (type == Material.SPRUCE_DOOR) {
            player.closeInventory();
            return;
        }

        if (type == Material.NETHER_STAR) {
            player.closeInventory();
            handleCreateWorld(player);
            return;
        }

        if (type == Material.CHEST) {
            plugin.getWorldMenuGUI().openMenu(player, 1);
            return;
        }

        if (type == Material.COMPASS) {
            String spawnWorldName = plugin.getDatabaseManager().getSpawnWorld();
            if (spawnWorldName == null) {
                player.sendMessage(MessageUtils.colorize("&cSpawn world not set!"));
                player.closeInventory();
                return;
            }
            World spawnWorld = Bukkit.getWorld(spawnWorldName);
            if (spawnWorld == null) {
                spawnWorld = Bukkit.createWorld(new org.bukkit.WorldCreator(spawnWorldName));
            }
            Location spawnLoc = plugin.getDatabaseManager().getSpawnLocation();
            if (spawnLoc != null) {
                player.teleport(spawnLoc);
                player.sendMessage(MessageUtils.colorize("%primary%Teleported to spawn!"));
            } else {
                player.sendMessage(MessageUtils.colorize("&cSpawn location not set!"));
            }
            player.closeInventory();
        }
    }

    private void handleCreateWorld(Player player) {
        if (!player.hasPermission("buildworld.create")) {
            player.sendMessage(MessageUtils.colorize("&cNo permission!"));
            return;
        }

        int maxWorlds = plugin.getConfig().getInt("World.MaxWorldsPerPlayer", 5);
        int extraCredits = plugin.getDatabaseManager().getExtraWorldCredits(player.getUniqueId());
        int currentWorlds = plugin.getDatabaseManager().getOwnedWorlds(player.getUniqueId()).size();

        if (currentWorlds >= (maxWorlds + extraCredits) && !player.hasPermission("buildworld.admin")) {
            player.sendMessage(MessageUtils.colorize("&cYou have reached the maximum amount of worlds!"));
            return;
        }

        player.sendMessage(MessageUtils.colorize("%primary%Creating world... Please wait."));
        String worldName = player.getName() + "-" + generateRandomSuffix(6);

        plugin.getWorldManager().createVoidWorld(worldName, player);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.getWorldControlGUI().openMenu(player, worldName);
            }
        }, 20L);
    }

    private String generateRandomSuffix(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++) sb.append(chars.charAt(random.nextInt(chars.length())));
        return sb.toString();
    }
}