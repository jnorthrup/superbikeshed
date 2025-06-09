package borg.trikeshed.net.http3

// HTTP/3 error codes per RFC 9114 Section 8.1
enum class Http3ErrorCode(val value: ULong) {
    H3_NO_ERROR(0x0100uL),
    H3_GENERAL_PROTOCOL_ERROR(0x0101uL),
    H3_INTERNAL_ERROR(0x0102uL),
    H3_STREAM_CREATION_ERROR(0x0103uL),
    H3_CLOSED_CRITICAL_STREAM(0x0104uL),
    H3_FRAME_UNEXPECTED(0x0105uL),
    H3_FRAME_ERROR(0x0106uL),
    H3_EXCESSIVE_LOAD(0x0107uL),
    H3_ID_ERROR(0x0108uL),
    H3_SETTINGS_ERROR(0x0109uL),
    H3_MISSING_SETTINGS(0x010AuL),
    H3_REQUEST_REJECTED(0x010BuL),
    H3_REQUEST_CANCELLED(0x010CuL),
    H3_REQUEST_INCOMPLETE(0x010DuL),
    H3_MESSAGE_ERROR(0x010EuL),
    H3_CONNECT_ERROR(0x010FuL),
    H3_VERSION_FALLBACK(0x0110uL);

    companion object {
        fun fromValue(value: ULong): Http3ErrorCode? = entries.find { it.value == value }
    }
}

// HTTP/3 frame types per RFC 9114 Section 7.2
enum class Http3FrameType(val value: ULong) {
    DATA(0x00uL),
    HEADERS(0x01uL),
    CANCEL_PUSH(0x03uL),
    SETTINGS(0x04uL),
    PUSH_PROMISE(0x05uL),
    GOAWAY(0x07uL),
    MAX_PUSH_ID(0x0DuL);

    companion object {
        fun fromValue(value: ULong): Http3FrameType? = entries.find { it.value == value }
    }
}

// HTTP/3 settings parameters per RFC 9114 Section 7.2.4
enum class Http3Setting(val id: ULong) {
    QPACK_MAX_TABLE_CAPACITY(0x01uL),
    MAX_FIELD_SECTION_SIZE(0x06uL),
    QPACK_BLOCKED_STREAMS(0x07uL);

    companion object {
        fun fromId(id: ULong): Http3Setting? = entries.find { it.id == id }
    }
}

// HTTP/3 stream types per RFC 9114 Section 6.2
enum class Http3StreamType(val value: ULong) {
    CONTROL(0x00uL),           // HTTP/3 control stream
    PUSH(0x01uL),              // Server push stream
    QPACK_ENCODER(0x02uL),     // QPACK encoder stream
    QPACK_DECODER(0x03uL);     // QPACK decoder stream

    companion object {
        fun fromValue(value: ULong): Http3StreamType? = entries.find { it.value == value }
    }
}
