package com.example.mittens.services

import com.example.mittens.model.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*

@Service
class KnitAnalysisService(private val project: Project) {

    private val logger = thisLogger()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Priority order for issue detection
     */
    private enum class IssuePriority(val issueType: IssueType) {
        CRITICAL_CIRCULAR(IssueType.CIRCULAR_DEPENDENCY),
        MEDIUM_AMBIGUOUS(IssueType.AMBIGUOUS_PROVIDER);

        companion object {
            fun getPriority(issueType: IssueType): IssuePriority? {
                return values().find { it.issueType == issueType }
            }
        }
    }




    private var lastAnalysisMetrics: AnalysisMetrics? = null

    private var lastAnalysisResult: AnalysisResult? = null
    private var isAnalysisRunning = false

    suspend fun runAnalysis(progressIndicator: ProgressIndicator? = null): AnalysisResult {
        if (isAnalysisRunning) {
            throw IllegalStateException("Analysis is already running")
        }

        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                isAnalysisRunning = true
                logger.info("Starting comprehensive Knit analysis for project: ${project.name}")

                progressIndicator?.text = "Detecting Knit project configuration..."
                progressIndicator?.fraction = 0.1


                val projectDetector = project.service<KnitProjectDetector>()
                val detectionResult = projectDetector.detectKnitProject()

                if (!detectionResult.isKnitProject) {
                    throw IllegalStateException("This project does not use the Knit framework")
                }

                logger.info("Knit project detected - Version: ${detectionResult.knitVersion ?: "Unknown"}")

                // Get components with analysis metadata
                progressIndicator?.text = "Analyzing project components..."
                progressIndicator?.fraction = 0.2
                
                val sourceAnalyzer = project.service<KnitSourceAnalyzer>()
                val analysisWithMetadata = sourceAnalyzer.analyzeProjectWithMetadata()
                val mergedComponents = analysisWithMetadata.components
                val dependencyGraph = buildDependencyGraph(mergedComponents)
                val detectedIssues = detectIssues(mergedComponents, dependencyGraph)

                progressIndicator?.text = "Validating issues for accuracy..."
                progressIndicator?.fraction = 0.95

                
                val validationStartTime = System.currentTimeMillis()
                val settingsService = project.service<KnitSettingsService>()
                val validationSettings = IssueValidator.ValidationSettings(
                    validationEnabled = settingsService.isValidationEnabled(),
                    minimumConfidenceThreshold = settingsService.getConfidenceThreshold()
                )

                val issueValidator = project.service<IssueValidator>()
                val validatedIssues = issueValidator.validateIssues(detectedIssues, mergedComponents, validationSettings)
                
                val statisticalService = project.service<StatisticalAccuracyService>()
                val expectedIssues = statisticalService.estimateExpectedIssues(mergedComponents)
                val accuracyMetrics = statisticalService.calculateAccuracyMetrics(
                    allIssues = validatedIssues,
                    validatedIssues = validatedIssues.filter { it.validationStatus != ValidationStatus.NOT_VALIDATED },
                    expectedIssues = expectedIssues
                )

                val validationTime = System.currentTimeMillis() - validationStartTime
                val totalAnalysisTime = System.currentTimeMillis() - startTime

                val result = AnalysisResult(
                    components = mergedComponents,
                    dependencyGraph = dependencyGraph,
                    issues = validatedIssues,
                    timestamp = System.currentTimeMillis(),
                    projectName = project.name,
                    knitVersion = detectionResult.knitVersion,
                    metadata = AnalysisMetadata(
                        analysisTimeMs = totalAnalysisTime,
                        bytecodeFilesScanned = 0, // Not used in current analysis approach
                        sourceFilesScanned = mergedComponents.size,
                        validationTimeMs = validationTime,
                        deduplicationTimeMs = 0,
                        analysisMethod = analysisWithMetadata.analysisMethod,
                        knitJsonPath = analysisWithMetadata.knitJsonPath
                    ),
                    accuracyMetrics = accuracyMetrics
                )

                lastAnalysisResult = result
                logger.info(
                    "Knit analysis completed successfully in ${totalAnalysisTime}ms - " +
                            "Found ${result.components.size} components, ${result.dependencyGraph.edges.size} dependencies, " +
                            "${result.issues.size} issues (${result.accuracyMetrics.truePositives} validated, " +
                            "${result.accuracyMetrics.falsePositives} false positives, " +
                            "${String.format("%.1f", result.getSummary().getAccuracyPercentage())}% accuracy)"
                )
                
                if (settingsService.isDetailedLoggingEnabled()) {
                    val accuracyReport = statisticalService.generateAccuracyReport(accuracyMetrics, validatedIssues.size)
                    logger.info("Accuracy Report:\n$accuracyReport")
                }

                progressIndicator?.text = "Analysis complete"
                progressIndicator?.fraction = 1.0

                result

            } catch (e: Exception) {
                logger.error("Knit analysis failed", e)
                throw e
            } finally {
                isAnalysisRunning = false
            }
        }
    }

    fun getLastAnalysisResult(): AnalysisResult? = lastAnalysisResult

    fun isAnalysisRunning(): Boolean = isAnalysisRunning

    fun clearCache() {
        lastAnalysisResult = null
        logger.info("Analysis cache cleared")
    }

    /**
     * Analyze project using knit.json - much faster than ASM analysis
     */
    private suspend fun analyzeWithKnitJson(progressIndicator: ProgressIndicator?): List<KnitComponent> {
        progressIndicator?.text = "Loading dependency tree from knit.json..."
        progressIndicator?.fraction = 0.3
        
        val sourceAnalyzer = project.service<KnitSourceAnalyzer>()
        val components = sourceAnalyzer.analyzeFromKnitJson()
        
        if (components.isEmpty()) {
            logger.warn("No components found in knit.json, falling back to source analysis")
            progressIndicator?.text = "Analyzing source code as fallback..."
            progressIndicator?.fraction = 0.5
            return sourceAnalyzer.analyzeFromSource()
        }
        
        progressIndicator?.text = "Dependency analysis complete"
        progressIndicator?.fraction = 0.9
        
        logger.info("knit.json analysis complete: ${components.size} components loaded")
        return components
    }

    /**
     * Source-only analysis fallback when knit.json is not available
     */
    private suspend fun analyzeWithSourceOnly(progressIndicator: ProgressIndicator?): List<KnitComponent> {
        progressIndicator?.text = "Analyzing source code..."
        progressIndicator?.fraction = 0.3

        val sourceAnalyzer = project.service<KnitSourceAnalyzer>()
        val sourceComponents = sourceAnalyzer.analyzeFromSource()

        logger.info("Found ${sourceComponents.size} components in source analysis")
        
        progressIndicator?.text = "Source analysis complete"
        progressIndicator?.fraction = 0.9

        return sourceComponents
    }


    internal fun buildDependencyGraph(components: List<KnitComponent>): DependencyGraph {
        val nodes = mutableListOf<GraphNode>()
        val edges = mutableListOf<GraphEdge>()

        
        val filteredComponents = components.filter { component ->
            val hasDependencies = component.dependencies.isNotEmpty()
            val hasProviders = component.providers.isNotEmpty()
            val isRelevant = hasDependencies || hasProviders
            
            if (!isRelevant) {
                logger.debug("Graph-level filtering: excluding component with 0 dependencies and 0 providers: ${component.packageName}.${component.className}")
            }
            
            isRelevant
        }

        val filteredCount = components.size - filteredComponents.size
        if (filteredCount > 0) {
            logger.info("Filtered out $filteredCount components with no dependencies or providers. Remaining: ${filteredComponents.size} components")
        }

        
        filteredComponents.forEach { component ->
            val nodeId = "${component.packageName}.${component.className}"
            val nodeType = when (component.type) {
                ComponentType.COMPONENT -> NodeType.COMPONENT
                ComponentType.PROVIDER -> NodeType.PROVIDER
                ComponentType.CONSUMER -> NodeType.COMPONENT
                ComponentType.COMPOSITE -> NodeType.COMPONENT
            }

            nodes.add(
                GraphNode(
                    id = nodeId,
                    label = component.className,
                    type = nodeType,
                    packageName = component.packageName,
                    issues = component.issues
                )
            )


            component.providers.forEach { provider ->
                val providerNodeId = "$nodeId.${provider.methodName}"
                nodes.add(
                    GraphNode(
                        id = providerNodeId,
                        label = "${provider.methodName}(): ${provider.returnType}",
                        type = NodeType.PROVIDER,
                        packageName = component.packageName
                    )
                )


                edges.add(
                    GraphEdge(
                        from = nodeId,
                        to = providerNodeId,
                        type = EdgeType.PROVIDES,
                        label = "provides"
                    )
                )
            }
        }

        
        filteredComponents.forEach { component ->
            val consumerNodeId = "${component.packageName}.${component.className}"

            component.dependencies.forEach { dependency ->

                val providerComponent = findProviderForType(
                    filteredComponents,
                    dependency.targetType,
                    if (dependency.isNamed) dependency.namedQualifier else null
                )
                if (providerComponent != null) {
                    val providerNodeId = "${providerComponent.packageName}.${providerComponent.className}"

                    edges.add(
                        GraphEdge(
                            from = consumerNodeId,
                            to = providerNodeId,
                            type = when {
                                dependency.isSingleton -> EdgeType.SINGLETON
                                dependency.isFactory -> EdgeType.FACTORY
                                dependency.isNamed -> EdgeType.NAMED
                                dependency.isLoadable -> EdgeType.DEPENDENCY
                                else -> EdgeType.DEPENDENCY
                            },
                            label = if (dependency.isNamed && dependency.namedQualifier != null) {
                                "${dependency.propertyName} (@Named(${dependency.namedQualifier}))"
                            } else {
                                dependency.propertyName
                            }
                        )
                    )
                }
            }
        }

        return DependencyGraph(nodes, edges)
    }

    private fun findProviderForType(
        components: List<KnitComponent>,
        targetType: String,
        namedQualifier: String? = null
    ): KnitComponent? {

        return components.find { component ->

            val classMatches = component.className == targetType.substringAfterLast('.') ||
                    "${component.packageName}.${component.className}" == targetType


            val providerMatches = component.providers.any { provider ->
                val typeMatches = provider.returnType == targetType ||
                        provider.providesType == targetType ||
                        provider.returnType.substringAfterLast('.') == targetType.substringAfterLast('.')


                val qualifierMatches = if (namedQualifier != null) {
                    provider.isNamed && provider.namedQualifier == namedQualifier
                } else {
                    !provider.isNamed
                }

                typeMatches && qualifierMatches
            }

            classMatches || providerMatches
        }
    }

    /**
     * Deduplicates issues by component and type
     */
    private fun deduplicateIssues(issues: List<KnitIssue>): List<KnitIssue> {
        
        val issuesByComponent = issues.groupBy { it.componentName }
        val deduplicatedIssues = mutableListOf<KnitIssue>()

        issuesByComponent.forEach { (componentName, componentIssues) ->
            if (componentIssues.isEmpty()) return@forEach

            
            val issuesByType = componentIssues.groupBy { it.type }
            
            
            issuesByType.forEach { (issueType, typeIssues) ->
                val selectedIssue = typeIssues.minByOrNull { issue ->
                    when (issue.severity) {
                        Severity.ERROR -> 0
                        Severity.WARNING -> 1  
                        Severity.INFO -> 2
                    }
                } ?: typeIssues.first()
                
                deduplicatedIssues.add(selectedIssue)
            }
        }

        
        
        val componentsByPriority = mutableMapOf<String, IssuePriority>()
        val finalIssues = mutableListOf<KnitIssue>()

        
        deduplicatedIssues.forEach { issue ->
            val componentName = issue.componentName
            val currentPriority = IssuePriority.getPriority(issue.type)
            
            if (currentPriority != null) {
                val existingPriority = componentsByPriority[componentName]
                if (existingPriority == null || currentPriority.ordinal < existingPriority.ordinal) {
                    componentsByPriority[componentName] = currentPriority
                }
            }
        }

        
        deduplicatedIssues.forEach { issue ->
            val componentName = issue.componentName
            val issuePriority = IssuePriority.getPriority(issue.type)
            val componentMaxPriority = componentsByPriority[componentName]
            
            if (issuePriority == componentMaxPriority || componentMaxPriority == null) {
                finalIssues.add(issue)
            }
        }

        return finalIssues
    }

    internal fun detectIssues(components: List<KnitComponent>, dependencyGraph: DependencyGraph): List<KnitIssue> {
        val issues = mutableListOf<KnitIssue>()
        val startTime = System.currentTimeMillis()


        components.forEach { component ->
            issues.addAll(component.issues)
        }

        logger.info("Starting advanced issue detection for ${components.size} components")


        val advancedDetector = project.service<AdvancedIssueDetector>()

        try {
            // Detect circular dependencies
            logger.debug("Phase 1: Detecting circular dependencies...")
            val circularIssues = advancedDetector.detectAdvancedCircularDependencies(dependencyGraph)
            issues.addAll(circularIssues)
            logger.debug("Phase 1: Found ${circularIssues.size} circular dependency issues.")

            // Detect ambiguous providers
            logger.debug("Phase 2: Detecting ambiguous providers...")
            val ambiguousIssues = advancedDetector.detectEnhancedAmbiguousProviders(components)
            issues.addAll(ambiguousIssues)
            logger.debug("Phase 2: Found ${ambiguousIssues.size} ambiguous provider issues.")

            logger.info("Issue detection complete. Total issues before deduplication: ${issues.size}")

        } catch (e: Exception) {
            logger.error("Error during issue detection, falling back to basic detection", e)
            issues.addAll(basicIssueDetection(components, dependencyGraph))
        }

        val detectionTime = System.currentTimeMillis() - startTime
        logger.info("Issue detection completed in ${detectionTime}ms. Found ${issues.size} issues before deduplication.")

        
        val deduplicatedIssues = deduplicateIssues(issues)
        logger.info("After deduplication: ${deduplicatedIssues.size} issues (${issues.size - deduplicatedIssues.size} duplicates removed)")

        return deduplicatedIssues.sortedWith(compareBy<KnitIssue> {
            when (it.severity) {
                Severity.ERROR -> 0
                Severity.WARNING -> 1
                Severity.INFO -> 2
            }
        }.thenBy { it.type.name })
    }

    /**
     * Fallback basic issue detection for error scenarios
     */
    private fun basicIssueDetection(
        components: List<KnitComponent>,
        dependencyGraph: DependencyGraph
    ): List<KnitIssue> {
        val issues = mutableListOf<KnitIssue>()

        // Only detect circular dependencies in basic mode
        if (dependencyGraph.hasCycles()) {
            issues.add(
                KnitIssue(
                    type = IssueType.CIRCULAR_DEPENDENCY,
                    severity = Severity.ERROR,
                    message = "Circular dependencies detected in the dependency graph",
                    componentName = "Multiple components",
                    suggestedFix = "Review dependency graph and break circular references"
                )
            )
        }

        return issues
    }





    fun dispose() {
        coroutineScope.cancel()
    }
}

/**
 * Performance metrics for analysis operations
 */
data class AnalysisMetrics(
    val totalAnalysisTime: Long,
    val sourceAnalysisTime: Long,
    val issueDetectionTime: Long,
    val graphConstructionTime: Long,
    val componentsProcessed: Int,
    val dependenciesAnalyzed: Int,
    val issuesFound: Int,
    val cyclesDetected: Int,
    val memoryUsedMB: Long
) {
    fun getPerformanceSummary(): String {
        return """
            |Analysis Performance Summary:
            |  Total Time: ${totalAnalysisTime}ms
            |  Source Analysis: ${sourceAnalysisTime}ms
            |  Issue Detection: ${issueDetectionTime}ms
            |  Graph Construction: ${graphConstructionTime}ms
            |  Components: $componentsProcessed
            |  Dependencies: $dependenciesAnalyzed
            |  Issues Found: $issuesFound
            |  Cycles Detected: $cyclesDetected
            |  Memory Used: ${memoryUsedMB}MB
        """.trimMargin()
    }
}