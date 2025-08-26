package com.example.mittens.model

data class AnalysisResult(
    val components: List<KnitComponent>,
    val dependencyGraph: DependencyGraph,
    val issues: List<KnitIssue>,
    val timestamp: Long,
    val projectName: String,
    val knitVersion: String? = null,
    val metadata: AnalysisMetadata = AnalysisMetadata()
) {
    fun getIssuesByType(): Map<IssueType, List<KnitIssue>> {
        return issues.groupBy { it.type }
    }
    
    fun getIssuesBySeverity(): Map<Severity, List<KnitIssue>> {
        return issues.groupBy { it.severity }
    }
    
    fun hasErrors(): Boolean = issues.any { it.severity == Severity.ERROR }
    
    fun hasWarnings(): Boolean = issues.any { it.severity == Severity.WARNING }
    
    fun getSummary(): AnalysisSummary {
        return AnalysisSummary(
            totalComponents = components.size,
            totalDependencies = dependencyGraph.edges.size,
            totalIssues = issues.size,
            errorCount = issues.count { it.severity == Severity.ERROR },
            warningCount = issues.count { it.severity == Severity.WARNING },
            infoCount = issues.count { it.severity == Severity.INFO },
            hasCycles = dependencyGraph.hasCycles(),
            analysisTime = metadata.analysisTimeMs
        )
    }
}

data class AnalysisMetadata(
    val analysisTimeMs: Long = 0,
    val bytecodeFilesScanned: Int = 0,
    val sourceFilesScanned: Int = 0,
    val pluginVersion: String = "1.0.0"
)

data class AnalysisSummary(
    val totalComponents: Int,
    val totalDependencies: Int,
    val totalIssues: Int,
    val errorCount: Int,
    val warningCount: Int,
    val infoCount: Int,
    val hasCycles: Boolean,
    val analysisTime: Long
)