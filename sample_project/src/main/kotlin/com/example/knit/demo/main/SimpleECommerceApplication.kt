package com.example.knit.demo.main

import com.example.knit.demo.core.models.*
import com.example.knit.demo.core.services.*
import com.example.knit.demo.payment.PaymentGateway
import knit.Component
import knit.di
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@Component
class SimpleECommerceApplication {
    private val userService: UserService by di
    private val orderService: OrderService by di
    private val inventoryService: InventoryService by di
    private val paymentService: PaymentService by di
    private val auditService: AuditService by di
    private val validationService: ValidationService by di
    private val paymentGateway: PaymentGateway by di
    
    // New interactive services
    private val sessionService: SessionService by di
    private val cartService: CartService by di
    private val orderHistoryService: OrderHistoryService by di
    private val productDataService: ProductDataService by di
    private val productService: ProductService by di
    
    private val scanner = Scanner(System.`in`)
    private var running = true
    
    fun start() {
        println("🚀 Welcome to Interactive E-Commerce Application")
        println("=" * 60)
        
        try {
            // Initialize sample product data
            productDataService.initializeSampleProducts()
            
            startInteractiveCLI()
        } catch (e: Exception) {
            println("❌ Application encountered an error: ${e.message}")
            println("This demonstrates how Knit DI issues are detected!")
            println("\nRunning fallback static demo...")
            runStaticDemo()
        }
    }
    
    private fun startInteractiveCLI() {
        while (running) {
            try {
                if (sessionService.isLoggedIn()) {
                    showLoggedInMenu()
                } else {
                    showMainMenu()
                }
            } catch (e: Exception) {
                println("❌ Error: ${e.message}")
                println("Please try again or exit the application.")
                readLine()
            }
        }
        
        println("👋 Thank you for using our E-Commerce Application!")
    }
    
    private fun showMainMenu() {
        println("\n" + "=" * 60)
        println("🏪 MAIN MENU")
        println("=" * 60)
        println("1. Login")
        println("2. Register New Account")
        println("3. Browse Products (Guest)")
        println("4. View Product Statistics")
        println("5. Exit")
        println("-" * 60)
        print("Choose an option (1-5): ")
        
        when (readLine()?.trim()) {
            "1" -> handleLogin()
            "2" -> handleRegistration()
            "3" -> browseProductsGuest()
            "4" -> showProductStatistics()
            "5" -> {
                running = false
                return
            }
            else -> println("❌ Invalid option. Please choose 1-5.")
        }
    }
    
    private fun showLoggedInMenu() {
        val user = sessionService.getCurrentUser()!!
        val cartItemCount = cartService.getCartItemCount(user.id)
        val cartTotal = cartService.getCartTotal(user.id)
        
        println("\n" + "=" * 60)
        println("🏪 WELCOME ${user.name.uppercase()}")
        println("=" * 60)
        println("Cart: $cartItemCount items (\$${cartTotal})")
        println("-" * 60)
        println("1. Browse Products")
        println("2. Search Products by Category") 
        println("3. View Cart")
        println("4. Checkout")
        println("5. Order History")
        println("6. Profile Settings")
        println("7. Admin Functions")
        println("8. Logout")
        println("-" * 60)
        print("Choose an option (1-8): ")
        
        when (readLine()?.trim()) {
            "1" -> browseProducts()
            "2" -> searchByCategory()
            "3" -> viewCart()
            "4" -> checkout()
            "5" -> viewOrderHistory()
            "6" -> profileSettings()
            "7" -> adminFunctions()
            "8" -> handleLogout()
            else -> println("❌ Invalid option. Please choose 1-8.")
        }
    }
    
    private fun handleLogin() {
        println("\n🔐 LOGIN")
        println("-" * 30)
        print("Email: ")
        val email = readLine()?.trim() ?: return
        
        if (email.isEmpty()) {
            println("❌ Email cannot be empty")
            return
        }
        
        when (val result = sessionService.login(email)) {
            is LoginResult.Success -> {
                println("✅ Welcome back, ${result.user.name}!")
            }
            is LoginResult.UserNotFound -> {
                println("❌ User not found. Please register first.")
            }
            is LoginResult.Error -> {
                println("❌ Login failed: ${result.message}")
            }
        }
    }
    
    private fun handleRegistration() {
        println("\n📝 NEW ACCOUNT REGISTRATION")
        println("-" * 40)
        print("Email: ")
        val email = readLine()?.trim() ?: return
        print("Full Name: ")
        val name = readLine()?.trim() ?: return
        
        if (email.isEmpty() || name.isEmpty()) {
            println("❌ Email and name cannot be empty")
            return
        }
        
        when (val result = sessionService.register(email, name)) {
            is RegisterResult.Success -> {
                println("✅ Registration successful! Welcome, ${result.user.name}!")
            }
            is RegisterResult.UserAlreadyExists -> {
                println("❌ User with this email already exists. Please login instead.")
            }
            is RegisterResult.Error -> {
                println("❌ Registration failed: ${result.message}")
                println("This may be due to intentional DI validation errors for testing purposes.")
            }
        }
    }
    
