package com.example.knit.demo.core.services

import com.example.knit.demo.core.models.Product
import com.example.knit.demo.core.repositories.ProductRepository
import knit.Provides
import knit.Named
import knit.di

@Provides
class ProductService {
    
    // Simple injection without named qualifiers
    private val productRepository: ProductRepository by di
    // CIRCULAR_DEPENDENCY: ProductService depends on OrderService which depends on ProductService
    private val orderService: OrderService by di
    
    fun findProduct(productId: Long): Product? {
        println("ProductService: Finding product $productId")
        return productRepository.findById(productId)
    }
    
    fun getProductsByCategory(category: String): List<Product> {
        println("ProductService: Getting products in category $category")
        return productRepository.findByCategory(category)
    }
    
    fun getAllProducts(): List<Product> {
        println("ProductService: Getting all products")
        return productRepository.findAll()
    }
    
    fun checkProductAvailability(productId: Long): Boolean {
        println("ProductService: Checking availability for product $productId")
        return productRepository.isInStock(productId)
    }
    
    fun updateProductStock(productId: Long, quantityChange: Int) {
        println("ProductService: Updating stock for product $productId by $quantityChange")
        productRepository.updateStock(productId, quantityChange)
    }
    
    fun createProduct(product: Product): Product {
        println("ProductService: Creating new product ${product.name}")
        return productRepository.save(product)
    }
    
    fun getProductOrderHistory(productId: Long): Map<String, Any> {
        // CIRCULAR_DEPENDENCY: This method would need to access OrderService to get order history
        // For demo purposes, we'll just call a method that triggers the dependency
        println("ProductService: Getting order history for product $productId - this would use OrderService")
        // Note: In a real implementation, this would cause infinite recursion
        // We're just demonstrating the circular dependency for Knit to detect
        return mapOf(
            "productId" to productId,
            "hasOrderService" to (orderService != null)
        )
    }
}

// NAMED_QUALIFIER_MISMATCH: More examples of qualifier mismatches
@Provides
class ProductCacheService {
    // Simple injection without named qualifiers
    private val productRepository: ProductRepository by di
    
    fun cacheProduct(productId: Long): Product? {
        println("ProductCacheService: Caching product $productId")
        return productRepository.findById(productId)
    }
}

@Provides  
class ProductAnalyticsService {
    // Simple injection without named qualifiers
    private val productRepository: ProductRepository by di
    
    fun getProductStats(productId: Long): Map<String, Any> {
        val product = productRepository.findById(productId)
        return mapOf(
            "product_id" to productId,
            "exists" to (product != null),
            "category" to (product?.category ?: "unknown")
        )
    }
}