package com.example.mittens.validation

import com.example.mittens.model.*
import com.example.mittens.services.*
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * Comprehensive Phase 5 validation test using the actual sample_project
 * Tests the complete accuracy pipeline against real-world Knit DI scenarios
 * 
 * This test validates:
 * 1. Detection of real circular dependency (OrderService ↔ InventoryService)
 * 2. Elimination of false positives from commented code
 * 3. Proper handling of ambiguous providers
 * 4. Achievement of 95%+ accuracy targets
 */
class SampleProjectAccuracyValidationTest : LightJavaCodeInsightFixtureTestCase() {

    private lateinit var knitAnalysisService: KnitAnalysisService
    private lateinit var settingsService: KnitSettingsService
    private lateinit var sourceAnalyzer: KnitSourceAnalyzer
    private lateinit var issueValidator: IssueValidator
    private lateinit var statisticalService: StatisticalAccuracyService
    
    override fun setUp() {
        super.setUp()
        knitAnalysisService = KnitAnalysisService(project)
        settingsService = KnitSettingsService()
        sourceAnalyzer = KnitSourceAnalyzer(project)
        issueValidator = IssueValidator(project)
        statisticalService = StatisticalAccuracyService()
        
        // Enable validation for comprehensive testing
        settingsService.setValidationEnabled(true)
        settingsService.setConfidenceThreshold(0.3) // Lower threshold for comprehensive validation
        settingsService.setAccuracyReportingEnabled(true)
    }

    @Test
    fun testSampleProjectCircularDependencyDetection() {
        // Test the real circular dependency from ANALYSIS_ACCURACY_INVESTIGATION.md
        // OrderService.kt:11 - private val inventoryService: InventoryService by di
        // InventoryService.kt:9 - private val orderService: OrderService by di
        
        val orderServiceContent = """
            package com.example.knit.demo.core.services
            
            import com.example.knit.demo.core.models.Order
            import com.example.knit.demo.core.models.OrderStatus
            import knit.Provides
            import knit.di

            @Provides
            class OrderService {
                private val inventoryService: InventoryService by di
                
                fun processOrder(order: Order): Order {
                    val hasStock = inventoryService.checkStock(order.items.map { it.productId })
                    return if (hasStock) {
                        inventoryService.reserveStock(order.items.map { it.productId to it.quantity }.toMap())
                        order.copy(status = OrderStatus.CONFIRMED)
                    } else {
                        order.copy(status = OrderStatus.CANCELLED)
                    }
                }
                
                fun cancelOrder(orderId: Long) {
                    inventoryService.releaseReservedStock(orderId)
                }
            }
        """.trimIndent()
        
        val inventoryServiceContent = """
            package com.example.knit.demo.core.services

            import knit.Provides
            import knit.di

            @Provides
            class InventoryService {
                private val orderService: OrderService by di
                
                private val inventory = mutableMapOf<Long, Int>(
                    1L to 10, 2L to 5, 3L to 20, 4L to 0
                )
                
                fun checkStock(productIds: List<Long>): Boolean {
                    return productIds.all { (inventory[it] ?: 0) > 0 }
                }
                
                fun reserveStock(productQuantities: Map<Long, Int>) {
                    productQuantities.forEach { (productId, quantity) ->
                        val available = inventory[productId] ?: 0
                        if (available >= quantity) {
                            inventory[productId] = available - quantity
                        }
                    }
                }
                
                fun releaseReservedStock(orderId: Long) {
                    orderService.cancelOrder(orderId)
                }
                
                fun getAvailableStock(productId: Long): Int {
                    return inventory[productId] ?: 0
                }
            }
        """.trimIndent()
        
        myFixture.configureByText("OrderService.kt", orderServiceContent)
        myFixture.configureByText("InventoryService.kt", inventoryServiceContent)
        
        val components = sourceAnalyzer.analyzeProject()
        val dependencyGraph = knitAnalysisService.buildDependencyGraph(components)
        val detectedIssues = knitAnalysisService.detectIssues(components, dependencyGraph)
        
        // Validate detection of circular dependency
        val circularIssues = detectedIssues.filter { it.type == IssueType.CIRCULAR_DEPENDENCY }
        assertTrue("Should detect circular dependency", circularIssues.isNotEmpty())
        
        val circularIssue = circularIssues.first()
        assertTrue("Issue should mention OrderService and InventoryService", 
                  circularIssue.componentName.contains("OrderService") && 
                  circularIssue.componentName.contains("InventoryService"))
        
        // Validate issue using Phase 4 accuracy system
        val validatedIssues = issueValidator.validateIssues(detectedIssues, components)
        val validatedCircularIssues = validatedIssues.filter { 
            it.type == IssueType.CIRCULAR_DEPENDENCY && 
            it.validationStatus == ValidationStatus.VALIDATED_TRUE_POSITIVE 
        }
        
        assertTrue("Circular dependency should be validated as true positive", validatedCircularIssues.isNotEmpty())
        
        val validatedCircular = validatedCircularIssues.first()
        assertTrue("Confidence should be high for real circular dependency", validatedCircular.confidenceScore > 0.8)
        
        println("✅ Sample Project Circular Dependency Detection:")
        println("  - Detected: ${circularIssues.size} circular dependency issues")
        println("  - Validated as true positive: ${validatedCircularIssues.size}")
        println("  - Confidence score: ${String.format("%.2f", validatedCircular.confidenceScore)}")
    }
    
