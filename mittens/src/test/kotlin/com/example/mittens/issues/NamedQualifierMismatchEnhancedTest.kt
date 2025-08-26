package com.example.mittens.issues

import com.example.mittens.model.*
import com.example.mittens.services.AdvancedIssueDetector
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

/**
 * Enhanced tests for named qualifier mismatch detection with fuzzy matching
 * Tests typo detection, intelligent suggestions, and qualifier validation
 */
class NamedQualifierMismatchEnhancedTest : BasePlatformTestCase() {
    
    private lateinit var advancedDetector: AdvancedIssueDetector
    
    override fun setUp() {
        super.setUp()
        advancedDetector = AdvancedIssueDetector(project)
    }
    
    @Test
    fun testBasicQualifierMismatch() {
        // Consumer asks for "primary" but provider offers "secondary"
        val dependency = createTestDependency("primaryDb", "DatabaseService",
            isNamed = true, namedQualifier = "primary")
        val consumer = createTestComponent("UserService", "com.test",
            dependencies = listOf(dependency)
        )
        
        val provider = createTestProvider("provideSecondaryDb", "DatabaseService",
            isNamed = true, namedQualifier = "secondary")
        val providerComponent = createTestComponent("DbProvider", "com.test",
            providers = listOf(provider)
        )
        
        val components = listOf(consumer, providerComponent)
        val issues = advancedDetector.detectEnhancedNamedQualifierMismatches(components)
        
        assertEquals("Should detect 1 qualifier mismatch", 1, issues.size)
        val issue = issues.first()
        assertEquals(IssueType.NAMED_QUALIFIER_MISMATCH, issue.type)
        assertEquals(Severity.ERROR, issue.severity)
        assertTrue("Message should mention @Named(primary)", issue.message.contains("@Named(primary)"))
        assertTrue("Message should mention DatabaseService", issue.message.contains("DatabaseService"))
        assertTrue("Suggested fix should mention available qualifiers", 
                  issue.suggestedFix?.contains("secondary") ?: false)
    }
    
    @Test
    fun testFuzzyMatchingForTypos() {
        // Consumer asks for "primery" (typo) but provider offers "primary"
        val dependency = createTestDependency("primaryDb", "DatabaseService",
            isNamed = true, namedQualifier = "primery") // typo
        val consumer = createTestComponent("UserService", "com.test",
            dependencies = listOf(dependency)
        )
        
        val provider = createTestProvider("providePrimaryDb", "DatabaseService",
            isNamed = true, namedQualifier = "primary") // correct
        val providerComponent = createTestComponent("DbProvider", "com.test",
            providers = listOf(provider)
        )
        
        val components = listOf(consumer, providerComponent)
        val issues = advancedDetector.detectEnhancedNamedQualifierMismatches(components)
        
        assertEquals("Should detect 1 qualifier mismatch", 1, issues.size)
        val issue = issues.first()
        assertTrue("Should suggest 'primary' for 'primery'", 
                  issue.suggestedFix?.contains("Did you mean: 'primary'") ?: false)
        
        val suggestions = issue.metadata["suggestions"] as? List<*>
        assertNotNull("Should have suggestions", suggestions)
        assertTrue("Should contain 'primary' as suggestion", 
                  suggestions?.contains("primary") ?: false)
    }
    
