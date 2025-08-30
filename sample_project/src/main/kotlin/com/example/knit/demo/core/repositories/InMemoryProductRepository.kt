package com.example.knit.demo.core.repositories

import com.example.knit.demo.core.models.Product
import knit.Provides
import knit.Named
import java.math.BigDecimal

// Simple provider without named qualifier
@Provides(ProductRepository::class)
class InMemoryProductRepository : ProductRepository {
    
    private val products = mutableMapOf<Long, Product>()
    private val stock = mutableMapOf<Long, Int>()
    
    init {
        
        val product1 = Product(1L, "Laptop", "Gaming laptop with high specs", BigDecimal("999.99"), "Electronics")
        val product2 = Product(2L, "Mouse", "Wireless gaming mouse", BigDecimal("49.99"), "Electronics")
        val product3 = Product(3L, "Book", "Programming guide", BigDecimal("29.99"), "Books")
        val product4 = Product(4L, "Chair", "Ergonomic office chair", BigDecimal("199.99"), "Furniture")
        
        products[1L] = product1
        products[2L] = product2
        products[3L] = product3
        products[4L] = product4
        
        
        stock[1L] = 10
        stock[2L] = 25
        stock[3L] = 50
        stock[4L] = 0 
    }
    
    override fun findById(id: Long): Product? {
        return products[id]
    }
    
    override fun findByCategory(category: String): List<Product> {
        return products.values.filter { it.category == category }
    }
    
    override fun save(product: Product): Product {
        products[product.id] = product
        return product
    }
    
    override fun findAll(): List<Product> {
        return products.values.toList()
    }
    
    override fun updateStock(productId: Long, quantity: Int) {
        val currentStock = stock[productId] ?: 0
        stock[productId] = maxOf(0, currentStock + quantity)
    }
    
    override fun isInStock(productId: Long): Boolean {
        return (stock[productId] ?: 0) > 0
    }
}

// NAMED_QUALIFIER_MISMATCH: Provider with different named qualifier - REMOVED to fix issues
// @Provides(ProductRepository::class) - Removed to avoid ambiguous provider
// @Named("backup")  // Provider uses "backup"
class BackupProductRepository : ProductRepository {
    private val backupProducts = mutableMapOf<Long, Product>()
    
    override fun findById(id: Long): Product? {
        println("BackupProductRepository: Finding backup product $id")
        return backupProducts[id]
    }
    
    override fun findByCategory(category: String): List<Product> {
        return backupProducts.values.filter { it.category == category }
    }
    
    override fun save(product: Product): Product {
        backupProducts[product.id] = product
        return product
    }
    
    override fun findAll(): List<Product> = backupProducts.values.toList()
    
    override fun updateStock(productId: Long, quantity: Int) {
        println("BackupProductRepository: Stock updates not supported")
    }
    
    override fun isInStock(productId: Long): Boolean = false
}