    @Test
    fun testCommentedCodeFalsePositiveElimination() {
        // Test the PaymentGateway false positive from the investigation
        // PaymentService.kt:12 - // private val paymentGateway: PaymentGateway by di
        
        val paymentServiceContent = """
            package com.example.knit.demo.payment

            import knit.Provides
            import knit.di

            @Provides
            class PaymentService {
                // This dependency is commented out and should NOT be detected
                // private val paymentGateway: PaymentGateway by di
                
                // Multiple comment styles to test parsing robustness
                /*
                 * private val anotherGateway: AnotherGateway by di
                 */
                
                // Active dependency that should be detected
                private val validator: PaymentValidator by di
                
                fun processPayment(amount: Double): Boolean {
                    return validator.validate(amount)
                }
            }
            
            interface PaymentGateway {
                fun charge(amount: Double): Boolean
            }
            
            interface AnotherGateway {
                fun process(): Boolean
            }
            
            @Provides
            class PaymentValidator {
                fun validate(amount: Double): Boolean = amount > 0
            }
        """.trimIndent()
        
        myFixture.configureByText("PaymentService.kt", paymentServiceContent)
        
        val components = sourceAnalyzer.analyzeProject()
        
        val paymentService = components.find { it.className == "PaymentService" }
        assertNotNull("PaymentService should be detected", paymentService)
        
        // Verify that commented dependencies are NOT detected
        val dependencies = paymentService!!.dependencies
        val dependencyTypes = dependencies.map { it.targetType }
        
        assertFalse("Should NOT detect commented PaymentGateway dependency", 
                   dependencyTypes.contains("PaymentGateway"))
        assertFalse("Should NOT detect commented AnotherGateway dependency", 
                   dependencyTypes.contains("AnotherGateway"))
        assertTrue("Should detect active PaymentValidator dependency", 
                  dependencyTypes.contains("PaymentValidator"))
        
        // Test full analysis pipeline with validation
        val dependencyGraph = knitAnalysisService.buildDependencyGraph(components)
        val detectedIssues = knitAnalysisService.detectIssues(components, dependencyGraph)
        
        // Should not detect unresolved dependency issues for commented code
        val unresolvedIssues = detectedIssues.filter { it.type == IssueType.UNRESOLVED_DEPENDENCY }
        val paymentGatewayIssues = unresolvedIssues.filter { 
            it.message.contains("PaymentGateway") || it.message.contains("AnotherGateway")
        }
        
        assertTrue("Should NOT have unresolved issues for commented dependencies", paymentGatewayIssues.isEmpty())
        
        println("✅ Commented Code False Positive Elimination:")
        println("  - Active dependencies detected: ${dependencies.size}")
        println("  - Commented dependencies ignored: 2 (PaymentGateway, AnotherGateway)")
        println("  - False positive issues for commented code: ${paymentGatewayIssues.size}")
    }
    
