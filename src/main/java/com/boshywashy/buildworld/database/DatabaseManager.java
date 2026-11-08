package com.boshywashy.buildworld.database;

import com.boshywashy.buildworld.BuildWorld;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.*;

public class DatabaseManager {
    private final BuildWorld plugin;
    private Connection connection;
    private final String type;

    public DatabaseManager(BuildWorld plugin) {
        this.plugin = plugin;
        this.type = plugin.getConfig().getString("Database.Type", "SQLITE");
        connect();
        createTables();
        migrateSchema();
    }

    private void connect() {
        try {
            if (type.equalsIgnoreCase("MYSQL")) {
                String host = plugin.getConfig().getString("Database.Host");
                int port = plugin.getConfig().getInt("Database.Port");
                String database = plugin.getConfig().getString("Database.Database");
                String username = plugin.getConfig().getString("Database.Username");
                String password = plugin.getConfig().getString("Database.Password");

                connection = DriverManager.getConnection(
                        "jdbc:mysql://" + host + ":" + port + "/" + database, username, password);
            } else {
                connection = DriverManager.getConnection(
                        "jdbc:sqlite:" + plugin.getDataFolder() + "/buildworld.db");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void createTables() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS worlds (" +
                    "world_name VARCHAR(64) PRIMARY KEY," +
                    "owner_uuid VARCHAR(36) NOT NULL," +
                    "nickname VARCHAR(64)," +
                    "item VARCHAR(64) DEFAULT 'GRASS_BLOCK'," +
                    "diameter INT DEFAULT 100," +
                    "is_open BOOLEAN DEFAULT FALSE," +
                    "is_hidden BOOLEAN DEFAULT FALSE," +
                    "is_inactive BOOLEAN DEFAULT FALSE," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            stmt.execute("CREATE TABLE IF NOT EXISTS members (" +
                    "world_name VARCHAR(64)," +
                    "player_uuid VARCHAR(36)," +
                    "role VARCHAR(10) DEFAULT 'MEMBER'," +
                    "PRIMARY KEY (world_name, player_uuid))");

            stmt.execute("CREATE TABLE IF NOT EXISTS expansion_blocks (" +
                    "player_uuid VARCHAR(36) PRIMARY KEY," +
                    "blocks INT DEFAULT 0)");

            stmt.execute("CREATE TABLE IF NOT EXISTS invites (" +
                    "world_name VARCHAR(64)," +
                    "player_uuid VARCHAR(36)," +
                    "role VARCHAR(10)," +
                    "PRIMARY KEY (world_name, player_uuid))");

            stmt.execute("CREATE TABLE IF NOT EXISTS spawn_world (" +
                    "id INT PRIMARY KEY DEFAULT 1," +
                    "world_name VARCHAR(64)," +
                    "x DOUBLE DEFAULT 0," +
                    "y DOUBLE DEFAULT 64," +
                    "z DOUBLE DEFAULT 0," +
                    "yaw FLOAT DEFAULT 0," +
                    "pitch FLOAT DEFAULT 0)");

            stmt.execute("CREATE TABLE IF NOT EXISTS world_spawns (" +
                    "world_name VARCHAR(64) PRIMARY KEY," +
                    "x DOUBLE DEFAULT 0," +
                    "y DOUBLE DEFAULT 64," +
                    "z DOUBLE DEFAULT 0," +
                    "yaw FLOAT DEFAULT 0," +
                    "pitch FLOAT DEFAULT 0)");

            stmt.execute("CREATE TABLE IF NOT EXISTS extra_world_credits (" +
                    "player_uuid VARCHAR(36) PRIMARY KEY," +
                    "extra_worlds INT DEFAULT 0)");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds any missing columns to existing tables (safe migration).
     */
    private void migrateSchema() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("ALTER TABLE worlds ADD COLUMN is_hidden BOOLEAN DEFAULT FALSE");
        } catch (SQLException ignored) {
        }
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("ALTER TABLE worlds ADD COLUMN is_inactive BOOLEAN DEFAULT FALSE");
        } catch (SQLException ignored) {
        }
    }

    public void createWorld(String worldName, UUID ownerUUID, String nickname) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO worlds (world_name, owner_uuid, nickname) VALUES (?, ?, ?)")) {
            ps.setString(1, worldName);
            ps.setString(2, ownerUUID.toString());
            ps.setString(3, nickname != null ? nickname : worldName);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteWorld(String worldName) {
        try {
            connection.prepareStatement("DELETE FROM worlds WHERE world_name = '" + worldName + "'").executeUpdate();
            connection.prepareStatement("DELETE FROM members WHERE world_name = '" + worldName + "'").executeUpdate();
            connection.prepareStatement("DELETE FROM invites WHERE world_name = '" + worldName + "'").executeUpdate();
            connection.prepareStatement("DELETE FROM world_spawns WHERE world_name = '" + worldName + "'").executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean isBuildWorld(String worldName) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM worlds WHERE world_name = ?")) {
            ps.setString(1, worldName);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public String getWorldOwner(String worldName) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT owner_uuid FROM worlds WHERE world_name = ?")) {
            ps.setString(1, worldName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("owner_uuid");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<String> getOwnedWorlds(UUID playerUUID) {
        List<String> worlds = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT world_name FROM worlds WHERE owner_uuid = ?")) {
            ps.setString(1, playerUUID.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                worlds.add(rs.getString("world_name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return worlds;
    }

    public List<String> getMemberWorlds(UUID playerUUID) {
        List<String> worlds = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT world_name FROM members WHERE player_uuid = ?")) {
            ps.setString(1, playerUUID.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                worlds.add(rs.getString("world_name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return worlds;
    }

    public List<String> getAllWorlds() {
        List<String> worlds = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT world_name FROM worlds")) {
            while (rs.next()) {
                worlds.add(rs.getString("world_name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return worlds;
    }

    public List<String> getActiveWorlds() {
        List<String> worlds = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT world_name FROM worlds WHERE is_inactive = FALSE")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                worlds.add(rs.getString("world_name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return worlds;
    }

    public List<String> getInactiveWorlds() {
        List<String> worlds = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT world_name FROM worlds WHERE is_inactive = TRUE")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                worlds.add(rs.getString("world_name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return worlds;
    }

    public void setInactive(String worldName, boolean inactive) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE worlds SET is_inactive = ? WHERE world_name = ?")) {
            ps.setBoolean(1, inactive);
            ps.setString(2, worldName);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean isInactive(String worldName) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT is_inactive FROM worlds WHERE world_name = ?")) {
            ps.setString(1, worldName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getBoolean("is_inactive");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void setNickname(String worldName, String nickname) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE worlds SET nickname = ? WHERE world_name = ?")) {
            ps.setString(1, nickname);
            ps.setString(2, worldName);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getNickname(String worldName) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT nickname FROM worlds WHERE world_name = ?")) {
            ps.setString(1, worldName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String nick = rs.getString("nickname");
                return nick != null ? nick : worldName;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return worldName;
    }

    public String getWorldByNickname(String nickname) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT world_name FROM worlds WHERE nickname = ?")) {
            ps.setString(1, nickname);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("world_name");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT world_name FROM worlds WHERE LOWER(nickname) = LOWER(?)")) {
            ps.setString(1, nickname);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("world_name");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void setItem(String worldName, String item) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE worlds SET item = ? WHERE world_name = ?")) {
            ps.setString(1, item);
            ps.setString(2, worldName);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getItem(String worldName) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT item FROM worlds WHERE world_name = ?")) {
            ps.setString(1, worldName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("item");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "GRASS_BLOCK";
    }

    public void setOpen(String worldName, boolean open) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE worlds SET is_open = ? WHERE world_name = ?")) {
            ps.setBoolean(1, open);
            ps.setString(2, worldName);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean isOpen(String worldName) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT is_open FROM worlds WHERE world_name = ?")) {
            ps.setString(1, worldName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getBoolean("is_open");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void setHidden(String worldName, boolean hidden) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE worlds SET is_hidden = ? WHERE world_name = ?")) {
            ps.setBoolean(1, hidden);
            ps.setString(2, worldName);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean isHidden(String worldName) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT is_hidden FROM worlds WHERE world_name = ?")) {
            ps.setString(1, worldName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getBoolean("is_hidden");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void setDiameter(String worldName, int diameter) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE worlds SET diameter = ? WHERE world_name = ?")) {
            ps.setInt(1, diameter);
            ps.setString(2, worldName);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public int getDiameter(String worldName) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT diameter FROM worlds WHERE world_name = ?")) {
            ps.setString(1, worldName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("diameter");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 100;
    }

    public void addMember(String worldName, UUID playerUUID, String role) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO members (world_name, player_uuid, role) VALUES (?, ?, ?)")) {
            ps.setString(1, worldName);
            ps.setString(2, playerUUID.toString());
            ps.setString(3, role);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void removeMember(String worldName, UUID playerUUID) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM members WHERE world_name = ? AND player_uuid = ?")) {
            ps.setString(1, worldName);
            ps.setString(2, playerUUID.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<UUID> getMembers(String worldName) {
        List<UUID> members = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT player_uuid FROM members WHERE world_name = ? AND role = 'MEMBER'")) {
            ps.setString(1, worldName);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                members.add(UUID.fromString(rs.getString("player_uuid")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return members;
    }

    public List<UUID> getOwners(String worldName) {
        List<UUID> owners = new ArrayList<>();
        String ownerUUID = getWorldOwner(worldName);
        if (ownerUUID != null) {
            owners.add(UUID.fromString(ownerUUID));
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT player_uuid FROM members WHERE world_name = ? AND role = 'OWNER'")) {
            ps.setString(1, worldName);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                owners.add(UUID.fromString(rs.getString("player_uuid")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return owners;
    }

    public boolean isMember(String worldName, UUID playerUUID) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM members WHERE world_name = ? AND player_uuid = ?")) {
            ps.setString(1, worldName);
            ps.setString(2, playerUUID.toString());
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean isOwner(String worldName, UUID playerUUID) {
        String owner = getWorldOwner(worldName);
        if (owner != null && owner.equals(playerUUID.toString())) return true;

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM members WHERE world_name = ? AND player_uuid = ? AND role = 'OWNER'")) {
            ps.setString(1, worldName);
            ps.setString(2, playerUUID.toString());
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void setExpansionBlocks(UUID playerUUID, int blocks) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO expansion_blocks (player_uuid, blocks) VALUES (?, ?)")) {
            ps.setString(1, playerUUID.toString());
            ps.setInt(2, blocks);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public int getExpansionBlocks(UUID playerUUID) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT blocks FROM expansion_blocks WHERE player_uuid = ?")) {
            ps.setString(1, playerUUID.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("blocks");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void addExpansionBlocks(UUID playerUUID, int blocks) {
        int current = getExpansionBlocks(playerUUID);
        setExpansionBlocks(playerUUID, current + blocks);
    }

    public void addInvite(String worldName, UUID playerUUID, String role) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO invites (world_name, player_uuid, role) VALUES (?, ?, ?)")) {
            ps.setString(1, worldName);
            ps.setString(2, playerUUID.toString());
            ps.setString(3, role);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void removeInvite(String worldName, UUID playerUUID) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM invites WHERE world_name = ? AND player_uuid = ?")) {
            ps.setString(1, worldName);
            ps.setString(2, playerUUID.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getInviteRole(String worldName, UUID playerUUID) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT role FROM invites WHERE world_name = ? AND player_uuid = ?")) {
            ps.setString(1, worldName);
            ps.setString(2, playerUUID.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("role");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<String> getPendingInvites(UUID playerUUID) {
        List<String> invites = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT world_name FROM invites WHERE player_uuid = ?")) {
            ps.setString(1, playerUUID.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                invites.add(rs.getString("world_name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return invites;
    }

    public List<UUID> getInvitedPlayers(String worldName) {
        List<UUID> players = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT player_uuid FROM invites WHERE world_name = ?")) {
            ps.setString(1, worldName);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                players.add(UUID.fromString(rs.getString("player_uuid")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return players;
    }

    public void setSpawnWorld(String worldName, Location loc) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO spawn_world (id, world_name, x, y, z, yaw, pitch) VALUES (1, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, worldName);
            ps.setDouble(2, loc.getX());
            ps.setDouble(3, loc.getY());
            ps.setDouble(4, loc.getZ());
            ps.setFloat(5, loc.getYaw());
            ps.setFloat(6, loc.getPitch());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void removeSpawnWorld() {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM spawn_world WHERE id = 1")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Location getSpawnLocation() {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM spawn_world WHERE id = 1")) {
            if (rs.next()) {
                String worldName = rs.getString("world_name");
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    world = Bukkit.createWorld(new org.bukkit.WorldCreator(worldName));
                }
                if (world != null) {
                    double x = rs.getDouble("x");
                    double y = rs.getDouble("y");
                    double z = rs.getDouble("z");
                    float yaw = rs.getFloat("yaw");
                    float pitch = rs.getFloat("pitch");
                    return new Location(world, x, y, z, yaw, pitch);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getSpawnWorld() {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT world_name FROM spawn_world WHERE id = 1")) {
            if (rs.next()) {
                return rs.getString("world_name");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void setWorldSpawn(String worldName, Location loc) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO world_spawns (world_name, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, worldName);
            ps.setDouble(2, loc.getX());
            ps.setDouble(3, loc.getY());
            ps.setDouble(4, loc.getZ());
            ps.setFloat(5, loc.getYaw());
            ps.setFloat(6, loc.getPitch());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Location getWorldSpawn(String worldName) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM world_spawns WHERE world_name = ?")) {
            ps.setString(1, worldName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    double x = rs.getDouble("x");
                    double y = rs.getDouble("y");
                    double z = rs.getDouble("z");
                    float yaw = rs.getFloat("yaw");
                    float pitch = rs.getFloat("pitch");
                    return new Location(world, x, y, z, yaw, pitch);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            return world.getSpawnLocation();
        }
        return null;
    }

    public boolean hasCustomWorldSpawn(String worldName) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM world_spawns WHERE world_name = ?")) {
            ps.setString(1, worldName);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void setExtraWorldCredits(UUID playerUUID, int credits) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO extra_world_credits (player_uuid, extra_worlds) VALUES (?, ?)")) {
            ps.setString(1, playerUUID.toString());
            ps.setInt(2, credits);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public int getExtraWorldCredits(UUID playerUUID) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT extra_worlds FROM extra_world_credits WHERE player_uuid = ?")) {
            ps.setString(1, playerUUID.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("extra_worlds");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void addExtraWorldCredits(UUID playerUUID, int credits) {
        int current = getExtraWorldCredits(playerUUID);
        setExtraWorldCredits(playerUUID, current + credits);
    }

    public List<UUID> getAllOnlineOwners() {
        List<UUID> online = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!getOwnedWorlds(p.getUniqueId()).isEmpty()) {
                online.add(p.getUniqueId());
            }
        }
        return online;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


}