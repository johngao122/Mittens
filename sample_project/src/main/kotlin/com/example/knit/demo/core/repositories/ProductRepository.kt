package com.example.knit.demo.core.repositories

import com.example.knit.demo.core.models.Product

interface ProductRepository {
    fun findById(id: Long): Product?
    fun findByCategory(category: String): List<Product>
    fun save(product: Product): Product
    fun findAll(): List<Product>
    fun updateStock(productId: Long, quantity: Int)
    fun isInStock(productId: Long): Boolean
}