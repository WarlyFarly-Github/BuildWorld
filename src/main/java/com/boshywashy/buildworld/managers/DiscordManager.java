package com.boshywashy.buildworld.managers;

import com.boshywashy.buildworld.BuildWorld;
import com.boshywashy.buildworld.database.DatabaseManager;
import com.boshywashy.buildworld.database.ExpansionRequest;
import com.boshywashy.buildworld.utils.MessageUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sends world border expansion requests to a Discord channel with Accept / Decline buttons,
 * and applies the decision when a reviewer clicks one.
 *
 * All database and Bukkit work is done on the main server thread; JDA callbacks hop over to it.
 */
public class DiscordManager implements Listener {
    private static final String ID_PREFIX = "bwexp";

    public static final String PENDING = "PENDING";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String DECLINED = "DECLINED";
    public static final String FAILED = "FAILED";

    private static final int COLOR_PENDING = 0xFEE75C;
    private static final int COLOR_ACCEPTED = 0x57F287;
    private static final int COLOR_DECLINED = 0xED4245;

    private static final String DEFAULT_ACCEPTED =
            "%primary%{discuser} accepted your expansion request for %secondary%{world}%primary%! You now have %secondary%{distance}%primary%.";
    private static final String DEFAULT_DECLINED =
            "&c{discuser} declined your expansion request for {world}.";
    private static final String DEFAULT_FAILED =
            "&cYour expansion request for {world} could not be applied. The world may no longer exist, or you no longer have enough expansion blocks.";

    private final BuildWorld plugin;
    private final AtomicInteger generation = new AtomicInteger();

    private volatile JDA jda;
    private volatile boolean configured;
    private volatile String channelId = "";
    private volatile String reviewerRoleId = "";

