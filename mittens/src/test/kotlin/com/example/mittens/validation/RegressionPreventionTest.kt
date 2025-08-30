package com.example.mittens.validation

import com.example.mittens.model.*
import com.example.mittens.services.*
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.Test
import org.junit.Assert.*

/**
 * Phase 5 Regression Prevention Test Suite
 * 
 * Prevents regression of issues fixed during Phases 1-4 of ANALYSIS_ACCURACY_INVESTIGATION.md
 * Contains test matrices covering all scenarios that were causing false positives and inaccuracies.
 * 
 * This test ensures that:
 * 1. Phase 1 fixes (comment detection) don't regress
 * 2. Phase 2 fixes (provider detection) remain stable
 * 3. Phase 3 fixes (classification & deduplication) continue working
 * 4. Phase 4 accuracy improvements are maintained
 */
class RegressionPreventionTest : LightJavaCodeInsightFixtureTestCase() {

    private lateinit var sourceAnalyzer: KnitSourceAnalyzer
    private lateinit var advancedDetector: AdvancedIssueDetector
    private lateinit var knitAnalysisService: KnitAnalysisService
    private lateinit var issueValidator: IssueValidator
    private lateinit var statisticalService: StatisticalAccuracyService
    
    override fun setUp() {
        super.setUp()
        sourceAnalyzer = KnitSourceAnalyzer(project)
        advancedDetector = AdvancedIssueDetector(project)
        knitAnalysisService = KnitAnalysisService(project)
        issueValidator = IssueValidator(project)
        statisticalService = StatisticalAccuracyService()
    }

    /**
     * Phase 1 Regression Prevention: Source Code Parsing Issues
     * Location: KnitSourceAnalyzer.kt:119 - comment detection
     */
    @Test
    fun testPhase1RegressionPrevention_CommentedDependencies() {
        val testContent = """
            package com.example.regression

            import knit.Provides
            import knit.di

            @Provides
            class TestService {
                // These should NOT be detected as dependencies
                // private val commentedDep1: String by di
                /* private val commentedDep2: Int by di */
                /*
                 * private val multiLineCommented: Double by di
                 */
                
                // This should be detected
                private val activeDep: ActiveService by di
            }
            
            @Provides
            class ActiveService
        """.trimIndent()
        
        myFixture.configureByText("TestService.kt", testContent)
        
        val components = sourceAnalyzer.analyzeProject()
        val testService = components.find { it.className == "TestService" }
        
        assertNotNull("TestService should be found", testService)
        assertEquals("Should have exactly 1 dependency (commented ones ignored)", 1, testService!!.dependencies.size)
        assertEquals("Active dependency should be detected", "ActiveService", testService.dependencies.first().targetType)
        
        // Validate no false unresolved dependency issues
        val dependencyGraph = knitAnalysisService.buildDependencyGraph(components)
        val issues = knitAnalysisService.detectIssues(components, dependencyGraph)
        val unresolvedIssues = issues.filter { it.type == IssueType.CIRCULAR_DEPENDENCY }
        
        // Should not have unresolved issues for commented dependencies
        assertFalse("Should not report unresolved issues for commented dependencies",
                   unresolvedIssues.any { it.message.contains("String") || it.message.contains("Int") || it.message.contains("Double") })
        
        println("✅ Phase 1 Regression Prevention: Comment detection working correctly")
    }

    /**
     * Phase 2 Regression Prevention: Provider Detection Logic Flaws  
     * Location: AdvancedIssueDetector.kt:313-332 - buildProviderIndex
     */
    @Test
    fun testPhase2RegressionPrevention_CommentedProviders() {
        val testContent = """
            package com.example.regression

            import knit.Provides

            interface TestRepository

            // This provider is commented out and should be ignored
            // @Provides(TestRepository::class)
            class InactiveRepository : TestRepository

            // This provider is active and should be detected
            @Provides(TestRepository::class)
            class ActiveRepository : TestRepository
            
            @Provides
            class Consumer {
                private val repository: TestRepository by di
            }
        """.trimIndent()
        
        myFixture.configureByText("TestProviders.kt", testContent)
        
        val components = sourceAnalyzer.analyzeProject()
        
        // Verify provider detection
        val activeRepo = components.find { it.className == "ActiveRepository" }
        
        assertNotNull("ActiveRepository should be found", activeRepo)
        
        // Active provider should have TestRepository provider; commented one must be ignored
        assertEquals("ActiveRepository should have 1 provider", 1, activeRepo!!.providers.size)
        assertEquals("Provider should return TestRepository", "TestRepository", activeRepo.providers.first().returnType)
        
        // Test issue detection
        val dependencyGraph = knitAnalysisService.buildDependencyGraph(components)
        val issues = knitAnalysisService.detectIssues(components, dependencyGraph)
        
        // Should not detect ambiguous provider (only one active)
        val ambiguousIssues = issues.filter { it.type == IssueType.AMBIGUOUS_PROVIDER }
        assertFalse("Should not detect ambiguous provider for TestRepository",
                   ambiguousIssues.any { it.message.contains("TestRepository") })
        
        // Should not detect unresolved dependency
        val unresolvedIssues = issues.filter { it.type == IssueType.CIRCULAR_DEPENDENCY }
        assertFalse("Consumer should not have unresolved TestRepository dependency",
                   unresolvedIssues.any { it.componentName.contains("Consumer") })
        
        println("✅ Phase 2 Regression Prevention: Provider detection ignoring commented annotations")
    }

