package com.example.mittens.services

import com.example.mittens.model.*
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.Test
import org.junit.Assert.*

/**
 * Test to validate that the new filtering logic properly excludes nodes 
 * with 0 dependencies and 0 providers from the dependency graph.
 */
class FilteringValidationTest : LightJavaCodeInsightFixtureTestCase() {

    private lateinit var sourceAnalyzer: KnitSourceAnalyzer
    private lateinit var analysisService: KnitAnalysisService

    override fun setUp() {
        super.setUp()
        sourceAnalyzer = KnitSourceAnalyzer(project)
        analysisService = KnitAnalysisService(project)
    }

    @Test
    fun testEnumConstantsFiltered() {
        // Test that enum constants like PENDING, CONFIRMED are filtered out
        val testContent = """
            package com.example.test
            
            enum class OrderStatus {
                PENDING, 
                CONFIRMED, 
                PROCESSING,
                SHIPPED,
                DELIVERED,
                CANCELLED
            }
            
            data class Order(val id: String, val status: OrderStatus)
            
            interface UserRepository
            
            @Component
            class OrderService {
                private val repository: UserRepository by di
            }
        """.trimIndent()
        
        myFixture.configureByText("TestFile.kt", testContent)
        
        val components = sourceAnalyzer.analyzeProject()
        
        // Should NOT include enum constants
        assertFalse("PENDING should be filtered out", 
                   components.any { it.className == "PENDING" })
        assertFalse("CONFIRMED should be filtered out", 
                   components.any { it.className == "CONFIRMED" })
        assertFalse("OrderStatus enum should be filtered out (no DI relevance)", 
                   components.any { it.className == "OrderStatus" })
        
        // Should NOT include data classes without DI
        assertFalse("Order data class should be filtered out", 
                   components.any { it.className == "Order" })
        
        // Should NOT include interfaces without @Provides methods
        assertFalse("UserRepository interface should be filtered out", 
                   components.any { it.className == "UserRepository" })
        
        // Should include classes with DI annotations and dependencies
        assertTrue("OrderService should be included (has @Component and 'by di')", 
                  components.any { it.className == "OrderService" })
        
        val orderService = components.find { it.className == "OrderService" }
        assertNotNull("OrderService should exist", orderService)
        assertTrue("OrderService should have dependencies", orderService!!.dependencies.isNotEmpty())
        
        println("✅ Filtering test: Components found: ${components.size}")
        components.forEach { component ->
            println("  - ${component.className} (deps: ${component.dependencies.size}, providers: ${component.providers.size})")
        }
    }
    
    @Test
    fun testGraphLevelFiltering() {
        // Test that the graph-level filtering works as a safety net
        
        // Create components manually (simulating what might slip through source analysis)
        val components = listOf(
            KnitComponent(
                className = "ValidComponent",
                packageName = "com.test",
                type = ComponentType.COMPONENT,
                dependencies = listOf(
                    KnitDependency("service", "TestService", false, null, false, false, false)
                ),
                providers = emptyList(),
                sourceFile = "ValidComponent.kt"
            ),
            KnitComponent(
                className = "EmptyComponent", // This should be filtered out
                packageName = "com.test", 
                type = ComponentType.COMPONENT,
                dependencies = emptyList(),
                providers = emptyList(),
                sourceFile = "EmptyComponent.kt"
            ),
            KnitComponent(
                className = "ProviderComponent",
                packageName = "com.test",
                type = ComponentType.PROVIDER,
                dependencies = emptyList(),
                providers = listOf(
                    KnitProvider("provideService", "TestService", null, false, null, false, false, false, false)
                ),
                sourceFile = "ProviderComponent.kt"
            )
        )
        
        val dependencyGraph = analysisService.buildDependencyGraph(components)
        
        // Should include ValidComponent (has dependencies)
        assertTrue("ValidComponent should be in graph", 
                  dependencyGraph.nodes.any { it.label == "ValidComponent" })
        
        // Should include ProviderComponent (has providers) 
        assertTrue("ProviderComponent should be in graph", 
                  dependencyGraph.nodes.any { it.label == "ProviderComponent" })
        
        // Should NOT include EmptyComponent (no dependencies or providers)
        assertFalse("EmptyComponent should be filtered out from graph", 
                   dependencyGraph.nodes.any { it.label == "EmptyComponent" })
        
        println("✅ Graph filtering test: Graph nodes: ${dependencyGraph.nodes.size}")
        dependencyGraph.nodes.forEach { node ->
            println("  - ${node.label}")
        }
    }
    
    @Test 
    fun testProviderMethodNodesOnlyForActualProviders() {
        // Test that provider method nodes are only created for components that actually have providers
        
        val components = listOf(
            KnitComponent(
                className = "ServiceProvider",
                packageName = "com.test",
                type = ComponentType.PROVIDER,
                dependencies = emptyList(),
                providers = listOf(
                    KnitProvider("provideUserService", "UserService", null, false, null, false, false, false, false)
                ),
                sourceFile = "ServiceProvider.kt"
            )
        )
        
        val dependencyGraph = analysisService.buildDependencyGraph(components)
        
        // Should have main component node
        val mainNode = dependencyGraph.nodes.find { it.label == "ServiceProvider" }
        assertNotNull("ServiceProvider main node should exist", mainNode)
        
        // Should have provider method node
        val providerNode = dependencyGraph.nodes.find { it.label.contains("provideUserService") }
        assertNotNull("Provider method node should exist", providerNode)
        
        // Should have edge from main node to provider method node
        val edge = dependencyGraph.edges.find { 
            it.from == mainNode!!.id && it.to == providerNode!!.id && it.type == EdgeType.PROVIDES 
        }
        assertNotNull("Should have PROVIDES edge from main node to provider method", edge)
        
        println("✅ Provider method test: Found ${dependencyGraph.nodes.size} nodes and ${dependencyGraph.edges.size} edges")
    }
}