package evolution.io.node

import org.khronos.webgl.Uint8Array
import kotlin.js.JsAny // For EventEmitter.emit vararg

// External for NodeJS Error (very basic)
external interface Error {
    val message: String
    val name: String
    val stack: String?
}

// External for NodeJS EventEmitter (very basic from "events" module)
@JsModule("events")
@JsNonModule
// In Kotlin, we'd typically represent a namespace like 'events' as an object if it contains static members,
// or directly use its members if it's just a grouping. For EventEmitter, it's a class.
// The "events.EventEmitter" implies EventEmitter is a class within the "events" module.
external abstract class EventEmitter { // Made abstract class as it's meant to be extended
    fun on(event: String, listener: Function<*>) // Function<*> is a simplification
    fun once(event: String, listener: Function<*>)
    fun removeListener(event: String, listener: Function<*>)
    fun emit(event: String, vararg args: JsAny)
    fun removeAllListeners(event: String? = definedExternally)
}


@JsModule("dgram")
@JsNonModule
external object Dgram {
    fun createSocket(type: String /* "udp4" or "udp6" */, callback: ((msg: Uint8Array, rinfo: RemoteInfo) -> Unit)? = definedExternally): Socket
}

// Node.js Socket extends events.EventEmitter
external interface Socket : EventEmitter {
    fun bind(port: Int? = definedExternally, address: String? = definedExternally, callback: (() -> Unit)? = definedExternally)
    fun send(msg: Uint8Array, port: Int, address: String, callback: ((error: Error?, bytes: Int) -> Unit)? = definedExternally)
    fun send(msg: Array<Uint8Array>, port: Int, address: String, callback: ((error: Error?, bytes: Int) -> Unit)? = definedExternally) // Overload for multi-buffer send
    fun close(callback: (() -> Unit)? = definedExternally)
    fun address(): AddressInfo
    // on, once, etc., are inherited from EventEmitter
}

external interface RemoteInfo {
    val address: String
    val family: String // "IPv4" or "IPv6"
    val port: Int
    val size: Int
}

external interface AddressInfo {
    val address: String
    val family: String
    val port: Int
}
