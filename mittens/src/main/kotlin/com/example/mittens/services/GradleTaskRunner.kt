package com.example.mittens.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
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

    fun runShadowJarWithKnit(): GradleExecutionResult {
        return runGradleTask(listOf("shadowJarWithKnit"))
    }

    fun runGradleTask(args: List<String>): GradleExecutionResult {
        val start = System.currentTimeMillis()
        return try {
            val projectDir = File(project.basePath ?: ".")
            val gradlew = if (isWindows()) File(projectDir, "gradlew.bat") else File(projectDir, "gradlew")
            val command = if (isWindows()) listOf(gradlew.absolutePath) + args else listOf(gradlew.absolutePath) + args

            if (!gradlew.exists()) {
                return GradleExecutionResult(false, "", "Gradle wrapper not found at ${gradlew.absolutePath}", 0)
            }

            if (!isWindows()) {
                try { gradlew.setExecutable(true) } catch (_: Throwable) {}
            }

            val pb = ProcessBuilder(command)
                .directory(projectDir)
                .redirectErrorStream(false)

            val process = pb.start()
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exit = process.waitFor()
            GradleExecutionResult(exit == 0, stdout, stderr, System.currentTimeMillis() - start)
        } catch (e: Exception) {
            logger.warn("Gradle task failed", e)
            GradleExecutionResult(false, "", e.message ?: e.toString(), System.currentTimeMillis() - start)
        }
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")

}
