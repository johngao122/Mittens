package com.example.mittens.services

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

/**
 * Expanded Phase 5 Comment Detection Test Suite
 * 
 * Core tests for Comment Detection for False Positive Elimination plus comprehensive
 * Phase 1-4 validation scenarios from ANALYSIS_ACCURACY_INVESTIGATION.md
 * 
 * This test suite validates:
 * - Phase 1: Source code parsing issues (comment detection)
 * - Phase 2: Provider detection logic with commented annotations  
 * - Phase 3: Issue classification integration with comment handling
 * - Phase 4: Accuracy validation of comment detection improvements
 * - Phase 5: Comprehensive validation and regression prevention
 */
@TestDataPath("\$CONTENT_ROOT/testData")
class CommentDetectionTest : BasePlatformTestCase() {
    
    private lateinit var sourceAnalyzer: KnitSourceAnalyzer
    
    override fun setUp() {
        super.setUp()
        sourceAnalyzer = KnitSourceAnalyzer(project)
    }
    
    @Test
    fun testCommentedDependencyIgnored() {
        // Commented 'by di' dependencies should be ignored
        // This addresses the PaymentGateway false positive in the investigation
        
        val paymentServiceContent = """
            package com.example.knit.demo.core.services
            
            import knit.Provides
            import knit.di
            
            @Provides
            class PaymentService {
                // private val paymentGateway: PaymentGateway by di // Commented to avoid unresolved error
                
                fun processOrderPayment(): String = "Payment processed without gateway"
            }
        """.trimIndent()
        
        myFixture.configureByText("PaymentService.kt", paymentServiceContent)
        
        val components = sourceAnalyzer.analyzeProject()
        val paymentService = components.find { it.className == "PaymentService" }
        
        assertNotNull("PaymentService should be found", paymentService)
        assertEquals("PaymentService should have 0 dependencies (commented PaymentGateway ignored)", 
                    0, paymentService!!.dependencies.size)
        
        println("✅ Commented dependency properly ignored in PaymentService")
    }
    
    @Test  
    fun testCommentedProviderIgnored() {
        // Commented @Provides annotations should be ignored
        // This addresses the InMemoryUserRepository false positive in the investigation
        
        val inMemoryRepoContent = """
            package com.example.knit.demo.core.repositories
            
            import knit.Provides
            
            // @Provides(UserRepository::class) // Temporarily commented to test other scenarios
            class InMemoryUserRepository {
                fun findAll(): List<String> = emptyList()
            }
        """.trimIndent()
        
        myFixture.configureByText("InMemoryUserRepository.kt", inMemoryRepoContent)
        
        val components = sourceAnalyzer.analyzeProject()
        val inMemoryRepo = components.find { it.className == "InMemoryUserRepository" }
        
        // Note: Component might not be detected at all since it has no active Knit annotations
        if (inMemoryRepo != null) {
            assertEquals("InMemoryUserRepository should have 0 providers (commented @Provides ignored)", 
                        0, inMemoryRepo.providers.size)
            println("✅ Commented provider properly ignored in InMemoryUserRepository")
        } else {
            println("✅ InMemoryUserRepository not detected as component (no active annotations)")
        }
    }
    
    @Test
    fun testActiveCodeStillDetected() {
        // Active (non-commented) code should still be detected
        // This ensures we don't break existing functionality
        
        val activeServiceContent = """
            package com.example.test
            
            import knit.Component  
            import knit.Provides
            import knit.di
            
            @Component
            class ActiveService {
                private val activeDependency: RealService by di
            }
            
            @Provides
            class ActiveProvider {
                @Provides
                fun provideRealService(): RealService = RealService()
            }
            
            class RealService
        """.trimIndent()
        
        myFixture.configureByText("ActiveService.kt", activeServiceContent)
        
        val components = sourceAnalyzer.analyzeProject()
        
        val activeService = components.find { it.className == "ActiveService" }
        assertNotNull("ActiveService should be found", activeService)
        assertEquals("ActiveService should have 1 dependency", 1, activeService!!.dependencies.size)
        assertEquals("Dependency should be RealService", "RealService", activeService.dependencies.first().targetType)
        
        val activeProvider = components.find { it.className == "ActiveProvider" }
        assertNotNull("ActiveProvider should be found", activeProvider)
        assertEquals("ActiveProvider should have 1 provider", 1, activeProvider!!.providers.size)
        assertEquals("Provider should return RealService", "RealService", activeProvider.providers.first().returnType)
        
        println("✅ Active dependencies and providers properly detected")
    }
    
