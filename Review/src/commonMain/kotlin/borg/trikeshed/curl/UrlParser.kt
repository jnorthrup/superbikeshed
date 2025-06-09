package borg.trikeshed.curl

import borg.trikeshed.net.http.QuicCurlException // Assuming this path

data class ParsedUrl(
    val scheme: String,
    val host: String,
    val port: Int,
    val path: String, // Should always start with "/" if not empty
    val query: String?
)

/**
 * Parses a URL string into its components.
 * This is a simplified parser focusing on "https" scheme and basic structure.
 *
 * @param urlString The URL string to parse.
 * @return A Result containing [ParsedUrl] on success, or [QuicCurlException] on failure.
 */
fun parseUrl(urlString: String): Result<ParsedUrl> {
    try {
        // Basic scheme check
        val schemeSeparator = "://"
        val schemeEndIndex = urlString.indexOf(schemeSeparator)
        if (schemeEndIndex == -1) {
            return Result.failure(QuicCurlException("Invalid URL: Missing scheme separator '://'"))
        }
        val scheme = urlString.substring(0, schemeEndIndex)
        if (scheme.lowercase() != "https" && scheme.lowercase() != "http") { // Allow http for parsing, but later check for https for H3
            return Result.failure(QuicCurlException("Invalid scheme: '$scheme'. Only 'http' or 'https' supported for parsing."))
        }

        val rest = urlString.substring(schemeEndIndex + schemeSeparator.length)

        // Host, Port, Path, Query
        val pathSeparator = "/"
        val querySeparator = "?"

        val authorityAndPath: String
        var query: String? = null

        val queryIndex = rest.indexOf(querySeparator)
        if (queryIndex != -1) {
            authorityAndPath = rest.substring(0, queryIndex)
            if (queryIndex + 1 < rest.length) {
                query = rest.substring(queryIndex + 1)
            }
        } else {
            authorityAndPath = rest
        }

        val pathIndex = authorityAndPath.indexOf(pathSeparator)
        val authority: String
        val path: String

        if (pathIndex != -1) {
            authority = authorityAndPath.substring(0, pathIndex)
            path = authorityAndPath.substring(pathIndex) // Path includes the leading "/"
        } else {
            // No path component found, means it's just authority, path is "/"
            authority = authorityAndPath
            path = "/"
        }

        if (authority.isEmpty()) {
            return Result.failure(QuicCurlException("Invalid URL: Missing authority (host/port)."))
        }

        // Host and Port from Authority
        val host: String
        val port: Int

        val portSeparator = ":"
        val portIndex = authority.lastIndexOf(portSeparator) // Use lastIndexOf in case IPv6 address with colons

        if (portIndex != -1 && authority.indexOf(']') < portIndex) { // Ensure ':' is for port, not part of IPv6
            host = authority.substring(0, portIndex)
            val portString = authority.substring(portIndex + 1)
            if (portString.isEmpty() || !portString.all { it.isDigit() }) {
                return Result.failure(QuicCurlException("Invalid port number: '$portString'"))
            }
            port = portString.toInt()
            if (port <= 0 || port > 65535) {
                 return Result.failure(QuicCurlException("Port number out of range: $port"))
            }
        } else {
            host = authority
            port = if (scheme.lowercase() == "https") 443 else 80 // Default port
        }

        if (host.isEmpty()) {
             return Result.failure(QuicCurlException("Invalid URL: Host cannot be empty."))
        }
        // Basic validation for host (very simple, not RFC compliant)
        if (host.startsWith("[") && !host.endsWith("]")) return Result.failure(QuicCurlException("Invalid IPv6 host format."))
        if (!host.startsWith("[") && host.contains(":")) {
            // Might be an attempt at IPv6 without brackets, or invalid char.
            // This simple parser won't handle all valid hostnames.
        }


        return Result.success(ParsedUrl(scheme, host, port, if (path.isEmpty()) "/" else path, query))

    } catch (e: Exception) {
        return Result.failure(QuicCurlException("Failed to parse URL: '$urlString'. Error: ${e.message}", cause = e))
    }
}
