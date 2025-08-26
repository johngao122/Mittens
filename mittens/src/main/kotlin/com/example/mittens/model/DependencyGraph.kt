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

    /**
     * Find all cycles in the dependency graph with detailed path information
     * Returns a list of cycles, where each cycle is a list of component IDs forming a closed loop
     */
    fun findCycles(): List<List<String>> {
        val allCycles = mutableListOf<List<String>>()
        val visited = mutableSetOf<String>()
        val recursionStack = mutableListOf<String>()

        for (node in nodes) {
            if (node.id !in visited) {
                findCyclesDFS(node.id, visited, recursionStack, allCycles)
            }
        }

        return allCycles.distinctBy { it.sorted() }
    }

    /**
     * Get strongly connected components using Tarjan's algorithm
     * Returns a list of components, where each component is a list of node IDs
     */
    fun getStronglyConnectedComponents(): List<List<String>> {
        val index = mutableMapOf<String, Int>()
        val lowLink = mutableMapOf<String, Int>()
        val onStack = mutableSetOf<String>()
        val stack = mutableListOf<String>()
        val components = mutableListOf<List<String>>()
        var currentIndex = 0

        fun tarjanDFS(nodeId: String) {
            index[nodeId] = currentIndex
            lowLink[nodeId] = currentIndex
            currentIndex++
            stack.add(nodeId)
            onStack.add(nodeId)

            val outgoingEdges = edges.filter { it.from == nodeId }
            for (edge in outgoingEdges) {
                val neighbor = edge.to
                if (neighbor !in index) {

                    tarjanDFS(neighbor)
                    lowLink[nodeId] = minOf(lowLink[nodeId]!!, lowLink[neighbor]!!)
                } else if (neighbor in onStack) {

                    lowLink[nodeId] = minOf(lowLink[nodeId]!!, index[neighbor]!!)
                }
            }


            if (lowLink[nodeId] == index[nodeId]) {
                val component = mutableListOf<String>()
                do {
                    val w = stack.removeLastOrNull() ?: break
                    onStack.remove(w)
                    component.add(w)
                } while (w != nodeId)

                if (component.size > 1 || hasEdgeToSelf(nodeId)) {
                    components.add(component)
                }
            }
        }

        for (node in nodes) {
            if (node.id !in index) {
                tarjanDFS(node.id)
            }
        }

        return components
    }

    /**
     * Get a detailed cycle report with component information
     */
    fun getCycleReport(): CycleReport {
        val cycles = findCycles()
        val stronglyConnectedComponents = getStronglyConnectedComponents()

        val detailedCycles = cycles.map { cycle ->
            val cycleNodes = cycle.mapNotNull { nodeId -> findNode(nodeId) }
            val cycleEdges = mutableListOf<GraphEdge>()

            for (i in cycle.indices) {
                val from = cycle[i]
                val to = cycle[(i + 1) % cycle.size]
                edges.find { it.from == from && it.to == to }?.let { cycleEdges.add(it) }
            }

            CycleInfo(
                path = cycle,
                nodes = cycleNodes,
                edges = cycleEdges,
                length = cycle.size
            )
        }

        return CycleReport(
            hasCycles = cycles.isNotEmpty(),
            cycleCount = cycles.size,
            cycles = detailedCycles,
            stronglyConnectedComponents = stronglyConnectedComponents
        )
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

    private fun findCyclesDFS(
        nodeId: String,
        visited: MutableSet<String>,
        recursionStack: MutableList<String>,
        allCycles: MutableList<List<String>>
    ) {
        visited.add(nodeId)
        recursionStack.add(nodeId)

        val outgoingEdges = edges.filter { it.from == nodeId }
        for (edge in outgoingEdges) {
            val neighbor = edge.to
            if (neighbor !in visited) {
                findCyclesDFS(neighbor, visited, recursionStack, allCycles)
            } else if (neighbor in recursionStack) {

                val cycleStart = recursionStack.indexOf(neighbor)
                val cycle = recursionStack.subList(cycleStart, recursionStack.size).toList()
                allCycles.add(cycle)
            }
        }

        recursionStack.remove(nodeId)
    }

    private fun hasEdgeToSelf(nodeId: String): Boolean {
        return edges.any { it.from == nodeId && it.to == nodeId }
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

/**
 * Detailed information about a cycle in the dependency graph
 */
data class CycleInfo(
    val path: List<String>,
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val length: Int
) {
    fun getDisplayPath(): String {
        return path.joinToString(" → ") + " → ${path.first()}"
    }
}

/**
 * Comprehensive report about cycles in the dependency graph
 */
data class CycleReport(
    val hasCycles: Boolean,
    val cycleCount: Int,
    val cycles: List<CycleInfo>,
    val stronglyConnectedComponents: List<List<String>>
) {
    fun getShortestCycle(): CycleInfo? = cycles.minByOrNull { it.length }
    fun getLongestCycle(): CycleInfo? = cycles.maxByOrNull { it.length }
    fun getTotalNodesInCycles(): Int = cycles.flatMap { it.path }.distinct().size
}