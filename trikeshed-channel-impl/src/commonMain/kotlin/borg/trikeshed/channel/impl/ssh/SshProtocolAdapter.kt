@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.channel.impl.ssh

import borg.trikeshed.lib.*
import borg.trikeshed.channel.api.*
import borg.trikeshed.channel.impl.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext

/**
 * SSH Protocol Adapter for channelization.
 * Normalizes SSH protocol to use unified channel abstractions.
 */
class SshProtocolAdapter(
    override val config: SshConfig
) : ProtocolAdapter<SshMessage, SshConfig> {
    
    override val protocolName: String = "SSH"
    override val key: CoroutineContext.Key<*> = Key
    
    companion object Key : CoroutineContext.Key<SshProtocolAdapter>
    
    /**
     * Parse SSH message from byte data using BBCursive-style parsing.
     */
    override suspend fun parseMessage(data: ByteIndexed, position: Int): Join<SshMessage?, Int>? {
        if (position + 4 >= data.size) return null
        
        // SSH packet format: [length:4][padding_length:1][payload][padding][mac]
        val packetLength = readUint32(data, position)
        if (packetLength <= 0 || position + 4 + packetLength > data.size) return null
        
        val paddingLength = data[position + 4].toInt()9ij9ij
        
        val payloadLength = packetLength - paddingLength - 1
        
        if (payloadLength < 0 || position + 5 + payloadLength > data.size) return null
        
        val messageType = data[position + 5].toInt()
        val payload = data.slice(position + 6 until position + 5 + payloadLength)
        
        val message = parseSshPayload(messageType, payload)
        if (message == null) return null
        
        val totalLength = 4 + packetLength
        return (message j (position + totalLength))
    }
    
    /**
     * Serialize SSH message to bytes.
     */
    override suspend fun serializeMessage(message: SshMessage): ByteIndexed {
        val payload = when (message) {
            is SshMessage.Disconnect -> serializeDisconnect(message)
            is SshMessage.Ignore -> serializeIgnore(message)
            is SshMessage.Unimplemented -> serializeUnimplemented(message)
            is SshMessage.Debug -> serializeDebug(message)
            is SshMessage.ServiceRequest -> serializeServiceRequest(message)
            is SshMessage.ServiceAccept -> serializeServiceAccept(message)
            is SshMessage.KexInit -> serializeKexInit(message)
            is SshMessage.NewKeys -> serializeNewKeys(message)
            is SshMessage.UserAuthRequest -> serializeUserAuthRequest(message)
            is SshMessage.UserAuthSuccess -> serializeUserAuthSuccess(message)
            is SshMessage.UserAuthFailure -> serializeUserAuthFailure(message)
            is SshMessage.ChannelOpen -> serializeChannelOpen(message)
            is SshMessage.ChannelOpenConfirmation -> serializeChannelOpenConfirmation(message)
            is SshMessage.ChannelOpenFailure -> serializeChannelOpenFailure(message)
            is SshMessage.ChannelWindowAdjust -> serializeChannelWindowAdjust(message)
            is SshMessage.ChannelData -> serializeChannelData(message)
            is SshMessage.ChannelExtendedData -> serializeChannelExtendedData(message)
            is SshMessage.ChannelEof -> serializeChannelEof(message)
            is SshMessage.ChannelClose -> serializeChannelClose(message)
            is SshMessage.ChannelRequest -> serializeChannelRequest(message)
            is SshMessage.ChannelSuccess -> serializeChannelSuccess(message)
            is SshMessage.ChannelFailure -> serializeChannelFailure(message)
        }
        
        // Add padding to meet block size requirements
        val paddingLength = calculatePaddingLength(payload.size)
        val padding = ByteArray(paddingLength) { (0..255).random().toByte() }
        
        // Build packet: [length:4][padding_length:1][payload][padding]
        val packetLength = 1 + payload.size + paddingLength
        val packet = ByteArray(4 + packetLength)
        
        writeUint32(packet, 0, packetLength.toLong())
        packet[4] = paddingLength.toByte()
        payload.toByteArray().copyInto(packet, 5)
        padding.copyInto(packet, 5 + payload.size)
        
        return packet.toIndexed()
    }
    
    /**
     * Handle SSH message processing with protocol context.
     */
    override suspend fun handleMessage(message: SshMessage, context: ProtocolContext): Flow<SshMessage> = flow {
        when (message) {
            is SshMessage.Disconnect -> {
                val handler = context.currentService<SshDisconnectHandler>()
                if (handler != null) {
                    val response = handler.handleDisconnect(message)
                    emit(response)
                } else {
                    // Default disconnect handling
                    emit(message)
                }
            }
            
            is SshMessage.KexInit -> {
                val handler = context.currentService<SshKexHandler>()
                if (handler != null) {
                    val response = handler.handleKexInit(message)
                    emit(response)
                } else {
                    // Default key exchange handling
                    emit(SshMessage.NewKeys)
                }
            }
            
            is SshMessage.UserAuthRequest -> {
                val handler = context.currentService<SshAuthHandler>()
                if (handler != null) {
                    val response = handler.handleAuthRequest(message)
                    emit(response)
                } else {
                    // Default auth handling - reject
                    emit(SshMessage.UserAuthFailure(
                        authentications = listOf("password", "publickey"),
                        partialSuccess = false
                    ))
                }
            }
            
            is SshMessage.ChannelOpen -> {
                val handler = context.currentService<SshChannelHandler>()
                if (handler != null) {
                    val response = handler.handleChannelOpen(message)
                    emit(response)
                } else {
                    // Default channel handling - reject
                    emit(SshMessage.ChannelOpenFailure(
                        recipientChannel = message.senderChannel,
                        reasonCode = SshChannelOpenFailureReason.ADMINISTRATIVELY_PROHIBITED,
                        description = "Channel not supported",
                        languageTag = ""
                    ))
                }
            }
            
            is SshMessage.ChannelData -> {
                val handler = context.currentService<SshChannelHandler>()
                if (handler != null) {
                    val response = handler.handleChannelData(message)
                    emit(response)
                } else {
                    // Echo back the data
                    emit(SshMessage.ChannelData(
                        recipientChannel = message.senderChannel,
                        data = message.data
                    ))
                }
            }
            
            else -> {
                // Pass through other messages unchanged
                emit(message)
            }
        }
    }
    
    /**
     * Create SSH protocol channel wrapper.
     */
    override fun wrapChannel(channel: Channel): ProtocolChannel<SshMessage> {
        return SshProtocolChannel(this, channel)
    }
    
    internal fun parseSshPayload(messageType: Int, payload: ByteIndexed): SshMessage? {
        return when (messageType) {
            1 -> parseDisconnect(payload)
            2 -> parseIgnore(payload)
            3 -> parseUnimplemented(payload)
            4 -> parseDebug(payload)
            5 -> parseServiceRequest(payload)
            6 -> parseServiceAccept(payload)
            20 -> parseKexInit(payload)
            21 -> SshMessage.NewKeys
            50 -> parseUserAuthRequest(payload)
            51 -> parseUserAuthSuccess(payload)
            52 -> parseUserAuthFailure(payload)
            90 -> parseChannelOpen(payload)
            91 -> parseChannelOpenConfirmation(payload)
            92 -> parseChannelOpenFailure(payload)
            93 -> parseChannelWindowAdjust(payload)
            94 -> parseChannelData(payload)
            95 -> parseChannelExtendedData(payload)
            96 -> parseChannelEof(payload)
            97 -> parseChannelClose(payload)
            98 -> parseChannelRequest(payload)
            99 -> parseChannelSuccess(payload)
            100 -> parseChannelFailure(payload)
            else -> null
        }
    }
    
    internal fun parseDisconnect(payload: ByteIndexed): SshMessage.Disconnect {
        val reasonCode = readUint32(payload, 0)
        val description = readString(payload, 4)
        val languageTag = readString(payload, 4 + 4 + description.length)
        
        return SshMessage.Disconnect(
            reasonCode = reasonCode.toInt(),
            description = description,
            languageTag = languageTag
        )
    }
    
    internal fun parseIgnore(payload: ByteIndexed): SshMessage.Ignore {
        val data = readString(payload, 0)
        return SshMessage.Ignore(data)
    }
    
    internal fun parseUnimplemented(payload: ByteIndexed): SshMessage.Unimplemented {
        val sequenceNumber = readUint32(payload, 0)
        return SshMessage.Unimplemented(sequenceNumber)
    }
    
    internal fun parseDebug(payload: ByteIndexed): SshMessage.Debug {
        val alwaysDisplay = payload[0] != 0.toByte()
        val message = readString(payload, 1)
        val languageTag = readString(payload, 1 + 4 + message.length)
        
        return SshMessage.Debug(
            alwaysDisplay = alwaysDisplay,
            message = message,
            languageTag = languageTag
        )
    }
    
    internal fun parseServiceRequest(payload: ByteIndexed): SshMessage.ServiceRequest {
        val serviceName = readString(payload, 0)
        return SshMessage.ServiceRequest(serviceName)
    }
    
    internal fun parseServiceAccept(payload: ByteIndexed): SshMessage.ServiceAccept {
        val serviceName = readString(payload, 0)
        return SshMessage.ServiceAccept(serviceName)
    }
    
    internal fun parseKexInit(payload: ByteIndexed): SshMessage.KexInit {
        val cookie = payload.slice(0 until 16)
        val kexAlgorithms = readNameList(payload, 16)
        val serverHostKeyAlgorithms = readNameList(payload, 16 + 4 + kexAlgorithms.sumOf { it.length })
        val encryptionAlgorithmsClientToServer = readNameList(payload, 16 + 4 + kexAlgorithms.sumOf { it.length } + 4 + serverHostKeyAlgorithms.sumOf { it.length })
        val encryptionAlgorithmsServerToClient = readNameList(payload, 16 + 4 + kexAlgorithms.sumOf { it.length } + 4 + serverHostKeyAlgorithms.sumOf { it.length } + 4 + encryptionAlgorithmsClientToServer.sumOf { it.length })
        val macAlgorithmsClientToServer = readNameList(payload, 16 + 4 + kexAlgorithms.sumOf { it.length } + 4 + serverHostKeyAlgorithms.sumOf { it.length } + 4 + encryptionAlgorithmsClientToServer.sumOf { it.length } + 4 + encryptionAlgorithmsServerToClient.sumOf { it.length })
        val macAlgorithmsServerToClient = readNameList(payload, 16 + 4 + kexAlgorithms.sumOf { it.length } + 4 + serverHostKeyAlgorithms.sumOf { it.length } + 4 + encryptionAlgorithmsClientToServer.sumOf { it.length } + 4 + encryptionAlgorithmsServerToClient.sumOf { it.length } + 4 + macAlgorithmsClientToServer.sumOf { it.length })
        val compressionAlgorithmsClientToServer = readNameList(payload, 16 + 4 + kexAlgorithms.sumOf { it.length } + 4 + serverHostKeyAlgorithms.sumOf { it.length } + 4 + encryptionAlgorithmsClientToServer.sumOf { it.length } + 4 + encryptionAlgorithmsServerToClient.sumOf { it.length } + 4 + macAlgorithmsClientToServer.sumOf { it.length } + 4 + macAlgorithmsServerToClient.sumOf { it.length })
        val compressionAlgorithmsServerToClient = readNameList(payload, 16 + 4 + kexAlgorithms.sumOf { it.length } + 4 + serverHostKeyAlgorithms.sumOf { it.length } + 4 + encryptionAlgorithmsClientToServer.sumOf { it.length } + 4 + encryptionAlgorithmsServerToClient.sumOf { it.length } + 4 + macAlgorithmsClientToServer.sumOf { it.length } + 4 + macAlgorithmsServerToClient.sumOf { it.length } + 4 + compressionAlgorithmsClientToServer.sumOf { it.length })
        val languagesClientToServer = readNameList(payload, 16 + 4 + kexAlgorithms.sumOf { it.length } + 4 + serverHostKeyAlgorithms.sumOf { it.length } + 4 + encryptionAlgorithmsClientToServer.sumOf { it.length } + 4 + encryptionAlgorithmsServerToClient.sumOf { it.length } + 4 + macAlgorithmsClientToServer.sumOf { it.length } + 4 + macAlgorithmsServerToClient.sumOf { it.length } + 4 + compressionAlgorithmsClientToServer.sumOf { it.length } + 4 + compressionAlgorithmsServerToClient.sumOf { it.length })
        val languagesServerToClient = readNameList(payload, 16 + 4 + kexAlgorithms.sumOf { it.length } + 4 + serverHostKeyAlgorithms.sumOf { it.length } + 4 + encryptionAlgorithmsClientToServer.sumOf { it.length } + 4 + encryptionAlgorithmsServerToClient.sumOf { it.length } + 4 + macAlgorithmsClientToServer.sumOf { it.length } + 4 + macAlgorithmsServerToClient.sumOf { it.length } + 4 + compressionAlgorithmsClientToServer.sumOf { it.length } + 4 + compressionAlgorithmsServerToClient.sumOf { it.length } + 4 + languagesClientToServer.sumOf { it.length })
        val firstKexPacketFollows = payload[16 + 4 + kexAlgorithms.sumOf { it.length } + 4 + serverHostKeyAlgorithms.sumOf { it.length } + 4 + encryptionAlgorithmsClientToServer.sumOf { it.length } + 4 + encryptionAlgorithmsServerToClient.sumOf { it.length } + 4 + macAlgorithmsClientToServer.sumOf { it.length } + 4 + macAlgorithmsServerToClient.sumOf { it.length } + 4 + compressionAlgorithmsClientToServer.sumOf { it.length } + 4 + compressionAlgorithmsServerToClient.sumOf { it.length } + 4 + languagesClientToServer.sumOf { it.length } + 4 + languagesServerToClient.sumOf { it.length }] != 0.toByte()
        val reserved = 0L
        
        return SshMessage.KexInit(
            cookie = cookie,
            kexAlgorithms = kexAlgorithms,
            serverHostKeyAlgorithms = serverHostKeyAlgorithms,
            encryptionAlgorithmsClientToServer = encryptionAlgorithmsClientToServer,
            encryptionAlgorithmsServerToClient = encryptionAlgorithmsServerToClient,
            macAlgorithmsClientToServer = macAlgorithmsClientToServer,
            macAlgorithmsServerToClient = macAlgorithmsServerToClient,
            compressionAlgorithmsClientToServer = compressionAlgorithmsClientToServer,
            compressionAlgorithmsServerToClient = compressionAlgorithmsServerToClient,
            languagesClientToServer = languagesClientToServer,
            languagesServerToClient = languagesServerToClient,
            firstKexPacketFollows = firstKexPacketFollows,
            reserved = reserved
        )
    }
    
    internal fun parseUserAuthRequest(payload: ByteIndexed): SshMessage.UserAuthRequest {
        val username = readString(payload, 0)
        val serviceName = readString(payload, 4 + username.length)
        val methodName = readString(payload, 4 + username.length + 4 + serviceName.length)
        
        return SshMessage.UserAuthRequest(
            username = username,
            serviceName = serviceName,
            methodName = methodName
        )
    }
    
    internal fun parseUserAuthSuccess(payload: ByteIndexed): SshMessage.UserAuthSuccess {
        return SshMessage.UserAuthSuccess
    }
    
    internal fun parseUserAuthFailure(payload: ByteIndexed): SshMessage.UserAuthFailure {
        val authentications = readNameList(payload, 0)
        val partialSuccess = payload[4 + authentications.sumOf { it.length }] != 0.toByte()
        
        return SshMessage.UserAuthFailure(
            authentications = authentications,
            partialSuccess = partialSuccess
        )
    }
    
    internal fun parseChannelOpen(payload: ByteIndexed): SshMessage.ChannelOpen {
        val channelType = readString(payload, 0)
        val senderChannel = readUint32(payload, 4 + channelType.length)
        val initialWindowSize = readUint32(payload, 4 + channelType.length + 4)
        val maximumPacketSize = readUint32(payload, 4 + channelType.length + 4 + 4)
        
        return SshMessage.ChannelOpen(
            channelType = channelType,
            senderChannel = senderChannel,
            initialWindowSize = initialWindowSize,
            maximumPacketSize = maximumPacketSize
        )
    }
    
    internal fun parseChannelOpenConfirmation(payload: ByteIndexed): SshMessage.ChannelOpenConfirmation {
        val recipientChannel = readUint32(payload, 0)
        val senderChannel = readUint32(payload, 4)
        val initialWindowSize = readUint32(payload, 8)
        val maximumPacketSize = readUint32(payload, 12)
        
        return SshMessage.ChannelOpenConfirmation(
            recipientChannel = recipientChannel,
            senderChannel = senderChannel,
            initialWindowSize = initialWindowSize,
            maximumPacketSize = maximumPacketSize
        )
    }
    
    internal fun parseChannelOpenFailure(payload: ByteIndexed): SshMessage.ChannelOpenFailure {
        val recipientChannel = readUint32(payload, 0)
        val reasonCode = readUint32(payload, 4)
        val description = readString(payload, 8)
        val languageTag = readString(payload, 8 + 4 + description.length)
        
        return SshMessage.ChannelOpenFailure(
            recipientChannel = recipientChannel,
            reasonCode = reasonCode.toInt(),
            description = description,
            languageTag = languageTag
        )
    }
    
    internal fun parseChannelWindowAdjust(payload: ByteIndexed): SshMessage.ChannelWindowAdjust {
        val recipientChannel = readUint32(payload, 0)
        val bytesToAdd = readUint32(payload, 4)
        
        return SshMessage.ChannelWindowAdjust(
            recipientChannel = recipientChannel,
            bytesToAdd = bytesToAdd
        )
    }
    
    internal fun parseChannelData(payload: ByteIndexed): SshMessage.ChannelData {
        val recipientChannel = readUint32(payload, 0)
        val data = readString(payload, 4)
        
        return SshMessage.ChannelData(
            recipientChannel = recipientChannel,
            data = data
        )
    }
    
    internal fun parseChannelExtendedData(payload: ByteIndexed): SshMessage.ChannelExtendedData {
        val recipientChannel = readUint32(payload, 0)
        val dataTypeCode = readUint32(payload, 4)
        val data = readString(payload, 8)
        
        return SshMessage.ChannelExtendedData(
            recipientChannel = recipientChannel,
            dataTypeCode = dataTypeCode.toInt(),
            data = data
        )
    }
    
    internal fun parseChannelEof(payload: ByteIndexed): SshMessage.ChannelEof {
        val recipientChannel = readUint32(payload, 0)
        return SshMessage.ChannelEof(recipientChannel)
    }
    
    internal fun parseChannelClose(payload: ByteIndexed): SshMessage.ChannelClose {
        val recipientChannel = readUint32(payload, 0)
        return SshMessage.ChannelClose(recipientChannel)
    }
    
    internal fun parseChannelRequest(payload: ByteIndexed): SshMessage.ChannelRequest {
        val recipientChannel = readUint32(payload, 0)
        val requestType = readString(payload, 4)
        val wantReply = payload[4 + 4 + requestType.length] != 0.toByte()
        
        return SshMessage.ChannelRequest(
            recipientChannel = recipientChannel,
            requestType = requestType,
            wantReply = wantReply
        )
    }
    
    internal fun parseChannelSuccess(payload: ByteIndexed): SshMessage.ChannelSuccess {
        val recipientChannel = readUint32(payload, 0)
        return SshMessage.ChannelSuccess(recipientChannel)
    }
    
    internal fun parseChannelFailure(payload: ByteIndexed): SshMessage.ChannelFailure {
        val recipientChannel = readUint32(payload, 0)
        return SshMessage.ChannelFailure(recipientChannel)
    }
    
    // Serialization methods
    internal fun serializeDisconnect(message: SshMessage.Disconnect): ByteIndexed {
        val payload = ByteArray(4 + 4 + message.description.length + 4 + message.languageTag.length)
        writeUint32(payload, 0, message.reasonCode.toLong())
        writeString(payload, 4, message.description)
        writeString(payload, 4 + 4 + message.description.length, message.languageTag)
        return payload.toIndexed()
    }
    
    internal fun serializeIgnore(message: SshMessage.Ignore): ByteIndexed {
        val payload = ByteArray(4 + message.data.length)
        writeString(payload, 0, message.data)
        return payload.toIndexed()
    }
    
    internal fun serializeUnimplemented(message: SshMessage.Unimplemented): ByteIndexed {
        val payload = ByteArray(4)
        writeUint32(payload, 0, message.sequenceNumber)
        return payload.toIndexed()
    }
    
    internal fun serializeDebug(message: SshMessage.Debug): ByteIndexed {
        val payload = ByteArray(1 + 4 + message.message.length + 4 + message.languageTag.length)
        payload[0] = if (message.alwaysDisplay) 1 else 0
        writeString(payload, 1, message.message)
        writeString(payload, 1 + 4 + message.message.length, message.languageTag)
        return payload.toIndexed()
    }
    
    internal fun serializeServiceRequest(message: SshMessage.ServiceRequest): ByteIndexed {
        val payload = ByteArray(4 + message.serviceName.length)
        writeString(payload, 0, message.serviceName)
        return payload.toIndexed()
    }
    
    internal fun serializeServiceAccept(message: SshMessage.ServiceAccept): ByteIndexed {
        val payload = ByteArray(4 + message.serviceName.length)
        writeString(payload, 0, message.serviceName)
        return payload.toIndexed()
    }
    
    internal fun serializeKexInit(message: SshMessage.KexInit): ByteIndexed {
        // Simplified KEX_INIT serialization
        val payload = ByteArray(16 + 4 + message.kexAlgorithms.sumOf { it.length } + 4 + message.serverHostKeyAlgorithms.sumOf { it.length } + 4 + message.encryptionAlgorithmsClientToServer.sumOf { it.length } + 4 + message.encryptionAlgorithmsServerToClient.sumOf { it.length } + 4 + message.macAlgorithmsClientToServer.sumOf { it.length } + 4 + message.macAlgorithmsServerToClient.sumOf { it.length } + 4 + message.compressionAlgorithmsClientToServer.sumOf { it.length } + 4 + message.compressionAlgorithmsServerToClient.sumOf { it.length } + 4 + message.languagesClientToServer.sumOf { it.length } + 4 + message.languagesServerToClient.sumOf { it.length } + 1 + 4)
        
        var offset = 0
        message.cookie.toByteArray().copyInto(payload, offset)
        offset += 16
        
        writeNameList(payload, offset, message.kexAlgorithms)
        offset += 4 + message.kexAlgorithms.sumOf { it.length }
        
        writeNameList(payload, offset, message.serverHostKeyAlgorithms)
        offset += 4 + message.serverHostKeyAlgorithms.sumOf { it.length }
        
        writeNameList(payload, offset, message.encryptionAlgorithmsClientToServer)
        offset += 4 + message.encryptionAlgorithmsClientToServer.sumOf { it.length }
        
        writeNameList(payload, offset, message.encryptionAlgorithmsServerToClient)
        offset += 4 + message.encryptionAlgorithmsServerToClient.sumOf { it.length }
        
        writeNameList(payload, offset, message.macAlgorithmsClientToServer)
        offset += 4 + message.macAlgorithmsClientToServer.sumOf { it.length }
        
        writeNameList(payload, offset, message.macAlgorithmsServerToClient)
        offset += 4 + message.macAlgorithmsServerToClient.sumOf { it.length }
        
        writeNameList(payload, offset, message.compressionAlgorithmsClientToServer)
        offset += 4 + message.compressionAlgorithmsClientToServer.sumOf { it.length }
        
        writeNameList(payload, offset, message.compressionAlgorithmsServerToClient)
        offset += 4 + message.compressionAlgorithmsServerToClient.sumOf { it.length }
        
        writeNameList(payload, offset, message.languagesClientToServer)
        offset += 4 + message.languagesClientToServer.sumOf { it.length }
        
        writeNameList(payload, offset, message.languagesServerToClient)
        offset += 4 + message.languagesServerToClient.sumOf { it.length }
        
        payload[offset] = if (message.firstKexPacketFollows) 1 else 0
        offset += 1
        
        writeUint32(payload, offset, message.reserved)
        
        return payload.toIndexed()
    }
    
    internal fun serializeNewKeys(message: SshMessage.NewKeys): ByteIndexed {
        return ByteArray(0).toIndexed()
    }
    
    internal fun serializeUserAuthRequest(message: SshMessage.UserAuthRequest): ByteIndexed {
        val payload = ByteArray(4 + message.username.length + 4 + message.serviceName.length + 4 + message.methodName.length)
        var offset = 0
        
        writeString(payload, offset, message.username)
        offset += 4 + message.username.length
        
        writeString(payload, offset, message.serviceName)
        offset += 4 + message.serviceName.length
        
        writeString(payload, offset, message.methodName)
        
        return payload.toIndexed()
    }
    
    internal fun serializeUserAuthSuccess(message: SshMessage.UserAuthSuccess): ByteIndexed {
        return ByteArray(0).toIndexed()
    }
    
    internal fun serializeUserAuthFailure(message: SshMessage.UserAuthFailure): ByteIndexed {
        val payload = ByteArray(4 + message.authentications.sumOf { it.length } + 1)
        var offset = 0
        
        writeNameList(payload, offset, message.authentications)
        offset += 4 + message.authentications.sumOf { it.length }
        
        payload[offset] = if (message.partialSuccess) 1 else 0
        
        return payload.toIndexed()
    }
    
    internal fun serializeChannelOpen(message: SshMessage.ChannelOpen): ByteIndexed {
        val payload = ByteArray(4 + message.channelType.length + 4 + 4 + 4)
        var offset = 0
        
        writeString(payload, offset, message.channelType)
        offset += 4 + message.channelType.length
        
        writeUint32(payload, offset, message.senderChannel)
        offset += 4
        
        writeUint32(payload, offset, message.initialWindowSize)
        offset += 4
        
        writeUint32(payload, offset, message.maximumPacketSize)
        
        return payload.toIndexed()
    }
    
    internal fun serializeChannelOpenConfirmation(message: SshMessage.ChannelOpenConfirmation): ByteIndexed {
        val payload = ByteArray(4 + 4 + 4 + 4)
        var offset = 0
        
        writeUint32(payload, offset, message.recipientChannel)
        offset += 4
        
        writeUint32(payload, offset, message.senderChannel)
        offset += 4
        
        writeUint32(payload, offset, message.initialWindowSize)
        offset += 4
        
        writeUint32(payload, offset, message.maximumPacketSize)
        
        return payload.toIndexed()
    }
    
    internal fun serializeChannelOpenFailure(message: SshMessage.ChannelOpenFailure): ByteIndexed {
        val payload = ByteArray(4 + 4 + 4 + message.description.length + 4 + message.languageTag.length)
        var offset = 0
        
        writeUint32(payload, offset, message.recipientChannel)
        offset += 4
        
        writeUint32(payload, offset, message.reasonCode.toLong())
        offset += 4
        
        writeString(payload, offset, message.description)
        offset += 4 + message.description.length
        
        writeString(payload, offset, message.languageTag)
        
        return payload.toIndexed()
    }
    
    internal fun serializeChannelWindowAdjust(message: SshMessage.ChannelWindowAdjust): ByteIndexed {
        val payload = ByteArray(4 + 4)
        writeUint32(payload, 0, message.recipientChannel)
        writeUint32(payload, 4, message.bytesToAdd)
        return payload.toIndexed()
    }
    
    internal fun serializeChannelData(message: SshMessage.ChannelData): ByteIndexed {
        val payload = ByteArray(4 + 4 + message.data.length)
        writeUint32(payload, 0, message.recipientChannel)
        writeString(payload, 4, message.data)
        return payload.toIndexed()
    }
    
    internal fun serializeChannelExtendedData(message: SshMessage.ChannelExtendedData): ByteIndexed {
        val payload = ByteArray(4 + 4 + 4 + message.data.length)
        writeUint32(payload, 0, message.recipientChannel)
        writeUint32(payload, 4, message.dataTypeCode.toLong())
        writeString(payload, 8, message.data)
        return payload.toIndexed()
    }
    
    internal fun serializeChannelEof(message: SshMessage.ChannelEof): ByteIndexed {
        val payload = ByteArray(4)
        writeUint32(payload, 0, message.recipientChannel)
        return payload.toIndexed()
    }
    
    internal fun serializeChannelClose(message: SshMessage.ChannelClose): ByteIndexed {
        val payload = ByteArray(4)
        writeUint32(payload, 0, message.recipientChannel)
        return payload.toIndexed()
    }
    
    internal fun serializeChannelRequest(message: SshMessage.ChannelRequest): ByteIndexed {
        val payload = ByteArray(4 + 4 + message.requestType.length + 1)
        var offset = 0
        
        writeUint32(payload, offset, message.recipientChannel)
        offset += 4
        
        writeString(payload, offset, message.requestType)
        offset += 4 + message.requestType.length
        
        payload[offset] = if (message.wantReply) 1 else 0
        
        return payload.toIndexed()
    }
    
    internal fun serializeChannelSuccess(message: SshMessage.ChannelSuccess): ByteIndexed {
        val payload = ByteArray(4)
        writeUint32(payload, 0, message.recipientChannel)
        return payload.toIndexed()
    }
    
    internal fun serializeChannelFailure(message: SshMessage.ChannelFailure): ByteIndexed {
        val payload = ByteArray(4)
        writeUint32(payload, 0, message.recipientChannel)
        return payload.toIndexed()
    }
    
    internal fun calculatePaddingLength(payloadLength: Int): Int {
        val blockSize = 8 // SSH block size
        val minPadding = 4
        val paddingLength = minPadding + (blockSize - ((payloadLength + minPadding) % blockSize)) % blockSize
        return paddingLength
    }
    
    // Utility functions
    internal fun readUint32(data: ByteIndexed, position: Int): Long {
        return (data[position].toLong() shl 24) or
               (data[position + 1].toLong() shl 16) or
               (data[position + 2].toLong() shl 8) or
               data[position + 3].toLong()
    }
    
    internal fun writeUint32(data: ByteArray, position: Int, value: Long) {
        data[position] = (value shr 24).toByte()
        data[position + 1] = (value shr 16).toByte()
        data[position + 2] = (value shr 8).toByte()
        data[position + 3] = value.toByte()
    }
    
    internal fun readString(data: ByteIndexed, position: Int): String {
        val length = readUint32(data, position).toInt()
        return data.slice(position + 4 until position + 4 + length).decodeToString()
    }
    
    internal fun writeString(data: ByteArray, position: Int, value: String) {
        val bytes = value.encodeToByteArray()
        writeUint32(data, position, bytes.size.toLong())
        bytes.copyInto(data, position + 4)
    }
    
    internal fun readNameList(data: ByteIndexed, position: Int): List<String> {
        val length = readUint32(data, position).toInt()
        val listData = data.slice(position + 4 until position + 4 + length)
        val names = mutableListOf<String>()
        var offset = 0
        
        while (offset < listData.size) {
            val nameLength = readUint32(listData, offset).toInt()
            val name = listData.slice(offset + 4 until offset + 4 + nameLength).decodeToString()
            names.add(name)
            offset += 4 + nameLength
        }
        
        return names
    }
    
    internal fun writeNameList(data: ByteArray, position: Int, names: List<String>) {
        val totalLength = names.sumOf { 4 + it.length }
        writeUint32(data, position, totalLength.toLong())
        var offset = position + 4
        
        names.forEach { name ->
            writeString(data, offset, name)
            offset += 4 + name.length
        }
    }
}

