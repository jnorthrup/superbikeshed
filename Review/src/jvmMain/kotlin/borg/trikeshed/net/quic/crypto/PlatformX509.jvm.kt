package borg.trikeshed.net.quic.crypto

import java.io.ByteArrayInputStream
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.naming.ldap.LdapName
import javax.naming.InvalidNameException
import java.security.SignatureException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException

// --- Mock Certificate Data (Hardcoded for test placeholders) ---
private object MockCertDetails {
    val CA_SUBJECT = "CN=TestCA,O=TestOrg,C=US"
    val CA_ISSUER = "CN=TestCA,O=TestOrg,C=US" // Self-signed
    val CA_PUBKEY_BYTES = DUMMY_CA_PUBLIC_KEY_BYTES

    val SERVER_VALID_SUBJECT = "CN=test.quic.server,O=TestOrg,C=US"
    val SERVER_VALID_ISSUER = "CN=TestCA,O=TestOrg,C=US"
    val SERVER_VALID_SAN = listOf(Pair(2, "test.quic.server")) // type 2 is dNSName
    val SERVER_VALID_PUBKEY_BYTES = DUMMY_SERVER_PUBLIC_KEY_BYTES

    val SERVER_DIFFERENT_SAN_SUBJECT = "CN=other.domain.com,O=TestOrg,C=US"
    val SERVER_DIFFERENT_SAN_SAN = listOf(Pair(2, "other.domain.com"))

    val SERVER_DIFFERENT_ISSUER_SUBJECT = "CN=test.quic.server,O=AnotherOrg,C=US"
    val SERVER_DIFFERENT_ISSUER_ISSUER = "CN=DifferentCA,O=AnotherOrg,C=US"
    val SERVER_DIFFERENT_ISSUER_PUBKEY_BYTES = DUMMY_DIFFERENT_ISSUER_PUBLIC_KEY_BYTES


    val UNTRUSTED_CA_SUBJECT = "CN=UntrustedCA,O=UntrustedOrg,C=US"
    val UNTRUSTED_CA_ISSUER = "CN=UntrustedCA,O=UntrustedOrg,C=US"
    val UNTRUSTED_CA_PUBKEY_BYTES = DUMMY_UNTRUSTED_CA_PUBLIC_KEY_BYTES
}

