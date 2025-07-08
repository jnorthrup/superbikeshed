@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.oauth

import borg.trikeshed.lib.*
import borg.trikeshed.cursor.*
import borg.trikeshed.ccek.*
import borg.trikeshed.http.*
import borg.trikeshed.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.coroutines.CoroutineContext

/**
 * OAuth 2.0/2.1 Protocol Implementation with Channelized Operations
 * 
 * Supports OAuth 2.0, OAuth 2.1, PKCE, and modern security standards
 * with indexed channels and CCek FSM integration.
 */

// OAuth 2.0/2.1 Grant Types
enum class OAuthGrantType(val value: String) {
    AUTHORIZATION_CODE("authorization_code"),
    CLIENT_CREDENTIALS("client_credentials"),
    PASSWORD("password"),
    IMPLICIT("implicit"),
    REFRESH_TOKEN("refresh_token"),
    DEVICE_CODE("urn:ietf:params:oauth:grant-type:device_code"),
    JWT_BEARER("urn:ietf:params:oauth:grant-type:jwt-bearer"),
    TOKEN_EXCHANGE("urn:ietf:params:oauth:grant-type:token-exchange");
    
    companion object {
        internal val map = values().associateBy { it.value }
        fun fromValue(value: String) = map[value]
    }
}

// OAuth Response Types
enum class OAuthResponseType(val value: String) {
    CODE("code"),
    TOKEN("token"),
    ID_TOKEN("id_token");
    
    companion object {
        internal val map = values().associateBy { it.value }
        fun fromValue(value: String) = map[value]
    }
}

// OAuth Token Types
enum class OAuthTokenType(val value: String) {
    BEARER("Bearer"),
    MAC("MAC"),
    JWT("JWT");
    
    companion object {
        internal val map = values().associateBy { it.value }
        fun fromValue(value: String) = map[value]
    }
}

// OAuth Scopes
enum class OAuthScope(val value: String) {
    OPENID("openid"),
    PROFILE("profile"),
    EMAIL("email"),
    ADDRESS("address"),
    PHONE("phone"),
    OFFLINE_ACCESS("offline_access"),
    READ("read"),
    WRITE("write"),
    DELETE("delete"),
    ADMIN("admin");
    
    companion object {
        internal val map = values().associateBy { it.value }
        fun fromValue(value: String) = map[value]
    }
}

// OAuth PKCE Code Challenge Methods
enum class PKCECodeChallengeMethod(val value: String) {
    S256("S256"),
    PLAIN("plain");
    
    companion object {
        internal val map = values().associateBy { it.value }
        fun fromValue(value: String) = map[value]
    }
}

// OAuth FSM States
enum class OAuthFSMState {
    IDLE,
    AUTHORIZATION_REQUEST,
    AUTHORIZATION_RESPONSE,
    TOKEN_REQUEST,
    TOKEN_RESPONSE,
    REFRESH_REQUEST,
    REFRESH_RESPONSE,
    ERROR,
    COMPLETE
}

// OAuth CCek Context
data class OAuthCCekContext(
    val clientId: String,
    val clientSecret: String? = null,
    val redirectUri: String,
    val scope: Indexed<String>,
    val state: String? = null,
    val pkceCodeVerifier: String? = null,
    val pkceCodeChallenge: String? = null,
    val pkceCodeChallengeMethod: PKCECodeChallengeMethod = PKCECodeChallengeMethod.S256,
    val grantType: OAuthGrantType,
    val channels: Indexed<OAuthChannel>,
    val cursor: Cursor? = null,
    val fsmState: OAuthFSMState = OAuthFSMState.IDLE,
    val sessionId: String = generateSessionId(),
    val executionId: String = generateExecutionId(),
    val authorizationUrl: String? = null,
    val tokenEndpoint: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val idToken: String? = null,
    val expiresIn: Long? = null
) : CoroutineContext.Element {
    override val key = OAuthCCekContextKey
    
    companion object OAuthCCekContextKey : CoroutineContext.Key<OAuthCCekContext>
}

// OAuth Channel Types
sealed class OAuthChannel {
    data class AuthorizationChannel(
        val url: String,
        val responseType: OAuthResponseType,
        val clientId: String,
        val redirectUri: String,
        val scope: Indexed<String>,
        val state: String?,
        val pkceCodeChallenge: String?,
        val pkceCodeChallengeMethod: PKCECodeChallengeMethod
    ) : OAuthChannel()
    
    data class TokenChannel(
        val endpoint: String,
        val grantType: OAuthGrantType,
        val clientId: String,
        val clientSecret: String?,
        val code: String?,
        val redirectUri: String?,
        val pkceCodeVerifier: String?,
        val refreshToken: String?
    ) : OAuthChannel()
    
