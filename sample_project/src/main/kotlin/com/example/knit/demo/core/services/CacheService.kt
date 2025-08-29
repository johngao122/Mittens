package com.example.knit.demo.core.services

import knit.Provides
import knit.Named
import knit.di

interface CacheProvider {
    fun get(key: String): String?
    fun set(key: String, value: String)
    fun clear(key: String)
}

// Provider without named qualifier
@Provides(CacheProvider::class)
class RedisCacheProvider : CacheProvider {
    private val cache = mutableMapOf<String, String>()
    
    override fun get(key: String): String? {
        println("RedisCacheProvider: Getting $key")
        return cache[key]
    }
    
    override fun set(key: String, value: String) {
        println("RedisCacheProvider: Setting $key = $value")
        cache[key] = value
    }
    
    override fun clear(key: String) {
        cache.remove(key)
    }
}

// Alternative provider - commented out to avoid ambiguous provider error
// @Provides(CacheProvider::class) 
class MemoryCacheProvider : CacheProvider {
    private val memoryCache = mutableMapOf<String, String>()
    
    override fun get(key: String): String? {
        println("MemoryCacheProvider: Getting $key from memory")
        return memoryCache[key]
    }
    
    override fun set(key: String, value: String) {
        memoryCache[key] = value
    }
    
    override fun clear(key: String) {
        memoryCache.remove(key)
    }
}

@Provides
class CacheService {
    // Simple injection without named qualifiers
    private val cache: CacheProvider by di
    
    fun cacheValue(key: String, value: String) {
        println("CacheService: Caching $key")
        cache.set(key, value)
    }
    
    fun getCachedValue(key: String): String? {
        return cache.get(key)
    }
}

@Provides
class CacheManagerService {
    // Simple injection without named qualifiers
    private val dbCache: CacheProvider by di
    
    fun manageDatabaseCache(key: String, value: String) {
        println("CacheManagerService: Managing database cache")
        dbCache.set(key, value)
    }
}