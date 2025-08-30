package com.example.mittens.issues

import com.example.mittens.model.*
import com.example.mittens.services.AdvancedIssueDetector
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

/**
 * Advanced tests for circular dependency detection with complex scenarios
 * Tests the enhanced DFS-based cycle detection with path reconstruction
 */
class CircularDependencyAdvancedTest : BasePlatformTestCase() {
    
    private lateinit var advancedDetector: AdvancedIssueDetector
    
    override fun setUp() {
        super.setUp()
        advancedDetector = AdvancedIssueDetector(project)
    }
    
    @Test
    fun testSimpleCircularDependency() {
        // A -> B -> A
        val componentA = createTestComponent("A", "com.test", 
            dependencies = listOf(createTestDependency("B", "B"))
        )
        val componentB = createTestComponent("B", "com.test",
            dependencies = listOf(createTestDependency("A", "A"))
        )
        
        val components = listOf(componentA, componentB)
        val graph = createTestGraph(components)
        
        val issues = advancedDetector.detectAdvancedCircularDependencies(graph)
        
        assertEquals("Should detect 1 circular dependency", 1, issues.size)
        val issue = issues.first()
        assertEquals(IssueType.CIRCULAR_DEPENDENCY, issue.type)
        assertEquals(Severity.ERROR, issue.severity)
        assertTrue("Message should contain cycle path", issue.message.contains("→"))
        assertNotNull("Should have metadata", issue.metadata)
        
        val cycleLength = issue.metadata["cycleLength"] as? Int
        assertEquals("Cycle length should be 2", 2, cycleLength)
    }
    
    @Test
    fun testComplexThreeComponentCycle() {
        // A -> B -> C -> A
        val componentA = createTestComponent("A", "com.test",
            dependencies = listOf(createTestDependency("B", "B"))
        )
        val componentB = createTestComponent("B", "com.test",
            dependencies = listOf(createTestDependency("C", "C"))
        )
        val componentC = createTestComponent("C", "com.test",
            dependencies = listOf(createTestDependency("A", "A"))
        )
        
        val components = listOf(componentA, componentB, componentC)
        val graph = createTestGraph(components)
        
        val issues = advancedDetector.detectAdvancedCircularDependencies(graph)
        
        assertTrue("Should detect at least 1 circular dependency", issues.isNotEmpty())
        val cycleIssue = issues.first { it.type == IssueType.CIRCULAR_DEPENDENCY }
        
        val cycleLength = cycleIssue.metadata["cycleLength"] as? Int
        assertEquals("Cycle length should be 3", 3, cycleLength)
        
        val cyclePath = cycleIssue.metadata["cyclePath"] as? List<*>
        assertNotNull("Should have cycle path", cyclePath)
        assertEquals("Path should have 3 elements", 3, cyclePath?.size)
    }
    
    @Test
    fun testSelfReferentialDependency() {
        // A -> A (self loop)
        val componentA = createTestComponent("A", "com.test",
            dependencies = listOf(createTestDependency("A", "A"))
        )
        
        val components = listOf(componentA)
        val graph = createTestGraphWithSelfLoop(componentA)
        
        val issues = advancedDetector.detectAdvancedCircularDependencies(graph)
        
        assertTrue("Should detect self-referential dependency", issues.isNotEmpty())
        val issue = issues.first()
        assertEquals(IssueType.CIRCULAR_DEPENDENCY, issue.type)
        
        val cycleLength = issue.metadata["cycleLength"] as? Int
        assertEquals("Self-loop should have length 1", 1, cycleLength)
    }
    
    @Test
    fun testMultipleIndependentCycles() {
        // Cycle 1: A -> B -> A
        // Cycle 2: C -> D -> C
        // E -> F (no cycle)
        val componentA = createTestComponent("A", "com.test",
            dependencies = listOf(createTestDependency("B", "B"))
        )
        val componentB = createTestComponent("B", "com.test",
            dependencies = listOf(createTestDependency("A", "A"))
        )
        val componentC = createTestComponent("C", "com.test",
            dependencies = listOf(createTestDependency("D", "D"))
        )
        val componentD = createTestComponent("D", "com.test",
            dependencies = listOf(createTestDependency("C", "C"))
        )
        val componentE = createTestComponent("E", "com.test",
            dependencies = listOf(createTestDependency("F", "F"))
        )
        val componentF = createTestComponent("F", "com.test")
        
        val components = listOf(componentA, componentB, componentC, componentD, componentE, componentF)
        val graph = createTestGraph(components)
        
        val issues = advancedDetector.detectAdvancedCircularDependencies(graph)
        
        val cycleIssues = issues.filter { it.type == IssueType.CIRCULAR_DEPENDENCY }
        assertTrue("Should detect multiple cycles", cycleIssues.size >= 2)
        
        // Verify both cycles are detected
        val cycleLengths = cycleIssues.mapNotNull { it.metadata["cycleLength"] as? Int }
        assertTrue("Should have cycles of length 2", cycleLengths.contains(2))
    }
    
