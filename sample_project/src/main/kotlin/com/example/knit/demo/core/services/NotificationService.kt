package com.example.knit.demo.core.services

import knit.Provides

interface NotificationChannel {
    fun send(message: String, recipient: String)
}

class EmailChannel : NotificationChannel {
    override fun send(message: String, recipient: String) {
        println("📧 Email sent to $recipient: $message")
    }
}

class SmsChannel : NotificationChannel {
    override fun send(message: String, recipient: String) {
        println("📱 SMS sent to $recipient: $message")
    }
}

@Provides
class NotificationService {
    
    @Provides
    fun provideEmailChannel(): EmailChannel = EmailChannel()
    
    @Provides
    fun provideSmsChannel(): SmsChannel = SmsChannel()
    
    fun sendOrderConfirmation(email: String, orderId: Long) {
        val emailChannel = EmailChannel()
        emailChannel.send("Order $orderId confirmed!", email)
    }
    
    fun sendOrderUpdate(email: String, orderId: Long, status: String) {
        val emailChannel = EmailChannel()
        emailChannel.send("Order $orderId status: $status", email)
    }
}