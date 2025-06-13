package borg.trikeshed.ipc

import borg.trikeshed.net.common.UdpSocketService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * UDP-based implementation of IPCTransport
 */
class UDPIPCTransport(
    private val udpService: UdpSocketService,
    private val remoteAddress: String
) : BaseIPCTransport() {
    private val receiveChannel = Channel<String>(Channel.BUFFERED)
    private val buffer = StringBuilder()
    
    init {
        startReceiving()
    }
    
    private fun startReceiving() {
        udpService.receive().collect { (data, address) ->
            if (address == remoteAddress) {
                val message = data.toString()
                buffer.append(message)
                
                var delimiterIndex: Int
                while (buffer.indexOf(IPCProtocol.DELIMITER).also { delimiterIndex = it } != -1) {
                    val completeMessage = buffer.substring(0, delimiterIndex)
                    buffer.delete(0, delimiterIndex + IPCProtocol.DELIMITER.length)
                    receiveChannel.trySend(completeMessage)
                }
            }
        }
    }
    
    override suspend fun sendRaw(data: String) {
        udpService.send(data.toByteArray(), remoteAddress)
    }
    
    override fun receiveRaw(): Flow<String> = receiveChannel.receiveAsFlow()
    
    override suspend fun close() {
        receiveChannel.close()
        udpService.close()
    }
} 