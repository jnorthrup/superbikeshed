package borg.trikeshed.lib

interface Usable {
    fun close()
    fun open()
}

interface IOOperation {
    val type: IOOperationType
    val fd: Int
    val buffer: ByteArray
    val offset: Long
    val flags: Int
    val priority: Int
}

interface IOResult {
    val operationId: Long
    val bytesTransferred: Int
    val error: Int
    val flags: Int
    val timestamp: Long
    val isSuccess: Boolean
    val isError: Boolean
}

enum class IOOperationType {
    READ, WRITE, READV, WRITEV, POLL, ACCEPT, CONNECT, SEND, RECV
}

interface PlatformServiceInvoker {
    suspend fun invokeService(serviceName: String, data: ByteArray): ByteArray
}

interface PlatformCodec {
    fun encode(data: ByteArray): ByteArray
    fun decode(data: ByteArray): ByteArray
}

expect class Point {
    val x: Double
    val y: Double
}

expect class Window {
    val width: Int
    val height: Int
}

expect class Winsize {
    val ws_row: UShort
    val ws_col: UShort
    val ws_xpixel: UShort
    val ws_ypixel: UShort
}

expect fun pack(data: ByteArray): ByteArray
expect fun parse(data: ByteArray): Any
expect fun stringify(data: Any): String
expect fun invokeService(serviceName: String, data: ByteArray): ByteArray 