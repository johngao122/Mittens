package com.example.mittens.services

import com.example.mittens.model.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger

/**
 * Service responsible for calculating and tracking statistical accuracy metrics
 * for dependency injection analysis results.
 */
@Service
class StatisticalAccuracyService {

    private val logger = thisLogger()

    /**
     * Calculates comprehensive accuracy metrics from validated issues
     */
    fun calculateAccuracyMetrics(
        allIssues: List<KnitIssue>,
        validatedIssues: List<KnitIssue>,
        expectedIssues: Int = 0
    ): AccuracyMetrics {
        val startTime = System.currentTimeMillis()

        val truePositives = validatedIssues.count { it.validationStatus == ValidationStatus.VALIDATED_TRUE_POSITIVE }
        val falsePositives = validatedIssues.count { it.validationStatus == ValidationStatus.VALIDATED_FALSE_POSITIVE }
        val totalValidated = validatedIssues.size
        
        // Estimate false negatives based on expected issues
        val falseNegatives = maxOf(0, expectedIssues - truePositives)

        val averageConfidence = if (validatedIssues.isNotEmpty()) {
            validatedIssues.map { it.confidenceScore }.average()
        } else {
            1.0
        }

        val validationDetails = calculateValidationDetailsByType(validatedIssues)

        val metrics = AccuracyMetrics(
            totalValidatedIssues = totalValidated,
            truePositives = truePositives,
            falsePositives = falsePositives,
            falseNegatives = falseNegatives,
            expectedIssues = expectedIssues,
            validationEnabled = true,
            averageConfidenceScore = averageConfidence,
            issueValidationDetails = validationDetails
        )

        val calculationTime = System.currentTimeMillis() - startTime
        logger.debug("Accuracy metrics calculated in ${calculationTime}ms: Precision=${metrics.getPrecision()}, Recall=${metrics.getRecall()}, F1=${metrics.getF1Score()}")

        return metrics
    }

    /**
     * Calculates validation details per issue type
     */
    private fun calculateValidationDetailsByType(validatedIssues: List<KnitIssue>): Map<IssueType, ValidationDetails> {
        return validatedIssues.groupBy { it.type }.mapValues { (_, issues) ->
            val totalDetected = issues.size
            val validated = issues.count { it.validationStatus != ValidationStatus.NOT_VALIDATED }
            val falsePositives = issues.count { it.validationStatus == ValidationStatus.VALIDATED_FALSE_POSITIVE }
            val averageConfidence = issues.map { it.confidenceScore }.average()

            ValidationDetails(
                totalDetected = totalDetected,
                validated = validated,
                falsePositives = falsePositives,
                averageConfidence = averageConfidence
            )
        }
    }

    /**
     * Generates a comprehensive accuracy report
     */
    fun generateAccuracyReport(accuracyMetrics: AccuracyMetrics, totalIssues: Int): String {
        val precision = accuracyMetrics.getPrecision() * 100
        val recall = accuracyMetrics.getRecall() * 100
        val f1Score = accuracyMetrics.getF1Score() * 100
        val falsePositiveRate = if (totalIssues > 0) {
            (accuracyMetrics.falsePositives.toDouble() / totalIssues) * 100
        } else {
            0.0
        }

        val statisticalError = if (accuracyMetrics.expectedIssues > 0) {
            Math.abs((totalIssues - accuracyMetrics.expectedIssues).toDouble() / accuracyMetrics.expectedIssues) * 100
        } else {
            0.0
        }

        return buildString {
            appendLine("=== Statistical Accuracy Report ===")
            appendLine()
            appendLine("📊 Overall Metrics:")
            appendLine("   Precision: ${String.format("%.1f", precision)}%")
            appendLine("   Recall: ${String.format("%.1f", recall)}%")
            appendLine("   F1-Score: ${String.format("%.1f", f1Score)}%")
            appendLine("   Average Confidence: ${String.format("%.1f", accuracyMetrics.averageConfidenceScore * 100)}%")
            appendLine()
            appendLine("🎯 Detection Results:")
            appendLine("   Total Issues Detected: $totalIssues")
            appendLine("   Issues Validated: ${accuracyMetrics.totalValidatedIssues}")
            appendLine("   True Positives: ${accuracyMetrics.truePositives}")
            appendLine("   False Positives: ${accuracyMetrics.falsePositives}")
            if (accuracyMetrics.expectedIssues > 0) {
                appendLine("   Expected Issues: ${accuracyMetrics.expectedIssues}")
                appendLine("   False Negatives (estimated): ${accuracyMetrics.falseNegatives}")
            }
            appendLine()
            appendLine("📈 Quality Indicators:")
            appendLine("   False Positive Rate: ${String.format("%.1f", falsePositiveRate)}%")
            if (statisticalError > 0) {
                appendLine("   Statistical Error: ${String.format("%.1f", statisticalError)}%")
            }
            
            if (accuracyMetrics.issueValidationDetails.isNotEmpty()) {
                appendLine()
                appendLine("🔍 Validation Details by Issue Type:")
                accuracyMetrics.issueValidationDetails.forEach { (issueType, details) ->
                    val typeAccuracy = if (details.totalDetected > 0) {
                        ((details.totalDetected - details.falsePositives).toDouble() / details.totalDetected) * 100
                    } else {
                        100.0
                    }
                    appendLine("   ${issueType.name}:")
                    appendLine("     Detected: ${details.totalDetected}")
                    appendLine("     False Positives: ${details.falsePositives}")
                    appendLine("     Accuracy: ${String.format("%.1f", typeAccuracy)}%")
                    appendLine("     Avg Confidence: ${String.format("%.1f", details.averageConfidence * 100)}%")
                }
            }
            
            appendLine()
            appendLine("🎯 Target Metrics:")
            appendLine("   Target Accuracy: ≥95%")
            appendLine("   Target False Positive Rate: <5%")
            appendLine("   Target Statistical Error: <10%")
            appendLine()
            
            val overallAccuracy = if (accuracyMetrics.totalValidatedIssues > 0) {
                (accuracyMetrics.truePositives.toDouble() / accuracyMetrics.totalValidatedIssues) * 100
            } else {
                100.0
            }
            
            when {
                overallAccuracy >= 95.0 && falsePositiveRate < 5.0 -> {
                    appendLine("✅ EXCELLENT: Analysis meets all accuracy targets!")
                }
                overallAccuracy >= 80.0 && falsePositiveRate < 10.0 -> {
                    appendLine("✅ GOOD: Analysis accuracy is acceptable")
                }
                overallAccuracy >= 60.0 -> {
                    appendLine("⚠️  WARNING: Analysis accuracy needs improvement")
                }
                else -> {
                    appendLine("❌ POOR: Analysis accuracy is below acceptable levels")
                }
            }
        }
    }

