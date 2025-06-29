@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")
package borg.trikeshed.ssh

import borg.trikeshed.lib.*
import kotlinx.coroutines.CoroutineContext
import kotlin.jvm.JvmInline

// === SSH TAXONOMICAL TYPE SYSTEM ===

// Transport layer taxonomy
typealias SSHTransportState = Indexed<StateToken>
typealias SSHPacketStream = Series<SSHPacket>
typealias SSHByteStream = Indexed<Byte>
typealias SSHTransportContext = Join<SSHTransportState, CoroutineContext>

// Wire format taxonomy
typealias SSHWirePacket = Indexed<Byte>
typealias SSHPacketLength = UInt
typealias SSHPaddingLength = Byte
typealias SSHSequenceNumber = UInt
typealias SSHPayload = Indexed<Byte>
// SSHMessageType is now an enum in SSHProtocol.kt

// Authentication taxonomy
typealias SSHIdentity = Join<SSHPublicKey, SSHPrivateKey>
typealias SSHCredential = Indexed<CredentialToken>
typealias SSHAuthMethod = (SSHCredential, SSHAuthContext) -> SSHAuthResult
typealias SSHAuthResult = Join<Boolean, SSHSessionID>
typealias SSHAuthContext = Join<SSHTransportContext, SSHServerInfo>
typealias SSHKnownHosts = Series<SSHHostKey>
typealias SSHHostKey = Join<SSHHostname, SSHPublicKey>

// Channel taxonomy
typealias SSHChannelStream = Series<SSHChannelEvent>
typealias SSHChannelEvent = Join<SSHChannelID, ChannelData>
typealias SSHChannelMultiplexer = (SSHChannelStream) -> Series<SSHChannel>
typealias SSHChannelContext = Join<SSHChannelID, CoroutineContext>
typealias SSHChannelID = UInt
typealias SSHWindowSize = UInt
typealias SSHMaxPacketSize = UInt

// State machine taxonomy
typealias SSHStateTransition = (SSHTransportState, SSHStateContext) -> SSHTransportState
typealias SSHStateGuard = (SSHTransportState, SSHStateContext) -> Boolean
typealias SSHStateEffect = (SSHTransportState, SSHStateContext) -> Unit
typealias SSHStateContext = Join<SSHTransportContext, SSHEventQueue>
typealias SSHEventQueue = Series<SSHEvent>

// Protocol negotiation taxonomy
typealias SSHAlgorithmSet = Join<KEXAlgorithms, Join<CipherSuites, Join<MACAlgorithms, CompressionMethods>>>
typealias SSHNegotiator = (SSHAlgorithmSet, SSHAlgorithmSet, SSHNegotiationContext) -> SSHAlgorithmSet
typealias SSHNegotiationContext = Join<SSHTransportContext, SSHSecurityPolicy>
typealias KEXAlgorithms = Series<SSHKexAlgorithm>
typealias CipherSuites = Series<SSHCipherSuite>
typealias MACAlgorithms = Series<SSHMacAlgorithm>
typealias CompressionMethods = Series<SSHCompressionMethod>

// Session taxonomy
typealias SSHSession = Join<SSHTransportState, Join<SSHChannelStream, SSHSessionContext>>
typealias SSHSessionTransformer = (SSHSession) -> SSHSession
typealias SSHSessionContext = Join<SSHIdentity, Join<SSHServerInfo, CoroutineContext>>
typealias SSHSessionID = Indexed<Byte>

// Crypto taxonomy
typealias SSHKexAlgorithm = String
typealias SSHCipherSuite = String
typealias SSHMacAlgorithm = String
typealias SSHCompressionMethod = String
typealias SSHPublicKey = Indexed<Byte>
typealias SSHPrivateKey = Indexed<Byte>
typealias SSHSharedSecret = Indexed<Byte>
typealias SSHCookie = Indexed<Byte>
typealias SSHSignature = Indexed<Byte>

// Network taxonomy
typealias SSHHostname = String
typealias SSHPort = Int
typealias SSHServerInfo = Join<SSHHostname, SSHPort>
typealias SSHVersion = String
typealias SSHSoftware = String
typealias SSHBanner = Join<SSHVersion, SSHSoftware>

