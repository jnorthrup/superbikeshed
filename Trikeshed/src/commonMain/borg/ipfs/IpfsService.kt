package borg.ipfs

import borg.trikeshed.lib.ByteSeries
import kotlinx.serialization.Serializable

interface IpfsService {
    suspend fun add(data: ByteSeries): String
    suspend fun get(cid: String): ByteSeries?
    suspend fun pin(cid: String): Boolean
    suspend fun unpin(cid: String): Boolean
    suspend fun ls(cid: String): List<IpfsObject>
    suspend fun publish(topic: String, data: ByteSeries): Boolean
    suspend fun subscribe(topic: String, callback: (ByteSeries) -> Unit)
}

@Serializable
data class IpfsObject(
    val cid: String,
    val size: Long,
    val type: String,
    val links: List<IpfsLink> = emptyList()
)

@Serializable
data class IpfsLink(
    val name: String,
    val cid: String,
    val size: Long
)

@Serializable
data class IpfsConfig(
    val host: String,
    val port: Int,
    val apiPort: Int,
    val useSSL: Boolean = false
) 