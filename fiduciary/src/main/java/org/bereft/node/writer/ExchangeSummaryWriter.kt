package org.bereft.node.writer


import com.hazelcast.collection.ItemEvent
import kotlinx.coroutines.runBlocking
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.hazelcast.HazelcastConstants
import org.apache.camel.main.Main
import org.asynchttpclient.DefaultAsyncHttpClient
import org.bereft.model.ExchangeSummary
import org.bereft.node.config.MeshNode
import org.bereft.node.config.NodeConfig
import org.intellij.lang.annotations.Language
import java.util.*


object ExchangeSummaryWriter {
    val edb = MeshNode.exchangeSummariesQ.name.lowercase(Locale.getDefault())

    init {
        try {
            @Language("JSON")
            val designDoc =
                """{
                      "_id": "_design/assets",
                      "views": {
                        "symbol-usd-avg": {
                          "reduce": "function(keys, values, rereduce) {\r\n    if (!rereduce) {\r\n        var length = values.length\r\n        return [sum(values) / length, length]\r\n    } else {\r\n        var length = sum(values.map(function(v){return v[1]}));\r\n        var avg = sum(values.map(function(v){\r\n            return v[0] * (v[1] / length)\r\n        }));\r\n        return [avg, length]\r\n    }\r\n}",
                          "map": "function (doc) {\nvar s=doc._id.split(':') \n \n\ndoc.assets.forEach(function(entry) {\n  var v=entry.usdValue\n  if(v)  emit([entry.symbol,s[0]+\":\"+s[1],entry.last_updated], v );\n});}"
                        },
                        "symbols-sum": {
                          "reduce": "_stats",
                          "map": "function (doc) {\nvar s=doc._id.split(':') \n \n\ndoc.assets.forEach(function(entry) {\n  var v=entry.usdValue\n  if(v)  emit([entry.symbol,s[0]+\":\"+s[1],entry.last_updated], v );\n});}"
                        }
                      },
                      "language": "javascript"
                    }"""
            val defaultAsyncHttpClient = DefaultAsyncHttpClient()
            defaultAsyncHttpClient.preparePut("http://localhost:5984/" + edb).execute().toCompletableFuture().join()
            val defaultAsyncHttpClient1 = DefaultAsyncHttpClient()
            val execute = defaultAsyncHttpClient1.preparePost("http://localhost:5984/" + edb).apply {
                setBody(designDoc)
                setHeader("Content-Type", "application/json")
            }.execute()
            val join = execute.toCompletableFuture().join()
            System.err.println(join.toString())

        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {

        }
    }

    /**
     *
     * database configs should not be a big deal.
     *     quick setup here<br/>
     *     [see this https://superuser.com/questions/1101803/docker-couchdb-optimal-configuration-for-continuous-deployment]
     *
     *
     * `docker create -v /usr/local/var/lib/couchdb --name datastore busybox:latest /bin/true`
     *
     *
     * `docker run -d --volumes-from datastore -p 5984:5984 --name db1 klaemo/couchdb bash`
     *
     *
     */
    fun writeCandles() {

        val main = Main()
        main.configure().addRoutesBuilder(object : RouteBuilder() {

            override fun configure() {
                runBlocking {
                    val s =
                        "hazelcast-queue:${MeshNode.exchangeSummariesQ.name}?hazelcastInstanceName=" + MeshNode.meshOp().name
                    System.err.println("couchd hazel from: " + s)
                    from(s).choice()
                }.`when`(header(HazelcastConstants.LISTENER_ACTION).isEqualTo(HazelcastConstants.ADDED))
                    .process {
                        val body = it.`in`.body as ItemEvent<ExchangeSummary>
                        val item = body.item
                        // item._id = item.id + ":" + ISO8601Utils.format(Date(item.last_updated * 1000))
                        it.out.body = NodeConfig.gson.toJson(item)
                        MeshNode.exchangeSummariesQ.remove(body.item)
                    }
                    .to("couchdb:http://localhost:5984/${MeshNode.exchangeSummariesQ.name.lowercase(Locale.getDefault())}?createDatabase=true&updates=false&deletes=false")
                    .`when`(header(HazelcastConstants.LISTENER_ACTION).isEqualTo(HazelcastConstants.REMOVED))
                    // .log("...removed")
                    .to("mock:removed")
                    .otherwise().log("fail!")
            }
        })
        main.run(arrayOf())
    }

    @JvmStatic
    fun main(vararg args: String) = runBlocking { writeCandles() }


}

