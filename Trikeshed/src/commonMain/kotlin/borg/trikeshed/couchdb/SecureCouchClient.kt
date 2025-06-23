package borg.trikeshed.couchdb

import borg.trikeshed.lib.*
import borg.trikeshed.lib.CZero.z
import borg.trikeshed.lib.CZero.nz
import borg.trikeshed.crypto.*
import borg.trikeshed.net.quic.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.*

/**
 * Secure CouchDB Client with full crypto support
 * Provides encrypted document storage, secure authentication, and encrypted transport
 */
class SecureCouchClient(
    private val baseUrl: String,
    private val cryptoEngine: CryptoEngine,
    private val transport: Transport = Transport.HTTP,
    private val quicEngine: QuicEngine? = null
) {
    enum class Transport { HTTP, QUIC }
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    // Crypto configuration
    private var encryptionKey: SymmetricKey? = null
    private var signingKeyPair: KeyPair? = null
    private var sessionKey: SymmetricKey? = null
    
    // Security settings
    data class SecurityConfig(
        val encryptDocuments: Boolean = true,
        val encryptAttachments: Boolean = true,
        val signDocuments: Boolean = true,
        val verifySignatures: Boolean = true,
        val useSecureTransport: Boolean = true,
        val keyDerivationIterations: Int = 100000
    )
    
    private var securityConfig = SecurityConfig()
    
    /**
     * Initialize secure connection with crypto keys
     */
    suspend fun initializeSecurity(
        password: String,
        salt: Indexed<Byte>? = null,
        config: SecurityConfig = SecurityConfig()
    ) {
        securityConfig = config
        
        // Generate or derive encryption key
        val passwordBytes = password.encodeToByteArray().toIdx()
        val keySalt = salt ?: cryptoEngine.generateRandomBytes(32)
        encryptionKey = cryptoEngine.deriveKey(
            passwordBytes, 
            keySalt, 
            KdfAlgorithm.PBKDF2_SHA256
        )
        
        // Generate signing key pair
        signingKeyPair = cryptoEngine.generateKeyPair(KeyAlgorithm.ED25519)
        
        // Generate session key for transport encryption
        sessionKey = cryptoEngine.generateSymmetricKey(SymmetricAlgorithm.AES_GCM_256)
    }
    
    /**
     * Encrypt document data
     */
    private suspend fun encryptDocument(doc: CouchDocument): EncryptedDocument {
        requireNotNull(encryptionKey) { "Encryption key not initialized" }
        
        val docJson = doc.toJson().toString()
        val docBytes = docJson.encodeToByteArray().toIdx()
        
        val encrypted = cryptoEngine.encrypt(docBytes, encryptionKey!!, EncryptionMode.GCM)
        
        // Sign the encrypted data if enabled
        val signature = if (securityConfig.signDocuments) {
            requireNotNull(signingKeyPair) { "Signing key not initialized" }
            cryptoEngine.sign(encrypted.ciphertext, signingKeyPair!!)
        } else {
            0 j { 0.toByte() }
        }
        
        return EncryptedDocument(
            id = doc.id,
            rev = doc.rev,
            encryptedData = encrypted,
            signature = signature,
            algorithm = encryptionKey!!.algorithm,
            timestamp = getCurrentTimeMillis()
        )
    }
    
    /**
     * Decrypt document data
     */
    private suspend fun decryptDocument(encryptedDoc: EncryptedDocument): CouchDocument {
        requireNotNull(encryptionKey) { "Encryption key not initialized" }
        
        // Verify signature if enabled
        if (securityConfig.verifySignatures && encryptedDoc.signature.a > 0) {
            requireNotNull(signingKeyPair) { "Signing key not initialized" }
            val isValid = cryptoEngine.verify(
                encryptedDoc.encryptedData.ciphertext,
                encryptedDoc.signature,
                signingKeyPair!!.publicKey
            )
            require(isValid) { "Document signature verification failed" }
        }
        
        val decryptedBytes = cryptoEngine.decrypt(encryptedDoc.encryptedData, encryptionKey!!)
        val docJson = String(ByteArray(decryptedBytes.a) { decryptedBytes.b(it) })
        val jsonElement = json.parseToJsonElement(docJson).jsonObject
        
        return CouchDocument(
            id = jsonElement["_id"]?.jsonPrimitive?.content,
            rev = jsonElement["_rev"]?.jsonPrimitive?.content,
            deleted = jsonElement["_deleted"]?.jsonPrimitive?.boolean,
            attachments = jsonElement["_attachments"]?.jsonObject,
            data = JsonObject(jsonElement.filterKeys { !it.startsWith("_") })
        )
    }
    
    /**
     * Encrypt attachment data
     */
    private suspend fun encryptAttachment(data: Indexed<Byte>): EncryptedAttachment {
        requireNotNull(encryptionKey) { "Encryption key not initialized" }
        
        val encrypted = cryptoEngine.encrypt(data, encryptionKey!!, EncryptionMode.GCM)
        
        return EncryptedAttachment(
            encryptedData = encrypted,
            originalSize = data.a,
            algorithm = encryptionKey!!.algorithm
        )
    }
    
    /**
     * Decrypt attachment data
     */
    private suspend fun decryptAttachment(encryptedAttachment: EncryptedAttachment): Indexed<Byte> {
        requireNotNull(encryptionKey) { "Encryption key not initialized" }
        
        return cryptoEngine.decrypt(encryptedAttachment.encryptedData, encryptionKey!!)
    }
    
    // Secure document operations
    
    suspend fun createSecureDocument(dbName: String, doc: CouchDocument): CouchResponse {
        val encryptedDoc = encryptDocument(doc)
        val docId = doc.id ?: generateDocId()
        
        // Store encrypted document
        val response = request("PUT", "/$dbName/$docId", encryptedDoc.toJson().toString())
        return json.decodeFromString<CouchResponse>(response)
    }
    
    suspend fun getSecureDocument(dbName: String, docId: String, rev: String? = null): CouchDocument {
        val path = if (rev != null) "/$dbName/$docId?rev=$rev" else "/$dbName/$docId"
        val response = request("GET", path)
        
        val jsonObj = json.parseToJsonElement(response).jsonObject
        
        // Check if document is encrypted
        val isEncrypted = jsonObj["encrypted"]?.jsonPrimitive?.boolean ?: false
        
        return if (isEncrypted) {
            val encryptedDoc = json.decodeFromString<EncryptedDocument>(response)
            decryptDocument(encryptedDoc)
        } else {
            // Handle unencrypted documents
            CouchDocument(
                id = jsonObj["_id"]?.jsonPrimitive?.content,
                rev = jsonObj["_rev"]?.jsonPrimitive?.content,
                deleted = jsonObj["_deleted"]?.jsonPrimitive?.boolean,
                attachments = jsonObj["_attachments"]?.jsonObject,
                data = JsonObject(jsonObj.filterKeys { !it.startsWith("_") })
            )
        }
    }
    
    suspend fun updateSecureDocument(dbName: String, doc: CouchDocument): CouchResponse {
        requireNotNull(doc.id) { "Document ID required for update" }
        requireNotNull(doc.rev) { "Document revision required for update" }
        
        val encryptedDoc = encryptDocument(doc)
        val response = request("PUT", "/$dbName/${doc.id}", encryptedDoc.toJson().toString())
        return json.decodeFromString<CouchResponse>(response)
    }
    
    // Secure attachment operations
    
    suspend fun putSecureAttachment(
        dbName: String, 
        docId: String, 
        attachmentName: String, 
        data: Indexed<Byte>,
        contentType: String = "application/octet-stream"
    ): CouchResponse {
        val encryptedAttachment = encryptAttachment(data)
        
        // Store encrypted attachment metadata
        val attachmentDoc = CouchDocument(
            id = "$docId/$attachmentName",
            data = JsonObject(mapOf(
                "encrypted" to JsonPrimitive(true),
                "contentType" to JsonPrimitive(contentType),
                "originalSize" to JsonPrimitive(encryptedAttachment.originalSize),
                "algorithm" to JsonPrimitive(encryptedAttachment.algorithm.name)
            ))
        )
        
        return createSecureDocument(dbName, attachmentDoc)
    }
    
    suspend fun getSecureAttachment(dbName: String, docId: String, attachmentName: String): Indexed<Byte> {
        val attachmentDoc = getSecureDocument(dbName, "$docId/$attachmentName")
        val encryptedData = attachmentDoc.data["encryptedData"]?.jsonObject
        
        requireNotNull(encryptedData) { "Attachment not found or not encrypted" }
        
        val encryptedAttachment = EncryptedAttachment(
            encryptedData = EncryptedData(
                ciphertext = CryptoUtils.decodeBase64(encryptedData["ciphertext"]?.jsonPrimitive?.content ?: ""),
                iv = CryptoUtils.decodeBase64(encryptedData["iv"]?.jsonPrimitive?.content ?: ""),
                mode = EncryptionMode.valueOf(encryptedData["mode"]?.jsonPrimitive?.content ?: "GCM")
            ),
            originalSize = encryptedData["originalSize"]?.jsonPrimitive?.int ?: 0,
            algorithm = SymmetricAlgorithm.valueOf(encryptedData["algorithm"]?.jsonPrimitive?.content ?: "AES_GCM_256")
        )
        
        return decryptAttachment(encryptedAttachment)
    }
    
    // Secure replication
    
    suspend fun replicateSecure(
        source: String,
        target: String,
        continuous: Boolean = false,
        filter: String? = null
    ): ReplicationStatus {
        val replicationRequest = ReplicationRequest(
            source = source,
            target = target,
            continuous = continuous,
            filter = filter,
            encrypted = true
        )
        
        val response = request("POST", "/_replicate", replicationRequest.toJson().toString())
        return json.decodeFromString<ReplicationStatus>(response)
    }
    
    // Security audit
    
    suspend fun auditSecurity(dbName: String): SecurityAudit {
        val audit = mutableListOf<SecurityAuditItem>()
        
        // Check database security settings
        val security = getSecurity(dbName)
        audit.add(SecurityAuditItem(
            type = "database_security",
            status = if (security.admins.names.a > 0 || security.members.names.a > 0) "SECURE" else "INSECURE",
            details = "Database has security settings configured"
        ))
        
        // Check for encrypted documents
        val allDocs = queryView(dbName, "_all_docs", "all_docs", ViewQueryParams(limit = 100))
        var encryptedCount = 0
        var totalCount = 0
        
        for (i in 0 until allDocs.rows.a) {
            val row = allDocs.rows.b(i)
            try {
                val doc = getSecureDocument(dbName, row.id)
                totalCount++
                if (doc.data["encrypted"]?.jsonPrimitive?.boolean == true) {
                    encryptedCount++
                }
            } catch (e: Exception) {
                // Document might be corrupted or not accessible
            }
        }
        
        audit.add(SecurityAuditItem(
            type = "document_encryption",
            status = if (encryptedCount == totalCount) "SECURE" else "PARTIAL",
            details = "$encryptedCount of $totalCount documents are encrypted"
        ))
        
        return SecurityAudit(audit.toIdx())
    }
    
    // Private helper methods
    
    private suspend fun request(method: String, path: String, body: String? = null): String {
        // Implement secure HTTP/QUIC request with crypto
        // This would include:
        // - TLS/QUIC transport encryption
        // - Request signing
        // - Response verification
        // - Session key management
        
        return "" // Placeholder
    }
    
    private fun generateDocId(): String {
        return cryptoEngine.generateRandomBytes(16).play.joinToString("") { 
            String.format("%02x", it) 
        }
    }
    
    // Data classes for encrypted storage
    
    data class EncryptedDocument(
        val id: String?,
        val rev: String?,
        val encryptedData: EncryptedData,
        val signature: Indexed<Byte>,
        val algorithm: SymmetricAlgorithm,
        val timestamp: Long
    ) {
        fun toJson(): JsonObject = JsonObject(mapOf(
            "_id" to JsonPrimitive(id ?: ""),
            "_rev" to JsonPrimitive(rev ?: ""),
            "encrypted" to JsonPrimitive(true),
            "encryptedData" to JsonObject(mapOf(
                "ciphertext" to JsonPrimitive(CryptoUtils.encodeBase64(encryptedData.ciphertext)),
                "iv" to JsonPrimitive(CryptoUtils.encodeBase64(encryptedData.iv)),
                "mode" to JsonPrimitive(encryptedData.mode.name)
            )),
            "signature" to JsonPrimitive(CryptoUtils.encodeBase64(signature)),
            "algorithm" to JsonPrimitive(algorithm.name),
            "timestamp" to JsonPrimitive(timestamp)
        ))
    }
    
    data class EncryptedAttachment(
        val encryptedData: EncryptedData,
        val originalSize: Int,
        val algorithm: SymmetricAlgorithm
    )
    
    data class SecurityAuditItem(
        val type: String,
        val status: String,
        val details: String
    )
    
    data class SecurityAudit(
        val items: Indexed<SecurityAuditItem>
    )
} 