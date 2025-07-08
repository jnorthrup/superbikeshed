package borg.trikeshed.services

import borg.trikeshed.channel.api.ChannelizedConnection
import borg.trikeshed.channel.api.LongPollChannel
import borg.trikeshed.channel.api.RealtimeChannel
import borg.trikeshed.channel.api.SecureChannel
import borg.trikeshed.channel.api.ViewChannel
import borg.trikeshed.couchdb.CouchDBRealtimeChannel
import borg.trikeshed.reactor.ReactorRealtimeChannel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ChannelizedServiceManager(
    private val reactorChannel: RealtimeChannel = ReactorRealtimeChannel(),
    private val couchDBChannel: RealtimeChannel = CouchDBRealtimeChannel()
) {

    suspend fun startMonitoringChannels() {
        println("Starting to monitor channelized services...")

        // Monitor Reactor real-time updates
        reactorChannel.realTimeUpdates().collect {
            println("Reactor Realtime Update: ${it.decodeToString()}")
        }

        // Monitor CouchDB real-time updates
        couchDBChannel.realTimeUpdates().collect {
            println("CouchDB Realtime Update: ${it.decodeToString()}")
        }
    }

    suspend fun demonstrateChannelTraits() {
        println("\nDemonstrating Channel Traits...")

        // Demonstrate SecureChannel
        if (reactorChannel is SecureChannel) {
            val handshakeSuccess = reactorChannel.performSecurityHandshake()
            println("Reactor Security Handshake Success: $handshakeSuccess")
        }
        if (couchDBChannel is SecureChannel) {
            val handshakeSuccess = couchDBChannel.performSecurityHandshake()
            println("CouchDB Security Handshake Success: $handshakeSuccess")
        }

        // Demonstrate ViewChannel
        if (reactorChannel is ViewChannel) {
            reactorChannel.requestView("myReactorView".encodeToByteArray()).collect {
                println("Reactor View Data: ${it.decodeToString()}")
            }
        }
        if (couchDBChannel is ViewChannel) {
            couchDBChannel.requestView("myCouchDBView".encodeToByteArray()).collect {
                println("CouchDB View Data: ${it.decodeToString()}")
            }
        }

        // Demonstrate LongPollChannel
        if (reactorChannel is LongPollChannel) {
            reactorChannel.longPoll("reactorLongPollRequest".encodeToByteArray()).collect {
                println("Reactor Long Poll Response: ${it.decodeToString()}")
            }
        }
        if (couchDBChannel is LongPollChannel) {
            couchDBChannel.longPoll("couchDBLongPollRequest".encodeToByteArray()).collect {
                println("CouchDB Long Poll Response: ${it.decodeToString()}")
            }
        }
    }
}

fun main() = runBlocking {
    val manager = ChannelizedServiceManager()

    // Launch monitoring in a separate coroutine
    launch {
        manager.startMonitoringChannels()
    }

    // Demonstrate other traits
    manager.demonstrateChannelTraits()

    // Keep the main coroutine alive for a bit to allow background monitoring
    kotlinx.coroutines.delay(5000) // Wait for 5 seconds
    println("\nDemonstration complete.")
}