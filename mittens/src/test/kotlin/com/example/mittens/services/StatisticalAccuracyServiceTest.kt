package com.example.mittens.services

import com.example.mittens.model.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

class StatisticalAccuracyServiceTest {

    private lateinit var statisticalService: StatisticalAccuracyService

    @Before
    fun setUp() {
        statisticalService = StatisticalAccuracyService()
    }

    @Test
    fun testCalculateAccuracyMetrics_PerfectAccuracy() {
        val validatedIssues = listOf(
            KnitIssue(
                type = IssueType.CIRCULAR_DEPENDENCY,
                severity = Severity.ERROR,
                message = "True positive 1",
                componentName = "Component1",
                validationStatus = ValidationStatus.VALIDATED_TRUE_POSITIVE,
                confidenceScore = 0.95
            ),
            KnitIssue(
                type = IssueType.UNRESOLVED_DEPENDENCY,
                severity = Severity.ERROR,
                message = "True positive 2",
                componentName = "Component2",
                validationStatus = ValidationStatus.VALIDATED_TRUE_POSITIVE,
                confidenceScore = 0.85
            )
        )

        val metrics = statisticalService.calculateAccuracyMetrics(
            allIssues = validatedIssues,
            validatedIssues = validatedIssues,
            expectedIssues = 2
        )

        assertEquals(2, metrics.totalValidatedIssues)
        assertEquals(2, metrics.truePositives)
        assertEquals(0, metrics.falsePositives)
        assertEquals(0, metrics.falseNegatives)
        assertEquals(2, metrics.expectedIssues)
        assertTrue("Validation should be enabled", metrics.validationEnabled)
        assertEquals(0.9, metrics.averageConfidenceScore, 0.01)
        assertEquals(1.0, metrics.getPrecision(), 0.001)
        assertEquals(1.0, metrics.getRecall(), 0.001)
        assertEquals(1.0, metrics.getF1Score(), 0.001)
    }

    @Test
    fun testCalculateAccuracyMetrics_WithFalsePositives() {
        val validatedIssues = listOf(
            KnitIssue(
                type = IssueType.CIRCULAR_DEPENDENCY,
                severity = Severity.ERROR,
                message = "True positive",
                componentName = "Component1",
                validationStatus = ValidationStatus.VALIDATED_TRUE_POSITIVE,
                confidenceScore = 0.9
            ),
            KnitIssue(
                type = IssueType.UNRESOLVED_DEPENDENCY,
                severity = Severity.ERROR,
                message = "False positive",
                componentName = "Component2",
                validationStatus = ValidationStatus.VALIDATED_FALSE_POSITIVE,
                confidenceScore = 0.2
            ),
            KnitIssue(
                type = IssueType.AMBIGUOUS_PROVIDER,
                severity = Severity.WARNING,
                message = "Another false positive",
                componentName = "Component3",
                validationStatus = ValidationStatus.VALIDATED_FALSE_POSITIVE,
                confidenceScore = 0.3
            )
        )

        val metrics = statisticalService.calculateAccuracyMetrics(
            allIssues = validatedIssues,
            validatedIssues = validatedIssues,
            expectedIssues = 2
        )

        assertEquals(3, metrics.totalValidatedIssues)
        assertEquals(1, metrics.truePositives)
        assertEquals(2, metrics.falsePositives)
        assertEquals(1, metrics.falseNegatives) // expectedIssues(2) - truePositives(1)
        assertEquals(0.33, metrics.getPrecision(), 0.01) // 1/(1+2)
        assertEquals(0.5, metrics.getRecall(), 0.01) // 1/(1+1)
        assertEquals(0.4, metrics.getF1Score(), 0.01) // 2 * (0.33*0.5)/(0.33+0.5)
    }

