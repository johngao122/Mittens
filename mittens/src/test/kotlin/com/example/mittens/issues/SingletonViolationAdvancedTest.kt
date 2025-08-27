package com.example.mittens.issues

import com.example.mittens.model.*
import com.example.mittens.services.AdvancedIssueDetector
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

/**
 * Advanced tests for singleton violation detection with lifecycle analysis
 * Tests component-level vs global singletons and lifecycle mismatches
 */
class SingletonViolationAdvancedTest : BasePlatformTestCase() {
    
    private lateinit var advancedDetector: AdvancedIssueDetector
    
    override fun setUp() {
        super.setUp()
        advancedDetector = AdvancedIssueDetector(project)
    }
    
    @Test
    fun testMultipleSingletonProvidersForSameType() {
        // Two singleton providers for the same type - violation
        val provider1 = createTestProvider("provideDatabase1", "DatabaseService", isSingleton = true)
        val provider2 = createTestProvider("provideDatabase2", "DatabaseService", isSingleton = true)
        
        val component1 = createTestComponent("DatabaseProvider1", "com.test",
            providers = listOf(provider1)
        )
        val component2 = createTestComponent("DatabaseProvider2", "com.test",
            providers = listOf(provider2)
        )
        
        val components = listOf(component1, component2)
        val issues = advancedDetector.detectAdvancedSingletonViolations(components)
        
        assertEquals("Should detect 1 singleton violation", 1, issues.size)
        val issue = issues.first()
        assertEquals(IssueType.SINGLETON_VIOLATION, issue.type)
        assertEquals(Severity.ERROR, issue.severity)
        assertTrue("Message should mention multiple singleton providers", 
                  issue.message.contains("Multiple singleton providers"))
        assertTrue("Message should mention DatabaseService", issue.message.contains("DatabaseService"))
        
        // Check metadata
        val metadata = issue.metadata
        assertNotNull("Should have metadata", metadata)
        assertEquals("Should have conflicting type", "DatabaseService", metadata["conflictingType"])
        assertEquals("Should have 2 providers", 2, metadata["providerCount"])
        
        val providers = metadata["providers"] as? List<*>
        assertEquals("Should list both providers", 2, providers?.size)
    }
    
    @Test
    fun testSingletonDependencyWithNonSingletonProvider() {
        // Component requires singleton but provider is not singleton - lifecycle mismatch
        val singletonDep = createTestDependency("database", "DatabaseService", isSingleton = true)
        val consumer = createTestComponent("UserService", "com.test.service",
            dependencies = listOf(singletonDep)
        )
        
        val nonSingletonProvider = createTestProvider("provideDatabase", "DatabaseService", 
                                                     isSingleton = false)
        val provider = createTestComponent("DatabaseProvider", "com.test.provider",
            providers = listOf(nonSingletonProvider)
        )
        
        val components = listOf(consumer, provider)
        val issues = advancedDetector.detectAdvancedSingletonViolations(components)
        
        assertEquals("Should detect 1 lifecycle mismatch", 1, issues.size)
        val issue = issues.first()
        assertEquals(IssueType.SINGLETON_VIOLATION, issue.type)
        assertEquals(Severity.WARNING, issue.severity)
        assertTrue("Message should mention singleton dependency with non-singleton provider",
                  issue.message.contains("provided by non-singleton providers"))
        assertTrue("Suggested fix should mention marking providers as @Singleton",
                  issue.suggestedFix?.contains("@Singleton") ?: false)
        
        val metadata = issue.metadata
        assertEquals("Should have dependency type", "DatabaseService", metadata["dependencyType"])
        assertEquals("Should have consumer component", "com.test.service.UserService", metadata["consumerComponent"])
    }
    
    @Test
    fun testValidSingletonConfiguration() {
        // Singleton dependency with singleton provider - should not report issues
        val singletonDep = createTestDependency("database", "DatabaseService", isSingleton = true)
        val consumer = createTestComponent("UserService", "com.test.service",
            dependencies = listOf(singletonDep)
        )
        
        val singletonProvider = createTestProvider("provideDatabase", "DatabaseService", 
                                                  isSingleton = true)
        val provider = createTestComponent("DatabaseProvider", "com.test.provider",
            providers = listOf(singletonProvider)
        )
        
        val components = listOf(consumer, provider)
        val issues = advancedDetector.detectAdvancedSingletonViolations(components)
        
        assertTrue("Should not detect any singleton violations", issues.isEmpty())
    }
    
