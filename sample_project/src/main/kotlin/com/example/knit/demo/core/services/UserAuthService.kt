package com.example.knit.demo.core.services

import com.example.knit.demo.core.models.User
import knit.Provides
import knit.di

data class AuthToken(
    val userId: Long,
    val token: String,
    val expiresAt: Long = System.currentTimeMillis() + 3600000 // 1 hour
)

@Provides
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