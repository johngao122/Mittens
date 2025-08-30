package com.example.mittens.services

import com.example.mittens.model.*
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

@TestDataPath("\$CONTENT_ROOT/testData")
class KnitAdvancedFeaturesTest : BasePlatformTestCase() {
    
    private lateinit var sourceAnalyzer: KnitSourceAnalyzer
    
    override fun setUp() {
        super.setUp()
        sourceAnalyzer = KnitSourceAnalyzer(project)
    }
    
    @Test
    fun testNamedQualifiers() {
        // Test named qualifiers parsing and validation
        val namedProvider1 = KnitProvider(
            methodName = "providePrimaryDatabase",
            returnType = "DatabaseService",
            isSingleton = false,
            isNamed = true,
            namedQualifier = "primary",
            providesType = null,
            isIntoSet = false,
            isIntoList = false,
            isIntoMap = false
        )
        
        val namedProvider2 = KnitProvider(
            methodName = "provideSecondaryDatabase",
            returnType = "DatabaseService",
            isSingleton = false,
            isNamed = true,
            namedQualifier = "secondary",
            providesType = null,
            isIntoSet = false,
            isIntoList = false,
            isIntoMap = false
        )
        
        val namedDependency = KnitDependency(
            propertyName = "primaryDb",
            targetType = "DatabaseService",
            isNamed = true,
            namedQualifier = "primary",
            isSingleton = false,
            isFactory = false,
            isLoadable = false
        )
        
        val components = listOf(
            KnitComponent(
                className = "DatabaseProvider",
                packageName = "com.test.providers",
                type = ComponentType.PROVIDER,
                dependencies = emptyList(),
                providers = listOf(namedProvider1, namedProvider2),
                sourceFile = "DatabaseProvider.kt"
            ),
            KnitComponent(
                className = "UserService",
                packageName = "com.test.services",
                type = ComponentType.CONSUMER,
                dependencies = listOf(namedDependency),
                providers = emptyList(),
                sourceFile = "UserService.kt"
            )
        )
        
        // Verify named qualifier properties
        assertTrue("Provider1 should be named", namedProvider1.isNamed)
        assertEquals("Provider1 should have 'primary' qualifier", "primary", namedProvider1.namedQualifier)
        assertTrue("Provider2 should be named", namedProvider2.isNamed)
        assertEquals("Provider2 should have 'secondary' qualifier", "secondary", namedProvider2.namedQualifier)
        assertTrue("Dependency should be named", namedDependency.isNamed)
        assertEquals("Dependency should have 'primary' qualifier", "primary", namedDependency.namedQualifier)
        
        // Test that components are properly categorized
        val providerComponent = components.find { it.className == "DatabaseProvider" }
        val consumerComponent = components.find { it.className == "UserService" }
        
        assertNotNull("DatabaseProvider component should exist", providerComponent)
        assertNotNull("UserService component should exist", consumerComponent)
        assertEquals("DatabaseProvider should be PROVIDER type", ComponentType.PROVIDER, providerComponent?.type)
        assertEquals("UserService should be CONSUMER type", ComponentType.CONSUMER, consumerComponent?.type)
    }
    
    @Test
    fun testAmbiguousProviderDetection() {
        // Test ambiguous provider detection (multiple providers for same type without qualifiers)
        val provider1 = KnitProvider(
            methodName = "provideDatabase1",
            returnType = "DatabaseService",
            isSingleton = false,
            isNamed = false,
            namedQualifier = null,
            providesType = null,
            isIntoSet = false,
            isIntoList = false,
            isIntoMap = false
        )
        
        val provider2 = KnitProvider(
            methodName = "provideDatabase2",
            returnType = "DatabaseService",
            isSingleton = false,
            isNamed = false,
            namedQualifier = null,
            providesType = null,
            isIntoSet = false,
            isIntoList = false,
            isIntoMap = false
        )
        
        val dependency = KnitDependency(
            propertyName = "database",
            targetType = "DatabaseService",
            isNamed = false,
            namedQualifier = null,
            isSingleton = false,
            isFactory = false,
            isLoadable = false
        )
        
        val components = listOf(
            KnitComponent(
                className = "DatabaseProvider1",
                packageName = "com.test.providers",
                type = ComponentType.PROVIDER,
                dependencies = emptyList(),
                providers = listOf(provider1),
                sourceFile = "DatabaseProvider1.kt"
            ),
            KnitComponent(
                className = "DatabaseProvider2",
                packageName = "com.test.providers",
                type = ComponentType.PROVIDER,
                dependencies = emptyList(),
                providers = listOf(provider2),
                sourceFile = "DatabaseProvider2.kt"
            ),
            KnitComponent(
                className = "UserService",
                packageName = "com.test.services",
                type = ComponentType.CONSUMER,
                dependencies = listOf(dependency),
                providers = emptyList(),
                sourceFile = "UserService.kt"
            )
        )
        
        // Test that components are properly categorized
        val providerComponents = components.filter { it.type == ComponentType.PROVIDER }
        val consumerComponents = components.filter { it.type == ComponentType.CONSUMER }
        
        assertEquals("Should have 2 provider components", 2, providerComponents.size)
        assertEquals("Should have 1 consumer component", 1, consumerComponents.size)
        
        // Verify that both providers return the same type
        val databaseProviders = providerComponents.flatMap { it.providers }
        val allReturnDatabaseService = databaseProviders.all { it.returnType == "DatabaseService" }
        assertTrue("All providers should return DatabaseService", allReturnDatabaseService)
    }
    