/**
 * SSH protocol channel implementation.
 */
class SshProtocolChannel(
    internal val adapter: SshProtocolAdapter,
    internal val channel: Channel
) : AbstractProtocolChannel<SshMessage>(adapter, channel) {
    
    /**
     * Send SSH disconnect message.
     */
    suspend fun sendDisconnect(reasonCode: Int, description: String) {
        sendMessage(SshMessage.Disconnect(reasonCode, description, ""))
    }
    
    /**
     * Send SSH channel data.
     */
    suspend fun sendChannelData(recipientChannel: Long, data: String) {
        sendMessage(SshMessage.ChannelData(recipientChannel, data))
    }
    
    /**
     * Get incoming SSH channel data.
     */
    fun incomingChannelData(): Flow<Join<Long, String>> = flow {
        incomingMessages()
            .filterIsInstance<SshMessage.ChannelData>()
            .collect { message ->
                emit(message.recipientChannel j message.data)
            }
    }
    
    /**
     * Get incoming SSH authentication requests.
     */
    fun incomingAuthRequests(): Flow<SshMessage.UserAuthRequest> = flow {
        incomingMessages()
            .filterIsInstance<SshMessage.UserAuthRequest>()
            .collect { message ->
                emit(message)
            }
    }
}

/**
 * SSH message types for channelization.
 */
