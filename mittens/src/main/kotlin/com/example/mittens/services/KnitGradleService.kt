package com.example.mittens.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import java.io.File
import kotlin.ExperimentalStdlibApi

@Service
@OptIn(ExperimentalStdlibApi::class)
class KnitGradleService(private val project: Project) {

    private val logger = thisLogger()

    fun isKnitProject(): Boolean {
        return findKnitGradleConfiguration() != null
    }

    fun findKnitGradleConfiguration(): VirtualFile? {
        val scope = GlobalSearchScope.projectScope(project)
        val candidates = buildList {
            addAll(FilenameIndex.getVirtualFilesByName(project, "build.gradle.kts", scope))
            addAll(FilenameIndex.getVirtualFilesByName(project, "build.gradle", scope))
        }
        for (vf in candidates) {
            val content = String(vf.contentsToByteArray())
            if (
                content.contains("id(\"io.github.tiktok.knit.plugin\")") ||
                content.contains("io.github.tiktok.knit.plugin") ||
                content.contains("knit-plugin") ||
                content.contains("io.github.tiktok:knit") ||
                content.contains("io.github.tiktok.knit:knit")
            ) {
                logger.info("Found Knit configuration in: ${vf.path}")
                return vf
            }
        }

        return null
    }

    fun getKnitVersion(): String? {
        val buildFile = findKnitGradleConfiguration() ?: return null
        val content = String(buildFile.contentsToByteArray())

        // Support multiple Gradle notations and both group ids
        // Examples matched:
        // implementation("io.github.tiktok.knit:knit:0.1.5")
        // implementation("io.github.tiktok:knit:0.1.5")
        // implementation 'io.github.tiktok.knit:knit:0.1.5'
        // implementation 'io.github.tiktok:knit:0.1.5'
        // id("io.github.tiktok.knit.plugin") version "0.1.5"
        // id 'io.github.tiktok.knit.plugin' version '0.1.5'
        val versionRegexes = listOf(
            // Dependency coordinates
            Regex("""io\.github\.tiktok\.knit:knit:([^\s"'()]+)"""),
            Regex("""io\.github\.tiktok:knit:([^\s"'()]+)"""),
            // Plugin block with version keyword
            Regex("""id\("io\.github\.tiktok\.knit\.plugin"\)\s+version\s+"([^"]+)"""),
            Regex("""id\s*'io\.github\.tiktok\.knit\.plugin'\s+version\s*'([^']+)'""")
        )
        val version = versionRegexes.asSequence()
            .mapNotNull { it.find(content)?.groupValues?.get(1) }
            .firstOrNull()

        return version?.also { v ->
            logger.info("Detected Knit version: $v")
        }
    }

    fun getBuildDir(): File? {
        val projectDir = File(project.basePath ?: return null)


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



        return classesDir.walk()
            .filter { it.extension == "class" }
            .take(10)
            .any { classFile ->
                try {
                    val bytes = classFile.readBytes()

                    String(bytes, Charsets.ISO_8859_1).contains("knit")
                } catch (e: Exception) {
                    logger.debug("Error reading class file: ${classFile.path}", e)
                    false
                }
            }
    }

    /**
     * Get the knit.json file location in the build directory
     * @return File object pointing to knit.json, or null if build directory doesn't exist
     */
    fun getKnitJsonFile(): File? {
        val buildDir = getBuildDir() ?: return null
        return File(buildDir, "knit.json")
    }

    /**
     * Check if the knit.json file exists and is readable
     * @return true if knit.json exists and can be read
     */
    fun hasKnitJsonFile(): Boolean {
        val knitJsonFile = getKnitJsonFile() ?: return false
        return knitJsonFile.exists() && knitJsonFile.canRead() && knitJsonFile.length() > 0
    }

    /**
     * Get the absolute path to the knit.json file
     * @return Absolute path string, or null if build directory doesn't exist
     */
    fun getKnitJsonPath(): String? {
        return getKnitJsonFile()?.absolutePath
    }

    /**
     * Get the last modified timestamp of the knit.json file
     * @return Last modified timestamp in milliseconds, or null if file doesn't exist
     */
    fun getKnitJsonLastModified(): Long? {
        val knitJsonFile = getKnitJsonFile()
        return if (knitJsonFile?.exists() == true) {
            knitJsonFile.lastModified()
        } else null
    }

    /**
     * Check if the knit.json file is newer than the last analysis
     * @param lastAnalysisTime Timestamp of the last analysis in milliseconds
     * @return true if knit.json has been modified since the last analysis
     */
    fun isKnitJsonNewer(lastAnalysisTime: Long): Boolean {
        val knitJsonModified = getKnitJsonLastModified() ?: return false
        return knitJsonModified > lastAnalysisTime
    }

    /**
     * Get information about the knit.json file for debugging/logging
     * @return Human-readable string with file information
     */
    fun getKnitJsonInfo(): String {
        val knitJsonFile = getKnitJsonFile()
        return if (knitJsonFile == null) {
            "knit.json: Build directory not found"
        } else if (!knitJsonFile.exists()) {
            "knit.json: File not found at ${knitJsonFile.absolutePath}"
        } else if (!knitJsonFile.canRead()) {
            "knit.json: File not readable at ${knitJsonFile.absolutePath}"
        } else {
            val sizeKB = knitJsonFile.length() / 1024
            val lastModified = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(java.util.Date(knitJsonFile.lastModified()))
            "knit.json: ${sizeKB}KB, modified $lastModified"
        }
    }

    /**
     * Check if the project has the knit plugin configured to generate knit.json
     * This checks for the dependencyTreeOutputPath configuration
     * @return true if the project is configured to generate knit.json
     */
    fun hasKnitJsonConfiguration(): Boolean {
        val buildFile = findKnitGradleConfiguration() ?: return false
        val content = String(buildFile.contentsToByteArray())
        
        return content.contains("dependencyTreeOutputPath") ||
               content.contains("build/knit.json") ||
               content.contains("KnitExtension")
    }

    /**
     * Get the configured output path for knit.json from the build file
     * @return The configured output path, or "build/knit.json" as default
     */
    fun getConfiguredKnitJsonOutputPath(): String {
        val buildFile = findKnitGradleConfiguration()
        if (buildFile != null) {
            val content = String(buildFile.contentsToByteArray())
            
            // Look for dependencyTreeOutputPath.set("path") pattern
            val pathRegex = Regex("""dependencyTreeOutputPath\.set\s*\(\s*"([^"]+)"\s*\)""")
            val match = pathRegex.find(content)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        
        // Default path used by Knit plugin
        return "build/knit.json"
    }
}