    /**
     * Estimates expected number of issues based on project characteristics
     */
    fun estimateExpectedIssues(components: List<KnitComponent>): Int {
        // Simple heuristic based on project size and complexity
        val totalDependencies = components.sumOf { it.dependencies.size }
        val totalProviders = components.sumOf { it.providers.size }
        val componentCount = components.size

        // Rough estimates based on typical patterns:
        // - 1-3% of dependencies typically have issues
        // - Larger projects tend to have more complexity issues
        // - Projects with many providers may have ambiguity issues

        val dependencyIssueRate = when {
            componentCount > 100 -> 0.03 // 3% for large projects
            componentCount > 50 -> 0.02  // 2% for medium projects
            else -> 0.01                 // 1% for small projects
        }

        val providerIssueRate = 0.02 // 2% of providers may have issues

        val estimatedDependencyIssues = (totalDependencies * dependencyIssueRate).toInt()
        val estimatedProviderIssues = (totalProviders * providerIssueRate).toInt()

        // Add complexity factor for circular dependencies
        val complexityFactor = when {
            componentCount > 100 -> 3
            componentCount > 50 -> 2
            componentCount > 20 -> 1
            else -> 0
        }

        return estimatedDependencyIssues + estimatedProviderIssues + complexityFactor
    }

    /**
     * Compares current analysis with previous analysis results for trend tracking
     */
    fun compareWithPreviousAnalysis(
        currentMetrics: AccuracyMetrics,
        previousMetrics: AccuracyMetrics?
    ): AccuracyTrendReport {
        if (previousMetrics == null) {
            return AccuracyTrendReport(
                hasComparison = false,
                precisionChange = 0.0,
                recallChange = 0.0,
                falsePositiveChange = 0,
                trend = AccuracyTrend.NO_DATA
            )
        }

        val precisionChange = currentMetrics.getPrecision() - previousMetrics.getPrecision()
        val recallChange = currentMetrics.getRecall() - previousMetrics.getRecall()
        val falsePositiveChange = currentMetrics.falsePositives - previousMetrics.falsePositives

        val trend = when {
            precisionChange > 0.05 && recallChange > 0.05 -> AccuracyTrend.IMPROVING
            precisionChange < -0.05 || recallChange < -0.05 -> AccuracyTrend.DECLINING
            else -> AccuracyTrend.STABLE
        }

        return AccuracyTrendReport(
            hasComparison = true,
            precisionChange = precisionChange,
            recallChange = recallChange,
            falsePositiveChange = falsePositiveChange,
            trend = trend
        )
    }
}

/**
 * Report comparing accuracy metrics over time
 */
data class AccuracyTrendReport(
    val hasComparison: Boolean,
    val precisionChange: Double,
    val recallChange: Double,
    val falsePositiveChange: Int,
    val trend: AccuracyTrend
)

enum class AccuracyTrend {
    IMPROVING,
    STABLE,
    DECLINING,
    NO_DATA
}