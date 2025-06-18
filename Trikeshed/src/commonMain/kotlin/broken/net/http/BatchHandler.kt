package borg.trikeshed.net.http

import borg.trikeshed.lib.*
import borg.trikeshed.parse.json.*
import kotlin.jvm.JvmInline

/**
 * Creates an HttpHandler that processes batched API requests.
 * This is the server-side counterpart to BatchClient.
 */
fun createBatchHandler(dealService: DealService): HttpHandler = { request ->
    if (request.method != HttpMethod.POST || request.path.value != "/api/batch") {
        HttpResponse(HttpStatusCode(405), HttpReasonPhrase("Method Not Allowed"))
    } else {
        try {
            // Parse the batch request body
            val requestBody = request.body.▶.toByteArray().decodeToString()
            val commands = JsonParser.parseCommands(requestBody)
            
            // Process each command and collect responses
            val responses = commands.α { command ->
                when (command.type) {
                    BatchClient.CommandType.FindDeal -> processFindDeal(command, dealService)
                    BatchClient.CommandType.FindDealsByProduct -> processFindDealsByProduct(command, dealService)
                    BatchClient.CommandType.PersistDeal -> processPersistDeal(command, dealService)
                    BatchClient.CommandType.GetVendors -> processGetVendors(command, dealService)
                    BatchClient.CommandType.Noop -> BatchClient.Response(true, "", null)
                }
            }

            // Serialize responses
            val responseBody = JsonSerializer.serializeResponses(responses)

            HttpResponse(
                status = HttpStatusCode(200),
                reasonPhrase = HttpReasonPhrase("OK"),
                headers = 2 j { i -> when(i) {
                    0 -> HttpHeaderName("Content-Type") j HttpHeaderValue("application/json")
                    1 -> HttpHeaderName("Content-Length") j HttpHeaderValue(responseBody.length.toString())
                    else -> throw IndexOutOfBoundsException()
                }},
                body = responseBody.encodeToByteArray().toSeries()
            )
        } catch (e: Exception) {
            HttpResponse(
                status = HttpStatusCode(400),
                reasonPhrase = HttpReasonPhrase("Bad Request"),
                headers = 1 j { HttpHeaderName("Content-Type") j HttpHeaderValue("application/json") },
                body = """{"error":"Invalid batch request: ${e.message}"}""".encodeToByteArray().toSeries()
            )
        }
    }
}

private suspend fun processFindDeal(
    command: BatchClient.Command,
    service: DealService
): BatchClient.Response {
    val id = command.payload
    return try {
        val deal = service.findDeal(id)
        if (deal != null) {
            BatchClient.Response(
                success = true,
                data = JsonSerializer.serializeDeal(deal),
                error = null
            )
        } else {
            BatchClient.Response(
                success = false,
                data = "",
                error = "Deal not found: $id"
            )
        }
    } catch (e: Exception) {
        BatchClient.Response(
            success = false,
            data = "",
            error = "Error finding deal: ${e.message}"
        )
    }
}

private suspend fun processFindDealsByProduct(
    command: BatchClient.Command,
    service: DealService
): BatchClient.Response {
    return try {
        val deals = service.findDealsByProduct(command.payload)
        BatchClient.Response(
            success = true,
            data = JsonSerializer.serializeDeals(deals),
            error = null
        )
    } catch (e: Exception) {
        BatchClient.Response(
            success = false,
            data = "",
            error = "Error finding deals: ${e.message}"
        )
    }
}

private suspend fun processPersistDeal(
    command: BatchClient.Command,
    service: DealService
): BatchClient.Response {
    return try {
        val deal = JsonParser.parseDeal(command.payload) 
            ?: return BatchClient.Response(false, "", "Invalid deal data")
            
        val result = service.persistDeal(deal)
        BatchClient.Response(
            success = true,
            data = JsonSerializer.serializeCouchTx(result),
            error = null
        )
    } catch (e: Exception) {
        BatchClient.Response(
            success = false,
            data = "",
            error = "Error persisting deal: ${e.message}"
        )
    }
}

private suspend fun processGetVendors(
    command: BatchClient.Command,
    service: DealService
): BatchClient.Response {
    return try {
        val vendors = service.getVendors()
        BatchClient.Response(
            success = true,
            data = JsonSerializer.serializeVendors(vendors),
            error = null
        )
    } catch (e: Exception) {
        BatchClient.Response(
            success = false,
            data = "",
            error = "Error getting vendors: ${e.message}"
        )
    }
}

/**
 * The service interface that the batch handler delegates to.
 * This would be implemented by the actual business logic layer.
 */
interface DealService {
    suspend fun findDeal(id: String): DealProxy?
    suspend fun findDealsByProduct(query: String): Series<DealProxy>
    suspend fun persistDeal(deal: DealProxy): CouchTxProxy
    suspend fun getVendors(): Series<VendorProxy>
}

@JvmInline
value class VendorProxy(private val fields: Join<String, String>) {
    val id: String get() = fields.a
    val name: String get() = fields.b
}

@JvmInline 
value class CouchTxProxy(private val fields: Join<String, Map<String, String>>) {
    val ok: Boolean get() = fields.b["ok"] == "true"
    val id: String get() = fields.a
    val rev: String get() = fields.b["rev"] ?: ""
    val error: String? get() = fields.b["error"]
    val reason: String? get() = fields.b["reason"]
} 