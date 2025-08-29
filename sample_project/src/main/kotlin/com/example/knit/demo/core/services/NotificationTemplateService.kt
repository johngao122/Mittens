package com.example.knit.demo.core.services

import knit.Provides
import knit.di

data class NotificationTemplate(
    val id: String,
    val name: String,
    val subject: String,
    val body: String,
    val variables: List<String> = emptyList()
)

@Provides
class NotificationTemplateService {
    
    private val notificationManager: NotificationManager by di
    private val loggingService: LoggingService by di
    
    private val templates = mutableMapOf<String, NotificationTemplate>()
    
    init {
        // Initialize with default templates
        templates["order_confirmation"] = NotificationTemplate(
            id = "order_confirmation",
            name = "Order Confirmation",
            subject = "Order {orderId} Confirmed",
            body = "Dear {userName}, your order {orderId} for {totalAmount} has been confirmed. Expected delivery: {deliveryDate}",
            variables = listOf("orderId", "userName", "totalAmount", "deliveryDate")
        )
        
        templates["order_shipped"] = NotificationTemplate(
            id = "order_shipped",
            name = "Order Shipped",
            subject = "Order {orderId} Shipped",
            body = "Great news {userName}! Your order {orderId} has been shipped. Tracking number: {trackingNumber}",
            variables = listOf("orderId", "userName", "trackingNumber")
        )
        
        templates["welcome"] = NotificationTemplate(
            id = "welcome",
            name = "Welcome Message",
            subject = "Welcome to Our Store!",
            body = "Welcome {userName}! We're excited to have you join our community. Enjoy shopping!",
            variables = listOf("userName")
        )
        
        templates["password_reset"] = NotificationTemplate(
            id = "password_reset",
            name = "Password Reset",
            subject = "Password Reset Request",
            body = "Hi {userName}, you requested a password reset. Click here to reset: {resetLink}",
            variables = listOf("userName", "resetLink")
        )
    }
    
    fun renderTemplate(templateId: String, variables: Map<String, String>): String {
        loggingService.debug("NotificationTemplateService", "Rendering template $templateId")
        
        val template = templates[templateId] 
            ?: throw IllegalArgumentException("Template not found: $templateId")
        
        var renderedBody = template.body
        variables.forEach { (key, value) ->
            renderedBody = renderedBody.replace("{$key}", value)
        }
        
        return renderedBody
    }
    
    fun sendTemplatedNotification(
        templateId: String,
        recipient: String,
        phoneNumber: String?,
        userId: Long?,
        variables: Map<String, String>
    ) {
        try {
            val message = renderTemplate(templateId, variables)
            
            notificationManager.sendMultiChannelNotification(
                recipient = recipient,
                phoneNumber = phoneNumber,
                message = message,
                userId = userId,
                useEmail = true,
                useSms = phoneNumber != null
            )
            
            loggingService.info("NotificationTemplateService", "Templated notification sent using $templateId")
        } catch (e: Exception) {
            loggingService.error("NotificationTemplateService", "Failed to send templated notification", e)
            throw e
        }
    }
    
    fun sendOrderConfirmationTemplate(orderId: Long, userName: String, userEmail: String, totalAmount: String) {
        sendTemplatedNotification(
            templateId = "order_confirmation",
            recipient = userEmail,
            phoneNumber = null,
            userId = null,
            variables = mapOf(
                "orderId" to orderId.toString(),
                "userName" to userName,
                "totalAmount" to totalAmount,
                "deliveryDate" to "3-5 business days"
            )
        )
    }
    
    fun sendWelcomeTemplate(userName: String, userEmail: String, userId: Long) {
        sendTemplatedNotification(
            templateId = "welcome",
            recipient = userEmail,
            phoneNumber = null,
            userId = userId,
            variables = mapOf("userName" to userName)
        )
    }
    
    fun getTemplate(templateId: String): NotificationTemplate? {
        return templates[templateId]
    }
    
    fun getAllTemplates(): List<NotificationTemplate> {
        return templates.values.toList()
    }
}