    @Test
    fun testInvestigationScenarios() {
        // Validate the specific scenarios from ANALYSIS_ACCURACY_INVESTIGATION.md
        // Before: 7 reported issues (6 false positives + 1 real)
        // After: Should eliminate false positives from commented code
        
        val realCircularDepContent = """
            package com.example.test
            
            import knit.Provides
            import knit.di
            
            @Provides
            class OrderService {
                private val inventoryService: InventoryService by di
            }
            
            @Provides  
            class InventoryService {
                private val orderService: OrderService by di
            }
        """.trimIndent()
        
        myFixture.configureByText("CircularDep.kt", realCircularDepContent)
        
        val components = sourceAnalyzer.analyzeProject()
        
        // Should detect both services with their dependencies (real circular dependency)
        val orderService = components.find { it.className == "OrderService" }
        val inventoryService = components.find { it.className == "InventoryService" }
        
        assertNotNull("OrderService should be found", orderService)
        assertNotNull("InventoryService should be found", inventoryService)
        
        assertEquals("OrderService should have 1 dependency on InventoryService", 1, orderService!!.dependencies.size)
        assertEquals("InventoryService should have 1 dependency on OrderService", 1, inventoryService!!.dependencies.size)
        
        assertEquals("OrderService dependency should be InventoryService", "InventoryService", orderService.dependencies.first().targetType)
        assertEquals("InventoryService dependency should be OrderService", "OrderService", inventoryService.dependencies.first().targetType)
        
        println("✅ Real circular dependency properly detected (not eliminated)")
        println("✅ Comment detection doesn't affect valid dependency detection")
    }
    
    @Test
    fun testAccuracyImprovement() {
        // Demonstrate accuracy improvement
        // This test combines scenarios to show before/after behavior
        
        val mixedScenarioContent = """
            package com.example.test
            
            import knit.Component
            import knit.Provides  
            import knit.di
            
            @Component
            class ServiceWithCommentedDeps {
                // private val commentedDep1: String by di // Should be ignored
                // private val commentedDep2: Int by di    // Should be ignored
                private val activeDep: ActiveService by di  // Should be detected
            }
            
            @Provides
            class ProviderWithCommentedMethods {
                // @Provides                               // Should be ignored
                // fun provideCommentedString(): String = "test"
                
                @Provides                                  // Should be detected
                fun provideActiveService(): ActiveService = ActiveService()
            }
            
            class ActiveService
        """.trimIndent()
        
        myFixture.configureByText("MixedScenario.kt", mixedScenarioContent)
        
        val components = sourceAnalyzer.analyzeProject()
        
        // Validate that only active code is detected
        val serviceWithCommentedDeps = components.find { it.className == "ServiceWithCommentedDeps" }
        assertNotNull("ServiceWithCommentedDeps should be found", serviceWithCommentedDeps)
        assertEquals("Should have only 1 dependency (commented ones ignored)", 
                    1, serviceWithCommentedDeps!!.dependencies.size)
        assertEquals("Active dependency should be ActiveService", "ActiveService", 
                    serviceWithCommentedDeps.dependencies.first().targetType)
        
        val providerWithCommentedMethods = components.find { it.className == "ProviderWithCommentedMethods" }
        assertNotNull("ProviderWithCommentedMethods should be found", providerWithCommentedMethods)
        assertEquals("Should have only 1 provider (commented one ignored)", 
                    1, providerWithCommentedMethods!!.providers.size)
        assertEquals("Active provider should return ActiveService", "ActiveService",
                    providerWithCommentedMethods.providers.first().returnType)
        
        println("✅ Mixed scenario - only active code detected, commented code ignored")
        println("✅ False positive elimination demonstrated")
    }