    private fun handleLogout() {
        val user = sessionService.getCurrentUser()
        sessionService.logout()
        println("✅ Goodbye, ${user?.name}! You have been logged out.")
    }
    
    private fun browseProductsGuest() {
        println("\n🛍️ PRODUCT CATALOG (Guest View)")
        println("-" * 50)
        
        try {
            val products = productService.getAllProducts()
            displayProducts(products)
        } catch (e: Exception) {
            println("❌ Unable to load products: ${e.message}")
        }
        
        println("\nPress Enter to continue...")
        readLine()
    }
    
    private fun browseProducts() {
        println("\n🛍️ PRODUCT CATALOG")
        println("-" * 50)
        
        try {
            val products = productService.getAllProducts()
            displayProducts(products)
            
            println("\nAdd product to cart? (Enter product ID or 'back')")
            print("Choice: ")
            val choice = readLine()?.trim() ?: return
            
            if (choice.lowercase() == "back") return
            
            try {
                val productId = choice.toLong()
                addToCart(productId)
            } catch (e: NumberFormatException) {
                println("❌ Invalid product ID")
            }
        } catch (e: Exception) {
            println("❌ Unable to load products: ${e.message}")
        }
    }
    
    private fun searchByCategory() {
        println("\n🔍 SEARCH BY CATEGORY")
        println("-" * 40)
        
        try {
            val categories = productDataService.getProductCategories()
            println("Available categories:")
            categories.forEachIndexed { index, category ->
                println("${index + 1}. $category")
            }
            
            print("\nChoose category (1-${categories.size}): ")
            val choice = readLine()?.toIntOrNull()
            
            if (choice == null || choice !in 1..categories.size) {
                println("❌ Invalid choice")
                return
            }
            
            val selectedCategory = categories[choice - 1]
            val products = productService.getProductsByCategory(selectedCategory)
            
            println("\n📦 Products in $selectedCategory:")
            displayProducts(products)
            
            if (sessionService.isLoggedIn()) {
                println("\nAdd product to cart? (Enter product ID or 'back')")
                print("Choice: ")
                val productChoice = readLine()?.trim() ?: return
                
                if (productChoice.lowercase() != "back") {
                    try {
                        val productId = productChoice.toLong()
                        addToCart(productId)
                    } catch (e: NumberFormatException) {
                        println("❌ Invalid product ID")
                    }
                }
            }
        } catch (e: Exception) {
            println("❌ Unable to search products: ${e.message}")
        }
    }
    
    private fun addToCart(productId: Long) {
        try {
            val userId = sessionService.getCurrentUserId()
            print("Quantity (default 1): ")
            val quantityStr = readLine()?.trim()
            val quantity = if (quantityStr.isNullOrEmpty()) 1 else quantityStr.toIntOrNull() ?: 1
            
            cartService.addItem(userId, productId, quantity)
            println("✅ Added $quantity item(s) to cart")
        } catch (e: Exception) {
            println("❌ Failed to add to cart: ${e.message}")
        }
    }
    
    private fun viewCart() {
        println("\n🛒 YOUR CART")
        println("-" * 30)
        
        try {
            val userId = sessionService.getCurrentUserId()
            val cartItems = cartService.viewCart(userId)
            
            if (cartItems.isEmpty()) {
                println("Your cart is empty")
                return
            }
            
            println("Item\t\t\tQty\tPrice\t\tTotal")
            println("-" * 60)
            
            cartItems.forEach { item ->
                println("${item.productName.take(20)}\t${item.quantity}\t$${item.unitPrice}\t\t$${item.totalPrice}")
            }
            
            val total = cartService.getCartTotal(userId)
            println("-" * 60)
            println("TOTAL: $$total")
            
            println("\nOptions: 'remove [productId]', 'update [productId] [quantity]', 'clear', or 'back'")
            print("Choice: ")
            val input = readLine()?.trim()?.split(" ") ?: return
            
            when (input[0].lowercase()) {
                "remove" -> {
                    if (input.size >= 2) {
                        try {
                            val productId = input[1].toLong()
                            cartService.removeItem(userId, productId)
                            println("✅ Item removed from cart")
                        } catch (e: NumberFormatException) {
                            println("❌ Invalid product ID")
                        }
                    }
                }
                "update" -> {
                    if (input.size >= 3) {
                        try {
                            val productId = input[1].toLong()
                            val quantity = input[2].toInt()
                            cartService.updateQuantity(userId, productId, quantity)
                            println("✅ Cart updated")
                        } catch (e: NumberFormatException) {
                            println("❌ Invalid input")
                        }
                    }
                }
                "clear" -> {
                    cartService.clearCart(userId)
                    println("✅ Cart cleared")
                }
            }
        } catch (e: Exception) {
            println("❌ Unable to view cart: ${e.message}")
        }
    }
    
