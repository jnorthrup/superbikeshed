package borg.trikeshed.couchdb

import borg.trikeshed.lib.*
import borg.trikeshed.ksp.TrikeShedDsl

@TrikeShedDsl
class CouchConfig {
    var baseUrl: String = "http://localhost:5984"
    var database: String = ""
    var username: String? = null
    var password: String? = null
}

@TrikeShedDsl  
object CouchDSL {
    fun couch(block: CouchConfig.() -> Unit): CouchConfig {
        return CouchConfig().apply(block)
    }
    
    fun CouchConfig.get(docId: String): String {
        return "GET /$database/$docId -> mock response"
    }
    
    fun CouchConfig.put(docId: String, doc: String): String {
        return "PUT /$database/$docId -> created"
    }
    
    fun CouchConfig.delete(docId: String): String {
        return "DELETE /$database/$docId -> deleted"
    }
    
    fun CouchConfig.list(): Indexed<String> {
        return listOf("doc1", "doc2", "doc3").toIdx()
    }
}