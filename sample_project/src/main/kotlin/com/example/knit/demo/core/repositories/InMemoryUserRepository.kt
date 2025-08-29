package com.example.knit.demo.core.repositories

import com.example.knit.demo.core.models.User
import knit.Provides

// AMBIGUOUS_PROVIDER: Now both DatabaseUserRepository and InMemoryUserRepository provide UserRepository
@Provides(UserRepository::class)
class InMemoryUserRepository : UserRepository {
    
    private val users = mutableListOf<User>()
    
    init {
        users.add(User(3, "charlie@example.com", "Charlie Brown"))
        users.add(User(4, "diana@example.com", "Diana Prince"))
    }
    
    override fun findById(id: Long): User? {
        return users.find { it.id == id }
    }
    
    override fun findByEmail(email: String): User? {
        return users.find { it.email == email }
    }
    
    override fun save(user: User): User {
        val existing = users.indexOfFirst { it.id == user.id }
        if (existing >= 0) {
            users[existing] = user
        } else {
            users.add(user)
        }
        return user
    }
    
    override fun findAll(): List<User> {
        return users.toList()
    }
}