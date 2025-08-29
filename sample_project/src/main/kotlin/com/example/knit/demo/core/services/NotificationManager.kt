package com.example.knit.demo.core.services

import knit.Provides
import knit.di

@Provides
class NotificationManager {
    
    // Inject the NotificationService to access its provider methods
    // This demonstrates consumption of the "dead" provider methods
    private val notificationService: NotificationService by di
    private val loggingService: LoggingService by di
    private val auditService: AuditService by di
    
    // Get channels from the previously "dead" provider methods
    private val emailChannel: EmailChannel by lazy { notificationService.provideEmailChannel() }
    private val smsChannel: SmsChannel by lazy { notificationService.provideSmsChannel() }
    
    fun sendMultiChannelNotification(
        recipient: String,
        phoneNumber: String?,
        message: String,
        userId: Long? = null,
        useEmail: Boolean = true,
        useSms: Boolean = false
    ) {
        println("📢 NotificationManager: Sending multi-channel notification")
        
        val channels = mutableListOf<String>()
        
        if (useEmail) {
            try {
                emailChannel.send(message, recipient)
                channels.add("email")
                loggingService.info("NotificationManager", "Email sent to $recipient")
            } catch (e: Exception) {
                loggingService.error("NotificationManager", "Failed to send email to $recipient", e)
            }
        }
        
        if (useSms && phoneNumber != null) {
            try {
                smsChannel.send(message, phoneNumber)
                channels.add("sms")
                loggingService.info("NotificationManager", "SMS sent to $phoneNumber")
            } catch (e: Exception) {
                loggingService.error("NotificationManager", "Failed to send SMS to $phoneNumber", e)
            }
        }
        
        // Log to audit service
        auditService.logEvent(
            userId = userId,
            action = "notification_sent",
            resource = "notification",
            details = mapOf(
                "recipient" to recipient,
                "channels" to channels,
                "message_preview" to message.take(50)
            )
        )
    }
    
    fun sendOrderNotification(orderId: Long, userId: Long, userEmail: String, userPhone: String?, message: String) {
        println("📦 NotificationManager: Sending order notification for order $orderId")
        
        sendMultiChannelNotification(
            recipient = userEmail,
            phoneNumber = userPhone,
            message = message,
            userId = userId,
            useEmail = true,
            useSms = userPhone != null
        )
        
        auditService.logOrderAction(userId, orderId, "order_notification_sent", mapOf("message_type" to "order_update"))
    }
    
    fun sendUserWelcome(userId: Long, userEmail: String, userName: String) {
        println("👋 NotificationManager: Sending welcome message to user $userId")
        
        val welcomeMessage = "Welcome to our e-commerce platform, $userName! We're glad to have you."
        
        sendMultiChannelNotification(
            recipient = userEmail,
            phoneNumber = null,
            message = welcomeMessage,
            userId = userId,
            useEmail = true,
            useSms = false
        )
        
        auditService.logUserAction(userId, "welcome_notification_sent")
    }
    
    fun sendSystemAlert(message: String, adminEmail: String) {
        println("🚨 NotificationManager: Sending system alert")
        
        val alertMessage = "SYSTEM ALERT: $message"
        
        sendMultiChannelNotification(
            recipient = adminEmail,
            phoneNumber = null,
            message = alertMessage,
            userId = null,
            useEmail = true,
            useSms = false
        )
        
        auditService.logEvent(null, "system_alert_sent", "system", mapOf("alert_message" to message))
        loggingService.warn("NotificationManager", "System alert sent: $message")
    }
}