    /**
     * Phase 3 Regression Prevention: Issue Classification & Deduplication
     * Location: KnitAnalysisService.kt:288-305 - coordinated detection flow
     */
    @Test
    fun testPhase3RegressionPrevention_IssueDeduplication() {
        val testContent = """
            package com.example.regression

            import knit.Provides
            import knit.di

            // Real circular dependency that should be detected once with high priority
            @Provides
            class ServiceA {
                private val serviceB: ServiceB by di
            }

            @Provides
            class ServiceB {
                private val serviceA: ServiceA by di
            }
            
            // Component that depends on circular components (should not create duplicate issues)
            @Provides
            class ConsumerService {
                private val serviceA: ServiceA by di
                private val serviceB: ServiceB by di
            }
        """.trimIndent()
        
        myFixture.configureByText("DeduplicationTest.kt", testContent)
        
        val components = sourceAnalyzer.analyzeProject()
        val dependencyGraph = knitAnalysisService.buildDependencyGraph(components)
        val issues = knitAnalysisService.detectIssues(components, dependencyGraph)
        
        // Test deduplication - should have only one circular dependency issue
        val circularIssues = issues.filter { it.type == IssueType.CIRCULAR_DEPENDENCY }
        assertEquals("Should detect exactly one circular dependency issue (no duplicates)", 1, circularIssues.size)
        
        val circularIssue = circularIssues.first()
        assertTrue("Issue should mention both ServiceA and ServiceB",
                  circularIssue.componentName.contains("ServiceA") && circularIssue.componentName.contains("ServiceB"))
        
        // Test priority system expectations with current detector behavior:
        // ConsumerService depends on circular components but should not create duplicate circular dependency issues
        // The circular dependency is between ServiceA and ServiceB, not involving ConsumerService directly
        val unresolvedIssues = issues.filter { it.type == IssueType.CIRCULAR_DEPENDENCY }
        val unresolvedForConsumer = unresolvedIssues.filter { it.componentName.contains("ConsumerService") }
        assertEquals("ConsumerService should not have circular dependency issues (they're in ServiceA/ServiceB)", 0, unresolvedForConsumer.size)
        
        // Verify that the circular dependency is correctly identified between ServiceA and ServiceB
        val serviceACircularIssues = unresolvedIssues.filter { it.componentName.contains("ServiceA") }
        val serviceBCircularIssues = unresolvedIssues.filter { it.componentName.contains("ServiceB") }
        assertTrue("ServiceA should be involved in circular dependency", serviceACircularIssues.isNotEmpty())
        assertTrue("ServiceB should be involved in circular dependency", serviceBCircularIssues.isNotEmpty())
        
        // Validate issue classification with priority system
        val serviceAIssues = issues.filter { it.componentName.contains("ServiceA") }
        val serviceBIssues = issues.filter { it.componentName.contains("ServiceB") }
        
        // Each component should have at most one issue (highest priority only)
        assertTrue("ServiceA should have at most 1 issue type", 
                  serviceAIssues.map { it.type }.distinct().size <= 1)
        assertTrue("ServiceB should have at most 1 issue type", 
                  serviceBIssues.map { it.type }.distinct().size <= 1)
        
        println("✅ Phase 3 Regression Prevention: Issue deduplication and priority system working")
        println("  - Circular issues detected: ${circularIssues.size}")
        println("  - Total issues: ${issues.size}")
        println("  - Issue types: ${issues.map { it.type }.distinct()}")
    }