    @Test
    fun testMultipleSimilarQualifiers() {
        // Test multiple similar qualifiers and fuzzy matching
        val dependency = createTestDependency("testDb", "DatabaseService",
            isNamed = true, namedQualifier = "teste") // typo
        val consumer = createTestComponent("TestService", "com.test",
            dependencies = listOf(dependency)
        )
        
        // Multiple similar providers
        val provider1 = createTestProvider("provideTestDb", "DatabaseService",
            isNamed = true, namedQualifier = "test")
        val provider2 = createTestProvider("provideTempDb", "DatabaseService", 
            isNamed = true, namedQualifier = "temp")
        val provider3 = createTestProvider("provideDevDb", "DatabaseService",
            isNamed = true, namedQualifier = "dev")
        
        val providerComponent = createTestComponent("DbProviders", "com.test",
            providers = listOf(provider1, provider2, provider3)
        )
        
        val components = listOf(consumer, providerComponent)
        val issues = advancedDetector.detectEnhancedNamedQualifierMismatches(components)
        
        assertEquals("Should detect 1 qualifier mismatch", 1, issues.size)
        val issue = issues.first()
        
        // Should suggest the closest match first
        val suggestedFix = issue.suggestedFix ?: ""
        assertTrue("Should suggest 'test' as closest match", suggestedFix.contains("'test'"))
        assertTrue("Should list all available qualifiers", suggestedFix.contains("'temp'"))
        assertTrue("Should list all available qualifiers", suggestedFix.contains("'dev'"))
        
        val availableQualifiers = issue.metadata["availableQualifiers"] as? List<*>
        assertEquals("Should have 3 available qualifiers", 3, availableQualifiers?.size)
    }
    
    @Test
    fun testNoMismatchWithCorrectQualifier() {
        // Correct qualifier - should not report mismatch
        val dependency = createTestDependency("primaryDb", "DatabaseService",
            isNamed = true, namedQualifier = "primary")
        val consumer = createTestComponent("UserService", "com.test",
            dependencies = listOf(dependency)
        )
        
        val provider = createTestProvider("providePrimaryDb", "DatabaseService",
            isNamed = true, namedQualifier = "primary")
        val providerComponent = createTestComponent("DbProvider", "com.test",
            providers = listOf(provider)
        )
        
        val components = listOf(consumer, providerComponent)
        val issues = advancedDetector.detectEnhancedNamedQualifierMismatches(components)
        
        assertTrue("Should not detect any mismatches", issues.isEmpty())
    }
    
    @Test
    fun testMultipleTypesWithQualifierMismatches() {
        // Multiple types with different qualifier mismatches
        val dbDep = createTestDependency("primaryDb", "DatabaseService",
            isNamed = true, namedQualifier = "primary")
        val cacheDep = createTestDependency("fastCache", "CacheService",
            isNamed = true, namedQualifier = "fast")
        val consumer = createTestComponent("BusinessService", "com.test",
            dependencies = listOf(dbDep, cacheDep)
        )
        
        // Provide different qualifiers
        val dbProvider = createTestProvider("provideSecondaryDb", "DatabaseService",
            isNamed = true, namedQualifier = "secondary")
        val cacheProvider = createTestProvider("provideSlowCache", "CacheService",
            isNamed = true, namedQualifier = "slow")
        
        val providerComponent = createTestComponent("Providers", "com.test",
            providers = listOf(dbProvider, cacheProvider)
        )
        
        val components = listOf(consumer, providerComponent)
        val issues = advancedDetector.detectEnhancedNamedQualifierMismatches(components)
        
        assertEquals("Should detect 2 qualifier mismatches", 2, issues.size)
        
        val dbIssue = issues.find { it.message.contains("DatabaseService") }
        val cacheIssue = issues.find { it.message.contains("CacheService") }
        
        assertNotNull("Should have DatabaseService mismatch", dbIssue)
        assertNotNull("Should have CacheService mismatch", cacheIssue)
        
        assertTrue("DB issue should mention 'primary'", dbIssue?.message?.contains("primary") ?: false)
        assertTrue("Cache issue should mention 'fast'", cacheIssue?.message?.contains("fast") ?: false)
    }
    
    @Test
    fun testQualifierMatchingWithCaseSensitivity() {
        // Test case sensitivity in qualifiers
        val dependency = createTestDependency("primaryDb", "DatabaseService",
            isNamed = true, namedQualifier = "Primary") // Capital P
        val consumer = createTestComponent("UserService", "com.test",
            dependencies = listOf(dependency)
        )
        
        val provider = createTestProvider("providePrimaryDb", "DatabaseService",
            isNamed = true, namedQualifier = "primary") // lowercase p
        val providerComponent = createTestComponent("DbProvider", "com.test",
            providers = listOf(provider)
        )
        
        val components = listOf(consumer, providerComponent)
        val issues = advancedDetector.detectEnhancedNamedQualifierMismatches(components)
        
        assertEquals("Should detect case mismatch", 1, issues.size)
        val issue = issues.first()
        assertTrue("Should suggest lowercase version", 
                  issue.suggestedFix?.contains("'primary'") ?: false)
    }
    
