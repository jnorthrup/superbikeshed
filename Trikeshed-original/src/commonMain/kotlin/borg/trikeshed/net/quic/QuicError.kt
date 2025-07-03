package borg.trikeshed.net.quic


import borg.trikeshed.lib.*

/**
 * Minimal placeholder QUIC error for compilation
 */
class QuicError(message: String) : Exception(message)

enum class QuicErrorCode(val code: Int) {
    NO_ERROR(0),
    INTERNAL_ERROR(1),
    CONNECTION_REFUSED(2),
    FLOW_CONTROL_ERROR(3),
    STREAM_LIMIT_ERROR(4),
    STREAM_STATE_ERROR(5),
    FINAL_SIZE_ERROR(6),
    FRAME_ENCODING_ERROR(7),
    TRANSPORT_PARAMETER_ERROR(8),
    CONNECTION_ID_LIMIT_ERROR(9),
    PROTOCOL_VIOLATION(10),
    INVALID_TOKEN(11),
    APPLICATION_ERROR(12),
    CRYPTO_BUFFER_EXCEEDED(13),
    KEY_UPDATE_ERROR(14),
    AEAD_LIMIT_REACHED(15),
    NO_VIABLE_PATH(16)
} 