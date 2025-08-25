package com.example.knit.demo.core.models

import java.math.BigDecimal

data class Product(
    val id: Long,
    val name: String,
    val description: String,
    val price: BigDecimal,
    val category: String
)