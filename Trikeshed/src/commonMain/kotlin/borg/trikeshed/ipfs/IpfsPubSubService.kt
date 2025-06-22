package borg.trikeshed.ipfs

import kotlinx.coroutines.Job

interface IpfsPubSubService {
    suspend fun publish(topic: String, data: String)
    suspend fun subscribe(topic: String, handler: (String) -> Unit): Job?
    fun unsubscribe(topic: String)
} 