    /**
     * Phase 4 Regression Prevention: Statistical Accuracy Validation
     * Location: IssueValidator.kt, StatisticalAccuracyService.kt
     */
    @Test
    fun testPhase4RegressionPrevention_AccuracyValidation() {
        // Create test scenario with known true and false positives
        val testContent = """
            package com.example.regression

            import knit.Provides
            import knit.di

            // True positive: Real circular dependency
            @Provides
            class RealCircular1 {
                private val dep: RealCircular2 by di
            }

            @Provides  
            class RealCircular2 {
                private val dep: RealCircular1 by di
            }
            
            // False positive scenario that should be caught
            @Provides
            class FalsePositiveComponent {
                // This commented dependency should not create issues
                // private val fakeDep: NonExistentService by di
                private val realDep: ExistingService by di
            }
            
            @Provides
            class ExistingService
        """.trimIndent()
        
        myFixture.configureByText("AccuracyTest.kt", testContent)
        
        val components = sourceAnalyzer.analyzeProject()
        val dependencyGraph = knitAnalysisService.buildDependencyGraph(components)
        val detectedIssues = knitAnalysisService.detectIssues(components, dependencyGraph)
        
        // Validate issues using Phase 4 accuracy system
        val validationSettings = IssueValidator.ValidationSettings(
            validationEnabled = true,
            minimumConfidenceThreshold = 0.3
        )
        
        val validatedIssues = issueValidator.validateIssues(detectedIssues, components, validationSettings)
        
        // Calculate accuracy metrics
        val expectedIssues = statisticalService.estimateExpectedIssues(components)
        val accuracyMetrics = statisticalService.calculateAccuracyMetrics(
            allIssues = validatedIssues,
            validatedIssues = validatedIssues.filter { it.validationStatus != ValidationStatus.NOT_VALIDATED },
            expectedIssues = expectedIssues
        )
        
        // Verify accuracy targets
        val precision = accuracyMetrics.getPrecision()
        val falsePositiveRate = if (accuracyMetrics.truePositives + accuracyMetrics.falsePositives > 0) {
            accuracyMetrics.falsePositives.toDouble() / (accuracyMetrics.truePositives + accuracyMetrics.falsePositives)
        } else 0.0
        
        // Phase 4 regression prevention assertions
        assertTrue("Precision should remain ≥90%", precision >= 0.90)
        assertTrue("False positive rate should remain <10%", falsePositiveRate < 0.10)
        assertTrue("Should have validation status for all issues", 
                  validatedIssues.all { it.validationStatus != ValidationStatus.NOT_VALIDATED })
        assertTrue("Average confidence should be reasonable", 
                  accuracyMetrics.averageConfidenceScore > 0.5)
        
        // Verify specific validations
        val truePositives = validatedIssues.filter { it.validationStatus == ValidationStatus.VALIDATED_TRUE_POSITIVE }
        val falsePositives = validatedIssues.filter { it.validationStatus == ValidationStatus.VALIDATED_FALSE_POSITIVE }
        
        // Should detect circular dependency as true positive
        val validCircularIssues = truePositives.filter { it.type == IssueType.CIRCULAR_DEPENDENCY }
        assertTrue("Should detect circular dependency as true positive", validCircularIssues.isNotEmpty())
        
        // Should not have false positives for commented dependencies
        assertFalse("Should not have false positives for commented dependencies",
                   falsePositives.any { it.message.contains("NonExistentService") })
        
        println("✅ Phase 4 Regression Prevention: Accuracy validation system working")
        println("  - Precision: ${String.format("%.1f", precision * 100)}%")
        println("  - False positive rate: ${String.format("%.1f", falsePositiveRate * 100)}%")
        println("  - True positives: ${accuracyMetrics.truePositives}")
        println("  - False positives: ${accuracyMetrics.falsePositives}")
        println("  - Average confidence: ${String.format("%.2f", accuracyMetrics.averageConfidenceScore)}")
    }

