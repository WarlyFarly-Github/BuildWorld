package com.boshywashy.buildworld.gui;

import com.boshywashy.buildworld.BuildWorld;
import com.boshywashy.buildworld.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

public class WorldMenuGUI implements Listener {
    private final BuildWorld plugin;
    private final Map<UUID, MenuData> playerMenuData = new HashMap<>();

    private static class MenuData {
        int currentPage = 1;
        boolean showAllWorlds = false;
        List<String> personalWorlds = new ArrayList<>();
        List<String> onlineWorlds = new ArrayList<>();
    }

    public WorldMenuGUI(BuildWorld plugin) {
        this.plugin = plugin;
    }

    public void openMenu(Player player) {
        openMenu(player, 1);
    }

    public void openMenu(Player player, int page) {
        UUID playerUUID = player.getUniqueId();
        MenuData data = playerMenuData.computeIfAbsent(playerUUID, k -> new MenuData());
        data.currentPage = page;

        calculateWorldLists(player, data);

        Inventory inv = Bukkit.createInventory(null, 54, MessageUtils.colorize("&l%primary%BuildWorld Menu"));

        int slot = 0;
        for (int i = 0; i < 18 && i < data.personalWorlds.size(); i++) {
            addWorldItem(inv, slot++, data.personalWorlds.get(i), player, true);
        }

        for (int i = 18; i < 27; i++) {
            inv.setItem(i, createGlassPane(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        int onlineWorldsPerPage = 18;
        int startIdx = (page - 1) * onlineWorldsPerPage;
        int endIdx = Math.min(startIdx + onlineWorldsPerPage, data.onlineWorlds.size());

        slot = 27;
        for (int i = startIdx; i < endIdx; i++) {
            addWorldItem(inv, slot++, data.onlineWorlds.get(i), player, false);
        }

        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta compassMeta = compass.getItemMeta();
        compassMeta.setDisplayName(MessageUtils.colorize("%primary%Teleport to Spawn"));
        compass.setItemMeta(compassMeta);
        inv.setItem(45, compass);

        inv.setItem(46, createGlassPane(Material.GRAY_STAINED_GLASS_PANE, " "));
        inv.setItem(47, createGlassPane(Material.GRAY_STAINED_GLASS_PANE, " "));

        boolean hasPrevPage = page > 1;
        Material prevPageMat = hasPrevPage ? Material.RED_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
        ItemStack prevPage = createGlassPane(prevPageMat, hasPrevPage ?
                MessageUtils.colorize("%primary%Previous Page") : MessageUtils.colorize("%secondary%No Previous Page"));
        inv.setItem(48, prevPage);

        ItemStack hopper = new ItemStack(Material.HOPPER);
        ItemMeta hopperMeta = hopper.getItemMeta();
        hopperMeta.setDisplayName(MessageUtils.colorize("%primary%Filter: " +
                (data.showAllWorlds ? "%secondary%All Worlds" : "%secondary%Online Only")));
        List<String> hopperLore = new ArrayList<>();
        hopperLore.add(MessageUtils.colorize("%secondary%Click to toggle"));
        hopperLore.add(MessageUtils.colorize("%secondary%filter mode"));
        hopperMeta.setLore(hopperLore);
        hopper.setItemMeta(hopperMeta);
        inv.setItem(49, hopper);

        boolean hasNextPage = endIdx < data.onlineWorlds.size();
        Material nextPageMat = hasNextPage ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
        ItemStack nextPage = createGlassPane(nextPageMat, hasNextPage ?
                MessageUtils.colorize("%primary%Next Page") : MessageUtils.colorize("%secondary%No Next Page"));
        inv.setItem(50, nextPage);

        inv.setItem(51, createGlassPane(Material.GRAY_STAINED_GLASS_PANE, " "));
        inv.setItem(52, createGlassPane(Material.GRAY_STAINED_GLASS_PANE, " "));

        // Slot 53: Return Door
        ItemStack returnDoor = new ItemStack(Material.SPRUCE_DOOR);
        ItemMeta doorMeta = returnDoor.getItemMeta();
        doorMeta.setDisplayName(MessageUtils.colorize("&cReturn"));
        doorMeta.setLore(Collections.singletonList(MessageUtils.colorize("&7Return to the previous page.")));
        returnDoor.setItemMeta(doorMeta);
        inv.setItem(53, returnDoor);

        player.openInventory(inv);
    }

    private boolean canViewHiddenWorld(String worldName, UUID viewerUUID, boolean isOp) {
        if (isOp) return true;
        if (plugin.getDatabaseManager().isOwner(worldName, viewerUUID)) return true;
        if (plugin.getDatabaseManager().isMember(worldName, viewerUUID)) return true;
        return false;
    }

    private void calculateWorldLists(Player player, MenuData data) {
        UUID playerUUID = player.getUniqueId();
        boolean isOp = player.isOp();

        data.personalWorlds = new ArrayList<>();
        Set<String> personalSet = new LinkedHashSet<>();
        personalSet.addAll(plugin.getDatabaseManager().getOwnedWorlds(playerUUID));
        personalSet.addAll(plugin.getDatabaseManager().getMemberWorlds(playerUUID));
        data.personalWorlds.addAll(personalSet);

        data.onlineWorlds = new ArrayList<>();

        if (data.showAllWorlds) {
            List<WorldSortEntry> entries = new ArrayList<>();
            for (String worldName : plugin.getDatabaseManager().getAllWorlds()) {
                boolean hidden = plugin.getDatabaseManager().isHidden(worldName);
                if (hidden && !canViewHiddenWorld(worldName, playerUUID, isOp)) continue;

                String nick = plugin.getDatabaseManager().getNickname(worldName);
                entries.add(new WorldSortEntry(worldName, nick));
            }
            entries.sort(Comparator.comparing(e -> e.sortKey.toLowerCase()));
            for (WorldSortEntry entry : entries) {
                data.onlineWorlds.add(entry.worldName);
            }
        } else {
            Set<UUID> onlineOwners = new HashSet<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!plugin.getDatabaseManager().getOwnedWorlds(p.getUniqueId()).isEmpty()) {
                    onlineOwners.add(p.getUniqueId());
                }
            }

            List<WorldSortEntry> entries = new ArrayList<>();
            for (UUID ownerUUID : onlineOwners) {
                for (String worldName : plugin.getDatabaseManager().getOwnedWorlds(ownerUUID)) {
                    boolean hidden = plugin.getDatabaseManager().isHidden(worldName);
                    boolean open = plugin.getDatabaseManager().isOpen(worldName);
                    boolean inactive = plugin.getDatabaseManager().isInactive(worldName);

                    if (hidden) {
                        if (!canViewHiddenWorld(worldName, playerUUID, isOp)) continue;
                    } else {
                        if (!open && !inactive) continue;
                    }

                    String nick = plugin.getDatabaseManager().getNickname(worldName);
                    entries.add(new WorldSortEntry(worldName, nick));
                }
            }
            entries.sort(Comparator.comparing(e -> e.sortKey.toLowerCase()));
            for (WorldSortEntry entry : entries) {
                data.onlineWorlds.add(entry.worldName);
            }
        }
    }

    private static class WorldSortEntry {
        final String worldName;
        final String sortKey;

        WorldSortEntry(String worldName, String nickname) {
            this.worldName = worldName;
            this.sortKey = nickname != null ? nickname : worldName;
        }
    }

    private ItemStack createGlassPane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private void addWorldItem(Inventory inv, int slot, String worldName, Player viewer, boolean isTopPersonalSection) {
        String ownerUUID = plugin.getDatabaseManager().getWorldOwner(worldName);
        if (ownerUUID == null) return;

        UUID viewerUUID = viewer.getUniqueId();
        boolean isOp = viewer.isOp();
        boolean isHidden = plugin.getDatabaseManager().isHidden(worldName);

        if (isHidden && !canViewHiddenWorld(worldName, viewerUUID, isOp)) return;

        String ownerName = Bukkit.getOfflinePlayer(UUID.fromString(ownerUUID)).getName();
        if (ownerName == null) ownerName = "Unknown";

        String itemName = plugin.getDatabaseManager().getItem(worldName);
        Material mat;
        try {
            mat = Material.valueOf(itemName);
        } catch (IllegalArgumentException e) {
            mat = Material.GRASS_BLOCK;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        String nickname = plugin.getDatabaseManager().getNickname(worldName);
        if (nickname == null) nickname = worldName;

        boolean isOpen = plugin.getDatabaseManager().isOpen(worldName);

        String displayName;
        if (isHidden) {
            displayName = MessageUtils.colorize("%primary%" + nickname + " &8(Hidden)");
        } else if (isOpen) {
            displayName = MessageUtils.colorize("%primary%" + nickname);
        } else {
            displayName = MessageUtils.colorize("%secondary%" + nickname + " &7(Closed)");
        }
        meta.setDisplayName(displayName);

        List<String> lore = new ArrayList<>();
        lore.add(MessageUtils.colorize("%secondary%Owner: " + ownerName));

        List<UUID> members = plugin.getDatabaseManager().getMembers(worldName);
        if (!members.isEmpty()) {
            StringBuilder memStr = new StringBuilder("%secondary%Members: ");
            int count = 0;
            for (UUID mem : members) {
                if (count >= 5) { memStr.append("... "); break; }
                String memName = Bukkit.getOfflinePlayer(mem).getName();
                if (memName != null) { memStr.append(memName).append(", "); count++; }
            }
            String memString = memStr.toString();
            if (memString.endsWith(", ")) memString = memString.substring(0, memString.length() - 2);
            lore.add(MessageUtils.colorize(memString));
        }

        int diameter = plugin.getDatabaseManager().getDiameter(worldName);
        lore.add(MessageUtils.colorize("%secondary%Diameter: " + diameter + " blocks"));

        if (plugin.getDatabaseManager().isOwner(worldName, viewerUUID)) {
            lore.add(MessageUtils.colorize("%primary%&lYou are the Owner"));
        } else if (plugin.getDatabaseManager().isMember(worldName, viewerUUID)) {
            lore.add(MessageUtils.colorize("%primary%&lYou are a Member"));
        }

        if (!isOpen && !isHidden) {
            lore.add(MessageUtils.colorize("&c&lWorld is Closed"));
        }
        if (isHidden) {
            lore.add(MessageUtils.colorize("&8&lWorld is Hidden"));
        }

        lore.add("");
        if (isTopPersonalSection) {
            lore.add(MessageUtils.colorize("%primary%Click to open control menu!"));
        } else {
            lore.add(MessageUtils.colorize("%primary%Click to visit!"));
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().contains("BuildWorld Menu")) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        String displayName = clicked.getItemMeta().getDisplayName();
        Player player = (Player) event.getWhoClicked();
        UUID playerUUID = player.getUniqueId();
        MenuData data = playerMenuData.get(playerUUID);

        if (data == null) return;

        Material type = clicked.getType();
        int slot = event.getRawSlot();

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
            return;
        }

        if (type == Material.SPRUCE_DOOR) {
            plugin.getMainMenuGUI().openMenu(player);
            return;
        }

        if (type == Material.LIME_STAINED_GLASS_PANE && displayName.contains("Next")) {
            openMenu(player, data.currentPage + 1);
            return;
        }

        if (type == Material.RED_STAINED_GLASS_PANE && displayName.contains("Previous")) {
            openMenu(player, data.currentPage - 1);
            return;
        }

        if (type == Material.HOPPER) {
            data.showAllWorlds = !data.showAllWorlds;
            data.currentPage = 1;
            openMenu(player, 1);
            player.sendMessage(MessageUtils.colorize("%primary%Filter set to: %secondary%" +
                    (data.showAllWorlds ? "All Worlds" : "Online Only")));
            return;
        }

        String cleanName = ChatColor.stripColor(displayName);
        cleanName = cleanName.replaceAll("§[0-9a-fk-or]", "").trim();
        cleanName = cleanName.replace(" (Closed)", "").replace(" (Hidden)", "").trim();

        String worldName = plugin.getDatabaseManager().getWorldByNickname(cleanName);
        if (worldName == null) {
            for (String w : plugin.getDatabaseManager().getAllWorlds()) {
                if (plugin.getDatabaseManager().getNickname(w).equalsIgnoreCase(cleanName)) {
                    worldName = w;
                    break;
                }
            }
        }

        if (worldName == null) {
            player.sendMessage(MessageUtils.colorize("&cWorld not found!"));
            return;
        }

        // Top section: slots 0-17 -> Open Control Menu
        if (slot < 18) {
            plugin.getWorldControlGUI().openMenu(player, worldName);
            return;
        }

        // Bottom section (slots 27-44): Always visit directly, even if player is owner/member
        boolean isHidden = plugin.getDatabaseManager().isHidden(worldName);
        boolean isOp = player.isOp();

        if (isHidden && !canViewHiddenWorld(worldName, playerUUID, isOp)) {
            player.sendMessage(MessageUtils.colorize("&cThis world is hidden!"));
            player.closeInventory();
            return;
        }

        if (!isHidden && !plugin.getDatabaseManager().isOpen(worldName) &&
                !plugin.getDatabaseManager().isOwner(worldName, playerUUID) &&
                !plugin.getDatabaseManager().isMember(worldName, playerUUID)) {
            player.sendMessage(MessageUtils.colorize("&cThis world is closed!"));
            player.closeInventory();
            return;
        }

        if (plugin.getConfig().getBoolean("Maintenance.Enabled", false) && !player.hasPermission("buildworld.admin")) {
            player.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("Maintenance.KickMessage",
                    "&cThe BuildWorld system is currently under maintenance.")));
            player.closeInventory();
            return;
        }

        if (plugin.getDatabaseManager().isInactive(worldName)) {
            player.sendMessage(MessageUtils.colorize("&eWorld is inactive and loading up... Please wait."));
            player.closeInventory();
            String finalWorldName = worldName;
            plugin.getWorldManager().activateWorld(worldName, () -> {
                if (!player.isOnline()) return;
                World world = plugin.getWorldManager().ensureWorldLoaded(finalWorldName);
                if (world != null) {
                    Location spawnLoc = plugin.getDatabaseManager().getWorldSpawn(finalWorldName);
                    player.teleport(spawnLoc);
                    String nickname = plugin.getDatabaseManager().getNickname(finalWorldName);
                    player.sendMessage(MessageUtils.colorize("%primary%Visiting %secondary%" + nickname));
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
            String nickname = plugin.getDatabaseManager().getNickname(worldName);
            player.sendMessage(MessageUtils.colorize("%primary%Visiting %secondary%" + nickname));
        } else {
            player.sendMessage(MessageUtils.colorize("&cWorld could not be loaded!"));
        }
        player.closeInventory();
    }
}