package borg.trikeshed.net.http.indexing

import borg.trikeshed.lib.Series
import borg.trikeshed.lib.emptySeries
import borg.trikeshed.lib.j
import borg.trikeshed.lib.α
import borg.trikeshed.lib.`▶`
import borg.trikeshed.net.http.types.*

/**
 * Fast header lookup using relaxfactory's stateless indexing pattern.
 * Pre-builds index of requested headers to avoid linear scanning.
 * 
 * Implements RFC 7230 Section 3.2 Header Fields processing with O(1) lookups
 * and RFC 6265 Section 4.2 Cookie header parsing optimization.
 */

fun HttpHeaders.buildIndex(pattern: HeaderRequestPattern): HeaderIndex {
    val positions = pattern.α { headerName ->
        // Find position of this header in the cursor
        var foundPos = -1
        for (r in 0 until this.cursor.rows) {
            val headerLine = this.cursor[r, 0]
            if (headerLine.startsWith("${headerName.value}:", ignoreCase = true)) {
                foundPos = r
                break
            }
        }
        foundPos
    }.▶ // Materialize positions series
    
    return HeaderIndex(positions)
}

fun HttpHeaders.lookup(headerName: HttpHeaderName, index: HeaderIndex, pattern: HeaderRequestPattern): HttpHeaderValue? {
    // Find the header in our pattern
    val patternIndex = pattern.α { it.value == headerName.value }.▶.indexOf(true)
    if (patternIndex == -1 || patternIndex >= index.keyPositions.size) return null
    
    val position = index.keyPositions[patternIndex]
    if (position == -1) return null
    
    val headerLine = this.cursor[position, 0]
    val colonIndex = headerLine.indexOf(':')
    return if (colonIndex > 0) {
        HttpHeaderValue(headerLine.substring(colonIndex + 1).trim())
    } else null
}

fun HttpHeaders.buildCookieIndex(pattern: CookieRequestPattern): CookieIndex {
    val cookieHeaderValue = this.lookup(HttpHeaderName("Cookie"), buildIndex(borg.trikeshed.lib.j(1) { HttpHeaderName("Cookie") }), borg.trikeshed.lib.j(1) { HttpHeaderName("Cookie") })
        ?: return CookieIndex(emptySeries())
    
    val positions = pattern.α { cookieName ->
        val cookieString = cookieHeaderValue.value
        val startIndex = cookieString.indexOf("$cookieName=")
        if (startIndex == -1) -1 else startIndex
    }.▶
    
    return CookieIndex(positions)
}

fun HttpHeaders.lookupCookie(cookieName: CookieName, index: CookieIndex, pattern: CookieRequestPattern): ParsedCookie? {
    val cookieHeaderValue = this.lookup(HttpHeaderName("Cookie"), buildIndex(borg.trikeshed.lib.j(1) { HttpHeaderName("Cookie") }), borg.trikeshed.lib.j(1) { HttpHeaderName("Cookie") })
        ?: return null
    
    val patternIndex = pattern.α { it == cookieName.value }.▶.indexOf(true)
    if (patternIndex == -1 || patternIndex >= index.cookiePositions.size) return null
    
    val position = index.cookiePositions[patternIndex]
    if (position == -1) return null
    
    val cookieString = cookieHeaderValue.value
    val startPos = position + cookieName.value.length + 1 // Skip "name="
    val endPos = cookieString.indexOf(';', startPos).takeIf { it != -1 } ?: cookieString.length
    
    val value = cookieString.substring(startPos, endPos).trim()
    
    // Extract attributes after semicolon
    val attributes = if (endPos < cookieString.length) {
        cookieString.substring(endPos + 1).trim()
    } else ""
    
    return ParsedCookie(
        name = cookieName,
        value = CookieValue(value),
        attributes = CookieAttributes(attributes)
    )
}

// Convenience functions for common patterns
object CommonHeaders {
    val BASIC_REQUEST = borg.trikeshed.lib.j(4) { i ->
        when (i) {
            0 -> HttpHeaderName("Content-Type")
            1 -> HttpHeaderName("Content-Length") 
            2 -> HttpHeaderName("Authorization")
            3 -> HttpHeaderName("Cookie")
            else -> HttpHeaderName("Unknown")
        }
    }
    
    val BASIC_RESPONSE = borg.trikeshed.lib.j(3) { i ->
        when (i) {
            0 -> HttpHeaderName("Content-Type")
            1 -> HttpHeaderName("Content-Length")
            2 -> HttpHeaderName("Set-Cookie")
            else -> HttpHeaderName("Unknown")
        }
    }
}

object CommonCookies {
    val SESSION_COOKIES = borg.trikeshed.lib.j(3) { i ->
        when (i) {
            0 -> "sessionid"
            1 -> "csrftoken"
            2 -> "auth_token"
            else -> "unknown"
        }
    }
    
    val ANALYTICS_COOKIES = borg.trikeshed.lib.j(2) { i ->
        when (i) {
            0 -> "_ga"
            1 -> "_gid"
            else -> "unknown"
        }
    }
}

// Extension to add indexOf to Series<Boolean>
private fun Series<Boolean>.indexOf(value: Boolean): Int {
    for (i in 0 until this.size) {
        if (this[i] == value) return i
    }
    return -1
}