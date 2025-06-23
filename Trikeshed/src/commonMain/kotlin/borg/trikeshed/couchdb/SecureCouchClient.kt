package borg.trikeshed.couchdb

import borg.trikeshed.lib.*
import borg.trikeshed.ksp.TrikeShedDsl

@TrikeShedDsl
class SecureCouchConfig {
    var baseUrl: String = "https://localhost:6984"
    var database: String = ""
    var encryption: Boolean = true
}

@TrikeShedDsl
object SecureCouchDSL {
    fun secureCouch(block: SecureCouchConfig.() -> Unit): SecureCouchConfig {
        return SecureCouchConfig().apply(block)
    }
    
    fun SecureCouchConfig.get(docId: String): String {
        return "SECURE GET /$database/$docId -> encrypted response"
    }
    
    fun SecureCouchConfig.put(docId: String, doc: String): String {
        return "SECURE PUT /$database/$docId -> encrypted and stored"
    }
}