package borg.trikeshed.core

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.coroutines.CoroutineContext

actual class SerializationService : CoroutineContext.Element {
    actual override val key: CoroutineContext.Key<*> = SerializationService.Key

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    actual fun <T> serializeSeries(series: Series<T>): String {
        val data = series.▶.toList()
        return json.encodeToString(data)
    }

    actual fun <T> deserializeSeries(json: String): Series<T> {
        val data = this.json.decodeFromString<List<T>>(json)
        return data.toSeries()
    }

    actual fun <A, B> serializeJoin(join: Join<A, B>): String {
        return json.encodeToString(join)
    }

    actual fun <A, B> deserializeJoin(json: String): Join<A, B> {
        return this.json.decodeFromString(json)
    }
} 