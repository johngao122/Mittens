package com.example.mittens.model

import org.junit.Test
import org.junit.Assert.*

class DependencyGraphTest {
    
    @Test
    fun testDependencyGraphCreation() {
        val graph = DependencyGraph(emptyList(), emptyList())
        
        assertTrue("New graph should have no edges", graph.edges.isEmpty())
        assertTrue("New graph should have no nodes", graph.nodes.isEmpty())
        assertFalse("New graph should not have cycles", graph.hasCycles())
    }
    
    @Test
    fun testDependencyGraphWithNodes() {
        val nodes = listOf(
            GraphNode("ComponentA", "ComponentA", NodeType.COMPONENT, "com.test"),
            GraphNode("ComponentB", "ComponentB", NodeType.COMPONENT, "com.test")
        )
        val edges = listOf(
            GraphEdge("ComponentA", "ComponentB", EdgeType.DEPENDENCY)
        )
        val graph = DependencyGraph(nodes, edges)
        
        assertEquals("Graph should have 1 edge", 1, graph.edges.size)
        assertEquals("Graph should have 2 nodes", 2, graph.nodes.size)
    }
    
    @Test
    fun testFindNode() {
        val nodes = listOf(
            GraphNode("ComponentA", "ComponentA", NodeType.COMPONENT, "com.test"),
            GraphNode("ComponentB", "ComponentB", NodeType.COMPONENT, "com.test")
        )
        val graph = DependencyGraph(nodes, emptyList())
        
        val foundNode = graph.findNode("ComponentA")
        val notFoundNode = graph.findNode("ComponentC")
        
        assertNotNull("Should find ComponentA", foundNode)
        assertEquals("Found node should have correct id", "ComponentA", foundNode?.id)
        assertNull("Should not find ComponentC", notFoundNode)
    }
    
    @Test
    fun testGetConnectedNodes() {
        val nodes = listOf(
            GraphNode("A", "A", NodeType.COMPONENT, "com.test"),
            GraphNode("B", "B", NodeType.COMPONENT, "com.test"),
            GraphNode("C", "C", NodeType.COMPONENT, "com.test"),
            GraphNode("D", "D", NodeType.COMPONENT, "com.test")
        )
        val edges = listOf(
            GraphEdge("A", "B", EdgeType.DEPENDENCY),
            GraphEdge("A", "C", EdgeType.DEPENDENCY),
            GraphEdge("B", "D", EdgeType.DEPENDENCY)
        )
        val graph = DependencyGraph(nodes, edges)
        
        val aConnectedNodes = graph.getConnectedNodes("A")
        val bConnectedNodes = graph.getConnectedNodes("B")
        val dConnectedNodes = graph.getConnectedNodes("D")
        
        assertEquals("A should have 2 connected nodes", 2, aConnectedNodes.size)
        assertTrue("A should be connected to B", aConnectedNodes.any { it.id == "B" })
        assertTrue("A should be connected to C", aConnectedNodes.any { it.id == "C" })
        
        assertEquals("B should have 2 connected nodes", 2, bConnectedNodes.size)
        assertTrue("B should be connected to A", bConnectedNodes.any { it.id == "A" })
        assertTrue("B should be connected to D", bConnectedNodes.any { it.id == "D" })
        
        assertEquals("D should have 1 connected node", 1, dConnectedNodes.size)
        assertTrue("D should be connected to B", dConnectedNodes.any { it.id == "B" })
    }
    
    @Test
    fun testNoCycleInLinearGraph() {
        val nodes = listOf(
            GraphNode("A", "A", NodeType.COMPONENT, "com.test"),
            GraphNode("B", "B", NodeType.COMPONENT, "com.test"),
            GraphNode("C", "C", NodeType.COMPONENT, "com.test"),
            GraphNode("D", "D", NodeType.COMPONENT, "com.test")
        )
        val edges = listOf(
            GraphEdge("A", "B", EdgeType.DEPENDENCY),
            GraphEdge("B", "C", EdgeType.DEPENDENCY),
            GraphEdge("C", "D", EdgeType.DEPENDENCY)
        )
        val graph = DependencyGraph(nodes, edges)
        
        assertFalse("Linear dependency chain should not have cycles", graph.hasCycles())
    }
    
    @Test
    fun testCycleDetection() {
        val nodes = listOf(
            GraphNode("A", "A", NodeType.COMPONENT, "com.test"),
            GraphNode("B", "B", NodeType.COMPONENT, "com.test"),
            GraphNode("C", "C", NodeType.COMPONENT, "com.test")
        )
        val edges = listOf(
            GraphEdge("A", "B", EdgeType.DEPENDENCY),
            GraphEdge("B", "C", EdgeType.DEPENDENCY),
            GraphEdge("C", "A", EdgeType.DEPENDENCY)
        )
        val graph = DependencyGraph(nodes, edges)
        
        assertTrue("Graph should detect cycles", graph.hasCycles())
    }
    
    @Test
    fun testNoCycleInComplexGraph() {
        val nodes = listOf(
            GraphNode("A", "A", NodeType.COMPONENT, "com.test"),
            GraphNode("B", "B", NodeType.COMPONENT, "com.test"),
            GraphNode("C", "C", NodeType.COMPONENT, "com.test"),
            GraphNode("D", "D", NodeType.COMPONENT, "com.test"),
            GraphNode("E", "E", NodeType.COMPONENT, "com.test")
        )
        val edges = listOf(
            GraphEdge("A", "B", EdgeType.DEPENDENCY),
            GraphEdge("A", "C", EdgeType.DEPENDENCY),
            GraphEdge("B", "D", EdgeType.DEPENDENCY),
            GraphEdge("C", "D", EdgeType.DEPENDENCY),
            GraphEdge("D", "E", EdgeType.DEPENDENCY)
        )
        val graph = DependencyGraph(nodes, edges)
        
        assertFalse("Complex acyclic graph should not have cycles", graph.hasCycles())
    }
    
    @Test
    fun testSelfLoop() {
        val nodes = listOf(
            GraphNode("A", "A", NodeType.COMPONENT, "com.test")
        )
        val edges = listOf(
            GraphEdge("A", "A", EdgeType.DEPENDENCY)
        )
        val graph = DependencyGraph(nodes, edges)
        
        assertTrue("Self-loop should be detected as cycle", graph.hasCycles())
    }
    
    @Test
    fun testGraphNodeCreation() {
        val node = GraphNode("TestComponent", "Test Component", NodeType.COMPONENT, "com.test")
        
        assertEquals("Node ID should match", "TestComponent", node.id)
        assertEquals("Node label should match", "Test Component", node.label)
        assertEquals("Node type should match", NodeType.COMPONENT, node.type)
        assertEquals("Node package should match", "com.test", node.packageName)
        assertTrue("Node issues should be empty by default", node.issues.isEmpty())
        assertTrue("Node metadata should be empty by default", node.metadata.isEmpty())
    }
    
    @Test
    fun testGraphEdgeCreation() {
        val edge = GraphEdge("ComponentA", "ComponentB", EdgeType.DEPENDENCY, "uses")
        
        assertEquals("Edge from should match", "ComponentA", edge.from)
        assertEquals("Edge to should match", "ComponentB", edge.to)
        assertEquals("Edge type should match", EdgeType.DEPENDENCY, edge.type)
        assertEquals("Edge label should match", "uses", edge.label)
        assertTrue("Edge metadata should be empty by default", edge.metadata.isEmpty())
    }
}