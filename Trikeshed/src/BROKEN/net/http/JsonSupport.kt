package borg.trikeshed.net.http

import borg.trikeshed.lib.*
import borg.trikeshed.parse.json.*

/**
 * JSON serialization support for BatchClient using TrikeShed's native JSON parser.
 */
object JsonSerializer {
    fun serializeCommands(commands: Series<BatchClient.Command>): String {
        val sb = StringBuilder()
        sb.append("[")
        commands.▶.forEachIndexed { index, cmd ->
            if (index > 0) sb.append(",")
            sb.append("""{
                "type":"${cmd.type}",
                "path":"${cmd.path}",
                "payload":"${cmd.payload}"
            }""".trimIndent())
        }
        sb.append("]")
        return sb.toString()
    }
}

object JsonParser {
    fun parseResponses(json: String): Series<BatchClient.Response> {
        val context = parseJson(json.toSeries())
        return context.size j { index ->
            val obj = context[index]
            BatchClient.Response(
                success = getBooleanField(obj, "success"),
                data = getStringField(obj, "data"),
                error = getStringFieldOrNull(obj, "error")
            )
        }
    }

    fun parseDeal(json: String): DealProxy? {
        val context = parseJson(json.toSeries())
        if (context.size == 0) return null
        
        return DealProxy(
            id = getStringField(context[0], "id"),
            version = getStringField(context[0], "version"),
            product = getStringField(context[0], "product"),
            productDescription = getStringField(context[0], "productDescription"),
            vendorId = getStringFieldOrNull(context[0], "vendorId"),
            npoId = getStringFieldOrNull(context[0], "npoId")
        )
    }

    fun parseDeals(json: String): Series<DealProxy> {
        val context = parseJson(json.toSeries())
        return context.size j { index ->
            val obj = context[index]
            DealProxy(
                id = getStringField(obj, "id"),
                version = getStringField(obj, "version"),
                product = getStringField(obj, "product"),
                productDescription = getStringField(obj, "productDescription"),
                vendorId = getStringFieldOrNull(obj, "vendorId"),
                npoId = getStringFieldOrNull(obj, "npoId")
            )
        }
    }

    private fun getBooleanField(element: JsElement, field: String): Boolean {
        val value = getStringField(element, field)
        return value == "true"
    }

    private fun getStringField(element: JsElement, field: String): String {
        // Implementation using TrikeShed's JSON parser
        // This would navigate the JsElement structure to find the field value
        TODO("Implement using TrikeShed's native JSON parser")
    }

    private fun getStringFieldOrNull(element: JsElement, field: String): String? {
        return try {
            getStringField(element, field)
        } catch (e: Exception) {
            null
        }
    }
}

@JvmInline
value class DealProxy(private val fields: Join<String, Map<String, String>>) {
    val id: String get() = fields.a
    val version: String get() = fields.b["version"] ?: ""
    val product: String get() = fields.b["product"] ?: ""
    val productDescription: String get() = fields.b["productDescription"] ?: ""
    val vendorId: String? get() = fields.b["vendorId"]
    val npoId: String? get() = fields.b["npoId"]

    constructor(
        id: String,
        version: String,
        product: String,
        productDescription: String,
        vendorId: String? = null,
        npoId: String? = null
    ) : this(id j mapOf(
        "version" to version,
        "product" to product,
        "productDescription" to productDescription,
        "vendorId" to (vendorId ?: ""),
        "npoId" to (npoId ?: "")
    ))
} 