// Channel types taxonomy
// SSHChannelType is now an enum in SSHProtocol.kt
typealias SSHChannelData = Indexed<Byte>
typealias SSHChannelRequest = Join<SSHChannelType, SSHChannelData>
typealias SSHChannelWindow = Join<SSHWindowSize, SSHMaxPacketSize>

// Error handling taxonomy
typealias SSHError = Join<SSHErrorCode, SSHErrorMessage>
typealias SSHErrorCode = UInt
typealias SSHErrorMessage = String
// SSHDisconnectReason is now an enum in SSHProtocol.kt
typealias SSHDisconnect = Join<SSHDisconnectReason, SSHErrorMessage>

// Service taxonomy  
// SSHService is now an enum in SSHProtocol.kt
typealias SSHServiceRequest = (SSHService, SSHServiceContext) -> SSHServiceResult
typealias SSHServiceResult = Join<Boolean, SSHServiceHandle>
typealias SSHServiceHandle = Long
typealias SSHServiceContext = Join<SSHSessionContext, CoroutineContext>

// SFTP taxonomy
typealias SFTPHandle = Indexed<Byte>
typealias SFTPPath = String
typealias SFTPAttributes = Join<SFTPFileType, Join<SFTPPermissions, SFTPSize>>
typealias SFTPFileType = Byte
typealias SFTPPermissions = UInt
typealias SFTPSize = ULong
typealias SFTPRequest = Join<SFTPRequestID, SFTPOperation>
typealias SFTPRequestID = UInt
typealias SFTPOperation = Indexed<Byte>

// Port forwarding taxonomy
typealias SSHForwardRequest = Join<SSHBindAddress, SSHBindPort>
typealias SSHBindAddress = String
typealias SSHBindPort = Int
typealias SSHForwardedConnection = Join<SSHChannelID, Join<SSHOriginAddress, SSHOriginPort>>
typealias SSHOriginAddress = String
typealias SSHOriginPort = Int

// Terminal taxonomy
typealias SSHTerminalType = String
typealias SSHTerminalModes = Series<SSHTerminalSetting>
typealias SSHTerminalSetting = Join<SSHTerminalMode, SSHTerminalValue>
// SSHTerminalMode is now an enum in SSHProtocol.kt
typealias SSHTerminalValue = UInt
typealias SSHTerminalSize = Join<SSHTerminalWidth, Join<SSHTerminalHeight, Join<SSHPixelWidth, SSHPixelHeight>>>
typealias SSHTerminalWidth = Int
typealias SSHTerminalHeight = Int
typealias SSHPixelWidth = Int
typealias SSHPixelHeight = Int

// Signal taxonomy
// SSHSignalType is now an enum in SSHProtocol.kt
typealias SSHSignal = SSHSignalType
typealias SSHExitStatus = Int
typealias SSHExitSignal = Join<SSHSignal, Join<Boolean, SSHErrorMessage>>

// Environment taxonomy
typealias SSHEnvironment = Series<SSHEnvironmentVariable>
typealias SSHEnvironmentVariable = Join<SSHVariableName, SSHVariableValue>
typealias SSHVariableName = String
typealias SSHVariableValue = String

// Value classes for type safety
@JvmInline value class StateToken(val value: Int)
@JvmInline value class CredentialToken(val value: Int)
@JvmInline value class ChannelData(val bytes: Indexed<Byte>)
@JvmInline value class SSHEvent(val type: Int)
@JvmInline value class SSHSecurityPolicy(val flags: Int)

// SSH State enumeration
enum class SSHState {
    DISCONNECTED,
    CONNECTING,
    VERSION_EXCHANGE,
    KEX_INIT,
    KEX_DH,
    NEW_KEYS,
    SERVICE_REQUEST,
    USERAUTH,
    CONNECTED,
    CLOSING
}

