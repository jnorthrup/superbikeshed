package borg.trikeshed.ssh

import borg.trikeshed.lib.*
import kotlinx.coroutines.*

/**
 * SSH Configuration File Reader
 * 
 * Parses ~/.ssh/config and related SSH configuration files
 */

// SSH key types defined in SSHTaxonomy.kt

// Configuration taxonomy
typealias SSHConfigHost = String
typealias SSHConfigKey = String
typealias SSHConfigValue = String
typealias SSHConfigEntry = Join<SSHConfigKey, SSHConfigValue>
typealias SSHConfigSection = Indexed<SSHConfigEntry>
typealias SSHConfig = Join<SSHConfigHost, SSHConfigSection>
typealias SSHConfigFile = Indexed<SSHConfig>

// Known hosts taxonomy
typealias SSHKnownHostEntry = Join<SSHHostPattern, Join<SSHKeyType, SSHPublicKey>>
typealias SSHHostPattern = String
typealias SSHKeyType = String
typealias SSHKnownHostsFile = Indexed<SSHKnownHostEntry>

// Authorized keys taxonomy
typealias SSHAuthorizedKey = Join<SSHKeyOptions, Join<SSHKeyType, Join<SSHPublicKey, SSHKeyComment>>>
typealias SSHKeyOptions = Indexed<String>
typealias SSHKeyComment = String
typealias SSHAuthorizedKeysFile = Indexed<SSHAuthorizedKey>

// SSH config keys enum
enum class SSHConfigOption(val key: String) {
    HOST("Host"),
    HOSTNAME("HostName"),
    USER("User"),
    PORT("Port"),
    IDENTITY_FILE("IdentityFile"),
    IDENTITY_AGENT("IdentityAgent"),
    PREFERRED_AUTHENTICATIONS("PreferredAuthentications"),
    PASSWORD_AUTHENTICATION("PasswordAuthentication"),
    PUBKEY_AUTHENTICATION("PubkeyAuthentication"),
    KEYBOARD_INTERACTIVE_AUTHENTICATION("KbdInteractiveAuthentication"),
    CHALLENGE_RESPONSE_AUTHENTICATION("ChallengeResponseAuthentication"),
    USE_KEYCHAIN("UseKeychain"),
    ADD_KEYS_TO_AGENT("AddKeysToAgent"),
    FORWARD_AGENT("ForwardAgent"),
    FORWARD_X11("ForwardX11"),
    FORWARD_X11_TRUSTED("ForwardX11Trusted"),
    PROXY_COMMAND("ProxyCommand"),
    PROXY_JUMP("ProxyJump"),
    LOCAL_FORWARD("LocalForward"),
    REMOTE_FORWARD("RemoteForward"),
    DYNAMIC_FORWARD("DynamicForward"),
    COMPRESSION("Compression"),
    COMPRESSION_LEVEL("CompressionLevel"),
    CIPHERS("Ciphers"),
    MACS("MACs"),
    KEX_ALGORITHMS("KexAlgorithms"),
    HOST_KEY_ALGORITHMS("HostKeyAlgorithms"),
    CONNECT_TIMEOUT("ConnectTimeout"),
    SERVER_ALIVE_INTERVAL("ServerAliveInterval"),
    SERVER_ALIVE_COUNT_MAX("ServerAliveCountMax"),
    TCP_KEEP_ALIVE("TCPKeepAlive"),
    CONTROL_MASTER("ControlMaster"),
    CONTROL_PATH("ControlPath"),
    CONTROL_PERSIST("ControlPersist"),
    STRICT_HOST_KEY_CHECKING("StrictHostKeyChecking"),
    USER_KNOWN_HOSTS_FILE("UserKnownHostsFile"),
    GLOBAL_KNOWN_HOSTS_FILE("GlobalKnownHostsFile"),
    HASH_KNOWN_HOSTS("HashKnownHosts"),
    CHECK_HOST_IP("CheckHostIP"),
    LOG_LEVEL("LogLevel"),
    SEND_ENV("SendEnv"),
    SET_ENV("SetEnv"),
    INCLUDE("Include");
    
    companion object {
        internal val map = values().associateBy { it.key.lowercase() }
        fun fromKey(key: String) = map[key.lowercase()]
    }
}

// Config parser interface
interface SSHConfigParser {
    suspend fun parseConfig(content: String): SSHConfigFile
    suspend fun parseKnownHosts(content: String): SSHKnownHostsFile
    suspend fun parseAuthorizedKeys(content: String): SSHAuthorizedKeysFile
}