    @Test
    fun testAmbiguousProviderHandling() {
        // Test UserRepository ambiguous provider scenario
        val databaseUserRepositoryContent = """
            package com.example.knit.demo.core.repositories

            import knit.Provides

            @Provides(UserRepository::class)
            class DatabaseUserRepository : UserRepository {
                override fun findAll(): List<User> {
                    return listOf(User(1L, "Database User"))
                }
                
                override fun findById(id: Long): User? {
                    return User(id, "Database User ${'$'}id")
                }
            }
        """.trimIndent()
        
        val inMemoryUserRepositoryContent = """
            package com.example.knit.demo.core.repositories

            import knit.Provides

            // This provider should be detected as commented (false positive elimination)
            // @Provides(UserRepository::class)
            class InMemoryUserRepository : UserRepository {
                override fun findAll(): List<User> {
                    return listOf(User(1L, "Memory User"))
                }
                
                override fun findById(id: Long): User? {
                    return User(id, "Memory User ${'$'}id")
                }
            }
        """.trimIndent()
        
        val userContent = """
            package com.example.knit.demo.core.repositories
            
            data class User(val id: Long, val name: String)
            
            interface UserRepository {
                fun findAll(): List<User>
                fun findById(id: Long): User?
            }
        """.trimIndent()
        
        val userServiceContent = """
            package com.example.knit.demo.core.services
            
            import com.example.knit.demo.core.repositories.UserRepository
            import knit.Provides
            import knit.di
            
            @Provides
            class UserService {
                private val userRepository: UserRepository by di
                
                fun getAllUsers() = userRepository.findAll()
                fun getUser(id: Long) = userRepository.findById(id)
            }
        """.trimIndent()
        
        myFixture.configureByText("DatabaseUserRepository.kt", databaseUserRepositoryContent)
        myFixture.configureByText("InMemoryUserRepository.kt", inMemoryUserRepositoryContent)
        myFixture.configureByText("User.kt", userContent)
        myFixture.configureByText("UserService.kt", userServiceContent)
        
        val components = sourceAnalyzer.analyzeProject()
        
        // Verify provider detection
        val databaseRepo = components.find { it.className == "DatabaseUserRepository" }
        val inMemoryRepo = components.find { it.className == "InMemoryUserRepository" }
        
        assertNotNull("DatabaseUserRepository should be detected", databaseRepo)
        assertNull("InMemoryUserRepository should NOT be detected (no active DI features)", inMemoryRepo)
        
        // Verify active provider is detected
        assertTrue("DatabaseUserRepository should have UserRepository provider", 
                  databaseRepo!!.providers.any { it.returnType == "UserRepository" })
        
        // Run full analysis
        val dependencyGraph = knitAnalysisService.buildDependencyGraph(components)
        val detectedIssues = knitAnalysisService.detectIssues(components, dependencyGraph)
        
        // Should not detect ambiguous provider issue since only one active provider
        val ambiguousIssues = detectedIssues.filter { it.type == IssueType.AMBIGUOUS_PROVIDER }
        val userRepositoryAmbiguous = ambiguousIssues.filter { it.message.contains("UserRepository") }
        
        assertTrue("Should NOT detect ambiguous provider for UserRepository (only one active)", 
                  userRepositoryAmbiguous.isEmpty())
        
        // Should not detect unresolved dependency for UserService
        val unresolvedIssues = detectedIssues.filter { 
            it.type == IssueType.UNRESOLVED_DEPENDENCY && 
            it.componentName.contains("UserService") 
        }
        
        assertTrue("UserService should not have unresolved UserRepository dependency", 
                  unresolvedIssues.isEmpty())
        
        println("✅ Ambiguous Provider Handling:")
        println("  - Active providers: ${components.flatMap { it.providers }.count { it.returnType == "UserRepository" }}")
        println("  - Commented providers ignored: 1 (InMemoryUserRepository)")
        println("  - Ambiguous provider issues: ${userRepositoryAmbiguous.size}")
        println("  - Unresolved dependency issues: ${unresolvedIssues.size}")
    }
    
