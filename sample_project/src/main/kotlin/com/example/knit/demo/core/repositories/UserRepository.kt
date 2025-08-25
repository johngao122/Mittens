package com.example.knit.demo.core.repositories

import com.example.knit.demo.core.models.User

interface UserRepository {
    fun findById(id: Long): User?
    fun findByEmail(email: String): User?
    fun save(user: User): User
    fun findAll(): List<User>
}