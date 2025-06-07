package borg.trikeshed.net.http3

import borg.trikeshed.core.Join
import borg.trikeshed.core.Series // Assuming Series might be used by some of these typealiases if they were more complex classes

// Import all from the new HttpTypes.kt
import borg.trikeshed.net.http.HttpHeader
import borg.trikeshed.net.http.HttpHeaders
import borg.trikeshed.net.http.HttpMethod
import borg.trikeshed.net.http.HttpReasonPhrase
import borg.trikeshed.net.http.HttpRequestLine
import borg.trikeshed.net.http.HttpRequestPath
import borg.trikeshed.net.http.HttpResponseLine
import borg.trikeshed.net.http.HttpScheme
import borg.trikeshed.net.http.HttpStatusCode

// Import the QuicConnectionStateEnum
import borg.trikeshed.net.quic.QuicConnectionStateEnum

// Import enums from the sibling Http3Enums.kt file in this package
import borg.trikeshed.net.http3.Http3ErrorCode
import borg.trikeshed.net.http3.Http3FrameType
import borg.trikeshed.net.http3.Http3Setting
import borg.trikeshed.net.http3.Http3StreamType
// Note: Http3.kt content did not directly use Http3StreamType or Http3ErrorCode in its typealiases,
// but Http3FrameType and Http3Setting were part of other typealiases.
// Http3ApplicationErrorCode is distinct from Http3ErrorCode enum.

// HTTP/3 error handling
typealias Http3ApplicationErrorCode = ULong   // Application error codes
typealias Http3BlockedStreams = ULong         // Maximum blocked streams
typealias Http3ConnectionFlowState = Join<Http3ConnectionId, Join<ULong, ULong>> // conn_id, sent, window
// === HTTP/3 PROTOCOL ONTOLOGY ===
// Core HTTP/3 identifiers following RFC 9114
typealias Http3ConnectionId = ULong           // HTTP/3 connection identifier
// HTTP/3 connection composition
typealias Http3ConnectionState = Join<Http3ConnectionId, Join<QuicConnectionStateEnum, Http3SettingsBundle>>
typealias Http3DebugData = ByteArray          // Additional debug information
// HTTP/3 error composition
typealias Http3Error = Join<Http3ApplicationErrorCode, Join<HttpReasonPhrase, Http3DebugData>>
typealias Http3Frame = Join<Http3FrameHeader, ByteArray>
// HTTP/3 frame composition
typealias Http3FrameHeader = Join<Http3FrameType, Http3FrameLength>
// HTTP/3 frame structure
typealias Http3FrameLength = ULong            // Variable-length frame size
typealias Http3HeaderBlockSize = ULong        // Compressed header block size
typealias Http3MaxFieldSize = ULong           // Maximum header field size
typealias Http3MaxTableCapacity = ULong       // QPACK table capacity limit
typealias Http3PayloadSize = ULong            // Frame payload size
typealias Http3PushId = ULong                 // Server push identifier
// typealias Http3ReasonPhrase = String // This is imported from borg.trikeshed.net.http.HttpReasonPhrase
// Complete HTTP message compositions
typealias Http3Request = Join<HttpRequestLine, Join<HttpHeaders, ByteArray>>
typealias Http3RequestId = ULong              // Request tracking identifier
typealias Http3Response = Join<HttpResponseLine, Join<HttpHeaders, ByteArray>>
typealias Http3SettingsBundle = Join<Http3MaxTableCapacity, Join<Http3MaxFieldSize, Http3BlockedStreams>>
// HTTP/3 settings composition
typealias Http3SettingsFrame = Join<Http3Setting, Http3SettingsValue>
// HTTP/3 settings and parameters
typealias Http3SettingsValue = ULong          // Settings parameter value
// HTTP/3 flow control compositions
typealias Http3StreamFlowState = Join<Http3StreamId, Join<ULong, ULong>>
typealias Http3StreamId = ULong               // HTTP/3 stream identifier
typealias Http3StreamState = Join<Http3StreamId, Join<Http3Request, Boolean>>