    @Test
    fun testEmptyOrNullQualifierHandling() {
        // Test edge cases with empty or null qualifiers
        val dependency = createTestDependency("service", "TestService",
            isNamed = true, namedQualifier = "") // Empty qualifier
        val consumer = createTestComponent("Consumer", "com.test",
            dependencies = listOf(dependency)
        )
        
        val provider = createTestProvider("provideService", "TestService",
            isNamed = true, namedQualifier = "test")
        val providerComponent = createTestComponent("Provider", "com.test",
            providers = listOf(provider)
        )
        
        val components = listOf(consumer, providerComponent)
        val issues = advancedDetector.detectEnhancedNamedQualifierMismatches(components)
        
        // Should handle gracefully without crashing
        assertEquals("Should detect mismatch for empty qualifier", 1, issues.size)
        val issue = issues.first()
        assertNotNull("Should have valid message", issue.message)
        assertTrue("Should mention available qualifiers", 
                  issue.suggestedFix?.contains("test") ?: false)
    }
    
    @Test
    fun testQualifierSuggestionsRanking() {
        // Test that suggestions are ranked by similarity
        val dependency = createTestDependency("service", "TestService",
            isNamed = true, namedQualifier = "produktion") // typo for "production"
        val consumer = createTestComponent("Consumer", "com.test",
            dependencies = listOf(dependency)
        )
        
        val provider1 = createTestProvider("provideDevService", "TestService",
            isNamed = true, namedQualifier = "development")
        val provider2 = createTestProvider("provideProdService", "TestService",
            isNamed = true, namedQualifier = "production") // closest match
        val provider3 = createTestProvider("provideTestService", "TestService",
            isNamed = true, namedQualifier = "testing")
        
        val providerComponent = createTestComponent("Providers", "com.test",
            providers = listOf(provider1, provider2, provider3)
        )
        
        val components = listOf(consumer, providerComponent)
        val issues = advancedDetector.detectEnhancedNamedQualifierMismatches(components)
        
        assertEquals("Should detect 1 qualifier mismatch", 1, issues.size)
        val issue = issues.first()
        
        val suggestions = issue.metadata["suggestions"] as? List<*>
        assertNotNull("Should have suggestions", suggestions)
        
        // "production" should be the first suggestion as it's closest to "produktion"
        if (suggestions?.isNotEmpty() == true) {
            assertEquals("First suggestion should be 'production'", "production", suggestions[0])
        }
    }
    
    @Test
    fun testQualifierMismatchWithInheritance() {
        // Test qualifier mismatches with interface inheritance
        val dependency = createTestDependency("userRepo", "UserRepository",
            isNamed = true, namedQualifier = "database")
        val consumer = createTestComponent("UserService", "com.test",
            dependencies = listOf(dependency)
        )
        
        // Provider returns implementation but provides interface with different qualifier
        val provider = createTestProvider("provideInMemoryUserRepo", "InMemoryUserRepository",
            providesType = "UserRepository", isNamed = true, namedQualifier = "memory")
        val providerComponent = createTestComponent("RepoProvider", "com.test",
            providers = listOf(provider)
        )
        
        val components = listOf(consumer, providerComponent)
        val issues = advancedDetector.detectEnhancedNamedQualifierMismatches(components)
        
        assertEquals("Should detect qualifier mismatch for interface", 1, issues.size)
        val issue = issues.first()
        assertTrue("Should mention UserRepository", issue.message.contains("UserRepository"))
        assertTrue("Should mention 'database' qualifier", issue.message.contains("database"))
        assertTrue("Should suggest 'memory' qualifier", 
                  issue.suggestedFix?.contains("memory") ?: false)
    }
    
