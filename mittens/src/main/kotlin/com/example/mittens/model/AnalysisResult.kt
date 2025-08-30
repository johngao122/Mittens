package com.example.mittens.model

data class AnalysisResult(
    val components: List<KnitComponent>,
    val dependencyGraph: DependencyGraph,
    val issues: List<KnitIssue>,
    val timestamp: Long,
    val projectName: String,
    val knitVersion: String? = null,
    val metadata: AnalysisMetadata = AnalysisMetadata(),
    val accuracyMetrics: AccuracyMetrics = AccuracyMetrics()
) {
    fun getIssuesByType(): Map<IssueType, List<KnitIssue>> {
        return issues.groupBy { it.type }
    }

    fun getIssuesBySeverity(): Map<Severity, List<KnitIssue>> {
        return issues.groupBy { it.severity }
    }

    fun hasErrors(): Boolean = issues.any { it.severity == Severity.ERROR }

    fun hasWarnings(): Boolean = issues.any { it.severity == Severity.WARNING }
    
    fun getValidatedIssues(): List<KnitIssue> {
        return issues.filter { it.validationStatus == ValidationStatus.VALIDATED_TRUE_POSITIVE }
    }
    
    fun getFalsePositives(): List<KnitIssue> {
        return issues.filter { it.validationStatus == ValidationStatus.VALIDATED_FALSE_POSITIVE }
    }

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
            allIssues = getAllIssues(),
            filesScanned = metadata.sourceFilesScanned + metadata.bytecodeFilesScanned,
            componentsWithIssues = components.count { component ->
                issues.any { it.componentName.contains(component.className) }
            },
            accuracyMetrics = accuracyMetrics
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
                    message = issue.message,
                    componentName = issue.componentName,
                    suggestedFix = issue.suggestedFix,
                    confidenceScore = issue.confidenceScore,
                    validationStatus = issue.validationStatus
                )
            }
    }

    private fun getAllIssues(): List<IssuePreview> {
        return issues
            .sortedWith(compareBy<KnitIssue> {
                when (it.severity) {
                    Severity.ERROR -> 0
                    Severity.WARNING -> 1
                    Severity.INFO -> 2
                }
            }.thenBy { it.type })
            .map { issue ->
                IssuePreview(
                    type = issue.type,
                    severity = issue.severity,
                    message = issue.message,
                    componentName = issue.componentName,
                    suggestedFix = issue.suggestedFix,
                    confidenceScore = issue.confidenceScore,
                    validationStatus = issue.validationStatus
                )
            }
    }
}

data class AnalysisMetadata(
    val analysisTimeMs: Long = 0,
    val bytecodeFilesScanned: Int = 0,
    val sourceFilesScanned: Int = 0,
    val pluginVersion: String = "1.0.0",
    val validationTimeMs: Long = 0,
    val deduplicationTimeMs: Long = 0,
    val analysisMethod: AnalysisMethod = AnalysisMethod.SOURCE_ANALYSIS,
    val knitJsonPath: String? = null
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
    val allIssues: List<IssuePreview> = emptyList(),
    val filesScanned: Int = 0,
    val componentsWithIssues: Int = 0,
    val accuracyMetrics: AccuracyMetrics = AccuracyMetrics()
) {
    fun getAccuracyPercentage(): Double {
        return if (accuracyMetrics.totalValidatedIssues > 0) {
            (accuracyMetrics.truePositives.toDouble() / accuracyMetrics.totalValidatedIssues) * 100.0
        } else {
            100.0 
        }
    }
    
    fun getFalsePositiveRate(): Double {
        return if (totalIssues > 0) {
            (accuracyMetrics.falsePositives.toDouble() / totalIssues) * 100.0
        } else {
            0.0
        }
    }
    
    fun getStatisticalError(): Double {
        return if (accuracyMetrics.expectedIssues > 0) {
            Math.abs((totalIssues - accuracyMetrics.expectedIssues).toDouble() / accuracyMetrics.expectedIssues) * 100.0
        } else {
            0.0
        }
    }
}

data class IssuePreview(
    val type: IssueType,
    val severity: Severity,
    val message: String,
    val componentName: String,
    val suggestedFix: String? = null,
    val confidenceScore: Double = 1.0,
    val validationStatus: ValidationStatus = ValidationStatus.NOT_VALIDATED
)

/**
 * Statistical accuracy metrics for analysis results
 */
data class AccuracyMetrics(
    val totalValidatedIssues: Int = 0,
    val truePositives: Int = 0,
    val falsePositives: Int = 0,
    val falseNegatives: Int = 0,
    val expectedIssues: Int = 0,
    val validationEnabled: Boolean = false,
    val averageConfidenceScore: Double = 1.0,
    val issueValidationDetails: Map<IssueType, ValidationDetails> = emptyMap()
) {
    fun getPrecision(): Double {
        return if (truePositives + falsePositives > 0) {
            truePositives.toDouble() / (truePositives + falsePositives)
        } else {
            1.0
        }
    }
    
    fun getRecall(): Double {
        return if (truePositives + falseNegatives > 0) {
            truePositives.toDouble() / (truePositives + falseNegatives)
        } else {
            1.0
        }
    }
    
    fun getF1Score(): Double {
        val precision = getPrecision()
        val recall = getRecall()
        return if (precision + recall > 0) {
            2 * (precision * recall) / (precision + recall)
        } else {
            0.0
        }
    }
}

data class ValidationDetails(
    val totalDetected: Int,
    val validated: Int,
    val falsePositives: Int,
    val averageConfidence: Double
)