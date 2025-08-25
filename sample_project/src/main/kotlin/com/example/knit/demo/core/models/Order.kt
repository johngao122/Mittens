package com.example.knit.demo.core.models

import java.math.BigDecimal
import java.time.LocalDateTime

data class Order(
    val id: Long,
    val userId: Long,
    val items: List<OrderItem>,
    val totalAmount: BigDecimal,
    val status: OrderStatus,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

data class OrderItem(
    val productId: Long,
    val quantity: Int,
    val unitPrice: BigDecimal
)

enum class OrderStatus {
    PENDING,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED
}