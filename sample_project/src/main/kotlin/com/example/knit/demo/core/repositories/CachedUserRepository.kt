package com.example.knit.demo.core.repositories

import com.example.knit.demo.core.models.User
import knit.Provides

// AMBIGUOUS_PROVIDER: Third implementation of UserRepository interface
// Now we have DatabaseUserRepository, InMemoryUserRepository, and CachedUserRepository
// all providing UserRepository::class
// @Provides(UserRepository::class) - Removed to fix ambiguous provider issue
class CachedUserRepository : UserRepository {
    
    private val cache = mutableMapOf<Long, User>()
    private val emailCache = mutableMapOf<String, User>()
    
    init {
        println("CachedUserRepository: Initializing cache with default users")
        val user1 = User(10, "cached@example.com", "Cached User")
        val user2 = User(11, "cache2@example.com", "Another Cached User")
        
        cache[10] = user1
        cache[11] = user2
        emailCache["cached@example.com"] = user1
        emailCache["cache2@example.com"] = user2
    }
    
    override fun findById(id: Long): User? {
        println("CachedUserRepository: Finding user $id from cache")
        return cache[id]
    }
    
    override fun findByEmail(email: String): User? {
        println("CachedUserRepository: Finding user by email $email from cache")
        return emailCache[email]
    }
    
    override fun save(user: User): User {
        println("CachedUserRepository: Caching user ${user.id}")
        cache[user.id] = user
        emailCache[user.email] = user
        return user
    }
    
    override fun findAll(): List<User> {
        println("CachedUserRepository: Returning all cached users")
        return cache.values.toList()
    }
    
    fun clearCache() {
        println("CachedUserRepository: Clearing cache")
        cache.clear()
        emailCache.clear()
    }
    
    fun getCacheStats(): Map<String, Any> {
        return mapOf(
            "cached_users" to cache.size,
            "cache_memory_usage" to (cache.size * 100), // Mock memory usage
            "cache_hit_rate" to 0.85 // Mock hit rate
        )
    }
}

// AMBIGUOUS_PROVIDER: Yet another UserRepository implementation to make it even more ambiguous
// @Provides(UserRepository::class) - Removed to fix ambiguous provider issue
class RemoteUserRepository : UserRepository {
    
    override fun findById(id: Long): User? {
        println("RemoteUserRepository: Fetching user $id from remote API")
        // Simulate remote API call delay
        Thread.sleep(10)
        
        return if (id % 2 == 0L) {
            User(id, "remote$id@api.com", "Remote User $id")
        } else {
            null
        }
    }
    
    override fun findByEmail(email: String): User? {
        println("RemoteUserRepository: Searching remote API for email $email")
        Thread.sleep(5)
        return null // Mock: remote API doesn't find users by email
    }
    
    override fun save(user: User): User {
        println("RemoteUserRepository: Saving user ${user.id} to remote API")
        Thread.sleep(20)
        return user
    }
    
    override fun findAll(): List<User> {
        println("RemoteUserRepository: Fetching all users from remote API (not recommended)")
        Thread.sleep(50)
        return emptyList() // Mock: too expensive to fetch all users
    }
}