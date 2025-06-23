package borg.trikeshed.reactor.http

import borg.trikeshed.io.PlatformFile
import borg.trikeshed.lib.*
import borg.trikeshed.net.http.*
import borg.trikeshed.services.RequestFactoryService

/**
 * Creates an HttpHandler dedicated to processing GWT RequestFactory RPC calls.
 * Uses our native RequestFactoryService implementation for processing.
 */
fun createRequestFactoryHandler(service: RequestFactoryService? = null): HttpHandler {
    return { request ->
        // The request body is already fully read by the connection handler.
        val payload = request.body

        // Delegate the actual processing to our RequestFactoryService
        val responsePayload = service?.process(payload) ?: "mock_response".toByteArray()
        val responseIndexed = responsePayload.toIdx()

        // Construct a valid HTTP response.
        HttpResponse(
            status = HttpStatusCode(200),
            reasonPhrase = HttpReasonPhrase("OK"),
            headers = 2 j { i:Int ->
                when (i) {
                    0 -> HttpHeaderName("Content-Type") j HttpHeaderValue("application/json; charset=utf-8")
                    1 -> HttpHeaderName("Content-Length") j HttpHeaderValue(responseIndexed.size.toString())
                    else -> throw IndexOutOfBoundsException()
                }
            },
            body = responseIndexed
        )
    }
}

// createGwtRpcHandler would be implemented similarly if needed 