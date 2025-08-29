package com.example.knit.demo.core.services

import com.example.knit.demo.core.models.User
import com.example.knit.demo.core.repositories.UserRepository
import knit.Provides
import knit.di

@Provides
class UserService {
    
    private val userRepository: UserRepository by di
    
    fun findUser(userId: Long): User? {
        println("UserService: Finding user $userId")
        return userRepository.findById(userId)
    }
    
    fun findUserByEmail(email: String): User? {
        println("UserService: Finding user by email $email")
        return userRepository.findByEmail(email)
    }
    
    fun getAllUsers(): List<User> {
        println("UserService: Getting all users")
        return userRepository.findAll()
    }
    
    fun registerUser(email: String, name: String): User {
        println("UserService: Registering new user $name")
        val newId = (userRepository.findAll().maxOfOrNull { it.id } ?: 0) + 1
        val user = User(newId, email, name)
        return userRepository.save(user)
    }
    
    fun updateUser(user: User): User {
        println("UserService: Updating user ${user.id}")
        return userRepository.save(user)
    }
    
    fun deactivateUser(userId: Long): User? {
        println("UserService: Deactivating user $userId")
        val user = userRepository.findById(userId)
        return user?.let {
            val deactivated = it.copy(isActive = false)
            userRepository.save(deactivated)
        }
    }
}