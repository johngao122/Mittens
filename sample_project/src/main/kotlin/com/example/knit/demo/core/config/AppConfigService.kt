package com.example.knit.demo.core.config

import knit.Provides

data class DatabaseConfig(
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val maxConnections: Int
)

data class NotificationConfig(
    val emailEnabled: Boolean,
    val smsEnabled: Boolean,
    val maxRetries: Int,
    val adminEmail: String
)

data class BusinessConfig(
    val maxOrderItems: Int,
    val orderTimeoutMinutes: Long,
    val inventoryWarningThreshold: Int,
    val supportedCurrencies: List<String>
)
