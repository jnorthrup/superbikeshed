package borg.trikeshed.ssh

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * SSH Channel Management Implementation
 * 
 * Handles SSH channel multiplexing, flow control, and channel lifecycle
 * management for SSH connections.
 */

// Channel management interface
interface SSHChannelManager {
    suspend fun openChannel(type: SSHChannelType, context: SSHChannelContext): SSHChannelID
    suspend fun sendChannelData(id: SSHChannelID, data: ChannelData, context: SSHChannelContext)
    suspend fun receiveChannelData(id: SSHChannelID, context: SSHChannelContext): ChannelData?
    suspend fun closeChannel(id: SSHChannelID, context: SSHChannelContext)
    suspend fun handleChannelRequest(request: SSHChannelRequest, context: SSHChannelContext)
    suspend fun adjustWindow(id: SSHChannelID, adjustment: UInt, context: SSHChannelContext)
    suspend fun getChannelInfo(id: SSHChannelID, context: SSHChannelContext): SSHChannelInfo?
}

// Channel manager implementation
class SSHChannelManagerImpl : SSHChannelManager {
    internal val channels = mutableMapOf<SSHChannelID, SSHChannel>()
    internal var nextChannelId: SSHChannelID = 0u
    
    override suspend fun openChannel(type: SSHChannelType, context: SSHChannelContext): SSHChannelID {
        return withContext(context.component2()) {
            val channelId = nextChannelId++
            
            val channel = SSHChannel(
                id = channelId,
                type = type,
                localWindowSize = SSHConstants.INITIAL_WINDOW_SIZE,
                remoteWindowSize = SSHConstants.INITIAL_WINDOW_SIZE,
                localMaxPacketSize = SSHConstants.MAX_PACKET_SIZE,
                remoteMaxPacketSize = SSHConstants.MAX_PACKET_SIZE,
                state = SSHChannelState.OPENING
            )
            
            channels[channelId] = channel
            
            // Send channel open request
            val request = buildChannelOpenRequest(channelId, type)
            // TODO: Send request through transport
            
            channelId
        }
    }
    
    override suspend fun sendChannelData(id: SSHChannelID, data: ChannelData, context: SSHChannelContext) {
        withContext(context.component2()) {
            val channel = channels[id] ?: throw SSHException("Channel not found: $id")
            
            if (channel.state != SSHChannelState.OPEN) {
                throw SSHException("Channel not open: $id")
            }
            
            // Check flow control
            if (data.component1() > channel.remoteWindowSize) {
                throw SSHException("Data exceeds remote window size")
            }
            
            if (data.component1() > channel.remoteMaxPacketSize) {
                throw SSHException("Data exceeds remote max packet size")
            }
            
            // Send channel data
            val request = buildChannelDataRequest(id, data)
            // TODO: Send request through transport
            
            // Update window size
            channel.remoteWindowSize -= data.component1().toUInt()
        }
    }
    
    override suspend fun receiveChannelData(id: SSHChannelID, context: SSHChannelContext): ChannelData? {
        return withContext(context.component2()) {
            val channel = channels[id] ?: return@withContext null
            
            if (channel.state != SSHChannelState.OPEN) {
                return@withContext null
            }
            
            // TODO: Receive data from transport
            // For now, return empty data
            null
        }
    }
    
    override suspend fun closeChannel(id: SSHChannelID, context: SSHChannelContext) {
        withContext(context.component2()) {
            val channel = channels[id] ?: return@withContext
            
            if (channel.state == SSHChannelState.CLOSED) {
                return@withContext
            }
            
            // Send channel close request
            val request = buildChannelCloseRequest(id)
            // TODO: Send request through transport
            
            channel.state = SSHChannelState.CLOSED
            channels.remove(id)
        }
    }
    
