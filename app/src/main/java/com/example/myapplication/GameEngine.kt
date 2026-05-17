package com.example.myapplication

class GameEngine {
    fun createDeck(): List<Card> {
        val deck = mutableListOf<Card>()
        repeat(3) {
            for (suit in Suit.entries) {
                if (suit == Suit.JOKER) continue
                for (rank in Rank.entries) {
                    if (rank == Rank.JOKER) continue
                    deck.add(Card(suit, rank))
                }
            }
            deck.add(Card(Suit.JOKER, Rank.JOKER))
            deck.add(Card(Suit.JOKER, Rank.JOKER))
        }
        deck.shuffle()
        return deck
    }

    fun startRound(players: List<Player>): GameState {
        var deck = createDeck().toMutableList()
        val wildJoker = deck.removeAt(0)
        
        // Pre-mark wild jokers in the remaining deck
        deck = deck.map { if (it.rank == wildJoker.rank) it.copy(isWildJoker = true) else it }.toMutableList()
        
        val updatedPlayers = players.map { player ->
            val hand = mutableListOf<Card>()
            repeat(5) {
                if (deck.isNotEmpty()) {
                    hand.add(deck.removeAt(0))
                }
            }
            player.copy(hand = hand, roundPoints = 0)
        }
        
        val discardPile = mutableListOf<Card>()
        if (deck.isNotEmpty()) {
            discardPile.add(deck.removeAt(0))
        }
        
        return GameState(
            players = updatedPlayers,
            deck = deck,
            discardPile = discardPile,
            wildJoker = wildJoker,
            currentPlayerIndex = 0,
            statusMessage = "Round started. ${updatedPlayers[0].name}'s turn."
        )
    }

    fun processShow(state: GameState, showerIndex: Int): GameState {
        val shower = state.players[showerIndex]
        val showerPoints = shower.hand.sumOf { it.points }
        
        var isWrongShow = false
        for (i in state.players.indices) {
            if (i == showerIndex) continue
            val otherPoints = state.players[i].hand.sumOf { it.points }
            if (showerPoints >= otherPoints) {
                isWrongShow = true
                break
            }
        }
        
        val updatedPlayers = state.players.mapIndexed { index, player ->
            val rPoints = if (isWrongShow) {
                if (index == showerIndex) 40 else 0
            } else {
                if (index == showerIndex) 0 else player.hand.sumOf { it.points }
            }
            player.copy(
                roundPoints = rPoints,
                totalPoints = player.totalPoints + rPoints
            )
        }
        
        val msg = if (isWrongShow) {
            "${shower.name} wrong show! Gets 40 pts."
        } else {
            "${shower.name} valid show!"
        }
        
        return state.copy(
            players = updatedPlayers,
            roundsPlayed = state.roundsPlayed + 1,
            statusMessage = msg
        )
    }
}
