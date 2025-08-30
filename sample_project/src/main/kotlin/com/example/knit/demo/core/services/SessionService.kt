package com.example.knit.demo.core.services

import com.example.knit.demo.core.models.User
import knit.Provides
import knit.di

@Provides
class SessionService {
    
    private val userService: UserService by di
    
    // Simple session management - track current logged-in user
    private var currentUser: User? = null
    
    fun login(email: String): LoginResult {
        println("SessionService: Attempting login for $email")
        
        return try {
            val user = userService.findUserByEmail(email)
            if (user != null && user.isActive) {
                currentUser = user
                println("SessionService: Login successful for ${user.name}")
                LoginResult.Success(user)
            } else {
                println("SessionService: Login failed - user not found or inactive")
                LoginResult.UserNotFound
            }
        } catch (e: Exception) {
            println("SessionService: Login failed with error - ${e.message}")
            LoginResult.Error(e.message ?: "Unknown error")
        }
    }
    
    fun register(email: String, name: String): RegisterResult {
        println("SessionService: Attempting registration for $email")
        
        return try {
            // Check if user already exists
            val existingUser = userService.findUserByEmail(email)
            if (existingUser != null) {
                return RegisterResult.UserAlreadyExists
            }
            
            val newUser = userService.registerUser(email, name)
            currentUser = newUser
            println("SessionService: Registration successful for ${newUser.name}")
            RegisterResult.Success(newUser)
        } catch (e: Exception) {
            println("SessionService: Registration failed - ${e.message}")
            RegisterResult.Error(e.message ?: "Registration failed")
        }
    }
    
    fun logout() {
        currentUser?.let { user ->
            println("SessionService: Logging out ${user.name}")
            currentUser = null
        }
    }
    
    fun getCurrentUser(): User? {
        return currentUser
    }
    
    fun isLoggedIn(): Boolean {
        return currentUser != null
    }
    
    fun requireLogin(): User {
        return currentUser ?: throw IllegalStateException("User must be logged in to perform this action")
    }
    
    fun getCurrentUserId(): Long {
        return requireLogin().id
    }
}

sealed class LoginResult {
    data class Success(val user: User) : LoginResult()
    object UserNotFound : LoginResult()
    data class Error(val message: String) : LoginResult()
}

sealed class RegisterResult {
    data class Success(val user: User) : RegisterResult()
    object UserAlreadyExists : RegisterResult()
    data class Error(val message: String) : RegisterResult()
}