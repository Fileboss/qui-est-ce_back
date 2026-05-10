package game;

import card.CardDTO;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class GameRegistryTest {

    private static final CardDTO CARD_1 = new CardDTO("1", "Alice", "http://img/1", "1");
    private static final List<CardDTO> SINGLE = List.of(CARD_1);

    private static final String SUB_1 = "user-sub-1";
    private static final String SUB_2 = "user-sub-2";
    private static final String SUB_3 = "user-sub-3";

    @SuppressWarnings("unchecked")
    private final Event<GameUpdateEvent> eventBus = mock(Event.class);
    private GameRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new GameRegistry(eventBus);
    }

    private GameUpdateEvent capturedSingleEvent() {
        ArgumentCaptor<GameUpdateEvent> captor = ArgumentCaptor.forClass(GameUpdateEvent.class);
        verify(eventBus).fire(captor.capture());
        return captor.getValue();
    }

    private List<GameUpdateEvent> capturedAllEvents() {
        ArgumentCaptor<GameUpdateEvent> captor = ArgumentCaptor.forClass(GameUpdateEvent.class);
        verify(eventBus, org.mockito.Mockito.atLeastOnce()).fire(captor.capture());
        return captor.getAllValues();
    }

    private String createGameAndJoinBothPlayers() {
        GameDTO dto = registry.createGame(SINGLE);
        registry.join(dto.gameId(), SUB_1);
        registry.join(dto.gameId(), SUB_2);
        return dto.gameId();
    }

    @Test
    void createGame_returnsDtoWithUuid_andFiresGameCreatedEvent() {
        GameDTO dto = registry.createGame(SINGLE);

        assertThat(dto.gameId()).isNotNull().isNotBlank();
        assertThat(dto.gameState()).isEqualTo("PREPARING");
        assertThat(dto.cards()).isEqualTo(SINGLE);

        GameUpdateEvent fired = capturedSingleEvent();
        assertThat(fired.gameId()).isEqualTo(dto.gameId());
        assertThat(fired.type()).isEqualTo("GAME_CREATED");
        assertThat(fired.gameState()).isEqualTo("PREPARING");
        assertThat(fired.correct()).isNull();
        assertThat(fired.playersJoined()).isNull();
    }

    @Test
    void createGame_producesDistinctIds_acrossCalls() {
        GameDTO first = registry.createGame(SINGLE);
        GameDTO second = registry.createGame(SINGLE);
        assertThat(first.gameId()).isNotEqualTo(second.gameId());
    }

    @Test
    void createGame_storesEngine_retrievableByGetGame() {
        GameDTO dto = registry.createGame(SINGLE);
        GameEngine engine = registry.getGame(dto.gameId());
        assertThat(engine).isNotNull();
        assertThat(engine.getGameState()).isEqualTo(GameEngine.GameState.PREPARING);
        assertThat(engine.getCardDTOs()).isEqualTo(SINGLE);
    }

    @Test
    void findAll_returnsEmptyList_initially() {
        assertThat(registry.findAll()).isEmpty();
    }

    @Test
    void findAll_returnsAllRegisteredGames_withoutCards() {
        GameDTO a = registry.createGame(SINGLE);
        GameDTO b = registry.createGame(SINGLE);

        List<GameDTO> all = registry.findAll();

        assertThat(all).hasSize(2);
        assertThat(all).extracting(GameDTO::gameId).containsExactlyInAnyOrder(a.gameId(), b.gameId());
        assertThat(all).extracting(GameDTO::gameState).containsOnly("PREPARING");
        assertThat(all).allSatisfy(dto -> assertThat(dto.cards()).isNull());
    }

    @Test
    void getGame_throwsIllegalArgumentException_whenIdUnknown() {
        assertThatThrownBy(() -> registry.getGame("nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Game not found: nope");
    }

    @Test
    void findGame_returnsEmpty_whenIdUnknown() {
        assertThat(registry.findGame("nope")).isEmpty();
    }

    @Test
    void findGame_returnsPresent_whenIdRegistered() {
        GameDTO dto = registry.createGame(SINGLE);
        Optional<GameEngine> found = registry.findGame(dto.gameId());
        assertThat(found).isPresent();
    }

    @Test
    void removeGame_removesEntry_andFiresDeletedEvent() {
        GameDTO dto = registry.createGame(SINGLE);
        String gameId = dto.gameId();
        registry.removeGame(gameId);

        assertThatThrownBy(() -> registry.getGame(gameId))
                .isInstanceOf(IllegalArgumentException.class);

        GameUpdateEvent deleted = capturedAllEvents().get(1); // [0] = GAME_CREATED, [1] = DELETED
        assertThat(deleted.type()).isEqualTo("DELETED");
        assertThat(deleted.gameId()).isEqualTo(gameId);
        assertThat(deleted.gameState()).isNull();
        assertThat(deleted.correct()).isNull();
        assertThat(deleted.playersJoined()).isNull();
    }

    @Test
    void removeGame_silentNoOp_whenIdUnknown_butStillFiresDeletedEvent() {
        registry.removeGame("ghost");

        // Encodes current behavior: removeGame is unconditional and always fires DELETED,
        // even if the id was never registered. Front-end must tolerate spurious DELETED frames.
        GameUpdateEvent deleted = capturedSingleEvent();
        assertThat(deleted.type()).isEqualTo("DELETED");
        assertThat(deleted.gameId()).isEqualTo("ghost");
    }

    @Test
    void join_firstCaller_returnsCard_andFiresStateChangeWithPlayersJoinedOne() {
        GameDTO dto = registry.createGame(SINGLE);

        CardDTO card = registry.join(dto.gameId(), SUB_1);

        assertThat(card).isEqualTo(CARD_1);
        GameUpdateEvent joinEvent = capturedAllEvents().get(1); // [0]=created, [1]=join
        assertThat(joinEvent.type()).isEqualTo(GameRegistry.STATE_CHANGE);
        assertThat(joinEvent.gameState()).isEqualTo("PREPARING");
        assertThat(joinEvent.playersJoined()).isEqualTo(1);
        assertThat(joinEvent.correct()).isNull();
    }

    @Test
    void join_secondCaller_returnsCard_andFiresStateChangeWithPlayersJoinedTwo() {
        GameDTO dto = registry.createGame(SINGLE);
        registry.join(dto.gameId(), SUB_1);

        CardDTO card = registry.join(dto.gameId(), SUB_2);

        assertThat(card).isEqualTo(CARD_1);
        List<GameUpdateEvent> events = capturedAllEvents();
        GameUpdateEvent secondJoin = events.getLast();
        assertThat(secondJoin.type()).isEqualTo(GameRegistry.STATE_CHANGE);
        assertThat(secondJoin.playersJoined()).isEqualTo(2);
    }

    @Test
    void join_thirdCaller_throwsPlayerConflictException_andFiresNoAdditionalEvent() {
        GameDTO dto = registry.createGame(SINGLE);
        registry.join(dto.gameId(), SUB_1);
        registry.join(dto.gameId(), SUB_2);
        int eventsBefore = capturedAllEvents().size();

        assertThatThrownBy(() -> registry.join(dto.gameId(), SUB_3))
                .isInstanceOf(util.PlayerConflictException.class)
                .hasMessage("Game is full");

        assertThat(capturedAllEvents()).hasSize(eventsBefore);
    }

    @Test
    void startGame_transitionsToStarted_andFiresStateChange() {
        String gameId = createGameAndJoinBothPlayers();
        registry.startGame(gameId);

        assertThat(registry.getGame(gameId).getGameState())
                .isEqualTo(GameEngine.GameState.STARTED);

        List<GameUpdateEvent> events = capturedAllEvents();
        GameUpdateEvent stateChange = events.getLast();
        assertThat(stateChange.type()).isEqualTo(GameRegistry.STATE_CHANGE);
        assertThat(stateChange.gameId()).isEqualTo(gameId);
        assertThat(stateChange.gameState()).isEqualTo("STARTED");
        assertThat(stateChange.correct()).isNull();
        assertThat(stateChange.playersJoined()).isNull();
    }

    @Test
    void startGame_throws_whenLessThanTwoPlayersJoined() {
        GameDTO dto = registry.createGame(SINGLE);
        String gameId = dto.gameId();
        registry.join(gameId, SUB_1);

        assertThatThrownBy(() -> registry.startGame(gameId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("both players");
    }

    @Test
    void startGame_throwsIllegalArgumentException_whenIdUnknown_andFiresNoEvent() {
        assertThatThrownBy(() -> registry.startGame("nope"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(eventBus, never()).fire(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resetGame_clearsCards_andFiresStateChange() {
        String gameId = createGameAndJoinBothPlayers();
        registry.startGame(gameId);
        registry.guess(gameId, SUB_1, CARD_1.id());
        registry.resetGame(gameId);

        GameEngine engine = registry.getGame(gameId);
        assertThat(engine.getGameState()).isEqualTo(GameEngine.GameState.NOT_STARTED);
        assertThat(engine.getCardDTOs()).isNull();

        List<GameUpdateEvent> all = capturedAllEvents();
        GameUpdateEvent last = all.getLast();
        assertThat(last.type()).isEqualTo(GameRegistry.STATE_CHANGE);
        assertThat(last.gameState()).isEqualTo("NOT_STARTED");
        assertThat(last.correct()).isNull();
    }

    @Test
    void guess_player1_correctAnswer_returnsTrue_andFiresWithCorrectTrue() {
        String gameId = createGameAndJoinBothPlayers();
        registry.startGame(gameId);

        boolean result = registry.guess(gameId, SUB_1, CARD_1.id());

        assertThat(result).isTrue();
        List<GameUpdateEvent> events = capturedAllEvents();
        GameUpdateEvent guessEvent = events.getLast();
        assertThat(guessEvent.type()).isEqualTo(GameRegistry.STATE_CHANGE);
        assertThat(guessEvent.gameState()).isEqualTo("PLAYER_1_WINS");
        assertThat(guessEvent.correct()).isTrue();
    }

    @Test
    void guess_player1_wrongAnswer_returnsFalse_andFiresWithCorrectFalse_andStaysStarted() {
        String gameId = createGameAndJoinBothPlayers();
        registry.startGame(gameId);

        boolean result = registry.guess(gameId, SUB_1, "99");

        assertThat(result).isFalse();
        assertThat(registry.getGame(gameId).getGameState())
                .isEqualTo(GameEngine.GameState.STARTED);
        List<GameUpdateEvent> events = capturedAllEvents();
        GameUpdateEvent guessEvent = events.getLast();
        assertThat(guessEvent.gameState()).isEqualTo("STARTED");
        assertThat(guessEvent.correct()).isFalse();
    }

    @Test
    void guess_player2_correctAnswer_returnsTrue_andFiresWithCorrectTrue() {
        String gameId = createGameAndJoinBothPlayers();
        registry.startGame(gameId);

        boolean result = registry.guess(gameId, SUB_2, CARD_1.id());

        assertThat(result).isTrue();
        List<GameUpdateEvent> events = capturedAllEvents();
        GameUpdateEvent guessEvent = events.getLast();
        assertThat(guessEvent.gameState()).isEqualTo("PLAYER_2_WINS");
        assertThat(guessEvent.correct()).isTrue();
    }
}
