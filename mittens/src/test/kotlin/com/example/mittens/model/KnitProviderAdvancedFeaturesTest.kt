package com.example.mittens.model

import org.junit.Test
import org.junit.Assert.*

class KnitProviderAdvancedFeaturesTest {
    
    @Test
    fun testAdvancedFlagsOnKnitProvider() {
        // Singleton provider
        val singletonProvider = KnitProvider(
            methodName = "provideDatabaseService",
            returnType = "DatabaseService",
            providesType = null,
            isNamed = false,
            namedQualifier = null,
            isSingleton = true,
            isIntoSet = false,
            isIntoList = false,
            isIntoMap = false
        )
        assertTrue("Singleton provider should be marked as singleton", singletonProvider.isSingleton)
        
        // Named provider
        val namedProvider = KnitProvider(
            methodName = "providePrimaryEmailClient",
            returnType = "EmailClient",
            providesType = null,
            isNamed = true,
            namedQualifier = "primary",
            isSingleton = false,
            isIntoSet = false,
            isIntoList = false,
            isIntoMap = false
        )
        assertTrue("Named provider should be marked as named", namedProvider.isNamed)
        assertEquals("Named qualifier should match", "primary", namedProvider.namedQualifier)
        
        // Interface injection provider via providesType
        val interfaceProvider = KnitProvider(
            methodName = "providePaymentGateway",
            returnType = "StripePaymentGateway",
            providesType = "PaymentGateway",
            isNamed = false,
            namedQualifier = null,
            isSingleton = false,
            isIntoSet = false,
            isIntoList = false,
            isIntoMap = false
        )
        assertEquals(
            "Interface injection should have correct provides type",
            "PaymentGateway",
            interfaceProvider.providesType
        )
        
        // IntoSet provider
        val intoSetProvider = KnitProvider(
            methodName = "providePlugin",
            returnType = "Plugin",
            providesType = null,
            isNamed = false,
            namedQualifier = null,
            isSingleton = false,
            isIntoSet = true,
            isIntoList = false,
            isIntoMap = false
        )
        assertTrue("IntoSet provider should be marked correctly", intoSetProvider.isIntoSet)
    }
}
