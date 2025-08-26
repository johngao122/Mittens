package com.example.mittens.actions

import com.example.mittens.services.KnitAnalysisService
import com.example.mittens.services.KnitProjectDetector
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import kotlinx.coroutines.runBlocking

class RunKnitAnalysisAction : AnAction("Run Knit Analysis", "Analyze Knit dependency injection in the current project", null) {
    
    private val logger = thisLogger()
    
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    
    override fun update(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT)
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        
        val projectDetector = project.service<KnitProjectDetector>()
        val analysisService = project.service<KnitAnalysisService>()
        
        // Only show action if this is a Knit project and analysis is not running
        val isKnitProject = try { 
            projectDetector.detectKnitProject().isKnitProject 
        } catch (e: Exception) { 
            false 
        }
        e.presentation.isEnabledAndVisible = isKnitProject && !analysisService.isAnalysisRunning()
        
        if (analysisService.isAnalysisRunning()) {
            e.presentation.text = "Knit Analysis Running..."
        } else {
            e.presentation.text = "Run Knit Analysis"
        }
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        
        val analysisService = project.service<KnitAnalysisService>()
        val projectDetector = project.service<KnitProjectDetector>()
        
        if (analysisService.isAnalysisRunning()) {
            showNotification(project, "Knit analysis is already running", NotificationType.WARNING)
            return
        }
        
        val detectionResult = try {
            projectDetector.detectKnitProject()
        } catch (e: Exception) {
            showNotification(project, "Failed to detect Knit project: ${e.message}", NotificationType.ERROR)
            return
        }
        
        if (!detectionResult.isKnitProject) {
            showNotification(project, "This project does not use the Knit framework", NotificationType.ERROR)
            return
        }
        
        // Run analysis in background task with progress indicator
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Running Knit Analysis", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "Starting Knit analysis..."
                    indicator.fraction = 0.1
                    logger.info("Starting analysis for Knit version: ${detectionResult.knitVersion ?: "Unknown"}")
                    
                    val result = runBlocking {
                        analysisService.runAnalysis(indicator)
                    }
                    
                    indicator.text = "Analysis complete"
                    indicator.fraction = 1.0
                    
                    // Show results notification
                    val summary = result.getSummary()
                    val message = buildString {
                        append("Analysis completed successfully!\n")
                        append("Components: ${summary.totalComponents}\n")
                        append("Dependencies: ${summary.totalDependencies}\n")
                        append("Issues found: ${summary.totalIssues}")
                        if (summary.errorCount > 0) {
                            append(" (${summary.errorCount} errors)")
                        }
                    }
                    
                    val notificationType = when {
                        summary.errorCount > 0 -> NotificationType.ERROR
                        summary.warningCount > 0 -> NotificationType.WARNING
                        else -> NotificationType.INFORMATION
                    }
                    
                    showNotification(project, message, notificationType)
                    
                } catch (e: Exception) {
                    logger.error("Knit analysis failed", e)
                    showNotification(
                        project, 
                        "Knit analysis failed: ${e.message}", 
                        NotificationType.ERROR
                    )
                }
            }
        })
    }
    
    private fun showNotification(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Knit Analysis")
            .createNotification(message, type)
            .notify(project)
    }
}