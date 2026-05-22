package game;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.event.Event;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.security.Principal;
import util.JsonSerializationException;

@WebSocket(path = "/ws/game/{gameId}")
@Authenticated
@RequiredArgsConstructor
public class GameWebSocket {

    static final int MAX_CHAT_TEXT = 500;
    private static final String CHAT_MESSAGE = "CHAT_MESSAGE";
    private static final String CHAT_ERROR = "CHAT_ERROR";

    private final GameRegistry gameRegistry;
    private final ObjectMapper objectMapper;
    private final SecurityIdentity identity;
    private final Event<GameUpdateEvent> gameUpdateEvent;

    @OnOpen
    public String onOpen(WebSocketConnection connection) {
        String gameId = connection.pathParam("gameId");
        GameUpdateEvent event = gameRegistry.findGame(gameId)
                .map(engine -> new GameUpdateEvent(gameId, "STATE_CHANGE", engine.getGameState().toString(), null, engine.getPlayersJoined(), null, null, null))
                .orElseGet(() -> new GameUpdateEvent(gameId, "DELETED", null, null, null, null, null, null));
        return serialize(event);
    }

    @OnTextMessage
    public void onTextMessage(String raw, WebSocketConnection connection) {
        String gameId = connection.pathParam("gameId");
        Inbound inbound;
        try {
            inbound = objectMapper.readValue(raw, Inbound.class);
        } catch (JsonProcessingException e) {
            sendError(connection, gameId, "malformed message");
            return;
        }
        if (inbound == null || !CHAT_MESSAGE.equals(inbound.type())) {
            sendError(connection, gameId, "unsupported message type");
            return;
        }
        String text = inbound.text();
        if (text == null || text.trim().isEmpty()) {
            sendError(connection, gameId, "text must not be blank");
            return;
        }
        if (text.length() > MAX_CHAT_TEXT) {
            sendError(connection, gameId, "text exceeds " + MAX_CHAT_TEXT + " chars");
            return;
        }
        String callerSub = identity.getPrincipal().getName();
        var engineOpt = gameRegistry.findGame(gameId);
        if (engineOpt.isEmpty()) {
            sendError(connection, gameId, "game not found");
            return;
        }
        if (!engineOpt.get().isParticipant(callerSub)) {
            sendError(connection, gameId, "not a participant");
            return;
        }
        String senderName = resolveSenderName(callerSub);
        gameUpdateEvent.fire(new GameUpdateEvent(gameId, CHAT_MESSAGE, null, null, null, callerSub, senderName, text));
    }

    private String resolveSenderName(String fallback) {
        Principal principal = identity.getPrincipal();
        if (principal instanceof JsonWebToken token) {
            Object claim = token.getClaim("preferred_username");
            if (claim instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return fallback;
    }

    private void sendError(WebSocketConnection connection, String gameId, String reason) {
        GameUpdateEvent error = new GameUpdateEvent(gameId, CHAT_ERROR, null, null, null, null, null, reason);
        try {
            connection.sendTextAndAwait(serialize(error));
        } catch (RuntimeException e) {
            Log.warnf(e, "WS chat error frame failed for connection %s", connection.id());
        }
    }

    private String serialize(GameUpdateEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new JsonSerializationException(e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Inbound(String type, String text) {}
}
