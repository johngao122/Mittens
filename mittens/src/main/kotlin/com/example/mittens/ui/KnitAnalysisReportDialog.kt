package com.example.mittens.ui

import com.example.mittens.export.GraphExportService
import com.example.mittens.model.AnalysisResult
import com.example.mittens.model.AnalysisSummary
import com.example.mittens.model.DetailedAnalysisReport
import com.example.mittens.model.IssuePreview
import com.example.mittens.model.Severity
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.io.File
import javax.swing.*
import javax.swing.filechooser.FileNameExtensionFilter

class KnitAnalysisReportDialog(
    private val project: Project,
    private val report: DetailedAnalysisReport,
    private val summary: AnalysisSummary,
    private val analysisResult: AnalysisResult
) : DialogWrapper(project) {

    private lateinit var reportTextArea: JBTextArea
    private lateinit var exportButton: JButton
    private lateinit var copyButton: JButton
    private lateinit var exportGraphButton: JButton

    init {
        title = "Knit Analysis - Detailed Report"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())
        mainPanel.preferredSize = Dimension(900, 600)


        val headerPanel = createHeaderPanel()
        mainPanel.add(headerPanel, BorderLayout.NORTH)


        val contentPanel = createContentPanel()
        mainPanel.add(contentPanel, BorderLayout.CENTER)


        val actionPanel = createActionPanel()
        mainPanel.add(actionPanel, BorderLayout.SOUTH)

        return mainPanel
    }

    private fun createHeaderPanel(): JComponent {
        val headerPanel = JPanel(BorderLayout())
        headerPanel.border = JBUI.Borders.empty(10)

        val healthScore = DetailedAnalysisReport.generateHealthScore(summary)
        val healthEmoji = DetailedAnalysisReport.getHealthEmoji(healthScore)

        val titleLabel = JLabel("Knit Analysis Results $healthEmoji Health Score: $healthScore/100")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 16f)

        val summaryText = buildString {
            append("Components: ${summary.totalComponents} • ")
            append("Dependencies: ${summary.totalDependencies} • ")
            append("Issues: ${summary.totalIssues} • ")
            append("Analysis Time: ${formatTime(summary.analysisTime)}")
        }

        val summaryLabel = JLabel(summaryText)
        summaryLabel.font = summaryLabel.font.deriveFont(Font.PLAIN, 12f)

        val issuesSummaryPanel = createIssuesSummaryPanel()

        val topPanel = JPanel(BorderLayout())
        topPanel.add(titleLabel, BorderLayout.NORTH)
        topPanel.add(summaryLabel, BorderLayout.CENTER)

        headerPanel.add(topPanel, BorderLayout.NORTH)
        headerPanel.add(issuesSummaryPanel, BorderLayout.CENTER)

        return headerPanel
    }

    private fun createIssuesSummaryPanel(): JComponent {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT))

        if (summary.errorCount > 0) {
            val errorLabel = JLabel("🔴 ${summary.errorCount} Errors")
            errorLabel.foreground = Color.RED.darker()
            panel.add(errorLabel)
        }

        if (summary.warningCount > 0) {
            val warningLabel = JLabel("🟡 ${summary.warningCount} Warnings")
            warningLabel.foreground = Color.ORANGE.darker()
            panel.add(warningLabel)
        }

        if (summary.infoCount > 0) {
            val infoLabel = JLabel("ℹ️ ${summary.infoCount} Info")
            infoLabel.foreground = Color.BLUE.darker()
            panel.add(infoLabel)
        }

        if (summary.totalIssues == 0) {
            val successLabel = JLabel("✨ No Issues Found!")
            successLabel.foreground = Color.GREEN.darker()
            successLabel.font = successLabel.font.deriveFont(Font.BOLD)
            panel.add(successLabel)
        }

        return panel
    }

    private fun createContentPanel(): JComponent {
        val tabbedPane = JTabbedPane()


        val overviewContent = createOverviewTab()
        tabbedPane.addTab("Overview", overviewContent)


        if (summary.totalIssues > 0) {
            val issuesContent = createIssuesTab()
            tabbedPane.addTab("Issues Details", issuesContent)
        }


        val rawReportContent = createRawReportTab()
        tabbedPane.addTab("Full Report", rawReportContent)

        return tabbedPane
    }

    private fun createOverviewTab(): JComponent {
        val panel = JPanel(BorderLayout())

        val overviewText = buildString {
            appendLine("=== Analysis Overview ===")
            appendLine()
            appendLine("Project Scan Results:")
            appendLine("• Files Scanned: ${summary.filesScanned}")
            appendLine("• Components Found: ${summary.totalComponents}")
            appendLine("• Total Dependencies: ${summary.totalDependencies}")
            appendLine("• Components with Issues: ${summary.componentsWithIssues}")
            appendLine("• Circular Dependencies: ${if (summary.hasCycles) "Yes ⚠️" else "No ✓"}")
            appendLine()

            if (summary.totalIssues > 0) {
                appendLine("=== Issue Breakdown ===")
                summary.issueBreakdown.forEach { (type, count) ->
                    appendLine("• ${formatIssueType(type)}: $count")
                }
                appendLine()

                if (summary.topIssues.isNotEmpty()) {
                    appendLine("=== Top Priority Issues ===")
                    summary.topIssues.forEachIndexed { index, issue ->
                        appendLine("${index + 1}. [${issue.severity}] ${issue.type}")
                        appendLine("   Component: ${issue.componentName}")
                        appendLine("   Issue: ${issue.message}")
                        if (issue.suggestedFix != null) {
                            appendLine("   Fix: ${issue.suggestedFix}")
                        }
                        appendLine()
                    }
                }
            }

            append(generateIssueSpecificRecommendations())
        }

        val textArea = JBTextArea(overviewText)
        textArea.isEditable = false
        textArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)

        val scrollPane = JBScrollPane(textArea)
        panel.add(scrollPane, BorderLayout.CENTER)

        return panel
    }

    private fun createIssuesTab(): JComponent {
        val panel = JPanel(BorderLayout())

        val issuesText = generateDetailedIssueAnalysis()

        val textArea = JBTextArea(issuesText)
        textArea.isEditable = false
        textArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)

        val scrollPane = JBScrollPane(textArea)
        panel.add(scrollPane, BorderLayout.CENTER)

        return panel
    }

    private fun createRawReportTab(): JComponent {
        val panel = JPanel(BorderLayout())

        reportTextArea = JBTextArea(report.generateExpandedDetails())
        reportTextArea.isEditable = false
        reportTextArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)

        val scrollPane = JBScrollPane(reportTextArea)
        panel.add(scrollPane, BorderLayout.CENTER)

        return panel
    }

    private fun createActionPanel(): JComponent {
        val panel = JPanel(FlowLayout(FlowLayout.RIGHT))

        copyButton = JButton("Copy to Clipboard")
        copyButton.addActionListener { copyReportToClipboard() }

        exportButton = JButton("Export Report")
        exportButton.addActionListener { exportReportToFile() }

        exportGraphButton = JButton("Export Graph JSON")
        exportGraphButton.addActionListener { exportGraphToFile() }

        panel.add(copyButton)
        panel.add(exportButton)
        panel.add(exportGraphButton)

        return panel
    }

    private fun copyReportToClipboard() {
        val reportContent = report.generateExpandedDetails()
        val stringSelection = StringSelection(reportContent)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(stringSelection, null)

        Messages.showInfoMessage(
            project,
            "Report copied to clipboard successfully!",
            "Copy Successful"
        )
    }

    private fun exportReportToFile() {
        val fileChooser = JFileChooser()
        fileChooser.dialogTitle = "Export Knit Analysis Report"
        fileChooser.selectedFile = File("knit-analysis-report.txt")

        val result = fileChooser.showSaveDialog(contentPane)
        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                val file = fileChooser.selectedFile
                val reportContent = buildString {
                    appendLine("Knit Analysis Report")
                    appendLine("Generated: ${java.time.LocalDateTime.now()}")
                    appendLine("Project: ${project.name}")
                    appendLine("=" * 50)
                    appendLine()
                    append(report.generateExpandedDetails())
                }

                file.writeText(reportContent)
                Messages.showInfoMessage(
                    project,
                    "Report exported successfully to: ${file.absolutePath}",
                    "Export Successful"
                )
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    project,
                    "Failed to export report: ${e.message}",
                    "Export Failed"
                )
            }
        }
    }

    private fun exportGraphToFile() {
        val fileChooser = JFileChooser()
        fileChooser.dialogTitle = "Export Dependency Graph JSON"
        fileChooser.selectedFile = File("${project.name}-dependency-graph.json")
        
        
        val jsonFilter = FileNameExtensionFilter("JSON files (*.json)", "json")
        fileChooser.fileFilter = jsonFilter
        fileChooser.addChoosableFileFilter(jsonFilter)

        val result = fileChooser.showSaveDialog(contentPane)
        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                val file = fileChooser.selectedFile
                
                val finalFile = if (!file.name.endsWith(".json")) {
                    File(file.parentFile, "${file.nameWithoutExtension}.json")
                } else {
                    file
                }
                
                
                val graphExportService = project.service<GraphExportService>()
                val graphExport = graphExportService.exportToJson(analysisResult)
                
                
                val objectMapper = jacksonObjectMapper()
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(finalFile, graphExport)
                
                Messages.showInfoMessage(
                    project,
                    "Dependency graph exported successfully to: ${finalFile.absolutePath}",
                    "Export Successful"
                )
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    project,
                    "Failed to export dependency graph: ${e.message}",
                    "Export Failed"
                )
            }
        }
    }

    private fun generateIssueSpecificRecommendations(): String {
        return buildString {
            appendLine("=== Issue-Specific Recommendations ===")
            
            if (summary.topIssues.isEmpty()) {
                appendLine("✨ Excellent! Your Knit dependency injection setup is clean and well-structured.")
                return@buildString
            }
            
            val issuesByType = summary.topIssues.groupBy { it.type }
            
            issuesByType.forEach { (issueType, issues) ->
                appendLine("${getIssueTypeIcon(issueType)} ${formatIssueType(issueType)} (${issues.size} found):")
                
                when (issueType) {
                    com.example.mittens.model.IssueType.CIRCULAR_DEPENDENCY -> {
                        appendLine("   💡 Specific Actions:")
                        issues.forEach { issue ->
                            appendLine("   • ${issue.componentName}: ${issue.suggestedFix ?: "Break the cycle by introducing an interface or removing direct dependency"}")
                        }
                        appendLine("   📋 General Approach:")
                        appendLine("   • Extract common interfaces to break direct dependencies")
                        appendLine("   • Use dependency injection patterns instead of direct instantiation")
                        appendLine("   • Consider using mediator pattern for complex cycles")
                    }
                    
                    com.example.mittens.model.IssueType.UNRESOLVED_DEPENDENCY -> {
                        appendLine("   💡 Specific Actions:")
                        issues.forEach { issue ->
                            appendLine("   • ${issue.componentName}: ${issue.suggestedFix ?: "Add provider for missing dependency"}")
                        }
                        appendLine("   📋 General Approach:")
                        appendLine("   • Add @Provides methods for missing dependencies")
                        appendLine("   • Check import statements and package visibility")
                        appendLine("   • Verify component registration in your DI setup")
                    }
                    
                    com.example.mittens.model.IssueType.SINGLETON_VIOLATION -> {
                        appendLine("   💡 Specific Actions:")
                        issues.forEach { issue ->
                            appendLine("   • ${issue.componentName}: ${issue.suggestedFix ?: "Ensure consistent singleton usage"}")
                        }
                        appendLine("   📋 General Approach:")
                        appendLine("   • Use @Singleton annotation consistently")
                        appendLine("   • Remove duplicate providers for same type")
                        appendLine("   • Review component lifecycle requirements")
                    }
                    
                    com.example.mittens.model.IssueType.NAMED_QUALIFIER_MISMATCH -> {
                        appendLine("   💡 Specific Actions:")
                        issues.forEach { issue ->
                            appendLine("   • ${issue.componentName}: ${issue.suggestedFix ?: "Check @Named qualifier spelling"}")
                        }
                        appendLine("   📋 General Approach:")
                        appendLine("   • Verify @Named annotations match exactly between providers and consumers")
                        appendLine("   • Use constants or enum for qualifier names to avoid typos")
                        appendLine("   • Consider using type-safe qualifiers")
                    }
                    
                    com.example.mittens.model.IssueType.AMBIGUOUS_PROVIDER -> {
                        appendLine("   💡 Specific Actions:")
                        issues.forEach { issue ->
                            appendLine("   • ${issue.componentName}: ${issue.suggestedFix ?: "Add @Named qualifiers to distinguish providers"}")
                        }
                        appendLine("   📋 General Approach:")
                        appendLine("   • Use @Named qualifiers to distinguish multiple providers of same type")
                        appendLine("   • Remove unnecessary duplicate providers")
                        appendLine("   • Consider using @Primary for default provider")
                    }
                    
                    com.example.mittens.model.IssueType.MISSING_COMPONENT_ANNOTATION -> {
                        appendLine("   💡 Specific Actions:")
                        issues.forEach { issue ->
                            appendLine("   • ${issue.componentName}: ${issue.suggestedFix ?: "Add appropriate component annotation"}")
                        }
                        appendLine("   📋 General Approach:")
                        appendLine("   • Add @Component annotation to classes that provide dependencies")
                        appendLine("   • Ensure all components are properly registered")
                        appendLine("   • Review component scanning configuration")
                    }
                }
                
                appendLine()
            }
            
            if (summary.errorCount > 0) {
                appendLine("⚠️ Priority: Address ERROR-level issues first to ensure compilation success")
            }
            if (summary.warningCount > 0) {
                appendLine("📈 Next: Review WARNING-level issues to improve code quality")
            }
        }
    }
    
    private fun getIssueTypeIcon(type: com.example.mittens.model.IssueType): String {
        return when (type) {
            com.example.mittens.model.IssueType.CIRCULAR_DEPENDENCY -> "🔄"
            com.example.mittens.model.IssueType.UNRESOLVED_DEPENDENCY -> "❓"
            com.example.mittens.model.IssueType.SINGLETON_VIOLATION -> "🔁"
            com.example.mittens.model.IssueType.NAMED_QUALIFIER_MISMATCH -> "🏷️"
            com.example.mittens.model.IssueType.AMBIGUOUS_PROVIDER -> "🎯"
            com.example.mittens.model.IssueType.MISSING_COMPONENT_ANNOTATION -> "📝"
        }
    }
    
    private fun generateDetailedIssueAnalysis(): String {
        return buildString {
            appendLine("=== Detailed Issue Solutions ===")
            appendLine()
            
            if (summary.topIssues.isEmpty()) {
                appendLine("✨ No issues found! Your dependency injection setup is clean.")
                return@buildString
            }
            
            val groupedIssues = summary.topIssues.groupBy { it.severity }
            
            groupedIssues[Severity.ERROR]?.let { errors ->
                appendLine("🔴 CRITICAL ERRORS (${errors.size}) - Fix these first:")
                appendLine("=" * 60)
                errors.forEachIndexed { index, issue ->
                    appendLine("${index + 1}. ${issue.type}")
                    appendLine("   🏢 Component: ${issue.componentName}")
                    appendLine("   ❌ Problem: ${issue.message}")
                    if (!issue.suggestedFix.isNullOrBlank()) {
                        appendLine("   💡 Solution: ${issue.suggestedFix}")
                    }
                    appendLine("   📍 Impact: This prevents compilation and must be resolved")
                    appendLine()
                }
                appendLine()
            }
            
            groupedIssues[Severity.WARNING]?.let { warnings ->
                appendLine("🟡 WARNINGS (${warnings.size}) - Address after fixing errors:")
                appendLine("=" * 50)
                warnings.forEachIndexed { index, issue ->
                    appendLine("${index + 1}. ${issue.type}")
                    appendLine("   🏢 Component: ${issue.componentName}")
                    appendLine("   ⚠️ Problem: ${issue.message}")
                    if (!issue.suggestedFix.isNullOrBlank()) {
                        appendLine("   💡 Solution: ${issue.suggestedFix}")
                    }
                    appendLine("   📍 Impact: Affects code quality and maintainability")
                    appendLine()
                }
                appendLine()
            }
            
            groupedIssues[Severity.INFO]?.let { infos ->
                appendLine("ℹ️ INFORMATION (${infos.size}) - Consider these improvements:")
                appendLine("=" * 45)
                infos.forEachIndexed { index, issue ->
                    appendLine("${index + 1}. ${issue.type}")
                    appendLine("   🏢 Component: ${issue.componentName}")
                    appendLine("   ℹ️ Info: ${issue.message}")
                    if (!issue.suggestedFix.isNullOrBlank()) {
                        appendLine("   💡 Suggestion: ${issue.suggestedFix}")
                    }
                    appendLine("   📍 Impact: Optional improvements for better practices")
                    appendLine()
                }
            }
        }
    }
    
    private fun formatTime(millis: Long): String {
        return when {
            millis < 1000 -> "${millis}ms"
            millis < 60000 -> "${"%.1f".format(millis / 1000.0)}s"
            else -> "${millis / 60000}m ${(millis % 60000) / 1000}s"
        }
    }

    private fun formatIssueType(type: com.example.mittens.model.IssueType): String {
        return when (type) {
            com.example.mittens.model.IssueType.CIRCULAR_DEPENDENCY -> "Circular Dependencies"
            com.example.mittens.model.IssueType.AMBIGUOUS_PROVIDER -> "Ambiguous Providers"
            com.example.mittens.model.IssueType.UNRESOLVED_DEPENDENCY -> "Unresolved Dependencies"
            com.example.mittens.model.IssueType.SINGLETON_VIOLATION -> "Singleton Violations"
            com.example.mittens.model.IssueType.NAMED_QUALIFIER_MISMATCH -> "Qualifier Mismatches"
            com.example.mittens.model.IssueType.MISSING_COMPONENT_ANNOTATION -> "Missing Annotations"
        }
    }

    override fun createActions(): Array<Action> {
        return arrayOf(okAction)
    }

    override fun getOKAction(): Action {
        val okAction = super.getOKAction()
        okAction.putValue(Action.NAME, "Close")
        return okAction
    }
}

private operator fun String.times(n: Int): String = this.repeat(n)