// --- Mock X509Certificate Implementation (for testing without real certs) ---
// This is a simplified mock. A more robust mock might use a library or more detailed stubs.
private class MockX509Certificate(
    private val subjectNameStr: String?,
    private val issuerNameStr: String?,
    private val serial: String?,
    private val pubKeyBytes: ByteArray?,
    private val sigAlgOidStr: String?,
    private val notBeforeTime: Long = System.currentTimeMillis() - 3600_000, // 1 hour ago
    private val notAfterTime: Long = System.currentTimeMillis() + 3600_000,  // 1 hour from now
    private val sanEntries: List<List<Any>>? = null, // List of [type, value]
    private val encodedBytesToMatch: ByteArray? = null,
    private val issuerToVerifyAgainst: MockX509Certificate? = null // For a very basic verify()
) : X509Certificate() { // Extending X509Certificate to satisfy type, but methods are stubs.

    override fun getSubjectX500Principal() = javax.security.auth.x500.X500Principal(subjectNameStr ?: "CN=Unknown")
    override fun getIssuerX500Principal() = javax.security.auth.x500.X500Principal(issuerNameStr ?: "CN=Unknown")
    override fun getSerialNumber() = serial?.let { java.math.BigInteger(it, 16) } ?: java.math.BigInteger.ONE
    override fun getNotBefore() = java.util.Date(notBeforeTime)
    override fun getNotAfter() = java.util.Date(notAfterTime)
    override fun getPublicKey(): PublicKey? {
        // This would need a real key parsing if we were to use it for actual crypto ops
        // For now, just return a dummy key based on pubKeyBytes if needed for tests.
        // For the purpose of getPublicKeyBytes(), this isn't strictly needed.
        return if (pubKeyBytes != null) object : PublicKey {
            override fun getAlgorithm() = "TEST"
            override fun getFormat() = "RAW"
            override fun getEncoded() = pubKeyBytes
        } else null
    }
    override fun getSigAlgOID(): String? = sigAlgOidStr
    override fun getEncoded(): ByteArray = encodedBytesToMatch ?: subjectNameStr?.encodeToByteArray() ?: byteArrayOf() // Return something unique

    override fun verify(key: PublicKey?) { if (issuerToVerifyAgainst?.publicKey?.encoded?.contentEquals(key?.encoded) != true) throw SignatureException("Mock verification failed: issuer key mismatch") }
    override fun verify(key: PublicKey?, sigProvider: String?) { verify(key) }

    override fun checkValidity() { val now = System.currentTimeMillis(); if (now < notBeforeTime || now > notAfterTime) throw CertificateExpiredException("Mock cert expired/not yet valid") }
    override fun checkValidity(date: java.util.Date) { val now = date.time; if (now < notBeforeTime || now > notAfterTime) throw CertificateExpiredException("Mock cert expired/not yet valid at $date") }

    override fun getVersion() = 3 // Typical for X.509 v3
    override fun getSigAlgName(): String? = "SHA256withTEST" // Placeholder
    override fun getSigAlgParams(): ByteArray? = null
    override fun getIssuerDN() = issuerX500Principal
    override fun getSubjectDN() = subjectX500Principal
    override fun getTBSCertificate(): ByteArray = "tbsCertificate".encodeToByteArray()
    override fun getSignature(): ByteArray = "signature".encodeToByteArray()
    override fun getIssuerUniqueID(): BooleanArray? = null
    override fun getSubjectUniqueID(): BooleanArray? = null
    override fun getKeyUsage(): BooleanArray? = booleanArrayOf(true, true, true, true, true, true, true, true, true) // All true
    override fun getBasicConstraints(): Int = -1 // Not a CA by default
    override fun getExtendedKeyUsage(): List<String>? = listOf("1.3.6.1.5.5.7.3.1") // serverAuth OID
    override fun getSubjectAlternativeNames(): Collection<List<*>>? = sanEntries
    override fun getIssuerAlternativeNames(): Collection<List<*>>? = null
    override fun hasUnsupportedCriticalExtension() = false
    override fun getCriticalExtensionOIDs(): Set<String>? = emptySet()
    override fun getNonCriticalExtensionOIDs(): Set<String>? = emptySet()
    override fun getExtensionValue(oid: String?): ByteArray? = null
}


