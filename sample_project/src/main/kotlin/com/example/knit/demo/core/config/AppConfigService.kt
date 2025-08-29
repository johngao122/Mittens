package com.example.knit.demo.core.config

import knit.Provides

data class DatabaseConfig(
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val maxConnections: Int
)

data class NotificationConfig(
    val emailEnabled: Boolean,
    val smsEnabled: Boolean,
    val maxRetries: Int,
    val adminEmail: String
)

data class BusinessConfig(
    val maxOrderItems: Int,
    val orderTimeoutMinutes: Long,
    val inventoryWarningThreshold: Int,
    val supportedCurrencies: List<String>
)

@Provides
class AppConfigService {
    
    val databaseConfig = DatabaseConfig(
        host = "localhost",
        port = 5432,
        database = "ecommerce_demo",
        username = "demo_user",
        maxConnections = 10
    )
    
    val notificationConfig = NotificationConfig(
        emailEnabled = true,
        smsEnabled = true,
        maxRetries = 3,
        adminEmail = "admin@example.com"
    )
    
    val businessConfig = BusinessConfig(
        maxOrderItems = 20,
        orderTimeoutMinutes = 30,
        inventoryWarningThreshold = 5,
        supportedCurrencies = listOf("USD", "EUR", "GBP")
    )
    
    fun getEnvironment(): String {
        return System.getProperty("app.environment", "development")
    }
    
    fun isDebugEnabled(): Boolean {
        return getEnvironment() == "development"
    }
    
    fun getAppVersion(): String {
        return "1.0.0-DEMO"
    }
    
    fun getFeatureFlags(): Map<String, Boolean> {
        return mapOf(
            "advanced_notifications" to true,
            "audit_logging" to true,
            "user_authentication" to true,
            "product_recommendations" to false,
            "payment_processing" to false // Intentionally disabled due to unresolved dependency
        )
    }
    
    fun isFeatureEnabled(featureName: String): Boolean {
        return getFeatureFlags()[featureName] ?: false
    }
    
    fun getConfigSummary(): String {
        return """
            |Application Configuration Summary:
            |  Environment: ${getEnvironment()}
            |  Version: ${getAppVersion()}
            |  Database: ${databaseConfig.host}:${databaseConfig.port}
            |  Max Connections: ${databaseConfig.maxConnections}
            |  Email Notifications: ${if (notificationConfig.emailEnabled) "Enabled" else "Disabled"}
            |  SMS Notifications: ${if (notificationConfig.smsEnabled) "Enabled" else "Disabled"}
            |  Max Order Items: ${businessConfig.maxOrderItems}
            |  Debug Mode: ${if (isDebugEnabled()) "Enabled" else "Disabled"}
        """.trimMargin()
    }
}