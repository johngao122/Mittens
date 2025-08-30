package com.example.knit.demo.core.services

import com.example.knit.demo.core.models.Product
import com.example.knit.demo.core.repositories.ProductRepository
import knit.Provides
import knit.di
import java.math.BigDecimal

@Provides
class ProductDataService {
    
    private val productRepository: ProductRepository by di
    private var isInitialized = false
    
    fun initializeSampleProducts() {
        if (isInitialized) {
            println("ProductDataService: Sample products already initialized")
            return
        }
        
        println("ProductDataService: Initializing comprehensive product catalog")
        
        val additionalProducts = listOf(
            // Electronics
            Product(10L, "iPhone 15", "Latest Apple smartphone with advanced camera", BigDecimal("999.99"), "Electronics"),
            Product(11L, "Samsung TV", "55-inch 4K Smart TV with HDR", BigDecimal("649.99"), "Electronics"),
            Product(12L, "Bluetooth Headphones", "Noise-canceling wireless headphones", BigDecimal("149.99"), "Electronics"),
            Product(13L, "Tablet", "10-inch tablet for work and entertainment", BigDecimal("299.99"), "Electronics"),
            Product(14L, "Smart Watch", "Fitness tracking smartwatch", BigDecimal("199.99"), "Electronics"),
            
            // Books
            Product(20L, "Clean Code", "A handbook of agile software craftsmanship", BigDecimal("34.99"), "Books"),
            Product(21L, "Design Patterns", "Elements of reusable object-oriented software", BigDecimal("39.99"), "Books"),
            Product(22L, "Kotlin in Action", "Complete guide to Kotlin programming", BigDecimal("44.99"), "Books"),
            Product(23L, "System Design Interview", "An insider's guide", BigDecimal("28.99"), "Books"),
            Product(24L, "The Pragmatic Programmer", "Your journey to mastery", BigDecimal("32.99"), "Books"),
            
            // Furniture
            Product(30L, "Standing Desk", "Height-adjustable standing desk", BigDecimal("399.99"), "Furniture"),
            Product(31L, "Bookshelf", "5-tier wooden bookshelf", BigDecimal("129.99"), "Furniture"),
            Product(32L, "Office Lamp", "LED desk lamp with USB charging", BigDecimal("59.99"), "Furniture"),
            Product(33L, "Filing Cabinet", "3-drawer metal filing cabinet", BigDecimal("179.99"), "Furniture"),
            Product(34L, "Monitor Stand", "Adjustable dual monitor stand", BigDecimal("89.99"), "Furniture"),
            
            // Home & Garden
            Product(40L, "Coffee Maker", "Programmable drip coffee maker", BigDecimal("79.99"), "Home & Garden"),
            Product(41L, "Air Purifier", "HEPA air purifier for large rooms", BigDecimal("199.99"), "Home & Garden"),
            Product(42L, "Plant Pot", "Decorative ceramic plant pot", BigDecimal("19.99"), "Home & Garden"),
            Product(43L, "Kitchen Knife Set", "Professional 6-piece knife set", BigDecimal("89.99"), "Home & Garden"),
            Product(44L, "Vacuum Cleaner", "Cordless stick vacuum", BigDecimal("249.99"), "Home & Garden"),
            
            // Sports & Outdoors
            Product(50L, "Yoga Mat", "Non-slip exercise yoga mat", BigDecimal("24.99"), "Sports & Outdoors"),
            Product(51L, "Dumbbells", "Adjustable weight dumbbells", BigDecimal("149.99"), "Sports & Outdoors"),
            Product(52L, "Hiking Backpack", "40L waterproof hiking backpack", BigDecimal("79.99"), "Sports & Outdoors"),
            Product(53L, "Running Shoes", "Lightweight running shoes", BigDecimal("119.99"), "Sports & Outdoors"),
            Product(54L, "Water Bottle", "Insulated stainless steel bottle", BigDecimal("22.99"), "Sports & Outdoors")
        )
        
        // Save all additional products
        additionalProducts.forEach { product ->
            try {
                productRepository.save(product)
                println("  - Added: ${product.name} (${product.category})")
            } catch (e: Exception) {
                println("  - Failed to add ${product.name}: ${e.message}")
            }
        }
        
        isInitialized = true
        println("ProductDataService: Initialization complete - ${additionalProducts.size} additional products added")
    }
    
    fun getProductCategories(): List<String> {
        val allProducts = productRepository.findAll()
        return allProducts.map { it.category }.distinct().sorted()
    }
    
    fun getProductCountByCategory(): Map<String, Int> {
        val allProducts = productRepository.findAll()
        return allProducts.groupingBy { it.category }.eachCount()
    }
    
    fun getProductStatistics(): ProductStatistics {
        val allProducts = productRepository.findAll()
        val availableProducts = allProducts.filter { productRepository.isInStock(it.id) }
        
        return ProductStatistics(
            totalProducts = allProducts.size,
            availableProducts = availableProducts.size,
            categories = getProductCategories().size,
            averagePrice = if (allProducts.isNotEmpty()) {
                allProducts.map { it.price }.reduce { acc, price -> acc.add(price) }
                    .divide(BigDecimal(allProducts.size), 2, java.math.RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            },
            priceRange = if (allProducts.isNotEmpty()) {
                val prices = allProducts.map { it.price }
                prices.minOrNull()!! to prices.maxOrNull()!!
            } else {
                BigDecimal.ZERO to BigDecimal.ZERO
            }
        )
    }
}

data class ProductStatistics(
    val totalProducts: Int,
    val availableProducts: Int,
    val categories: Int,
    val averagePrice: BigDecimal,
    val priceRange: Pair<BigDecimal, BigDecimal>
)