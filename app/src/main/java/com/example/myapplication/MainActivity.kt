package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.myapplication.ui.theme.MyApplicationTheme

// Premium Color Palette
val PokerGreen = Color(0xFF1B5E20)
val DeepTable = Color(0xFF0A2E10)
val CardGold = Color(0xFFFFD700)
val CardSurface = Color(0xFFFFFFFF)
val SuitRed = Color(0xFFD32F2F)
val SuitBlack = Color(0xFF212121)

// Helper for rank labels
fun getRankLabel(rank: Rank): String = when (rank) {
    Rank.ACE -> "A"
    Rank.JACK -> "J"
    Rank.QUEEN -> "Q"
    Rank.KING -> "K"
    Rank.JOKER -> "JK"
    else -> rank.value.toString()
}

// Rank Display Extension for convenient use
fun Rank.toDisplayString(): String = getRankLabel(this)

class MainActivity : ComponentActivity() {
    private val viewModel: GameViewModel by viewModels()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val appMode by viewModel.appMode.collectAsState()
                val state by viewModel.gameState.collectAsState()
                val discoveredGames by viewModel.discoveredGames.collectAsState()
                val connectedPlayers by viewModel.connectedPlayers.collectAsState()
                
                // Calculate window size class for responsive layout
                val windowSizeClass = calculateWindowSizeClass(this@MainActivity)
                val isTablet = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