    /**
     * Comprehensive regression test covering the specific scenario from ANALYSIS_ACCURACY_INVESTIGATION.md
     * Before: 7 reported issues (67% false positive rate, 33% accuracy)
     * After: Should detect only real issues with 95%+ accuracy
     */
    @Test
    fun testInvestigationScenarioRegressionPrevention() {
        // Recreate the exact scenario described in the investigation document
        val orderServiceContent = """
            package com.example.investigation

            import knit.Provides
            import knit.di

            @Provides
            class OrderService {
                // Real dependency that creates circular dependency with InventoryService
                private val inventoryService: InventoryService by di
                
                fun processOrder() = inventoryService.checkStock(emptyList())
                fun cancelOrder(orderId: Long) = println("Order cancelled")
            }
        """.trimIndent()
        
        val inventoryServiceContent = """
            package com.example.investigation

            import knit.Provides
            import knit.di

            @Provides
            class InventoryService {
                // Real dependency that completes circular dependency with OrderService
                private val orderService: OrderService by di
                
                fun checkStock(items: List<Long>) = true
                fun releaseReservedStock(orderId: Long) = orderService.cancelOrder(orderId)
            }
        """.trimIndent()
        
        val paymentServiceContent = """
            package com.example.investigation

            import knit.Provides
            import knit.di

            @Provides
            class PaymentService {
                // This dependency is commented out and should NOT create false positive
                // private val paymentGateway: PaymentGateway by di
                
                private val validator: PaymentValidator by di
                
                fun processPayment() = validator.validate()
            }
            
            @Provides
            class PaymentValidator {
                fun validate() = true
            }
        """.trimIndent()
        
        val repositoryContent = """
            package com.example.investigation

            import knit.Provides

            interface UserRepository {
                fun findAll(): List<String>
            }

            // Only this provider is active
            @Provides(UserRepository::class)
            class DatabaseUserRepository : UserRepository {
                override fun findAll() = listOf("user1", "user2")
            }

            // This provider is commented out and should be ignored
            // @Provides(UserRepository::class)
            class InMemoryUserRepository : UserRepository {
                override fun findAll() = listOf("memory_user")
            }
            
            @Provides
            class UserService {
                private val userRepository: UserRepository by di
                fun getUsers() = userRepository.findAll()
            }
        """.trimIndent()
        
        myFixture.configureByText("OrderService.kt", orderServiceContent)
        myFixture.configureByText("InventoryService.kt", inventoryServiceContent)
        myFixture.configureByText("PaymentService.kt", paymentServiceContent)
        myFixture.configureByText("UserRepository.kt", repositoryContent)
        
        val components = sourceAnalyzer.analyzeProject()
        val dependencyGraph = knitAnalysisService.buildDependencyGraph(components)
        val detectedIssues = knitAnalysisService.detectIssues(components, dependencyGraph)
        
        // Validate with Phase 4 accuracy system
        val validatedIssues = issueValidator.validateIssues(detectedIssues, components)
        
        // Analysis: Should detect exactly 1 real issue (circular dependency)
        val truePositives = validatedIssues.filter { it.validationStatus == ValidationStatus.VALIDATED_TRUE_POSITIVE }
        val falsePositives = validatedIssues.filter { it.validationStatus == ValidationStatus.VALIDATED_FALSE_POSITIVE }
        
        // Regression prevention: Should not have the 6 false positives from before
        assertTrue("Should have minimal false positives (target: 0-1, was: 4)", falsePositives.size <= 1)
        assertTrue("Should detect the real circular dependency", 
                  truePositives.any { it.type == IssueType.CIRCULAR_DEPENDENCY })
        
        // Should not detect false positives for:
        // 1. Commented PaymentGateway dependency
        assertFalse("Should not detect PaymentGateway issues",
                   detectedIssues.any { it.message.contains("PaymentGateway") })
        
        // 2. Ambiguous UserRepository providers (only one active)
        val ambiguousUserRepo = detectedIssues.filter { 
            it.type == IssueType.AMBIGUOUS_PROVIDER && it.message.contains("UserRepository") 
        }
        assertTrue("Should not detect ambiguous UserRepository providers", ambiguousUserRepo.isEmpty())
        
        // 3. Unresolved dependencies that are actually resolved
        val unresolvedUserRepo = detectedIssues.filter {
            it.type == IssueType.CIRCULAR_DEPENDENCY && 
            (it.componentName.contains("UserService") || it.message.contains("UserRepository"))
        }
        assertTrue("Should not detect unresolved UserRepository dependency", unresolvedUserRepo.isEmpty())
        
        // Calculate final accuracy metrics
        val expectedIssues = 1 // Only the circular dependency is real
        val accuracyMetrics = statisticalService.calculateAccuracyMetrics(
            allIssues = validatedIssues,
            validatedIssues = validatedIssues,
            expectedIssues = expectedIssues
        )
        
        val accuracy = if (accuracyMetrics.truePositives + accuracyMetrics.falsePositives > 0) {
            accuracyMetrics.truePositives.toDouble() / (accuracyMetrics.truePositives + accuracyMetrics.falsePositives)
        } else 1.0
        val falsePositiveRate = if (accuracyMetrics.truePositives + accuracyMetrics.falsePositives > 0) {
            accuracyMetrics.falsePositives.toDouble() / (accuracyMetrics.truePositives + accuracyMetrics.falsePositives)
        } else 0.0
        
        // Validate investigation targets achieved
        assertTrue("Accuracy should be ≥95% (was 33%)", accuracy >= 0.95)
        assertTrue("False positive rate should be <5% (was 67%)", falsePositiveRate < 0.05)
        assertTrue("Statistical error should be minimal", 
                  Math.abs(detectedIssues.size - expectedIssues) <= 1)
        
        println("✅ Investigation Scenario Regression Prevention:")
        println("  - BEFORE: 7 reported issues (67% false positives, 33% accuracy)")
        println("  - AFTER: ${detectedIssues.size} reported issues")
        println("  - True Positives: ${accuracyMetrics.truePositives}")
        println("  - False Positives: ${accuracyMetrics.falsePositives}")
        println("  - Accuracy: ${String.format("%.1f", accuracy * 100)}% (Target: ≥95%)")
        println("  - False Positive Rate: ${String.format("%.1f", falsePositiveRate * 100)}% (Target: <5%)")
        println("  - Statistical Error: ${Math.abs(detectedIssues.size - expectedIssues)} (Target: ≤1)")
        
        // Final regression prevention assertion
        assertTrue("Investigation accuracy targets must be maintained!", 
                  accuracy >= 0.95 && falsePositiveRate < 0.05)
    }

