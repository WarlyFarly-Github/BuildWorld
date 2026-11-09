package com.boshywashy.buildworld.managers;

import com.boshywashy.buildworld.BuildWorld;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    private final BuildWorld plugin;
    private String primaryColor;
    private String secondaryColor;

    public ConfigManager(BuildWorld plugin) {
        this.plugin = plugin;
        loadColors();
    }

    public void reload() {
        plugin.reloadConfig();
        loadColors();
        if (plugin.getDiscordManager() != null) {
            plugin.getDiscordManager().start(); // pick up a changed bot token / channel
        }
        if (plugin.getBorderParticleManager() != null) {
            plugin.getBorderParticleManager().start(); // pick up changed particle settings
        }
    }

    private void loadColors() {
        primaryColor = plugin.getConfig().getString("Colors.Primary", "b");
        secondaryColor = plugin.getConfig().getString("Colors.Secondary", "f");
    }

    public String getPrimaryColor() {
        return primaryColor;
    }

    public String getSecondaryColor() {
        return secondaryColor;
    }

    public boolean isOnlineExpansionEnabled() {
        return plugin.getConfig().getBoolean("OnlineWorldExpansion.Enabled", false);
    }

    public int getBlockGainPerMin() {
        return plugin.getConfig().getInt("OnlineWorldExpansion.BlockGainPerMin", 20);
    }

    public int getCheckInterval() {
        return plugin.getConfig().getInt("OnlineWorldExpansion.CheckInterval", 60);
    }

    public int getInactiveMinutes() {
        return plugin.getConfig().getInt("World.InactiveMinutes", 10);
    }

    public Map<String, String> getTutorialMessages() {
        Map<String, String> tutorials = new HashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("Tutorials");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                tutorials.put(key, section.getString(key));
            }
        }
        return tutorials;
    }

    public java.util.List<String> getWelcomeMessage() {
        return plugin.getConfig().getStringList("Messages.Welcome");
    }

    public int getCreateCooldown() {
        return plugin.getConfig().getInt("World.CreateCooldown", 60);
    }


}