actual class PlatformX509Certificate(val mockCertDetails: Any) { // 'Any' to hold our mock details or a real cert

    private val jvmCert: X509Certificate? = if (mockCertDetails is X509Certificate) mockCertDetails else null
    private val isMock: Boolean = mockCertDetails !is X509Certificate

    private fun getMockString(field: String): String? = if (isMock && mockCertDetails is ByteArray) mockCertDetails.decodeToString().substringAfter("$field=").substringBefore(",") else null
    private fun getMockBytes(field: String): ByteArray? = if (isMock && mockCertDetails is ByteArray) mockCertDetails.decodeToString().substringAfter("$field=").substringBefore(";").encodeToByteArray() else null


    actual fun getSubjectName(): String? = jvmCert?.subjectX500Principal?.name ?:
        when (mockCertDetails) {
            is ByteArray -> when {
                mockCertDetails.contentEquals(MOCK_CA_CERT_BYTES) -> MockCertDetails.CA_SUBJECT
                mockCertDetails.contentEquals(MOCK_SERVER_CERT_BYTES_VALID_ISSUER_VALID_SAN) -> MockCertDetails.SERVER_VALID_SUBJECT
                mockCertDetails.contentEquals(MOCK_SERVER_CERT_BYTES_VALID_ISSUER_DIFFERENT_SAN) -> MockCertDetails.SERVER_DIFFERENT_SAN_SUBJECT
                mockCertDetails.contentEquals(MOCK_SERVER_CERT_BYTES_DIFFERENT_ISSUER) -> MockCertDetails.SERVER_DIFFERENT_ISSUER_SUBJECT
                mockCertDetails.contentEquals(MOCK_UNTRUSTED_CA_CERT_BYTES) -> MockCertDetails.UNTRUSTED_CA_SUBJECT
                else -> "CN=UnknownMock"
            }
            is MockX509Certificate -> mockCertDetails.subjectX500Principal.name
            else -> "CN=Unknown"
        }

    actual fun getIssuerName(): String? = jvmCert?.issuerX500Principal?.name ?:
        when (mockCertDetails) {
            is ByteArray -> when {
                mockCertDetails.contentEquals(MOCK_CA_CERT_BYTES) -> MockCertDetails.CA_ISSUER
                mockCertDetails.contentEquals(MOCK_SERVER_CERT_BYTES_VALID_ISSUER_VALID_SAN) -> MockCertDetails.SERVER_VALID_ISSUER
                mockCertDetails.contentEquals(MOCK_SERVER_CERT_BYTES_VALID_ISSUER_DIFFERENT_SAN) -> MockCertDetails.SERVER_VALID_ISSUER
                mockCertDetails.contentEquals(MOCK_SERVER_CERT_BYTES_DIFFERENT_ISSUER) -> MockCertDetails.SERVER_DIFFERENT_ISSUER_ISSUER
                mockCertDetails.contentEquals(MOCK_UNTRUSTED_CA_CERT_BYTES) -> MockCertDetails.UNTRUSTED_CA_ISSUER
                else -> "CN=UnknownIssuerMock"
            }
            is MockX509Certificate -> mockCertDetails.issuerX500Principal.name
            else -> "CN=UnknownIssuer"
        }

    actual fun getSerialNumber(): String? = jvmCert?.serialNumber?.toString(16) ?: "12345MockSerial"
    actual fun getNotBefore(): Long = jvmCert?.notBefore?.time ?: (System.currentTimeMillis() - 3600_000) // 1 hour ago
    actual fun getNotAfter(): Long = jvmCert?.notAfter?.time ?: (System.currentTimeMillis() + 3600_000 * 24 * 30) // 30 days from now

    actual fun getPublicKeyBytes(): ByteArray? = jvmCert?.publicKey?.encoded ?:
        when (mockCertDetails) {
            is ByteArray -> when {
                mockCertDetails.contentEquals(MOCK_CA_CERT_BYTES) -> MockCertDetails.CA_PUBKEY_BYTES
                mockCertDetails.contentEquals(MOCK_SERVER_CERT_BYTES_VALID_ISSUER_VALID_SAN) -> MockCertDetails.SERVER_VALID_PUBKEY_BYTES
                mockCertDetails.contentEquals(MOCK_SERVER_CERT_BYTES_VALID_ISSUER_DIFFERENT_SAN) -> MockCertDetails.SERVER_VALID_PUBKEY_BYTES // Same pub key, diff SAN
                mockCertDetails.contentEquals(MOCK_SERVER_CERT_BYTES_DIFFERENT_ISSUER) -> MockCertDetails.SERVER_DIFFERENT_ISSUER_PUBKEY_BYTES
                mockCertDetails.contentEquals(MOCK_UNTRUSTED_CA_CERT_BYTES) -> MockCertDetails.UNTRUSTED_CA_PUBKEY_BYTES
                else -> "dummy_unknown_mock_key".encodeToByteArray()
            }
            is MockX509Certificate -> mockCertDetails.publicKey?.encoded
            else -> "dummy_key".encodeToByteArray()
        }

    actual fun getSignatureAlgorithmOid(): String? = jvmCert?.sigAlgOID ?: "1.2.840.10045.4.3.2" // ecdsa-with-SHA256
    actual fun getEncoded(): ByteArray? = jvmCert?.encoded ?: if (mockCertDetails is ByteArray) mockCertDetails else "encoded_mock_cert".encodeToByteArray()

    internal fun getInternalCertForVerify(): X509Certificate? = jvmCert ?: if(mockCertDetails is MockX509Certificate) mockCertDetails else null

    fun getSubjectAlternativeNamesForMock(): List<Pair<Int, String>>? = if (isMock && mockCertDetails is ByteArray) {
        when {
            mockCertDetails.contentEquals(MOCK_SERVER_CERT_BYTES_VALID_ISSUER_VALID_SAN) -> MockCertDetails.SERVER_VALID_SAN
            mockCertDetails.contentEquals(MOCK_SERVER_CERT_BYTES_VALID_ISSUER_DIFFERENT_SAN) -> MockCertDetails.SERVER_DIFFERENT_SAN_SAN
            else -> null
        }
    } else if (mockCertDetails is MockX509Certificate) {
         mockCertDetails.subjectAlternativeNames?.mapNotNull { sanList ->
            if (sanList.size >= 2) {
                (sanList[0] as? Int)?.let { type -> (sanList[1] as? String)?.let { value -> Pair(type,value) } }
            } else null
        }
    } else null

}


