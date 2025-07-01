package borg.trikeshed.lib

import borg.trikeshed.lib.Indexed

typealias ByteIndexed = Indexed<Byte>
typealias IntIndexed = Indexed<Int>

fun ByteArray.toIndexed(): Indexed<Byte> = this.size j { this[it] }
fun IntArray.toIndexed(): Indexed<Int> = this.size j { this[it] }

// Taxonomical ontological typealiases for external dependencies
@Deprecated("Use system lz4 tool via exec instead")
typealias LZ4FrameInputStream = java.io.InputStream

@Deprecated("Use system zstd tool via exec instead")
typealias ZstdJni = Any

@Deprecated("Use kotlinx.serialization instead")
typealias serialization = kotlinx.serialization

@Deprecated("Use kotlinx.serialization.json.Json instead")
typealias Json = kotlinx.serialization.json.Json 