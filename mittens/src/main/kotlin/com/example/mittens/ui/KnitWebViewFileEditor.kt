package com.example.mittens.ui

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.JBUI
import com.example.mittens.services.KnitAnalysisService
import com.example.mittens.export.GraphExportService
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import java.nio.file.Files
import java.util.*
import javax.swing.*

class KnitWebViewFileEditor(private val project: Project, private val virtualFile: VirtualFile) : FileEditor {
    
    private val logger = thisLogger()
    private var browser: JBCefBrowser? = null
    private lateinit var mainPanel: JPanel
    private lateinit var statusLabel: JLabel
    
    // Default URL for the Next.js dependency graph page
    private val defaultUrl = "http://localhost:3000/dependency"
    
    init {
        initializeComponents()
    }
    
    private fun initializeComponents() {
        mainPanel = JPanel(BorderLayout())
        
        // Create toolbar
        val toolbar = createToolbar()
        mainPanel.add(toolbar, BorderLayout.NORTH)
        
        // Create status bar
        statusLabel = JLabel("Ready")
        statusLabel.border = JBUI.Borders.empty(5, 10)
        mainPanel.add(statusLabel, BorderLayout.SOUTH)
        
        // Initialize browser
        initializeBrowser()
    }
    
    private fun createToolbar(): JComponent {
        val actionGroup = DefaultActionGroup()
        
        // Refresh browser action
        actionGroup.add(object : AnAction("Refresh", "Refresh the web view", null) {
            override fun actionPerformed(e: AnActionEvent) {
                refreshBrowser()
            }
        })
        
        // Export current analysis action
        actionGroup.add(object : AnAction("Export Analysis", "Export current analysis to web view", null) {
            override fun actionPerformed(e: AnActionEvent) {
                exportCurrentAnalysis()
            }
        })
        
        // Separator
        actionGroup.addSeparator()
        
        // Open in external browser action
        actionGroup.add(object : AnAction("Open in Browser", "Open in external browser", null) {
            override fun actionPerformed(e: AnActionEvent) {
                openInExternalBrowser()
            }
        })
        
        val toolbar = ActionManager.getInstance().createActionToolbar("KnitWebViewEditor", actionGroup, true)
        toolbar.targetComponent = mainPanel
        return toolbar.component
    }
    
    private fun initializeBrowser() {
        try {
            browser = JBCefBrowser(defaultUrl)
            browser?.let { browserInstance ->
                mainPanel.add(browserInstance.component, BorderLayout.CENTER)
                statusLabel.text = "Loading web view..."

                browserInstance.jbCefClient.addLoadHandler(object : org.cef.handler.CefLoadHandlerAdapter() {
                    override fun onLoadEnd(
                        browser: org.cef.browser.CefBrowser,
                        frame: org.cef.browser.CefFrame,
                        httpStatusCode: Int
                    ) {
                        SwingUtilities.invokeLater {
                            statusLabel.text = if (httpStatusCode == 200) {
                                "Connected to web view"
                            } else {
                                "Connection error (HTTP $httpStatusCode)"
                            }
                        }
                    }

                    override fun onLoadError(
                        browser: org.cef.browser.CefBrowser,
                        frame: org.cef.browser.CefFrame,
                        errorCode: org.cef.handler.CefLoadHandler.ErrorCode,
                        errorText: String,
                        failedUrl: String
                    ) {
                        SwingUtilities.invokeLater {
                            statusLabel.text = "Failed to load: $errorText"
                            showConnectionError()
                        }
                    }
                }, browserInstance.cefBrowser)
            }
        } catch (e: Exception) {
            logger.error("Failed to initialize JCEF browser", e)
            showBrowserError()
        }
    }
    
