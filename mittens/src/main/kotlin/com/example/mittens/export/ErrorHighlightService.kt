package com.example.mittens.export

import com.example.mittens.model.*

/**
 * Service for analyzing errors and cycles in the dependency graph to provide
 * error highlighting information for visual rendering
 */
class ErrorHighlightService {
    
    /**
     * Analyze the dependency graph and issues to create error highlighting data
     */
    fun analyzeErrors(dependencyGraph: DependencyGraph, issues: List<KnitIssue>): ErrorAnalysis {
        val cycles = analyzeCycles(dependencyGraph, issues)
        val nodeErrors = mapIssuestoNodes(issues)
        val edgeErrors = mapIssuesToEdges(issues, dependencyGraph)
        
        val cycleNodes = cycles.flatMap { it.nodeIds }.toSet()
        val cycleEdges = cycles.flatMap { it.edgeIds }.toSet()
        
        val nodeCycleMap = mutableMapOf<String, String>()
        val edgeCycleMap = mutableMapOf<String, String>()
        
        cycles.forEachIndexed { index, cycle ->
            val cycleId = "cycle_${index + 1}"
            cycle.nodeIds.forEach { nodeId ->
                nodeCycleMap[nodeId] = cycleId
            }
            cycle.edgeIds.forEach { edgeId ->
                edgeCycleMap[edgeId] = cycleId
            }
        }
        
        return ErrorAnalysis(
            nodeErrors = nodeErrors,
            edgeErrors = edgeErrors,
            cycles = cycles,
            cycleNodes = cycleNodes,
            cycleEdges = cycleEdges,
            nodeCycleMap = nodeCycleMap,
            edgeCycleMap = edgeCycleMap
        )
    }
    
    private fun analyzeCycles(dependencyGraph: DependencyGraph, issues: List<KnitIssue>): List<CycleAnalysis> {
        val circularIssues = issues.filter { it.type == IssueType.CIRCULAR_DEPENDENCY }
        val detectedCycles = dependencyGraph.findCycles()
        
        return detectedCycles.mapIndexed { index, cyclePath ->
            val cycleNodeIds = cyclePath.map { componentName ->
                
                findFullNodeId(componentName, dependencyGraph)
            }
            
            val cycleEdgeIds = mutableListOf<String>()
            for (i in cyclePath.indices) {
                val current = cycleNodeIds[i]
                val next = cycleNodeIds[(i + 1) % cycleNodeIds.size]
                cycleEdgeIds.add(generateEdgeId(current, next))
            }
            
            CycleAnalysis(
                path = cyclePath,
                nodeIds = cycleNodeIds,
                edgeIds = cycleEdgeIds,
                relatedIssues = circularIssues.filter { issue ->
                    cyclePath.any { it.contains(extractComponentNameFromIssue(issue)) }
                }
            )
        }
    }
    
    private fun mapIssuestoNodes(issues: List<KnitIssue>): Map<String, List<KnitIssue>> {
        return issues.groupBy { issue ->
            
            
            convertComponentNameToNodeId(issue.componentName)
        }
    }
    
    private fun mapIssuesToEdges(issues: List<KnitIssue>, dependencyGraph: DependencyGraph): Map<String, List<KnitIssue>> {
        val edgeIssues = mutableMapOf<String, MutableList<KnitIssue>>()
        
        
        val circularIssues = issues.filter { it.type == IssueType.CIRCULAR_DEPENDENCY }
        val cycles = dependencyGraph.findCycles()
        
        circularIssues.forEach { issue ->
            val relevantCycle = cycles.find { cycle ->
                cycle.any { it.contains(extractComponentNameFromIssue(issue)) }
            }
            
            relevantCycle?.let { cycle ->
                
                for (i in cycle.indices) {
                    val current = findFullNodeId(cycle[i], dependencyGraph)
                    val next = findFullNodeId(cycle[(i + 1) % cycle.size], dependencyGraph)
                    val edgeId = generateEdgeId(current, next)
                    
                    edgeIssues.getOrPut(edgeId) { mutableListOf() }.add(issue)
                }
            }
        }

        
        return edgeIssues
    }
    
    private fun findFullNodeId(componentName: String, dependencyGraph: DependencyGraph): String {
        
        return dependencyGraph.nodes.find { node ->
            node.id.endsWith(".$componentName") || node.id == componentName || node.label == componentName
        }?.id ?: componentName
    }
    
    private fun generateEdgeId(from: String, to: String): String {
        return "${from}_to_${to}".replace(".", "_")
    }
    
    private fun convertComponentNameToNodeId(componentName: String): String {
        
        
        return if (componentName.contains(".")) {
            componentName
        } else {
            
            "com.example.$componentName"
        }
    }
    
    private fun extractComponentNameFromIssue(issue: KnitIssue): String {
        
        return issue.componentName.substringAfterLast(".")
    }
}

/**
 * Data class containing the results of error analysis
 */
data class ErrorAnalysis(
    val nodeErrors: Map<String, List<KnitIssue>>,
    val edgeErrors: Map<String, List<KnitIssue>>,
    val cycles: List<CycleAnalysis>,
    val cycleNodes: Set<String>,
    val cycleEdges: Set<String>,
    val nodeCycleMap: Map<String, String>,
    val edgeCycleMap: Map<String, String>
)

/**
 * Information about a detected cycle
 */
data class CycleAnalysis(
    val path: List<String>,
    val nodeIds: List<String>,
    val edgeIds: List<String>,
    val relatedIssues: List<KnitIssue>
)