package com.example.mittens.services

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

/**
 * Core tests for Phase 1 implementation: Comment Detection for False Positive Elimination
 * These tests validate the specific scenarios mentioned in ANALYSIS_ACCURACY_INVESTIGATION.md
 */
@TestDataPath("\$CONTENT_ROOT/testData")
class Phase1CommentDetectionTest : BasePlatformTestCase() {
    
    private lateinit var sourceAnalyzer: KnitSourceAnalyzer
    
    override fun setUp() {
        super.setUp()
        sourceAnalyzer = KnitSourceAnalyzer(project)
    }
    
    @Test
    fun testPhase1Requirement_CommentedDependencyIgnored() {
        // Phase 1 Requirement: Commented 'by di' dependencies should be ignored
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
        
        println("✅ Phase 1: Commented dependency properly ignored in PaymentService")
    }
    
    @Test  
    fun testPhase1Requirement_CommentedProviderIgnored() {
        // Phase 1 Requirement: Commented @Provides annotations should be ignored
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
            println("✅ Phase 1: Commented provider properly ignored in InMemoryUserRepository")
        } else {
            println("✅ Phase 1: InMemoryUserRepository not detected as component (no active annotations)")
        }
    }
    
    @Test
    fun testPhase1Requirement_ActiveCodeStillDetected() {
        // Phase 1 Requirement: Active (non-commented) code should still be detected
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
        
        println("✅ Phase 1: Active dependencies and providers properly detected")
    }
    
    @Test
    fun testPhase1Requirement_InvestigationScenarios() {
        // Phase 1 Requirement: Validate the specific scenarios from ANALYSIS_ACCURACY_INVESTIGATION.md
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
        
        println("✅ Phase 1: Real circular dependency properly detected (not eliminated)")
        println("✅ Phase 1: Comment detection doesn't affect valid dependency detection")
    }
    
    @Test
    fun testPhase1Requirement_AccuracyImprovement() {
        // Phase 1 Requirement: Demonstrate accuracy improvement
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
        
        println("✅ Phase 1: Mixed scenario - only active code detected, commented code ignored")
        println("✅ Phase 1: False positive elimination demonstrated")
    }
}