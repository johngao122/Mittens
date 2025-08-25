package com.example.knit.demo.core.models

data class User(
    val id: Long,
    val email: String,
    val name: String,
    val isActive: Boolean = true
)