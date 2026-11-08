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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DeleteConfirmGUI implements Listener {
    private final BuildWorld plugin;
    private final Map<UUID, String> targetWorld = new HashMap<>();

    public DeleteConfirmGUI(BuildWorld plugin) {
        this.plugin = plugin;
    }

    public void openMenu(Player player, String worldName) {
        targetWorld.put(player.getUniqueId(), worldName);

        Inventory inv = Bukkit.createInventory(null, 27, MessageUtils.colorize("&c&lConfirm Deletion"));

        for (int i = 0; i < 27; i++) {
            inv.setItem(i, createPane(Material.RED_STAINED_GLASS_PANE, " "));
        }

        ItemStack tnt = new ItemStack(Material.TNT);
        ItemMeta tMeta = tnt.getItemMeta();
        tMeta.setDisplayName(MessageUtils.colorize("&c&lCONFIRM DELETE WORLD"));
        tMeta.setLore(Arrays.asList(
                MessageUtils.colorize("%secondary%Clicking this will PERMANENTLY"),
                MessageUtils.colorize("%secondary%delete this world forever!")
        ));
        tnt.setItemMeta(tMeta);
        inv.setItem(13, tnt);

        ItemStack returnDoor = new ItemStack(Material.SPRUCE_DOOR);
        ItemMeta doorMeta = returnDoor.getItemMeta();
        doorMeta.setDisplayName(MessageUtils.colorize("&cReturn"));
        doorMeta.setLore(Collections.singletonList(MessageUtils.colorize("%secondary%Cancel and return to previous menu.")));
        returnDoor.setItemMeta(doorMeta);
        inv.setItem(26, returnDoor);

        player.openInventory(inv);
    }

    private ItemStack createPane(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().contains("Confirm Deletion")) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        Player player = (Player) event.getWhoClicked();
        String worldName = targetWorld.get(player.getUniqueId());
        if (worldName == null) return;

        Material type = clicked.getType();

        if (type == Material.SPRUCE_DOOR) {
            plugin.getWorldControlGUI().openMenu(player, worldName);
            return;
        }

        if (type == Material.TNT) {
            player.closeInventory();
            player.performCommand("bw delete " + worldName + " confirm confirm");
        }
    }
}