// Config reader interface
interface SSHConfigReader {
    suspend fun readUserConfig(): SSHConfigFile
    suspend fun readSystemConfig(): SSHConfigFile
    suspend fun readKnownHosts(): SSHKnownHostsFile
    suspend fun readAuthorizedKeys(): SSHAuthorizedKeysFile
    suspend fun findIdentityFiles(host: String): Indexed<String>
}

// Default parser implementation
class DefaultSSHConfigParser : SSHConfigParser {
    override suspend fun parseConfig(content: String): SSHConfigFile = coroutineScope {
        val configs = mutableListOf<SSHConfig>()
        var currentHost: String? = null
        var currentEntries = mutableListOf<SSHConfigEntry>()
        
        content.lines().forEach { line ->
            val trimmed = line.trim()
            
            // Skip empty lines and comments
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            
            // Parse key-value pairs
            val parts = trimmed.split(Regex("\\s+"), limit = 2)
            if (parts.size < 2) return@forEach
            
            val key = parts[0]
            val value = parts[1].trim()
            
            // Check if this is a Host directive
            if (key.equals("Host", ignoreCase = true)) {
                // Save previous host section
                currentHost?.let { host ->
                    val entries = currentEntries.size j { i: Int -> currentEntries[i] }
                    configs.add(Join(host, entries))
                }
                
                // Start new host section
                currentHost = value
                currentEntries = mutableListOf()
            } else {
                // Add to current section
                currentEntries.add(Join(key, value))
            }
        }
        
        // Save final host section
        currentHost?.let { host ->
            val entries = currentEntries.size j { i: Int -> currentEntries[i] }
            configs.add(Join(host, entries))
        }
        
        configs.size j { i: Int -> configs[i] }
    }
    
    override suspend fun parseKnownHosts(content: String): SSHKnownHostsFile = coroutineScope {
        val entries = mutableListOf<SSHKnownHostEntry>()
        
        content.lines().forEach { line ->
            val trimmed = line.trim()
            
            // Skip empty lines and comments
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            
            // Handle @cert-authority and @revoked markers
            val (marker, actualLine) = when {
                trimmed.startsWith("@cert-authority ") -> "cert-authority" to trimmed.substring(16)
                trimmed.startsWith("@revoked ") -> "revoked" to trimmed.substring(9)
                else -> null to trimmed
            }
            
            // Parse: hostpattern keytype base64key [comment]
            val parts = actualLine.split(Regex("\\s+"), limit = 3)
            if (parts.size < 3) return@forEach
            
            val hostPattern = parts[0]
            val keyType = parts[1]
            val keyData = parts[2].split(" ")[0] // Remove any trailing comment
            
            // Decode base64 key
            val keyBytes = try {
                decodeBase64(keyData)
            } catch (e: Exception) {
                return@forEach
            }
            
            val publicKey = keyBytes.size j { i: Int -> keyBytes[i] }
            entries.add(Join(hostPattern, Join(keyType, publicKey)))
        }
        
        entries.size j { i: Int -> entries[i] }
    }
    
    override suspend fun parseAuthorizedKeys(content: String): SSHAuthorizedKeysFile = coroutineScope {
        val keys = mutableListOf<SSHAuthorizedKey>()
        
        content.lines().forEach { line ->
            val trimmed = line.trim()
            
            // Skip empty lines and comments
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            
            // Parse options if present
            val (options, keyPart) = if (trimmed.contains(" ") && !trimmed.startsWith("ssh-")) {
                // Has options
                val firstSpace = trimmed.indexOf(' ')
                val optionString = trimmed.substring(0, firstSpace)
                val remainder = trimmed.substring(firstSpace + 1)
                parseKeyOptions(optionString) to remainder
            } else {
                // No options
                (0 j { _: Int -> "" }) to trimmed
            }
            
            // Parse key type and data
            val parts = keyPart.split(Regex("\\s+"), limit = 3)
            if (parts.size < 2) return@forEach
            
            val keyType = parts[0]
            val keyData = parts[1]
            val comment = if (parts.size > 2) parts[2] else ""
            
            // Decode key
            val keyBytes = try {
                decodeBase64(keyData)
            } catch (e: Exception) {
                return@forEach
            }
            
            val publicKey = keyBytes.size j { i: Int -> keyBytes[i] }
            keys.add(Join(options, Join(keyType, Join(publicKey, comment))))
        }
        
        keys.size j { i: Int -> keys[i] }
    }
    
