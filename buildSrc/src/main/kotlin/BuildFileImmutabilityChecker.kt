package buildtools

import java.io.File
import java.security.MessageDigest
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * Enforces build file immutability policy.
 * Tracks checksums and modification permissions for build files.
 */
object BuildFileImmutabilityChecker {
    
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true 
    }
    
    private val checksumFile = File(".gradle/build-file-checksums.json")
    
    @Serializable
    data class FileChecksum(
        val path: String,
        val checksum: String,
        val lastModified: Long,
        val permissions: List<Permission> = emptyList()
    )
    
    @Serializable
    data class Permission(
        val grantedAt: Long,
        val purpose: String,
        val expiresAt: Long? = null,
        val grantedBy: String = System.getProperty("user.name")
    )
    
    @Serializable
    data class ChecksumDatabase(
        val files: Map<String, FileChecksum> = emptyMap(),
        val lastChecked: Long = Instant.now().toEpochMilli()
    )
    
    fun loadDatabase(): ChecksumDatabase {
        return if (checksumFile.exists()) {
            try {
                json.decodeFromString(checksumFile.readText())
            } catch (e: Exception) {
                println("Warning: Could not load checksum database: ${e.message}")
                ChecksumDatabase()
            }
        } else {
            ChecksumDatabase()
        }
    }
    
    fun saveDatabase(database: ChecksumDatabase) {
        checksumFile.parentFile.mkdirs()
        checksumFile.writeText(json.encodeToString(database))
    }
    
    fun calculateChecksum(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = file.readBytes()
        val hashBytes = digest.digest(bytes)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    fun grantPermission(
        filePath: String, 
        purpose: String, 
        durationMinutes: Long? = null
    ) {
        val database = loadDatabase()
        val file = File(filePath)
        
        if (!file.exists()) {
            throw IllegalArgumentException("File does not exist: $filePath")
        }
        
        val permission = Permission(
            grantedAt = Instant.now().toEpochMilli(),
            purpose = purpose,
            expiresAt = durationMinutes?.let { 
                Instant.now().plusSeconds(it * 60).toEpochMilli() 
            }
        )
        
        val currentChecksum = database.files[filePath]
        val updatedChecksum = if (currentChecksum != null) {
            currentChecksum.copy(
                permissions = currentChecksum.permissions + permission
            )
        } else {
            FileChecksum(
                path = filePath,
                checksum = calculateChecksum(file),
                lastModified = file.lastModified(),
                permissions = listOf(permission)
            )
        }
        
        val updatedDatabase = database.copy(
            files = database.files + (filePath to updatedChecksum)
        )
        
        saveDatabase(updatedDatabase)
        println("Permission granted for $filePath: $purpose")
    }
    
    fun checkFile(file: File): ValidationResult {
        val database = loadDatabase()
        val relativePath = file.path
        val stored = database.files[relativePath]
        
        if (stored == null) {
            // First time seeing this file, record it
            val checksum = FileChecksum(
                path = relativePath,
                checksum = calculateChecksum(file),
                lastModified = file.lastModified()
            )
            
            val updatedDatabase = database.copy(
                files = database.files + (relativePath to checksum)
            )
            saveDatabase(updatedDatabase)
            
            return ValidationResult.NewFile(relativePath)
        }
        
        val currentChecksum = calculateChecksum(file)
        if (currentChecksum == stored.checksum) {
            return ValidationResult.Unchanged(relativePath)
        }
        
        // File has changed, check permissions
        val now = Instant.now().toEpochMilli()
        val activePermissions = stored.permissions.filter { permission ->
            permission.expiresAt == null || permission.expiresAt > now
        }
        
        if (activePermissions.isEmpty()) {
            return ValidationResult.UnauthorizedChange(
                path = relativePath,
                oldChecksum = stored.checksum,
                newChecksum = currentChecksum
            )
        }
        
        // Update checksum since change is authorized
        val updated = stored.copy(
            checksum = currentChecksum,
            lastModified = file.lastModified()
        )
        
        val updatedDatabase = database.copy(
            files = database.files + (relativePath to updated)
        )
        saveDatabase(updatedDatabase)
        
        return ValidationResult.AuthorizedChange(
            path = relativePath,
            permission = activePermissions.first()
        )
    }
    
    fun validateProject(rootDir: File): List<ValidationResult> {
        val buildFiles = rootDir.walk()
            .filter { it.isFile && (it.name == "build.gradle.kts" || it.name == "settings.gradle.kts") }
            .toList()
        
        return buildFiles.map { checkFile(it) }
    }
    
    sealed class ValidationResult {
        data class NewFile(val path: String) : ValidationResult()
        data class Unchanged(val path: String) : ValidationResult()
        data class AuthorizedChange(
            val path: String, 
            val permission: Permission
        ) : ValidationResult()
        data class UnauthorizedChange(
            val path: String,
            val oldChecksum: String,
            val newChecksum: String
        ) : ValidationResult() {
            fun toErrorMessage(): String {
                return """
                    |IMMUTABILITY VIOLATION: Unauthorized change to $path
                    |Old checksum: $oldChecksum
                    |New checksum: $newChecksum
                    |
                    |To authorize this change, run:
                    |./gradlew grantBuildFilePermission -Pfile="$path" -Ppurpose="<reason for change>"
                """.trimMargin()
            }
        }
    }
}