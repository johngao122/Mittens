package com.example.knit.demo.core.services

import knit.Provides
import knit.di

@Provides
class InventoryService {
    
    private val orderService: OrderService by di
    
    private val inventory = mutableMapOf<Long, Int>(
        1L to 10,
        2L to 5,
        3L to 20,
        4L to 0
    )
    
    private val reservedStock = mutableMapOf<Long, MutableMap<Long, Int>>()
    
    fun checkStock(productIds: List<Long>): Boolean {
        println("InventoryService: Checking stock for products $productIds")
        return productIds.all { (inventory[it] ?: 0) > 0 }
    }
    
    fun reserveStock(productQuantities: Map<Long, Int>) {
        println("InventoryService: Reserving stock $productQuantities")
        productQuantities.forEach { (productId, quantity) ->
            val available = inventory[productId] ?: 0
            if (available >= quantity) {
                inventory[productId] = available - quantity
            }
        }
    }
    
    fun releaseReservedStock(orderId: Long) {
        println("InventoryService: Releasing reserved stock for order $orderId")
        
        reservedStock[orderId]?.let { reserved ->
            reserved.forEach { (productId, quantity) ->
                inventory[productId] = (inventory[productId] ?: 0) + quantity
            }
            reservedStock.remove(orderId)
        }
        
        orderService.cancelOrder(orderId)
    }
    
    fun getAvailableStock(productId: Long): Int {
        return inventory[productId] ?: 0
    }
}