    private fun checkout() {
        println("\n💳 CHECKOUT")
        println("-" * 20)
        
        try {
            val userId = sessionService.getCurrentUserId()
            val cartItems = cartService.viewCart(userId)
            
            if (cartItems.isEmpty()) {
                println("❌ Your cart is empty")
                return
            }
            
            val total = cartService.getCartTotal(userId)
            val orderItems = cartService.convertToOrderItems(userId)
            
            println("Order Summary:")
            cartItems.forEach { item ->
                println("  ${item.productName} x${item.quantity} = $${item.totalPrice}")
            }
            println("Total: $$total")
            
            print("\nConfirm order? (y/n): ")
            val confirm = readLine()?.trim()?.lowercase()
            
            if (confirm == "y" || confirm == "yes") {
                val order = Order(
                    id = 0L, // Will be assigned by OrderHistoryService
                    userId = userId,
                    items = orderItems,
                    totalAmount = total,
                    status = OrderStatus.PENDING,
                    createdAt = LocalDateTime.now()
                )
                
                val processedOrder = orderHistoryService.processAndSaveOrder(order)
                
                if (processedOrder.status == OrderStatus.CONFIRMED) {
                    cartService.clearCart(userId)
                    println("✅ Order #${processedOrder.id} confirmed!")
                    
                    // Attempt payment processing (may fail due to missing PaymentGateway)
                    try {
                        val paymentResult = paymentGateway.processPayment(
                            orderId = processedOrder.id,
                            amount = processedOrder.totalAmount,
                            cardToken = "demo_card_token"
                        )
                        println("💳 Payment: ${if (paymentResult.success) "SUCCESS" else "FAILED"}")
                    } catch (e: Exception) {
                        println("⚠️ Payment processing unavailable (DI issue): ${e.message}")
                    }
                } else {
                    println("❌ Order could not be confirmed. Status: ${processedOrder.status}")
                }
            } else {
                println("❌ Order cancelled")
            }
        } catch (e: Exception) {
            println("❌ Checkout failed: ${e.message}")
        }
    }
    
    private fun viewOrderHistory() {
        println("\n📋 ORDER HISTORY")
        println("-" * 30)
        
        try {
            val userId = sessionService.getCurrentUserId()
            val orders = orderHistoryService.getOrderHistory(userId)
            
            if (orders.isEmpty()) {
                println("No orders found")
                return
            }
            
            orders.forEach { order ->
                println("Order #${order.id} - ${order.createdAt.toLocalDate()} - ${order.status} - $${order.totalAmount}")
                order.items.forEach { item ->
                    println("  - Product ${item.productId} x${item.quantity} @ $${item.unitPrice}")
                }
                println()
            }
            
            val stats = orderHistoryService.getUserOrderStatistics(userId)
            println("📊 Your Statistics:")
            println("  Total Orders: ${stats.totalOrders}")
            println("  Confirmed: ${stats.confirmedOrders}")
            println("  Total Spent: $${stats.totalSpent}")
        } catch (e: Exception) {
            println("❌ Unable to load order history: ${e.message}")
        }
        
        println("\nPress Enter to continue...")
        readLine()
    }
    
    private fun profileSettings() {
        println("\n👤 PROFILE SETTINGS")
        println("-" * 30)
        
        val user = sessionService.getCurrentUser()!!
        println("Name: ${user.name}")
        println("Email: ${user.email}")
        println("Active: ${user.isActive}")
        
        try {
            val stats = orderHistoryService.getUserOrderStatistics(user.id)
            println("\n📊 Account Summary:")
            println("Orders: ${stats.totalOrders}")
            println("Total Spent: $${stats.totalSpent}")
        } catch (e: Exception) {
            println("❌ Unable to load account stats: ${e.message}")
        }
        
        println("\nPress Enter to continue...")
        readLine()
    }
    
    private fun adminFunctions() {
        println("\n🔧 ADMIN FUNCTIONS")
        println("-" * 30)
        println("1. View All Users")
        println("2. View All Orders")
        println("3. Inventory Management")
        println("4. Back")
        print("Choice: ")
        
        when (readLine()?.trim()) {
            "1" -> viewAllUsers()
            "2" -> viewAllOrders()
            "3" -> inventoryManagement()
        }
    }
    
