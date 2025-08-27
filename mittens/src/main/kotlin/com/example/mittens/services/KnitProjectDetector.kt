package com.example.mittens.services

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import kotlin.ExperimentalStdlibApi

@Service
@OptIn(ExperimentalStdlibApi::class)
class KnitProjectDetector(private val project: Project) {

    private val logger = thisLogger()

    data class KnitDetectionResult(
        val isKnitProject: Boolean,
        val hasKnitPlugin: Boolean,
        val hasKnitDependency: Boolean,
        val knitVersion: String? = null,
        val componentsWithByDi: List<ComponentInfo> = emptyList(),
        val componentsWithProvides: List<ComponentInfo> = emptyList(),
        val componentsWithComponent: List<ComponentInfo> = emptyList()
    )

    data class ComponentInfo(
        val className: String,
        val packageName: String,
        val filePath: String,
        val detectionReason: String
    )

    fun detectKnitProject(): KnitDetectionResult {
        logger.info("Starting comprehensive Knit project detection")

        val buildFileAnalysis = analyzeBuildFiles()
        val sourceAnalysis = analyzeSourceFiles()

        val isKnitProject = buildFileAnalysis.hasKnitPlugin ||
                buildFileAnalysis.hasKnitDependency ||
                sourceAnalysis.componentsWithByDi.isNotEmpty() ||
                sourceAnalysis.componentsWithProvides.isNotEmpty() ||
                sourceAnalysis.componentsWithComponent.isNotEmpty()

        return KnitDetectionResult(
            isKnitProject = isKnitProject,
            hasKnitPlugin = buildFileAnalysis.hasKnitPlugin,
            hasKnitDependency = buildFileAnalysis.hasKnitDependency,
            knitVersion = buildFileAnalysis.knitVersion,
            componentsWithByDi = sourceAnalysis.componentsWithByDi,
            componentsWithProvides = sourceAnalysis.componentsWithProvides,
            componentsWithComponent = sourceAnalysis.componentsWithComponent
        )
    }

    private fun analyzeBuildFiles(): BuildAnalysisResult {
        return runReadAction {
            val scope = GlobalSearchScope.projectScope(project)
            val buildFiles = buildList {
                addAll(FilenameIndex.getVirtualFilesByName(project, "build.gradle.kts", scope))
                addAll(FilenameIndex.getVirtualFilesByName(project, "build.gradle", scope))
            }
            var hasKnitPlugin = false
            var hasKnitDependency = false
            var knitVersion: String? = null

            for (vf in buildFiles) {
                val content = String(vf.contentsToByteArray())

                if (content.contains("id(\"io.github.tiktok.knit.plugin\")") ||
                    content.contains("io.github.tiktok.knit.plugin") ||
                    content.contains("knit-plugin")
                ) {
                    hasKnitPlugin = true
                    logger.info("Found Knit plugin in: ${vf.path}")
                }

                if (content.contains("io.github.tiktok:knit") || content.contains("io.github.tiktok.knit:knit")) {
                    hasKnitDependency = true
                    logger.info("Found Knit dependency in: ${vf.path}")
                }

                if (knitVersion == null) {
                    val version = listOf(
                        Regex("""io\\.github\\.tiktok:knit[^:]*:([^"']+)"""),
                        Regex("""io\\.github\\.tiktok\\.knit:knit[^:]*:([^"']+)""")
                    ).asSequence()
                        .mapNotNull { it.find(content)?.groupValues?.get(1) }
                        .firstOrNull()
                    if (version != null) knitVersion = version
                }
            }

            BuildAnalysisResult(hasKnitPlugin, hasKnitDependency, knitVersion)
        }
    }

    private fun analyzeSourceFiles(): SourceAnalysisResult {
        return runReadAction {
            val componentsWithByDi = mutableListOf<ComponentInfo>()
            val componentsWithProvides = mutableListOf<ComponentInfo>()
            val componentsWithComponent = mutableListOf<ComponentInfo>()


            val kotlinFiles = FileTypeIndex.getFiles(
                KotlinFileType.INSTANCE,
                GlobalSearchScope.projectScope(project)
            )

            val psiManager = PsiManager.getInstance(project)

            for (file in kotlinFiles) {
                val psiFile = psiManager.findFile(file) as? KtFile ?: continue

                val classes = psiFile.collectDescendantsOfType<KtClass>()
                for (ktClass in classes) {
                    val className = ktClass.name ?: continue
                    val packageName = psiFile.packageFqName.asString()
                    val filePath = file.path


                    val properties = ktClass.collectDescendantsOfType<KtProperty>()
                    val hasByDi = properties.any { property ->
                        property.delegate?.text?.contains("di") == true
                    }

                    if (hasByDi) {
                        componentsWithByDi.add(
                            ComponentInfo(
                                className, packageName, filePath, "Has 'by di' properties"
                            )
                        )
                        logger.debug("Found class with 'by di': $packageName.$className")
                    }


                    val annotations = ktClass.annotationEntries
                    val hasProvides = annotations.any { it.shortName?.asString() == "Provides" }
                    val hasComponent = annotations.any { it.shortName?.asString() == "Component" }

                    if (hasProvides) {
                        componentsWithProvides.add(
                            ComponentInfo(
                                className, packageName, filePath, "Has @Provides annotation"
                            )
                        )
                        logger.debug("Found class with @Provides: $packageName.$className")
                    }

                    if (hasComponent) {
                        componentsWithComponent.add(
                            ComponentInfo(
                                className, packageName, filePath, "Has @Component annotation"
                            )
                        )
                        logger.debug("Found class with @Component: $packageName.$className")
                    }


                    val methods = ktClass.collectDescendantsOfType<KtNamedFunction>()
                    val hasProvidesMethods = methods.any { method ->
                        method.annotationEntries.any { it.shortName?.asString() == "Provides" }
                    }

                    if (hasProvidesMethods && !hasProvides) {
                        componentsWithProvides.add(
                            ComponentInfo(
                                className, packageName, filePath, "Has methods with @Provides annotation"
                            )
                        )
                        logger.debug("Found class with @Provides methods: $packageName.$className")
                    }
                }
            }

            logger.info(
                "Source analysis complete: " +
                        "${componentsWithByDi.size} components with 'by di', " +
                        "${componentsWithProvides.size} components with @Provides, " +
                        "${componentsWithComponent.size} components with @Component"
            )

            SourceAnalysisResult(componentsWithByDi, componentsWithProvides, componentsWithComponent)
        }
    }

    private data class BuildAnalysisResult(
        val hasKnitPlugin: Boolean,
        val hasKnitDependency: Boolean,
        val knitVersion: String?
    )

    private data class SourceAnalysisResult(
        val componentsWithByDi: List<ComponentInfo>,
        val componentsWithProvides: List<ComponentInfo>,
        val componentsWithComponent: List<ComponentInfo>
    )
}