package com.example.knit.demo.core.services

import com.example.knit.demo.core.models.Order
import com.example.knit.demo.core.models.OrderStatus
import knit.Provides
import knit.di

@Provides
class OrderHistoryService {
    
    private val orderService: OrderService by di
    
    // In-memory order storage: orderId -> Order
    private val orderHistory = mutableMapOf<Long, Order>()
    
    // Track next order ID
    private var nextOrderId = 1000L
    
    fun saveCompletedOrder(order: Order): Order {
        println("OrderHistoryService: Saving completed order ${order.id}")
        
        val savedOrder = order.copy(id = nextOrderId++)
        orderHistory[savedOrder.id] = savedOrder
        
        return savedOrder
    }
    
    fun processAndSaveOrder(order: Order): Order {
        println("OrderHistoryService: Processing and saving order")
        
        // Use the existing OrderService to process the order
        val processedOrder = orderService.processOrder(order)
        
        // Save the processed order to history
        return saveCompletedOrder(processedOrder)
    }
    
    fun getOrderHistory(userId: Long): List<Order> {
        println("OrderHistoryService: Getting order history for user $userId")
        return orderHistory.values
            .filter { it.userId == userId }
            .sortedByDescending { it.createdAt }
    }
    
    fun getAllOrders(): List<Order> {
        println("OrderHistoryService: Getting all orders (admin function)")
        return orderHistory.values.sortedByDescending { it.createdAt }
    }
    
    fun getOrderById(orderId: Long): Order? {
        return orderHistory[orderId]
    }
    
    fun getOrdersByStatus(status: OrderStatus): List<Order> {
        return orderHistory.values.filter { it.status == status }
    }
    
    fun cancelOrder(orderId: Long): Boolean {
        println("OrderHistoryService: Attempting to cancel order $orderId")
        
        val order = orderHistory[orderId]
        if (order != null && order.status == OrderStatus.PENDING) {
            // Cancel through OrderService to handle inventory
            orderService.cancelOrder(orderId)
            
            // Update order status in history
            val cancelledOrder = order.copy(status = OrderStatus.CANCELLED)
            orderHistory[orderId] = cancelledOrder
            
            println("OrderHistoryService: Order $orderId cancelled successfully")
            return true
        }
        
        println("OrderHistoryService: Cannot cancel order $orderId - not found or not pending")
        return false
    }
    
    fun getOrderCount(): Int {
        return orderHistory.size
    }
    
    fun getOrderCountByUser(userId: Long): Int {
        return orderHistory.values.count { it.userId == userId }
    }
    
    fun getUserOrderStatistics(userId: Long): OrderStatistics {
        val userOrders = getOrderHistory(userId)
        
        return OrderStatistics(
            totalOrders = userOrders.size,
            confirmedOrders = userOrders.count { it.status == OrderStatus.CONFIRMED },
            cancelledOrders = userOrders.count { it.status == OrderStatus.CANCELLED },
            pendingOrders = userOrders.count { it.status == OrderStatus.PENDING },
            totalSpent = userOrders.filter { it.status == OrderStatus.CONFIRMED }
                .sumOf { it.totalAmount }
        )
    }
}

data class OrderStatistics(
    val totalOrders: Int,
    val confirmedOrders: Int,
    val cancelledOrders: Int,
    val pendingOrders: Int,
    val totalSpent: java.math.BigDecimal
)