    override suspend fun handleChannelRequest(request: SSHChannelRequest, context: SSHChannelContext) {
        withContext(context.component2()) {
            when (request) {
                is PTYRequest -> handlePTYRequest(request, context)
                is ShellRequest -> handleShellRequest(request, context)
                is ExecRequest -> handleExecRequest(request, context)
                is SubsystemRequest -> handleSubsystemRequest(request, context)
                is WindowChangeRequest -> handleWindowChangeRequest(request, context)
                is X11Request -> handleX11Request(request, context)
                is SignalRequest -> handleSignalRequest(request, context)
                is ExitStatusRequest -> handleExitStatusRequest(request, context)
                is ExitSignalRequest -> handleExitSignalRequest(request, context)
                else -> throw SSHException("Unsupported channel request: ${request::class.simpleName}")
            }
        }
    }
    
    override suspend fun adjustWindow(id: SSHChannelID, adjustment: UInt, context: SSHChannelContext) {
        withContext(context.component2()) {
            val channel = channels[id] ?: throw SSHException("Channel not found: $id")
            
            channel.localWindowSize += adjustment
            
            // Send window adjust request
            val request = buildWindowAdjustRequest(id, adjustment)
            // TODO: Send request through transport
        }
    }
    
    override suspend fun getChannelInfo(id: SSHChannelID, context: SSHChannelContext): SSHChannelInfo? {
        return withContext(context.component2()) {
            val channel = channels[id] ?: return@withContext null
            
            SSHChannelInfo(
                id = channel.id,
                type = channel.type,
                state = channel.state,
                localWindowSize = channel.localWindowSize,
                remoteWindowSize = channel.remoteWindowSize,
                localMaxPacketSize = channel.localMaxPacketSize,
                remoteMaxPacketSize = channel.remoteMaxPacketSize,
                bytesIn = channel.bytesIn,
                bytesOut = channel.bytesOut
            )
        }
    }
    
    internal suspend fun handlePTYRequest(request: PTYRequest, context: SSHChannelContext) {
        val channelId = context.component1()
        
        // Process PTY request
        val term = request.terminal
        val width = request.width
        val height = request.height
        val modes = request.modes
        
        // TODO: Set up PTY
        println("Setting up PTY: $term ${width}x$height")
        
        // Send success response
        val response = buildChannelSuccessResponse(channelId)
        // TODO: Send response through transport
    }
    
    internal suspend fun handleShellRequest(request: ShellRequest, context: SSHChannelContext) {
        val channelId = context.component1()
        
        // TODO: Start shell
        println("Starting shell for channel $channelId")
        
        // Send success response
        val response = buildChannelSuccessResponse(channelId)
        // TODO: Send response through transport
    }
    
    internal suspend fun handleExecRequest(request: ExecRequest, context: SSHChannelContext) {
        val channelId = context.component1()
        val command = request.command
        
        // TODO: Execute command
        println("Executing command: $command")
        
        // Send success response
        val response = buildChannelSuccessResponse(channelId)
        // TODO: Send response through transport
    }
    
    internal suspend fun handleSubsystemRequest(request: SubsystemRequest, context: SSHChannelContext) {
        val channelId = context.component1()
        val subsystem = request.subsystem
        
        // TODO: Start subsystem
        println("Starting subsystem: ${subsystem.toByteArray().decodeToString()}")
        
        // Send success response
        val response = buildChannelSuccessResponse(channelId)
        // TODO: Send response through transport
    }
    
    internal suspend fun handleWindowChangeRequest(request: WindowChangeRequest, context: SSHChannelContext) {
        val channelId = context.component1()
        val width = request.width
        val height = request.height
        
        // TODO: Update window size
        println("Window change: ${width}x$height")
        
        // Send success response
        val response = buildChannelSuccessResponse(channelId)
        // TODO: Send response through transport
    }
    
    internal suspend fun handleX11Request(request: X11Request, context: SSHChannelContext) {
        val channelId = context.component1()
        
        // TODO: Handle X11 forwarding
        println("X11 forwarding request")
        
        // Send success response
        val response = buildChannelSuccessResponse(channelId)
        // TODO: Send response through transport
    }
    
