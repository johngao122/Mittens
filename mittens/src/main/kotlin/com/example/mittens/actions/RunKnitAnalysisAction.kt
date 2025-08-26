package com.example.mittens.actions

import com.example.mittens.services.KnitAnalysisService
import com.example.mittens.services.KnitGradleService
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
        
        val gradleService = project.service<KnitGradleService>()
        val analysisService = project.service<KnitAnalysisService>()
        
        // Only show action if this is a Knit project and analysis is not running
        e.presentation.isEnabledAndVisible = gradleService.isKnitProject() && !analysisService.isAnalysisRunning()
        
        if (analysisService.isAnalysisRunning()) {
            e.presentation.text = "Knit Analysis Running..."
        } else {
            e.presentation.text = "Run Knit Analysis"
        }
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        
        val analysisService = project.service<KnitAnalysisService>()
        val gradleService = project.service<KnitGradleService>()
        
        if (analysisService.isAnalysisRunning()) {
            showNotification(project, "Knit analysis is already running", NotificationType.WARNING)
            return
        }
        
        if (!gradleService.isKnitProject()) {
            showNotification(project, "This project does not use the Knit framework", NotificationType.ERROR)
            return
        }
        
        // Run analysis in background task with progress indicator
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Running Knit Analysis", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "Initializing Knit analysis..."
                    indicator.fraction = 0.1
                    
                    indicator.text = "Detecting Knit configuration..."
                    val knitVersion = gradleService.getKnitVersion()
                    logger.info("Starting analysis for Knit version: $knitVersion")
                    indicator.fraction = 0.2
                    
                    indicator.text = "Analyzing dependency graph..."
                    indicator.fraction = 0.5
                    
                    val result = runBlocking {
                        analysisService.runAnalysis()
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