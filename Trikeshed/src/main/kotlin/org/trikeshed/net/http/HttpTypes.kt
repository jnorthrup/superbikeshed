package org.trikeshed.net.http

import java.net.URL
import kotlinx.serialization.Serializable

@Serializable
data class HttpRequest(
    val url: String,
    val body: Any
)

@Serializable
data class HttpResponse(
    val statusCode: Int,
    val body: Any
) 