    /**
     * Phase 1-4 Integration: Advanced Comment Detection Scenarios
     * Tests various comment patterns and edge cases that could cause regressions
     */
    @Test
    fun testAdvancedCommentPatterns() {
        val advancedCommentContent = """
            package com.example.advanced
            
            import knit.Provides
            import knit.di
            
            @Provides
            class AdvancedCommentService {
                // Single line comments with variations
                // private val dep1: Service1 by di
                //private val dep2: Service2 by di  
                //    private val dep3: Service3 by di
                
                /* Block comments */
                /* private val dep4: Service4 by di */
                
                /*
                 * Multi-line comments with proper formatting
                 * private val dep5: Service5 by di
                 */
                 
                /* Multi-line block
                   private val dep6: Service6 by di
                   continued... */
                
                // Nested comments and complex patterns
                // /* private val dep7: Service7 by di */
                /* // private val dep8: Service8 by di */
                
                // This should be detected as active
                private val activeDep: ActiveService by di
                
                // Active dependency after comments
                private val anotherActive: AnotherService by di
            }
            
            @Provides
            class ActiveService
            
            @Provides  
            class AnotherService
        """.trimIndent()
        
        myFixture.configureByText("AdvancedComments.kt", advancedCommentContent)
        
        val components = sourceAnalyzer.analyzeProject()
        val service = components.find { it.className == "AdvancedCommentService" }
        
        assertNotNull("AdvancedCommentService should be found", service)
        assertEquals("Should detect exactly 2 active dependencies", 2, service!!.dependencies.size)
        
        val dependencyTypes = service.dependencies.map { it.targetType }.sorted()
        assertEquals("Should detect ActiveService", "ActiveService", dependencyTypes[0])
        assertEquals("Should detect AnotherService", "AnotherService", dependencyTypes[1])
        
        println("✅ Advanced comment patterns properly handled")
        println("  - Active dependencies detected: 2")
        println("  - Commented dependencies ignored: 8")
    }

    /**
     * Phase 2 Integration: Provider Detection with Comment Variations  
     */
    @Test
    fun testProviderCommentDetection() {
        val providerCommentContent = """
            package com.example.provider
            
            import knit.Provides
            
            interface TestService
            interface AnotherService
            
            @Provides
            class MultiProviderService {
                // Commented provider methods should be ignored
                // @Provides
                // fun provideCommentedService(): TestService = object : TestService {}
                
                /*
                 * @Provides
                 * fun provideBlockCommentedService(): TestService = object : TestService {}
                 */
                
                /* @Provides
                   fun provideInlineBlockService(): TestService = object : TestService {} */
                
                // Active provider should be detected
                @Provides
                fun provideActiveService(): TestService = object : TestService {}
                
                @Provides
                fun provideAnotherService(): AnotherService = object : AnotherService {}
            }
            
            // Commented class-level provider annotation
            // @Provides(TestService::class)
            class CommentedClassProvider : TestService
            
            // Active class-level provider
            @Provides(AnotherService::class)
            class ActiveClassProvider : AnotherService
        """.trimIndent()
        
        myFixture.configureByText("ProviderComments.kt", providerCommentContent)
        
        val components = sourceAnalyzer.analyzeProject()
        
        val multiProvider = components.find { it.className == "MultiProviderService" }
        assertNotNull("MultiProviderService should be found", multiProvider)
        assertEquals("Should have 2 active provider methods", 2, multiProvider!!.providers.size)
        
        val providerTypes = multiProvider.providers.map { it.returnType }.sorted()
        assertTrue("Should provide AnotherService", providerTypes.contains("AnotherService"))
        assertTrue("Should provide TestService", providerTypes.contains("TestService"))
        
        val commentedProvider = components.find { it.className == "CommentedClassProvider" }
        val activeProvider = components.find { it.className == "ActiveClassProvider" }
        
        // CommentedClassProvider might not be detected as it has no active annotations
        if (commentedProvider != null) {
            assertTrue("CommentedClassProvider should have no providers", commentedProvider.providers.isEmpty())
        }
        
        assertNotNull("ActiveClassProvider should be found", activeProvider)
        assertEquals("ActiveClassProvider should have 1 provider", 1, activeProvider!!.providers.size)
        assertEquals("Should provide AnotherService", "AnotherService", activeProvider.providers.first().returnType)
        
        println("✅ Provider comment detection working correctly")
        println("  - Method providers detected: ${multiProvider.providers.size}")
        println("  - Class providers detected: ${activeProvider.providers.size}")
    }

