package com.example.myapplication

import android.app.Application
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

sealed class AppMode {
    object Initial : AppMode()
    object SingleDeviceSetup : AppMode()
    object NetworkLobby : AppMode()
    object NetworkJoin : AppMode()
    object GameInProgress : AppMode()
}

data class DiscoveredGame(
    val name: String,
    val hostIp: String,
    val port: Int
) : java.io.Serializable

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = GameEngine()
    private val nsdManager = application.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = application.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null
    
    private val SERVICE_TYPE = "_royalcardgame._tcp."

    private val _gameState = MutableStateFlow<GameState?>(null)
    val gameState: StateFlow<GameState?> = _gameState.asStateFlow()

    private val _appMode = MutableStateFlow<AppMode>(AppMode.Initial)
    val appMode: StateFlow<AppMode> = _appMode.asStateFlow()

    private val _connectedPlayers = MutableStateFlow<List<String>>(emptyList())
    val connectedPlayers: StateFlow<List<String>> = _connectedPlayers.asStateFlow()

    private val _discoveredGames = MutableStateFlow<List<DiscoveredGame>>(emptyList())
    val discoveredGames: StateFlow<List<DiscoveredGame>> = _discoveredGames.asStateFlow()

    var totalRoundsToPlay: Int = 10
    var isHost: Boolean = false
    var myPlayerName: String = "Player"
    var hostIpAddress: String = ""

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var objectOut: ObjectOutputStream? = null
    private val clientStreams = mutableListOf<ObjectOutputStream>()

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    init {
        try {
            multicastLock = wifiManager.createMulticastLock("RoyalCardGameLock")
            multicastLock?.setReferenceCounted(true)
            multicastLock?.acquire()
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun setAppMode(mode: AppMode) {
        _appMode.value = mode
        if (mode is AppMode.NetworkJoin) {
            startDiscovery()
        } else {
            stopDiscovery()
        }
    }

    fun startGameSingleDevice(playerNames: List<String>, rounds: Int) {
        totalRoundsToPlay = rounds
        isHost = true
        val players = playerNames.map { Player(id = UUID.randomUUID().toString(), name = it) }
        _gameState.value = engine.startRound(players).copy(roundsPlayed = 0)
        _appMode.value = AppMode.GameInProgress
    }

    fun createNetworkGame(name: String, rounds: Int) {
        myPlayerName = name
        totalRoundsToPlay = rounds
        isHost = true
        hostIpAddress = getLocalIpAddress()
        _connectedPlayers.value = listOf(myPlayerName)
        _appMode.value = AppMode.NetworkLobby
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(0)
                val port = serverSocket!!.localPort
                registerService(port, name)
                
                while (isHost) {
                    val socket = serverSocket?.accept() ?: break
                    handleNewConnection(socket)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr.hostAddress.contains(".")) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "Unknown IP"
    }

    private fun registerService(port: Int, hostName: String) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "RoyalCardGame_$hostName"
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) {}
        }
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    private fun startDiscovery() {
        _discoveredGames.value = emptyList()
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.contains(SERVICE_TYPE)) {
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(si: NsdServiceInfo, error: Int) {}
                        override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                            val game = DiscoveredGame(
                                name = resolvedInfo.serviceName.removePrefix("RoyalCardGame_"),
                                hostIp = resolvedInfo.host.hostAddress ?: "",
                                port = resolvedInfo.port
                            )
                            viewModelScope.launch(Dispatchers.Main) {
                                if (!_discoveredGames.value.any { it.hostIp == game.hostIp }) {
                                    _discoveredGames.value = _discoveredGames.value + game
                                }
                            }
                        }
                    })
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                viewModelScope.launch(Dispatchers.Main) {
                    _discoveredGames.value = _discoveredGames.value.filter { !serviceInfo.serviceName.contains(it.name) }
                }
            }
            override fun onDiscoveryStopped(regType: String) {}
            override fun onStartDiscoveryFailed(type: String, error: Int) { try { nsdManager.stopServiceDiscovery(this) } catch(e:Exception){} }
            override fun onStopDiscoveryFailed(type: String, error: Int) { try { nsdManager.stopServiceDiscovery(this) } catch(e:Exception){} }
        }
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    private fun stopDiscovery() {
        try { discoveryListener?.let { nsdManager.stopServiceDiscovery(it) } } catch (e: Exception) {}
        discoveryListener = null
    }

    fun joinNetworkGame(name: String, game: DiscoveredGame) {
        myPlayerName = name
        isHost = false
        stopDiscovery()
        _appMode.value = AppMode.GameInProgress
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                clientSocket = Socket(game.hostIp, game.port)
                objectOut = ObjectOutputStream(clientSocket?.getOutputStream())
                val inStream = ObjectInputStream(clientSocket?.getInputStream())
                objectOut?.writeObject(myPlayerName)
                objectOut?.flush()

                while (true) {
                    val received = inStream.readObject()
                    withContext(Dispatchers.Main) {
                        if (received is GameState) {
                            _gameState.value = received
                            _appMode.value = AppMode.GameInProgress
                        } else if (received is List<*>) {
                            @Suppress("UNCHECKED_CAST")
                            _connectedPlayers.value = received as List<String>
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { _appMode.value = AppMode.Initial }
            }
        }
    }

    private fun handleNewConnection(socket: Socket) {
        viewModelScope.launch(Dispatchers.IO) {
            val out = ObjectOutputStream(socket.getOutputStream())
            val inStream = ObjectInputStream(socket.getInputStream())
            clientStreams.add(out)
            try {
                val joinName = inStream.readObject() as String
                withContext(Dispatchers.Main) { _connectedPlayers.value = _connectedPlayers.value + joinName }
                broadcastLobbyUpdate()
                while (true) {
                    val action = inStream.readObject()
                    withContext(Dispatchers.Main) { handleRemoteAction(action) }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun broadcastLobbyUpdate() {
        viewModelScope.launch(Dispatchers.IO) {
            clientStreams.forEach { out ->
                try { out.writeObject(_connectedPlayers.value); out.flush() } catch (e: Exception) {}
            }
        }
    }

    fun startNetworkMatch() {
        if (!isHost) return
        val players = _connectedPlayers.value.map { Player(id = UUID.randomUUID().toString(), name = it) }
        val initialState = engine.startRound(players).copy(roundsPlayed = 0)
        _gameState.value = initialState
        _appMode.value = AppMode.GameInProgress
        broadcastState(initialState)
    }

    private fun broadcastState(state: GameState) {
        viewModelScope.launch(Dispatchers.IO) {
            clientStreams.forEach { out ->
                try { out.writeObject(state); out.flush() } catch (e: Exception) {}
            }
        }
    }

    private fun handleRemoteAction(action: Any) {
        when (action) {
            is NetworkAction.Drop -> onDropCards(action.cards)
            is NetworkAction.Draw -> onDrawCard(action.fromDeck)
            is NetworkAction.Show -> onShow()
            is NetworkAction.NextRound -> nextRound()
            is NetworkAction.Shuffle -> shuffleHand()
        }
    }

    fun onDropCards(cards: List<Card>): Boolean {
        val currentState = _gameState.value ?: return false
        val playerIndex = currentState.currentPlayerIndex
        val player = currentState.players[playerIndex]
        
        // Safety check: ensure cards are actually in player's hand
        if (!cards.all { c -> player.hand.any { it.id == c.id } }) return false
        
        val openCard = currentState.discardPile.lastOrNull() ?: return false
        
        // Matching rule: if ANY dropped card rank matches open card rank, turn ends immediately.
        // Also Joker/Wild Joker always ends turn.
        val matches = cards.any { it.rank == openCard.rank || it.isWildJoker || it.rank == Rank.JOKER }
        
        val newHand = player.hand.filter { ph -> !cards.any { it.id == ph.id } }
        val updatedPlayer = player.copy(hand = newHand)
        val updatedPlayers = currentState.players.toMutableList()
        updatedPlayers[playerIndex] = updatedPlayer

        val nextState = if (matches) {
            // Turn ends immediately
            val nextDiscardPile = currentState.discardPile + cards
            val nextIdx = (playerIndex + 1) % updatedPlayers.size
            currentState.copy(
                players = updatedPlayers, 
                discardPile = nextDiscardPile, 
                currentPlayerIndex = nextIdx, 
                statusMessage = "${updatedPlayers[nextIdx].name}'s turn.",
                pendingDroppedCard = null
            )
        } else {
            // No match found in the dropped set, must draw
            // We only support one "floating" card visual for now, so we pick the last one dropped
            currentState.copy(
                players = updatedPlayers, 
                statusMessage = "${updatedPlayer.name} must draw.",
                pendingDroppedCard = cards.last() 
            )
        }
        
        _gameState.value = nextState
        if (isHost) broadcastState(nextState) else sendAction(NetworkAction.Drop(cards))
        return matches
    }

    fun onDropCard(card: Card): Boolean = onDropCards(listOf(card))

    fun onDrawCard(fromDeck: Boolean) {
        val currentState = _gameState.value ?: return
        val playerIndex = currentState.currentPlayerIndex
        val player = currentState.players[playerIndex]
        val newHand = player.hand.toMutableList()
        val newDeck = currentState.deck.toMutableList()
        val newDiscardPile = currentState.discardPile.toMutableList()
        
        // Finalize the dropped card into discard pile
        currentState.pendingDroppedCard?.let {
            newDiscardPile.add(it)
        }

        if (fromDeck) {
            if (newDeck.isNotEmpty()) newHand.add(newDeck.removeAt(0))
        } else {
            if (newDiscardPile.size > 1) {
                newHand.add(newDiscardPile.removeAt(newDiscardPile.size - 2))
            }
        }
        
        val updatedPlayers = currentState.players.toMutableList()
        updatedPlayers[playerIndex] = player.copy(hand = newHand)
        val nextIdx = (playerIndex + 1) % updatedPlayers.size
        
        val nextState = currentState.copy(
            players = updatedPlayers, 
            deck = newDeck, 
            discardPile = newDiscardPile, 
            currentPlayerIndex = nextIdx, 
            statusMessage = "${updatedPlayers[nextIdx].name}'s turn.",
            pendingDroppedCard = null
        )
        _gameState.value = nextState
        if (isHost) broadcastState(nextState) else sendAction(NetworkAction.Draw(fromDeck))
    }

    fun shuffleHand() {
        val currentState = _gameState.value ?: return
        // Since GameInProgress can be networked, we need to know whose hand we're shuffling.
        // On a local device (Pass & Play), it's always the current player.
        // In Network mode, a client shuffles their own hand and notifies host (if we sent full hand).
        // However, host holds the state.
        
        // Simplest: Shuffling is local-only visual unless we update the actual order in state.
        val playerIndex = currentState.currentPlayerIndex
        val players = currentState.players.toMutableList()
        val player = players[playerIndex]
        val shuffledHand = player.hand.shuffled()
        players[playerIndex] = player.copy(hand = shuffledHand)
        
        val nextState = currentState.copy(players = players)
        _gameState.value = nextState
        if (isHost) broadcastState(nextState) else sendAction(NetworkAction.Shuffle)
    }

    fun onShow() {
        val currentState = _gameState.value ?: return
        var newState = engine.processShow(currentState, currentState.currentPlayerIndex)
        if (newState.roundsPlayed >= totalRoundsToPlay) {
            val winner = newState.players.minByOrNull { it.totalPoints }
            newState = newState.copy(isGameOver = true, statusMessage = "Game Over! Winner: ${winner?.name ?: "Unknown"}")
        }
        _gameState.value = newState
        if (isHost) broadcastState(newState) else sendAction(NetworkAction.Show)
    }

    private fun sendAction(action: Any) {
        viewModelScope.launch(Dispatchers.IO) { try { objectOut?.writeObject(action); objectOut?.flush() } catch (e: Exception) {} }
    }
    
    fun nextRound() {
        val currentState = _gameState.value ?: return
        if (!isHost) { sendAction(NetworkAction.NextRound); return }
        val nextState = engine.startRound(currentState.players).copy(roundsPlayed = currentState.roundsPlayed)
        _gameState.value = nextState
        broadcastState(nextState)
    }

    fun resetGame() {
        isHost = false
        viewModelScope.launch(Dispatchers.IO) {
            try { registrationListener?.let { nsdManager.unregisterService(it) } } catch (e: Exception) {}
            stopDiscovery()
            try { serverSocket?.close() } catch (e: Exception) {}
            try { clientSocket?.close() } catch (e: Exception) {}
            clientStreams.clear()
        }
        _gameState.value = null
        _appMode.value = AppMode.Initial
    }

    override fun onCleared() {
        super.onCleared()
        multicastLock?.release()
        resetGame()
    }
}

sealed class NetworkAction : java.io.Serializable {
    data class Drop(val cards: List<Card>) : NetworkAction()
    data class Draw(val fromDeck: Boolean) : NetworkAction()
    object Show : NetworkAction()
    object NextRound : NetworkAction()
    object Shuffle : NetworkAction()
}
