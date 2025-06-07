package evolution

import borg.trikeshed.lib.Join
import kotlin.jvm.JvmInline

// --- HTTP/3 Strong Types (Value Classes) ---
// These are candidates for moving to a dedicated Http3SpecTypes.kt file

@JvmInline
value class Http3ErrorCode(val value: ULong) // Replaces Http3ApplicationErrorCode

// Internal application-specific ID for an HTTP/3 connection concept, if not directly using QUIC's ConnectionID
@JvmInline
value class AppHttp3ConnectionId(val value: ULong) // Replaces Http3ConnectionId

@JvmInline
value class Http3FrameType(val value: ULong) // HTTP/3 frame types are VarInts

@JvmInline
value class Http3FrameLength(val value: ULong)

@JvmInline
value class Http3HeaderBlockSize(val value: ULong)

@JvmInline
value class Http3MaxFieldSize(val value: ULong) // Corresponds to SETTINGS_MAX_FIELD_SECTION_SIZE

@JvmInline
value class Http3MaxTableCapacity(val value: ULong) // QPACK related

@JvmInline
value class Http3BlockedStreams(val value: ULong) // Corresponds to SETTINGS_MAX_PUSH_ID (if server) or conceptually for stream concurrency

@JvmInline
value class Http3PayloadSize(val value: ULong)

@JvmInline
value class Http3PushId(val value: ULong)

@JvmInline
value class Http3ReasonPhrase(val value: String)

@JvmInline
value class Http3DebugData(val data: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as Http3DebugData
        return data.contentEquals(other.data)
    }
    override fun hashCode(): Int = data.contentHashCode()
}

@JvmInline
value class Http3RequestId(val value: ULong) // Application-specific request tracking

// Http3SettingId could represent the identifier for a setting (e.g., SETTINGS_MAX_FIELD_SECTION_SIZE ID itself)
@JvmInline
value class Http3SettingId(val id: ULong) // Replaces Http3Setting if it was just an ID

@JvmInline
value class Http3SettingsValue(val value: ULong)


// --- Original Aliases (Now Refactored or Using New Types) ---

// Typealias for HTTP/3 Stream ID, now using StreamID from QuicSpecTypes.kt (assuming it's ULong based)
// No, Http3.kt uses its own Http3StreamId = ULong, which is fine. QuicSpecTypes.StreamID is also ULong.
// For clarity and potential future divergence, we can make a specific Http3StreamId value class.
// However, the prompt asked to use existing QuicSpecTypes if applicable. StreamID is applicable.
// typealias Http3StreamId = ULong // Will use evolution.StreamID directly

// Renamed typealiases that are now value classes or use value classes:
// typealias Http3ApplicationErrorCode = ULong // Now Http3ErrorCode
// typealias Http3ConnectionId = ULong       // Now AppHttp3ConnectionId
// typealias Http3FrameLength = ULong        // Now value class Http3FrameLength
// typealias Http3HeaderBlockSize = ULong    // Now value class Http3HeaderBlockSize
// typealias Http3MaxFieldSize = ULong       // Now value class Http3MaxFieldSize
// typealias Http3MaxTableCapacity = ULong   // Now value class Http3MaxTableCapacity
// typealias Http3BlockedStreams = ULong     // Now value class Http3BlockedStreams
// typealias Http3PayloadSize = ULong        // Now value class Http3PayloadSize
// typealias Http3PushId = ULong             // Now value class Http3PushId
// typealias Http3ReasonPhrase = String      // Now value class Http3ReasonPhrase
// typealias Http3DebugData = ByteArray      // Now value class Http3DebugData
// typealias Http3RequestId = ULong          // Now value class Http3RequestId
// typealias Http3SettingsValue = ULong      // Now value class Http3SettingsValue

// --- Compositions using Join and new strong types ---

// Assuming the inner ULongs represent sent bytes and window size respectively.
// These could also become value classes like SentBytes(val value: ULong), WindowSizeValue(val value: ULong)
typealias Http3ConnectionFlowState = Join<AppHttp3ConnectionId, Join<ULong, ULong>>

// QuicConnectionState is defined in QuicConnectionState.kt (enum)
typealias Http3ConnectionState = Join<AppHttp3ConnectionId, Join<QuicConnectionState, Http3SettingsBundle>>

typealias Http3Error = Join<Http3ErrorCode, Join<Http3ReasonPhrase, Http3DebugData>>

// HttpHeaders, HttpRequestLine, HttpResponseLine are assumed to be defined elsewhere (e.g. Http.kt)
// and potentially refactored separately.
typealias Http3Frame = Join<Http3FrameHeader, ByteArray> // ByteArray is the raw frame payload
typealias Http3FrameHeader = Join<Http3FrameType, Http3FrameLength>

typealias Http3Request = Join<HttpRequestLine, Join<HttpHeaders, ByteArray>> // ByteArray is request body
typealias Http3Response = Join<HttpResponseLine, Join<HttpHeaders, ByteArray>> // ByteArray is response body

typealias Http3SettingsBundle = Join<Http3MaxTableCapacity, Join<Http3MaxFieldSize, Http3BlockedStreams>>
typealias Http3SettingsFrame = Join<Http3SettingId, Http3SettingsValue> // Http3Setting replaced by Http3SettingId

// Using StreamID from QuicSpecTypes.kt (evolution.StreamID) for HTTP/3 streams
// The inner ULongs represent offset and window size for stream-level flow control.
typealias Http3StreamFlowState = Join<StreamID, Join<ULong, ULong>>
typealias Http3StreamState = Join<StreamID, Join<Http3Request, Boolean>> // Boolean for 'completed' status