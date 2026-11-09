package com.boshywashy.buildworld.utils;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Detects Bedrock players connected through GeyserMC.
 *
 * Floodgate / Geyser are looked up by reflection, so BuildWorld needs no extra dependency and
 * works whether or not they are installed. If neither API can be reached it falls back to the
 * UUID pattern Floodgate gives Bedrock players (most significant bits are zero).
 */
public final class BedrockUtils {
    private static final long RETRY_MILLIS = 30_000L;

    private static long lastLookup = 0L;
    private static Object floodgate;
    private static Method floodgateCheck;
    private static Object geyser;
    private static Method geyserCheck;

    private BedrockUtils() {
    }

    public static boolean isBedrock(Player player) {
        lookupApis();
        UUID id = player.getUniqueId();

        boolean apiAvailable = false;
        try {
            if (floodgate != null && floodgateCheck != null) {
                apiAvailable = true;
                if (Boolean.TRUE.equals(floodgateCheck.invoke(floodgate, id))) return true;
            }
            if (geyser != null && geyserCheck != null) {
                apiAvailable = true;
                if (Boolean.TRUE.equals(geyserCheck.invoke(geyser, id))) return true;
            }
        } catch (ReflectiveOperationException ignored) {
            // fall through to the UUID check
        }

        // Only guess from the UUID when no API could answer for us
        return !apiAvailable && id.getMostSignificantBits() == 0L;
    }

    /** Looks the APIs up, retrying now and then in case Floodgate/Geyser finished loading after us. */
    private static synchronized void lookupApis() {
        boolean complete = floodgate != null && geyser != null;
        long now = System.currentTimeMillis();
        if (complete || now - lastLookup < RETRY_MILLIS) return;
        lastLookup = now;

        if (floodgate == null) {
            try {
                Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                floodgate = api.getMethod("getInstance").invoke(null);
                floodgateCheck = api.getMethod("isFloodgatePlayer", UUID.class);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                floodgate = null;
            }
        }
        if (geyser == null) {
            try {
                Class<?> api = Class.forName("org.geysermc.geyser.api.GeyserApi");
                geyser = api.getMethod("api").invoke(null);
                geyserCheck = api.getMethod("isBedrockPlayer", UUID.class);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                geyser = null;
            }
        }
    }
}