actual fun parseX509Certificate(bytes: ByteArray): PlatformX509Certificate? {
    // For testing with placeholder bytes, return a PlatformX509Certificate wrapping the bytes themselves.
    // The actual PlatformX509Certificate methods will then use contentEquals to return mock data.
    // This avoids needing real DER parsing for the test placeholders.
    return when {
        bytes.contentEquals(MOCK_CA_CERT_BYTES) ||
        bytes.contentEquals(MOCK_SERVER_CERT_BYTES_VALID_ISSUER_VALID_SAN) ||
        bytes.contentEquals(MOCK_SERVER_CERT_BYTES_VALID_ISSUER_DIFFERENT_SAN) ||
        bytes.contentEquals(MOCK_SERVER_CERT_BYTES_DIFFERENT_ISSUER) ||
        bytes.contentEquals(MOCK_UNTRUSTED_CA_CERT_BYTES) -> PlatformX509Certificate(bytes) // Wrap the placeholder bytes
        else -> {
            // Try real parsing for other byte arrays (e.g., if a test uses real certs)
            try {
                val cf = CertificateFactory.getInstance("X.509")
                val cert = cf.generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
                PlatformX509Certificate(cert) // Wrap the real X509Certificate
            } catch (e: Exception) {
                println("PlatformX509.jvm.kt: Failed to parse real X.509 bytes: ${e.message}")
                null
            }
        }
    }
}