    @Test
    fun testStronglyConnectedComponents() {
        // Complex graph with strongly connected components
        // A -> B -> C -> A (SCC 1)
        // D -> E -> F -> D (SCC 2)  
        // G -> H (no cycle)
        val componentA = createTestComponent("A", "com.test",
            dependencies = listOf(createTestDependency("B", "B"))
        )
        val componentB = createTestComponent("B", "com.test",
            dependencies = listOf(createTestDependency("C", "C"))
        )
        val componentC = createTestComponent("C", "com.test",
            dependencies = listOf(createTestDependency("A", "A"))
        )
        val componentD = createTestComponent("D", "com.test",
            dependencies = listOf(createTestDependency("E", "E"))
        )
        val componentE = createTestComponent("E", "com.test",
            dependencies = listOf(createTestDependency("F", "F"))
        )
        val componentF = createTestComponent("F", "com.test",
            dependencies = listOf(createTestDependency("D", "D"))
        )
        val componentG = createTestComponent("G", "com.test",
            dependencies = listOf(createTestDependency("H", "H"))
        )
        val componentH = createTestComponent("H", "com.test")
        
        val components = listOf(componentA, componentB, componentC, componentD, componentE, componentF, componentG, componentH)
        val graph = createTestGraph(components)
        
        // Test the strongly connected components detection
        val stronglyConnectedComponents = graph.getStronglyConnectedComponents()
        assertTrue("Should find strongly connected components", stronglyConnectedComponents.isNotEmpty())
        
        // Each SCC should have 3 components
        val sccSizes = stronglyConnectedComponents.map { it.size }
        assertTrue("Should have SCCs of size 3", sccSizes.contains(3))
        
        val issues = advancedDetector.detectAdvancedCircularDependencies(graph)
        val sccIssues = issues.filter { it.message.contains("strongly connected") }
        assertTrue("Should detect strongly connected components", sccIssues.isNotEmpty())
    }
    
    @Test
    fun testCyclePathReconstruction() {
        // A -> B -> C -> A
        val componentA = createTestComponent("A", "com.test",
            dependencies = listOf(createTestDependency("B", "B"))
        )
        val componentB = createTestComponent("B", "com.test",
            dependencies = listOf(createTestDependency("C", "C"))
        )
        val componentC = createTestComponent("C", "com.test",
            dependencies = listOf(createTestDependency("A", "A"))
        )
        
        val components = listOf(componentA, componentB, componentC)
        val graph = createTestGraph(components)
        
        val cycleReport = graph.getCycleReport()
        
        assertTrue("Should have cycles", cycleReport.hasCycles)
        assertEquals("Should have 1 cycle", 1, cycleReport.cycleCount)
        
        val cycle = cycleReport.cycles.first()
        assertEquals("Cycle should have length 3", 3, cycle.length)
        assertTrue("Display path should contain arrows", cycle.getDisplayPath().contains("→"))
        
        // Verify nodes and edges are properly associated
        assertEquals("Should have 3 nodes", 3, cycle.nodes.size)
        assertTrue("Should have edges", cycle.edges.isNotEmpty())
    }
    