    private fun showBrowserError() {
        val errorPanel = JPanel(BorderLayout())
        errorPanel.add(JLabel("<html><center>" +
            "Web view is not available.<br>" +
            "Please ensure the Next.js application is running on port 3000.<br><br>" +
            "<b>To start the web application:</b><br>" +
            "cd view/mittens<br>" +
            "npm run dev" +
            "</center></html>", SwingConstants.CENTER), BorderLayout.CENTER)
        
        val retryButton = JButton("Retry Connection")
        retryButton.addActionListener { 
            mainPanel.removeAll()
            initializeComponents()
            mainPanel.revalidate()
            mainPanel.repaint()
        }
        
        val buttonPanel = JPanel()
        buttonPanel.add(retryButton)
        errorPanel.add(buttonPanel, BorderLayout.SOUTH)
        
        mainPanel.add(errorPanel, BorderLayout.CENTER)
        statusLabel.text = "Browser initialization failed"
    }
    
    private fun showConnectionError() {
        logger.warn("Connection to web view failed")
    }
    
    private fun refreshBrowser() {
        browser?.let { browser ->
            browser.loadURL(defaultUrl)
            statusLabel.text = "Refreshing..."
        }
    }
    
    private fun exportCurrentAnalysis() {
        try {
            val analysisService = project.service<KnitAnalysisService>()
            val lastAnalysisResult = analysisService.getLastAnalysisResult()
            
            if (lastAnalysisResult == null) {
                Messages.showInfoMessage(
                    project,
                    "No analysis results available. Please run Knit Analysis first.",
                    "Export to Web View"
                )
                return
            }
            
            // Export the analysis result to JSON
            val exportService = project.service<GraphExportService>()
            val jsonExport = exportService.exportToJson(lastAnalysisResult)
            
            // Save to a temporary file that the web app can access
            val tempDir = Files.createTempDirectory("knit-webview").toFile()
            val jsonFile = File(tempDir, "dependency-graph.json")
            
            // Use Jackson to write JSON
            val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build())
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonFile, jsonExport)
            
            statusLabel.text = "Analysis exported: ${jsonFile.absolutePath}"
            
            Messages.showInfoMessage(
                project,
                "Analysis exported successfully!\nFile: ${jsonFile.absolutePath}\n\nYou can now load this file in the web view.",
                "Export Successful"
            )
            
        } catch (e: Exception) {
            logger.error("Failed to export analysis to web view", e)
            Messages.showErrorDialog(
                project,
                "Failed to export analysis: ${e.message}",
                "Export Failed"
            )
        }
    }
    
    private fun openInExternalBrowser() {
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI(defaultUrl))
        } catch (e: Exception) {
            logger.warn("Failed to open external browser", e)
            Messages.showErrorDialog(
                project,
                "Failed to open external browser: ${e.message}",
                "Open Browser Failed"
            )
        }
    }
    
    // FileEditor interface methods
    override fun getComponent(): JComponent = mainPanel
    
    override fun getPreferredFocusedComponent(): JComponent? = browser?.component
    
    override fun getName(): String = "Knit Dependency Graph"
    
    override fun setState(state: FileEditorState) {
        // No state to restore
    }
    
    override fun isModified(): Boolean = false
    
    override fun isValid(): Boolean = true
    
    override fun addPropertyChangeListener(listener: java.beans.PropertyChangeListener) {
        // No properties to listen to
    }
    
    override fun removePropertyChangeListener(listener: java.beans.PropertyChangeListener) {
        // No properties to listen to
    }
    
    override fun dispose() {
        browser?.dispose()
    }
    
    override fun getFile(): VirtualFile = virtualFile
    
    override fun <T : Any?> getUserData(key: Key<T>): T? = null
    
    override fun <T : Any?> putUserData(key: Key<T>, value: T?) {
        // No user data
    }
    
    override fun getCurrentLocation(): FileEditorLocation? = null
    
    override fun getStructureViewBuilder(): StructureViewBuilder? = null
    
    override fun getBackgroundHighlighter(): BackgroundEditorHighlighter? = null
}