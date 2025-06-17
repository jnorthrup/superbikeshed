package gk.kademlia.bitswap

import gk.kademlia.security.DummyCryptoService // For placeholder hashing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import gk.kademlia.include.SubnetRoute // For BitswapNetworkSender
import gk.kademlia.net.NetMask // For BitswapNetworkSender

// Interface for sending Bitswap messages to the network
interface BitswapNetworkSender<TNum : Comparable<TNum>, Sz : NetMask<TNum>> {
    suspend fun sendMessage(targetRoute: SubnetRoute<TNum>, message: BitswapMessage)
    // We might also need a way to get the local node's PeerID if not known by engine (localPeerId is now in Engine)
}

class BitswapEngine<TNum : Comparable<TNum>, Sz : NetMask<TNum>>( // Made generic
    private val scope: CoroutineScope, // For launching ongoing tasks & handling messages
    private val blockStore: BlockStore,
    private val wantManager: WantManager,
    private val networkSender: BitswapNetworkSender<TNum, Sz>, // Use generic sender
    private val localPeerId: PeerID // The PeerID of the local node (string form for map keys)
) {
    private val peerLedgersMutex = Mutex()
    // PeerID (String) maps to Pair of Ledger and its SubnetRoute
    private val peerLedgers = mutableMapOf<PeerID, Pair<PeerLedger, SubnetRoute<TNum>>>()
    private var wantBroadcastJob: Job? = null

    // Placeholder for CryptoService if needed for CID hashing internally
    // This should ideally be passed in or accessed via a proper DI mechanism.
    // Using a specific instance here for now.
    private val internalCryptoService = DummyCryptoService()


    fun start() {
        stop() // Ensure any previous job is stopped
        wantBroadcastJob = wantManager.wantlistChangesFlow
            .onEach { entry ->
                // When our want list changes, broadcast this entry.
                println("BitswapEngine: Wantlist change detected for CID ${entry.block}, cancel: ${entry.cancel}. Broadcasting...")
                val message = BitswapMessage(wantlist = Wantlist(entries = listOf(entry), full = false))
                broadcastMessage(message)
            }
            .launchIn(scope)
        println("BitswapEngine started. Listening to wantlist changes.")
    }

    fun stop() {
        wantBroadcastJob?.cancel()
        wantBroadcastJob = null
        println("BitswapEngine stopped.")
    }

    // getOrCreateLedger now also accepts and stores/updates the SubnetRoute
    private suspend fun getOrCreateLedger(peerIdStr: PeerID, sourceRoute: SubnetRoute<TNum>? = null): PeerLedger {
        return peerLedgersMutex.withLock {
            val existingEntry = peerLedgers[peerIdStr]
            if (existingEntry != null) {
                // If sourceRoute is new and different, update it (e.g. peer changed address)
                if (sourceRoute != null && existingEntry.second != sourceRoute) {
                    peerLedgers[peerIdStr] = Pair(existingEntry.first, sourceRoute)
                    println("BitswapEngine: Updated SubnetRoute for peer $peerIdStr to $sourceRoute")
                }
                existingEntry.first // Return existing ledger
            } else {
                val newLedger = PeerLedger(peerIdStr)
                // sourceRoute should ideally not be null for a new peer, as we need its route info.
                // If it is null, we can't send messages back until we get a route.
                val routeToStore = sourceRoute ?: run {
                    println("BitswapEngine: WARNING - Creating ledger for peer $peerIdStr without initial SubnetRoute.")
                    // Create a placeholder or handle this case based on stricter requirements later
                    // For now, this means we can't send to this peer until its route is learned.
                    // This scenario should be rare if incoming messages always provide a source route.
                    null // Cannot store a null route, but the Pair would need to allow it or handle it.
                        // Let's assume for now that a route is required to create an entry that can be sent to.
                        // If sourceRoute is null for a new peer, we might not add to peerLedgers yet,
                        // or the Pair value would be PeerLedger and a nullable SubnetRoute.
                        // For now, let's assume sourceRoute is usually present for new peers.
                }
                if (routeToStore != null) {
                    peerLedgers[peerIdStr] = Pair(newLedger, routeToStore)
                    println("BitswapEngine: Creating new ledger and storing route for peer $peerIdStr: $routeToStore")
                } else {
                    // If no route, we can't effectively communicate back yet.
                    // Consider if we should store ledger without route or handle differently.
                    // For now, if no route, we just create ledger but can't use it for sending.
                    // The map should probably be `PeerID` to `Pair<PeerLedger, SubnetRoute<TNum>?>`
                    // Or, only add to map if route is known.
                    // Let's adjust peerLedgers to store SubnetRoute<TNum>? (nullable)
                    // No, the prompt implies we store it: `Pair<PeerLedger, SubnetRoute<TNum>>>`
                    // This means if sourceRoute is null for a new peer, we have an issue.
                    // For `handleIncomingMessage`, `sourceRoute` will be non-null.
                    // For outgoing messages, we'd need to have learned the route previously.
                    println("BitswapEngine: Ledger created for $peerIdStr, but no route provided to store.")
                }
                newLedger
            }
        }
    }

    // Signature changed to accept SubnetRoute from Kademlia layer
    suspend fun handleIncomingMessage(sourceRoute: SubnetRoute<TNum>, message: BitswapMessage) {
        val sourcePeerIdString = sourceRoute.nuid.id?.toString() ?: return Unit.also {
            println("BitswapEngine: Received message from route with null NUID ID. Discarding. Route: $sourceRoute")
        }
        println("BitswapEngine: Received message from $sourcePeerIdString (Route: $sourceRoute): Wants: ${message.wantlist?.entries?.size ?: 0}, Blocks: ${message.payload?.size ?: 0}, Presences: ${message.blockPresences?.size ?: 0}")

        val ledger = getOrCreateLedger(sourcePeerIdString, sourceRoute)
        ledger.updateLastMessageTime()

        // Process received blocks first
        message.payload?.forEach { blockContainer ->
            processReceivedBlock(sourcePeerIdString, ledger, blockContainer) // Pass PeerID string
        }

        // Process wantlist from peer
        message.wantlist?.let { wantlist ->
            processWantlistFromPeer(sourcePeerIdString, ledger, wantlist) // Pass PeerID string
        }

        // Process block presences (HAVE/DONT_HAVE)
        message.blockPresences?.forEach { presence ->
            processBlockPresenceFromPeer(sourcePeerIdString, ledger, presence) // Pass PeerID string
        }

        // TODO: Handle pendingBytes if needed for flow control
    }

    private suspend fun processReceivedBlock(sourcePeerId: PeerID, ledger: PeerLedger, blockContainer: BlockContainer) {
        // This is a placeholder for actual CID derivation from blockContainer.prefix + blockContainer.data
        // For now, we hash the data part to get a CID. This assumes data is the full block content.
        val blockData = blockContainer.data
        val receivedCID = CID(internalCryptoService.hash(blockData)) // Simplified CID derivation

        println("BitswapEngine: Processing received block (derived CID ${receivedCID}) of size ${blockData.size} from $sourcePeerId")

        val wasWanted = wantManager.getWantEntry(receivedCID) != null

        // Store the block regardless of whether it was actively wanted (could be opportunistic send)
        val success = blockStore.put(receivedCID, blockData)
        if (success) {
            ledger.blockReceivedFromPeer(blockData.size)
            if (wasWanted) {
                println("BitswapEngine: Stored block $receivedCID from $sourcePeerId (was in active wantlist).")
                wantManager.blockReceived(receivedCID) // Notifies want manager, removes from active wants
                // TODO: Consider sending CANCEL messages to other peers for this CID.
                // This would involve getting the WantEntry *before* calling wantManager.blockReceived,
                // then iterating over its sentToPeers list.
            } else {
                println("BitswapEngine: Stored block $receivedCID from $sourcePeerId (not in active wantlist or already received).")
            }
        } else {
            println("BitswapEngine: Failed to store block $receivedCID from $sourcePeerId.")
        }
    }

    private suspend fun processWantlistFromPeer(sourcePeerId: PeerID, ledger: PeerLedger, wantlist: Wantlist) {
        val blocksToSend = mutableListOf<BlockContainer>()
        val presencesToSend = mutableListOf<BlockPresence>()

        val cidsToRemove = wantlist.entries?.filter { it.cancel }?.map { it.block } ?: emptyList()
        val cidsToAddOrUpdate = wantlist.entries?.filterNot { it.cancel } ?: emptyList()

        if (cidsToRemove.isNotEmpty()) ledger.removeWants(cidsToRemove)
        // AddWants will filter out duplicates or already existing wants from the peer's perspective
        if (cidsToAddOrUpdate.isNotEmpty()) ledger.addWants(cidsToAddOrUpdate.map { it.block })


        for (entry in cidsToAddOrUpdate) {
            when (entry.wantType) {
                Wantlist.WantType.Block -> {
                    blockStore.get(entry.block)?.let { data ->
                        // Prefix needs proper handling based on CID actual structure.
                        // For dummy CID (just hash), prefix might be empty.
                        blocksToSend.add(BlockContainer(prefix = byteArrayOf(), data = data))
                        ledger.blockSentToPeer(data.size)
                        // We don't remove from peer's want list here; peer should send CANCEL when it receives the block.
                    } ?: run {
                        if (entry.sendDontHave) {
                            presencesToSend.add(BlockPresence(entry.block, BlockPresenceType.DontHave))
                        }
                    }
                }
                Wantlist.WantType.Have -> {
                    if (blockStore.has(entry.block)) {
                        presencesToSend.add(BlockPresence(entry.block, BlockPresenceType.Have))
                    } else {
                        if (entry.sendDontHave) {
                            presencesToSend.add(BlockPresence(entry.block, BlockPresenceType.DontHave))
                        }
                    }
                }
            }
        }

        if (blocksToSend.isNotEmpty() || presencesToSend.isNotEmpty()) {
            val responseMessage = BitswapMessage(
                payload = blocksToSend.takeIf { it.isNotEmpty() },
                blockPresences = presencesToSend.takeIf { it.isNotEmpty() }
            )
            println("BitswapEngine: Sending ${blocksToSend.size} blocks and ${presencesToSend.size} presences to $sourcePeerId")
            networkSender.sendMessage(sourcePeerId, responseMessage)
        } else {
            println("BitswapEngine: No blocks or presences to send to $sourcePeerId for their wantlist.")
        }
    }

    private suspend fun processBlockPresenceFromPeer(sourcePeerId: PeerID, ledger: PeerLedger, presence: BlockPresence) {
        println("BitswapEngine: Peer $sourcePeerId reported ${presence.type} for CID ${presence.cid}")
        // TODO: Update internal state based on this information (e.g., in WantManager or a new PeerInfo tracker)
        // For example, if peer HAS a block we want, we could prioritize asking them.
        // If they DON'T HAVE it, we might deprioritize them for that CID.
    }

    private suspend fun broadcastMessage(message: BitswapMessage) {
        val peersToBroadcast = peerLedgersMutex.withLock { peerLedgers.toMap() } // Get a copy of map
        peersToBroadcast.forEach { (peerId, ledgerInfo) ->
            // ledgerInfo is Pair<PeerLedger, SubnetRoute<TNum>>
            if (peerId != localPeerId) {
                scope.launch {
                    println("BitswapEngine: Broadcasting message to $peerId")
                    networkSender.sendMessage(ledgerInfo.second, message) // Use SubnetRoute
                }
            }
        }
    }

    // --- Methods called by local application/agent ---
    suspend fun requestBlock(cid: CID, priority: Int = 1) {
        println("BitswapEngine: Local request for block $cid with priority $priority")
        wantManager.wantBlock(cid, priority)
        // The change in wantManager will trigger wantBroadcastJob to send wants.
        // Proactive sending to specific peers could also be done here if desired.
    }

    suspend fun notifyLocalBlockAvailable(cid: CID) {
        println("BitswapEngine: Block $cid now available locally.")
        // Check if any connected peers want this block and send it proactively.
        val peersToNotify = peerLedgersMutex.withLock { peerLedgers.toMap() }

        peersToNotify.forEach { (peerId, ledgerInfo) ->
            val ledger = ledgerInfo.first
            val route = ledgerInfo.second // This is SubnetRoute<TNum>
            scope.launch {
                if (ledger.getPeerWants().contains(cid)) {
                    blockStore.get(cid)?.let { data ->
                        val blockContainer = BlockContainer(prefix = byteArrayOf(), data = data) // Prefix needs real handling
                        val message = BitswapMessage(payload = listOf(blockContainer))

                        println("BitswapEngine: Proactively sending block $cid to peer $peerId who wanted it.")
                        networkSender.sendMessage(route, message)
                        ledger.blockSentToPeer(data.size)
                    }
                }
            }
        }
    }

    suspend fun addPotentialPeers(potentialPeers: List<SubnetRoute<TNum>>) {
        if (potentialPeers.isEmpty()) {
            println("BitswapEngine: addPotentialPeers called with no peers.")
            return
        }

        println("BitswapEngine: Considering ${potentialPeers.size} potential new peers.")
        val currentWants = wantManager.getActiveWants()
        if (currentWants.isEmpty()) {
            println("BitswapEngine: No active wants to send to new peers. Adding ledgers only.")
            // Add ledgers even if no wants, so we know about them for future interactions
            for (peerRoute in potentialPeers) {
                 if (peerRoute.nuid.id == null) {
                    println("BitswapEngine: Skipping potential peer with null NUID ID: $peerRoute")
                    continue
                }
                val peerIdStr = peerRoute.nuid.id.toString()
                if (peerIdStr == localPeerId) continue

                getOrCreateLedger(peerIdStr, peerRoute) // Ensure ledger exists, stores/updates route
            }
            return
        }

        val message = BitswapMessage(wantlist = Wantlist(entries = currentWants, full = true))

        for (peerRoute in potentialPeers) {
            if (peerRoute.nuid.id == null) {
                println("BitswapEngine: Skipping potential peer with null NUID ID: $peerRoute")
                continue
            }
            val peerIdStr = peerRoute.nuid.id.toString()
            if (peerIdStr == localPeerId) {
                 println("BitswapEngine: Skipping self ($localPeerId) from potential peers.")
                continue
            }

            getOrCreateLedger(peerIdStr, peerRoute) // Ensure ledger exists, stores/updates route

            println("BitswapEngine: Proactively sending full wantlist to new/potential peer $peerIdStr (Route: $peerRoute)")
            scope.launch { // Launch for each send
                networkSender.sendMessage(peerRoute, message)
            }
            // Note: Tracking that a full wantlist was sent (and to whom for which CIDs)
            // could be an enhancement for WantManager or PeerLedger to avoid redundant full sends.
            // For now, it sends to all provided `potentialPeers`.
        }
    }
}