    @Test
    fun testMultipleSingletonDependenciesWithMixedProviders() {
        // Multiple singleton dependencies, some have singleton providers, others don't
        val dbDep = createTestDependency("database", "DatabaseService", isSingleton = true)
        val cacheDep = createTestDependency("cache", "CacheService", isSingleton = true)
        val logDep = createTestDependency("logger", "LogService", isSingleton = true)
        
        val consumer = createTestComponent("BusinessService", "com.test.service",
            dependencies = listOf(dbDep, cacheDep, logDep)
        )
        
        // Database - singleton provider (good)
        val dbProvider = createTestProvider("provideDatabase", "DatabaseService", isSingleton = true)
        val dbComponent = createTestComponent("DatabaseProvider", "com.test.db",
            providers = listOf(dbProvider)
        )
        
        // Cache - non-singleton provider (violation)
        val cacheProvider = createTestProvider("provideCache", "CacheService", isSingleton = false)
        val cacheComponent = createTestComponent("CacheProvider", "com.test.cache",
            providers = listOf(cacheProvider)
        )
        
        // Logger - two non-singleton providers (violation)
        val logProvider1 = createTestProvider("provideLogger1", "LogService", isSingleton = false)
        val logProvider2 = createTestProvider("provideLogger2", "LogService", isSingleton = false)
        val logComponent1 = createTestComponent("LogProvider1", "com.test.log",
            providers = listOf(logProvider1)
        )
        val logComponent2 = createTestComponent("LogProvider2", "com.test.log",
            providers = listOf(logProvider2)
        )
        
        val components = listOf(consumer, dbComponent, cacheComponent, logComponent1, logComponent2)
        val issues = advancedDetector.detectAdvancedSingletonViolations(components)
        
        // Should detect 2 lifecycle mismatches (cache and logger)
        val lifecycleMismatches = issues.filter { 
            it.severity == Severity.WARNING && it.message.contains("non-singleton providers")
        }
        assertEquals("Should detect 2 lifecycle mismatches", 2, lifecycleMismatches.size)
        
        val cacheIssue = lifecycleMismatches.find { it.message.contains("CacheService") }
        val logIssue = lifecycleMismatches.find { it.message.contains("LogService") }
        
        assertNotNull("Should have cache lifecycle mismatch", cacheIssue)
        assertNotNull("Should have logger lifecycle mismatch", logIssue)
    }
    
    @Test
    fun testSingletonProvidersWithDifferentQualifiers() {
        // Multiple singleton providers for same type but different qualifiers - should be OK
        val primaryProvider = createTestProvider("providePrimaryDb", "DatabaseService",
            isSingleton = true, isNamed = true, namedQualifier = "primary")
        val secondaryProvider = createTestProvider("provideSecondaryDb", "DatabaseService",
            isSingleton = true, isNamed = true, namedQualifier = "secondary")
        
        val component1 = createTestComponent("PrimaryDbProvider", "com.test",
            providers = listOf(primaryProvider)
        )
        val component2 = createTestComponent("SecondaryDbProvider", "com.test",
            providers = listOf(secondaryProvider)
        )
        
        val components = listOf(component1, component2)
        val issues = advancedDetector.detectAdvancedSingletonViolations(components)
        
        // Should not report violation since they have different qualifiers
        val singletonViolations = issues.filter { 
            it.type == IssueType.SINGLETON_VIOLATION && it.severity == Severity.ERROR
        }
        assertTrue("Should not detect singleton violations for different qualifiers", 
                  singletonViolations.isEmpty())
    }
    
    @Test
    fun testComponentLevelSingletons() {
        // Test component-level singleton behavior (simplified)
        val componentProvider = createTestProvider("provideService", "ComponentService", 
                                                  isSingleton = true)
        val component = createTestComponent("ServiceComponent", "com.test.component",
            providers = listOf(componentProvider)
        )
        
        val dependency = createTestDependency("service", "ComponentService", isSingleton = true)
        val consumer = createTestComponent("Consumer", "com.test.consumer",
            dependencies = listOf(dependency)
        )
        
        val components = listOf(component, consumer)
        val issues = advancedDetector.detectAdvancedSingletonViolations(components)
        
        // Should not detect issues for proper component-level singleton
        assertTrue("Component-level singletons should not trigger violations", issues.isEmpty())
    }
    
