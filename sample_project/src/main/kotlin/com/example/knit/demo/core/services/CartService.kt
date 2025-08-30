package com.example.knit.demo.core.services

import com.example.knit.demo.core.models.OrderItem
import knit.Provides
import knit.di
import java.math.BigDecimal

@Provides
class CartService {
    
    private val productService: ProductService by di
    
    // In-memory cart storage: userId -> List<CartItem>
    private val userCarts = mutableMapOf<Long, MutableList<CartItem>>()
    
    fun addItem(userId: Long, productId: Long, quantity: Int = 1) {
        println("CartService: Adding $quantity of product $productId to user $userId's cart")
        
        val product = productService.findProduct(productId)
            ?: throw IllegalArgumentException("Product $productId not found")
        
        val cart = userCarts.getOrPut(userId) { mutableListOf() }
        val existingItem = cart.find { it.productId == productId }
        
        if (existingItem != null) {
            // Update quantity if item already exists
            cart.remove(existingItem)
            cart.add(existingItem.copy(quantity = existingItem.quantity + quantity))
        } else {
            // Add new item
            cart.add(CartItem(productId, product.name, quantity, product.price))
        }
    }
    
    fun removeItem(userId: Long, productId: Long) {
        println("CartService: Removing product $productId from user $userId's cart")
        userCarts[userId]?.removeIf { it.productId == productId }
    }
    
    fun updateQuantity(userId: Long, productId: Long, newQuantity: Int) {
        println("CartService: Updating product $productId quantity to $newQuantity for user $userId")
        
        if (newQuantity <= 0) {
            removeItem(userId, productId)
            return
        }
        
        val cart = userCarts[userId] ?: return
        val itemIndex = cart.indexOfFirst { it.productId == productId }
        
        if (itemIndex >= 0) {
            cart[itemIndex] = cart[itemIndex].copy(quantity = newQuantity)
        }
    }
    
    fun viewCart(userId: Long): List<CartItem> {
        return userCarts[userId]?.toList() ?: emptyList()
    }
    
    fun getCartTotal(userId: Long): BigDecimal {
        val cart = userCarts[userId] ?: return BigDecimal.ZERO
        return cart.sumOf { it.unitPrice.multiply(BigDecimal(it.quantity)) }
    }
    
    fun clearCart(userId: Long) {
        println("CartService: Clearing cart for user $userId")
        userCarts[userId]?.clear()
    }
    
    fun getCartItemCount(userId: Long): Int {
        return userCarts[userId]?.sumOf { it.quantity } ?: 0
    }
    
    fun convertToOrderItems(userId: Long): List<OrderItem> {
        val cart = userCarts[userId] ?: return emptyList()
        return cart.map { cartItem ->
            OrderItem(
                productId = cartItem.productId,
                quantity = cartItem.quantity,
                unitPrice = cartItem.unitPrice
            )
        }
    }
}

data class CartItem(
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal
) {
    val totalPrice: BigDecimal
        get() = unitPrice.multiply(BigDecimal(quantity))
}