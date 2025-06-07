package borg.trikeshed.net.quic.crypto

// Using simple string content to differentiate the byte arrays for mock parsing.
val MOCK_CA_CERT_BYTES = "CA_CERT_PLACEHOLDER_SELF_SIGNED_CN_TestCA".encodeToByteArray()
val MOCK_SERVER_CERT_BYTES_VALID_ISSUER_VALID_SAN = "SERVER_CERT_CN_test.quic.server_ISSUER_CN_TestCA_SAN_test.quic.server".encodeToByteArray()
val MOCK_SERVER_CERT_BYTES_VALID_ISSUER_DIFFERENT_SAN = "SERVER_CERT_CN_other.domain.com_ISSUER_CN_TestCA_SAN_other.domain.com".encodeToByteArray()
val MOCK_SERVER_CERT_BYTES_DIFFERENT_ISSUER = "SERVER_CERT_CN_test.quic.server_ISSUER_CN_DifferentCA".encodeToByteArray()
val MOCK_UNTRUSTED_CA_CERT_BYTES = "UNTRUSTED_CA_CERT_PLACEHOLDER_SELF_SIGNED_CN_UntrustedCA".encodeToByteArray()

val EXPECTED_SERVER_NAME = "test.quic.server"

// Dummy public keys for mock verification
val DUMMY_CA_PUBLIC_KEY_BYTES = "dummy_ca_public_key".encodeToByteArray()
val DUMMY_SERVER_PUBLIC_KEY_BYTES = "dummy_server_public_key".encodeToByteArray()
val DUMMY_UNTRUSTED_CA_PUBLIC_KEY_BYTES = "dummy_untrusted_ca_public_key".encodeToByteArray()
val DUMMY_DIFFERENT_ISSUER_PUBLIC_KEY_BYTES = "dummy_different_issuer_public_key".encodeToByteArray()
