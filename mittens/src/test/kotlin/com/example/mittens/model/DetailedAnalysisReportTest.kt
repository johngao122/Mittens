package com.example.mittens.model

import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DetailedAnalysisReportTest {
    
    @Test
    fun testBasicNotificationMessage() {
        val summary = AnalysisSummary(
            totalComponents = 5,
            totalDependencies = 8,
            totalIssues = 0,
            errorCount = 0,
            warningCount = 0,
            infoCount = 0,
            hasCycles = false,
            analysisTime = 1200,
            issueBreakdown = emptyMap(),
            topIssues = emptyList(),
            filesScanned = 15,
            componentsWithIssues = 0
        )
        
        // Create components to match the expected count
        val components = List(5) { i ->
            KnitComponent(
                className = "TestComponent$i",
                packageName = "com.test",
                type = ComponentType.COMPONENT,
                dependencies = emptyList(),
                providers = emptyList(),
                sourceFile = "TestComponent$i.kt"
            )
        }
        
        // Create dependency graph with expected edge count
        val nodes = components.map { component ->
            GraphNode(
                id = "${component.packageName}.${component.className}",
                label = component.className,
                type = NodeType.COMPONENT,
                packageName = component.packageName
            )
        }
        
        val edges = List(8) { i ->
            GraphEdge(
                from = nodes[i % nodes.size].id,
                to = nodes[(i + 1) % nodes.size].id,
                type = EdgeType.DEPENDENCY,
                label = "dep$i"
            )
        }
        
        val dependencyGraph = DependencyGraph(nodes, edges)
        
        val analysisResult = AnalysisResult(
            components = components,
            dependencyGraph = dependencyGraph,
            issues = emptyList(),
            timestamp = System.currentTimeMillis(),
            projectName = "test-project",
            metadata = AnalysisMetadata(
                analysisTimeMs = summary.analysisTime,
                sourceFilesScanned = summary.filesScanned,
                analysisMethod = AnalysisMethod.SOURCE_ANALYSIS
            )
        )
        val report = DetailedAnalysisReport(analysisResult)
        val message = report.generateNotificationMessage()
        
        assertContains(message, "Knit Analysis Complete!")
        assertContains(message, "Components: 5")
        assertContains(message, "Dependencies: 8")
        assertContains(message, "1.2s")
        assertContains(message, "No issues found!")
    }
    
    @Test
    fun testNotificationMessageWithIssues() {
        val topIssues = listOf(
            IssuePreview(
                type = IssueType.CIRCULAR_DEPENDENCY,
                severity = Severity.ERROR,
                message = "Circular dependency detected: UserService → OrderService → UserService",
                componentName = "com.example.UserService"
            ),
            IssuePreview(
                type = IssueType.CIRCULAR_DEPENDENCY,
                severity = Severity.ERROR,
                message = "No provider found for dependency: DatabaseService",
                componentName = "com.example.OrderProcessor",
                suggestedFix = "Create a provider with @Provides annotation"
            )
        )
        
        val summary = AnalysisSummary(
            totalComponents = 10,
            totalDependencies = 15,
            totalIssues = 5,
            errorCount = 2,
            warningCount = 2,
            infoCount = 1,
            hasCycles = true,
            analysisTime = 2500,
            issueBreakdown = mapOf(
                IssueType.CIRCULAR_DEPENDENCY to 2,
                IssueType.AMBIGUOUS_PROVIDER to 3
            ),
            topIssues = topIssues,
            filesScanned = 25,
            componentsWithIssues = 3
        )
        
        // Create components to match the expected count
        val components = List(10) { i ->
            KnitComponent(
                className = "TestComponent$i",
                packageName = "com.test",
                type = ComponentType.COMPONENT,
                dependencies = emptyList(),
                providers = emptyList(),
                sourceFile = "TestComponent$i.kt"
            )
        }
        
        // Create dependency graph with expected edge count
        val nodes = components.map { component ->
            GraphNode(
                id = "${component.packageName}.${component.className}",
                label = component.className,
                type = NodeType.COMPONENT,
                packageName = component.packageName
            )
        }
        
        val edges = List(15) { i ->
            GraphEdge(
                from = nodes[i % nodes.size].id,
                to = nodes[(i + 1) % nodes.size].id,
                type = EdgeType.DEPENDENCY,
                label = "dep$i"
            )
        }
        
        val dependencyGraph = DependencyGraph(nodes, edges)
        
        // Create issues to match the expected count and types
        val issues = listOf(
            KnitIssue(
                type = IssueType.CIRCULAR_DEPENDENCY,
                severity = Severity.ERROR,
                message = "Circular dependency detected: UserService → OrderService → UserService",
                componentName = "com.example.UserService",
                suggestedFix = "Break the cycle by extracting interfaces",
                confidenceScore = 1.0,
                validationStatus = ValidationStatus.NOT_VALIDATED
            ),
            KnitIssue(
                type = IssueType.CIRCULAR_DEPENDENCY,
                severity = Severity.ERROR,
                message = "No provider found for dependency: DatabaseService",
                componentName = "com.example.OrderProcessor",
                suggestedFix = "Create a provider with @Provides annotation",
                confidenceScore = 1.0,
                validationStatus = ValidationStatus.NOT_VALIDATED
            ),
            KnitIssue(
                type = IssueType.AMBIGUOUS_PROVIDER,
                severity = Severity.WARNING,
                message = "Multiple providers found for DatabaseService",
                componentName = "com.example.DatabaseProvider",
                suggestedFix = "Use @Named qualifiers",
                confidenceScore = 0.9,
                validationStatus = ValidationStatus.NOT_VALIDATED
            ),
            KnitIssue(
                type = IssueType.AMBIGUOUS_PROVIDER,
                severity = Severity.WARNING,
                message = "Duplicate provider for UserService",
                componentName = "com.example.UserProvider",
                suggestedFix = "Remove duplicate provider",
                confidenceScore = 0.9,
                validationStatus = ValidationStatus.NOT_VALIDATED
            ),
            KnitIssue(
                type = IssueType.AMBIGUOUS_PROVIDER,
                severity = Severity.INFO,
                message = "Consider using @Primary for default provider",
                componentName = "com.example.DefaultProvider",
                suggestedFix = "Add @Primary annotation",
                confidenceScore = 0.8,
                validationStatus = ValidationStatus.NOT_VALIDATED
            )
        )
        
        val analysisResult = AnalysisResult(
            components = components,
            dependencyGraph = dependencyGraph,
            issues = issues,
            timestamp = System.currentTimeMillis(),
            projectName = "test-project",
            metadata = AnalysisMetadata(
                analysisTimeMs = summary.analysisTime,
                sourceFilesScanned = summary.filesScanned,
                analysisMethod = AnalysisMethod.SOURCE_ANALYSIS
            )
        )
        val report = DetailedAnalysisReport(analysisResult)
        val message = report.generateNotificationMessage()
        
        assertContains(message, "ISSUES FOUND (5 total)")
        assertContains(message, "🔴 Errors (2)")
        assertContains(message, "🟡 Warnings (2)")
        assertContains(message, "Top Issues:")
        assertContains(message, "UserService → OrderService → UserService")
        assertContains(message, "DatabaseService")
    }
    
    @Test
    fun testExpandedDetailsReport() {
        val summary = AnalysisSummary(
            totalComponents = 3,
            totalDependencies = 4,
            totalIssues = 2,
            errorCount = 1,
            warningCount = 1,
            infoCount = 0,
            hasCycles = true,
            analysisTime = 800,
            issueBreakdown = mapOf(
                IssueType.CIRCULAR_DEPENDENCY to 2
            ),
            topIssues = listOf(
                IssuePreview(
                    type = IssueType.CIRCULAR_DEPENDENCY,
                    severity = Severity.ERROR,
                    message = "Test circular dependency",
                    componentName = "TestComponent"
                )
            ),
            filesScanned = 10,
            componentsWithIssues = 2
        )
        
        // Create components to match the expected count
        val components = List(3) { i ->
            KnitComponent(
                className = "TestComponent$i",
                packageName = "com.test",
                type = ComponentType.COMPONENT,
                dependencies = emptyList(),
                providers = emptyList(),
                sourceFile = "TestComponent$i.kt"
            )
        }
        
        // Create dependency graph with expected edge count and cycles
        val nodes = components.map { component ->
            GraphNode(
                id = "${component.packageName}.${component.className}",
                label = component.className,
                type = NodeType.COMPONENT,
                packageName = component.packageName
            )
        }
        
        val edges = List(4) { i ->
            GraphEdge(
                from = nodes[i % nodes.size].id,
                to = nodes[(i + 1) % nodes.size].id,
                type = EdgeType.DEPENDENCY,
                label = "dep$i"
            )
        }
        
        val dependencyGraph = DependencyGraph(nodes, edges)
        
        // Create issues to match the expected count - use component names that will match
        val issues = listOf(
            KnitIssue(
                type = IssueType.CIRCULAR_DEPENDENCY,
                severity = Severity.ERROR,
                message = "Test circular dependency",
                componentName = "TestComponent0", // Match actual component name
                suggestedFix = "Break the cycle",
                confidenceScore = 1.0,
                validationStatus = ValidationStatus.NOT_VALIDATED
            ),
            KnitIssue(
                type = IssueType.CIRCULAR_DEPENDENCY,
                severity = Severity.WARNING,
                message = "Another circular dependency",
                componentName = "TestComponent1", // Match actual component name
                suggestedFix = "Extract interface",
                confidenceScore = 0.9,
                validationStatus = ValidationStatus.NOT_VALIDATED
            )
        )
        
        val analysisResult = AnalysisResult(
            components = components,
            dependencyGraph = dependencyGraph,
            issues = issues,
            timestamp = System.currentTimeMillis(),
            projectName = "test-project",
            metadata = AnalysisMetadata(
                analysisTimeMs = summary.analysisTime,
                sourceFilesScanned = summary.filesScanned,
                analysisMethod = AnalysisMethod.SOURCE_ANALYSIS
            )
        )
        val report = DetailedAnalysisReport(analysisResult)
        val expandedDetails = report.generateExpandedDetails()
        
        assertContains(expandedDetails, "=== Knit Analysis Detailed Report ===")
        assertContains(expandedDetails, "=== Project Scan Summary ===")
        assertContains(expandedDetails, "Components Found: 3")
        assertContains(expandedDetails, "Components with Issues: 2")
        assertContains(expandedDetails, "Circular Dependencies: Yes ⚠️")
        assertContains(expandedDetails, "=== Issue Breakdown by Type ===")
        assertContains(expandedDetails, "🔄 Circular Dependencies: 2")
        assertContains(expandedDetails, "🔴 CRITICAL ERRORS")
    }
    
    
    @Test
    fun testFormatIssueForQuickInfo() {
        val issue = IssuePreview(
            type = IssueType.CIRCULAR_DEPENDENCY,
            severity = Severity.ERROR,
            message = "No provider found for DatabaseService",
            componentName = "OrderProcessor"
        )
        
        val formatted = DetailedAnalysisReport.formatIssueForQuickInfo(issue)
        
        assertContains(formatted, "[ERROR]")
        assertContains(formatted, "Components depend on each other in a cycle")
        assertContains(formatted, "No provider found for DatabaseService")
    }
}