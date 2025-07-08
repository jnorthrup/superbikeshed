package org.bereft.node.writer


import com.google.gson.internal.bind.util.ISO8601Utils
import com.hazelcast.collection.ItemEvent
import kotlinx.coroutines.runBlocking
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.hazelcast.HazelcastConstants
import org.apache.camel.main.Main
import org.asynchttpclient.DefaultAsyncHttpClient
import org.bereft.model.CmcCurrency
import org.bereft.node.config.MeshNode
import org.bereft.runtime.couchUtil.bulk.Companion.writeBulk
import java.util.*

object CmcWriter {
    val dbname = MeshNode.cmcUpdatesQ.name.lowercase(Locale.getDefault())

    init {
        try {
            DefaultAsyncHttpClient().preparePut("http://localhost:5984/" + dbname).execute()
        } catch (_: Throwable) {
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
                            "hazelcast-queue:${MeshNode.cmcUpdatesQ.name}?hazelcastInstanceName=" + MeshNode.meshOp().name
                        System.err.println("couchd hazel from: " + s)
                        from(s).choice()
                    }.`when`(header(HazelcastConstants.LISTENER_ACTION).isEqualTo(HazelcastConstants.ADDED))
//                        .log("...added")
                        .process {
                            val body = it.`in`.body as ItemEvent<List<CmcCurrency>>

                            var dead = 0;
                            val input = body.item.filter { cmcCurrency ->
                                when {
                                    cmcCurrency.last_updated == 0L -> {

                                        false.also { dead++ }
                                    }
                                    else -> true
                                }

                            }.map {
                                it.also {
                                    it._id = "${it.id}:${ISO8601Utils.format(Date(it.last_updated * 1000))}"
                                }
                            }
                            writeBulk(input,
                                dbname) /*{ result: result, map: Map<String, CmcCurrency> ->
                                    if (seeBrokeCouchCommits) {
                                        if (Random().nextInt() % 10 == 0) {
                                            System.err.println(
                                                    "--- " + result.id + " " + result.reason + map[result.id])
                                        }
                                    }

                                }*/

                            MeshNode.cmcUpdatesQ.remove(body.item)
                            if (dead > 0) System.err.println("skipped " + dead + " dead tickers")
                        }
//                            .to("couchdb:http://localhost:5984/${MeshNode.cmcUpdatesQ.name.toLowerCase()}?createDatabase=true&updates=false&deletes=false")
                        .`when`(header(HazelcastConstants.LISTENER_ACTION).isEqualTo(HazelcastConstants.REMOVED))
//                        .log("...removed")
                        .to("mock:removed")
                        .otherwise().log("fail!")
                }

            })
        main.run(arrayOf())
    }

    fun main(args: Array<String>) {
        runBlocking { writeCandles(/*MeshNode.meshOp().name*/) }
    }
}
