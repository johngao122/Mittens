package com.example.mittens.model

import org.junit.Test
import org.junit.Assert.*

class AnalysisResultTest {
    
    @Test
    fun testAnalysisResultCreation() {
        val components = listOf(
            KnitComponent(
                className = "TestComponent", 
                packageName = "com.test",
                type = ComponentType.COMPONENT,
                dependencies = emptyList(),
                providers = emptyList(),
                sourceFile = "TestComponent.kt"
            )
        )
        val dependencyGraph = DependencyGraph(emptyList(), emptyList())
        val issues = listOf(
            KnitIssue(
                type = IssueType.MISSING_COMPONENT_ANNOTATION,
                severity = Severity.WARNING,
                message = "Test issue",
                componentName = "TestComponent"
            )
        )
        
        val result = AnalysisResult(
            components = components,
            dependencyGraph = dependencyGraph,
            issues = issues,
            timestamp = System.currentTimeMillis(),
            projectName = "TestProject"
        )
        
        assertEquals("Components should match", 1, result.components.size)
        assertEquals("Issues should match", 1, result.issues.size)
        assertEquals("Project name should match", "TestProject", result.projectName)
        assertNotNull("Timestamp should be set", result.timestamp)
    }
    
    @Test
    fun testGetIssuesByType() {
        val issues = listOf(
            KnitIssue(IssueType.UNRESOLVED_DEPENDENCY, Severity.ERROR, "Dependency issue", "Component1"),
            KnitIssue(IssueType.MISSING_COMPONENT_ANNOTATION, Severity.WARNING, "Component issue", "Component2"),
            KnitIssue(IssueType.UNRESOLVED_DEPENDENCY, Severity.INFO, "Another dependency issue", "Component3")
        )
        
        val result = AnalysisResult(
            components = emptyList(),
            dependencyGraph = DependencyGraph(emptyList(), emptyList()),
            issues = issues,
            timestamp = System.currentTimeMillis(),
            projectName = "TestProject"
        )
        
        val issuesByType = result.getIssuesByType()
        
        assertEquals("Should have 2 issue types", 2, issuesByType.size)
        assertEquals("Should have 2 dependency issues", 2, issuesByType[IssueType.UNRESOLVED_DEPENDENCY]?.size)
        assertEquals("Should have 1 component issue", 1, issuesByType[IssueType.MISSING_COMPONENT_ANNOTATION]?.size)
    }
    
    @Test
    fun testGetIssuesBySeverity() {
        val issues = listOf(
            KnitIssue(IssueType.UNRESOLVED_DEPENDENCY, Severity.ERROR, "Error issue", "Component1"),
            KnitIssue(IssueType.MISSING_COMPONENT_ANNOTATION, Severity.WARNING, "Warning issue", "Component2"),
            KnitIssue(IssueType.UNRESOLVED_DEPENDENCY, Severity.WARNING, "Another warning", "Component3"),
            KnitIssue(IssueType.MISSING_COMPONENT_ANNOTATION, Severity.INFO, "Info issue", "Component4")
        )
        
        val result = AnalysisResult(
            components = emptyList(),
            dependencyGraph = DependencyGraph(emptyList(), emptyList()),
            issues = issues,
            timestamp = System.currentTimeMillis(),
            projectName = "TestProject"
        )
        
        val issuesBySeverity = result.getIssuesBySeverity()
        
        assertEquals("Should have 3 severity levels", 3, issuesBySeverity.size)
        assertEquals("Should have 1 error", 1, issuesBySeverity[Severity.ERROR]?.size)
        assertEquals("Should have 2 warnings", 2, issuesBySeverity[Severity.WARNING]?.size)
        assertEquals("Should have 1 info", 1, issuesBySeverity[Severity.INFO]?.size)
    }
    
