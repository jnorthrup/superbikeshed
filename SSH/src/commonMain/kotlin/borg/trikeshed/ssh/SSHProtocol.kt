@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")
package borg.trikeshed.ssh

import borg.trikeshed.lib.*
import kotlin.jvm.JvmInline

/**
 * SSH Protocol Constants and Core Types (RFC 4251-4254)
 */

object SSHProtocol {
    const val VERSION = "SSH-2.0-TrikeShed_1.0"
    const val MIN_PACKET_SIZE = 16
    const val MAX_PACKET_SIZE = 35000
    const val DEFAULT_WINDOW_SIZE = 2097152 // 2MB
    const val DEFAULT_MAX_PACKET_SIZE = 32768 // 32KB
}

// Message types as enum
enum class SSHMessageType(val value: Byte) {
    // Transport layer protocol
    DISCONNECT(1),
    IGNORE(2),
    UNIMPLEMENTED(3),
    DEBUG(4),
    SERVICE_REQUEST(5),
    SERVICE_ACCEPT(6),
    
    // Algorithm negotiation
    KEXINIT(20),
    NEWKEYS(21),
    
    // Key exchange method specific (30-49)
    KEXDH_INIT(30),
    KEXDH_REPLY(31),
    KEX_ECDH_INIT(30),
    KEX_ECDH_REPLY(31),
    
    // User authentication protocol
    USERAUTH_REQUEST(50),
    USERAUTH_FAILURE(51),
    USERAUTH_SUCCESS(52),
    USERAUTH_BANNER(53),
    USERAUTH_INFO_REQUEST(60),
    USERAUTH_INFO_RESPONSE(61),
    USERAUTH_PK_OK(60),
    
    // Connection protocol
    GLOBAL_REQUEST(80),
    REQUEST_SUCCESS(81),
    REQUEST_FAILURE(82),
    CHANNEL_OPEN(90),
    CHANNEL_OPEN_CONFIRMATION(91),
    CHANNEL_OPEN_FAILURE(92),
    CHANNEL_WINDOW_ADJUST(93),
    CHANNEL_DATA(94),
    CHANNEL_EXTENDED_DATA(95),
    CHANNEL_EOF(96),
    CHANNEL_CLOSE(97),
    CHANNEL_REQUEST(98),
    CHANNEL_SUCCESS(99),
    CHANNEL_FAILURE(100);
    
    companion object {
        private val map = values().associateBy(SSHMessageType::value)
        fun fromByte(type: Byte) = map[type]
    }
}

// Disconnect reasons as enum
enum class SSHDisconnectReason(val code: UInt) {
    HOST_NOT_ALLOWED_TO_CONNECT(1u),
    PROTOCOL_ERROR(2u),
    KEY_EXCHANGE_FAILED(3u),
    RESERVED(4u),
    MAC_ERROR(5u),
    COMPRESSION_ERROR(6u),
    SERVICE_NOT_AVAILABLE(7u),
    PROTOCOL_VERSION_NOT_SUPPORTED(8u),
    HOST_KEY_NOT_VERIFIABLE(9u),
    CONNECTION_LOST(10u),
    BY_APPLICATION(11u),
    TOO_MANY_CONNECTIONS(12u),
    AUTH_CANCELLED_BY_USER(13u),
    NO_MORE_AUTH_METHODS_AVAILABLE(14u),
    ILLEGAL_USER_NAME(15u);
    
    companion object {
        private val map = values().associateBy(SSHDisconnectReason::code)
        fun fromCode(code: UInt) = map[code]
    }
}

// Channel open failure reasons
enum class SSHChannelOpenFailure(val code: UInt) {
    ADMINISTRATIVELY_PROHIBITED(1u),
    CONNECT_FAILED(2u),
    UNKNOWN_CHANNEL_TYPE(3u),
    RESOURCE_SHORTAGE(4u);
    
    companion object {
        private val map = values().associateBy(SSHChannelOpenFailure::code)
        fun fromCode(code: UInt) = map[code]
    }
}

// Channel types
enum class SSHChannelType(val typeName: String) {
    SESSION("session"),
    X11("x11"),
    FORWARDED_TCPIP("forwarded-tcpip"),
    DIRECT_TCPIP("direct-tcpip");
    
    companion object {
        private val map = values().associateBy(SSHChannelType::typeName)
        fun fromName(name: String) = map[name]
    }
}