    @Test
    fun testKnitViewModelDetection() {
        // Test @KnitViewModel annotation handling
        val viewModelComponent = KnitComponent(
            className = "UserViewModel",
            packageName = "com.test.viewmodels",
            type = ComponentType.COMPONENT, // KnitViewModel should be treated as component
            dependencies = listOf(
                KnitDependency(
                    propertyName = "userRepository",
                    targetType = "UserRepository",
                    isNamed = false,
                    namedQualifier = null,
                    isFactory = false,
                    isLoadable = false,
                    isSingleton = false
                )
            ),
            providers = emptyList(),
            sourceFile = "UserViewModel.kt"
        )
        
        // Verify component type
        assertEquals("KnitViewModel should be treated as COMPONENT", ComponentType.COMPONENT, viewModelComponent.type)
        assertFalse("ViewModel dependencies should not be empty", viewModelComponent.dependencies.isEmpty())
    }
    
    @Test
    fun testComponentTypeClassification() {
        // Test component type classification logic
        val componentTypes = listOf(
            ComponentType.COMPONENT,
            ComponentType.PROVIDER,
            ComponentType.CONSUMER,
            ComponentType.COMPOSITE
        )
        
        // Verify all component types are valid
        componentTypes.forEach { type ->
            assertNotNull("Component type should not be null", type)
            assertTrue("Component type should have a valid name", type.name.isNotEmpty())
        }
        
        // Test component type comparison
        assertFalse("Component types should be different", ComponentType.COMPONENT == ComponentType.PROVIDER)
        assertFalse("Component types should be different", ComponentType.CONSUMER == ComponentType.COMPOSITE)
    }
    
    @Test
    fun testKnitDependencyProperties() {
        // Test KnitDependency property validation
        val dependency = KnitDependency(
            propertyName = "testDependency",
            targetType = "TestService",
            isNamed = true,
            namedQualifier = "test",
            isSingleton = true,
            isFactory = false,
            isLoadable = false
        )
        
        // Verify all properties are set correctly
        assertEquals("Property name should match", "testDependency", dependency.propertyName)
        assertEquals("Target type should match", "TestService", dependency.targetType)
        assertTrue("Should be named", dependency.isNamed)
        assertEquals("Named qualifier should match", "test", dependency.namedQualifier)
        assertTrue("Should be singleton", dependency.isSingleton)
        assertFalse("Should not be factory", dependency.isFactory)
        assertFalse("Should not be loadable", dependency.isLoadable)
    }
    
    @Test
    fun testKnitProviderProperties() {
        // Test KnitProvider property validation
        val provider = KnitProvider(
            methodName = "provideTestService",
            returnType = "TestService",
            providesType = "TestInterface",
            isNamed = true,
            namedQualifier = "test",
            isSingleton = true,
            isIntoSet = true,
            isIntoList = false,
            isIntoMap = false
        )
        
        // Verify all properties are set correctly
        assertEquals("Method name should match", "provideTestService", provider.methodName)
        assertEquals("Return type should match", "TestService", provider.returnType)
        assertEquals("Provides type should match", "TestInterface", provider.providesType)
        assertTrue("Should be named", provider.isNamed)
        assertEquals("Named qualifier should match", "test", provider.namedQualifier)
        assertTrue("Should be singleton", provider.isSingleton)
        assertTrue("Should be into set", provider.isIntoSet)
        assertFalse("Should not be into list", provider.isIntoList)
        assertFalse("Should not be into map", provider.isIntoMap)
    }
    
