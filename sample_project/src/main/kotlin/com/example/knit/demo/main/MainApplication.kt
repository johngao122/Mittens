package com.example.knit.demo.main

import com.example.knit.demo.core.models.*
import com.example.knit.demo.core.repositories.*
import com.example.knit.demo.core.services.*
import com.example.knit.demo.core.config.*
import knit.Provides
import knit.Component
import knit.di
import java.math.BigDecimal
import java.time.LocalDateTime

@Component
class ECommerceApplication {
    
    val userRepository: UserRepository by di
    val orderService: OrderService by di
    val inventoryService: InventoryService by di
    val paymentService: PaymentService by di
    
    val userService: UserService by di
    val userAuthService: UserAuthService by di
    val productService: ProductService by di
    val notificationManager: NotificationManager by di
    val notificationTemplateService: NotificationTemplateService by di
    val auditService: AuditService by di
    val loggingService: LoggingService by di
    val appConfigService: AppConfigService by di
    val databaseService: DatabaseService by di
    
    fun demonstrateDIScenarios() {
        println("Starting E-Commerce Application Demo")
        println("=" * 50)
        
        initializeApplication()
        
        demonstrateIntentionalDIErrors()
        
        demonstrateProperDIPatterns()
        
        showApplicationStats()
    }
    
    private fun initializeApplication() {
        println("\n Initializing Application")
        println("-" * 30)
        
        loggingService.info("MainApplication", "Starting application initialization")
        
        println(appConfigService.getConfigSummary())
        
        val dbHealth = databaseService.healthCheck()
        println("Database Health: ${if (dbHealth) "✅" else "❌"}")
        
        auditService.logEvent(null, "application_started", "system")
    }
    
    private fun demonstrateIntentionalDIErrors() {
        println("\n Intentional DI Error Demonstrations")
        println("-" * 40)
        
        try {
            println("\n Testing User Repository (Ambiguous Scenario)")
            val users = userRepository.findAll()
            println("Found ${users.size} users: ${users.map { it.name }}")
            
        } catch (e: Exception) {
            println("❌ Ambiguous UserRepository error: ${e.message}")
        }
        
        try {
            println("\n Testing Circular Dependency (Cycle Scenario)")
            val stock = inventoryService.getAvailableStock(1L)
            println("Product 1 stock: $stock")
            
        } catch (e: Exception) {
            println("❌ Cycle dependency error: ${e.message}")
        }
        
        try {
            println("\n Testing Payment Service (Unresolved Scenario)")
            val order = createSampleOrder()
            val result = paymentService.processOrderPayment(order, "card_token_123")
            println("Payment result: $result")
            
        } catch (e: Exception) {
            println("❌ Unresolved PaymentGateway error: ${e.message}")
        }
    }
    
    private fun demonstrateProperDIPatterns() {
        println("\n Proper DI Pattern Demonstrations")
        println("-" * 40)
        
        demonstrateUserManagement()
        demonstrateProductManagement()
        demonstrateNotificationSystem()
        demonstrateAuditAndLogging()
    }
    
    private fun demonstrateUserManagement() {
        println("\n User Management with DI")
        
        val newUser = userService.registerUser("john.doe@example.com", "John Doe")
        println("Registered user: ${newUser.name}")
        
        val authToken = userAuthService.authenticate("john.doe@example.com", "password123")
        authToken?.let { token ->
            println("Authentication successful. Token: ${token.token.take(20)}...")
            
            notificationTemplateService.sendWelcomeTemplate(newUser.name, newUser.email, newUser.id)
            
            val validatedUser = userAuthService.validateToken(token.token)
            println("Token validation: ${validatedUser?.name ?: "Failed"}")
        }
    }
    
    private fun demonstrateProductManagement() {
        println("\n Product Management with DI")
        
        val products = productService.getAllProducts()
        println("Available products: ${products.size}")
        
        products.take(2).forEach { product ->
            val isAvailable = productService.checkProductAvailability(product.id)
            println("  - ${product.name}: ${if (isAvailable) "✅ Available" else "❌ Out of Stock"}")
        }
            
        
        val electronics = productService.getProductsByCategory("Electronics")
        println("Electronics products: ${electronics.size}")
    }
    
    private fun demonstrateNotificationSystem() {
        println("\n Notification System with DI")
        
        notificationManager.sendUserWelcome(1L, "alice@example.com", "Alice")
        
        notificationTemplateService.sendOrderConfirmationTemplate(
            orderId = 12345L,
            userName = "Alice",
            userEmail = "alice@example.com", 
            totalAmount = "$99.98"
        )
        
        notificationManager.sendSystemAlert(
            message = "Demonstration of notification system completed",
            adminEmail = appConfigService.notificationConfig.adminEmail
        )
    }
    
    private fun demonstrateAuditAndLogging() {
        println("\n Audit & Logging with DI")
        
        loggingService.info("DemoSystem", "Demonstration of logging service")
        loggingService.warn("DemoSystem", "This is a warning message")
        
        val recentEvents = auditService.getAuditHistory(limit = 5)
        println("Recent audit events: ${recentEvents.size}")
        recentEvents.forEach { event ->
            println("  - ${event.action} on ${event.resource} at ${event.timestamp}")
        }
        
        val logStats = loggingService.getLogStats()
        println("Log statistics: $logStats")
    }
    
    private fun showApplicationStats() {
        println("\n Application Statistics")
        println("-" * 30)
        
        val dbStats = databaseService.getConnectionStats()
        println("Database connections: ${dbStats["active_connections"]}/${dbStats["max_connections"]}")
        
        val auditCount = auditService.getAuditHistory().size
        println("Total audit events: $auditCount")
        
        val logStats = loggingService.getLogStats()
        val totalLogs = logStats.values.sum()
        println("Total log entries: $totalLogs")
        
        val features = appConfigService.getFeatureFlags()
        val enabledFeatures = features.count { it.value }
        println("Enabled features: $enabledFeatures/${features.size}")
    }
    
    private fun createSampleOrder(): Order {
        return Order(
            id = 1L,
            userId = 1L,
            items = listOf(
                OrderItem(1L, 2, BigDecimal("29.99")),
                OrderItem(2L, 1, BigDecimal("15.99"))
            ),
            totalAmount = BigDecimal("75.97"),
            status = OrderStatus.PENDING,
            createdAt = LocalDateTime.now()
        )
    }
}

fun main() {
    try {
        val app = ECommerceApplication()
        app.demonstrateDIScenarios()
    } catch (e: Exception) {
        println("Expected DI Framework Error: ${e.message}")
        println("This demonstrates how Knit detects and handles DI problems!")
    }
}

private operator fun String.times(n: Int): String = this.repeat(n)