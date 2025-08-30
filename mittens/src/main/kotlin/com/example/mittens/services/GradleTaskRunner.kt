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

}