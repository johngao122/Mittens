package com.example.knit.demo.core.services

import knit.Provides
import java.time.LocalDateTime

data class AuditEvent(
    val id: String = "audit_${System.nanoTime()}",
    val userId: Long?,
    val action: String,
    val resource: String,
    val details: Map<String, Any> = emptyMap(),
    val timestamp: LocalDateTime = LocalDateTime.now()
)

// MISSING_COMPONENT_ANNOTATION: Missing @Provides annotation
// @Provides  // Commented out to demonstrate missing annotation error
class AuditService {
    
    private val auditLog = mutableListOf<AuditEvent>()
    
    fun logEvent(userId: Long?, action: String, resource: String, details: Map<String, Any> = emptyMap()) {
        val event = AuditEvent(
            userId = userId,
            action = action,
            resource = resource,
            details = details
        )
        auditLog.add(event)
        println("AuditService: Logged event - User $userId performed '$action' on '$resource'")
    }
    
    fun logUserAction(userId: Long, action: String, details: Map<String, Any> = emptyMap()) {
        logEvent(userId, action, "user", details + ("targetUserId" to userId))
    }
    
    fun logOrderAction(userId: Long?, orderId: Long, action: String, details: Map<String, Any> = emptyMap()) {
        logEvent(userId, action, "order", details + ("orderId" to orderId))
    }
    
    fun logProductAction(userId: Long?, productId: Long, action: String, details: Map<String, Any> = emptyMap()) {
        logEvent(userId, action, "product", details + ("productId" to productId))
    }
    
    fun logPaymentAction(userId: Long?, orderId: Long, action: String, details: Map<String, Any> = emptyMap()) {
        logEvent(userId, action, "payment", details + ("orderId" to orderId))
    }
    
    fun getAuditHistory(userId: Long? = null, resource: String? = null, limit: Int = 50): List<AuditEvent> {
        println("AuditService: Retrieving audit history (limit: $limit)")
        
        return auditLog
            .filter { userId == null || it.userId == userId }
            .filter { resource == null || it.resource == resource }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }
    
    fun clearAuditHistory() {
        println("AuditService: Clearing audit history")
        auditLog.clear()
    }
}