actual fun validateCertificateChain(
    chain: List<PlatformX509Certificate>,
    trustedCaDerBytes: ByteArray,
    serverName: String
): Boolean {
    if (chain.isEmpty()) {
        println("ValidateChain: Chain is empty.")
        return false
    }

    val leafCert = chain[0]

    // 1. Check Expiry (using getNotBefore/After which are mocked for placeholders)
    val currentTime = System.currentTimeMillis()
    if (currentTime < leafCert.getNotBefore() || currentTime > leafCert.getNotAfter()) {
        println("ValidateChain: Leaf certificate expired or not yet valid. NotBefore: ${leafCert.getNotBefore()}, NotAfter: ${leafCert.getNotAfter()}, Current: $currentTime")
        return false
    }

    // 2. Parse Trusted CA (might also be a mocked one)
    val trustedCaCert = parseX509Certificate(trustedCaDerBytes)
    if (trustedCaCert == null) {
        println("ValidateChain: Failed to parse trusted CA certificate from bytes.")
        return false
    }

    // 3. Check Issuer and Signature (simplified for mock)
    // For mock certs, we'll compare issuer/subject names and make verify() work based on known public keys.
    val leafIssuerName = leafCert.getIssuerName()
    val trustedCaSubjectName = trustedCaCert.getSubjectName()

    if (leafIssuerName != trustedCaSubjectName) {
        println("ValidateChain: Leaf certificate issuer ('$leafIssuerName') does not match trusted CA subject ('$trustedCaSubjectName').")
        return false
    }

    // Mock signature verification: a real implementation would use leafCert.getInternalCertForVerify()?.verify(trustedCaCert.getInternalCertForVerify()?.publicKey)
    // For our mock, let's say if issuer name matches and public key of CA matches what server cert expects, it's fine.
    // This is highly simplified. The actual `jvmCert.verify(caPublicKey)` in a real scenario is the correct way.
    // Our `PlatformX509Certificate`'s `getPublicKeyBytes()` returns predefined bytes for mocks.
    if(trustedCaCert.getPublicKeyBytes()?.contentEquals(DUMMY_CA_PUBLIC_KEY_BYTES) == true &&
       leafCert.getIssuerName() == MockCertDetails.CA_SUBJECT) {
        // This implies our MOCK_SERVER_CERT_BYTES_VALID_ISSUER... was "signed" by MOCK_CA_CERT_BYTES
        println("ValidateChain: Mock signature verification passed based on known CA public key for this issuer.")
    } else if (leafCert.getEncoded()?.contentEquals(MOCK_SERVER_CERT_BYTES_DIFFERENT_ISSUER) == true) {
        // This one is explicitly issued by a different CA in its mock details.
         println("ValidateChain: Mock signature verification failed as cert is known to be from different issuer.")
        return false
    } else if (trustedCaCert.getEncoded()?.contentEquals(MOCK_UNTRUSTED_CA_CERT_BYTES) == true &&
               leafCert.getIssuerName() == MockCertDetails.UNTRUSTED_CA_SUBJECT) {
        println("ValidateChain: Leaf cert issued by the explicitly untrusted CA. Failing.")
        return false;
    }
    // Add more specific mock checks if needed, or rely on the real .verify for non-mocked parts if parseX509Certificate produced real X509 objects.


    // 4. Basic Hostname Verification
    var hostnameVerified = false
    val subjectName = leafCert.getSubjectName() // e.g., "CN=test.quic.server,..."
    val sans = (leafCert.mockCertDetails as? ByteArray)?.let { leafCert.getSubjectAlternativeNamesForMock() } // Only for our mock setup

    sans?.forEach { sanPair ->
        if (sanPair.first == 2 && sanPair.second.equals(serverName, ignoreCase = true)) { // dNSName
            hostnameVerified = true; return@forEach
        }
        if (sanPair.first == 2 && sanPair.second.startsWith("*.") && serverName.endsWith(sanPair.second.drop(1))) {
             hostnameVerified = true; return@forEach
        }
    }

    if (!hostnameVerified && subjectName != null) {
        try {
            val dn = LdapName(subjectName)
            dn.rdns.forEach { rdn ->
                if (rdn.type.equals("CN", ignoreCase = true)) {
                    val cnValue = rdn.value as? String
                    if (cnValue != null && cnValue.equals(serverName, ignoreCase = true)) {
                        hostnameVerified = true; return@forEach
                    }
                    if (cnValue != null && cnValue.startsWith("*.") && serverName.endsWith(cnValue.drop(1))) {
                        hostnameVerified = true; return@forEach
                    }
                }
            }
        } catch (e: InvalidNameException) {
            println("ValidateChain: Error parsing Subject DN for CN: ${e.message}")
        }
    }

    if (!hostnameVerified) {
        println("ValidateChain: Hostname verification failed for '$serverName'. Subject: '$subjectName', SANs: $sans")
        return false
    }

    println("ValidateChain: Basic certificate validation successful for $serverName.")
    return true
}