    /**
     * Phase 3 Integration: Issue Classification with Comment Handling
     */
    @Test
    fun testIssueClassificationWithComments() {
        val issueClassificationContent = """
            package com.example.classification
            
            import knit.Provides
            import knit.di
            
            // Real circular dependency that should be detected
            @Provides
            class ServiceA {
                private val serviceB: ServiceB by di
                // private val commentedDep: ServiceC by di  // Should not affect analysis
            }
            
            @Provides
            class ServiceB {
                private val serviceA: ServiceA by di
                /* private val anotherCommented: ServiceA by di */ // Should not create duplicate
            }
            
            // Service with only commented dependencies (should not create false issues)
            @Provides
            class ServiceC {
                // private val serviceA: ServiceA by di
                // private val serviceB: ServiceB by di
                // No active dependencies
            }
            
            // Provider with commented and active providers
            @Provides
            class MixedProvider {
                // @Provides
                // fun provideCommented(): String = "commented"
                
                @Provides
                fun provideActive(): String = "active"
            }
            
            @Provides
            class StringConsumer {
                private val value: String by di
            }
        """.trimIndent()
        
        myFixture.configureByText("Classification.kt", issueClassificationContent)
        
        val components = sourceAnalyzer.analyzeProject()
        
        // Verify component detection
        val serviceA = components.find { it.className == "ServiceA" }
        val serviceB = components.find { it.className == "ServiceB" }
        val serviceC = components.find { it.className == "ServiceC" }
        
        assertNotNull("ServiceA should be found", serviceA)
        assertNotNull("ServiceB should be found", serviceB)
        assertNotNull("ServiceC should be found", serviceC)
        
        // Verify comment handling in dependencies
        assertEquals("ServiceA should have 1 dependency", 1, serviceA!!.dependencies.size)
        assertEquals("ServiceB should have 1 dependency", 1, serviceB!!.dependencies.size)
        assertEquals("ServiceC should have 0 dependencies", 0, serviceC!!.dependencies.size)
        
        // Verify circular dependency is properly detected
        assertEquals("ServiceA depends on ServiceB", "ServiceB", serviceA.dependencies.first().targetType)
        assertEquals("ServiceB depends on ServiceA", "ServiceA", serviceB.dependencies.first().targetType)
        
        // Verify provider detection
        val mixedProvider = components.find { it.className == "MixedProvider" }
        assertNotNull("MixedProvider should be found", mixedProvider)
        assertEquals("Should have 1 active provider", 1, mixedProvider!!.providers.size)
        assertEquals("Should provide String", "String", mixedProvider.providers.first().returnType)
        
        println("✅ Issue classification with comment handling working correctly")
        println("  - Circular dependency components: 2 (ServiceA, ServiceB)")
        println("  - Components with no dependencies: 1 (ServiceC)")
        println("  - Active providers: 1 (MixedProvider)")
    }

    /**
     * Phase 4 Integration: Accuracy Validation with Comment Scenarios
     */
    @Test
    fun testAccuracyValidationWithComments() {
        // Create a scenario that would have caused false positives before comment detection fixes
        val accuracyValidationContent = """
            package com.example.accuracy
            
            import knit.Provides
            import knit.di
            
            // Scenario 1: Service with many commented dependencies (potential false positives)
            @Provides
            class ServiceWithManyComments {
                // These would have caused false "unresolved dependency" issues before Phase 1
                // private val nonExistentService1: NonExistent1 by di
                // private val nonExistentService2: NonExistent2 by di  
                // private val nonExistentService3: NonExistent3 by di
                /* private val nonExistentService4: NonExistent4 by di */
                /*
                 * private val nonExistentService5: NonExistent5 by di
                 * private val nonExistentService6: NonExistent6 by di
                 */
                
                // Only this dependency is real and should be detected
                private val realService: RealService by di
            }
            
            @Provides
            class RealService
            
            // Scenario 2: Provider with commented ambiguous providers
            interface CommonInterface
            
            @Provides(CommonInterface::class)
            class ActiveProvider : CommonInterface
            
            // This would have caused "ambiguous provider" false positive before Phase 2
            // @Provides(CommonInterface::class)
            class CommentedProvider : CommonInterface
            
            @Provides
            class InterfaceConsumer {
                private val service: CommonInterface by di
            }
        """.trimIndent()
        
        myFixture.configureByText("AccuracyValidation.kt", accuracyValidationContent)
        
        val components = sourceAnalyzer.analyzeProject()
        
        // Validate accurate dependency detection
        val serviceWithComments = components.find { it.className == "ServiceWithManyComments" }
        assertNotNull("ServiceWithManyComments should be found", serviceWithComments)
        assertEquals("Should detect only 1 real dependency", 1, serviceWithComments!!.dependencies.size)
        assertEquals("Should depend on RealService", "RealService", serviceWithComments.dependencies.first().targetType)
        
        // Validate accurate provider detection  
        val activeProvider = components.find { it.className == "ActiveProvider" }
        val commentedProvider = components.find { it.className == "CommentedProvider" }
        
        assertNotNull("ActiveProvider should be found", activeProvider)
        assertEquals("ActiveProvider should have 1 provider", 1, activeProvider!!.providers.size)
        assertEquals("Should provide CommonInterface", "CommonInterface", activeProvider.providers.first().returnType)
        
        // CommentedProvider should not have any providers
        if (commentedProvider != null) {
            assertTrue("CommentedProvider should have no providers", commentedProvider.providers.isEmpty())
        }
        
        // Validate that consumer has resolvable dependency  
        val consumer = components.find { it.className == "InterfaceConsumer" }
        assertNotNull("InterfaceConsumer should be found", consumer)
        assertEquals("Consumer should have 1 dependency", 1, consumer!!.dependencies.size)
        assertEquals("Should depend on CommonInterface", "CommonInterface", consumer.dependencies.first().targetType)
        
        // Count potential false positives that were avoided
        val potentialFalsePositives = 6 // NonExistent1-6 dependencies
        val actualDependencies = serviceWithComments.dependencies.size
        val falsePositivesAvoided = potentialFalsePositives - (actualDependencies - 1) // -1 for RealService
        
        println("✅ Accuracy validation with comment scenarios:")
        println("  - Potential false positives avoided: $falsePositivesAvoided")
        println("  - Actual dependencies detected: $actualDependencies")
        println("  - Active providers: ${activeProvider.providers.size}")  
        println("  - Commented providers ignored: 1")
    }

