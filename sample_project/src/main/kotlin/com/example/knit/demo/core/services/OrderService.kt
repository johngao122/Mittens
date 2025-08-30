package com.example.knit.demo.core.services

import com.example.knit.demo.core.models.Order
import com.example.knit.demo.core.models.OrderStatus
import knit.Provides
import knit.di

@Provides
class OrderService {
    
    private val inventoryService: InventoryService by di
    // CIRCULAR_DEPENDENCY: OrderService depends on ProductService which will depend on OrderService
    private val productService: ProductService by di
    
    fun processOrder(order: Order): Order {
        println("OrderService: Processing order ${order.id}")
        
        // CIRCULAR_DEPENDENCY: Validate all products exist before processing (uses ProductService)
        val allProductsExist = order.items.all { item ->
            productService.findProduct(item.productId) != null
        }
        
        if (!allProductsExist) {
            println("OrderService: Order ${order.id} cancelled - invalid products")
            return order.copy(status = OrderStatus.CANCELLED)
        }
        
        val hasStock = inventoryService.checkStock(order.items.map { it.productId })
        
        return if (hasStock) {
            inventoryService.reserveStock(order.items.map { it.productId to it.quantity }.toMap())
            order.copy(status = OrderStatus.CONFIRMED)
        } else {
            order.copy(status = OrderStatus.CANCELLED)
        }
    }
    
    fun cancelOrder(orderId: Long) {
        println("OrderService: Cancelling order $orderId")
        inventoryService.releaseReservedStock(orderId)
    }
}