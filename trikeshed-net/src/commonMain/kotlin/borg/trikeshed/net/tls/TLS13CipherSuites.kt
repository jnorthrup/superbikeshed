package borg.trikeshed.net.tls

/**
 * TLS 1.3 Cipher Suites
 * RFC 8446 Section B.4
 */
enum class TLS13CipherSuites(val value: UShort) {
    TLS_AES_128_GCM_SHA256(0x1301u),
    TLS_AES_256_GCM_SHA384(0x1302u),
    TLS_CHACHA20_POLY1305_SHA256(0x1303u),
    TLS_AES_128_CCM_SHA256(0x1304u),
    TLS_AES_128_CCM_8_SHA256(0x1305u)
} 