    /**
     * Phase 5 Integration: Performance Impact of Comment Detection
     */
    @Test
    fun testCommentDetectionPerformance() {
        // Create a large file with many comments to test performance impact
        val largeFileContent = StringBuilder().apply {
            appendLine("package com.example.performance")
            appendLine()
            appendLine("import knit.Provides")
            appendLine("import knit.di")
            appendLine()
            appendLine("@Provides")
            appendLine("class LargeCommentedService {")
            
            // Add many commented dependencies
            for (i in 1..100) {
                appendLine("    // private val commentedDep$i: Service$i by di")
                if (i % 10 == 0) {
                    appendLine("    /* private val blockCommentedDep$i: BlockService$i by di */")
                }
                if (i % 20 == 0) {
                    appendLine("    /*")
                    appendLine("     * private val multiLineCommentedDep$i: MultiService$i by di")
                    appendLine("     */")
                }
            }
            
            // Add a few real dependencies
            appendLine("    private val realDep1: RealService1 by di")
            appendLine("    private val realDep2: RealService2 by di")
            appendLine("}")
            appendLine()
            appendLine("@Provides")
            appendLine("class RealService1")
            appendLine()
            appendLine("@Provides") 
            appendLine("class RealService2")
        }.toString()
        
        myFixture.configureByText("LargeCommented.kt", largeFileContent)
        
        val startTime = System.currentTimeMillis()
        val components = sourceAnalyzer.analyzeProject()
        val analysisTime = System.currentTimeMillis() - startTime
        
        val largeService = components.find { it.className == "LargeCommentedService" }
        assertNotNull("LargeCommentedService should be found", largeService)
        assertEquals("Should detect only 2 real dependencies", 2, largeService!!.dependencies.size)
        
        val dependencyTypes = largeService.dependencies.map { it.targetType }.sorted()
        assertEquals("Should detect RealService1", "RealService1", dependencyTypes[0])
        assertEquals("Should detect RealService2", "RealService2", dependencyTypes[1])
        
        // Performance assertion - comment detection should not significantly impact analysis time
        assertTrue("Comment detection should not significantly slow down analysis (was ${analysisTime}ms)", 
                  analysisTime < 1000)
        
        println("✅ Comment detection performance validation:")
        println("  - Analysis time: ${analysisTime}ms")
        println("  - Commented lines processed: ~200")
        println("  - Real dependencies detected: 2")
        println("  - Performance impact: minimal")
    }

