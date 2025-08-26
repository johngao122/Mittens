package com.example.mittens.actions

import com.example.mittens.model.DetailedAnalysisReport
import com.example.mittens.services.KnitAnalysisService
import com.example.mittens.services.KnitProjectDetector
import com.example.mittens.ui.KnitAnalysisReportDialog
import com.intellij.notification.NotificationAction
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
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.runBlocking

class RunKnitAnalysisAction :
    AnAction("Run Knit Analysis", "Analyze Knit dependency injection in the current project", null) {

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


                    val summary = result.getSummary()
                    val report = DetailedAnalysisReport(summary)
                    val message = report.generateNotificationMessage()

                    val notificationType = when {
                        summary.errorCount > 0 -> NotificationType.ERROR
                        summary.warningCount > 0 -> NotificationType.WARNING
                        else -> NotificationType.INFORMATION
                    }

                    showEnhancedNotification(project, message, report, notificationType)

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

    private fun showEnhancedNotification(
        project: Project,
        message: String,
        report: DetailedAnalysisReport,
        type: NotificationType
    ) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Knit Analysis")
            .createNotification(message, type)


        notification.addAction(NotificationAction.createSimple("View Full Report") {
            val dialog = KnitAnalysisReportDialog(project, report, report.summary)
            dialog.show()
        })

        if (type == NotificationType.ERROR || type == NotificationType.WARNING) {
            notification.addAction(NotificationAction.createSimple("Analysis Tips") {
                Messages.showInfoMessage(
                    project,
                    generateAnalysisTips(),
                    "Knit Analysis - Tips & Best Practices"
                )
            })
        }






        notification.notify(project)
    }

    private fun generateAnalysisTips(): String {
        return """
            |Knit Dependency Injection Best Practices:
            |
            |🔄 Circular Dependencies:
            |  • Use interfaces to break tight coupling
            |  • Consider using a mediator pattern
            |  • Review your component architecture
            |
            |❓ Unresolved Dependencies:
            |  • Ensure all required providers are annotated with @Provides
            |  • Check that provider return types match dependency types
            |  • Verify named qualifiers match exactly
            |
            |🔁 Singleton Violations:
            |  • Use @Singleton annotation consistently
            |  • Avoid multiple singleton providers for the same type
            |  • Consider component-level vs global singletons
            |
            |🏷️ Named Qualifier Issues:
            |  • Double-check qualifier names for typos
            |  • Ensure @Named annotations match between providers and consumers
            |  • Consider using type-safe qualifiers with classes
            |
            |For more help, check the Knit documentation or plugin settings.
        """.trimMargin()
    }
}