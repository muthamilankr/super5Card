package com.example.myapplication

import java.util.UUID
import java.io.Serializable

enum class Suit : Serializable {
    HEARTS, DIAMONDS, CLUBS, SPADES, JOKER
}

enum class Rank(val value: Int) : Serializable {
    ACE(1), TWO(2), THREE(3), FOUR(4), FIVE(5), SIX(6), SEVEN(7), EIGHT(8), NINE(9), TEN(10),
    JACK(10), QUEEN(10), KING(10), JOKER(0)
}

data class Card(
    val suit: Suit,
    val rank: Rank,
    val id: String = UUID.randomUUID().toString(),
    var isWildJoker: Boolean = false
) : Serializable {
    val points: Int
        get() = if (rank == Rank.JOKER || isWildJoker) 0 else rank.value
}

data class Player(
    val id: String,
    val name: String,
    var hand: List<Card> = emptyList(),
    var totalPoints: Int = 0,
    var roundPoints: Int = 0
) : Serializable

data class GameState(
    val players: List<Player>,
    var deck: List<Card>,
    var discardPile: List<Card>,
    var wildJoker: Card?,
    var pendingDroppedCard: Card? = null,
    var currentPlayerIndex: Int = 0,
    var roundsPlayed: Int = 0,
    var isGameOver: Boolean = false,
    var statusMessage: String = ""
) : Serializable
