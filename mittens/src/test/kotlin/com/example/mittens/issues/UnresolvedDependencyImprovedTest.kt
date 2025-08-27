package com.example.mittens.issues

import com.example.mittens.model.*
import com.example.mittens.services.AdvancedIssueDetector
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

/**
 * Improved tests for unresolved dependency detection with generic type support
 * Tests better type matching, inheritance chains, and enhanced provider lookup
 */
class UnresolvedDependencyImprovedTest : BasePlatformTestCase() {
    
    private lateinit var advancedDetector: AdvancedIssueDetector
    
    override fun setUp() {
        super.setUp()
        advancedDetector = AdvancedIssueDetector(project)
    }
    
    @Test
    fun testBasicUnresolvedDependency() {
        // Component requiring UserService but no provider available
        val dependency = createTestDependency("userService", "UserService")
        val consumer = createTestComponent("UserController", "com.test.controller",
            dependencies = listOf(dependency)
        )
        
        val components = listOf(consumer)
        val issues = advancedDetector.detectImprovedUnresolvedDependencies(components)
        
        assertEquals("Should detect 1 unresolved dependency", 1, issues.size)
        val issue = issues.first()
        assertEquals(IssueType.UNRESOLVED_DEPENDENCY, issue.type)
        assertEquals(Severity.ERROR, issue.severity)
        assertTrue("Message should mention UserService", issue.message.contains("UserService"))
        assertTrue("Suggested fix should mention creating provider", 
                  issue.suggestedFix?.contains("@Provides") ?: false)
        
        // Check metadata
        assertNotNull("Should have metadata", issue.metadata)
        assertEquals("Should have target type", "UserService", issue.metadata["targetType"])
        assertFalse("Should not be named", issue.metadata["isNamed"] as Boolean)
    }
    
    @Test
    fun testUnresolvedNamedDependency() {
        // Component requiring named dependency but no matching provider
        val dependency = createTestDependency("primaryDb", "DatabaseService",
            isNamed = true, namedQualifier = "primary")
        val consumer = createTestComponent("UserService", "com.test.service",
            dependencies = listOf(dependency)
        )
        
        // Provide a non-named provider (shouldn't match)
        val provider = createTestProvider("provideDatabase", "DatabaseService")
        val providerComponent = createTestComponent("DatabaseProvider", "com.test.provider",
            providers = listOf(provider)
        )
        
        val components = listOf(consumer, providerComponent)
        val issues = advancedDetector.detectImprovedUnresolvedDependencies(components)
        
        assertEquals("Should detect 1 unresolved dependency", 1, issues.size)
        val issue = issues.first()
        assertTrue("Message should mention @Named qualifier", issue.message.contains("@Named(primary)"))
        assertTrue("Suggested fix should mention creating @Named provider",
                  issue.suggestedFix?.contains("@Named(primary)") ?: false)
        
        assertEquals("Should have named qualifier", "primary", issue.metadata["namedQualifier"])
        assertTrue("Should be named", issue.metadata["isNamed"] as Boolean)
    }
    
    @Test
    fun testResolvedDependencyNoIssue() {
        // Component with dependency that has matching provider
        val dependency = createTestDependency("userService", "UserService")
        val consumer = createTestComponent("UserController", "com.test.controller",
            dependencies = listOf(dependency)
        )
        
        val provider = createTestProvider("provideUserService", "UserService")
        val providerComponent = createTestComponent("UserServiceProvider", "com.test.provider",
            providers = listOf(provider)
        )
        
        val components = listOf(consumer, providerComponent)
        val issues = advancedDetector.detectImprovedUnresolvedDependencies(components)
        
        assertTrue("Should not detect any unresolved dependencies", issues.isEmpty())
    }
    
    @Test
    fun testResolvedNamedDependency() {
        // Component with named dependency that has matching named provider
        val dependency = createTestDependency("primaryDb", "DatabaseService",
            isNamed = true, namedQualifier = "primary")
        val consumer = createTestComponent("UserService", "com.test.service",
            dependencies = listOf(dependency)
        )
        
        val provider = createTestProvider("providePrimaryDatabase", "DatabaseService",
            isNamed = true, namedQualifier = "primary")
        val providerComponent = createTestComponent("DatabaseProvider", "com.test.provider",
            providers = listOf(provider)
        )
        
        val components = listOf(consumer, providerComponent)
        val issues = advancedDetector.detectImprovedUnresolvedDependencies(components)
        
        assertTrue("Should not detect any unresolved dependencies", issues.isEmpty())
    }
    
    @Test
    fun testGenericTypeDependency() {
        // Test generic type matching
        val dependency = createTestDependency("userList", "List<User>")
        val consumer = createTestComponent("UserProcessor", "com.test.processor",
            dependencies = listOf(dependency)
        )
        
        val components = listOf(consumer)
        val issues = advancedDetector.detectImprovedUnresolvedDependencies(components)
        
        assertEquals("Should detect unresolved generic dependency", 1, issues.size)
        val issue = issues.first()
        assertTrue("Message should mention List<User>", issue.message.contains("List<User>"))
        assertTrue("Suggested fix should handle generic types",
                  issue.suggestedFix?.contains("List<User>") ?: false)
    }
    
