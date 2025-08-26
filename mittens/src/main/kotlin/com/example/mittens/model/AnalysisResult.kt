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
        val issuesByType = getIssuesByType()
        val issuesBySeverity = getIssuesBySeverity()

        return AnalysisSummary(
            totalComponents = components.size,
            totalDependencies = dependencyGraph.edges.size,
            totalIssues = issues.size,
            errorCount = issues.count { it.severity == Severity.ERROR },
            warningCount = issues.count { it.severity == Severity.WARNING },
            infoCount = issues.count { it.severity == Severity.INFO },
            hasCycles = dependencyGraph.hasCycles(),
            analysisTime = metadata.analysisTimeMs,
            issueBreakdown = issuesByType.mapValues { it.value.size },
            topIssues = getTopIssues(),
            filesScanned = metadata.sourceFilesScanned + metadata.bytecodeFilesScanned,
            componentsWithIssues = components.count { component ->
                issues.any { it.componentName.contains(component.className) }
            }
        )
    }

    private fun getTopIssues(): List<IssuePreview> {
        return issues
            .sortedWith(compareBy<KnitIssue> {
                when (it.severity) {
                    Severity.ERROR -> 0
                    Severity.WARNING -> 1
                    Severity.INFO -> 2
                }
            }.thenBy { it.type })
            .take(3)
            .map { issue ->
                IssuePreview(
                    type = issue.type,
                    severity = issue.severity,
                    message = issue.message.take(80) + if (issue.message.length > 80) "..." else "",
                    componentName = issue.componentName,
                    suggestedFix = issue.suggestedFix?.take(60) + if ((issue.suggestedFix?.length
                            ?: 0) > 60
                    ) "..." else ""
                )
            }
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
    val analysisTime: Long,
    val issueBreakdown: Map<IssueType, Int> = emptyMap(),
    val topIssues: List<IssuePreview> = emptyList(),
    val filesScanned: Int = 0,
    val componentsWithIssues: Int = 0
)

data class IssuePreview(
    val type: IssueType,
    val severity: Severity,
    val message: String,
    val componentName: String,
    val suggestedFix: String? = null
)