    public DiscordManager(BuildWorld plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ lifecycle

    /**
     * Reads the Discord settings from config.yml and (re)connects the bot.
     * Safe to call again after a config reload.
     */
    public void start() {
        shutdown();

        String token = plugin.getConfig().getString("Discord.BotToken", "").trim();
        channelId = plugin.getConfig().getString("Discord.ChannelId", "").trim();
        reviewerRoleId = plugin.getConfig().getString("Discord.ReviewerRoleId", "").trim();
        configured = !token.isEmpty() && !channelId.isEmpty();

        if (!configured) {
            plugin.getLogger().info("Discord expansion requests are off (set Discord.BotToken and Discord.ChannelId in config.yml to enable them).");
            return;
        }

        final int myGeneration = generation.incrementAndGet();

        // Connecting blocks for a few seconds, so keep it off the main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                JDA built = JDABuilder.createLight(token, Collections.emptyList()) // no extra intents needed; JDA always receives guild/channel data
                        .addEventListeners(new ButtonListener())
                        .build();
                built.awaitReady();

                if (generation.get() != myGeneration) {
                    built.shutdownNow(); // config was reloaded or plugin disabled while connecting
                    return;
                }
                jda = built;
                plugin.getLogger().info("Connected to Discord as " + built.getSelfUser().getName() + ".");
            } catch (Exception e) {
                plugin.getLogger().severe("Could not connect the Discord bot: " + e.getMessage());
            }
        });
    }

    public void shutdown() {
        generation.incrementAndGet(); // cancels any connection attempt still in progress
        JDA current = jda;
        jda = null;
        if (current != null) {
            current.shutdown();
        }
    }

    /** True when a bot token and channel id are set. When true, /bw ExpandWorld requires staff approval. */
    public boolean isConfigured() {
        return configured;
    }

    // ------------------------------------------------------------------ sending requests

    /**
     * Called on the main thread after the command has validated the world, ownership and block balance.
     */
    public void submitExpansionRequest(Player player, String worldName, int blocks) {
        DatabaseManager db = plugin.getDatabaseManager();

        JDA api = jda;
        if (api == null) {
            player.sendMessage(MessageUtils.colorize("&cThe Discord approval system isn't connected right now. Please try again later."));
            return;
        }

        if (db.hasPendingExpansionRequest(worldName)) {
            player.sendMessage(MessageUtils.colorize("&cThis world already has an expansion request waiting for review."));
            return;
        }

        MessageChannel channel = findChannel(api);
        if (channel == null) {
            plugin.getLogger().warning("Discord.ChannelId is wrong, or the bot can't see that channel.");
            player.sendMessage(MessageUtils.colorize("&cExpansion requests are unavailable right now. Please tell a staff member."));
            return;
        }

        String requestId = UUID.randomUUID().toString();
        String nickname = db.getNickname(worldName);
        int currentDiameter = db.getDiameter(worldName);
        int available = db.getExpansionBlocks(player.getUniqueId());

        db.createExpansionRequest(new ExpansionRequest(
                requestId, player.getUniqueId(), player.getName(), worldName, blocks, PENDING, null, 0));

        MessageEmbed embed = new EmbedBuilder()
                .setTitle("World Border Expansion Request")
                .setColor(COLOR_PENDING)
                .addField("Player", player.getName(), true)
                .addField("World", nickname + " (`" + worldName + "`)", true)
                .addField("Requested", "+" + blocks + " blocks", true)
                .addField("Border", currentDiameter + " \u2192 " + (currentDiameter + blocks) + " blocks", true)
                .addField("Player's balance", available + " expansion blocks", true)
                .setFooter("Request ID: " + requestId)
                .setTimestamp(Instant.now())
                .build();

        channel.sendMessageEmbeds(embed)
                .addComponents(ActionRow.of(
                        Button.success(ID_PREFIX + ":accept:" + requestId, "Accept"),
                        Button.danger(ID_PREFIX + ":decline:" + requestId, "Decline")))
                .queue(
                        sent -> runSync(() -> player.sendMessage(MessageUtils.colorize(
                                "%primary%Your request to expand %secondary%" + nickname + "%primary% by %secondary%" + blocks
                                        + "%primary% blocks was sent to staff on Discord. You'll be told when it's reviewed."))),
                        error -> runSync(() -> {
                            db.deleteExpansionRequest(requestId);
                            plugin.getLogger().warning("Could not post expansion request to Discord: " + error.getMessage());
                            player.sendMessage(MessageUtils.colorize("&cCould not send your expansion request. Please try again later."));
                        }));
    }

    private MessageChannel findChannel(JDA api) {
        try {
            return api.getChannelById(MessageChannel.class, channelId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ handling button clicks

    private final class ButtonListener extends ListenerAdapter {
        @Override
        public void onButtonInteraction(ButtonInteractionEvent event) {
            String[] parts = event.getComponentId().split(":", 3);
            if (parts.length != 3 || !parts[0].equals(ID_PREFIX)) return;

            boolean accept = parts[1].equals("accept");
            if (!accept && !parts[1].equals("decline")) return;
            String requestId = parts[2];

            String roleId = reviewerRoleId;
            if (!roleId.isEmpty()) {
                Member member = event.getMember();
                boolean allowed = member != null && member.getRoles().stream().anyMatch(r -> r.getId().equals(roleId));
                if (!allowed) {
                    event.reply("You don't have permission to review expansion requests.").setEphemeral(true).queue();
                    return;
                }
            }

            // Acknowledge now (Discord gives us 3 seconds); the real work happens on the main thread
            event.deferEdit().queue();

            String reviewer = event.getUser().getName();
            InteractionHook hook = event.getHook();
            MessageEmbed original = event.getMessage().getEmbeds().isEmpty() ? null : event.getMessage().getEmbeds().get(0);

            runSync(() -> handleDecision(requestId, accept, reviewer, hook, original));
        }
    }

    private void handleDecision(String requestId, boolean accept, String reviewer, InteractionHook hook, MessageEmbed original) {
        DatabaseManager db = plugin.getDatabaseManager();
        ExpansionRequest request = db.getExpansionRequest(requestId);

        if (request == null) {
            finishMessage(hook, original, "\u26A0\uFE0F This request no longer exists.", COLOR_DECLINED);
            return;
        }
        if (!request.status().equals(PENDING)) {
            hook.sendMessage("This request was already reviewed.").setEphemeral(true).queue();
            return;
        }

        UUID owner = request.playerUuid();
        String world = request.worldName();

        // Work out the outcome first, using the current state (things may have changed since the request)
        String status;
        if (!accept) {
            status = DECLINED;
        } else if (!db.isBuildWorld(world) || !db.isOwner(world, owner) || db.getExpansionBlocks(owner) < request.blocks()) {
            status = FAILED;
        } else {
            status = ACCEPTED;
        }

        int newDiameter = status.equals(ACCEPTED) ? db.getDiameter(world) + request.blocks() : 0;

        // Claim the request; if this returns false someone else already decided it
        if (!db.decideExpansionRequest(requestId, status, reviewer, newDiameter)) {
            hook.sendMessage("This request was already reviewed.").setEphemeral(true).queue();
            return;
        }

        if (status.equals(ACCEPTED)) {
            db.setDiameter(world, newDiameter);
            db.addExpansionBlocks(owner, -request.blocks());
            org.bukkit.World bukkitWorld = Bukkit.getWorld(world);
            if (bukkitWorld != null) {
                bukkitWorld.getWorldBorder().setSize(newDiameter);
            }
        }

        switch (status) {
            case ACCEPTED -> finishMessage(hook, original, "\u2705 Accepted by " + reviewer + " (now " + newDiameter + " blocks)", COLOR_ACCEPTED);
            case DECLINED -> finishMessage(hook, original, "\u274C Declined by " + reviewer, COLOR_DECLINED);
            default -> finishMessage(hook, original,
                    "\u26A0\uFE0F Could not be applied (the world is gone, or the player no longer has enough expansion blocks)", COLOR_DECLINED);
        }

        Player online = Bukkit.getPlayer(owner);
        if (online != null) {
            deliverResults(online);
        }
        // If they're offline, onJoin delivers it the next time they connect
    }

    /** Replaces the request message's buttons with the outcome. */
    private void finishMessage(InteractionHook hook, MessageEmbed original, String statusText, int color) {
        EmbedBuilder builder = original != null
                ? new EmbedBuilder(original)
                : new EmbedBuilder().setTitle("World Border Expansion Request");
        builder.setColor(color).addField("Status", statusText, false);

        hook.editOriginalEmbeds(builder.build())
                .setComponents(Collections.emptyList())
                .queue(null, error -> plugin.getLogger().warning("Could not update the Discord request message: " + error.getMessage()));
    }

    // ------------------------------------------------------------------ telling the player

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Small delay so the message isn't buried under the join spam
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) deliverResults(player);
        }, 40L);
    }

    private void deliverResults(Player player) {
        DatabaseManager db = plugin.getDatabaseManager();
        for (ExpansionRequest result : db.getUnnotifiedExpansionResults(player.getUniqueId())) {
            player.sendMessage(formatResult(result));
            db.markExpansionNotified(result.id());
        }
    }

    /**
     * Builds the in-game message from config.yml. Placeholders:
     * {discuser} the Discord reviewer, {world} the world's name, {distance} the new border size,
     * {blocks} how many blocks were requested.
     */
    private String formatResult(ExpansionRequest request) {
        String template;
        switch (request.status()) {
            case ACCEPTED -> template = plugin.getConfig().getString("Discord.Messages.Accepted", DEFAULT_ACCEPTED);
            case DECLINED -> template = plugin.getConfig().getString("Discord.Messages.Declined", DEFAULT_DECLINED);
            default -> template = plugin.getConfig().getString("Discord.Messages.Failed", DEFAULT_FAILED);
        }

        String reviewer = request.decidedBy() != null ? request.decidedBy() : "Staff";
        String world = plugin.getDatabaseManager().getNickname(request.worldName());

        return MessageUtils.colorize(template
                .replace("{discuser}", reviewer)
                .replace("{world}", world)
                .replace("{distance}", request.newDiameter() + " blocks")
                .replace("{blocks}", String.valueOf(request.blocks())));
    }

    private void runSync(Runnable task) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
}
