package com.example.knit.demo.core.services

import com.example.knit.demo.core.models.User
import knit.Provides
import knit.Singleton
import knit.di

data class AuthToken(
    val userId: Long,
    val token: String,
    val expiresAt: Long = System.currentTimeMillis() + 3600000 // 1 hour
)

// Primary auth service (singleton)
@Provides
@Singleton 
class UserAuthService {
    
    private val userService: UserService by di
    private val activeSessions = mutableMapOf<String, AuthToken>()
    
    fun authenticate(email: String, password: String): AuthToken? {
        println("UserAuthService: Authenticating user $email")
        
        val user = userService.findUserByEmail(email)
        return if (user != null && user.isActive) {
            
            val token = "token_${user.id}_${System.currentTimeMillis()}"
            val authToken = AuthToken(user.id, token)
            activeSessions[token] = authToken
            println("UserAuthService: Authentication successful for user ${user.name}")
            authToken
        } else {
            println("UserAuthService: Authentication failed for $email")
            null
        }
    }
    
    fun validateToken(token: String): User? {
        println("UserAuthService: Validating token")
        
        val authToken = activeSessions[token]
        return if (authToken != null && authToken.expiresAt > System.currentTimeMillis()) {
            userService.findUser(authToken.userId)
        } else {
            if (authToken != null) {
                activeSessions.remove(token) 
            }
            null
        }
    }
    
    fun logout(token: String): Boolean {
        println("UserAuthService: Logging out user")
        return activeSessions.remove(token) != null
    }
    
    fun refreshToken(oldToken: String): AuthToken? {
        println("UserAuthService: Refreshing token")
        
        val user = validateToken(oldToken)
        return if (user != null) {
            activeSessions.remove(oldToken)
            authenticate(user.email, "dummy_password") 
        } else {
            null
        }
    }
}

// SINGLETON_VIOLATION: Multiple singleton providers for UserAuthService - REMOVED @Provides to clean up DI graph
// @Provides
// @Singleton
class BackupUserAuthService {
    fun authenticateBackup(email: String, password: String): AuthToken? {
        println("BackupUserAuthService: Backup authentication for $email")
        return AuthToken(1L, "backup_token_${System.currentTimeMillis()}")
    }
}

// SINGLETON_VIOLATION: Non-singleton provider for same interface/type conflicts
@Provides
class SessionManager {
    // This depends on a singleton but is itself not singleton - creates violation
    private val userAuthService: UserAuthService by di
    
    fun getActiveSessionCount(): Int {
        println("SessionManager: Getting active session count")
        return 5 // Mock implementation
    }
}