    @Test
    fun testNoFalsePositivesInAcyclicGraph() {
        // Create a complex but acyclic graph
        // A -> B, A -> C, B -> D, C -> D, D -> E
        val componentA = createTestComponent("A", "com.test",
            dependencies = listOf(
                createTestDependency("B", "B"),
                createTestDependency("C", "C")
            )
        )
        val componentB = createTestComponent("B", "com.test",
            dependencies = listOf(createTestDependency("D", "D"))
        )
        val componentC = createTestComponent("C", "com.test",
            dependencies = listOf(createTestDependency("D", "D"))
        )
        val componentD = createTestComponent("D", "com.test",
            dependencies = listOf(createTestDependency("E", "E"))
        )
        val componentE = createTestComponent("E", "com.test")
        
        val components = listOf(componentA, componentB, componentC, componentD, componentE)
        val graph = createTestGraph(components)
        
        assertFalse("Graph should not have cycles", graph.hasCycles())
        
        val cycleReport = graph.getCycleReport()
        assertFalse("Cycle report should show no cycles", cycleReport.hasCycles)
        assertEquals("Should have 0 cycles", 0, cycleReport.cycleCount)
        
        val issues = advancedDetector.detectAdvancedCircularDependencies(graph)
        val cycleIssues = issues.filter { it.type == IssueType.CIRCULAR_DEPENDENCY }
        assertTrue("Should not detect any circular dependencies", cycleIssues.isEmpty())
    }
    
    @Test
    fun testPerformanceWithLargeGraph() {
        // Create a larger graph to test performance
        val components = mutableListOf<KnitComponent>()
        val componentCount = 50
        
        // Create chain: 0 -> 1 -> 2 -> ... -> 49 -> 0 (one large cycle)
        for (i in 0 until componentCount) {
            val nextIndex = (i + 1) % componentCount
            val component = createTestComponent("Component$i", "com.test.large",
                dependencies = listOf(createTestDependency("Component$nextIndex", "Component$nextIndex"))
            )
            components.add(component)
        }
        
        val graph = createTestGraph(components)
        
        val startTime = System.currentTimeMillis()
        val issues = advancedDetector.detectAdvancedCircularDependencies(graph)
        val detectionTime = System.currentTimeMillis() - startTime
        
        // Performance assertion - should complete in reasonable time
        assertTrue("Detection should complete quickly (< 1000ms)", detectionTime < 1000)
        
        // Correctness assertion
        assertTrue("Should detect the large cycle", issues.isNotEmpty())
        val cycleIssue = issues.first { it.type == IssueType.CIRCULAR_DEPENDENCY }
        val cycleLength = cycleIssue.metadata["cycleLength"] as? Int
        assertEquals("Should detect full cycle", componentCount, cycleLength)
    }
    
    // Helper methods
    
    private fun createTestComponent(
        name: String, 
        packageName: String, 
        dependencies: List<KnitDependency> = emptyList(),
        providers: List<KnitProvider> = emptyList()
    ): KnitComponent {
        return KnitComponent(
            className = name,
            packageName = packageName,
            type = ComponentType.COMPONENT,
            dependencies = dependencies,
            providers = providers,
            sourceFile = "$name.kt"
        )
    }
    
    private fun createTestDependency(propertyName: String, targetType: String): KnitDependency {
        return KnitDependency(
            propertyName = propertyName,
            targetType = targetType,
            isNamed = false,
            namedQualifier = null,
            isSingleton = false,
            isFactory = false,
            isLoadable = false
        )
    }
    
    private fun createTestGraph(components: List<KnitComponent>): DependencyGraph {
        val nodes = components.map { component ->
            GraphNode(
                id = "${component.packageName}.${component.className}",
                label = component.className,
                type = NodeType.COMPONENT,
                packageName = component.packageName
            )
        }
        
        val edges = mutableListOf<GraphEdge>()
        components.forEach { component ->
            val fromId = "${component.packageName}.${component.className}"
            component.dependencies.forEach { dependency ->
                val toComponent = components.find { it.className == dependency.targetType }
                if (toComponent != null) {
                    val toId = "${toComponent.packageName}.${toComponent.className}"
                    edges.add(GraphEdge(
                        from = fromId,
                        to = toId,
                        type = EdgeType.DEPENDENCY,
                        label = dependency.propertyName
                    ))
                }
            }
        }
        
        return DependencyGraph(nodes, edges)
    }
    
    private fun createTestGraphWithSelfLoop(component: KnitComponent): DependencyGraph {
        val nodeId = "${component.packageName}.${component.className}"
        val node = GraphNode(
            id = nodeId,
            label = component.className,
            type = NodeType.COMPONENT,
            packageName = component.packageName
        )
        
        val selfEdge = GraphEdge(
            from = nodeId,
            to = nodeId,
            type = EdgeType.DEPENDENCY,
            label = "self"
        )
        
        return DependencyGraph(listOf(node), listOf(selfEdge))
    }
}