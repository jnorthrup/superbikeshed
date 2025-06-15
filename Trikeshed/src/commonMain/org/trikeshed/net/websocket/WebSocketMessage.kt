package org.trikeshed.net.websocket

import kotlinx.serialization.Serializable

@Serializable
data class WebSocketMessage(
    val content: Any
) 