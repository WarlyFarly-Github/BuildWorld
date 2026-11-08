package com.boshywashy.buildworld.gui;

import com.boshywashy.buildworld.BuildWorld;
import com.boshywashy.buildworld.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class InvitePlayerGUI implements Listener {
    private final BuildWorld plugin;
    private final Map<UUID, InviteSession> sessions = new HashMap<>();

    private static class InviteSession {
        String worldName;
        int page;
        List<Player> onlineList;
    }

    public InvitePlayerGUI(BuildWorld plugin) {
        this.plugin = plugin;
    }

    public void openMenu(Player inviter, String worldName, int page) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.remove(inviter); // Don't invite self

        InviteSession session = sessions.computeIfAbsent(inviter.getUniqueId(), k -> new InviteSession());
        session.worldName = worldName;
        session.page = page;
        session.onlineList = players;

        Inventory inv = Bukkit.createInventory(null, 54, MessageUtils.colorize("&l%primary%Invite Players to World"));

        for (int i = 0; i < 54; i++) {
            inv.setItem(i, createGlassPane(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        int itemsPerPage = 36;
        int start = (page - 1) * itemsPerPage;
        int end = Math.min(start + itemsPerPage, players.size());

        int slot = 9;
        for (int i = start; i < end; i++) {
            Player target = players.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(target);
            meta.setDisplayName(MessageUtils.colorize("%primary%" + target.getName()));
            meta.setLore(Collections.singletonList(MessageUtils.colorize("%secondary%Click to invite as Member")));
            head.setItemMeta(meta);
            inv.setItem(slot++, head);
        }

        boolean hasPrev = page > 1;
        ItemStack prev = createGlassPane(hasPrev ? Material.RED_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                hasPrev ? MessageUtils.colorize("%primary%Previous Page") : MessageUtils.colorize("%secondary%No Previous Page"));
        inv.setItem(48, prev);

        boolean hasNext = end < players.size();
        ItemStack next = createGlassPane(hasNext ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                hasNext ? MessageUtils.colorize("%primary%Next Page") : MessageUtils.colorize("%secondary%No Previous Page"));
        inv.setItem(50, next);

        ItemStack returnDoor = new ItemStack(Material.SPRUCE_DOOR);
        ItemMeta doorMeta = returnDoor.getItemMeta();
        doorMeta.setDisplayName(MessageUtils.colorize("&cReturn"));
        doorMeta.setLore(Collections.singletonList(MessageUtils.colorize("%secondary%Return to the previous page.")));
        returnDoor.setItemMeta(doorMeta);
        inv.setItem(53, returnDoor);

        inviter.openInventory(inv);
    }

    private ItemStack createGlassPane(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().contains("Invite Players to World")) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        Player player = (Player) event.getWhoClicked();
        InviteSession session = sessions.get(player.getUniqueId());
        if (session == null) return;

        Material type = clicked.getType();
        String display = clicked.getItemMeta().getDisplayName();

        if (type == Material.SPRUCE_DOOR) {
            plugin.getWorldControlGUI().openMenu(player, session.worldName);
            return;
        }

        if (type == Material.RED_STAINED_GLASS_PANE && display.contains("Previous")) {
            openMenu(player, session.worldName, session.page - 1);
            return;
        }

        if (type == Material.LIME_STAINED_GLASS_PANE && display.contains("Next")) {
            openMenu(player, session.worldName, session.page + 1);
            return;
        }

        if (type == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) clicked.getItemMeta();
            if (meta != null && meta.getOwningPlayer() != null) {
                String targetName = meta.getOwningPlayer().getName();
                if (targetName != null) {
                    player.performCommand("bw invite member " + session.worldName + " " + targetName);
                    openMenu(player, session.worldName, session.page);
                }
            }
        }
    }
}