package com.example.knit.demo.payment

import java.math.BigDecimal

interface PaymentGateway {
    fun processPayment(orderId: Long, amount: BigDecimal, cardToken: String): PaymentResult
    fun refund(orderId: Long, amount: BigDecimal): PaymentResult
}

data class PaymentResult(
    val success: Boolean,
    val transactionId: String?,
    val errorMessage: String?
)