sealed class SshMessage {
    data class Disconnect(
        val reasonCode: Int,
        val description: String,
        val languageTag: String
    ) : SshMessage()
    
    data class Ignore(val data: String) : SshMessage()
    data class Unimplemented(val sequenceNumber: Long) : SshMessage()
    
    data class Debug(
        val alwaysDisplay: Boolean,
        val message: String,
        val languageTag: String
    ) : SshMessage()
    
    data class ServiceRequest(val serviceName: String) : SshMessage()
    data class ServiceAccept(val serviceName: String) : SshMessage()
    
    data class KexInit(
        val cookie: ByteIndexed,
        val kexAlgorithms: List<String>,
        val serverHostKeyAlgorithms: List<String>,
        val encryptionAlgorithmsClientToServer: List<String>,
        val encryptionAlgorithmsServerToClient: List<String>,
        val macAlgorithmsClientToServer: List<String>,
        val macAlgorithmsServerToClient: List<String>,
        val compressionAlgorithmsClientToServer: List<String>,
        val compressionAlgorithmsServerToClient: List<String>,
        val languagesClientToServer: List<String>,
        val languagesServerToClient: List<String>,
        val firstKexPacketFollows: Boolean,
        val reserved: Long
    ) : SshMessage()
    
    object NewKeys : SshMessage()
    
