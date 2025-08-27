package com.example.mittens.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.GradleProject
import java.io.ByteArrayOutputStream
import java.io.File

@Service
class GradleTaskRunner(private val project: Project) {

    private val logger = thisLogger()

    data class GradleExecutionResult(
        val success: Boolean,
        val output: String,
        val errorOutput: String,
        val executionTimeMs: Long
    )

    suspend fun executeKnitCompilation(progressIndicator: ProgressIndicator?): GradleExecutionResult {
        return try {
            progressIndicator?.text = "Preparing Gradle execution..."

            val projectDir = File(project.basePath ?: throw IllegalStateException("Project path is null"))
            if (!projectDir.exists()) {
                throw IllegalStateException("Project directory does not exist: ${projectDir.absolutePath}")
            }

            val startTime = System.currentTimeMillis()

            val connector = GradleConnector.newConnector()
            connector.forProjectDirectory(projectDir)


            val currentThread = Thread.currentThread()
            val originalClassLoader = currentThread.contextClassLoader
            currentThread.contextClassLoader = this::class.java.classLoader

            val connection: ProjectConnection = connector.connect()

            try {
                progressIndicator?.text = "Executing Knit compilation tasks..."
                progressIndicator?.fraction = 0.3


                val result = tryExecuteKnitTasks(connection, progressIndicator)
                    ?: executeStandardCompilation(connection, progressIndicator)

                val executionTime = System.currentTimeMillis() - startTime
                logger.info("Gradle execution completed in ${executionTime}ms")

                result.copy(executionTimeMs = executionTime)

            } finally {
                connection.close()

                currentThread.contextClassLoader = originalClassLoader
            }

        } catch (e: Exception) {
            logger.error("Gradle execution failed", e)
            GradleExecutionResult(
                success = false,
                output = "",
                errorOutput = "Gradle execution failed: ${e.message}",
                executionTimeMs = 0
            )
        }
    }

    private fun tryExecuteKnitTasks(
        connection: ProjectConnection,
        progressIndicator: ProgressIndicator?
    ): GradleExecutionResult? {
        return try {

            val knitTasks = listOf("shadowJarWithKnit", "compileKotlinWithKnit", "compileKotlin")

            for (task in knitTasks) {
                progressIndicator?.text = "Trying to execute $task..."

                if (taskExists(connection, task)) {
                    logger.info("Executing Knit task: $task")
                    return executeTask(connection, task, progressIndicator)
                }
            }

            null

        } catch (e: Exception) {
            logger.warn("Failed to execute Knit-specific tasks", e)
            null
        }
    }

    private fun executeStandardCompilation(
        connection: ProjectConnection,
        progressIndicator: ProgressIndicator?
    ): GradleExecutionResult {
        progressIndicator?.text = "Executing standard Kotlin compilation..."
        logger.info("Falling back to standard compilation tasks")

        return executeTask(connection, "compileKotlin", progressIndicator)
    }

    private fun executeTask(
        connection: ProjectConnection,
        taskName: String,
        progressIndicator: ProgressIndicator?
    ): GradleExecutionResult {
        val outputStream = ByteArrayOutputStream()
        val errorStream = ByteArrayOutputStream()

        return try {
            progressIndicator?.text = "Running $taskName..."
            progressIndicator?.fraction = 0.6

            val buildLauncher = connection.newBuild()
            buildLauncher.forTasks(taskName)
            buildLauncher.setStandardOutput(outputStream)
            buildLauncher.setStandardError(errorStream)


            progressIndicator?.let { indicator ->
                buildLauncher.addProgressListener(org.gradle.tooling.ProgressListener { event ->
                    indicator.text = "Gradle: ${event.description}"
                })
            }

            buildLauncher.run()

            progressIndicator?.fraction = 0.9
            val output = outputStream.toString()
            val errorOutput = errorStream.toString()

            logger.info("Task $taskName completed successfully")
            logger.debug("Task output: $output")

            GradleExecutionResult(
                success = true,
                output = output,
                errorOutput = errorOutput,
                executionTimeMs = 0
            )

        } catch (e: Exception) {
            val output = outputStream.toString()
            val errorOutput = errorStream.toString()

            logger.error("Task $taskName failed", e)
            logger.debug("Task output: $output")
            logger.debug("Task error output: $errorOutput")

            GradleExecutionResult(
                success = false,
                output = output,
                errorOutput = errorOutput + "\nException: ${e.message}",
                executionTimeMs = 0
            )
        }
    }

    private fun taskExists(connection: ProjectConnection, taskName: String): Boolean {
        return try {
            val project = connection.getModel(GradleProject::class.java)
            val allTasks = getAllTasks(project)
            val exists = allTasks.any { it.name == taskName }

            logger.debug("Task '$taskName' ${if (exists) "exists" else "does not exist"}")
            exists

        } catch (e: Exception) {
            logger.warn("Failed to check if task $taskName exists", e)
            false
        }
    }

    private fun getAllTasks(project: GradleProject): List<org.gradle.tooling.model.GradleTask> {
        val tasks = mutableListOf<org.gradle.tooling.model.GradleTask>()

        fun collectTasks(proj: GradleProject) {
            tasks.addAll(proj.tasks)
            proj.children.forEach { collectTasks(it) }
        }

        collectTasks(project)
        return tasks
    }

    fun hasKnitGradlePlugin(): Boolean {
        return try {
            val projectDir = File(project.basePath ?: return false)
            val buildFile = findBuildFile(projectDir) ?: return false
            val content = buildFile.readText()

            content.contains("io.github.tiktok.knit.plugin") ||
                    content.contains("knit-plugin")

        } catch (e: Exception) {
            logger.warn("Failed to check for Knit Gradle plugin", e)
            false
        }
    }

    private fun findBuildFile(projectDir: File): File? {
        val buildFiles = listOf("build.gradle.kts", "build.gradle")
        return buildFiles.mapNotNull { fileName ->
            val file = File(projectDir, fileName)
            if (file.exists()) file else null
        }.firstOrNull()
    }

    fun getExpectedClassesDirectory(): File? {
        val projectDir = File(project.basePath ?: return null)
        val buildDir = File(projectDir, "build")


        val possiblePaths = listOf(
            "classes/kotlin/main",
            "classes/java/main",
            "classes/main"
        )

        for (path in possiblePaths) {
            val classesDir = File(buildDir, path)
            if (classesDir.exists() && classesDir.isDirectory) {
                logger.debug("Found classes directory: ${classesDir.absolutePath}")
                return classesDir
            }
        }

        logger.warn("No classes directory found in build directory")
        return null
    }
}