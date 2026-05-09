package game;

import card.CardDTO;
import org.junit.jupiter.api.Test;
import util.PlayerConflictException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameEngineTest {

    private static final CardDTO CARD_1 = new CardDTO("1", "Alice", "http://img/1", "1");
    private static final CardDTO CARD_2 = new CardDTO("2", "Bob",   "http://img/2", "1");
    private static final CardDTO CARD_3 = new CardDTO("3", "Cara",  "http://img/3", "1");
    private static final List<CardDTO> DECK = List.of(CARD_1, CARD_2, CARD_3);
    private static final List<CardDTO> SINGLE = List.of(CARD_1);

    private static final String SUB_1 = "user-sub-1";
    private static final String SUB_2 = "user-sub-2";

    private static GameEngine prepared(List<CardDTO> deck) {
        GameEngine e = new GameEngine();
        e.create(deck);
        return e;
    }

    private static GameEngine started(List<CardDTO> deck) {
        GameEngine e = prepared(deck);
        e.player1Join(SUB_1);
        e.player2Join(SUB_2);
        e.start();
        return e;
    }

    @Test
    void create_inNotStarted_transitionsToPreparingAndStoresDeck() {
        GameEngine engine = new GameEngine();
        engine.create(DECK);
        assertThat(engine.getGameState()).isEqualTo(GameEngine.GameState.PREPARING);
        assertThat(engine.getCardDTOs()).isEqualTo(DECK);
    }

    @Test
    void create_singleCardDeck_assignsSameTargetToBothPlayers() {
        GameEngine engine = prepared(SINGLE);
        assertThat(engine.player1Join(SUB_1)).isEqualTo(CARD_1);
        assertThat(engine.player2Join(SUB_2)).isEqualTo(CARD_1);
    }

    @Test
    void create_throws_whenAlreadyPreparing() {
        GameEngine engine = prepared(DECK);
        assertThatThrownBy(() -> engine.create(DECK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Can only create a game if not started");
        assertThat(engine.getGameState()).isEqualTo(GameEngine.GameState.PREPARING);
    }

    @Test
    void create_throws_whenStarted() {
        GameEngine engine = started(DECK);
        assertThatThrownBy(() -> engine.create(DECK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Can only create a game if not started");
    }

    @Test
    void create_throws_onEmptyDeck_andStaysInNotStarted() {
        GameEngine engine = new GameEngine();
        assertThatThrownBy(() -> engine.create(List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("A game cannot be started with an empty card pack");
        assertThat(engine.getGameState()).isEqualTo(GameEngine.GameState.NOT_STARTED);
    }

    @Test
    void create_assignsCardsFromDeck_toBothPlayers() {
        GameEngine engine = prepared(DECK);
        assertThat(engine.player1Join(SUB_1)).isIn(DECK);
        assertThat(engine.player2Join(SUB_2)).isIn(DECK);
    }

    @Test
    void player1Join_throws_inNotStarted() {
        GameEngine engine = new GameEngine();
        assertThatThrownBy(() -> engine.player1Join(SUB_1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("being prepared");
    }

    @Test
    void player2Join_throws_inNotStarted() {
        GameEngine engine = new GameEngine();
        assertThatThrownBy(() -> engine.player2Join(SUB_2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("being prepared");
    }

    @Test
    void player1Join_throws_inStarted() {
        GameEngine engine = started(DECK);
        assertThatThrownBy(() -> engine.player1Join(SUB_1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void player2Join_throws_inStarted() {
        GameEngine engine = started(DECK);
        assertThatThrownBy(() -> engine.player2Join(SUB_2))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void player1Join_throws_whenSameSubAlreadyJoinedAsPlayer2() {
        GameEngine engine = prepared(DECK);
        engine.player2Join(SUB_1);
        assertThatThrownBy(() -> engine.player1Join(SUB_1))
                .isInstanceOf(PlayerConflictException.class)
                .hasMessageContaining("player 2");
    }

    @Test
    void player2Join_throws_whenSameSubAlreadyJoinedAsPlayer1() {
        GameEngine engine = prepared(DECK);
        engine.player1Join(SUB_1);
        assertThatThrownBy(() -> engine.player2Join(SUB_1))
                .isInstanceOf(PlayerConflictException.class)
                .hasMessageContaining("player 1");
    }

    @Test
    void player1Join_idempotent_whenSameSubRejoinsSameSlot() {
        GameEngine engine = prepared(DECK);
        engine.player1Join(SUB_1);
        // Re-joining the same slot must not throw and the player count stays at 1.
        engine.player1Join(SUB_1);
        assertThat(engine.getPlayersJoined()).isEqualTo(1);
    }

    @Test
    void getPlayersJoined_reflectsJoinState() {
        GameEngine engine = prepared(DECK);
        assertThat(engine.getPlayersJoined()).isZero();
        engine.player1Join(SUB_1);
        assertThat(engine.getPlayersJoined()).isEqualTo(1);
        engine.player2Join(SUB_2);
        assertThat(engine.getPlayersJoined()).isEqualTo(2);
    }

    @Test
    void start_inPreparing_withBothPlayers_transitionsToStarted() {
        GameEngine engine = prepared(DECK);
        engine.player1Join(SUB_1);
        engine.player2Join(SUB_2);
        engine.start();
        assertThat(engine.getGameState()).isEqualTo(GameEngine.GameState.STARTED);
    }

    @Test
    void start_throws_inNotStarted() {
        GameEngine engine = new GameEngine();
        assertThatThrownBy(engine::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Can only start a game which is being prepared");
    }

    @Test
    void start_throws_whenNoPlayersJoined() {
        GameEngine engine = prepared(DECK);
        assertThatThrownBy(engine::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot start a game until both players have joined");
    }

    @Test
    void start_throws_whenOnlyPlayer1HasJoined() {
        GameEngine engine = prepared(DECK);
        engine.player1Join(SUB_1);
        assertThatThrownBy(engine::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot start a game until both players have joined");
    }

    @Test
    void start_throws_whenOnlyPlayer2HasJoined() {
        GameEngine engine = prepared(DECK);
        engine.player2Join(SUB_2);
        assertThatThrownBy(engine::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot start a game until both players have joined");
    }

    @Test
    void start_throws_whenAlreadyStarted() {
        GameEngine engine = started(DECK);
        assertThatThrownBy(engine::start).isInstanceOf(IllegalStateException.class);
        assertThat(engine.getGameState()).isEqualTo(GameEngine.GameState.STARTED);
    }

    @Test
    void player1Guess_correctId_returnsTrue_andTransitionsToPlayer1Wins() {
        GameEngine engine = started(SINGLE);
        boolean correct = engine.player1Guess(CARD_1.id());
        assertThat(correct).isTrue();
        assertThat(engine.getGameState()).isEqualTo(GameEngine.GameState.PLAYER_1_WINS);
    }

    @Test
    void player1Guess_wrongId_returnsFalse_andStaysStarted() {
        GameEngine engine = started(SINGLE);
        boolean correct = engine.player1Guess("99");
        assertThat(correct).isFalse();
        assertThat(engine.getGameState()).isEqualTo(GameEngine.GameState.STARTED);
    }

    @Test
    void player1Guess_throws_outsideStarted() {
        GameEngine notStarted = new GameEngine();
        assertThatThrownBy(() -> notStarted.player1Guess("1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Can only guess if the game is started");

        GameEngine preparing = prepared(SINGLE);
        assertThatThrownBy(() -> preparing.player1Guess("1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void player2Guess_correctId_transitionsToPlayer2Wins() {
        GameEngine engine = started(SINGLE);
        boolean correct = engine.player2Guess(CARD_1.id());
        assertThat(correct).isTrue();
        assertThat(engine.getGameState()).isEqualTo(GameEngine.GameState.PLAYER_2_WINS);
    }

    @Test
    void player2Guess_wrongId_staysStarted() {
        GameEngine engine = started(SINGLE);
        assertThat(engine.player2Guess("99")).isFalse();
        assertThat(engine.getGameState()).isEqualTo(GameEngine.GameState.STARTED);
    }

    @Test
    void reset_fromPlayer1Wins_clearsCardsAndSubs_andReturnsToNotStarted() {
        GameEngine engine = started(SINGLE);
        engine.player1Guess(CARD_1.id());
        engine.reset();
        assertThat(engine.getGameState()).isEqualTo(GameEngine.GameState.NOT_STARTED);
        assertThat(engine.getCardDTOs()).isNull();
        assertThat(engine.getPlayersJoined()).isZero();
    }

    @Test
    void reset_fromPlayer2Wins_returnsToNotStarted() {
        GameEngine engine = started(SINGLE);
        engine.player2Guess(CARD_1.id());
        engine.reset();
        assertThat(engine.getGameState()).isEqualTo(GameEngine.GameState.NOT_STARTED);
    }

    @Test
    void reset_throws_inNotStarted() {
        GameEngine engine = new GameEngine();
        assertThatThrownBy(engine::reset)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Can only reset a game if it is over");
    }

    @Test
    void reset_throws_inPreparing() {
        GameEngine engine = prepared(SINGLE);
        assertThatThrownBy(engine::reset).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reset_throws_inStarted() {
        GameEngine engine = started(SINGLE);
        assertThatThrownBy(engine::reset).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reset_allowsRejoinAfterCreate_withDifferentSubs() {
        GameEngine engine = started(SINGLE);
        engine.player1Guess(CARD_1.id());
        engine.reset();
        engine.create(SINGLE);
        // After reset, the same sub may take any slot again.
        engine.player1Join(SUB_2);
        engine.player2Join(SUB_1);
        engine.start();
        assertThat(engine.getGameState()).isEqualTo(GameEngine.GameState.STARTED);
    }
}