// SSH Events
object SSHEvents {
    val CONNECT = SSHEvent(1)
    val VERSION_RECEIVED = SSHEvent(2)
    val KEX_INIT_RECEIVED = SSHEvent(3)
    val KEX_DH_REPLY_RECEIVED = SSHEvent(4)
    val NEW_KEYS_RECEIVED = SSHEvent(5)
    val SERVICE_ACCEPT_RECEIVED = SSHEvent(6)
    val USERAUTH_SUCCESS = SSHEvent(7)
    val USERAUTH_FAILURE = SSHEvent(8)
    val CHANNEL_OPEN = SSHEvent(9)
    val CHANNEL_CLOSE = SSHEvent(10)
    val DISCONNECT = SSHEvent(11)
    val ERROR = SSHEvent(12)
}

// Context-aware service locator
expect object SSHServiceLocator {
    fun getTransportService(context: CoroutineContext): SSHTransportService
    fun getAuthService(context: CoroutineContext): SSHAuthService
    fun getCryptoService(context: CoroutineContext): SSHCryptoService
    fun getChannelService(context: CoroutineContext): SSHChannelService
    fun getSessionService(context: CoroutineContext): SSHSessionService
}

// Service interfaces
interface SSHTransportService {
    suspend fun connect(server: SSHServerInfo, context: SSHTransportContext): SSHTransportState
    suspend fun sendPacket(packet: SSHPacket, context: SSHTransportContext)
    suspend fun receivePacket(context: SSHTransportContext): SSHPacket
    suspend fun disconnect(reason: SSHDisconnectReason, context: SSHTransportContext)
}

interface SSHAuthService {
    suspend fun authenticate(method: SSHAuthMethod, context: SSHAuthContext): SSHAuthResult
    suspend fun verifyHostKey(hostKey: SSHHostKey, context: SSHAuthContext): Boolean
    suspend fun loadIdentity(path: String, context: SSHAuthContext): SSHIdentity
}

interface SSHCryptoService {
    suspend fun negotiateAlgorithms(context: SSHNegotiationContext): SSHAlgorithmSet
    suspend fun performKex(algorithm: SSHKexAlgorithm, context: SSHTransportContext): SSHSharedSecret
    suspend fun deriveKeys(secret: SSHSharedSecret, context: SSHTransportContext): SSHKeySet
    suspend fun encrypt(data: SSHPayload, context: SSHTransportContext): SSHWirePacket
    suspend fun decrypt(packet: SSHWirePacket, context: SSHTransportContext): SSHPayload
}

interface SSHChannelService {
    suspend fun openChannel(type: SSHChannelType, context: SSHChannelContext): SSHChannelID
    suspend fun sendChannelData(id: SSHChannelID, data: ChannelData, context: SSHChannelContext)
    suspend fun closeChannel(id: SSHChannelID, context: SSHChannelContext)
    suspend fun handleChannelRequest(request: SSHChannelRequest, context: SSHChannelContext)
}

interface SSHSessionService {
    suspend fun createSession(context: SSHSessionContext): SSHSession
    suspend fun destroySession(session: SSHSession, context: SSHSessionContext)
    suspend fun getSessionInfo(session: SSHSession): SSHSessionInfo
}

// Supporting types
typealias SSHKeySet = Join<SSHEncryptionKeys, SSHMacKeys>
typealias SSHEncryptionKeys = Join<SSHCipherKey, SSHCipherIV>
typealias SSHMacKeys = Join<SSHMacKey, SSHMacKey>
typealias SSHCipherKey = Indexed<Byte>
typealias SSHCipherIV = Indexed<Byte>
typealias SSHMacKey = Indexed<Byte>

data class SSHSessionInfo(
    val sessionId: SSHSessionID,
    val serverInfo: SSHServerInfo,
    val algorithms: SSHAlgorithmSet,
    val startTime: Long,
    val bytesIn: Long,
    val bytesOut: Long
)

// Packet structure
data class SSHPacket(
    val length: SSHPacketLength,
    val paddingLength: SSHPaddingLength,
    val messageType: SSHMessageType,
    val payload: SSHPayload,
    val padding: Indexed<Byte>,
    val mac: Indexed<Byte>
)

// Channel structure
data class SSHChannel(
    val localId: SSHChannelID,
    val remoteId: SSHChannelID,
    val type: SSHChannelType,
    val localWindow: SSHWindowSize,
    val remoteWindow: SSHWindowSize,
    val localMaxPacket: SSHMaxPacketSize,
    val remoteMaxPacket: SSHMaxPacketSize,
    val context: SSHChannelContext
)