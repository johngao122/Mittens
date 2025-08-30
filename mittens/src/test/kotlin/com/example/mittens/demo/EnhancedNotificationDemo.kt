package com.example.mittens.demo

import com.example.mittens.model.*

/**
 * Demo class to showcase the enhanced notification format
 * This is not a test but a demonstration of the enhanced notification capabilities
 */
object EnhancedNotificationDemo {
    
    fun generateDemoNotification(): String {
        val topIssues = listOf(
            IssuePreview(
                type = IssueType.CIRCULAR_DEPENDENCY,
                severity = Severity.ERROR,
                message = "UserService → OrderService → UserService (circular)",
                componentName = "com.example.service.UserService"
            ),
            IssuePreview(
                type = IssueType.CIRCULAR_DEPENDENCY,
                severity = Severity.ERROR,
                message = "DatabaseService provider missing for OrderProcessor",
                componentName = "com.example.processor.OrderProcessor",
                suggestedFix = "Create a provider with @Provides annotation returning DatabaseService"
            ),
            IssuePreview(
                type = IssueType.AMBIGUOUS_PROVIDER,
                severity = Severity.ERROR,
                message = "Multiple @Singleton CacheService providers found",
                componentName = "Multiple components",
                suggestedFix = "Remove duplicate singleton providers"
            )
        )
        
        val summary = AnalysisSummary(
            totalComponents = 6,
            totalDependencies = 7,
            totalIssues = 9,
            errorCount = 7,
            warningCount = 2,
            infoCount = 0,
            hasCycles = true,
            analysisTime = 1234,
            issueBreakdown = mapOf(
                IssueType.CIRCULAR_DEPENDENCY to 2,
                IssueType.CIRCULAR_DEPENDENCY to 3,
                IssueType.AMBIGUOUS_PROVIDER to 2,
                IssueType.AMBIGUOUS_PROVIDER to 2
            ),
            topIssues = topIssues,
            filesScanned = 15,
            componentsWithIssues = 6
        )
        
        val analysisResult = AnalysisResult(
            components = emptyList(),
            dependencyGraph = DependencyGraph(emptyList(), emptyList()),
            issues = emptyList(),
            timestamp = System.currentTimeMillis(),
            projectName = "demo-project",
            metadata = AnalysisMetadata(
                analysisTimeMs = summary.analysisTime,
                sourceFilesScanned = summary.filesScanned,
                analysisMethod = AnalysisMethod.SOURCE_ANALYSIS
            )
        )
        val report = DetailedAnalysisReport(analysisResult)
        return report.generateNotificationMessage()
    }
    
    fun generateHealthyProjectDemo(): String {
        val summary = AnalysisSummary(
            totalComponents = 12,
            totalDependencies = 18,
            totalIssues = 0,
            errorCount = 0,
            warningCount = 0,
            infoCount = 0,
            hasCycles = false,
            analysisTime = 850,
            issueBreakdown = emptyMap(),
            topIssues = emptyList(),
            filesScanned = 25,
            componentsWithIssues = 0
        )
        
        val analysisResult = AnalysisResult(
            components = emptyList(),
            dependencyGraph = DependencyGraph(emptyList(), emptyList()),
            issues = emptyList(),
            timestamp = System.currentTimeMillis(),
            projectName = "demo-project",
            metadata = AnalysisMetadata(
                analysisTimeMs = summary.analysisTime,
                sourceFilesScanned = summary.filesScanned,
                analysisMethod = AnalysisMethod.SOURCE_ANALYSIS
            )
        )
        val report = DetailedAnalysisReport(analysisResult)
        return report.generateNotificationMessage()
    }
    
    fun generateDetailedReportDemo(): String {
        val topIssues = listOf(
            IssuePreview(
                type = IssueType.CIRCULAR_DEPENDENCY,
                severity = Severity.ERROR,
                message = "Service A depends on Service B which depends on Service A",
                componentName = "com.example.ServiceA",
                suggestedFix = "Introduce interface abstraction to break the cycle"
            )
        )
        
        val summary = AnalysisSummary(
            totalComponents = 8,
            totalDependencies = 12,
            totalIssues = 3,
            errorCount = 1,
            warningCount = 2,
            infoCount = 0,
            hasCycles = true,
            analysisTime = 1500,
            issueBreakdown = mapOf(
                IssueType.CIRCULAR_DEPENDENCY to 1,
                IssueType.AMBIGUOUS_PROVIDER to 2
            ),
            topIssues = topIssues,
            filesScanned = 20,
            componentsWithIssues = 3
        )
        
        val analysisResult = AnalysisResult(
            components = emptyList(),
            dependencyGraph = DependencyGraph(emptyList(), emptyList()),
            issues = emptyList(),
            timestamp = System.currentTimeMillis(),
            projectName = "demo-project",
            metadata = AnalysisMetadata(
                analysisTimeMs = summary.analysisTime,
                sourceFilesScanned = summary.filesScanned,
                analysisMethod = AnalysisMethod.SOURCE_ANALYSIS
            )
        )
        val report = DetailedAnalysisReport(analysisResult)
        return report.generateExpandedDetails()
    }
}

fun main() {
    println("=== Demo: Enhanced Plugin Notifications ===")
    println()
    
    println("1. Healthy Project Notification:")
    println(EnhancedNotificationDemo.generateHealthyProjectDemo())
    println()
    println("=" * 50)
    println()
    
    println("2. Project with Issues Notification:")
    println(EnhancedNotificationDemo.generateDemoNotification())
    println()
    println("=" * 50)
    println()
    
    println("3. Detailed Report Example:")
    println(EnhancedNotificationDemo.generateDetailedReportDemo())
}

private operator fun String.times(n: Int): String = this.repeat(n)