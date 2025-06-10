package borg.trikeshed.net

import borg.trikeshed.io.network.NetworkAddress
import kotlinx.cinterop.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
internal object NativeNetworkUtils {
    /**
     * Converts a common [NetworkAddress] to a native `sockaddr_in` structure.
     * This is a blocking operation and should ideally be done off the main thread if performance is critical.
     */
    fun networkAddressToNativeSockaddr(networkAddress: NetworkAddress): CValue<sockaddr_in>? {
        return memScoped {
            val addrInfo = allocPointerTo<addrinfo>()
            val hints = alloc<addrinfo>()
            platform.posix.memset(hints.ptr, 0, sizeOf<addrinfo>().convert())
            hints.ai_family = AF_INET // IPv4
            hints.ai_socktype = SOCK_STREAM // Or SOCK_DGRAM for UDP, depending on context
            hints.ai_flags = AI_PASSIVE // For bind, or 0 for connect

            val result = getaddrinfo(networkAddress.first, networkAddress.second.toString(), hints.ptr, addrInfo)
            if (result != 0) {
                println("getaddrinfo failed for ${networkAddress.first}:${networkAddress.second}: ${gai_strerror(result)?.toKString()}")
                return@memScoped null
            }
            val resolvedAddrInfo = addrInfo.pointed ?: return@memScoped null
            val sockaddrPtr = resolvedAddrInfo.ai_addr?.reinterpret<sockaddr_in>() ?: return@memScoped null

            val nativeAddr = alloc<sockaddr_in>()
            nativeAddr.sin_family = sockaddrPtr.pointed.sin_family
            nativeAddr.sin_port = sockaddrPtr.pointed.sin_port
            nativeAddr.sin_addr.s_addr = sockaddrPtr.pointed.sin_addr.s_addr
            freeaddrinfo(addrInfo.value)
            return@memScoped nativeAddr.readValue()
        }
    }
}
