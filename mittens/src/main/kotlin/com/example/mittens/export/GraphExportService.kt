package com.example.mittens.export

import com.example.mittens.model.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service
class GraphExportService(private val project: Project) {
    
    /**
     * Convert AnalysisResult to JSON export format with complete graph and error highlighting
     */
    fun exportToJson(analysisResult: AnalysisResult): GraphJsonExport {
        val errorHighlightService = ErrorHighlightService()
        val errorAnalysis = errorHighlightService.analyzeErrors(
            analysisResult.dependencyGraph,
            analysisResult.issues
        )
        
        val graphStructure = GraphStructure(
            nodes = convertNodes(analysisResult.dependencyGraph.nodes, analysisResult.components, errorAnalysis),
            edges = convertEdges(analysisResult.dependencyGraph.edges, errorAnalysis)
        )
        
        val errorContext = buildErrorContext(analysisResult.issues, errorAnalysis)
        
        val metadata = ExportMetadata(
            projectName = analysisResult.projectName,
            analysisTimestamp = analysisResult.timestamp,
            totalComponents = analysisResult.components.size,
            totalDependencies = analysisResult.dependencyGraph.edges.size,
            knitVersion = analysisResult.knitVersion,
            pluginVersion = analysisResult.metadata.pluginVersion
        )
        
        return GraphJsonExport(
            graph = graphStructure,
            errorContext = errorContext,
            metadata = metadata
        )
    }
    
    private fun convertNodes(
        graphNodes: List<GraphNode>,
        components: List<KnitComponent>,
        errorAnalysis: ErrorAnalysis
    ): List<GraphNodeExport> {
        return graphNodes.map { graphNode ->
            val component = findComponentForNode(graphNode, components)
            val nodeErrors = errorAnalysis.nodeErrors[graphNode.id] ?: emptyList()
            val isPartOfCycle = errorAnalysis.cycleNodes.contains(graphNode.id)
            val cycleId = errorAnalysis.nodeCycleMap[graphNode.id]
            
            GraphNodeExport(
                id = graphNode.id,
                label = graphNode.label,
                type = graphNode.type.name,
                packageName = graphNode.packageName,
                className = extractClassName(graphNode.id),
                metadata = NodeMetadata(
                    sourceFile = component?.sourceFile,
                    dependencyCount = component?.dependencies?.size ?: 0,
                    providerCount = component?.providers?.size ?: 0,
                    issueCount = nodeErrors.size
                ),
                errorHighlight = ErrorHighlight(
                    hasErrors = nodeErrors.isNotEmpty(),
                    errorSeverity = getHighestSeverity(nodeErrors)?.name,
                    errorTypes = nodeErrors.map { it.type.name }.distinct(),
                    isPartOfCycle = isPartOfCycle,
                    cycleId = cycleId,
                    visualHints = generateNodeVisualHints(nodeErrors, isPartOfCycle)
                )
            )
        }
    }
    
    private fun convertEdges(
        graphEdges: List<GraphEdge>,
        errorAnalysis: ErrorAnalysis
    ): List<GraphEdgeExport> {
        return graphEdges.map { graphEdge ->
            val edgeErrors = errorAnalysis.edgeErrors[generateEdgeId(graphEdge)] ?: emptyList()
            val isPartOfCycle = errorAnalysis.cycleEdges.contains(generateEdgeId(graphEdge))
            val cycleId = errorAnalysis.edgeCycleMap[generateEdgeId(graphEdge)]
            
            GraphEdgeExport(
                id = generateEdgeId(graphEdge),
                source = graphEdge.from,
                target = graphEdge.to,
                type = graphEdge.type.name,
                label = graphEdge.label,
                metadata = EdgeMetadata(
                    isNamed = isNamedDependency(graphEdge),
                    namedQualifier = extractNamedQualifier(graphEdge.label),
                    isSingleton = graphEdge.type == EdgeType.SINGLETON,
                    isFactory = graphEdge.type == EdgeType.FACTORY
                ),
                errorHighlight = ErrorHighlight(
                    hasErrors = edgeErrors.isNotEmpty(),
                    errorSeverity = getHighestSeverity(edgeErrors)?.name,
                    errorTypes = edgeErrors.map { it.type.name }.distinct(),
                    isPartOfCycle = isPartOfCycle,
                    cycleId = cycleId,
                    visualHints = generateEdgeVisualHints(edgeErrors, isPartOfCycle)
                )
            )
        }
    }
    
    private fun buildErrorContext(
        issues: List<KnitIssue>,
        errorAnalysis: ErrorAnalysis
    ): ErrorContext {
        val cycles = errorAnalysis.cycles.mapIndexed { index, cycle ->
            CycleInfo(
                id = "cycle_${index + 1}",
                path = cycle.path,
                nodeIds = cycle.nodeIds,
                edgeIds = cycle.edgeIds,
                severity = "ERROR" 
            )
        }

        
        val issueDetails = issues.mapIndexed { index, issue ->
            IssueDetail(
                id = "issue_${index + 1}",
                type = issue.type.name,
                severity = issue.severity.name,
                message = issue.message,
                affectedNodes = findAffectedNodesForIssue(issue, errorAnalysis),
                affectedEdges = findAffectedEdgesForIssue(issue, errorAnalysis),
                suggestedFix = issue.suggestedFix,
                confidenceScore = issue.confidenceScore
            )
        }
        
        return ErrorContext(
            totalErrors = issues.count { it.severity == Severity.ERROR },
            totalWarnings = issues.count { it.severity == Severity.WARNING },
            cycles = cycles,
            issueDetails = issueDetails
        )
    }
    
