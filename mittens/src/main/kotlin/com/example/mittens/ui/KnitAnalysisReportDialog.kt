package com.example.mittens.ui

import com.example.mittens.export.GraphExportService
import com.example.mittens.model.AnalysisResult
import com.example.mittens.model.AnalysisSummary
import com.example.mittens.model.DetailedAnalysisReport
import com.example.mittens.model.IssuePreview
import com.example.mittens.model.Severity
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
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
import com.intellij.util.io.HttpRequests
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.Duration
import javax.swing.*
import javax.swing.filechooser.FileNameExtensionFilter

class KnitAnalysisReportDialog(
    private val project: Project,
    private val report: DetailedAnalysisReport,
    private val analysisResult: AnalysisResult
) : DialogWrapper(project) {
    private val summary: AnalysisSummary = report.summary

    private lateinit var reportTextArea: JBTextArea
    private lateinit var exportButton: JButton
    private lateinit var copyButton: JButton
    private lateinit var exportGraphButton: JButton
    private lateinit var exportToWebViewButton: JButton

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

        val titleLabel = JLabel("Knit Analysis Results")
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

                if (summary.allIssues.isNotEmpty()) {
                    appendLine("=== All Issues ===")
                    summary.allIssues.forEachIndexed { index, issue ->
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

        exportToWebViewButton = JButton("Export to Web View")
        exportToWebViewButton.addActionListener { exportToWebView() }

        panel.add(copyButton)
        panel.add(exportButton)
        panel.add(exportGraphButton)
        panel.add(exportToWebViewButton)

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
            
            if (summary.allIssues.isEmpty()) {
                appendLine("✨ Excellent! Your Knit dependency injection setup is clean and well-structured.")
                return@buildString
            }
            
            val issuesByType = summary.allIssues.groupBy { it.type }
            
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
            com.example.mittens.model.IssueType.AMBIGUOUS_PROVIDER -> "🎯"
        }
    }
    
    private fun generateDetailedIssueAnalysis(): String {
        return buildString {
            appendLine("=== Detailed Issue Solutions ===")
            appendLine()
            
            if (summary.allIssues.isEmpty()) {
                appendLine("✨ No issues found! Your dependency injection setup is clean.")
                return@buildString
            }
            
            val groupedIssues = summary.allIssues.groupBy { it.severity }
            
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
    
    private fun exportToWebView() {
        try {
            val graphExportService = project.service<GraphExportService>()
            val graphExport = graphExportService.exportToJson(analysisResult)
            
            // Convert the graph export to JSON string for HTTP POST
            val objectMapper = jacksonObjectMapper()
            val jsonString = objectMapper.writeValueAsString(graphExport)
            
            // Run HTTP POST in background thread with progress indicator
            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Exporting to Web View", true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.text = "Connecting to web view server..."
                    indicator.fraction = 0.3
                    
                    val webViewUrl = "http://localhost:3000"
                    val httpResult = sendDataToWebViewBackground(jsonString, webViewUrl, indicator)
                    
                    // Update UI on EDT after background operation
                    ApplicationManager.getApplication().invokeLater {
                        handleExportResult(httpResult, graphExport, objectMapper)
                    }
                }
                
                override fun onCancel() {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(
                            project,
                            "Export to web view was cancelled.",
                            "Export Cancelled"
                        )
                    }
                }
            })
            
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "Failed to start export to web view: ${e.message}",
                "Export Failed"
            )
        }
    }
    
    private data class HttpResult(
        val success: Boolean,
        val errorMessage: String? = null,
        val statusCode: Int? = null
    )
    
    private fun handleExportResult(httpResult: HttpResult, graphExport: Any, objectMapper: ObjectMapper) {
        try {
            if (httpResult.success) {
                // Save to a temporary file for backup/reference
                val tempDir = Files.createTempDirectory("knit-webview").toFile()
                val jsonFile = File(tempDir, "dependency-graph.json")
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonFile, graphExport)
                
                // Open the web view in a new editor tab
                val virtualFile = KnitWebViewVirtualFile()
                val fileEditorManager = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
                
                // Open the file in the editor
                fileEditorManager.openFile(virtualFile, true)
                
                Messages.showInfoMessage(
                    project,
                    "Analysis data successfully sent to web view!\nFile backup: ${jsonFile.absolutePath}\n\nThe dependency graph has been opened and data is available.",
                    "Export to Web View Successful"
                )
            } else {
                // Fallback: just save to file and show instructions
                val tempDir = Files.createTempDirectory("knit-webview").toFile()
                val jsonFile = File(tempDir, "dependency-graph.json")
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonFile, graphExport)
                
                val errorDetails = if (httpResult.statusCode != null) {
                    "HTTP ${httpResult.statusCode}: ${httpResult.errorMessage ?: "Unknown error"}"
                } else {
                    httpResult.errorMessage ?: "Connection failed"
                }
                
                Messages.showInfoMessage(
                    project,
                    "Could not connect to web view ($errorDetails).\nData exported to file: ${jsonFile.absolutePath}\n\nTo start web server: cd view/mittens && npm run dev",
                    "Export to File"
                )
            }
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "Failed to handle export result: ${e.message}",
                "Export Failed"
            )
        }
    }
    
    private fun sendDataToWebViewBackground(jsonData: String, webViewUrl: String, indicator: ProgressIndicator): HttpResult {
        // First try IntelliJ's HTTP client (more compatible with plugin environment)
        val intellijResult = tryIntellijHttpClient(jsonData, webViewUrl, indicator)
        if (intellijResult.success) {
            return intellijResult
        }
        
        // Fallback to standard Java HTTP client
        return tryStandardHttpClient(jsonData, webViewUrl, indicator)
    }
    
    private fun tryIntellijHttpClient(jsonData: String, webViewUrl: String, indicator: ProgressIndicator): HttpResult {
        return try {
            indicator.text = "Using IntelliJ HTTP client..."
            indicator.fraction = 0.4

            val url = "$webViewUrl/api/import-data"

            indicator.text = "Sending data to web view..."
            indicator.fraction = 0.7

            val response = HttpRequests
                .post(url, "application/json")
                .userAgent("IntelliJ-Knit-Plugin")
                .connectTimeout(10000)  // 10 seconds
                .readTimeout(15000)     // 15 seconds
                .connect { request ->
                    request.write(jsonData)
                    request.readString()
                }

            indicator.text = "Processing response..."
            indicator.fraction = 0.9

            // If we got here without exception, the request was successful
            indicator.text = "Export completed successfully!"
            indicator.fraction = 1.0
            HttpResult(success = true)

        } catch (e: java.net.ConnectException) {
            HttpResult(
                success = false,
                errorMessage = "Connection refused - server may not be running"
            )
        } catch (e: java.net.SocketTimeoutException) {
            HttpResult(
                success = false,
                errorMessage = "Connection timeout - server may be slow to respond"
            )
        } catch (e: java.io.IOException) {
            if (e.message?.contains("Connection refused") == true) {
                HttpResult(
                    success = false,
                    errorMessage = "Connection refused - server may not be running"
                )
            } else {
                HttpResult(
                    success = false,
                    errorMessage = "IO error: ${e.message}"
                )
            }
        } catch (e: Exception) {
            HttpResult(
                success = false,
                errorMessage = "IntelliJ HTTP client error: ${e.message}"
            )
        }
    }
    
    private fun tryStandardHttpClient(jsonData: String, webViewUrl: String, indicator: ProgressIndicator): HttpResult {
        return try {
            indicator.text = "Trying standard HTTP client..."
            indicator.fraction = 0.5
            
            val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build()
            
            indicator.text = "Sending data to web view..."
            indicator.fraction = 0.7
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$webViewUrl/api/import-data"))
                .header("Content-Type", "application/json")
                .header("User-Agent", "IntelliJ-Knit-Plugin")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(jsonData))
                .build()
            
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            
            indicator.text = "Processing response..."
            indicator.fraction = 0.9
            
            val success = response.statusCode() in 200..299
            
            if (success) {
                indicator.text = "Export completed successfully!"
                indicator.fraction = 1.0
                HttpResult(success = true)
            } else {
                HttpResult(
                    success = false,
                    statusCode = response.statusCode(),
                    errorMessage = response.body()
                )
            }
            
        } catch (e: java.net.ConnectException) {
            HttpResult(
                success = false,
                errorMessage = "Connection refused - server may not be running"
            )
        } catch (e: java.net.SocketTimeoutException) {
            HttpResult(
                success = false,
                errorMessage = "Connection timeout - server may be slow to respond"
            )
        } catch (e: java.net.UnknownHostException) {
            HttpResult(
                success = false,
                errorMessage = "Cannot resolve hostname: ${e.message}"
            )
        } catch (e: Exception) {
            HttpResult(
                success = false,
                errorMessage = "Standard HTTP client error: ${e.message}"
            )
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