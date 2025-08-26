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
    fun testInvalidProviderValidation() {
        // InMemoryUserRepository provider should be filtered out
        // This addresses the false "ambiguous provider" error mentioned in the investigation
        
        // Create the DatabaseUserRepository (active provider)
        val databaseProvider = KnitProvider(
            methodName = "provideDatabaseUserRepository",
            returnType = "DatabaseUserRepository", 
            providesType = "UserRepository",  // Provides UserRepository interface
            isNamed = false,
            namedQualifier = null,
            isSingleton = false,
            isIntoSet = false,
            isIntoList = false,
            isIntoMap = false
        )
        
        val databaseComponent = KnitComponent(
            className = "DatabaseUserRepository",
            packageName = "com.example.knit.demo.core.repositories",
            type = ComponentType.PROVIDER,
            dependencies = emptyList(),
            providers = listOf(databaseProvider),
            sourceFile = "DatabaseUserRepository.kt"
        )
        
        // Create the InMemoryUserRepository (should be filtered as suspicious)
        val inMemoryProvider = KnitProvider(
            methodName = "provideInMemoryUserRepository",
            returnType = "InMemoryUserRepository",
            providesType = "UserRepository",  // Also provides UserRepository - this should be filtered!
            isNamed = false,
            namedQualifier = null,
            isSingleton = false,
            isIntoSet = false,
            isIntoList = false,
            isIntoMap = false
        )
        
        val inMemoryComponent = KnitComponent(
            className = "InMemoryUserRepository", // This matches our suspicious pattern check
            packageName = "com.example.knit.demo.core.repositories",
            type = ComponentType.PROVIDER,
            dependencies = emptyList(),
            providers = listOf(inMemoryProvider),
            sourceFile = "InMemoryUserRepository.kt"
        )
        
        val components = listOf(databaseComponent, inMemoryComponent)
        
        // Before Phase 2: This would detect 2 providers for UserRepository -> ambiguous
        // After Phase 2: InMemoryUserRepository should be filtered out -> no ambiguity
        val issues = advancedDetector.detectEnhancedAmbiguousProviders(components)
        
        // Validate Phase 2 fix
        val ambiguousProviderIssues = issues.filter { it.type == IssueType.AMBIGUOUS_PROVIDER }
        val userRepositoryIssues = ambiguousProviderIssues.filter { 
            it.message.contains("UserRepository") 
        }
        
        assertTrue("Should not detect ambiguous UserRepository providers (InMemoryUserRepository filtered)", 
                  userRepositoryIssues.isEmpty())
        
        println("✅ InMemoryUserRepository correctly filtered - no false ambiguous provider error")
        println("✅ UserRepository ambiguity false positive eliminated")
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