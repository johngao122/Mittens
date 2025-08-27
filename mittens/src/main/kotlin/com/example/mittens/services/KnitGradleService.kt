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

        val versionRegexes = listOf(
            Regex("""io\\.github\\.tiktok\\.knit:knit[^:]*:([^"']+)"""),
            Regex("""io\\.github\\.tiktok:knit[^:]*:([^"']+)""")
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
}