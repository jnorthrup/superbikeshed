package com.v2superbikeshed.nexus.rpc.mcp_adapters

import org.apache.commons.codec.binary.Base64

fun String.decodeBase64Bytes(): ByteArray {
    return Base64.decodeBase64(this)
}

fun ByteArray.encodeBase64(): String {
    return Base64.encodeBase64String(this)
}
