package game;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.WebSocket;
import lombok.RequiredArgsConstructor;
import util.JsonSerializationException;

@WebSocket(path = "/ws/games")
@RequiredArgsConstructor
public class GamesWebSocket {

    private final GameRegistry gameRegistry;
    private final ObjectMapper objectMapper;

    @OnOpen
    public String onOpen() {
        try {
            return objectMapper.writeValueAsString(gameRegistry.findAll());
        } catch (JsonProcessingException e) {
            throw new JsonSerializationException(e);
        }
    }
}