    data class UserAuthRequest(
        val username: String,
        val serviceName: String,
        val methodName: String
    ) : SshMessage()
    
    object UserAuthSuccess : SshMessage()
    
    data class UserAuthFailure(
        val authentications: List<String>,
        val partialSuccess: Boolean
    ) : SshMessage()
    
    data class ChannelOpen(
        val channelType: String,
        val senderChannel: Long,
        val initialWindowSize: Long,
        val maximumPacketSize: Long
    ) : SshMessage()
    
    data class ChannelOpenConfirmation(
        val recipientChannel: Long,
        val senderChannel: Long,
        val initialWindowSize: Long,
        val maximumPacketSize: Long
    ) : SshMessage()
    
    data class ChannelOpenFailure(
        val recipientChannel: Long,
        val reasonCode: Int,
        val description: String,
        val languageTag: String
    ) : SshMessage()
    
    data class ChannelWindowAdjust(
        val recipientChannel: Long,
        val bytesToAdd: Long
    ) : SshMessage()
    
    data class ChannelData(
        val recipientChannel: Long,
        val data: String
    ) : SshMessage()
    
    data class ChannelExtendedData(
        val recipientChannel: Long,
        val dataTypeCode: Int,
        val data: String
    ) : SshMessage()
    
    data class ChannelEof(val recipientChannel: Long) : SshMessage()
    data class ChannelClose(val recipientChannel: Long) : SshMessage()
    