// Channel requests
enum class SSHChannelRequest(val requestType: String) {
    PTY_REQ("pty-req"),
    X11_REQ("x11-req"),
    ENV("env"),
    SHELL("shell"),
    EXEC("exec"),
    SUBSYSTEM("subsystem"),
    WINDOW_CHANGE("window-change"),
    XON_XOFF("xon-xoff"),
    SIGNAL("signal"),
    EXIT_STATUS("exit-status"),
    EXIT_SIGNAL("exit-signal");
    
    companion object {
        private val map = values().associateBy(SSHChannelRequest::requestType)
        fun fromType(type: String) = map[type]
    }
}

// Global requests
enum class SSHGlobalRequest(val requestType: String) {
    TCPIP_FORWARD("tcpip-forward"),
    CANCEL_TCPIP_FORWARD("cancel-tcpip-forward");
    
    companion object {
        private val map = values().associateBy(SSHGlobalRequest::requestType)
        fun fromType(type: String) = map[type]
    }
}

// Services
enum class SSHService(val serviceName: String) {
    USERAUTH("ssh-userauth"),
    CONNECTION("ssh-connection");
    
    companion object {
        private val map = values().associateBy(SSHService::serviceName)
        fun fromName(name: String) = map[name]
    }
}

// Authentication methods
enum class SSHAuthMethod(val methodName: String) {
    NONE("none"),
    PUBLICKEY("publickey"),
    PASSWORD("password"),
    HOSTBASED("hostbased"),
    KEYBOARD_INTERACTIVE("keyboard-interactive");
    
    companion object {
        private val map = values().associateBy(SSHAuthMethod::methodName)
        fun fromName(name: String) = map[name]
    }
}

// Signals
enum class SSHSignalType(val signalName: String) {
    ABRT("ABRT"),
    ALRM("ALRM"),
    FPE("FPE"),
    HUP("HUP"),
    ILL("ILL"),
    INT("INT"),
    KILL("KILL"),
    PIPE("PIPE"),
    QUIT("QUIT"),
    SEGV("SEGV"),
    TERM("TERM"),
    USR1("USR1"),
    USR2("USR2");
    
    companion object {
        private val map = values().associateBy(SSHSignalType::signalName)
        fun fromName(name: String) = map[name]
    }
}

// Terminal modes
enum class SSHTerminalMode(val opcode: Byte) {
    TTY_OP_END(0),
    
    // Control characters
    VINTR(1),
    VQUIT(2),
    VERASE(3),
    VKILL(4),
    VEOF(5),
    VEOL(6),
    VEOL2(7),
    VSTART(8),
    VSTOP(9),
    VSUSP(10),
    VDSUSP(11),
    VREPRINT(12),
    VWERASE(13),
    VLNEXT(14),
    VFLUSH(15),
    VSWTCH(16),
    VSTATUS(17),
    VDISCARD(18),
    
    // Input modes
    IGNPAR(30),
    PARMRK(31),
    INPCK(32),
    ISTRIP(33),
    INLCR(34),
    IGNCR(35),
    ICRNL(36),
    IUCLC(37),
    IXON(38),
    IXANY(39),
    IXOFF(40),
    IMAXBEL(41),
    
    // Local modes
    ISIG(50),
    ICANON(51),
    XCASE(52),
    ECHO(53),
    ECHOE(54),
    ECHOK(55),
    ECHONL(56),
    NOFLSH(57),
    TOSTOP(58),
    IEXTEN(59),
    ECHOCTL(60),
    ECHOKE(61),
    PENDIN(62),
    
    // Output modes
    OPOST(70),
    OLCUC(71),
    ONLCR(72),
    OCRNL(73),
    ONOCR(74),
    ONLRET(75),
    
    // Control modes
    CS7(90),
    CS8(91),
    PARENB(92),
    PARODD(93),
    
    // Baud rates
    TTY_OP_ISPEED(128.toByte()),
    TTY_OP_OSPEED(129.toByte());
    
    companion object {
        private val map = values().associateBy(SSHTerminalMode::opcode)
        fun fromOpcode(code: Byte) = map[code]
    }
}

// Extended data types
enum class SSHExtendedDataType(val type: UInt) {
    STDERR(1u);
    
