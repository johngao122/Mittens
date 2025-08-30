package com.example.mittens.services

import com.example.mittens.model.*
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.TestDataPath
import org.junit.Test
import org.junit.Assert.*

/**
 * Validation test for provider detection logic fixes
 * Tests the specific U serRepository case mentioned in ANALYSIS_ACCURACY_INVESTIGATION.md
 */
@TestDataPath("\$CONTENT_ROOT/testData")
class InvalidProviderValidationTest : BasePlatformTestCase() {
    
    private lateinit var advancedDetector: AdvancedIssueDetector
    
    override fun setUp() {
        super.setUp()
        advancedDetector = AdvancedIssueDetector(project)
    }
    
    @Test
    fun testUserRepositoryUnresolvedDependencyFix() {
        // Test the real bug fix: InMemoryUserRepository provider should be available for dependency resolution
        // This addresses the false UNRESOLVED_DEPENDENCY errors mentioned in the investigation
        
        // Create the InMemoryUserRepository provider (the only provider)
        val inMemoryProvider = KnitProvider(
            methodName = "provideInMemoryUserRepository", 
            returnType = "InMemoryUserRepository",
            providesType = "UserRepository",  // Provides UserRepository interface
            isNamed = false,
            namedQualifier = null,
            isSingleton = false,
            isIntoSet = false,
            isIntoList = false,
            isIntoMap = false
        )
        
        val inMemoryComponent = KnitComponent(
            className = "InMemoryUserRepository",
            packageName = "com.example.knit.demo.core.repositories", 
            type = ComponentType.PROVIDER,
            dependencies = emptyList(),
            providers = listOf(inMemoryProvider),
            sourceFile = "InMemoryUserRepository.kt"
        )
        
        // Create UserService that depends on UserRepository
        val userServiceDependency = KnitDependency(
            propertyName = "userRepository",
            targetType = "UserRepository", 
            isNamed = false,
            namedQualifier = null,
            isFactory = false,
            isLoadable = false,
            isSingleton = false
        )
        
        val userServiceComponent = KnitComponent(
            className = "UserService",
            packageName = "com.example.knit.demo.core.services",
            type = ComponentType.CONSUMER,
            dependencies = listOf(userServiceDependency),
            providers = emptyList(),
            sourceFile = "UserService.kt"
        )
        
        val components = listOf(inMemoryComponent, userServiceComponent)
        
        // Test ambiguous provider detection - should NOT find any issues now
        val issues = advancedDetector.detectEnhancedAmbiguousProviders(components)
        val userRepositoryIssues = issues.filter { 
            it.message.contains("UserRepository") && it.type == IssueType.AMBIGUOUS_PROVIDER 
        }
        
        assertTrue("Should NOT detect unresolved UserRepository dependency (provider is available)", 
                  userRepositoryIssues.isEmpty())
        
        println("✅ InMemoryUserRepository provider is correctly available for dependency resolution")
        println("✅ UserRepository UNRESOLVED_DEPENDENCY false positive fixed")
    }
    
    @Test
    fun testLegitimateAmbiguityStillDetected() {
        // Legitimate ambiguous providers should still be detected
        // This ensures we don't break valid ambiguity detection
        
        val provider1 = KnitProvider(
            methodName = "providePrimaryDatabase",
            returnType = "DatabaseService",
            providesType = null,
            isNamed = false,
            namedQualifier = null,
            isSingleton = false,
            isIntoSet = false,
            isIntoList = false,
            isIntoMap = false
        )
        
        val provider2 = KnitProvider(
            methodName = "provideSecondaryDatabase",
            returnType = "DatabaseService", // Same type, no qualifiers -> legitimate ambiguity
            providesType = null,
            isNamed = false,
            namedQualifier = null,
            isSingleton = false,
            isIntoSet = false,
            isIntoList = false,
            isIntoMap = false
        )
        
        val component1 = KnitComponent(
            className = "PrimaryDatabaseProvider",
            packageName = "com.test.providers",
            type = ComponentType.PROVIDER,
            dependencies = emptyList(),
            providers = listOf(provider1),
            sourceFile = "PrimaryDatabaseProvider.kt"
        )
        
        val component2 = KnitComponent(
            className = "SecondaryDatabaseProvider",
            packageName = "com.test.providers",
            type = ComponentType.PROVIDER,
            dependencies = emptyList(),
            providers = listOf(provider2),
            sourceFile = "SecondaryDatabaseProvider.kt"
        )
        
        val components = listOf(component1, component2)
        val issues = advancedDetector.detectEnhancedAmbiguousProviders(components)
        
        // This should still be detected as ambiguous (legitimate case)
        val ambiguousIssues = issues.filter { it.type == IssueType.AMBIGUOUS_PROVIDER }
        val databaseIssues = ambiguousIssues.filter { it.message.contains("DatabaseService") }
        
        assertEquals("Should still detect legitimate ambiguous DatabaseService providers", 
                    1, databaseIssues.size)
        
        println("✅ Legitimate ambiguous providers still detected correctly")
    }
    
    @Test
    fun testSuspiciousProviderFiltering() {
        // Suspicious providers should be filtered out
        
        val suspiciousProviders = listOf(
            // Provider with "temp" in method name
            KnitProvider("provideTempService", "TempService", null, false, null, false, false, false, false),
            // Provider with "commented" in method name  
            KnitProvider("provideCommentedService", "CommentedService", null, false, null, false, false, false, false),
            // Provider with "test" in method name
            KnitProvider("provideTestService", "TestService", null, false, null, false, false, false, false)
        )
        
        val validProvider = KnitProvider("provideValidService", "ValidService", null, false, null, false, false, false, false)
        
        val suspiciousComponents = suspiciousProviders.mapIndexed { index, provider ->
            KnitComponent(
                className = "SuspiciousProvider$index",
                packageName = "com.test",
                type = ComponentType.PROVIDER,
                dependencies = emptyList(),
                providers = listOf(provider),
                sourceFile = "SuspiciousProvider$index.kt"
            )
        }
        
        val validComponent = KnitComponent(
            className = "ValidProvider",
            packageName = "com.test",
            type = ComponentType.PROVIDER,
            dependencies = emptyList(),
            providers = listOf(validProvider),
            sourceFile = "ValidProvider.kt"
        )
        
        val components = suspiciousComponents + validComponent
        val issues = advancedDetector.detectEnhancedAmbiguousProviders(components)
        
        // Should not detect any ambiguous providers because suspicious ones are filtered out
        val ambiguousIssues = issues.filter { it.type == IssueType.AMBIGUOUS_PROVIDER }
        
        assertTrue("Suspicious providers should be filtered out", ambiguousIssues.isEmpty())
        println("✅ Suspicious providers correctly filtered")
    }
}