    @Test
    fun testGlobalSingletonConflicts() {
        // Multiple global singleton providers - clear violation
        val globalProvider1 = createTestProvider("provideGlobalService1", "GlobalService", 
                                                 isSingleton = true)
        val globalProvider2 = createTestProvider("provideGlobalService2", "GlobalService", 
                                                 isSingleton = true)
        val globalProvider3 = createTestProvider("provideGlobalService3", "GlobalService", 
                                                 isSingleton = true)
        
        val component1 = createTestComponent("GlobalProvider1", "com.test.global",
            providers = listOf(globalProvider1)
        )
        val component2 = createTestComponent("GlobalProvider2", "com.test.global",
            providers = listOf(globalProvider2)
        )
        val component3 = createTestComponent("GlobalProvider3", "com.test.global",
            providers = listOf(globalProvider3)
        )
        
        val components = listOf(component1, component2, component3)
        val issues = advancedDetector.detectAdvancedSingletonViolations(components)
        
        assertEquals("Should detect 1 global singleton violation", 1, issues.size)
        val issue = issues.first()
        assertEquals(Severity.ERROR, issue.severity)
        assertTrue("Message should mention multiple singleton providers", 
                  issue.message.contains("Multiple singleton providers"))
        
        val providerCount = issue.metadata["providerCount"] as? Int
        assertEquals("Should have 3 conflicting providers", 3, providerCount)
        
        assertTrue("Suggested fix should mention removing duplicates", 
                  issue.suggestedFix?.contains("Remove duplicate") ?: false)
    }
    
    @Test
    fun testSingletonLifecycleComplexScenario() {
        // Complex scenario with multiple types and mixed lifecycle requirements
        
        // Singleton dependency chain: A (singleton) -> B (singleton) -> C (non-singleton)
        val depAtoB = createTestDependency("serviceB", "ServiceB", isSingleton = true)
        val depBtoC = createTestDependency("serviceC", "ServiceC", isSingleton = false)
        
        val componentA = createTestComponent("ComponentA", "com.test",
            dependencies = listOf(depAtoB)
        )
        val componentB = createTestComponent("ComponentB", "com.test",
            dependencies = listOf(depBtoC)
        )
        
        // Providers
        val providerB = createTestProvider("provideServiceB", "ServiceB", isSingleton = true)
        val providerC = createTestProvider("provideServiceC", "ServiceC", isSingleton = false)
        
        val providerComponentB = createTestComponent("ProviderB", "com.test.provider",
            providers = listOf(providerB)
        )
        val providerComponentC = createTestComponent("ProviderC", "com.test.provider",
            providers = listOf(providerC)
        )
        
        val components = listOf(componentA, componentB, providerComponentB, providerComponentC)
        val issues = advancedDetector.detectAdvancedSingletonViolations(components)
        
        // Should not detect violations since B is properly singleton and C is properly non-singleton
        assertTrue("Complex valid scenario should not have violations", issues.isEmpty())
    }
    
    @Test
    fun testSingletonViolationWithInheritance() {
        // Test singleton violations with interface inheritance
        val interfaceProvider = createTestProvider("provideUserService", "UserService", 
                                                   isSingleton = true, providesType = "UserService")
        val implProvider = createTestProvider("provideUserServiceImpl", "UserServiceImpl", 
                                             isSingleton = true, providesType = "UserService")
        
        val component1 = createTestComponent("InterfaceProvider", "com.test",
            providers = listOf(interfaceProvider)
        )
        val component2 = createTestComponent("ImplProvider", "com.test",
            providers = listOf(implProvider)
        )
        
        val components = listOf(component1, component2)
        val issues = advancedDetector.detectAdvancedSingletonViolations(components)
        
        assertEquals("Should detect violation for interface type", 1, issues.size)
        val issue = issues.first()
        assertTrue("Should mention UserService", issue.message.contains("UserService"))
        assertEquals("Should be ERROR severity", Severity.ERROR, issue.severity)
    }
    