                Surface(modifier = Modifier.fillMaxSize(), color = DeepTable) {
                    when (appMode) {
                        is AppMode.Initial -> InitialScreen(
                            onSingleDevice = { viewModel.setAppMode(AppMode.SingleDeviceSetup) },
                            onNetwork = { viewModel.setAppMode(AppMode.NetworkLobby) }
                        )
                        is AppMode.SingleDeviceSetup -> SetupScreen(
                            onStartGame = { names, rounds -> viewModel.startGameSingleDevice(names, rounds) },
                            onBack = { viewModel.setAppMode(AppMode.Initial) }
                        )
                        is AppMode.NetworkLobby -> NetworkHostScreen(
                            onHost = { name, rounds -> viewModel.createNetworkGame(name, rounds) },
                            onBack = { viewModel.setAppMode(AppMode.Initial) },
                            onJoinMode = { viewModel.setAppMode(AppMode.NetworkJoin) },
                            connectedPlayers = connectedPlayers,
                            onStartMatch = { viewModel.startNetworkMatch() },
                            isHost = viewModel.isHost,
                            hostIp = viewModel.hostIpAddress
                        )
                        is AppMode.NetworkJoin -> NetworkJoinScreen(
                            discoveredGames = discoveredGames,
                            onJoin = { name, game -> viewModel.joinNetworkGame(name, game) },
                            onBack = { viewModel.setAppMode(AppMode.Initial) },
                            onRefresh = { viewModel.setAppMode(AppMode.NetworkJoin) }
                        )
                        is AppMode.GameInProgress -> {
                            if (state != null) {
                                GameScreen(
                                    state = state!!,
                                    myPlayerName = viewModel.myPlayerName,
                                    isHost = viewModel.isHost,
                                    onDropCards = { cards -> viewModel.onDropCards(cards) },
                                    onDraw = { fromDeck -> viewModel.onDrawCard(fromDeck) },
                                    onShow = { viewModel.onShow() },
                                    onShuffle = { viewModel.shuffleHand() },
                                    onNextRound = { viewModel.nextRound() },
                                    onCloseGame = { viewModel.resetGame() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InitialScreen(onSingleDevice: () -> Unit, onNetwork: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(PokerGreen, DeepTable)))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("SUPER 5 CARDS", fontSize = 42.sp, fontWeight = FontWeight.Black, color = CardGold)
        Spacer(modifier = Modifier.height(60.dp))
        
        Button(
            onClick = onSingleDevice,
            modifier = Modifier.fillMaxWidth().height(70.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CardGold, contentColor = DeepTable)
        ) {
            Text("PLAY ON SINGLE DEVICE", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedButton(
            onClick = onNetwork,
            modifier = Modifier.fillMaxWidth().height(70.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, CardGold),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = CardGold)
        ) {
            Text("PLAY ON LOCAL NETWORK", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun NetworkHostScreen(
    onHost: (String, Int) -> Unit,
    onBack: () -> Unit,
    onJoinMode: () -> Unit,
    connectedPlayers: List<String>,
    onStartMatch: () -> Unit,
    isHost: Boolean,
    hostIp: String
) {
    var name by remember { mutableStateOf("Host") }
    var rounds by remember { mutableIntStateOf(10) }
    var gameCreated by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            TextButton(onClick = onBack) { Text("< BACK", color = CardGold) }
        }
        
        Text("HOST A GAME", fontSize = 32.sp, fontWeight = FontWeight.Black, color = CardGold)
        Spacer(modifier = Modifier.height(32.dp))

        if (!gameCreated) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("YOUR NAME", color = Color.White.copy(0.6f)) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("ROUNDS: $rounds", color = Color.White)
            Slider(
                value = rounds.toFloat(),
                onValueChange = { rounds = it.toInt() },
                valueRange = 1f..20f,
                colors = SliderDefaults.colors(thumbColor = CardGold, activeTrackColor = CardGold)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { onHost(name, rounds); gameCreated = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CardGold)
            ) {
                Text("CREATE LOBBY")
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onJoinMode) { Text("OR JOIN EXISTING GAME", color = CardGold) }
        } else {
            Text("LOBBY ACTIVE", color = Color.Green, fontWeight = FontWeight.Bold)
            Text("GAME ID: $hostIp", color = CardGold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("TELL FRIENDS TO JOIN VIA THIS ID", color = Color.White.copy(0.7f))
            Spacer(modifier = Modifier.height(24.dp))
            Text("CONNECTED PLAYERS:", color = CardGold, fontWeight = FontWeight.Bold)
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(connectedPlayers) { p ->
                    Text(p, color = Color.White, modifier = Modifier.padding(8.dp), fontSize = 18.sp)
                }
            }
            if (isHost && connectedPlayers.size >= 2) {
                Button(
                    onClick = onStartMatch,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CardGold)
                ) {
                    Text("START MATCH (${connectedPlayers.size} PLAYERS)")
                }
            } else {
                Text("WAITING FOR AT LEAST 2 PLAYERS...", color = Color.White.copy(0.5f))
            }
        }
    }
}

@Composable
fun NetworkJoinScreen(
    discoveredGames: List<DiscoveredGame>,
    onJoin: (String, DiscoveredGame) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    var name by remember { mutableStateOf("Player") }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("< BACK", color = CardGold) }
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh", tint = CardGold) }
        }
        
        Text("JOIN A GAME", fontSize = 32.sp, fontWeight = FontWeight.Black, color = CardGold)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("YOUR NAME", color = Color.White.copy(0.6f)) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        Text("AVAILABLE GAMES ON NETWORK:", color = CardGold, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        if (discoveredGames.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CardGold.copy(0.5f))
                    Text("Searching for hosts...", color = Color.White.copy(0.5f), modifier = Modifier.padding(top = 16.dp))
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(discoveredGames) { game ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable { onJoin(name, game) },
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.1f)),
                        border = BorderStroke(1.dp, CardGold.copy(0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(game.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("ID: ${game.hostIp}", color = Color.White.copy(0.6f), fontSize = 12.sp)
                            }
                            Text("TAP TO JOIN", color = CardGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SetupScreen(onStartGame: (List<String>, Int) -> Unit, onBack: () -> Unit) {
    var playerCount by remember { mutableIntStateOf(2) }
    var roundsToPlay by remember { mutableIntStateOf(10) }
    val names = remember { mutableStateListOf("Player 1", "Player 2", "Player 3", "Player 4", "Player 5", "Player 6", "Player 7", "Player 8") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(PokerGreen, DeepTable)))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            TextButton(onClick = onBack) { Text("< BACK", color = CardGold) }
        }
        Text("SINGLE DEVICE", fontSize = 32.sp, fontWeight = FontWeight.Black, color = CardGold)
        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, CardGold.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("PLAYERS: $playerCount", color = Color.White, fontWeight = FontWeight.Bold)
                Slider(
                    value = playerCount.toFloat(),
                    onValueChange = { playerCount = it.toInt() },
                    valueRange = 2f..8f,
                    steps = 5,
                    colors = SliderDefaults.colors(thumbColor = CardGold, activeTrackColor = CardGold)
                )

                Text("ROUNDS: $roundsToPlay", color = Color.White, fontWeight = FontWeight.Bold)
                Slider(
                    value = roundsToPlay.toFloat(),
                    onValueChange = { roundsToPlay = it.toInt() },
                    valueRange = 1f..20f,
                    steps = 18,
                    colors = SliderDefaults.colors(thumbColor = CardGold, activeTrackColor = CardGold)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        LazyRow(modifier = Modifier.height(80.dp)) {
            items(playerCount) { index ->
                OutlinedTextField(
                    value = names[index],
                    onValueChange = { names[index] = it },
                    label = { Text("P${index + 1}", color = Color.White.copy(alpha = 0.7f)) },
                    modifier = Modifier.padding(4.dp).width(110.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { onStartGame(names.take(playerCount), roundsToPlay) },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CardGold, contentColor = DeepTable)
        ) {
            Text("START MATCH", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
fun GameScreen(
    state: GameState,
    myPlayerName: String,
    isHost: Boolean,
    onDropCards: (List<Card>) -> Boolean,
    onDraw: (Boolean) -> Unit,
    onShow: () -> Unit,
    onShuffle: () -> Unit,
    onNextRound: () -> Unit,
    onCloseGame: () -> Unit
) {
    val currentPlayer = state.players.getOrNull(state.currentPlayerIndex) ?: return
    val myPlayer = state.players.find { it.name == myPlayerName } ?: currentPlayer
    // Detect local pass-and-play: all players use default "Player" names and no network identity set.
    val isLocalPassAndPlay = myPlayerName == "Player" && state.players.all { it.name.startsWith("Player") }
    val isMyTurn = if (isLocalPassAndPlay) {
        // On a shared device, the UI should allow interacting with the current player's hand.
        true
    } else {
        // Networked mode: only the player whose name matches `myPlayerName` may act.
        currentPlayer.name == myPlayerName
    }

    val selectedCards = remember { mutableStateListOf<Card>() }
    val mustDraw = state.pendingDroppedCard != null
    // Is the current player the one required to draw the pending card?
    val mustDrawForCurrentPlayer = state.pendingDroppedCard != null && state.pendingPlayerIndex == state.currentPlayerIndex

    LaunchedEffect(state.currentPlayerIndex) {
        selectedCards.clear()
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Brush.verticalGradient(listOf(PokerGreen, DeepTable)))) {
        
        IconButton(
            onClick = onCloseGame,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Close, null, tint = CardGold, modifier = Modifier.size(32.dp))
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("ROUND", fontSize = 12.sp, color = CardGold.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                    Text("${state.roundsPlayed + 1}", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color.White)
                }
                
                Surface(
                    color = Color.Black.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Text(
                        text = if (isMyTurn) state.statusMessage else "Waiting for ${currentPlayer.name}...",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = if (mustDrawForCurrentPlayer && isMyTurn) Color.Red else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Deck Area (Stacked on top of peeking Wild Joker)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.width(140.dp)) {
                            state.wildJoker?.let {
                                CardView(
                                    card = it, 
                                    isMini = false, 
                                    modifier = Modifier
                                        .zIndex(0f)
                                        .offset(x = (-60).dp, y = 15.dp) // Adjusted offset to look half hidden but readable on the left
                                        .rotate(-15f)
                                )
                            }
                            CardView(
                                card = null,
                                label = "DECK",
                                isSelected = mustDrawForCurrentPlayer && isMyTurn,
                                modifier = Modifier.zIndex(1f),
                                onClick = { if (mustDrawForCurrentPlayer && isMyTurn) onDraw(true) }
                            )
                        }
                        Text("DECK", color = CardGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                    }

                    Spacer(modifier = Modifier.width(80.dp))

                    // Discard Pile
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CardView(
                            card = state.discardPile.lastOrNull(),
                            isSelected = mustDrawForCurrentPlayer && isMyTurn,
                            onClick = { if (mustDrawForCurrentPlayer && isMyTurn) onDraw(false) }
                        )
                        Text("DISCARD", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Floating card in air (Pending draw)
                state.pendingDroppedCard?.let { floatingCard ->
                    val infiniteTransition = rememberInfiniteTransition()
                    val floatOffset by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = -30f,
                        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse)
                    )
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CardView(
                                card = floatingCard,
                                modifier = Modifier
                                    .offset(y = floatOffset.dp)
                                    .shadow(24.dp, RoundedCornerShape(12.dp))
                                    .graphicsLayer { rotationZ = 4f }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("PICK A CARD", color = CardGold, fontWeight = FontWeight.Black, fontSize = 10.sp)
                        }
                    }
                }
            }

            LazyRow(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 24.dp)) {
                items(state.players) { player ->
                    val isCurrent = state.players.indexOf(player) == state.currentPlayerIndex
                    Column(
                        modifier = Modifier
                            .padding(4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isCurrent) CardGold else Color.White.copy(alpha = 0.05f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(player.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isCurrent) DeepTable else Color.White)
                        Text("${player.totalPoints} PTS", fontSize = 11.sp, color = if (isCurrent) DeepTable.copy(alpha = 0.7f) else CardGold)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.4f),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            ) {
                Column(modifier = Modifier.padding(bottom = 24.dp, start = 12.dp, end = 12.dp, top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("YOUR HAND", fontSize = 12.sp, color = CardGold, fontWeight = FontWeight.Black)
                        TextButton(onClick = onShuffle, enabled = isMyTurn && !mustDraw) {
                            Text("SHUFFLE", color = CardGold, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    // Box to act as container for centered overlapping cards
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(130.dp), contentAlignment = Alignment.Center) {
                        val cardCount = myPlayer.hand.size
                        val cardWidth = 72.dp
                        val containerWidth = maxWidth
                        
                        // Overlap calculation
                        val overlapSpacing = if (cardCount > 1) {
                            val totalWidthNeeded = cardWidth * cardCount
                            if (totalWidthNeeded > containerWidth) {
                                (totalWidthNeeded - containerWidth) / (cardCount - 1).toFloat()
                            } else 0.dp
                        } else 0.dp

                        val step = cardWidth - overlapSpacing

                        myPlayer.hand.forEachIndexed { index, card ->
                            // Centering cards: (index - centerIndex) * step
                            val xOffset = (index - (cardCount - 1) / 2f).dp * step.value
                            val animatedX by animateDpAsState(targetValue = xOffset, animationSpec = spring(stiffness = Spring.StiffnessLow))
                            
                            CardView(
                                card = card,
                                isSelected = selectedCards.contains(card),
                                modifier = Modifier
                                    .offset(x = animatedX)
                                    .zIndex(index.toFloat()),
                                onClick = { if (!mustDraw && isMyTurn) {
                                    if (selectedCards.contains(card)) {
                                        selectedCards.remove(card)
                                    } else {
                                        // SELECTION LOGIC: Only allow multiple cards of SAME RANK
                                        if (selectedCards.isEmpty()) {
                                            selectedCards.add(card)
                                        } else {
                                            val currentRank = selectedCards.first().rank
                                            if (card.rank == currentRank) {
                                                selectedCards.add(card)
                                            } else {
                                                // Reset selection to the newly clicked card if rank differs
                                                selectedCards.clear()
                                                selectedCards.add(card)
                                            }
                                        }
                                    }
                                } }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (!state.isGameOver) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    if (selectedCards.isNotEmpty()) {
                                        onDropCards(selectedCards.toList())
                                        selectedCards.clear()
                                    }
                                },
                                enabled = selectedCards.isNotEmpty() && !mustDraw && isMyTurn,
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CardGold, contentColor = DeepTable)
                            ) {
                                Text("DROP (${selectedCards.size})", fontWeight = FontWeight.ExtraBold)
                            }
                            
                            Button(
                                onClick = onShow,
                                enabled = !mustDraw && isMyTurn,
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = DeepTable)
                            ) {
                                Text("SHOW", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    } else {
                        Button(onClick = onCloseGame, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                            Text("NEW MATCH")
                        }
                    }

                    if (state.statusMessage.contains("show", ignoreCase = true) && !state.isGameOver) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onNextRound, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)), enabled = isHost) {
                            Text("CONTINUE TO NEXT ROUND")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CardView(
    card: Card?,
    label: String? = null,
    isSelected: Boolean = false,
    isMini: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val cardWidth = if (isMini) 60.dp else 85.dp
    val cardHeight = if (isMini) 85.dp else 125.dp
    
    val scale by animateFloatAsState(if (isSelected) 1.15f else 1f)
    
    Box(
        modifier = modifier
            .padding(2.dp)
            .requiredSize(width = cardWidth, height = cardHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(if (isSelected) 16.dp else 4.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(if (card == null) DeepTable else CardSurface)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) CardGold else Color.Gray.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(enabled = onClick != null) { onClick?.invoke() }
    ) {
        if (card == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val step = 15.dp.toPx()
                    for (i in -10..20) {
                        drawLine(CardGold.copy(alpha = 0.1f), Offset(i * step, 0f), Offset((i + 10) * step, this.size.height), 2f)
                        drawLine(CardGold.copy(0.1f), Offset((i + 10) * step, 0f), Offset(i * step, this.size.height), 2f)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Star, null, tint = CardGold.copy(0.3f), modifier = Modifier.size(32.dp))
                    if (label != null) Text(label, color = CardGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            val color = if (card.suit == Suit.HEARTS || card.suit == Suit.DIAMONDS) SuitRed else SuitBlack
            
            Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                Text(
                    text = getRankLabel(card.rank),
                    color = color,
                    fontWeight = FontWeight.Black,
                    fontSize = if (isMini) 14.sp else 22.sp,
                    modifier = Modifier.align(Alignment.TopStart)
                )

                Box(modifier = Modifier.align(Alignment.Center)) {
                    SuitIcon(card.suit, color, if (isMini) 28.dp else 44.dp)
                }

                Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                    SuitIcon(card.suit, color, if (isMini) 14.dp else 20.dp)
                }

                if (card.rank == Rank.JOKER || card.isWildJoker) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = CardGold,
                        modifier = Modifier
                            .size(if (isMini) 14.dp else 24.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SuitIcon(suit: Suit, color: Color, iconSize: Dp) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        when (suit) {
            Suit.HEARTS -> {
                val path = Path().apply {
                    moveTo(w / 2f, h * 0.3f)
                    cubicTo(w * 0.2f, 0f, 0f, h * 0.4f, w / 2f, h)
                    cubicTo(w, h * 0.4f, w * 0.8f, 0f, w / 2f, h * 0.3f)
                }
                drawPath(path, color)
            }
            Suit.DIAMONDS -> {
                val path = Path().apply {
                    moveTo(w / 2f, 0f)
                    lineTo(w, h / 2f)
                    lineTo(w / 2f, h)
                    lineTo(0f, h / 2f)
                    close()
                }
                drawPath(path, color)
            }
            Suit.CLUBS -> {
                drawCircle(color, radius = w * 0.22f, center = Offset(w / 2f, h * 0.3f))
                drawCircle(color, radius = w * 0.22f, center = Offset(w * 0.3f, h * 0.65f))
                drawCircle(color, radius = w * 0.22f, center = Offset(w * 0.7f, h * 0.65f))
                drawRect(color, size = androidx.compose.ui.geometry.Size(w * 0.1f, h * 0.35f), topLeft = Offset(w * 0.45f, h * 0.65f))
            }
            Suit.SPADES -> {
                val path = Path().apply {
                    moveTo(w / 2f, 0f)
                    cubicTo(w * 1.1f, h * 0.7f, w * 0.8f, h * 0.8f, w / 2f, h * 0.8f)
                    cubicTo(w * 0.2f, h * 0.8f, -w * 0.1f, h * 0.7f, w / 2f, 0f)
                }
                drawPath(path, color)
                drawRect(color, size = androidx.compose.ui.geometry.Size(w * 0.1f, h * 0.3f), topLeft = Offset(w * 0.45f, h * 0.7f))
            }
            Suit.JOKER -> {
                drawCircle(color, radius = w * 0.45f)
                drawCircle(Color.White, radius = w * 0.15f, center = this.center)
            }
        }
    }
}
