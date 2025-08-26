package com.example.mittens.model

import org.junit.Test
import org.junit.Assert.*

class KnitComponentTypeDetectionTest {
    
    @Test
    fun testViewModelAndCompositeComponents() {
        // @KnitViewModel treated as COMPONENT
        val viewModelComponent = KnitComponent(
            className = "UserViewModel",
            packageName = "com.example",
            type = ComponentType.COMPONENT,
            dependencies = listOf(
                KnitDependency("userService", "UserService", false, null, false, false, false)
            ),
            providers = emptyList(),
            sourceFile = "UserViewModel.kt"
        )
        assertEquals(ComponentType.COMPONENT, viewModelComponent.type)
        assertFalse(viewModelComponent.dependencies.isEmpty())
        
        // Composite component (has dependencies and providers)
        val compositeComponent = KnitComponent(
            className = "EmailService",
            packageName = "com.example",
            type = ComponentType.COMPOSITE,
            dependencies = listOf(
                KnitDependency("config", "AppConfig", false, null, false, true, false)
            ),
            providers = listOf(
                KnitProvider("providePrimaryEmail", "EmailClient", null, true, "primary", false, false, false, false),
                KnitProvider("provideSecondaryEmail", "EmailClient", null, true, "secondary", false, false, false, false)
            ),
            sourceFile = "EmailService.kt"
        )
        assertEquals(ComponentType.COMPOSITE, compositeComponent.type)
        assertFalse(compositeComponent.dependencies.isEmpty())
        assertFalse(compositeComponent.providers.isEmpty())
    }
}