    @Test
    fun testComprehensiveAccuracyMetricsValidation() {
        // Create a comprehensive test scenario covering all investigation scenarios
        val comprehensiveTestContent = """
            package com.example.comprehensive

            import knit.Provides
            import knit.Component
            import knit.di

            // Real circular dependency (should be detected as true positive)
            @Provides
            class OrderService {
                private val inventoryService: InventoryService by di
                fun processOrder() = inventoryService.checkStock(emptyList())
            }

            @Provides
            class InventoryService {
                private val orderService: OrderService by di
                fun releaseStock(orderId: Long) = orderService.cancelOrder(orderId)
                fun checkStock(items: List<Long>) = true
            }

            // Commented dependency (should NOT create false positive)
            @Provides
            class PaymentService {
                // private val paymentGateway: PaymentGateway by di
                private val validator: PaymentValidator by di
                fun validate() = validator.isValid()
            }

            @Provides
            class PaymentValidator {
                fun isValid() = true
            }

            // Single active provider (should NOT be ambiguous)
            @Provides(DataRepository::class)
            class DatabaseRepository : DataRepository {
                override fun getData() = "database data"
            }

            // Commented provider (should be ignored)
            // @Provides(DataRepository::class)
            class CacheRepository : DataRepository {
                override fun getData() = "cache data"
            }

            interface DataRepository {
                fun getData(): String
            }

            @Provides
            class DataService {
                private val repository: DataRepository by di
                fun fetchData() = repository.getData()
            }
        """.trimIndent()
        
        myFixture.configureByText("ComprehensiveTest.kt", comprehensiveTestContent)
        
        val components = sourceAnalyzer.analyzeProject()
        val dependencyGraph = knitAnalysisService.buildDependencyGraph(components)
        val detectedIssues = knitAnalysisService.detectIssues(components, dependencyGraph)
        
        // Validate issues with Phase 4 accuracy system
        val validationSettings = IssueValidator.ValidationSettings(
            validationEnabled = true,
            minimumConfidenceThreshold = 0.3
        )
        
        val validatedIssues = issueValidator.validateIssues(detectedIssues, components, validationSettings)
        val expectedIssues = statisticalService.estimateExpectedIssues(components)
        val accuracyMetrics = statisticalService.calculateAccuracyMetrics(
            allIssues = validatedIssues,
            validatedIssues = validatedIssues.filter { it.validationStatus != ValidationStatus.NOT_VALIDATED },
            expectedIssues = expectedIssues
        )
        
        // Validate accuracy targets from ANALYSIS_ACCURACY_INVESTIGATION.md
        val precision = accuracyMetrics.getPrecision()
        val recall = accuracyMetrics.getRecall()
        val f1Score = accuracyMetrics.getF1Score()
        val falsePositiveRate = if (accuracyMetrics.truePositives + accuracyMetrics.falsePositives > 0) {
            accuracyMetrics.falsePositives.toDouble() / (accuracyMetrics.truePositives + accuracyMetrics.falsePositives)
        } else 0.0
        
        // Target validation
        assertTrue("Precision should be ≥95%", precision >= 0.95)
        assertTrue("Recall should be ≥90% (allowing some false negatives)", recall >= 0.90)
        assertTrue("F1 score should be ≥92%", f1Score >= 0.92)
        assertTrue("False positive rate should be <5%", falsePositiveRate < 0.05)
        
        // Validate specific issue detection
        val truePositives = validatedIssues.filter { it.validationStatus == ValidationStatus.VALIDATED_TRUE_POSITIVE }
        val falsePositives = validatedIssues.filter { it.validationStatus == ValidationStatus.VALIDATED_FALSE_POSITIVE }
        
        // Should detect exactly 1 true positive (circular dependency)
        val circularDependencyIssues = truePositives.filter { it.type == IssueType.CIRCULAR_DEPENDENCY }
        assertEquals("Should detect exactly 1 circular dependency", 1, circularDependencyIssues.size)
        
        // Should have minimal false positives
        assertTrue("False positives should be minimal", falsePositives.size <= 1)
        
        // Generate comprehensive accuracy report
        val accuracyReport = statisticalService.generateAccuracyReport(accuracyMetrics, validatedIssues.size)
        
        println("✅ Comprehensive Accuracy Metrics Validation:")
        println("  - Total Issues Detected: ${detectedIssues.size}")
        println("  - True Positives: ${accuracyMetrics.truePositives}")
        println("  - False Positives: ${accuracyMetrics.falsePositives}")
        println("  - Precision: ${String.format("%.1f", precision * 100)}% (Target: ≥95%)")
        println("  - Recall: ${String.format("%.1f", recall * 100)}% (Target: ≥90%)")
        println("  - F1 Score: ${String.format("%.1f", f1Score * 100)}% (Target: ≥92%)")
        println("  - False Positive Rate: ${String.format("%.1f", falsePositiveRate * 100)}% (Target: <5%)")
        println("  - Average Confidence: ${String.format("%.2f", accuracyMetrics.averageConfidenceScore)}")
        println("\n" + accuracyReport)
        
        // Validate investigation targets achieved
        assertTrue("Phase 5 validation: Investigation accuracy targets achieved!", 
                  precision >= 0.95 && falsePositiveRate < 0.05 && f1Score >= 0.92)
    }