    @Test
    fun testHasErrors() {
        val issuesWithErrors = listOf(
            KnitIssue(IssueType.UNRESOLVED_DEPENDENCY, Severity.ERROR, "Error", "Component1"),
            KnitIssue(IssueType.MISSING_COMPONENT_ANNOTATION, Severity.WARNING, "Warning", "Component2")
        )
        
        val issuesWithoutErrors = listOf(
            KnitIssue(IssueType.MISSING_COMPONENT_ANNOTATION, Severity.WARNING, "Warning", "Component1"),
            KnitIssue(IssueType.UNRESOLVED_DEPENDENCY, Severity.INFO, "Info", "Component2")
        )
        
        val resultWithErrors = AnalysisResult(
            components = emptyList(),
            dependencyGraph = DependencyGraph(emptyList(), emptyList()),
            issues = issuesWithErrors,
            timestamp = System.currentTimeMillis(),
            projectName = "TestProject"
        )
        
        val resultWithoutErrors = AnalysisResult(
            components = emptyList(),
            dependencyGraph = DependencyGraph(emptyList(), emptyList()),
            issues = issuesWithoutErrors,
            timestamp = System.currentTimeMillis(),
            projectName = "TestProject"
        )
        
        assertTrue("Should have errors", resultWithErrors.hasErrors())
        assertFalse("Should not have errors", resultWithoutErrors.hasErrors())
    }
    
    @Test
    fun testHasWarnings() {
        val issuesWithWarnings = listOf(
            KnitIssue(IssueType.MISSING_COMPONENT_ANNOTATION, Severity.WARNING, "Warning", "Component1"),
            KnitIssue(IssueType.UNRESOLVED_DEPENDENCY, Severity.INFO, "Info", "Component2")
        )
        
        val issuesWithoutWarnings = listOf(
            KnitIssue(IssueType.UNRESOLVED_DEPENDENCY, Severity.ERROR, "Error", "Component1"),
            KnitIssue(IssueType.MISSING_COMPONENT_ANNOTATION, Severity.INFO, "Info", "Component2")
        )
        
        val resultWithWarnings = AnalysisResult(
            components = emptyList(),
            dependencyGraph = DependencyGraph(emptyList(), emptyList()),
            issues = issuesWithWarnings,
            timestamp = System.currentTimeMillis(),
            projectName = "TestProject"
        )
        
        val resultWithoutWarnings = AnalysisResult(
            components = emptyList(),
            dependencyGraph = DependencyGraph(emptyList(), emptyList()),
            issues = issuesWithoutWarnings,
            timestamp = System.currentTimeMillis(),
            projectName = "TestProject"
        )
        
        assertTrue("Should have warnings", resultWithWarnings.hasWarnings())
        assertFalse("Should not have warnings", resultWithoutWarnings.hasWarnings())
    }
    
    @Test
    fun testGetSummary() {
        val components = listOf(
            KnitComponent(
                className = "Component1",
                packageName = "com.test",
                type = ComponentType.COMPONENT,
                dependencies = emptyList(),
                providers = emptyList(),
                sourceFile = "Component1.kt"
            ),
            KnitComponent(
                className = "Component2",
                packageName = "com.test", 
                type = ComponentType.COMPONENT,
                dependencies = emptyList(),
                providers = emptyList(),
                sourceFile = "Component2.kt"
            )
        )

        val nodes = listOf(
            GraphNode("Component1", "Component1", NodeType.COMPONENT, "com.test"),
            GraphNode("Component2", "Component2", NodeType.COMPONENT, "com.test")
        )
        val edges = listOf(
            GraphEdge("Component1", "Component2", EdgeType.DEPENDENCY),
            GraphEdge("Component2", "Component1", EdgeType.DEPENDENCY)
        )
        val dependencyGraph = DependencyGraph(nodes, edges)

        val issues = listOf(
            KnitIssue(IssueType.UNRESOLVED_DEPENDENCY, Severity.ERROR, "Error", "Component1"),
            KnitIssue(IssueType.MISSING_COMPONENT_ANNOTATION, Severity.WARNING, "Warning", "Component2"),
            KnitIssue(IssueType.UNRESOLVED_DEPENDENCY, Severity.INFO, "Info", "Component1")
        )
        
        val result = AnalysisResult(
            components = components,
            dependencyGraph = dependencyGraph,
            issues = issues,
            timestamp = System.currentTimeMillis(),
            projectName = "TestProject",
            metadata = AnalysisMetadata(analysisTimeMs = 1500)
        )
        
        val summary = result.getSummary()
        
        assertEquals("Total components should be 2", 2, summary.totalComponents)
        assertEquals("Total dependencies should be 2", 2, summary.totalDependencies)
        assertEquals("Total issues should be 3", 3, summary.totalIssues)
        assertEquals("Error count should be 1", 1, summary.errorCount)
        assertEquals("Warning count should be 1", 1, summary.warningCount)
        assertEquals("Info count should be 1", 1, summary.infoCount)
        assertEquals("Analysis time should be 1500", 1500, summary.analysisTime)
        assertTrue("Should detect cycles", summary.hasCycles)
    }
    