    private fun viewAllUsers() {
        println("\n👥 ALL USERS")
        println("-" * 20)
        
        try {
            val users = userService.getAllUsers()
            users.forEach { user ->
                println("#${user.id}: ${user.name} (${user.email}) - ${if (user.isActive) "Active" else "Inactive"}")
            }
        } catch (e: Exception) {
            println("❌ Unable to load users: ${e.message}")
        }
        
        println("\nPress Enter to continue...")
        readLine()
    }
    
    private fun viewAllOrders() {
        println("\n📋 ALL ORDERS")
        println("-" * 20)
        
        try {
            val orders = orderHistoryService.getAllOrders()
            orders.forEach { order ->
                println("Order #${order.id} - User ${order.userId} - ${order.status} - $${order.totalAmount}")
            }
            
            val totalOrders = orderHistoryService.getOrderCount()
            println("\nTotal Orders: $totalOrders")
        } catch (e: Exception) {
            println("❌ Unable to load orders: ${e.message}")
        }
        
        println("\nPress Enter to continue...")
        readLine()
    }
    
    private fun inventoryManagement() {
        println("\n📦 INVENTORY MANAGEMENT")
        println("-" * 40)
        
        try {
            val products = productService.getAllProducts()
            
            println("Product\t\t\tStock")
            println("-" * 40)
            products.forEach { product ->
                val stock = inventoryService.getAvailableStock(product.id)
                val availability = if (stock > 0) "$stock units" else "Out of Stock"
                println("${product.name.take(20)}\t$availability")
            }
            
            print("\nRestock product? (Enter product ID or 'back'): ")
            val input = readLine()?.trim() ?: return
            
            if (input.lowercase() == "back") return
            
            try {
                val productId = input.toLong()
                print("Add quantity: ")
                val quantity = readLine()?.toIntOrNull() ?: 0
                
                if (quantity > 0) {
                    productService.updateProductStock(productId, quantity)
                    println("✅ Stock updated")
                } else {
                    println("❌ Invalid quantity")
                }
            } catch (e: NumberFormatException) {
                println("❌ Invalid product ID")
            }
        } catch (e: Exception) {
            println("❌ Unable to manage inventory: ${e.message}")
        }
    }
    
    private fun showProductStatistics() {
        println("\n📊 PRODUCT STATISTICS")
        println("-" * 40)
        
        try {
            val stats = productDataService.getProductStatistics()
            val categoryStats = productDataService.getProductCountByCategory()
            
            println("Total Products: ${stats.totalProducts}")
            println("Available Products: ${stats.availableProducts}")
            println("Categories: ${stats.categories}")
            println("Average Price: $${stats.averagePrice}")
            println("Price Range: $${stats.priceRange.first} - $${stats.priceRange.second}")
            
            println("\nProducts by Category:")
            categoryStats.forEach { (category, count) ->
                println("  $category: $count products")
            }
        } catch (e: Exception) {
            println("❌ Unable to load statistics: ${e.message}")
        }
        
        println("\nPress Enter to continue...")
        readLine()
    }
    
    private fun displayProducts(products: List<Product>) {
        if (products.isEmpty()) {
            println("No products found")
            return
        }
        
        println("ID\tName\t\t\tPrice\t\tCategory\t\tStock")
        println("-" * 80)
        
        products.forEach { product ->
            val stock = try {
                val available = inventoryService.getAvailableStock(product.id)
                if (available > 0) "$available units" else "Out of Stock"
            } catch (e: Exception) {
                "Unknown"
            }
            
            println("${product.id}\t${product.name.take(20)}\t$${product.price}\t\t${product.category.take(15)}\t$stock")
        }
    }
    
    private fun runStaticDemo() {
        // Keep original demo as fallback when DI issues prevent interactive mode
        println("\n📝 Testing User Operations")
        try {
            val user = userService.registerUser("demo@example.com", "Demo User")
            println("✅ Registered user: ${user.name} (${user.email})")
            
            val availableStock = inventoryService.getAvailableStock(1L)
            println("✅ Product 1 stock: $availableStock units")
        } catch (e: Exception) {
            println("❌ Demo failed: ${e.message}")
        }
        
        println("\n🎉 Static demo completed!")
    }
    
    private fun createSampleOrder(userId: Long): Order {
        return Order(
            id = System.currentTimeMillis(),
            userId = userId,
            items = listOf(
                OrderItem(productId = 1L, quantity = 2, unitPrice = BigDecimal("25.00")),
                OrderItem(productId = 2L, quantity = 1, unitPrice = BigDecimal("15.00"))
            ),
            totalAmount = BigDecimal("65.00"),
            status = OrderStatus.PENDING,
            createdAt = LocalDateTime.now()
        )
    }
}

fun main() {
    val app = SimpleECommerceApplication()
    app.start()
}

private operator fun String.times(n: Int): String = this.repeat(n)