    private fun generateNodeVisualHints(errors: List<KnitIssue>, isPartOfCycle: Boolean): VisualHints {
        return when {
            errors.any { it.severity == Severity.ERROR } -> {
                val classes = mutableListOf("error-node")
                if (isPartOfCycle) classes.add("cycle-participant")
                
                VisualHints(
                    borderColor = "#ff0000",
                    backgroundColor = "#ffeeee", 
                    borderWidth = 2,
                    classes = classes
                )
            }
            errors.any { it.severity == Severity.WARNING } -> {
                VisualHints(
                    borderColor = "#ff8c00",
                    backgroundColor = "#fff8e1",
                    borderWidth = 2,
                    classes = listOf("warning-node")
                )
            }
            else -> {
                VisualHints(
                    borderColor = "#28a745",
                    backgroundColor = "#f8fff9",
                    borderWidth = 1,
                    classes = listOf("healthy-node")
                )
            }
        }
    }
    
    private fun generateEdgeVisualHints(errors: List<KnitIssue>, isPartOfCycle: Boolean): VisualHints {
        return when {
            errors.any { it.severity == Severity.ERROR } || isPartOfCycle -> {
                val classes = mutableListOf("error-edge")
                if (isPartOfCycle) classes.add("cycle-edge")
                
                VisualHints(
                    borderColor = "#ff0000",
                    backgroundColor = "#ffffff",
                    color = "#ff0000",
                    width = 3,
                    style = "solid",
                    classes = classes
                )
            }
            errors.any { it.severity == Severity.WARNING } -> {
                VisualHints(
                    borderColor = "#ff8c00",
                    backgroundColor = "#ffffff", 
                    color = "#ff8c00",
                    width = 2,
                    style = "dashed",
                    classes = listOf("warning-edge")
                )
            }
            else -> {
                VisualHints(
                    borderColor = "#28a745",
                    backgroundColor = "#ffffff",
                    color = "#28a745", 
                    width = 1,
                    style = "solid",
                    classes = listOf("healthy-edge")
                )
            }
        }
    }
    
    
    private fun generateEdgeId(edge: GraphEdge): String {
        return "${edge.from}_to_${edge.to}".replace(".", "_")
    }
    
    private fun extractClassName(nodeId: String): String {
        return nodeId.substringAfterLast(".")
    }
    
    private fun findComponentForNode(node: GraphNode, components: List<KnitComponent>): KnitComponent? {
        return components.find { 
            "${it.packageName}.${it.className}" == node.id 
        }
    }
    
    private fun getHighestSeverity(errors: List<KnitIssue>): Severity? {
        return errors.minByOrNull { 
            when (it.severity) {
                Severity.ERROR -> 0
                Severity.WARNING -> 1
                Severity.INFO -> 2
            }
        }?.severity
    }
    
    
    private fun isNamedDependency(edge: GraphEdge): Boolean {
        return edge.type == EdgeType.NAMED || edge.label?.contains("@Named") == true
    }
    
    private fun extractNamedQualifier(label: String?): String? {
        return label?.let { 
            if (it.contains("@Named(")) {
                it.substringAfter("@Named(").substringBefore(")")
            } else null
        }
    }
    
    private fun findNodeIdForComponent(componentName: String): String {
        
        return componentName
    }
    
    private fun extractTargetType(message: String): String {
        
        return "UnknownType"
    }
    
    private fun extractNamedQualifierFromIssue(issue: KnitIssue): String? {
        
        return issue.metadata["namedQualifier"] as? String
    }
    
    private fun findAffectedNodesForIssue(issue: KnitIssue, errorAnalysis: ErrorAnalysis): List<String> {
        return when (issue.type) {
            IssueType.CIRCULAR_DEPENDENCY -> {
                
                errorAnalysis.cycles
                    .find { cycle -> cycle.path.any { it.contains(issue.componentName) } }
                    ?.nodeIds ?: listOf(findNodeIdForComponent(issue.componentName))
            }
            IssueType.AMBIGUOUS_PROVIDER -> {
                // Parse multiple component names from the issue message for ambiguous providers
                parseAffectedNodesFromIssue(issue)
            }
            else -> listOf(findNodeIdForComponent(issue.componentName))
        }
    }
    
    private fun parseAffectedNodesFromIssue(issue: KnitIssue): List<String> {
        // For ambiguous provider issues, the componentName field may contain multiple components
        // separated by commas, like "EmailChannel.<init>, NotificationService.provideEmailChannel"
        return if (issue.componentName.contains(",")) {
            issue.componentName.split(",").map { 
                findNodeIdForComponent(it.trim()) 
            }
        } else {
            listOf(findNodeIdForComponent(issue.componentName))
        }
    }
    
    private fun findAffectedEdgesForIssue(issue: KnitIssue, errorAnalysis: ErrorAnalysis): List<String> {
        return when (issue.type) {
            IssueType.CIRCULAR_DEPENDENCY -> {
                
                errorAnalysis.cycles
                    .find { cycle -> cycle.path.any { it.contains(issue.componentName) } }
                    ?.edgeIds ?: emptyList()
            }
            else -> emptyList()
        }
    }
    
    companion object {
        fun getInstance(project: Project): GraphExportService {
            return project.getService(GraphExportService::class.java)
        }
    }
}