    @Test
    fun testCalculateAccuracyMetrics_EmptyInput() {
        val metrics = statisticalService.calculateAccuracyMetrics(
            allIssues = emptyList(),
            validatedIssues = emptyList(),
            expectedIssues = 0
        )

        assertEquals(0, metrics.totalValidatedIssues)
        assertEquals(0, metrics.truePositives)
        assertEquals(0, metrics.falsePositives)
        assertEquals(0, metrics.falseNegatives)
        assertEquals(1.0, metrics.averageConfidenceScore, 0.001) // Default for empty list
        assertEquals(1.0, metrics.getPrecision(), 0.001) // Perfect when no false positives
        assertEquals(1.0, metrics.getRecall(), 0.001) // Perfect when no false negatives
    }

    @Test
    fun testGenerateAccuracyReport_ExcellentAccuracy() {
        val metrics = AccuracyMetrics(
            totalValidatedIssues = 10,
            truePositives = 10,
            falsePositives = 0,
            falseNegatives = 0,
            expectedIssues = 10,
            validationEnabled = true,
            averageConfidenceScore = 0.92
        )

        val report = statisticalService.generateAccuracyReport(metrics, 10)

        assertContains(report, "=== Statistical Accuracy Report ===")
        assertContains(report, "Precision: 100.0%")
        assertContains(report, "Recall: 100.0%")
        assertContains(report, "F1-Score: 100.0%")
        assertContains(report, "False Positive Rate: 0.0%")
        assertContains(report, "Statistical Error: 0.0%")
        assertContains(report, "✅ EXCELLENT: Analysis meets all accuracy targets!")
    }

    @Test
    fun testGenerateAccuracyReport_PoorAccuracy() {
        val metrics = AccuracyMetrics(
            totalValidatedIssues = 10,
            truePositives = 4,
            falsePositives = 6,
            falseNegatives = 2,
            expectedIssues = 6,
            validationEnabled = true,
            averageConfidenceScore = 0.45,
            issueValidationDetails = mapOf(
                IssueType.CIRCULAR_DEPENDENCY to ValidationDetails(
                    totalDetected = 5,
                    validated = 5,
                    falsePositives = 3,
                    averageConfidence = 0.4
                ),
                IssueType.UNRESOLVED_DEPENDENCY to ValidationDetails(
                    totalDetected = 5,
                    validated = 5,
                    falsePositives = 3,
                    averageConfidence = 0.5
                )
            )
        )

        val report = statisticalService.generateAccuracyReport(metrics, 14) // 4 TP + 6 FP + 4 others

        assertContains(report, "Precision: 40.0%") // 4/(4+6)
        assertContains(report, "Recall: 66.7%") // 4/(4+2)
        assertContains(report, "False Positive Rate: 42.9%") // 6/14
        assertContains(report, "Statistical Error: 133.3%") // |14-6|/6
        assertContains(report, "🔍 Validation Details by Issue Type:")
        assertContains(report, "CIRCULAR_DEPENDENCY:")
        assertContains(report, "Accuracy: 40.0%") // (5-3)/5
        assertContains(report, "❌ POOR: Analysis accuracy is below acceptable levels")
    }

    @Test
    fun testEstimateExpectedIssues_SmallProject() {
        val components = listOf(
            createSimpleComponent("Component1", 2, 1), // 2 deps, 1 provider
            createSimpleComponent("Component2", 1, 0), // 1 dep, 0 providers
            createSimpleComponent("Component3", 0, 2)  // 0 deps, 2 providers
        )

        val expectedIssues = statisticalService.estimateExpectedIssues(components)

        // Small project (<=20 components): 1% dep issues + 2% provider issues + 0 complexity
        // (2+1+0) * 0.01 + (1+0+2) * 0.02 + 0 = 0.03 + 0.06 = 0.09 -> 0 (rounded)
        assertTrue("Should estimate few issues for small project", expectedIssues <= 2)
    }

    @Test
    fun testEstimateExpectedIssues_MediumProject() {
        val components = (1..60).map { i ->
            createSimpleComponent("Component$i", 3, 1) // Each has 3 deps, 1 provider
        }

        val expectedIssues = statisticalService.estimateExpectedIssues(components)

        // Medium project (50-100 components): 2% dep issues + 2% provider issues + 2 complexity
        // 60 * 3 * 0.02 + 60 * 1 * 0.02 + 2 = 3.6 + 1.2 + 2 = 6.8 -> ~7
        assertTrue("Should estimate moderate issues for medium project", expectedIssues >= 5 && expectedIssues <= 10)
    }

