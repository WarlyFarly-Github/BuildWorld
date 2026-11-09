package com.boshywashy.buildworld.database;

import java.util.UUID;

/**
 * A world border expansion request that is sent to Discord for staff to accept or decline.
 *
 * @param id          unique request id (also embedded in the Discord button ids)
 * @param playerUuid  the player who asked for the expansion
 * @param playerName  the player's name at the time of the request
 * @param worldName   internal world name
 * @param blocks      how many blocks the player wants to expand by
 * @param status      PENDING, ACCEPTED, DECLINED or FAILED
 * @param decidedBy   Discord username of the reviewer (null while pending)
 * @param newDiameter the world's diameter after an accepted request (0 otherwise)
 */
public record ExpansionRequest(
        String id,
        UUID playerUuid,
        String playerName,
        String worldName,
        int blocks,
        String status,
        String decidedBy,
        int newDiameter
) {
}