    data class ChannelRequest(
        val recipientChannel: Long,
        val requestType: String,
        val wantReply: Boolean
    ) : SshMessage()
    
    data class ChannelSuccess(val recipientChannel: Long) : SshMessage()
    data class ChannelFailure(val recipientChannel: Long) : SshMessage()
}

/**
 * SSH configuration for protocol adapter.
 */
data class SshConfig(
    val maxPacketSize: Int = 32768,
    val windowSize: Long = 2097152, // 2MB
    val maxChannels: Int = 100,
    val enableCompression: Boolean = false
)

/**
 * SSH channel open failure reasons.
 */
enum class SshChannelOpenFailureReason(val value: Int) {
    ADMINISTRATIVELY_PROHIBITED(1),
    CONNECT_FAILED(2),
    UNKNOWN_CHANNEL_TYPE(3),
    RESOURCE_SHORTAGE(4)
}

/**
 * SSH disconnect reasons.
 */
enum class SshDisconnectReason(val value: Int) {
    HOST_NOT_ALLOWED_TO_CONNECT(1),
    PROTOCOL_ERROR(2),
    KEY_EXCHANGE_FAILED(3),
    RESERVED(4),
    MAC_ERROR(5),
    COMPRESSION_ERROR(6),
    SERVICE_NOT_AVAILABLE(7),
    PROTOCOL_VERSION_NOT_SUPPORTED(8),
    HOST_KEY_NOT_VERIFIABLE(9),
    CONNECTION_LOST(10),
    BY_APPLICATION(11),
    TOO_MANY_CONNECTIONS(12),
    AUTH_CANCELLED_BY_USER(13),
    NO_MORE_AUTH_METHODS_AVAILABLE(14),
    ILLEGAL_USER_NAME(15)
}

