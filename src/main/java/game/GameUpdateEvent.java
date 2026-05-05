package game;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameUpdateEvent(
        String gameId,
        String type,      // "GAME_CREATED" | "STATE_CHANGE" | "DELETED"
        String gameState, // null when type = "DELETED"
        Boolean correct   // non-null only after a guess; null otherwise
) {}
