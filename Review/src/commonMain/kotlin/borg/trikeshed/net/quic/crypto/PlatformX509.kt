package borg.trikeshed.net.quic.crypto

/**
 * Represents a platform-specific X.509 certificate.
 * Provides a common interface to access certificate properties.
 */
expect class PlatformX509Certificate {
    /** Returns the Subject Distinguished Name as a string, or null if not available. */
    fun getSubjectName(): String?
    /** Returns the Issuer Distinguished Name as a string, or null if not available. */
    fun getIssuerName(): String?
    /** Returns the serial number as a String (often hex), or null. */
    fun getSerialNumber(): String?
    /** Returns the Not Before validity date as epoch milliseconds. */
    fun getNotBefore(): Long
    /** Returns the Not After validity date as epoch milliseconds. */
    fun getNotAfter(): Long
    /**
     * Returns the bytes of the Subject Public Key Info (SPKI).
     * This is the ASN.1 structure containing the public key.
     */
    fun getPublicKeyBytes(): ByteArray?
    /** Returns the OID of the signature algorithm used to sign this certificate. */
    fun getSignatureAlgorithmOid(): String?
    /** Returns the raw encoded bytes of the entire certificate. */
    fun getEncoded(): ByteArray?

    // TODO: Consider adding methods for KeyUsage, ExtendedKeyUsage, SubjectAlternativeNames (SANs)
    // fun getKeyUsage(): BooleanArray? // Standard order, e.g., digitalSignature is bit 0
    // fun getExtendedKeyUsage(): List<String>? // List of OIDs
    // fun getSubjectAlternativeNames(): List<Pair<Int, String>>? // Pair of type (e.g., 2 for dNSName) and value
}

/**
 * Parses a byte array into a [PlatformX509Certificate].
 * @param bytes The DER-encoded certificate bytes.
 * @return A [PlatformX509Certificate] object, or null if parsing fails.
 */
expect fun parseX509Certificate(bytes: ByteArray): PlatformX509Certificate?

/**
 * Validates a certificate chain against a trusted CA and expected server name.
 * This is a simplified validation for testing purposes.
 *
 * @param chain The list of [PlatformX509Certificate]s, where chain[0] is the leaf (end-entity) certificate.
 * @param trustedCaDerBytes The DER-encoded bytes of the single trusted CA certificate.
 * @param serverName The expected server name (hostname) for identity verification.
 * @return True if the basic validation passes, false otherwise.
 */
expect fun validateCertificateChain(
    chain: List<PlatformX509Certificate>,
    trustedCaDerBytes: ByteArray,
    serverName: String
): Boolean
