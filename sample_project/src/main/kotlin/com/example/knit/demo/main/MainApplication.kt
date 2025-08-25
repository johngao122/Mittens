package com.example.knit.demo.main

import com.example.knit.demo.core.models.*
import com.example.knit.demo.core.repositories.*
import com.example.knit.demo.core.services.*
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
    
    fun demonstrateDIScenarios() {
        println("🚀 Starting E-Commerce Application Demo")
        println("=" * 50)
        
        try {
            println("\n📊 Testing User Repository (Ambiguous Scenario)")
            val users = userRepository.findAll()
            println("Found ${users.size} users: ${users.map { it.name }}")
            
        } catch (e: Exception) {
            println("❌ Ambiguous UserRepository error: ${e.message}")
        }
        
        try {
            println("\n📦 Testing Inventory Service")
            val stock = inventoryService.getAvailableStock(1L)
            println("Product 1 stock: $stock")
            
        } catch (e: Exception) {
            println("❌ Cycle dependency error: ${e.message}")
        }
        
        try {
            println("\n💳 Testing Payment Service (Unresolved Scenario)")
            val order = createSampleOrder()
            val result = paymentService.processOrderPayment(order, "card_token_123")
            println("Payment result: $result")
            
        } catch (e: Exception) {
            println("❌ Unresolved PaymentGateway error: ${e.message}")
        }
        
        println("\n🔍 Classic DI Scenarios Summary:")
        println("1. ✅ CYCLE: OrderService ↔ InventoryService mutual dependency")
        println("2. ✅ AMBIGUOUS: DatabaseUserRepository & InMemoryUserRepository → UserRepository")
        println("3. ✅ UNRESOLVED: PaymentService → PaymentGateway (no provider)")
        println("4. ✅ DEAD: NotificationService provides EmailChannel & SmsChannel (unused)")
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
    println("🌟 Knit DI Framework Demo - Classic Scenarios")
    println("This demo intentionally includes problematic DI scenarios:")
    println("- Cycle dependencies")
    println("- Ambiguous providers") 
    println("- Unresolved dependencies")
    println("- Dead/unused providers")
    println()
    
    try {
        val app = ECommerceApplication()
        app.demonstrateDIScenarios()
    } catch (e: Exception) {
        println("🎯 Expected DI Framework Error: ${e.message}")
        println("This demonstrates how Knit detects and handles DI problems!")
    }
}

private operator fun String.times(n: Int): String = this.repeat(n)