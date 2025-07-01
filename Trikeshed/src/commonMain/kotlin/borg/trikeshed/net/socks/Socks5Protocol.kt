package borg.trikeshed.net.socks

import borg.trikeshed.lib.Indexed
import kotlin.jvm.JvmInline

// === SOCKS5 TAXONOMICAL TYPEALIASES ===

typealias SocksAddress = Indexed<Byte>

// Value classes for type safety
@JvmInline value class SocksVersion(val value: Byte)
@JvmInline value class SocksMethod(val value: Byte)
@JvmInline value class SocksCommand(val value: Byte)
@JvmInline value class SocksAddressType(val value: Byte)
@JvmInline value class SocksReplyCode(val value: Byte)

/**
 * SOCKS5 Protocol Constants (RFC 1928)
 */
object Socks5Protocol {
    const val VERSION: Byte = 0x05

    object Methods {
        const val NO_AUTHENTICATION_REQUIRED: SocksMethod = 0x00
        const val GSSAPI: SocksMethod = 0x01
        const val USERNAME_PASSWORD: SocksMethod = 0x02
        const val NO_ACCEPTABLE_METHODS: SocksMethod = 0xFF
    }

    object Commands {
        const val CONNECT: SocksCommand = 0x01
        const val BIND: SocksCommand = 0x02
        const val UDP_ASSOCIATE: SocksCommand = 0x03
    }

    object AddressTypes {
        const val IPV4: SocksAddressType = 0x01
        const val DOMAINNAME: SocksAddressType = 0x03
        const val IPV6: SocksAddressType = 0x04
    }

    object ReplyCodes {
        const val SUCCEEDED: SocksReplyCode = 0x00
        const val SOCKS_SERVER_FAILURE: SocksReplyCode = 0x01
        const val NETWORK_UNREACHABLE: SocksReplyCode = 0x03
        const val HOST_UNREACHABLE: SocksReplyCode = 0x04
        const val CONNECTION_REFUSED: SocksReplyCode = 0x05
        const val TTL_EXPIRED: SocksReplyCode = 0x06
        const val COMMAND_NOT_SUPPORTED: SocksReplyCode = 0x07
        const val ADDRESS_TYPE_NOT_SUPPORTED: SocksReplyCode = 0x08
    }
}