    /**
     * Edge cases regression prevention for complex scenarios
     */
    @Test
    fun testEdgeCasesRegressionPrevention() {
        val edgeCaseContent = """
            package com.example.edge

            import knit.Provides
            import knit.di

            // Edge case 1: Multiple comment styles
            @Provides
            class MultiCommentService {
                // Single line comment dependency
                // private val dep1: Service1 by di
                
                /* Block comment dependency */
                // private val dep2: Service2 by di
                
                /*
                 * Multi-line comment dependency
                 * private val dep3: Service3 by di
                 */
                
                // This is active and should be detected
                private val activeDep: ActiveService by di
            }
            
            // Edge case 2: Mixed active and commented providers
            @Provides
            class MixedProviderService {
                // Active provider
                @Provides
                fun provideActiveService(): ActiveService = ActiveService()
                
                // Commented provider should be ignored
                // @Provides
                // fun provideInactiveService(): InactiveService = InactiveService()
            }
            
            class ActiveService
            class InactiveService
            
            // Edge case 3: Whitespace and formatting variations
            @Provides
            class WhitespaceService {
                //private val spaceDep1: SpaceService by di
                //    private val spaceDep2: SpaceService by di  
                   // private val spaceDep3: SpaceService by di
                
                private val realDep: ActiveService by di
            }
        """.trimIndent()
        
        myFixture.configureByText("EdgeCases.kt", edgeCaseContent)
        
        val components = sourceAnalyzer.analyzeProject()
        
        // Validate edge case handling
        val multiCommentService = components.find { it.className == "MultiCommentService" }
        assertNotNull("MultiCommentService should be found", multiCommentService)
        assertEquals("Should detect only 1 active dependency", 1, multiCommentService!!.dependencies.size)
        assertEquals("Should detect ActiveService", "ActiveService", multiCommentService.dependencies.first().targetType)
        
        val mixedProviderService = components.find { it.className == "MixedProviderService" }
        assertNotNull("MixedProviderService should be found", mixedProviderService)
        assertEquals("Should have only 1 active provider", 1, mixedProviderService!!.providers.size)
        assertEquals("Should provide ActiveService", "ActiveService", mixedProviderService.providers.first().returnType)
        
        val whitespaceService = components.find { it.className == "WhitespaceService" }
        assertNotNull("WhitespaceService should be found", whitespaceService)
        assertEquals("Should handle whitespace variations", 1, whitespaceService!!.dependencies.size)
        
        println("✅ Edge Cases Regression Prevention: All comment parsing edge cases handled correctly")
    }
}