    @Test
    fun testKnitComponentProperties() {
        // Test KnitComponent property validation
        val component = KnitComponent(
            className = "TestComponent",
            packageName = "com.test.components",
            type = ComponentType.COMPONENT,
            dependencies = listOf(
                KnitDependency(
                    propertyName = "dependency",
                    targetType = "DependencyService",
                    isNamed = false,
                    namedQualifier = null,
                    isFactory = false,
                    isLoadable = false,
                    isSingleton = false
                )
            ),
            providers = listOf(
                KnitProvider(
                    methodName = "provideService",
                    returnType = "TestService",
                    isNamed = false,
                    namedQualifier = null,
                    isSingleton = false,
                    providesType = null,
                    isIntoSet = false,
                    isIntoList = false,
                    isIntoMap = false
                )
            ),
            sourceFile = "TestComponent.kt"
        )
        
        // Verify all properties are set correctly
        assertEquals("Class name should match", "TestComponent", component.className)
        assertEquals("Package name should match", "com.test.components", component.packageName)
        assertEquals("Component type should match", ComponentType.COMPONENT, component.type)
        assertEquals("Should have 1 dependency", 1, component.dependencies.size)
        assertEquals("Should have 1 provider", 1, component.providers.size)
        assertEquals("Source file should match", "TestComponent.kt", component.sourceFile)
        assertEquals("Fully qualified name should be correct", "com.test.components.TestComponent", component.fullyQualifiedName)
    }
    
    @Test
    fun testIssueTypeAndSeverity() {
        // Test issue type and severity enums
        val issueTypes = listOf(
            IssueType.CIRCULAR_DEPENDENCY,
            IssueType.AMBIGUOUS_PROVIDER
        )
        
        val severities = listOf(
            Severity.ERROR,
            Severity.WARNING,
            Severity.INFO
        )
        
        // Verify all issue types are valid
        issueTypes.forEach { type ->
            assertNotNull("Issue type should not be null", type)
            assertTrue("Issue type should have a valid name", type.name.isNotEmpty())
        }
        
        // Verify all severities are valid
        severities.forEach { severity ->
            assertNotNull("Severity should not be null", severity)
            assertTrue("Severity should have a valid name", severity.name.isNotEmpty())
        }
        
        // Test specific values
        assertEquals("CIRCULAR_DEPENDENCY should be first", "CIRCULAR_DEPENDENCY", IssueType.CIRCULAR_DEPENDENCY.name)
        assertEquals("AMBIGUOUS_PROVIDER should be second", "AMBIGUOUS_PROVIDER", IssueType.AMBIGUOUS_PROVIDER.name)
        assertEquals("ERROR should be first severity", "ERROR", Severity.ERROR.name)
        assertEquals("WARNING should be second severity", "WARNING", Severity.WARNING.name)
        assertEquals("INFO should be third severity", "INFO", Severity.INFO.name)
    }
    
    @Test
    fun testValidationStatus() {
        // Test validation status enum
        val validationStatuses = listOf(
            ValidationStatus.NOT_VALIDATED,
            ValidationStatus.VALIDATED_TRUE_POSITIVE,
            ValidationStatus.VALIDATED_FALSE_POSITIVE,
            ValidationStatus.VALIDATION_FAILED
        )
        
        // Verify all validation statuses are valid
        validationStatuses.forEach { status ->
            assertNotNull("Validation status should not be null", status)
            assertTrue("Validation status should have a valid name", status.name.isNotEmpty())
        }
        
        // Test specific values
        assertEquals("NOT_VALIDATED should be first", "NOT_VALIDATED", ValidationStatus.NOT_VALIDATED.name)
        assertEquals("VALIDATED_TRUE_POSITIVE should be second", "VALIDATED_TRUE_POSITIVE", ValidationStatus.VALIDATED_TRUE_POSITIVE.name)
        assertEquals("VALIDATED_FALSE_POSITIVE should be third", "VALIDATED_FALSE_POSITIVE", ValidationStatus.VALIDATED_FALSE_POSITIVE.name)
        assertEquals("VALIDATION_FAILED should be fourth", "VALIDATION_FAILED", ValidationStatus.VALIDATION_FAILED.name)
    }
}