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
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBuilder
import com.intellij.ui.jcef.JBCefClient
import com.intellij.util.ui.JBUI
import com.example.mittens.services.KnitAnalysisService
import com.example.mittens.export.GraphExportService
import com.example.mittens.services.GradleTaskRunner
import com.example.mittens.services.KnitSourceAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.jcef.JBCefJSQuery
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import java.nio.file.Files
import java.util.*
import javax.swing.*

class KnitWebViewFileEditor(private val project: Project, private val virtualFile: VirtualFile) : FileEditor {
    
    private val logger = thisLogger()
    private var browser: JBCefBrowser? = null
    private var cefClient: JBCefClient? = null
    private lateinit var mainPanel: JPanel
    private lateinit var statusLabel: JLabel
    private var refreshQuery: JBCefJSQuery? = null
    
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
            // Create a dedicated client so we can configure properties before browser creation
            cefClient = JBCefApp.getInstance().createClient().apply {
                // Allow creating JS queries after the browser is created (required for our bridge)
                try { this.setProperty(JBCefClient.Properties.JS_QUERY_POOL_SIZE, 4) } catch (_: Throwable) {}
            }

            val client = cefClient ?: return
            browser = JBCefBrowserBuilder()
                .setClient(client)
                .setUrl(defaultUrl)
                .build()
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
                            // Re-inject JS mapping after load using existing query
                            injectJsMapping()
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

                // Create the JS bridge once and inject mapping
                installJsBridge()
            }
        } catch (e: Exception) {
            logger.error("Failed to initialize JCEF browser", e)
            showBrowserError()
        }
    }

    private fun installJsBridge() {
        val browserInstance = browser ?: return
        if (refreshQuery == null) {
            refreshQuery = JBCefJSQuery.create(browserInstance)
            refreshQuery?.addHandler { _ ->
                // Trigger build + export on background thread
                ApplicationManager.getApplication().executeOnPooledThread {
                    try {
                        runBuildAndExportToWeb()
                    } catch (e: Exception) {
                        logger.warn("Bridge refresh failed", e)
                    }
                }
                null
            }
        }
        injectJsMapping()
    }

    private fun injectJsMapping() {
        val browserInstance = browser ?: return
        val injected = refreshQuery?.inject("refresh") ?: return
        val js = """
            window.mittensBridge = window.mittensBridge || {};
            window.mittensBridge.refresh = function() { $injected };
        """.trimIndent()
        try {
            browserInstance.cefBrowser.executeJavaScript(js, browserInstance.cefBrowser.url, 0)
        } catch (e: Exception) {
            logger.warn("Failed to inject JS bridge", e)
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

    private fun runBuildAndExportToWeb() {
        SwingUtilities.invokeLater { statusLabel.text = "Running Gradle: shadowJarWithKnit..." }

        val gradle = project.getService(GradleTaskRunner::class.java)
        val result = gradle.runShadowJarWithKnit()
        if (!result.success) {
            SwingUtilities.invokeLater {
                statusLabel.text = "Gradle failed"
                Messages.showErrorDialog(project, "Gradle failed: ${result.errorOutput.take(4000)}", "Gradle Error")
            }
            return
        }

        try {
            SwingUtilities.invokeLater { statusLabel.text = "Analyzing + exporting graph..." }

            // Use knit.json if present (analyzer prefers it) and export consistent JSON
            val analysisService = project.getService(KnitAnalysisService::class.java)
            // Run analysis synchronously
            val analysisResult = kotlinx.coroutines.runBlocking {
                analysisService.runAnalysis()
            }

            val exportService = project.getService(GraphExportService::class.java)
            val graphJson = exportService.exportToJson(analysisResult)

            // POST to Next.js API
            postJsonToWebApp(graphJson)

            // Reload the web view to surface any UI changes; SSE will also update
            SwingUtilities.invokeLater {
                statusLabel.text = "Build + export complete"
                refreshBrowser()
            }
        } catch (e: Exception) {
            logger.error("Export to web failed", e)
            SwingUtilities.invokeLater {
                statusLabel.text = "Export failed"
                Messages.showErrorDialog(project, "Export failed: ${e.message}", "Export Error")
            }
        }
    }

    private fun postJsonToWebApp(payload: Any) {
        val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build())
        val jsonBytes = objectMapper.writeValueAsBytes(payload)

        val url = java.net.URL("http://localhost:3000/api/import-data")
        val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 5000
            readTimeout = 15000
        }
        conn.outputStream.use { it.write(jsonBytes) }
        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.readText()
            throw RuntimeException("Web app import failed (HTTP $code): ${err ?: ""}")
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