    internal suspend fun handleSignalRequest(request: SignalRequest, context: SSHChannelContext) {
        val channelId = context.component1()
        val signal = request.signal
        
        // TODO: Send signal to process
        println("Signal request: $signal")
        
        // Send success response
        val response = buildChannelSuccessResponse(channelId)
        // TODO: Send response through transport
    }
    
    internal suspend fun handleExitStatusRequest(request: ExitStatusRequest, context: SSHChannelContext) {
        val channelId = context.component1()
        val status = request.status
        
        // TODO: Handle exit status
        println("Exit status: $status")
        
        // Send success response
        val response = buildChannelSuccessResponse(channelId)
        // TODO: Send response through transport
    }
    
    internal suspend fun handleExitSignalRequest(request: ExitSignalRequest, context: SSHChannelContext) {
        val channelId = context.component1()
        val signal = request.signal
        val coreDumped = request.coreDumped
        val errorMessage = request.errorMessage
        val languageTag = request.languageTag
        
        // TODO: Handle exit signal
        println("Exit signal: $signal, core dumped: $coreDumped")
        
        // Send success response
        val response = buildChannelSuccessResponse(channelId)
        // TODO: Send response through transport
    }
    
    // Request builders
    internal fun buildChannelOpenRequest(channelId: SSHChannelID, type: SSHChannelType): SSHPayload {
        val typeName = type.typeName.encodeToByteArray()
        val senderChannel = channelId.toByteArray()
        
        val size = 1 + 4 + typeName.size + 4 + senderChannel.size + 4 + 4 + 4 + 4
        
        return size j { i: Int ->
            when {
                i == 0 -> SSHMessageType.CHANNEL_OPEN.value
                i < 5 -> ((typeName.size shr ((4 - i) * 8)) and 0xFF).toByte()
                i < 5 + typeName.size -> typeName[i - 5]
                i < 9 + typeName.size -> ((senderChannel.size shr ((8 + typeName.size - i) * 8)) and 0xFF).toByte()
                i < 9 + typeName.size + senderChannel.size -> senderChannel[i - 9 - typeName.size]
                i < 13 + typeName.size + senderChannel.size -> ((SSHConstants.INITIAL_WINDOW_SIZE shr ((12 + typeName.size + senderChannel.size - i) * 8)) and 0xFFu).toByte()
                i < 17 + typeName.size + senderChannel.size -> ((SSHConstants.MAX_PACKET_SIZE shr ((16 + typeName.size + senderChannel.size - i) * 8)) and 0xFFu).toByte()
                else -> 0
            }
        }
    }
    
    internal fun buildChannelDataRequest(channelId: SSHChannelID, data: ChannelData): SSHPayload {
        val recipientChannel = channelId.toByteArray()
        
        val size = 1 + 4 + recipientChannel.size + 4 + data.component1()
        
        return size j { i: Int ->
            when {
                i == 0 -> SSHMessageType.CHANNEL_DATA.value
                i < 5 -> ((recipientChannel.size shr ((4 - i) * 8)) and 0xFF).toByte()
                i < 5 + recipientChannel.size -> recipientChannel[i - 5]
                i < 9 + recipientChannel.size -> ((data.component1() shr ((8 + recipientChannel.size - i) * 8)) and 0xFF).toByte()
                else -> data[i - 9 - recipientChannel.size]
            }
        }
    }
    
    internal fun buildChannelCloseRequest(channelId: SSHChannelID): SSHPayload {
        val recipientChannel = channelId.toByteArray()
        
        val size = 1 + 4 + recipientChannel.size
        
        return size j { i: Int ->
            when {
                i == 0 -> SSHMessageType.CHANNEL_CLOSE.value
                i < 5 -> ((recipientChannel.size shr ((4 - i) * 8)) and 0xFF).toByte()
                else -> recipientChannel[i - 5]
            }
        }
    }
    