    data class RefreshChannel(
        val endpoint: String,
        val clientId: String,
        val clientSecret: String?,
        val refreshToken: String,
        val scope: Indexed<String>?
    ) : OAuthChannel()
    
    data class UserInfoChannel(
        val endpoint: String,
        val accessToken: String
    ) : OAuthChannel()
    
    data class IntrospectChannel(
        val endpoint: String,
        val clientId: String,
        val clientSecret: String?,
        val token: String
    ) : OAuthChannel()
    
    data class RevokeChannel(
        val endpoint: String,
        val clientId: String,
        val clientSecret: String?,
        val token: String,
        val tokenTypeHint: String?
    ) : OAuthChannel()
}

// OAuth Operations
sealed class OAuthOperation {
    data class AuthorizationRequest(
        val responseType: OAuthResponseType,
        val clientId: String,
        val redirectUri: String,
        val scope: Indexed<String>,
        val state: String?,
        val pkceCodeChallenge: String?,
        val pkceCodeChallengeMethod: PKCECodeChallengeMethod
    ) : OAuthOperation()
    
    data class TokenRequest(
        val grantType: OAuthGrantType,
        val clientId: String,
        val clientSecret: String?,
        val code: String?,
        val redirectUri: String?,
        val pkceCodeVerifier: String?,
        val refreshToken: String?,
        val scope: Indexed<String>?
    ) : OAuthOperation()
    
    data class RefreshRequest(
        val clientId: String,
        val clientSecret: String?,
        val refreshToken: String,
        val scope: Indexed<String>?
    ) : OAuthOperation()
    
    data class UserInfoRequest(
        val accessToken: String
    ) : OAuthOperation()
    
    data class IntrospectRequest(
        val clientId: String,
        val clientSecret: String?,
        val token: String
    ) : OAuthOperation()
    
    data class RevokeRequest(
        val clientId: String,
        val clientSecret: String?,
        val token: String,
        val tokenTypeHint: String?
    ) : OAuthOperation()
}

// OAuth Responses
sealed class OAuthResponse {
    data class AuthorizationResponse(
        val code: String?,
        val state: String?,
        val error: String?,
        val errorDescription: String?
    ) : OAuthResponse()
    
    data class TokenResponse(
        val accessToken: String,
        val tokenType: OAuthTokenType,
        val expiresIn: Long?,
        val refreshToken: String?,
        val scope: Indexed<String>?,
        val idToken: String?
    ) : OAuthResponse()
    
    data class ErrorResponse(
        val error: String,
        val errorDescription: String?,
        val errorUri: String?
    ) : OAuthResponse()
    
    data class UserInfoResponse(
        val sub: String,
        val name: String?,
        val givenName: String?,
        val familyName: String?,
        val email: String?,
        val emailVerified: Boolean?,
        val picture: String?
    ) : OAuthResponse()
    
    data class IntrospectResponse(
        val active: Boolean,
        val scope: String?,
        val clientId: String?,
        val username: String?,
        val tokenType: String?,
        val exp: Long?,
        val iat: Long?,
        val nbf: Long?,
        val sub: String?,
        val aud: String?,
        val iss: String?
    ) : OAuthResponse()
}

// OAuth Client Interface
interface OAuthClient {
    suspend fun createAuthorizationUrl(context: OAuthCCekContext): String
    suspend fun exchangeCodeForToken(code: String, context: OAuthCCekContext): OAuthResponse.TokenResponse
    suspend fun refreshToken(refreshToken: String, context: OAuthCCekContext): OAuthResponse.TokenResponse
    suspend fun getUserInfo(accessToken: String, context: OAuthCCekContext): OAuthResponse.UserInfoResponse
    suspend fun introspectToken(token: String, context: OAuthCCekContext): OAuthResponse.IntrospectResponse
    suspend fun revokeToken(token: String, context: OAuthCCekContext): Boolean
    suspend fun validateIdToken(idToken: String, context: OAuthCCekContext): Boolean
}

// OAuth Client Implementation
class OAuthClientImpl : OAuthClient {
    
    override suspend fun createAuthorizationUrl(context: OAuthCCekContext): String {
        return withContext(context) {
            val baseUrl = context.authorizationUrl ?: throw OAuthException("Authorization URL not configured")
            
            val params = mutableMapOf<String, String>().apply {
                put("response_type", context.grantType.value)
                put("client_id", context.clientId)
                put("redirect_uri", context.redirectUri)
                put("scope", context.scope.a.joinToString(" "))
                context.state?.let { put("state", it) }
                context.pkceCodeChallenge?.let { put("code_challenge", it) }
                context.pkceCodeChallengeMethod?.let { put("code_challenge_method", it.value) }
            }
            
            val queryString = params.entries.joinToString("&") { "${it.key}=${encodeUrl(it.value)}" }
            "$baseUrl?$queryString"
        }
    }
    