    internal fun parseKeyOptions(optionString: String): SSHKeyOptions {
        val options = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        
        for (char in optionString) {
            when (char) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) {
                    options.add(current.toString())
                    current = StringBuilder()
                } else {
                    current.append(char)
                }
                else -> current.append(char)
            }
        }
        
        if (current.isNotEmpty()) {
            options.add(current.toString())
        }
        
        return options.size j { i: Int -> options[i] }
    }
}

// Base64 decoder (simplified)
internal fun decodeBase64(data: String): ByteArray {
    // Remove any whitespace
    val cleaned = data.replace(Regex("\\s"), "")
    
    // Simple base64 decoding (would use proper implementation)
    val bytes = mutableListOf<Byte>()
    
    // Placeholder - in real implementation would decode properly
    for (i in cleaned.indices step 4) {
        val chunk = cleaned.substring(i, minOf(i + 4, cleaned.length))
        // Decode chunk...
        bytes.add((i % 256).toByte())
    }
    
    return bytes.toByteArray()
}

// SSH directory structure
data class SSHDirectory(
    val path: String,
    val configFile: String = "config",
    val knownHostsFile: String = "known_hosts",
    val authorizedKeysFile: String = "authorized_keys",
    val privateKeyPattern: Regex = Regex("^id_[a-z0-9]+$")
)

// Config resolution
class SSHConfigResolver(
    internal val parser: SSHConfigParser = DefaultSSHConfigParser()
) {
    suspend fun resolveHost(hostname: String, config: SSHConfigFile): SSHResolvedConfig {
        val matchingConfigs = mutableListOf<SSHConfigSection>()
        
        // Find all matching host patterns
        for (i in 0 until config.a) {
            val hostPattern = config[i].a
            val section = config[i].b
            
            if (matchesHostPattern(hostname, hostPattern)) {
                matchingConfigs.add(section)
            }
        }
        
        // Merge configurations (first match wins)
        val merged = mutableMapOf<String, String>()
        matchingConfigs.forEach { section ->
            for (j in 0 until section.a) {
                val key = section[j].a
                val value = section[j].b
                
                // Only set if not already set (first match wins)
                if (!merged.containsKey(key)) {
                    merged[key] = value
                }
            }
        }
        
        return SSHResolvedConfig(
            hostname = merged["HostName"] ?: hostname,
            user = merged["User"] ?: "user",  // Default user, platform-specific in actual implementations
            port = merged["Port"]?.toIntOrNull() ?: 22,
            identityFiles = parseIdentityFiles(merged["IdentityFile"]),
            options = merged
        )
    }
    
    internal fun matchesHostPattern(hostname: String, pattern: String): Boolean {
        // Handle wildcards
        val regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".")
        
        return hostname.matches(Regex(regex))
    }
    
    internal fun parseIdentityFiles(value: String?): List<String> {
        if (value == null) return emptyList()
        
        // Identity files can be space-separated
        return value.split(Regex("\\s+"))
            .map { expandPath(it) }
            .filter { it.isNotEmpty() }
    }
    
    internal fun expandPath(path: String): String {
        return path
            .replace("~", "/home/user")  // TODO: Platform-specific home directory
            .replace("%d", "/home/user")  // TODO: Platform-specific home directory
            .replace("%u", "user")  // TODO: Platform-specific username
            .replace("%l", getLocalHostname())
    }
    
    internal fun getLocalHostname(): String {
        // Platform-specific implementation needed
        return "localhost"
    }
}

// Resolved configuration
data class SSHResolvedConfig(
    val hostname: String,
    val user: String,
    val port: Int,
    val identityFiles: List<String>,
    val options: Map<String, String>
)

// Key file reader
interface SSHKeyReader {
    suspend fun readPrivateKey(path: String, passphrase: String?): SSHPrivateKey
    suspend fun readPublicKey(path: String): SSHPublicKey
    suspend fun readKeyPair(privatePath: String, passphrase: String?): SSHIdentity
}

// OpenSSH key format parser
class OpenSSHKeyParser : SSHKeyReader {
    override suspend fun readPrivateKey(path: String, passphrase: String?): SSHPrivateKey = coroutineScope {
        val content = readFile(path)
        
        // Detect key format
        val format = SSHKeyFormat.detectFormat(content)
            ?: throw IllegalArgumentException("Unknown key format")
        
        when (format) {
            SSHKeyFormat.OPENSSH_PRIVATE -> parseOpenSSHPrivateKey(content, passphrase)
            SSHKeyFormat.RSA_PRIVATE -> parseRSAPrivateKey(content, passphrase)
            SSHKeyFormat.EC_PRIVATE -> parseECPrivateKey(content, passphrase)
            else -> throw IllegalArgumentException("Unsupported internal key format: $format")
        }
    }
    
