package borg.trikeshed.net.http

import borg.trikeshed.nio.PlatformByteBuffer
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.Join

/**
 * Stub interface for HTTP parsing functionality
 * TODO: Implement proper HTTP parsing
 */
interface HttpParser {
    fun parse(buffer: PlatformByteBuffer): HttpMessage?
}

/**
 * Stub interface for HTTP message
 * TODO: Implement proper HTTP message structure
 */
interface HttpMessage {
    val startLine: HttpRequestLine
    val headerFields: Indexed<Join<String, String>>
    val messageBody: String
}

/**
 * Stub interface for HTTP request line
 * TODO: Implement proper HTTP request line structure
 */
interface HttpRequestLine {
    val method: String
    val requestTarget: String
    val httpVersion: String
}

 