    @Test
    fun testAccuracyTrendTracking() {
        // Test trend tracking functionality
        val initialMetrics = AccuracyMetrics(
            totalValidatedIssues = 6,
            truePositives = 2, // Before fix: 33% accuracy
            falsePositives = 4, // 67% false positive rate
            falseNegatives = 1,
            validationEnabled = true,
            averageConfidenceScore = 0.5
        )
        
        val improvedMetrics = AccuracyMetrics(
            totalValidatedIssues = 2,
            truePositives = 2, // After fix: 100% accuracy
            falsePositives = 0, // 0% false positive rate
            falseNegatives = 0,
            validationEnabled = true,
            averageConfidenceScore = 0.95
        )
        
        val trendReport = statisticalService.compareWithPreviousAnalysis(improvedMetrics, initialMetrics)
        
        assertTrue("Should have comparison", trendReport.hasComparison)
        assertTrue("Precision should improve dramatically", trendReport.precisionChange > 0.6)
        assertTrue("False positives should decrease significantly", trendReport.falsePositiveChange < -3)
        assertTrue("Average confidence should improve", improvedMetrics.averageConfidenceScore > initialMetrics.averageConfidenceScore)
        assertEquals("Should show improving trend", AccuracyTrend.IMPROVING, trendReport.trend)
        
        println("✅ Accuracy Trend Tracking Validation:")
        println("  - Before: ${initialMetrics.truePositives}/${initialMetrics.totalValidatedIssues} correct (${String.format("%.1f", initialMetrics.getPrecision() * 100)}%)")
        println("  - After: ${improvedMetrics.truePositives}/${improvedMetrics.totalValidatedIssues} correct (${String.format("%.1f", improvedMetrics.getPrecision() * 100)}%)")
        println("  - Precision improvement: ${String.format("%.1f", trendReport.precisionChange * 100)}%")
        println("  - False positive reduction: ${trendReport.falsePositiveChange}")
        println("  - Trend: ${trendReport.trend}")
    }
    
    private fun assertContains(text: String, substring: String) {
        assertTrue("Text should contain '$substring'", text.contains(substring))
    }
}