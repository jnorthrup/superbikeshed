package org.example.trikeshedminimaljson

import kotlinx.serialization.Serializable // This should be available from commonMain dependency

// Make sure DealProxySerializer and VendorProxySerializer are resolvable or defined soon
// For now, let's assume they will be in the same package in JsonSerialization.kt

@Serializable(with = DealProxySerializer::class)
data class DealProxy(
    val id: String = "",
    val product: String,
    val vendor: String,
    val price: Double,
    val quantity: Int
)

@Serializable(with = VendorProxySerializer::class)
data class VendorProxy(
    val id: String = "",
    val name: String
)

@Serializable
data class CouchTxProxy(
    val id: String,
    val ok: Boolean,
    val rev: String? = null,
    val error: String? = null,
    val reason: String? = null
)