    @Test
    fun testPerformanceWithManyQualifiers() {
        // Test performance with many qualifiers
        val components = mutableListOf<KnitComponent>()
        val qualifierCount = 50
        
        // Create component with dependency using non-existent qualifier
        val dependency = createTestDependency("service", "TestService",
            isNamed = true, namedQualifier = "nonexistent")
        val consumer = createTestComponent("Consumer", "com.test.perf",
            dependencies = listOf(dependency)
        )
        components.add(consumer)
        
        // Create many providers with different qualifiers
        val providers = (0 until qualifierCount).map { index ->
            createTestProvider("provideService$index", "TestService",
                isNamed = true, namedQualifier = "qualifier$index")
        }
        
        val providerComponent = createTestComponent("BigProvider", "com.test.perf",
            providers = providers
        )
        components.add(providerComponent)
        
        val startTime = System.currentTimeMillis()
        val issues = advancedDetector.detectEnhancedNamedQualifierMismatches(components)
        val detectionTime = System.currentTimeMillis() - startTime
        
        // Performance assertion
        assertTrue("Detection should complete quickly (< 300ms)", detectionTime < 300)
        
        // Should detect the mismatch
        assertEquals("Should detect 1 qualifier mismatch", 1, issues.size)
        val issue = issues.first()
        
        val availableQualifiers = issue.metadata["availableQualifiers"] as? List<*>
        assertEquals("Should have all $qualifierCount available qualifiers", 
                    qualifierCount, availableQualifiers?.size)
    }
    
    @Test
    fun testComplexQualifierScenario() {
        // Complex scenario with multiple consumers and providers
        val primaryDbDep = createTestDependency("primaryDb", "DatabaseService",
            isNamed = true, namedQualifier = "primary")
        val backupDbDep = createTestDependency("backupDb", "DatabaseService", 
            isNamed = true, namedQualifier = "backup")
        val logDbDep = createTestDependency("logDb", "DatabaseService",
            isNamed = true, namedQualifier = "logging") // not available
        
        val consumer = createTestComponent("ComplexService", "com.test",
            dependencies = listOf(primaryDbDep, backupDbDep, logDbDep)
        )
        
        // Provide only some qualifiers
        val primaryProvider = createTestProvider("providePrimaryDb", "DatabaseService",
            isNamed = true, namedQualifier = "primary")
        val secondaryProvider = createTestProvider("provideSecondaryDb", "DatabaseService",
            isNamed = true, namedQualifier = "secondary") // different from backup
        val auditProvider = createTestProvider("provideAuditDb", "DatabaseService",
            isNamed = true, namedQualifier = "audit") // different from logging
        
        val providerComponent = createTestComponent("DbProviders", "com.test",
            providers = listOf(primaryProvider, secondaryProvider, auditProvider)
        )
        
        val components = listOf(consumer, providerComponent)
        val issues = advancedDetector.detectEnhancedNamedQualifierMismatches(components)
        
        // Should detect 2 mismatches: backup -> secondary, logging -> audit
        assertEquals("Should detect 2 qualifier mismatches", 2, issues.size)
        
        val backupIssue = issues.find { it.message.contains("backup") }
        val loggingIssue = issues.find { it.message.contains("logging") }
        
        assertNotNull("Should have backup mismatch", backupIssue)
        assertNotNull("Should have logging mismatch", loggingIssue)
        
        // Check that each has appropriate suggestions
        assertTrue("Should suggest 'secondary' for backup", 
                  backupIssue?.suggestedFix?.contains("secondary") ?: false)
        assertTrue("Should suggest 'audit' for logging", 
                  loggingIssue?.suggestedFix?.contains("audit") ?: false)
    }
    
    // Helper methods
    
    private fun createTestDependency(
        propertyName: String,
        targetType: String,
        isNamed: Boolean = false,
        namedQualifier: String? = null
    ): KnitDependency {
        return KnitDependency(
            propertyName = propertyName,
            targetType = targetType,
            isNamed = isNamed,
            namedQualifier = namedQualifier,
            isSingleton = false,
            isFactory = false,
            isLoadable = false
        )
    }
    
    private fun createTestProvider(
        methodName: String,
        returnType: String,
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
            isSingleton = false,
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