    override suspend fun exchangeCodeForToken(code: String, context: OAuthCCekContext): OAuthResponse.TokenResponse {
        return withContext(context) {
            val endpoint = context.tokenEndpoint ?: throw OAuthException("Token endpoint not configured")
            
            val params = mutableMapOf<String, String>().apply {
                put("grant_type", OAuthGrantType.AUTHORIZATION_CODE.value)
                put("client_id", context.clientId)
                put("code", code)
                put("redirect_uri", context.redirectUri)
                context.pkceCodeVerifier?.let { put("code_verifier", it) }
                context.clientSecret?.let { put("client_secret", it) }
            }
            
            // TODO: Make HTTP request to token endpoint
            // For now, return mock response
            OAuthResponse.TokenResponse(
                accessToken = "mock_access_token",
                tokenType = OAuthTokenType.BEARER,
                expiresIn = 3600L,
                refreshToken = "mock_refresh_token",
                scope = context.scope,
                idToken = null
            )
        }
    }
    
    override suspend fun refreshToken(refreshToken: String, context: OAuthCCekContext): OAuthResponse.TokenResponse {
        return withContext(context) {
            val endpoint = context.tokenEndpoint ?: throw OAuthException("Token endpoint not configured")
            
            val params = mutableMapOf<String, String>().apply {
                put("grant_type", OAuthGrantType.REFRESH_TOKEN.value)
                put("client_id", context.clientId)
                put("refresh_token", refreshToken)
                context.clientSecret?.let { put("client_secret", it) }
                context.scope?.let { put("scope", it.a.joinToString(" ")) }
            }
            
            // TODO: Make HTTP request to token endpoint
            // For now, return mock response
            OAuthResponse.TokenResponse(
                accessToken = "new_mock_access_token",
                tokenType = OAuthTokenType.BEARER,
                expiresIn = 3600L,
                refreshToken = "new_mock_refresh_token",
                scope = context.scope,
                idToken = null
            )
        }
    }
    
    override suspend fun getUserInfo(accessToken: String, context: OAuthCCekContext): OAuthResponse.UserInfoResponse {
        return withContext(context) {
            // TODO: Make HTTP request to userinfo endpoint
            // For now, return mock response
            OAuthResponse.UserInfoResponse(
                sub = "mock_user_id",
                name = "Mock User",
                givenName = "Mock",
                familyName = "User",
                email = "mock@example.com",
                emailVerified = true,
                picture = "https://example.com/avatar.jpg"
            )
        }
    }
    
    override suspend fun introspectToken(token: String, context: OAuthCCekContext): OAuthResponse.IntrospectResponse {
        return withContext(context) {
            // TODO: Make HTTP request to introspection endpoint
            // For now, return mock response
            OAuthResponse.IntrospectResponse(
                active = true,
                scope = context.scope.a.joinToString(" "),
                clientId = context.clientId,
                username = "mock_user",
                tokenType = OAuthTokenType.BEARER.value,
                exp = System.currentTimeMillis() / 1000 + 3600,
                iat = System.currentTimeMillis() / 1000,
                nbf = System.currentTimeMillis() / 1000,
                sub = "mock_user_id",
                aud = context.clientId,
                iss = "https://example.com"
            )
        }
    }
    
    override suspend fun revokeToken(token: String, context: OAuthCCekContext): Boolean {
        return withContext(context) {
            // TODO: Make HTTP request to revocation endpoint
            // For now, return mock response
            true
        }
    }
    
    override suspend fun validateIdToken(idToken: String, context: OAuthCCekContext): Boolean {
        return withContext(context) {
            // TODO: Validate JWT ID token
            // For now, return mock response
            true
        }
    }
    
    private fun encodeUrl(value: String): String {
        return java.net.URLEncoder.encode(value, "UTF-8")
    }
}

// OAuth Exception
class OAuthException(message: String) : Exception(message)

// Utility functions
fun generatePKCECodeVerifier(): String {
    val bytes = ByteArray(32)
    java.security.SecureRandom().nextBytes(bytes)
    return encodeBase64(bytes).replace("+", "-").replace("/", "_").replace("=", "")
}

fun generatePKCECodeChallenge(verifier: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
    return encodeBase64(digest).replace("+", "-").replace("/", "_").replace("=", "")
}

fun generateState(): String {
    val bytes = ByteArray(16)
    java.security.SecureRandom().nextBytes(bytes)
    return encodeBase64(bytes).replace("+", "-").replace("/", "_").replace("=", "")
} 