    override suspend fun readPublicKey(path: String): SSHPublicKey = coroutineScope {
        val content = readFile(path)
        
        // OpenSSH public key format: keytype base64data [comment]
        val parts = content.trim().split(Regex("\\s+"), limit = 3)
        if (parts.size < 2) {
            throw IllegalArgumentException("Invalid public key format")
        }
        
        val keyType = parts[0]
        val keyData = parts[1]
        
        val decoded = decodeBase64(keyData)
        decoded.size j { i: Int -> decoded[i] }
    }
    
    override suspend fun readKeyPair(privatePath: String, passphrase: String?): SSHIdentity = coroutineScope {
        val privateKey = readPrivateKey(privatePath, passphrase)
        
        // Try to read public key
        val publicKey = try {
            readPublicKey("$privatePath.pub")
        } catch (e: Exception) {
            // Derive public key from internal key
            derivePublicKey(privateKey)
        }
        
        Join(publicKey, privateKey)
    }
    
    internal suspend fun parseOpenSSHPrivateKey(content: String, passphrase: String?): SSHPrivateKey {
        // Remove header/footer
        val base64 = content
            .replace(SSHKeyFormat.OPENSSH_PRIVATE.header, "")
            .replace(SSHKeyFormat.OPENSSH_PRIVATE.footer, "")
            .trim()
        
        val decoded = decodeBase64(base64)
        
        // Parse OpenSSH format
        // Magic: "openssh-key-v1\0"
        // cipher name
        // kdf name
        // kdf options
        // number of keys (usually 1)
        // public key
        // encrypted internal key
        
        // Simplified - return decoded data
        return decoded.size j { i: Int -> decoded[i] }
    }
    
    internal suspend fun parseRSAPrivateKey(content: String, passphrase: String?): SSHPrivateKey {
        // PEM format RSA key
        val base64 = content
            .replace(SSHKeyFormat.RSA_PRIVATE.header, "")
            .replace(SSHKeyFormat.RSA_PRIVATE.footer, "")
            .trim()
        
        val decoded = decodeBase64(base64)
        return decoded.size j { i: Int -> decoded[i] }
    }
    
    internal suspend fun parseECPrivateKey(content: String, passphrase: String?): SSHPrivateKey {
        // PEM format EC key
        val base64 = content
            .replace(SSHKeyFormat.EC_PRIVATE.header, "")
            .replace(SSHKeyFormat.EC_PRIVATE.footer, "")
            .trim()
        
        val decoded = decodeBase64(base64)
        return decoded.size j { i: Int -> decoded[i] }
    }
    
    internal fun derivePublicKey(privateKey: SSHPrivateKey): SSHPublicKey {
        // Derive public key from internal key
        // Implementation depends on key type
        return 32 j { i: Int -> (i * 7).toByte() } // Placeholder
    }
}

// Platform-specific file reading
expect suspend fun readFile(path: String): String
expect suspend fun writeFile(path: String, content: String)
expect suspend fun fileExists(path: String): Boolean
expect suspend fun listFiles(directory: String, pattern: Regex?): List<String>

// SSH home directory helper
expect fun getSSHDirectory(): String

// Config file locator
class SSHFileLocator {
    fun getUserConfigPath(): String = "${getSSHDirectory()}/config"
    fun getSystemConfigPath(): String = "/etc/ssh/ssh_config"
    fun getUserKnownHostsPath(): String = "${getSSHDirectory()}/known_hosts"
    fun getSystemKnownHostsPath(): String = "/etc/ssh/ssh_known_hosts"
    fun getUserAuthorizedKeysPath(): String = "${getSSHDirectory()}/authorized_keys"
    
    suspend fun findIdentityFiles(): List<String> = coroutineScope {
        val sshDir = getSSHDirectory()
        val files = listFiles(sshDir, Regex("^id_[a-z0-9_]+$"))
        
        files.filter { file ->
            // Only return internal keys (not .pub files)
            !file.endsWith(".pub") && fileExists("$sshDir/$file")
        }.map { "$sshDir/$it" }
    }
}