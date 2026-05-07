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

    @SuppressWarnings("unchecked")
    private final Event<GameUpdateEvent> eventBus = mock(Event.class);
    private GameRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new GameRegistry(eventBus);
    }

    private GameUpdateEvent capturedSingleEvent() {
        ArgumentCaptor<GameUpdateEvent> captor = ArgumentCaptor.forClass(GameUpdateEvent.class);
        verify(eventBus).fireAsync(captor.capture());
        return captor.getValue();
    }

    private List<GameUpdateEvent> capturedAllEvents() {
        ArgumentCaptor<GameUpdateEvent> captor = ArgumentCaptor.forClass(GameUpdateEvent.class);
        verify(eventBus, org.mockito.Mockito.atLeastOnce()).fireAsync(captor.capture());
        return captor.getAllValues();
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
        registry.removeGame(dto.gameId());

        assertThatThrownBy(() -> registry.getGame(dto.gameId()))
                .isInstanceOf(IllegalArgumentException.class);

        GameUpdateEvent deleted = capturedAllEvents().get(1); // [0] = GAME_CREATED, [1] = DELETED
        assertThat(deleted.type()).isEqualTo("DELETED");
        assertThat(deleted.gameId()).isEqualTo(dto.gameId());
        assertThat(deleted.gameState()).isNull();
        assertThat(deleted.correct()).isNull();
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
    void startGame_transitionsToStarted_andFiresStateChange() {
        GameDTO dto = registry.createGame(SINGLE);
        registry.startGame(dto.gameId());

        assertThat(registry.getGame(dto.gameId()).getGameState())
                .isEqualTo(GameEngine.GameState.STARTED);

        GameUpdateEvent stateChange = capturedAllEvents().get(1);
        assertThat(stateChange.type()).isEqualTo(GameRegistry.STATE_CHANGE);
        assertThat(stateChange.gameId()).isEqualTo(dto.gameId());
        assertThat(stateChange.gameState()).isEqualTo("STARTED");
        assertThat(stateChange.correct()).isNull();
    }

    @Test
    void startGame_throwsIllegalArgumentException_whenIdUnknown_andFiresNoEvent() {
        assertThatThrownBy(() -> registry.startGame("nope"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(eventBus, never()).fireAsync(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resetGame_clearsCards_andFiresStateChange() {
        GameDTO dto = registry.createGame(SINGLE);
        registry.startGame(dto.gameId());
        registry.player1Guess(dto.gameId(), CARD_1.id());
        registry.resetGame(dto.gameId());

        GameEngine engine = registry.getGame(dto.gameId());
        assertThat(engine.getGameState()).isEqualTo(GameEngine.GameState.NOT_STARTED);
        assertThat(engine.getCardDTOs()).isNull();

        // Last fired event is the reset's STATE_CHANGE.
        List<GameUpdateEvent> all = capturedAllEvents();
        GameUpdateEvent last = all.get(all.size() - 1);
        assertThat(last.type()).isEqualTo(GameRegistry.STATE_CHANGE);
        assertThat(last.gameState()).isEqualTo("NOT_STARTED");
        assertThat(last.correct()).isNull();
    }

    @Test
    void player1Guess_correctAnswer_returnsTrue_andFiresWithCorrectTrue() {
        GameDTO dto = registry.createGame(SINGLE);
        registry.startGame(dto.gameId());

        boolean result = registry.player1Guess(dto.gameId(), CARD_1.id());

        assertThat(result).isTrue();
        GameUpdateEvent guessEvent = capturedAllEvents().get(2); // [0]=created, [1]=start, [2]=guess
        assertThat(guessEvent.type()).isEqualTo(GameRegistry.STATE_CHANGE);
        assertThat(guessEvent.gameState()).isEqualTo("PLAYER_1_WINS");
        assertThat(guessEvent.correct()).isTrue();
    }

    @Test
    void player1Guess_wrongAnswer_returnsFalse_andFiresWithCorrectFalse_andStaysStarted() {
        GameDTO dto = registry.createGame(SINGLE);
        registry.startGame(dto.gameId());

        boolean result = registry.player1Guess(dto.gameId(), "99");

        assertThat(result).isFalse();
        assertThat(registry.getGame(dto.gameId()).getGameState())
                .isEqualTo(GameEngine.GameState.STARTED);
        GameUpdateEvent guessEvent = capturedAllEvents().get(2);
        assertThat(guessEvent.gameState()).isEqualTo("STARTED");
        assertThat(guessEvent.correct()).isFalse();
    }

    @Test
    void player2Guess_correctAnswer_returnsTrue_andFiresWithCorrectTrue() {
        GameDTO dto = registry.createGame(SINGLE);
        registry.startGame(dto.gameId());

        boolean result = registry.player2Guess(dto.gameId(), CARD_1.id());

        assertThat(result).isTrue();
        GameUpdateEvent guessEvent = capturedAllEvents().get(2);
        assertThat(guessEvent.gameState()).isEqualTo("PLAYER_2_WINS");
        assertThat(guessEvent.correct()).isTrue();
    }
}
