package com.example.knit.demo.core.services

import com.example.knit.demo.core.models.Order
import com.example.knit.demo.payment.PaymentGateway
import com.example.knit.demo.payment.PaymentResult
import knit.Provides
import knit.di

@Provides
class PaymentService {
    
    private val paymentGateway: PaymentGateway by di // Now resolved via SimplePaymentGateway
    
    fun processOrderPayment(order: Order, cardToken: String): PaymentResult {
        println("💳 PaymentService: Processing payment for order ${order.id}")
        
        return paymentGateway.processPayment(order.id, order.totalAmount, cardToken)
    }
    
    fun refundOrder(order: Order): PaymentResult {
        println("🔄 PaymentService: Processing refund for order ${order.id}")
        
        return paymentGateway.refund(order.id, order.totalAmount)
    }
}