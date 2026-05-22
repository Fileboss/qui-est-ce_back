package game;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameUpdateEvent(
        String gameId,
        String type,           // "GAME_CREATED" | "STATE_CHANGE" | "DELETED" | "CHAT_MESSAGE" | "CHAT_ERROR"
        String gameState,      // null when type = "DELETED" or chat
        Boolean correct,       // non-null only after a guess; null otherwise
        Integer playersJoined, // non-null only on join events; null otherwise
        String senderSub,      // CHAT_MESSAGE / CHAT_ERROR only — stable sub of the chat sender
        String senderName,     // CHAT_MESSAGE only — preferred_username, falls back to senderSub
        String text            // CHAT_MESSAGE (message body) / CHAT_ERROR (error reason)
) {}
