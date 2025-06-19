package borg.trikeshed.lib

enum class AccessLevel {
    PUBLIC,
    PROTECTED,
    PRIVATE;
    
    val isPublic: Boolean get() = this == PUBLIC
    val isProtected: Boolean get() = this == PROTECTED
    val isPrivate: Boolean get() = this == PRIVATE
}

enum class AccessPattern {
    READ,
    WRITE,
    EXECUTE;
    
    val isRead: Boolean get() = this == READ
    val isWrite: Boolean get() = this == WRITE
    val isExecute: Boolean get() = this == EXECUTE
}

class AccessControl(private val level: AccessLevel, private val pattern: AccessPattern) {
    fun checkAccess(requestedLevel: AccessLevel, requestedPattern: AccessPattern): Boolean {
        return when (level) {
            AccessLevel.PUBLIC -> true
            AccessLevel.PROTECTED -> requestedLevel != AccessLevel.PRIVATE
            AccessLevel.PRIVATE -> requestedLevel == AccessLevel.PRIVATE
        } && pattern == requestedPattern
    }
} 