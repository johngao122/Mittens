package com.example.knit.demo.core.services

import com.example.knit.demo.core.models.Order
import com.example.knit.demo.core.models.OrderStatus
import knit.Provides
import knit.di

@Provides
class OrderService {
    
    private val inventoryService: InventoryService by di
    
    fun processOrder(order: Order): Order {
        println("📦 OrderService: Processing order ${order.id}")
        
        val hasStock = inventoryService.checkStock(order.items.map { it.productId })
        
        return if (hasStock) {
            inventoryService.reserveStock(order.items.map { it.productId to it.quantity }.toMap())
            order.copy(status = OrderStatus.CONFIRMED)
        } else {
            order.copy(status = OrderStatus.CANCELLED)
        }
    }
    
    fun cancelOrder(orderId: Long) {
        println("❌ OrderService: Cancelling order $orderId")
        inventoryService.releaseReservedStock(orderId)
    }
}