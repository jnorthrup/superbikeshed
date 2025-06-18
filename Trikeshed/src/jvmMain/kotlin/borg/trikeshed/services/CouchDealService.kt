package borg.trikeshed.services

import borg.trikeshed.lib.*
import borg.trikeshed.net.http.*
import borg.trikeshed.isam.CouchConnection
import borg.trikeshed.parse.json.*

/**
 * CouchDB-backed implementation of DealService.
 * Uses TrikeShed's native HTTP client and ISAM storage.
 */
class CouchDealService(
    private val couchHost: String = "localhost",
    private val couchPort: Int = 5984,
    private val dbName: String = "deals"
) : DealService {
    private val connection = CouchConnection(
        host = couchHost,
        port = couchPort,
        database = dbName
    )

    override suspend fun findDeal(id: String): DealProxy? {
        val request = HttpRequest(
            method = HttpMethod.GET,
            path = HttpRequestPath("/$dbName/$id"),
            headers = Series(listOf(
                Join(HttpHeaderName("Accept"), HttpHeaderValue("application/json"))
            )),
            body = Series(listOf<Byte>()),
            version = HttpVersion("HTTP/1.1")
        )

        return try {
            val response = request.send()
            if (response.status.value == 200) {
                val bytes = response.body.`play`.toList().toByteArray()
                CouchJsonParser.parseDeal(bytes.decodeToString())
            } else {
                null
            }
        } catch (e: Exception) {
            println("Error finding deal: ${e.message}")
            null
        }
    }

    override suspend fun findDealsByProduct(query: String): Series<DealProxy> {
        // CouchDB view query for product search
        val viewRequest = HttpRequest(
            method = HttpMethod.GET,
            path = HttpRequestPath("/$dbName/_design/deals/_view/by_product?key=\"$query\""),
            headers = Series(listOf(
                Join(HttpHeaderName("Accept"), HttpHeaderValue("application/json"))
            )),
            body = Series(listOf<Byte>()),
            version = HttpVersion("HTTP/1.1")
        )

        return try {
            val response = viewRequest.send()
            if (response.status.value == 200) {
                val bytes = response.body.`play`.toList().toByteArray()
                val results = CouchJsonParser.parseViewResults(bytes.decodeToString())
                Series(results)
            } else {
                Series(emptyList())
            }
        } catch (e: Exception) {
            println("Error searching deals: ${e.message}")
            Series(emptyList())
        }
    }

    override suspend fun persistDeal(deal: DealProxy): CouchTxProxy {
        val docId = deal.id.takeIf { it.isNotEmpty() } ?: generateDocId()
        val json = JsonSerializer.serializeDeal(deal)
        
        val request = HttpRequest(
            method = if (deal.id.isEmpty()) HttpMethod.POST else HttpMethod.PUT,
            path = HttpRequestPath("/$dbName/${if (deal.id.isEmpty()) "" else docId}"),
            headers = Series(listOf(
                Join(HttpHeaderName("Content-Type"), HttpHeaderValue("application/json")),
                Join(HttpHeaderName("Accept"), HttpHeaderValue("application/json"))
            )),
            body = Series(json.encodeToByteArray().toList()),
            version = HttpVersion("HTTP/1.1")
        )

        return try {
            val response = request.send()
            if (response.status.value in 200..201) {
                val bytes = response.body.`play`.toList().toByteArray()
                val result = CouchJsonParser.parseCouchResponse(bytes.decodeToString())
                CouchTxProxy(Join(result.id, mapOf(
                    "ok" to "true",
                    "rev" to result.rev
                )))
            } else {
                CouchTxProxy(Join(docId, mapOf(
                    "ok" to "false",
                    "error" to "HTTP ${response.status.value}",
                    "reason" to response.reasonPhrase.value
                )))
            }
        } catch (e: Exception) {
            CouchTxProxy(Join(docId, mapOf(
                "ok" to "false",
                "error" to "Exception",
                "reason" to e.message.orEmpty()
            )))
        }
    }

    override suspend fun getVendors(): Series<VendorProxy> {
        val request = HttpRequest(
            method = HttpMethod.GET,
            path = HttpRequestPath("/$dbName/_design/vendors/_view/all"),
            headers = Series(listOf(
                Join(HttpHeaderName("Accept"), HttpHeaderValue("application/json"))
            )),
            body = Series(listOf<Byte>()),
            version = HttpVersion("HTTP/1.1")
        )

        return try {
            val response = request.send()
            if (response.status.value == 200) {
                val bytes = response.body.`play`.toList().toByteArray()
                val vendors = CouchJsonParser.parseVendors(bytes.decodeToString())
                Series(vendors)
            } else {
                Series(emptyList())
            }
        } catch (e: Exception) {
            println("Error fetching vendors: ${e.message}")
            Series(emptyList())
        }
    }

    private fun generateDocId(): String = 
        System.currentTimeMillis().toString(16) + "-" + 
        (0..4).joinToString("") { ('a'..'z').random().toString() }

    /**
     * Ensures the necessary CouchDB views exist
     */
    suspend fun ensureViews() {
        val designDoc = """
        {
          "_id": "_design/deals",
          "views": {
            "by_product": {
              "map": "function(doc) { if (doc.type === 'deal' && doc.product) emit(doc.product, doc); }"
            }
          }
        }
        """.trimIndent()

        val vendorDesignDoc = """
        {
          "_id": "_design/vendors",
          "views": {
            "all": {
              "map": "function(doc) { if (doc.type === 'vendor') emit(doc._id, {id: doc._id, name: doc.name}); }"
            }
          }
        }
        """.trimIndent()

        // Create or update the design documents
        connection.ensureDesignDoc("deals", designDoc)
        connection.ensureDesignDoc("vendors", vendorDesignDoc)
    }
} 