    @Test
    fun testGenericTypeMatching() {
        // Test that generic types can match with compatible providers
        val dependency = createTestDependency("userList", "List<User>")
        val consumer = createTestComponent("UserProcessor", "com.test.processor",
            dependencies = listOf(dependency)
        )
        
        val provider = createTestProvider("provideUserList", "List<User>")
        val providerComponent = createTestComponent("UserListProvider", "com.test.provider",
            providers = listOf(provider)
        )
        
        val components = listOf(consumer, providerComponent)
        val issues = advancedDetector.detectImprovedUnresolvedDependencies(components)
        
        assertTrue("Generic types should match", issues.isEmpty())
    }
    
    @Test
    fun testFactoryDependencies() {
        // Test Factory<T> dependencies
        val factoryDependency = createTestDependency("userFactory", "Factory<User>",
            isFactory = true)
        val functionDependency = createTestDependency("userCreator", "() -> User",
            isFactory = true)
        
        val consumer = createTestComponent("UserManager", "com.test.manager",
            dependencies = listOf(factoryDependency, functionDependency)
        )
        
        val components = listOf(consumer)
        val issues = advancedDetector.detectImprovedUnresolvedDependencies(components)
        
        assertEquals("Should detect 2 unresolved factory dependencies", 2, issues.size)
        
        val factoryIssue = issues.find { it.message.contains("Factory<User>") }
        val functionIssue = issues.find { it.message.contains("() -> User") }
        
        assertNotNull("Should detect Factory<User> dependency", factoryIssue)
        assertNotNull("Should detect () -> User dependency", functionIssue)
    }
    
    @Test
    fun testLoadableDependencies() {
        // Test Loadable<T> dependencies
        val loadableDependency = createTestDependency("userLoader", "Loadable<User>",
            isLoadable = true)
        val consumer = createTestComponent("UserService", "com.test.service",
            dependencies = listOf(loadableDependency)
        )
        
        val components = listOf(consumer)
        val issues = advancedDetector.detectImprovedUnresolvedDependencies(components)
        
        assertEquals("Should detect 1 unresolved loadable dependency", 1, issues.size)
        val issue = issues.first()
        assertTrue("Message should mention Loadable<User>", issue.message.contains("Loadable<User>"))
        assertTrue("Suggested fix should handle Loadable types",
                  issue.suggestedFix?.contains("Loadable") ?: false)
    }
    
    @Test
    fun testInterfaceProviders() {
        // Test interface vs implementation matching
        val dependency = createTestDependency("userRepository", "UserRepository")
        val consumer = createTestComponent("UserService", "com.test.service",
            dependencies = listOf(dependency)
        )
        
        // Provider returns implementation but provides interface
        val provider = createTestProvider("provideUserRepo", "UserRepositoryImpl",
            providesType = "UserRepository")
        val providerComponent = createTestComponent("UserRepoProvider", "com.test.provider",
            providers = listOf(provider)
        )
        
        val components = listOf(consumer, providerComponent)
        val issues = advancedDetector.detectImprovedUnresolvedDependencies(components)
        
        assertTrue("Interface should be satisfied by implementation provider", issues.isEmpty())
    }
    
    @Test
    fun testInheritanceChainMatching() {
        // Test inheritance chain matching - this is a simplified test
        // In real scenarios, we'd need IDE type system integration
        val dependency = createTestDependency("service", "BaseService")
        val consumer = createTestComponent("Consumer", "com.test",
            dependencies = listOf(dependency)
        )
        
        // Provider returns derived class
        val provider = createTestProvider("provideDerivedService", "DerivedService",
            providesType = "BaseService") // Explicitly provides base type
        val providerComponent = createTestComponent("ServiceProvider", "com.test.provider",
            providers = listOf(provider)
        )
        
        val components = listOf(consumer, providerComponent)
        val issues = advancedDetector.detectImprovedUnresolvedDependencies(components)
        
        assertTrue("Inheritance should work with explicit providesType", issues.isEmpty())
    }
    
    @Test
    fun testSingletonDependencies() {
        // Test singleton dependencies
        val singletonDependency = createTestDependency("database", "DatabaseService",
            isSingleton = true)
        val consumer = createTestComponent("UserService", "com.test.service",
            dependencies = listOf(singletonDependency)
        )
        
        val components = listOf(consumer)
        val issues = advancedDetector.detectImprovedUnresolvedDependencies(components)
        
        assertEquals("Should detect unresolved singleton dependency", 1, issues.size)
        val issue = issues.first()
        assertTrue("Message should mention DatabaseService", issue.message.contains("DatabaseService"))
        assertTrue("Suggested fix should mention creating provider",
                  issue.suggestedFix?.contains("@Provides") ?: false)
    }
    
