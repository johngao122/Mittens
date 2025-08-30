package com.example.knit.demo.payment

import knit.Provides
import java.math.BigDecimal
import java.util.UUID

@Provides(PaymentGateway::class)
class SimplePaymentGateway : PaymentGateway {
    
    private val processedPayments = mutableMapOf<String, PaymentRecord>()
    
    override fun processPayment(orderId: Long, amount: BigDecimal, cardToken: String): PaymentResult {
        println("SimplePaymentGateway: Processing payment for order $orderId, amount $amount")
        
        // Simulate payment processing
        return when {
            amount <= BigDecimal.ZERO -> {
                PaymentResult(false, null, "Invalid amount: must be positive")
            }
            cardToken.isBlank() -> {
                PaymentResult(false, null, "Invalid card token")
            }
            cardToken.startsWith("invalid") -> {
                PaymentResult(false, null, "Payment declined: invalid card")
            }
            else -> {
                val transactionId = UUID.randomUUID().toString()
                processedPayments[transactionId] = PaymentRecord(orderId, amount, cardToken)
                PaymentResult(true, transactionId, null)
            }
        }
    }
    
    override fun refund(orderId: Long, amount: BigDecimal): PaymentResult {
        println("SimplePaymentGateway: Processing refund for order $orderId, amount $amount")
        
        // Find the original payment
        val originalPayment = processedPayments.values.find { it.orderId == orderId }
        
        return when {
            originalPayment == null -> {
                PaymentResult(false, null, "No payment found for order $orderId")
            }
            amount > originalPayment.amount -> {
                PaymentResult(false, null, "Refund amount cannot exceed original payment")
            }
            else -> {
                val refundTransactionId = "refund_${UUID.randomUUID()}"
                PaymentResult(true, refundTransactionId, null)
            }
        }
    }
    
    private data class PaymentRecord(
        val orderId: Long,
        val amount: BigDecimal,
        val cardToken: String
    )
}