    internal fun buildWindowAdjustRequest(channelId: SSHChannelID, adjustment: UInt): SSHPayload {
        val recipientChannel = channelId.toByteArray()
        val adjustmentBytes = adjustment.toByteArray()
        
        val size = 1 + 4 + recipientChannel.size + 4 + adjustmentBytes.size
        
        return size j { i: Int ->
            when {
                i == 0 -> SSHMessageType.CHANNEL_WINDOW_ADJUST.value
                i < 5 -> ((recipientChannel.size shr ((4 - i) * 8)) and 0xFF).toByte()
                i < 5 + recipientChannel.size -> recipientChannel[i - 5]
                i < 9 + recipientChannel.size -> ((adjustmentBytes.size shr ((8 + recipientChannel.size - i) * 8)) and 0xFF).toByte()
                else -> adjustmentBytes[i - 9 - recipientChannel.size]
            }
        }
    }
    
    internal fun buildChannelSuccessResponse(channelId: SSHChannelID): SSHPayload {
        val recipientChannel = channelId.toByteArray()
        
        val size = 1 + 4 + recipientChannel.size
        
        return size j { i: Int ->
            when {
                i == 0 -> SSHMessageType.CHANNEL_SUCCESS.value
                i < 5 -> ((recipientChannel.size shr ((4 - i) * 8)) and 0xFF).toByte()
                else -> recipientChannel[i - 5]
            }
        }
    }
}

// Channel data structures
data class SSHChannel(
    val id: SSHChannelID,
    val type: SSHChannelType,
    var localWindowSize: SSHWindowSize,
    var remoteWindowSize: SSHWindowSize,
    var localMaxPacketSize: SSHMaxPacketSize,
    var remoteMaxPacketSize: SSHMaxPacketSize,
    var state: SSHChannelState,
    var bytesIn: Long = 0,
    var bytesOut: Long = 0
)

data class SSHChannelInfo(
    val id: SSHChannelID,
    val type: SSHChannelType,
    val state: SSHChannelState,
    val localWindowSize: SSHWindowSize,
    val remoteWindowSize: SSHWindowSize,
    val localMaxPacketSize: SSHMaxPacketSize,
    val remoteMaxPacketSize: SSHMaxPacketSize,
    val bytesIn: Long,
    val bytesOut: Long
)

enum class SSHChannelState {
    OPENING,
    OPEN,
    CLOSING,
    CLOSED
}

// Channel request implementations
sealed class SSHChannelRequest {
    abstract val requestType: String
}

class PTYRequest(
    val terminal: String,
    val width: UInt,
    val height: UInt,
    val modes: Indexed<Byte>
) : SSHChannelRequest() {
    override val requestType = "pty-req"
}

class ShellRequest : SSHChannelRequest() {
    override val requestType = "shell"
}

class ExecRequest(val command: String) : SSHChannelRequest() {
    override val requestType = "exec"
}

class SubsystemRequest(val subsystem: Indexed<Byte>) : SSHChannelRequest() {
    override val requestType = "subsystem"
}

class WindowChangeRequest(
    val width: UInt,
    val height: UInt
) : SSHChannelRequest() {
    override val requestType = "window-change"
}

class X11Request(
    val singleConnection: Boolean,
    val x11AuthenticationProtocol: String,
    val x11AuthenticationCookie: String,
    val x11ScreenNumber: UInt
) : SSHChannelRequest() {
    override val requestType = "x11-req"
}

class SignalRequest(val signal: String) : SSHChannelRequest() {
    override val requestType = "signal"
}

class ExitStatusRequest(val status: UInt) : SSHChannelRequest() {
    override val requestType = "exit-status"
}

class ExitSignalRequest(
    val signal: String,
    val coreDumped: Boolean,
    val errorMessage: String,
    val languageTag: String
) : SSHChannelRequest() {
    override val requestType = "exit-signal"
}

// Channel manager factory
object SSHChannelManagerFactory {
    fun createChannelManager(): SSHChannelManager {
        return SSHChannelManagerImpl()
    }
}

// Extension functions
fun UInt.toByteArray(): ByteArray {
    return ByteArray(4) { i ->
        ((this shr ((3 - i) * 8)) and 0xFFu).toByte()
    }
} 