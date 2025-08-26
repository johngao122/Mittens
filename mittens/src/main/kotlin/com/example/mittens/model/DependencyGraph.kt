package com.example.mittens.model

data class DependencyGraph(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>
) {
    fun findNode(componentName: String): GraphNode? {
        return nodes.find { it.id == componentName }
    }
    
    fun getConnectedNodes(nodeId: String): List<GraphNode> {
        val connectedIds = edges
            .filter { it.from == nodeId || it.to == nodeId }
            .flatMap { listOf(it.from, it.to) }
            .distinct()
            .filter { it != nodeId }
            
        return nodes.filter { it.id in connectedIds }
    }
    
    fun hasCycles(): Boolean {
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        
        for (node in nodes) {
            if (node.id !in visited) {
                if (hasCycleDFS(node.id, visited, recursionStack)) {
                    return true
                }
            }
        }
        return false
    }
    
    private fun hasCycleDFS(nodeId: String, visited: MutableSet<String>, recursionStack: MutableSet<String>): Boolean {
        visited.add(nodeId)
        recursionStack.add(nodeId)
        
        val outgoingEdges = edges.filter { it.from == nodeId }
        for (edge in outgoingEdges) {
            if (edge.to !in visited) {
                if (hasCycleDFS(edge.to, visited, recursionStack)) {
                    return true
                }
            } else if (edge.to in recursionStack) {
                return true
            }
        }
        
        recursionStack.remove(nodeId)
        return false
    }
}

data class GraphNode(
    val id: String,
    val label: String,
    val type: NodeType,
    val packageName: String,
    val issues: List<KnitIssue> = emptyList(),
    val metadata: Map<String, Any> = emptyMap()
)

data class GraphEdge(
    val from: String,
    val to: String,
    val type: EdgeType,
    val label: String? = null,
    val metadata: Map<String, Any> = emptyMap()
)

enum class NodeType {
    COMPONENT,
    PROVIDER, 
    INTERFACE
}

enum class EdgeType {
    DEPENDENCY,
    PROVIDES,
    SINGLETON,
    NAMED,
    FACTORY,
    COLLECTION
}