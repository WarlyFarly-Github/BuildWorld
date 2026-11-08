package com.boshywashy.buildworld.gui;

import com.boshywashy.buildworld.BuildWorld;
import com.boshywashy.buildworld.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class ManagePlayersGUI implements Listener {
    private final BuildWorld plugin;
    private final Map<UUID, Session> activeSessions = new HashMap<>();

    private static class Session {
        String worldName;
        boolean isPromoteMode = false; // false = Kick Mode, true = Make Co-Owner Mode
    }

    public ManagePlayersGUI(BuildWorld plugin) {
        this.plugin = plugin;
    }

    public void openMenu(Player player, String worldName) {
        Session session = activeSessions.computeIfAbsent(player.getUniqueId(), k -> new Session());
        session.worldName = worldName;

        Inventory inv = Bukkit.createInventory(null, 54, MessageUtils.colorize("&l%primary%Manage World Players"));

        for (int i = 0; i < 54; i++) {
            inv.setItem(i, createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null));
        }

        List<UUID> members = plugin.getDatabaseManager().getMembers(worldName);
        List<UUID> owners = plugin.getDatabaseManager().getOwners(worldName);

        int slot = 10;
        for (UUID memberUUID : members) {
            if (owners.contains(memberUUID)) continue; // skip owners in list

            OfflinePlayer op = Bukkit.getOfflinePlayer(memberUUID);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(op);
            meta.setDisplayName(MessageUtils.colorize("%primary%" + (op.getName() != null ? op.getName() : "Member")));

            List<String> lore = new ArrayList<>();
            if (session.isPromoteMode) {
                lore.add(MessageUtils.colorize("%secondary%Click to make CO-OWNER"));
            } else {
                lore.add(MessageUtils.colorize("&cClick to KICK from world"));
            }
            meta.setLore(lore);
            head.setItemMeta(meta);

            inv.setItem(slot++, head);
            if ((slot % 9) == 8) slot += 2;
            if (slot >= 44) break;
        }

        // Toggle button at slot 49
        ItemStack toggle = new ItemStack(session.isPromoteMode ? Material.GOLDEN_HELMET : Material.BARRIER);
        ItemMeta tMeta = toggle.getItemMeta();
        tMeta.setDisplayName(MessageUtils.colorize("%primary%Current Mode: " +
                (session.isPromoteMode ? "%secondary%Make Co-Owner" : "&cKick Member")));
        tMeta.setLore(Collections.singletonList(MessageUtils.colorize("%secondary%Click to switch click behavior")));
        toggle.setItemMeta(tMeta);
        inv.setItem(49, toggle);

        // Return door at slot 53
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
        if (!event.getView().getTitle().contains("Manage World Players")) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        Player player = (Player) event.getWhoClicked();
        Session session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        Material type = clicked.getType();

        if (type == Material.SPRUCE_DOOR) {
            plugin.getWorldControlGUI().openMenu(player, session.worldName);
            return;
        }

        if (type == Material.GOLDEN_HELMET || type == Material.BARRIER) {
            session.isPromoteMode = !session.isPromoteMode;
            openMenu(player, session.worldName);
            return;
        }

        if (type == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) clicked.getItemMeta();
            if (meta != null && meta.getOwningPlayer() != null) {
                String targetName = meta.getOwningPlayer().getName();
                if (targetName != null) {
                    if (session.isPromoteMode) {
                        plugin.getDatabaseManager().addMember(session.worldName, meta.getOwningPlayer().getUniqueId(), "OWNER");
                        player.sendMessage(MessageUtils.colorize("%primary%Promoted %secondary%" + targetName + " %primary%to Co-Owner!"));
                    } else {
                        player.performCommand("bw kick " + session.worldName + " " + targetName);
                    }
                    openMenu(player, session.worldName);
                }
            }
        }
    }
}