/**
 * SSH disconnect handler interface.
 */
interface SshDisconnectHandler : CoroutineContext.Element {
    suspend fun handleDisconnect(message: SshMessage.Disconnect): SshMessage
    
    companion object Key : CoroutineContext.Key<SshDisconnectHandler>
    override val key: CoroutineContext.Key<*> = Key
}

/**
 * SSH key exchange handler interface.
 */
interface SshKexHandler : CoroutineContext.Element {
    suspend fun handleKexInit(message: SshMessage.KexInit): SshMessage
    
    companion object Key : CoroutineContext.Key<SshKexHandler>
    override val key: CoroutineContext.Key<*> = Key
}

/**
 * SSH authentication handler interface.
 */
interface SshAuthHandler : CoroutineContext.Element {
    suspend fun handleAuthRequest(message: SshMessage.UserAuthRequest): SshMessage
    
    companion object Key : CoroutineContext.Key<SshAuthHandler>
    override val key: CoroutineContext.Key<*> = Key
}

/**
 * SSH channel handler interface.
 */
interface SshChannelHandler : CoroutineContext.Element {
    suspend fun handleChannelOpen(message: SshMessage.ChannelOpen): SshMessage
    suspend fun handleChannelData(message: SshMessage.ChannelData): SshMessage
    
    companion object Key : CoroutineContext.Key<SshChannelHandler>
    override val key: CoroutineContext.Key<*> = Key
} 