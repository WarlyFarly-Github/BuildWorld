package com.boshywashy.buildworld;

import com.boshywashy.buildworld.commands.AdminCommand;
import com.boshywashy.buildworld.commands.BuildWorldCommand;
import com.boshywashy.buildworld.commands.TabCompleter;
import com.boshywashy.buildworld.database.DatabaseManager;
import com.boshywashy.buildworld.gui.DeleteConfirmGUI;
import com.boshywashy.buildworld.gui.InvitePlayerGUI;
import com.boshywashy.buildworld.gui.MainMenuGUI;
import com.boshywashy.buildworld.gui.ManagePlayersGUI;
import com.boshywashy.buildworld.gui.WorldControlGUI;
import com.boshywashy.buildworld.gui.WorldMenuGUI;
import com.boshywashy.buildworld.listeners.PlayerListener;
import com.boshywashy.buildworld.listeners.WorldListener;
import com.boshywashy.buildworld.managers.*;
import com.boshywashy.buildworld.world.VoidWorldGenerator;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class BuildWorld extends JavaPlugin {
    private static BuildWorld instance;
    private DatabaseManager databaseManager;
    private ConfigManager configManager;
    private WorldManager worldManager;
    private PermissionManager permissionManager;
    private ExpansionManager expansionManager;
    private DiscordManager discordManager;
    private BorderParticleManager borderParticleManager;
    private WorldMenuGUI worldMenuGUI;
    private MainMenuGUI mainMenuGUI;
    private WorldControlGUI worldControlGUI;
    private InvitePlayerGUI invitePlayerGUI;
    private ManagePlayersGUI managePlayersGUI;
    private DeleteConfirmGUI deleteConfirmGUI;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.configManager = new ConfigManager(this);
        this.databaseManager = new DatabaseManager(this);
        this.worldManager = new WorldManager(this);
        this.permissionManager = new PermissionManager(this);
        this.expansionManager = new ExpansionManager(this);
        this.discordManager = new DiscordManager(this);
        this.borderParticleManager = new BorderParticleManager(this);
        this.worldMenuGUI = new WorldMenuGUI(this);
        this.mainMenuGUI = new MainMenuGUI(this);
        this.worldControlGUI = new WorldControlGUI(this);
        this.invitePlayerGUI = new InvitePlayerGUI(this);
        this.managePlayersGUI = new ManagePlayersGUI(this);
        this.deleteConfirmGUI = new DeleteConfirmGUI(this);

        loadActiveWorlds();

        getCommand("buildworld").setExecutor(new BuildWorldCommand(this));
        getCommand("buildworld").setTabCompleter(new TabCompleter(this));
        getCommand("buildworldadmin").setExecutor(new AdminCommand(this));
        getCommand("buildworldadmin").setTabCompleter(new TabCompleter(this));

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new WorldListener(this), this);
        getServer().getPluginManager().registerEvents(discordManager, this);
        getServer().getPluginManager().registerEvents(worldMenuGUI, this);
        getServer().getPluginManager().registerEvents(mainMenuGUI, this);
        getServer().getPluginManager().registerEvents(worldControlGUI, this);
        getServer().getPluginManager().registerEvents(invitePlayerGUI, this);
        getServer().getPluginManager().registerEvents(managePlayersGUI, this);
        getServer().getPluginManager().registerEvents(deleteConfirmGUI, this);

        if (configManager.isOnlineExpansionEnabled()) {
            expansionManager.startExpansionTask();
        }

        discordManager.start();
        borderParticleManager.start();

        getLogger().info("BuildWorld has been enabled!");
    }

    private void loadActiveWorlds() {
        List<String> worlds = databaseManager.getActiveWorlds();
        for (String worldName : worlds) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                WorldCreator wc = new WorldCreator(worldName);
                wc.generator(new VoidWorldGenerator());
                wc.generateStructures(false);
                world = Bukkit.createWorld(wc);

                if (world != null) {
                    int diameter = databaseManager.getDiameter(worldName);
                    world.getWorldBorder().setCenter(0, 0);
                    world.getWorldBorder().setSize(diameter);
                    world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
                    world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
                    world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
                    world.setTime(6000);
                    getLogger().info("Loaded active world: " + worldName);
                }
            }
        }
    }

    @Override
    public void onDisable() {
        if (worldManager != null) {
            worldManager.stopAutoUnloadTask();
            worldManager.stopInactiveCheckTask();
        }
        if (expansionManager != null) {
            expansionManager.stopExpansionTask();
        }
        if (discordManager != null) {
            discordManager.shutdown();
        }
        if (borderParticleManager != null) {
            borderParticleManager.stop();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("BuildWorld has been disabled!");
    }

    public static BuildWorld getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public ConfigManager getConfigManager() { return configManager; }
    public WorldManager getWorldManager() { return worldManager; }
    public PermissionManager getPermissionManager() { return permissionManager; }
    public ExpansionManager getExpansionManager() { return expansionManager; }
    public DiscordManager getDiscordManager() { return discordManager; }
    public BorderParticleManager getBorderParticleManager() { return borderParticleManager; }
    public WorldMenuGUI getWorldMenuGUI() { return worldMenuGUI; }
    public MainMenuGUI getMainMenuGUI() { return mainMenuGUI; }
    public WorldControlGUI getWorldControlGUI() { return worldControlGUI; }
    public InvitePlayerGUI getInvitePlayerGUI() { return invitePlayerGUI; }
    public ManagePlayersGUI getManagePlayersGUI() { return managePlayersGUI; }
    public DeleteConfirmGUI getDeleteConfirmGUI() { return deleteConfirmGUI; }
}