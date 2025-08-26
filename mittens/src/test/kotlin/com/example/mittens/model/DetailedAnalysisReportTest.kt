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
        
        val report = DetailedAnalysisReport(summary)
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
                type = IssueType.UNRESOLVED_DEPENDENCY,
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
                IssueType.CIRCULAR_DEPENDENCY to 1,
                IssueType.UNRESOLVED_DEPENDENCY to 1,
                IssueType.SINGLETON_VIOLATION to 2,
                IssueType.NAMED_QUALIFIER_MISMATCH to 1
            ),
            topIssues = topIssues,
            filesScanned = 25,
            componentsWithIssues = 3
        )
        
        val report = DetailedAnalysisReport(summary)
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
                IssueType.CIRCULAR_DEPENDENCY to 1,
                IssueType.UNRESOLVED_DEPENDENCY to 1
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
        
        val report = DetailedAnalysisReport(summary)
        val expandedDetails = report.generateExpandedDetails()
        
        assertContains(expandedDetails, "=== Knit Analysis Detailed Report ===")
        assertContains(expandedDetails, "=== Project Scan Summary ===")
        assertContains(expandedDetails, "Components Found: 3")
        assertContains(expandedDetails, "Components with Issues: 2")
        assertContains(expandedDetails, "Circular Dependencies: Yes ⚠️")
        assertContains(expandedDetails, "=== Issue Breakdown by Type ===")
        assertContains(expandedDetails, "🔄 Circular Dependencies: 1")
        assertContains(expandedDetails, "🔴 CRITICAL ERRORS")
    }
    
    @Test
    fun testHealthScoreCalculation() {
        // Perfect health
        val perfectSummary = AnalysisSummary(
            totalComponents = 10,
            totalDependencies = 15,
            totalIssues = 0,
            errorCount = 0,
            warningCount = 0,
            infoCount = 0,
            hasCycles = false,
            analysisTime = 1000,
            componentsWithIssues = 0
        )
        
        val perfectScore = DetailedAnalysisReport.generateHealthScore(perfectSummary)
        assertEquals(100, perfectScore)
        
        // Poor health
        val poorSummary = AnalysisSummary(
            totalComponents = 10,
            totalDependencies = 15,
            totalIssues = 20,
            errorCount = 5,
            warningCount = 10,
            infoCount = 5,
            hasCycles = true,
            analysisTime = 1000,
            componentsWithIssues = 8
        )
        
        val poorScore = DetailedAnalysisReport.generateHealthScore(poorSummary)
        assertTrue(poorScore < 50)
    }
    
    @Test
    fun testFormatIssueForQuickInfo() {
        val issue = IssuePreview(
            type = IssueType.UNRESOLVED_DEPENDENCY,
            severity = Severity.ERROR,
            message = "No provider found for DatabaseService",
            componentName = "OrderProcessor"
        )
        
        val formatted = DetailedAnalysisReport.formatIssueForQuickInfo(issue)
        
        assertContains(formatted, "[ERROR]")
        assertContains(formatted, "No provider found for this dependency")
        assertContains(formatted, "No provider found for DatabaseService")
    }
}