    companion object {
        private val map = values().associateBy(SSHExtendedDataType::type)
        fun fromType(type: UInt) = map[type]
    }
}

// Algorithm preferences with ordered lists
sealed class AlgorithmPreferences {
    abstract val preferences: List<String>
    
    object KeyExchange : AlgorithmPreferences() {
        override val preferences = listOf(
            "curve25519-sha256",
            "curve25519-sha256@libssh.org",
            "ecdh-sha2-nistp256",
            "ecdh-sha2-nistp384",
            "ecdh-sha2-nistp521",
            "diffie-hellman-group-exchange-sha256",
            "diffie-hellman-group16-sha512",
            "diffie-hellman-group18-sha512",
            "diffie-hellman-group14-sha256"
        )
    }
    
    object HostKey : AlgorithmPreferences() {
        override val preferences = listOf(
            "ssh-ed25519",
            "ecdsa-sha2-nistp256",
            "ecdsa-sha2-nistp384",
            "ecdsa-sha2-nistp521",
            "rsa-sha2-512",
            "rsa-sha2-256",
            "ssh-rsa"
        )
    }
    
    object Cipher : AlgorithmPreferences() {
        override val preferences = listOf(
            "chacha20-poly1305@openssh.com",
            "aes128-gcm@openssh.com",
            "aes256-gcm@openssh.com",
            "aes128-ctr",
            "aes192-ctr",
            "aes256-ctr"
        )
    }
    
    object MAC : AlgorithmPreferences() {
        override val preferences = listOf(
            "umac-64-etm@openssh.com",
            "umac-128-etm@openssh.com",
            "hmac-sha2-256-etm@openssh.com",
            "hmac-sha2-512-etm@openssh.com",
            "hmac-sha1-etm@openssh.com",
            "umac-64@openssh.com",
            "umac-128@openssh.com",
            "hmac-sha2-256",
            "hmac-sha2-512",
            "hmac-sha1"
        )
    }
    
    object Compression : AlgorithmPreferences() {
        override val preferences = listOf(
            "none",
            "zlib@openssh.com",
            "zlib"
        )
    }
}

// Key format types
enum class SSHKeyFormat(val header: String, val footer: String) {
    OPENSSH_PRIVATE(
        "-----BEGIN OPENSSH PRIVATE KEY-----",
        "-----END OPENSSH PRIVATE KEY-----"
    ),
    RSA_PRIVATE(
        "-----BEGIN RSA PRIVATE KEY-----",
        "-----END RSA PRIVATE KEY-----"
    ),
    EC_PRIVATE(
        "-----BEGIN EC PRIVATE KEY-----",
        "-----END EC PRIVATE KEY-----"
    ),
    RSA_PUBLIC(
        "-----BEGIN RSA PUBLIC KEY-----",
        "-----END RSA PUBLIC KEY-----"
    ),
    PUBLIC_KEY(
        "-----BEGIN PUBLIC KEY-----",
        "-----END PUBLIC KEY-----"
    );
    
    companion object {
        fun detectFormat(content: String): SSHKeyFormat? {
            return values().find { content.contains(it.header) }
        }
    }
}

// Cipher types for key encryption
enum class SSHKeyCipher(val algorithm: String) {
    NONE("none"),
    AES256_CTR("aes256-ctr"),
    AES256_CBC("aes256-cbc"),
    AES128_CTR("aes128-ctr"),
    AES128_CBC("aes128-cbc");
    
    companion object {
        private val map = values().associateBy(SSHKeyCipher::algorithm)
        fun fromAlgorithm(alg: String) = map[alg] ?: NONE
    }
}

// Hash algorithms
enum class SSHHashAlgorithm(val algorithm: String) {
    SHA1("sha1"),
    SHA256("sha256"),
    SHA384("sha384"),
    SHA512("sha512"),
    MD5("md5");
    
    companion object {
        private val map = values().associateBy(SSHHashAlgorithm::algorithm)
        fun fromName(name: String) = map[name]
    }
}

// Constants that don't fit into enums
object SSHConstants {
    const val AUTH_MAGIC = "openssh-key-v1\u0000"
    const val MAX_CHANNEL_WINDOW = 4194304 // 4MB
    const val INITIAL_WINDOW_SIZE = 2097152 // 2MB
    const val MAX_PACKET_SIZE = 32768 // 32KB
}