package com.example.mittens.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

@Service
class KnitGradleService(private val project: Project) {
    
    private val logger = thisLogger()
    
    fun isKnitProject(): Boolean {
        return findKnitGradleConfiguration() != null
    }
    
    fun findKnitGradleConfiguration(): VirtualFile? {
        val buildFiles = listOf("build.gradle.kts", "build.gradle")
        
        for (buildFileName in buildFiles) {
            val buildFile = project.projectFile?.parent?.findChild(buildFileName)
            if (buildFile != null && buildFile.exists()) {
                val content = String(buildFile.contentsToByteArray())
                if (content.contains("io.github.tiktok.knit") || content.contains("knit-plugin")) {
                    logger.info("Found Knit configuration in: $buildFileName")
                    return buildFile
                }
            }
        }
        
        return null
    }
    
    fun getKnitVersion(): String? {
        val buildFile = findKnitGradleConfiguration() ?: return null
        val content = String(buildFile.contentsToByteArray())
        
        val versionRegex = Regex("""io\.github\.tiktok\.knit:knit[^:]*:([^"']+)""")
        val match = versionRegex.find(content)
        
        return match?.groupValues?.get(1)?.also { version ->
            logger.info("Detected Knit version: $version")
        }
    }
    
    fun getBuildDir(): File? {
        val projectDir = File(project.basePath ?: return null)
        
        // Check for custom build directory in gradle.properties or build script
        val buildDir = File(projectDir, "build")
        return if (buildDir.exists() && buildDir.isDirectory) {
            buildDir
        } else {
            logger.warn("Build directory not found at: ${buildDir.absolutePath}")
            null
        }
    }
    
    fun getClassesDir(): File? {
        val buildDir = getBuildDir() ?: return null
        val classesDir = File(buildDir, "classes/kotlin/main")
        
        return if (classesDir.exists() && classesDir.isDirectory) {
            classesDir
        } else {
            logger.warn("Classes directory not found at: ${classesDir.absolutePath}")
            null
        }
    }
    
    fun hasKnitTransformApplied(): Boolean {
        val classesDir = getClassesDir() ?: return false
        
        // Look for evidence of Knit transformation in compiled classes
        // This is a placeholder - we'll implement proper detection in Phase 2
        return classesDir.walk()
            .filter { it.extension == "class" }
            .take(10) // Just check first 10 classes for now
            .any { classFile ->
                try {
                    val bytes = classFile.readBytes()
                    // Very basic check - look for Knit-related strings in bytecode
                    String(bytes, Charsets.ISO_8859_1).contains("knit")
                } catch (e: Exception) {
                    logger.debug("Error reading class file: ${classFile.path}", e)
                    false
                }
            }
    }
}