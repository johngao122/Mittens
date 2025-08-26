package com.example.mittens.model

import org.junit.Test
import org.junit.Assert.*

class KnitDependencyAdvancedFeaturesTest {
    
    @Test
    fun testAdvancedFlagsOnKnitDependency() {
        // Singleton dependency
        val singletonDep = KnitDependency(
            propertyName = "database",
            targetType = "DatabaseService",
            isNamed = false,
            namedQualifier = null,
            isFactory = false,
            isLoadable = false,
            isSingleton = true
        )
        assertTrue("Singleton dependency should be marked as singleton", singletonDep.isSingleton)
        
        // Named dependency
        val namedDep = KnitDependency(
            propertyName = "primaryEmail",
            targetType = "EmailClient",
            isNamed = true,
            namedQualifier = "primary",
            isFactory = false,
            isLoadable = false,
            isSingleton = false
        )
        assertTrue("Named dependency should be marked as named", namedDep.isNamed)
        assertEquals("Named qualifier should match", "primary", namedDep.namedQualifier)
        
        // Factory dependency (Factory<T>)
        val factoryDep = KnitDependency(
            propertyName = "userFactory",
            targetType = "Factory<User>",
            isNamed = false,
            namedQualifier = null,
            isFactory = true,
            isLoadable = false,
            isSingleton = false
        )
        assertTrue("Factory dependency should be marked as factory", factoryDep.isFactory)
        
        // Loadable dependency (Loadable<T>)
        val loadableDep = KnitDependency(
            propertyName = "configLoader",
            targetType = "Loadable<AppConfig>",
            isNamed = false,
            namedQualifier = null,
            isFactory = false,
            isLoadable = true,
            isSingleton = false
        )
        assertTrue("Loadable dependency should be marked as loadable", loadableDep.isLoadable)
        
        // Function type dependency (() -> T treated as factory)
        val functionDep = KnitDependency(
            propertyName = "notificationSender",
            targetType = "() -> NotificationSender",
            isNamed = false,
            namedQualifier = null,
            isFactory = true,
            isLoadable = false,
            isSingleton = false
        )
        assertTrue("Function dependency should be marked as factory", functionDep.isFactory)
    }
}
