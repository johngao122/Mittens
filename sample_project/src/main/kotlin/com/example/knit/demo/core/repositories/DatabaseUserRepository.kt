package com.example.knit.demo.core.repositories

import com.example.knit.demo.core.models.User
import knit.Provides

@Provides(UserRepository::class)
class DatabaseUserRepository : UserRepository {
    
    private val database = mutableMapOf<Long, User>()
    
    init {
        database[1] = User(1, "alice@example.com", "Alice Smith")
        database[2] = User(2, "bob@example.com", "Bob Johnson")
    }
    
    override fun findById(id: Long): User? {
        return database[id]
    }
    
    override fun findByEmail(email: String): User? {
        return database.values.find { it.email == email }
    }
    
    override fun save(user: User): User {
        database[user.id] = user
        return user
    }
    
    override fun findAll(): List<User> {
        return database.values.toList()
    }
}