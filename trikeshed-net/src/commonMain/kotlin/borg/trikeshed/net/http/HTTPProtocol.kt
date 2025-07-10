package borg.trikeshed.net.http

/**
 * HTTP Protocol stub for trikeshed-ccek compatibility
 */
enum class HTTPProtocol {
    HTTP_0_9,
    HTTP_1_0,
    HTTP_1_1,
    HTTP_2_0,
    HTTP_3_0
}

/**
 * HTTP Response stub
 */
data class HTTPResponse(
    val statusCode: Int,
    val headers: Map<String, String>,
    val body: ByteArray
) 