    @Test
    fun testMultipleUnresolvedDependencies() {
        // Component with multiple unresolved dependencies
        val userServiceDep = createTestDependency("userService", "UserService")
        val emailServiceDep = createTestDependency("emailService", "EmailService")
        val cacheServiceDep = createTestDependency("cacheService", "CacheService")
        
        val consumer = createTestComponent("BusinessService", "com.test.service",
            dependencies = listOf(userServiceDep, emailServiceDep, cacheServiceDep)
        )
        
        val components = listOf(consumer)
        val issues = advancedDetector.detectImprovedUnresolvedDependencies(components)
        
        assertEquals("Should detect 3 unresolved dependencies", 3, issues.size)
        
        val serviceTypes = issues.map { issue ->
            issue.metadata["targetType"] as String
        }.toSet()
        
        assertTrue("Should detect UserService", serviceTypes.contains("UserService"))
        assertTrue("Should detect EmailService", serviceTypes.contains("EmailService"))
        assertTrue("Should detect CacheService", serviceTypes.contains("CacheService"))
    }
    
    @Test
    fun testPartiallyResolvedDependencies() {
        // Some dependencies resolved, others not
        val userServiceDep = createTestDependency("userService", "UserService")
        val emailServiceDep = createTestDependency("emailService", "EmailService")
        
        val consumer = createTestComponent("BusinessService", "com.test.service",
            dependencies = listOf(userServiceDep, emailServiceDep)
        )
        
        // Only provide UserService
        val userProvider = createTestProvider("provideUserService", "UserService")
        val userProviderComponent = createTestComponent("UserProvider", "com.test.provider",
            providers = listOf(userProvider)
        )
        
        val components = listOf(consumer, userProviderComponent)
        val issues = advancedDetector.detectImprovedUnresolvedDependencies(components)
        
        assertEquals("Should detect 1 unresolved dependency", 1, issues.size)
        val issue = issues.first()
        assertEquals("Should be EmailService", "EmailService", issue.metadata["targetType"])
    }
    
    @Test
    fun testSuggestionQualityForSimilarTypes() {
        // Test intelligent suggestions when similar types are available
        val dependency = createTestDependency("userService", "UserService")
        val consumer = createTestComponent("UserController", "com.test.controller",
            dependencies = listOf(dependency)
        )
        
        // Provide similar but not exact types
        val userRepoProvider = createTestProvider("provideUserRepo", "UserRepository")
        val userManagerProvider = createTestProvider("provideUserManager", "UserManager") 
        val userValidatorProvider = createTestProvider("provideUserValidator", "UserValidator")
        
        val providerComponent = createTestComponent("UserProviders", "com.test.provider",
            providers = listOf(userRepoProvider, userManagerProvider, userValidatorProvider)
        )
        
        val components = listOf(consumer, providerComponent)
        val issues = advancedDetector.detectImprovedUnresolvedDependencies(components)
        
        assertEquals("Should detect 1 unresolved dependency", 1, issues.size)
        val issue = issues.first()
        
        // The suggestion should be intelligent and mention available similar types
        val suggestedFix = issue.suggestedFix ?: ""
        assertTrue("Should have comprehensive suggestions", suggestedFix.length > 50)
        assertTrue("Should mention creating @Provides", suggestedFix.contains("@Provides"))
    }
    
    @Test
    fun testPerformanceWithManyDependencies() {
        // Test performance with many dependencies
        val components = mutableListOf<KnitComponent>()
        val dependencyCount = 50
        
        // Create component with many unresolved dependencies
        val dependencies = (0 until dependencyCount).map { index ->
            createTestDependency("service$index", "Service$index")
        }
        
        val consumer = createTestComponent("BigConsumer", "com.test.perf",
            dependencies = dependencies
        )
        components.add(consumer)
        
        // Add some providers (but not all)
        val providerCount = 20
        val providers = (0 until providerCount).map { index ->
            createTestProvider("provideService$index", "Service$index")
        }
        
        val providerComponent = createTestComponent("PartialProvider", "com.test.perf",
            providers = providers
        )
        components.add(providerComponent)
        
        val startTime = System.currentTimeMillis()
        val issues = advancedDetector.detectImprovedUnresolvedDependencies(components)
        val detectionTime = System.currentTimeMillis() - startTime
        
        // Performance assertion
        assertTrue("Detection should complete quickly (< 500ms)", detectionTime < 500)
        
        // Should detect unresolved dependencies (total - resolved)
        val unresolvedCount = dependencyCount - providerCount
        assertEquals("Should detect $unresolvedCount unresolved dependencies", 
                    unresolvedCount, issues.size)
    }
    
    // Helper methods
    
    private fun createTestDependency(
        propertyName: String,
        targetType: String,
        isNamed: Boolean = false,
        namedQualifier: String? = null,
        isSingleton: Boolean = false,
        isFactory: Boolean = false,
        isLoadable: Boolean = false
    ): KnitDependency {
        return KnitDependency(
            propertyName = propertyName,
            targetType = targetType,
            isNamed = isNamed,
            namedQualifier = namedQualifier,
            isSingleton = isSingleton,
            isFactory = isFactory,
            isLoadable = isLoadable
        )
    }
    
    private fun createTestProvider(
        methodName: String,
        returnType: String,
        providesType: String? = null,
        isNamed: Boolean = false,
        namedQualifier: String? = null,
        isSingleton: Boolean = false
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