package org.bereft.node.index

import org.apache.http.HttpHeaders
import org.apache.http.NameValuePair
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.utils.URIBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import org.asynchttpclient.AsyncHttpClient
import org.asynchttpclient.DefaultAsyncHttpClient
import org.asynchttpclient.Response
import org.bereft.model.CmcCurrency
import org.bereft.node.config.MeshNode
import org.bereft.runtime.RANDOM
import org.bereft.runtime.couchUtil.bulk.Companion.wrapList
import java.io.IOException
import java.net.URISyntaxException

/**
 * This example uses the Apache HTTPComponents library.
 */


object JavaExample {
    private const val apiKey = "b54bcf4d-1bca-4e8e-9a24-22ff2c3d462c"
    @JvmStatic
    fun main(args: Array<String>) {
        val uri = "https://pro-api.coinmarketcap.com/v1/cryptocurrency/listings/latest"
        val paratmers: MutableList<NameValuePair> = ArrayList()
        paratmers.add(BasicNameValuePair("start", "1"))
        paratmers.add(BasicNameValuePair("limit", "5000"))
        paratmers.add(BasicNameValuePair("convert", "USD"))
        try {
            val result = makeAPICall(uri, paratmers)
            println(result)
        } catch (e: IOException) {
            println("Error: cannont access content - $e")
        } catch (e: URISyntaxException) {
            println("Error: Invalid URL $e")
        }
    }

    @Throws(URISyntaxException::class, IOException::class)
    fun makeAPICall(uri: String?, parameters: List<NameValuePair>?): String {
        var response_content = ""
        val query = URIBuilder(uri)
        query.addParameters(parameters)
        val client = HttpClients.createDefault()
        val request = HttpGet(query.build())
        request.setHeader(HttpHeaders.ACCEPT, "application/json")
        request.addHeader("X-CMC_PRO_API_KEY", apiKey)
        val response = client.execute(request)
        try {
            println(response.statusLine)
            val entity = response.entity
            response_content = EntityUtils.toString(entity)
            EntityUtils.consume(entity)
        } finally {
            response.close()
        }
        return response_content
    }
}


fun CmcCurrencies(lim: Int? = null): List<CmcCurrency> {
    lateinit  var fromJson: CmcEnvelope
    var asyncHttpClient: AsyncHttpClient? = null
    lateinit var res: Response
    try {
        asyncHttpClient = DefaultAsyncHttpClient()
        val prepareGet = asyncHttpClient
            .prepareGet("https://api.coinmarketcap.com/v1/ticker/${if (lim != null) "?limit=$lim" else ""}")
            .setHeader("Accept", "application/json")
            .setHeader("User-Agent", "Mozilla(" + RANDOM.nextGaussian() + ")")
        res = prepareGet.execute().get()
        if (res.statusCode != 200 || "application/json" != res.headers["Content-Type"])
            throw  IOException("Currency load failure: ${res.statusText} \n ${res.toString()}")
        val x = wrapList(res.responseBody)
        fromJson = MeshNode.gson.fromJson(x, CmcEnvelope::class.java)

    } catch (e: Exception) {
        System.err.println("!!! ${res.responseBody}")
    } finally {
        asyncHttpClient?.close()
    }
    return fromJson.docs

}

val view: String = """
function (doc) {
    var date = new Date(doc.last_updated * 1000)

    emit([doc.id,
             date.getUTCFullYear(),
             date.getUTCMonth() + 1,
             date.getUTCDate(),
             date.getUTCHours(),
             date.getUTCMinutes()/*,
             date.getUTCSeconds()*/
         ],
         doc
    )
}"""

fun main(args: Array<String>) {
    System.err.println(CmcCurrencies())
}