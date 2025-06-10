package evolution

import kotlin.coroutines.CoroutineContext // Required for CoroutineContext parameter
// Crypto operations are now handled through the evolution.Crypto expect object in QuicCurl.kt
// Forward declaration for QuicPacket, QuicInitialKeys, QuicConnection if not already visible
// For the purpose of this file, assume they are defined elsewhere in the 'evolution' package or imported.
// e.g. import evolution.QuicPacket (if in a different file)

internal expect suspend fun protectPacket(
    context: CoroutineContext, // Added CoroutineContext to access CCEK services
    packet: QuicPacket,       // Assuming QuicPacket is defined
    keys: QuicInitialKeys,    // Assuming QuicInitialKeys is defined
    connection: QuicConnection // Assuming QuicConnection is defined
): ByteArray

// Added for packet decryption and header unprotection
internal expect suspend fun unprotectPacket(
    context: CoroutineContext,
    protectedPacketBytes: ByteArray, // The raw bytes received from the wire
    keys: QuicInitialKeys,           // Keys for the current encryption level
    connection: QuicConnection      // Connection context, e.g., for expected PN
): QuicPacket? // Returns null if unprotection fails (e.g., GCM tag mismatch)