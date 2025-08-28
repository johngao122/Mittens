package com.example.mittens.export

import com.example.mittens.model.*
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Complete dependency graph export format optimized for JavaScript graphing libraries
 * with rich error highlighting capabilities
 */
data class GraphJsonExport(
    @JsonProperty("graph")
    val graph: GraphStructure,
    
    @JsonProperty("errorContext") 
    val errorContext: ErrorContext,
    
    @JsonProperty("metadata")
    val metadata: ExportMetadata
)

/**
 * Main graph structure containing all nodes and edges
 */
data class GraphStructure(
    @JsonProperty("nodes")
    val nodes: List<GraphNodeExport>,
    
    @JsonProperty("edges") 
    val edges: List<GraphEdgeExport>
)

/**
 * Node in the dependency graph with error highlighting information
 */
data class GraphNodeExport(
    @JsonProperty("id")
    val id: String,
    
    @JsonProperty("label")
    val label: String,
    
    @JsonProperty("type")
    val type: String, // NodeType as string
    
    @JsonProperty("packageName")
    val packageName: String,
    
    @JsonProperty("className")
    val className: String,
    
    @JsonProperty("metadata")
    val metadata: NodeMetadata,
    
    @JsonProperty("errorHighlight")
    val errorHighlight: ErrorHighlight
)

/**
 * Edge in the dependency graph with error highlighting information
 */
data class GraphEdgeExport(
    @JsonProperty("id")
    val id: String,
    
    @JsonProperty("source")
    val source: String,
    
    @JsonProperty("target")
    val target: String,
    
    @JsonProperty("type")
    val type: String, // EdgeType as string
    
    @JsonProperty("label")
    val label: String?,
    
    @JsonProperty("metadata")
    val metadata: EdgeMetadata,
    
    @JsonProperty("errorHighlight")
    val errorHighlight: ErrorHighlight
)

/**
 * Error highlighting information for visual rendering
 */
data class ErrorHighlight(
    @JsonProperty("hasErrors")
    val hasErrors: Boolean,
    
    @JsonProperty("errorSeverity")
    val errorSeverity: String?, // Severity as string, null if no errors
    
    @JsonProperty("errorTypes")
    val errorTypes: List<String>, // List of IssueType as strings
    
    @JsonProperty("isPartOfCycle")
    val isPartOfCycle: Boolean = false,
    
    @JsonProperty("cycleId")
    val cycleId: String? = null,
    
    @JsonProperty("visualHints")
    val visualHints: VisualHints
)

/**
 * Visual styling hints for graphing libraries
 */
data class VisualHints(
    @JsonProperty("borderColor")
    val borderColor: String,
    
    @JsonProperty("backgroundColor") 
    val backgroundColor: String,
    
    @JsonProperty("borderWidth")
    val borderWidth: Int = 1,
    
    @JsonProperty("color")
    val color: String? = null, // For edges
    
    @JsonProperty("width")
    val width: Int? = null, // For edges
    
    @JsonProperty("style")
    val style: String? = null, // solid, dashed, dotted
    
    @JsonProperty("classes")
    val classes: List<String> = emptyList()
)

/**
 * Additional metadata for nodes
 */
data class NodeMetadata(
    @JsonProperty("sourceFile")
    val sourceFile: String?,
    
    @JsonProperty("dependencyCount")
    val dependencyCount: Int,
    
    @JsonProperty("providerCount")
    val providerCount: Int,
    
    @JsonProperty("issueCount")
    val issueCount: Int
)

/**
 * Additional metadata for edges  
 */
data class EdgeMetadata(
    @JsonProperty("isNamed")
    val isNamed: Boolean = false,
    
    @JsonProperty("namedQualifier")
    val namedQualifier: String? = null,
    
    @JsonProperty("isSingleton")
    val isSingleton: Boolean = false,
    
    @JsonProperty("isFactory")
    val isFactory: Boolean = false
)

/**
 * Error context providing detailed information about issues in the graph
 */
data class ErrorContext(
    @JsonProperty("totalErrors")
    val totalErrors: Int,
    
    @JsonProperty("totalWarnings") 
    val totalWarnings: Int,
    
    @JsonProperty("cycles")
    val cycles: List<CycleInfo>,
    
    @JsonProperty("unresolvedDependencies")
    val unresolvedDependencies: List<UnresolvedDependency>,
    
    @JsonProperty("issueDetails")
    val issueDetails: List<IssueDetail>
)

/**
 * Information about a circular dependency cycle
 */
data class CycleInfo(
    @JsonProperty("id")
    val id: String,
    
    @JsonProperty("path") 
    val path: List<String>, // Human-readable path
    
    @JsonProperty("nodeIds")
    val nodeIds: List<String>, // Actual node IDs in the cycle
    
    @JsonProperty("edgeIds") 
    val edgeIds: List<String>, // Edge IDs forming the cycle
    
    @JsonProperty("severity")
    val severity: String // ERROR, WARNING, etc.
)

/**
 * Information about an unresolved dependency
 */
data class UnresolvedDependency(
    @JsonProperty("fromNodeId")
    val fromNodeId: String,
    
    @JsonProperty("targetType")
    val targetType: String,
    
    @JsonProperty("issue")
    val issue: String,
    
    @JsonProperty("namedQualifier")
    val namedQualifier: String? = null
)

/**
 * Detailed information about a specific issue
 */
data class IssueDetail(
    @JsonProperty("id")
    val id: String,
    
    @JsonProperty("type")
    val type: String, // IssueType as string
    
    @JsonProperty("severity")
    val severity: String, // Severity as string
    
    @JsonProperty("message")
    val message: String,
    
    @JsonProperty("affectedNodes")
    val affectedNodes: List<String>,
    
    @JsonProperty("affectedEdges") 
    val affectedEdges: List<String>,
    
    @JsonProperty("suggestedFix")
    val suggestedFix: String?,
    
    @JsonProperty("confidenceScore")
    val confidenceScore: Double = 1.0
)

/**
 * Export metadata
 */
data class ExportMetadata(
    @JsonProperty("projectName")
    val projectName: String,
    
    @JsonProperty("analysisTimestamp")
    val analysisTimestamp: Long,
    
    @JsonProperty("totalComponents")
    val totalComponents: Int,
    
    @JsonProperty("totalDependencies")
    val totalDependencies: Int,
    
    @JsonProperty("componentsWithErrors")
    val componentsWithErrors: Int,
    
    @JsonProperty("healthyComponents")
    val healthyComponents: Int,
    
    @JsonProperty("knitVersion")
    val knitVersion: String?,
    
    @JsonProperty("pluginVersion")
    val pluginVersion: String = "1.0.0"
)