    @Test
    fun testAnalysisResultWithAccuracyMetrics() {
        val issues = listOf(
            KnitIssue(
                type = IssueType.CIRCULAR_DEPENDENCY, 
                severity = Severity.ERROR, 
                message = "Circular dependency", 
                componentName = "Component1",
                confidenceScore = 0.9,
                validationStatus = ValidationStatus.VALIDATED_TRUE_POSITIVE
            ),
            KnitIssue(
                type = IssueType.UNRESOLVED_DEPENDENCY, 
                severity = Severity.WARNING, 
                message = "False positive", 
                componentName = "Component2",
                confidenceScore = 0.2,
                validationStatus = ValidationStatus.VALIDATED_FALSE_POSITIVE
            )
        )
        
        val accuracyMetrics = AccuracyMetrics(
            totalValidatedIssues = 2,
            truePositives = 1,
            falsePositives = 1,
            falseNegatives = 0,
            expectedIssues = 1,
            validationEnabled = true,
            averageConfidenceScore = 0.55
        )
        
        val result = AnalysisResult(
            components = emptyList(),
            dependencyGraph = DependencyGraph(emptyList(), emptyList()),
            issues = issues,
            timestamp = System.currentTimeMillis(),
            projectName = "TestProject",
            accuracyMetrics = accuracyMetrics
        )
        
        assertEquals("Should have accuracy metrics", accuracyMetrics, result.accuracyMetrics)
        assertEquals("Should have 1 validated true positive", 1, result.getValidatedIssues().size)
        assertEquals("Should have 1 false positive", 1, result.getFalsePositives().size)
        
        val summary = result.getSummary()
        assertEquals("Summary should include accuracy metrics", accuracyMetrics, summary.accuracyMetrics)
        assertEquals(50.0, summary.getAccuracyPercentage(), 0.1) // 1 TP / 2 total = 50%
        assertEquals(50.0, summary.getFalsePositiveRate(), 0.1) // 1 FP / 2 total = 50%
        assertEquals(100.0, summary.getStatisticalError(), 0.1) // |2-1|/1 = 100%
    }
    
    @Test
    fun testAnalysisResultDefaultAccuracyMetrics() {
        val result = AnalysisResult(
            components = emptyList(),
            dependencyGraph = DependencyGraph(emptyList(), emptyList()),
            issues = emptyList(),
            timestamp = System.currentTimeMillis(),
            projectName = "TestProject"
        )
        
        assertNotNull("Should have default accuracy metrics", result.accuracyMetrics)
        assertFalse("Validation should be disabled by default", result.accuracyMetrics.validationEnabled)
        assertEquals("Should have empty validation details", 0, result.accuracyMetrics.issueValidationDetails.size)
    }
    
    @Test
    fun testIssuePreviewWithValidation() {
        val issue = KnitIssue(
            type = IssueType.AMBIGUOUS_PROVIDER,
            severity = Severity.WARNING,
            message = "This is a test issue with validation data",
            componentName = "TestComponent",
            confidenceScore = 0.75,
            validationStatus = ValidationStatus.VALIDATED_TRUE_POSITIVE
        )
        
        val result = AnalysisResult(
            components = emptyList(),
            dependencyGraph = DependencyGraph(emptyList(), emptyList()),
            issues = listOf(issue),
            timestamp = System.currentTimeMillis(),
            projectName = "TestProject"
        )
        
        val summary = result.getSummary()
        assertEquals("Should have 1 top issue", 1, summary.topIssues.size)
        
        val topIssue = summary.topIssues[0]
        assertEquals("Should preserve confidence score", 0.75, topIssue.confidenceScore, 0.001)
        assertEquals("Should preserve validation status", ValidationStatus.VALIDATED_TRUE_POSITIVE, topIssue.validationStatus)
    }
}