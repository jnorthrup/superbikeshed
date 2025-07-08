package org.bereft.runtime.couchUtil

import org.asynchttpclient.DefaultAsyncHttpClient
import org.asynchttpclient.Response
import org.bereft.node.config.MeshNode
import org.bereft.node.config.NodeConfig

interface hasId {
    val id: String?;
}

data class result(val error: String?, val reason: String?, override val id: String?, val _rev: String?) : hasId

interface has_id {
    val _id: String?;
}

open class bulk<T>(open val docs: List<T>) {
    companion object {
        fun wrapList(jsonList: String) = "{\"docs\":$jsonList}"

        var couchHttpClient: org.asynchttpclient.AsyncHttpClient = DefaultAsyncHttpClient()
            get() {
                if (field.isClosed) field = DefaultAsyncHttpClient()
                return field
            }

        inline fun <reified T : has_id> writeBulk(
            input: List<T>,
            dbname: String,
            /*  , noinline onError: ((result, Map<String, T>) -> Unit)? =null*/
        ) = input.let {
            var errorMemoization: Map<String, T>? = null;
            if (seeBrokeCouchCommits) {
                errorMemoization = input.filter { it._id != null }.associateBy { it._id!! }
            }
            var response: Response? = null
            try {
                val toJson = MeshNode.gson.toJson(bulk(input))
                val request = couchHttpClient.preparePost(
                    "http://localhost:5984/$dbname/_bulk_docs")
                    .setHeader(
                        "Content-Type", "application/json")
                    .setBody(toJson)
                    .setRequestTimeout(15 * 1000 * 60)
                    .build()

                val executeRequest = couchHttpClient.executeRequest(request)
                response = executeRequest.get()

            } catch (e: Exception) {
            }

            response?.also {
                System.err.println(
                    "response: " + (response.statusCode))
                when {
                    201 == response.statusCode -> {
                        val responseBody: resultBag = MeshNode.gson.fromJson<resultBag>(
                            wrapList(response.responseBody), resultBag::class.java)
                        var err = 0
                        responseBody.docs.forEach {
                            if (it.error != null) {
                                err++
//                    onError?.invoke(it, errorMemoization!!  )
                            }
                        }
                        val size = responseBody.docs.size
                        System.err.println(
                            "total: $size ok: ${responseBody.docs.size - err} errors: $err")


                    }
                    else -> System.err.println("fail:" + response.toString())

                }
            }
        }

        val seeBrokeCouchCommits = NodeConfig.getK("seeBrokeCouchCommits", "false") == "true"
    }
}

class resultBag(docs: List<result>) : bulk<result>(docs)