    @Test
    fun testEstimateExpectedIssues_LargeProject() {
        val components = (1..150).map { i ->
            createSimpleComponent("Component$i", 4, 1) // Each has 4 deps, 1 provider
        }

        val expectedIssues = statisticalService.estimateExpectedIssues(components)

        // Large project (>100 components): 3% dep issues + 2% provider issues + 3 complexity
        // 150 * 4 * 0.03 + 150 * 1 * 0.02 + 3 = 18 + 3 + 3 = 24
        assertTrue("Should estimate many issues for large project", expectedIssues >= 20 && expectedIssues <= 30)
    }

    @Test
    fun testCompareWithPreviousAnalysis_Improving() {
        val previousMetrics = AccuracyMetrics(
            totalValidatedIssues = 10,
            truePositives = 6,
            falsePositives = 4,
            falseNegatives = 2
        )

        val currentMetrics = AccuracyMetrics(
            totalValidatedIssues = 10,
            truePositives = 9,
            falsePositives = 1,
            falseNegatives = 1
        )

        val trendReport = statisticalService.compareWithPreviousAnalysis(currentMetrics, previousMetrics)

        assertTrue("Should have comparison data", trendReport.hasComparison)
        assertTrue("Precision should improve", trendReport.precisionChange > 0.05)
        assertTrue("Recall should improve", trendReport.recallChange > 0.05)
        assertEquals(AccuracyTrend.IMPROVING, trendReport.trend)
        assertTrue("False positives should decrease", trendReport.falsePositiveChange < 0)
    }

    @Test
    fun testCompareWithPreviousAnalysis_Declining() {
        val previousMetrics = AccuracyMetrics(
            totalValidatedIssues = 10,
            truePositives = 9,
            falsePositives = 1,
            falseNegatives = 1
        )

        val currentMetrics = AccuracyMetrics(
            totalValidatedIssues = 10,
            truePositives = 5,
            falsePositives = 5,
            falseNegatives = 3
        )

        val trendReport = statisticalService.compareWithPreviousAnalysis(currentMetrics, previousMetrics)

        assertTrue("Should have comparison data", trendReport.hasComparison)
        assertTrue("Precision should decline", trendReport.precisionChange < -0.05)
        assertTrue("Recall should decline", trendReport.recallChange < -0.05)
        assertEquals(AccuracyTrend.DECLINING, trendReport.trend)
        assertTrue("False positives should increase", trendReport.falsePositiveChange > 0)
    }

    @Test
    fun testCompareWithPreviousAnalysis_NoData() {
        val currentMetrics = AccuracyMetrics(
            totalValidatedIssues = 5,
            truePositives = 4,
            falsePositives = 1
        )

        val trendReport = statisticalService.compareWithPreviousAnalysis(currentMetrics, null)

        assertFalse("Should not have comparison data", trendReport.hasComparison)
        assertEquals(0.0, trendReport.precisionChange, 0.001)
        assertEquals(0.0, trendReport.recallChange, 0.001)
        assertEquals(0, trendReport.falsePositiveChange)
        assertEquals(AccuracyTrend.NO_DATA, trendReport.trend)
    }

    private fun createSimpleComponent(name: String, depCount: Int, providerCount: Int): KnitComponent {
        val dependencies = (1..depCount).map { i ->
            KnitDependency(
                propertyName = "dep$i",
                targetType = "DepType$i",
                isNamed = false
            )
        }

        val providers = (1..providerCount).map { i ->
            KnitProvider(
                methodName = "provide$i",
                returnType = "ProviderType$i",
                isNamed = false,
                isSingleton = false
            )
        }

        return KnitComponent(
            className = name,
            packageName = "com.test",
            type = ComponentType.COMPONENT,
            dependencies = dependencies,
            providers = providers,
            sourceFile = "$name.kt"
        )
    }

    private fun assertContains(text: String, substring: String) {
        assertTrue("Expected '$text' to contain '$substring'", text.contains(substring))
    }
}