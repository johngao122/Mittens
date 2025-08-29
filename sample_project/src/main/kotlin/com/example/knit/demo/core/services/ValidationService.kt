package com.example.knit.demo.core.services

import com.example.knit.demo.core.models.User
import com.example.knit.demo.core.models.Order
import com.example.knit.demo.core.models.Product

// MISSING_COMPONENT_ANNOTATION: This class is missing @Provides annotation
// but is still referenced by other services
// @Provides  // Missing annotation!
class ValidationService {
    
    fun validateUser(user: User): ValidationResult {
        val errors = mutableListOf<String>()
        
        if (user.email.isBlank()) {
            errors.add("Email cannot be blank")
        }
        
        if (!user.email.contains("@")) {
            errors.add("Email must contain @ symbol")
        }
        
        if (user.name.isBlank()) {
            errors.add("Name cannot be blank")
        }
        
        return ValidationResult(errors.isEmpty(), errors)
    }
    
    fun validateOrder(order: Order): ValidationResult {
        val errors = mutableListOf<String>()
        
        if (order.items.isEmpty()) {
            errors.add("Order must have at least one item")
        }
        
        if (order.userId <= 0) {
            errors.add("Valid user ID is required")
        }
        
        return ValidationResult(errors.isEmpty(), errors)
    }
    
    fun validateProduct(product: Product): ValidationResult {
        val errors = mutableListOf<String>()
        
        if (product.name.isBlank()) {
            errors.add("Product name cannot be blank")
        }
        
        if (product.price.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            errors.add("Product price must be positive")
        }
        
        return ValidationResult(errors.isEmpty(), errors)
    }
}

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String>
) {
    fun getErrorMessage(): String {
        return if (errors.isNotEmpty()) {
            "Validation failed: ${errors.joinToString(", ")}"
        } else {
            "Validation passed"
        }
    }
}

// MISSING_COMPONENT_ANNOTATION: Another class missing annotation but referenced elsewhere
class EmailValidationService {
    fun validateEmail(email: String): Boolean {
        return email.contains("@") && email.contains(".")
    }
    
    fun getEmailDomain(email: String): String? {
        return email.substringAfter("@").takeIf { it.isNotBlank() }
    }
}