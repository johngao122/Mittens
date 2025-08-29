package com.example.knit.demo.core.config

import knit.Provides
import knit.di

data class ConnectionInfo(
    val id: String,
    val isConnected: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long = System.currentTimeMillis()
)

@Provides
class DatabaseService {
    
    private val appConfigService: AppConfigService by di
    private val activeConnections = mutableMapOf<String, ConnectionInfo>()
    private var isInitialized = false
    
    fun initialize() {
        if (!isInitialized) {
            val config = appConfigService.databaseConfig
            println("DatabaseService: Initializing connection to ${config.host}:${config.port}")
            println("   Database: ${config.database}")
            println("   Max connections: ${config.maxConnections}")
            
            
            Thread.sleep(100) 
            isInitialized = true
            println("DatabaseService: Database connection initialized successfully")
        }
    }
    
    fun getConnection(): ConnectionInfo {
        if (!isInitialized) {
            initialize()
        }
        
        val connectionId = "conn_${System.nanoTime()}"
        val connection = ConnectionInfo(connectionId, true)
        
        val config = appConfigService.databaseConfig
        if (activeConnections.size >= config.maxConnections) {
            
            val oldestConnection = activeConnections.values.minByOrNull { it.lastUsed }
            oldestConnection?.let { 
                activeConnections.remove(it.id)
                println("DatabaseService: Recycled oldest connection ${it.id}")
            }
        }
        
        activeConnections[connectionId] = connection
        println("DatabaseService: Created new connection $connectionId")
        return connection
    }
    
    fun releaseConnection(connectionId: String) {
        activeConnections.remove(connectionId)?.let {
            println("DatabaseService: Released connection $connectionId")
        }
    }
    
    fun executeQuery(query: String): Map<String, Any> {
        val connection = getConnection()
        
        try {
            println("DatabaseService: Executing query on ${connection.id}")
            println("   Query: ${query.take(50)}${if (query.length > 50) "..." else ""}")
            
            
            Thread.sleep(50)
            
            
            return mapOf(
                "success" to true,
                "rows_affected" to (1..10).random(),
                "execution_time_ms" to (10..100).random(),
                "connection_id" to connection.id
            )
            
        } finally {
            releaseConnection(connection.id)
        }
    }
    
    fun beginTransaction(): String {
        val connection = getConnection()
        println("DatabaseService: Beginning transaction on ${connection.id}")
        return connection.id
    }
    
    fun commitTransaction(transactionId: String) {
        println("DatabaseService: Committing transaction $transactionId")
        releaseConnection(transactionId)
    }
    
    fun rollbackTransaction(transactionId: String) {
        println("DatabaseService: Rolling back transaction $transactionId")
        releaseConnection(transactionId)
    }
    
    fun getConnectionStats(): Map<String, Any> {
        val config = appConfigService.databaseConfig
        return mapOf(
            "max_connections" to config.maxConnections,
            "active_connections" to activeConnections.size,
            "available_connections" to (config.maxConnections - activeConnections.size),
            "total_connections_created" to activeConnections.size,
            "database_initialized" to isInitialized
        )
    }
    
    fun healthCheck(): Boolean {
        return try {
            if (!isInitialized) initialize()
            val result = executeQuery("SELECT 1 as health_check")
            result["success"] as Boolean
        } catch (e: Exception) {
            println("DatabaseService: Health check failed - ${e.message}")
            false
        }
    }
}