    @Test
    fun testPerformanceWithManySingletons() {
        // Test performance with many singleton providers
        val components = mutableListOf<KnitComponent>()
        val singletonCount = 100
        
        // Create many singleton providers for different types (valid scenario)
        for (i in 0 until singletonCount) {
            val provider = createTestProvider("provideService$i", "Service$i", isSingleton = true)
            val component = createTestComponent("Provider$i", "com.test.perf",
                providers = listOf(provider)
            )
            components.add(component)
        }
        
        // Add a few violations
        val violationProvider1 = createTestProvider("provideCommonService1", "CommonService", 
                                                   isSingleton = true)
        val violationProvider2 = createTestProvider("provideCommonService2", "CommonService", 
                                                   isSingleton = true)
        
        components.add(createTestComponent("ViolationProvider1", "com.test.perf",
            providers = listOf(violationProvider1)
        ))
        components.add(createTestComponent("ViolationProvider2", "com.test.perf",
            providers = listOf(violationProvider2)
        ))
        
        val startTime = System.currentTimeMillis()
        val issues = advancedDetector.detectAdvancedSingletonViolations(components)
        val detectionTime = System.currentTimeMillis() - startTime
        
        // Performance assertion
        assertTrue("Detection should complete quickly (< 500ms)", detectionTime < 500)
        
        // Should only detect the CommonService violation
        assertEquals("Should detect exactly 1 singleton violation", 1, issues.size)
        assertTrue("Should be for CommonService", issues.first().message.contains("CommonService"))
    }
    
    @Test
    fun testSingletonScopeValidation() {
        // Test different singleton scopes (component vs global)
        val componentSingletonProvider = createTestProvider("provideComponentService", "ComponentService",
            isSingleton = true)
        val globalSingletonProvider = createTestProvider("provideGlobalService", "GlobalService",
            isSingleton = true)
        
        val component = createTestComponent("ScopeTestComponent", "com.test.scope",
            providers = listOf(componentSingletonProvider, globalSingletonProvider)
        )
        
        // Dependencies from different scopes
        val componentDep = createTestDependency("componentService", "ComponentService", 
                                              isSingleton = true)
        val globalDep = createTestDependency("globalService", "GlobalService", 
                                           isSingleton = true)
        
        val consumer = createTestComponent("ScopeConsumer", "com.test.consumer",
            dependencies = listOf(componentDep, globalDep)
        )
        
        val components = listOf(component, consumer)
        val issues = advancedDetector.detectAdvancedSingletonViolations(components)
        
        // Should not detect violations for proper scope usage
        assertTrue("Proper scope usage should not trigger violations", issues.isEmpty())
    }
    
    // Helper methods
    
    private fun createTestProvider(
        methodName: String,
        returnType: String,
        isSingleton: Boolean = false,
        providesType: String? = null,
        isNamed: Boolean = false,
        namedQualifier: String? = null
    ): KnitProvider {
        return KnitProvider(
            methodName = methodName,
            returnType = returnType,
            providesType = providesType,
            isNamed = isNamed,
            namedQualifier = namedQualifier,
            isSingleton = isSingleton,
            isIntoSet = false,
            isIntoList = false,
            isIntoMap = false
        )
    }
    
    private fun createTestDependency(
        propertyName: String,
        targetType: String,
        isSingleton: Boolean = false,
        isNamed: Boolean = false,
        namedQualifier: String? = null
    ): KnitDependency {
        return KnitDependency(
            propertyName = propertyName,
            targetType = targetType,
            isNamed = isNamed,
            namedQualifier = namedQualifier,
            isSingleton = isSingleton,
            isFactory = false,
            isLoadable = false
        )
    }
    
    private fun createTestComponent(
        name: String,
        packageName: String,
        dependencies: List<KnitDependency> = emptyList(),
        providers: List<KnitProvider> = emptyList()
    ): KnitComponent {
        return KnitComponent(
            className = name,
            packageName = packageName,
            type = if (providers.isNotEmpty()) ComponentType.PROVIDER else ComponentType.CONSUMER,
            dependencies = dependencies,
            providers = providers,
            sourceFile = "$name.kt"
        )
    }
}