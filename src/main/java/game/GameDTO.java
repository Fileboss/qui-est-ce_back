package game;

import card.CardDTO;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Represents the observable state of a game, returned on creation and listing. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameDTO(String gameId, String gameState, List<CardDTO> cards) {
}