    /**
     * Comprehensive Phase 1-5 Integration Test
     */
    @Test
    fun testComprehensiveCommentHandlingIntegration() {
        // Integration test covering all phases with comment-related scenarios
        val comprehensiveContent = """
            package com.example.comprehensive
            
            import knit.Provides
            import knit.di
            
            // Phase 1: Source parsing with various comment styles  
            @Provides
            class ComprehensiveService {
                // Standard commented dependency
                // private val commented1: String by di
                
                /* Block commented dependency */
                // private val commented2: Int by di
                
                /*
                 * Multi-line commented dependency
                 * private val commented3: Double by di
                 */
                
                // Mixed comment styles
                /* // private val commented4: Boolean by di */
                // /* private val commented5: Char by di */
                
                // Active dependencies that should be detected
                private val activeDep1: ActiveService1 by di
                private val activeDep2: ActiveService2 by di
            }
            
            // Phase 2: Provider detection with commented annotations
            interface SharedInterface
            
            @Provides(SharedInterface::class)
            class ActiveInterfaceProvider : SharedInterface
            
            // Commented provider that should be ignored  
            // @Provides(SharedInterface::class)
            class CommentedInterfaceProvider : SharedInterface
            
            @Provides
            class MultiMethodProvider {
                // Commented provider methods
                // @Provides
                // fun provideCommented(): String = "commented"
                
                /* @Provides
                   fun provideBlockCommented(): String = "block" */
                
                // Active provider methods
                @Provides
                fun provideActive(): String = "active"
            }
            
            // Phase 3: Components that consume the interfaces
            @Provides
            class InterfaceConsumer {
                private val shared: SharedInterface by di
                private val value: String by di
            }
            
            // Phase 4: Components that would cause circular dependency
            @Provides
            class CircularA {
                private val circularB: CircularB by di
                // private val commentedCircular: CircularC by di // Should not affect cycle detection
            }
            
            @Provides
            class CircularB {
                private val circularA: CircularA by di
            }
            
            // Supporting services
            @Provides
            class ActiveService1
            
            @Provides
            class ActiveService2
        """.trimIndent()
        
        myFixture.configureByText("Comprehensive.kt", comprehensiveContent)
        
        val components = sourceAnalyzer.analyzeProject()
        
        // Phase 1 validation: Comment parsing
        val comprehensiveService = components.find { it.className == "ComprehensiveService" }
        assertNotNull("ComprehensiveService should be found", comprehensiveService)
        assertEquals("Should detect 2 active dependencies", 2, comprehensiveService!!.dependencies.size)
        
        // Phase 2 validation: Provider detection
        val activeProvider = components.find { it.className == "ActiveInterfaceProvider" }
        val commentedProvider = components.find { it.className == "CommentedInterfaceProvider" }
        val multiProvider = components.find { it.className == "MultiMethodProvider" }
        
        assertNotNull("ActiveInterfaceProvider should be found", activeProvider)
        assertEquals("Should have 1 provider", 1, activeProvider!!.providers.size)
        
        if (commentedProvider != null) {
            assertTrue("CommentedInterfaceProvider should have no providers", commentedProvider.providers.isEmpty())
        }
        
        assertNotNull("MultiMethodProvider should be found", multiProvider)
        assertEquals("Should have 1 active provider method", 1, multiProvider!!.providers.size)
        
        // Phase 3 validation: Dependency resolution
        val consumer = components.find { it.className == "InterfaceConsumer" }
        assertNotNull("InterfaceConsumer should be found", consumer)
        assertEquals("Should have 2 dependencies", 2, consumer!!.dependencies.size)
        
        // Phase 4 validation: Circular dependency detection
        val circularA = components.find { it.className == "CircularA" }
        val circularB = components.find { it.className == "CircularB" }
        
        assertNotNull("CircularA should be found", circularA)
        assertNotNull("CircularB should be found", circularB)
        assertEquals("CircularA should have 1 dependency", 1, circularA!!.dependencies.size)
        assertEquals("CircularB should have 1 dependency", 1, circularB!!.dependencies.size)
        
        // Validate circular dependency structure
        assertEquals("CircularA should depend on CircularB", "CircularB", circularA.dependencies.first().targetType)
        assertEquals("CircularB should depend on CircularA", "CircularA", circularB.dependencies.first().targetType)
        
        println("✅ Comprehensive comment handling integration test passed:")
        println("  - Phase 1 (Parsing): Active dependencies detected correctly")
        println("  - Phase 2 (Providers): Commented providers ignored") 
        println("  - Phase 3 (Classification): Dependencies resolved properly")
        println("  - Phase 4 (Accuracy): Circular dependency detected accurately")
        println("  - Phase 5 (Validation): All integration points working")
    }
}