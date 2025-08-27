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
     * Priority order for issue detection - higher priority issues exclude components from lower priority detection
     */
    private enum class IssuePriority(val issueType: IssueType) {
        CRITICAL_CIRCULAR(IssueType.CIRCULAR_DEPENDENCY),
        HIGH_UNRESOLVED(IssueType.UNRESOLVED_DEPENDENCY),
        MEDIUM_AMBIGUOUS(IssueType.AMBIGUOUS_PROVIDER),
        LOW_SINGLETON(IssueType.SINGLETON_VIOLATION),
        LOW_QUALIFIER(IssueType.NAMED_QUALIFIER_MISMATCH),
        INFO_ANNOTATION(IssueType.MISSING_COMPONENT_ANNOTATION);

        companion object {
            fun getPriority(issueType: IssueType): IssuePriority? {
                return values().find { it.issueType == issueType }
            }
        }
    }

    /**
     * Data class to track which components should be excluded from detection
     */
    private data class ComponentExclusionSet(
        val excludedComponents: Set<String> = emptySet(),
        val componentPairs: Set<Pair<String, String>> = emptySet()
    ) {
        fun shouldExcludeComponent(componentName: String): Boolean {
            return componentName in excludedComponents
        }

        fun shouldExcludeComponentPair(comp1: String, comp2: String): Boolean {
            return Pair(comp1, comp2) in componentPairs || Pair(comp2, comp1) in componentPairs
        }

        fun addExcludedComponent(componentName: String): ComponentExclusionSet {
            return copy(excludedComponents = excludedComponents + componentName)
        }

        fun addExcludedComponentPair(comp1: String, comp2: String): ComponentExclusionSet {
            return copy(componentPairs = componentPairs + Pair(comp1, comp2))
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

                progressIndicator?.text = "Analyzing source code..."
                progressIndicator?.fraction = 0.3


                val sourceAnalyzer = project.service<KnitSourceAnalyzer>()
                val sourceComponents = sourceAnalyzer.analyzeProject()

                logger.info("Found ${sourceComponents.size} components in source analysis")

                progressIndicator?.text = "Compiling project with Knit transformations..."
                progressIndicator?.fraction = 0.5


                val gradleRunner = project.service<GradleTaskRunner>()
                val compilationResult = gradleRunner.executeKnitCompilation(progressIndicator)

                if (!compilationResult.success) {
                    logger.warn("Gradle compilation had issues: ${compilationResult.errorOutput}")
                }

                progressIndicator?.text = "Analyzing bytecode..."
                progressIndicator?.fraction = 0.7


                val bytecodeAnalyzer = project.service<KnitBytecodeAnalyzer>()
                val classesDir = gradleRunner.getExpectedClassesDirectory()

                val bytecodeResult = if (classesDir != null) {
                    bytecodeAnalyzer.analyzeBytecode(classesDir)
                } else {
                    logger.warn("Classes directory not found, skipping bytecode analysis")
                    KnitBytecodeAnalyzer.BytecodeAnalysisResult(emptyList(), emptyList(), emptyList())
                }

                progressIndicator?.text = "Building dependency graph..."
                progressIndicator?.fraction = 0.9


                val mergedComponents = mergeAnalysisResults(sourceComponents, bytecodeResult.detectedComponents)
                val dependencyGraph = buildDependencyGraph(mergedComponents)
                val detectedIssues = detectIssues(mergedComponents, dependencyGraph)

                progressIndicator?.text = "Validating issues for accuracy..."
                progressIndicator?.fraction = 0.95

                // Issue validation and accuracy metrics calculation
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
                        bytecodeFilesScanned = bytecodeResult.detectedComponents.size,
                        sourceFilesScanned = sourceComponents.size,
                        validationTimeMs = validationTime,
                        deduplicationTimeMs = 0 // This is calculated in detectIssues method
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

    private fun mergeAnalysisResults(
        sourceComponents: List<KnitComponent>,
        bytecodeComponents: List<KnitComponent>
    ): List<KnitComponent> {

        val mergedMap = mutableMapOf<String, KnitComponent>()


        sourceComponents.forEach { component ->
            val key = "${component.packageName}.${component.className}"
            mergedMap[key] = component
        }


        bytecodeComponents.forEach { bytecodeComponent ->
            val key = "${bytecodeComponent.packageName}.${bytecodeComponent.className}"
            val existingComponent = mergedMap[key]

            if (existingComponent != null) {

                mergedMap[key] = existingComponent.copy(
                    dependencies = (existingComponent.dependencies + bytecodeComponent.dependencies).distinctBy { it.propertyName },
                    providers = (existingComponent.providers + bytecodeComponent.providers).distinctBy { it.methodName }
                )
            } else {

                mergedMap[key] = bytecodeComponent
            }
        }

        return mergedMap.values.toList()
    }

    private fun buildDependencyGraph(components: List<KnitComponent>): DependencyGraph {
        val nodes = mutableListOf<GraphNode>()
        val edges = mutableListOf<GraphEdge>()


        components.forEach { component ->
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


        components.forEach { component ->
            val consumerNodeId = "${component.packageName}.${component.className}"

            component.dependencies.forEach { dependency ->

                val providerComponent = findProviderForType(
                    components,
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
     * Deduplicates issues by component and type, keeping highest priority issues
     */
    private fun deduplicateIssues(issues: List<KnitIssue>): List<KnitIssue> {
        // Group issues by component name
        val issuesByComponent = issues.groupBy { it.componentName }
        val deduplicatedIssues = mutableListOf<KnitIssue>()

        issuesByComponent.forEach { (componentName, componentIssues) ->
            if (componentIssues.isEmpty()) return@forEach

            // Group by issue type to find duplicates
            val issuesByType = componentIssues.groupBy { it.type }
            
            // For each issue type, keep only one instance (prefer ERROR severity)
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

        // Now apply priority-based deduplication: components with higher priority issues 
        // should not appear in lower priority issue categories
        val componentsByPriority = mutableMapOf<String, IssuePriority>()
        val finalIssues = mutableListOf<KnitIssue>()

        // First pass: identify highest priority issue per component
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

        // Second pass: only keep issues that match the component's highest priority
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

    private fun detectIssues(components: List<KnitComponent>, dependencyGraph: DependencyGraph): List<KnitIssue> {
        val issues = mutableListOf<KnitIssue>()
        val startTime = System.currentTimeMillis()


        components.forEach { component ->
            issues.addAll(component.issues)
        }

        logger.info("Starting advanced issue detection for ${components.size} components")


        val advancedDetector = project.service<AdvancedIssueDetector>()

        try {
            // Priority-based coordinated detection with component exclusion
            var exclusionSet = ComponentExclusionSet()

            // Phase 1: CRITICAL - Circular Dependencies (highest priority)
            logger.debug("Phase 1: Detecting circular dependencies...")
            val circularIssues = advancedDetector.detectAdvancedCircularDependencies(components, dependencyGraph)
            issues.addAll(circularIssues)
            
            // Track components involved in circular dependencies for exclusion
            circularIssues.forEach { issue ->
                if (issue.type == IssueType.CIRCULAR_DEPENDENCY) {
                    // Extract component names from circular dependency metadata or component names
                    val componentNames = issue.componentName.split(", ")
                    componentNames.forEach { compName ->
                        exclusionSet = exclusionSet.addExcludedComponent(compName.trim())
                    }
                }
            }
            logger.debug("Phase 1: Found ${circularIssues.size} circular dependency issues. Excluded ${exclusionSet.excludedComponents.size} components from further analysis.")

            // Phase 2: HIGH - Unresolved Dependencies
            logger.debug("Phase 2: Detecting unresolved dependencies (excluding ${exclusionSet.excludedComponents.size} circular dependency components)...")
            val unresolvedIssues = advancedDetector.detectImprovedUnresolvedDependencies(components, exclusionSet.excludedComponents)
            issues.addAll(unresolvedIssues)
            
            // Track components with unresolved dependencies
            unresolvedIssues.forEach { issue ->
                if (issue.type == IssueType.UNRESOLVED_DEPENDENCY) {
                    exclusionSet = exclusionSet.addExcludedComponent(issue.componentName)
                }
            }
            logger.debug("Phase 2: Found ${unresolvedIssues.size} unresolved dependency issues. Total excluded components: ${exclusionSet.excludedComponents.size}")

            // Phase 3: MEDIUM - Ambiguous Providers
            logger.debug("Phase 3: Detecting ambiguous providers (excluding ${exclusionSet.excludedComponents.size} components)...")
            val ambiguousIssues = advancedDetector.detectEnhancedAmbiguousProviders(components, exclusionSet.excludedComponents)
            issues.addAll(ambiguousIssues)
            
            // Track components with ambiguous providers
            ambiguousIssues.forEach { issue ->
                if (issue.type == IssueType.AMBIGUOUS_PROVIDER) {
                    // Ambiguous provider issues may involve multiple components
                    val componentNames = issue.componentName.split(", ")
                    componentNames.forEach { compName ->
                        exclusionSet = exclusionSet.addExcludedComponent(compName.trim())
                    }
                }
            }
            logger.debug("Phase 3: Found ${ambiguousIssues.size} ambiguous provider issues. Total excluded components: ${exclusionSet.excludedComponents.size}")

            // Phase 4: LOW - Singleton Violations
            logger.debug("Phase 4: Detecting singleton violations (excluding ${exclusionSet.excludedComponents.size} components)...")
            val singletonIssues = advancedDetector.detectAdvancedSingletonViolations(components, exclusionSet.excludedComponents)
            issues.addAll(singletonIssues)
            logger.debug("Phase 4: Found ${singletonIssues.size} singleton violation issues.")

            // Phase 5: LOW - Named Qualifier Mismatches
            logger.debug("Phase 5: Detecting named qualifier mismatches (excluding ${exclusionSet.excludedComponents.size} components)...")
            val qualifierIssues = advancedDetector.detectEnhancedNamedQualifierMismatches(components, exclusionSet.excludedComponents)
            issues.addAll(qualifierIssues)
            logger.debug("Phase 5: Found ${qualifierIssues.size} qualifier mismatch issues.")

            logger.info("Priority-based detection complete. Total issues before deduplication: ${issues.size}")

        } catch (e: Exception) {
            logger.error("Error during advanced issue detection, falling back to basic detection", e)
            issues.addAll(basicIssueDetection(components, dependencyGraph))
        }

        val detectionTime = System.currentTimeMillis() - startTime
        logger.info("Issue detection completed in ${detectionTime}ms. Found ${issues.size} issues before deduplication.")

        // Apply deduplication to remove duplicates and enforce priority-based classification
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


        issues.addAll(detectSingletonViolations(components))


        issues.addAll(detectNamedQualifierMismatches(components))

        return issues
    }

    /**
     * Detect singleton violations - multiple singleton instances of the same type
     */
    private fun detectSingletonViolations(components: List<KnitComponent>): List<KnitIssue> {
        val issues = mutableListOf<KnitIssue>()
        val singletonTypes = mutableMapOf<String, MutableList<String>>()


        components.forEach { component ->
            component.providers.filter { it.isSingleton }.forEach { provider ->
                val providedType = provider.providesType ?: provider.returnType
                val providerPath = "${component.packageName}.${component.className}.${provider.methodName}"

                singletonTypes.getOrPut(providedType) { mutableListOf() }.add(providerPath)
            }


            component.dependencies.filter { it.isSingleton }.forEach { dependency ->
                val dependencyType = dependency.targetType
                val dependencyPath = "${component.packageName}.${component.className}.${dependency.propertyName}"


                val nonSingletonProviders = components.flatMap { comp ->
                    comp.providers.filter { prov ->
                        (prov.providesType ?: prov.returnType) == dependencyType && !prov.isSingleton
                    }.map { "${comp.packageName}.${comp.className}.${it.methodName}" }
                }

                if (nonSingletonProviders.isNotEmpty()) {
                    issues.add(
                        KnitIssue(
                            type = IssueType.SINGLETON_VIOLATION,
                            severity = Severity.WARNING,
                            message = "Singleton dependency '${dependency.targetType}' is provided by non-singleton providers",
                            componentName = "${component.packageName}.${component.className}",
                            sourceLocation = component.sourceFile,
                            suggestedFix = "Mark providers as @Singleton: ${nonSingletonProviders.joinToString()}"
                        )
                    )
                }
            }
        }


        singletonTypes.forEach { (type, providers) ->
            if (providers.size > 1) {
                issues.add(
                    KnitIssue(
                        type = IssueType.SINGLETON_VIOLATION,
                        severity = Severity.ERROR,
                        message = "Multiple singleton providers found for type: $type",
                        componentName = providers.joinToString(", "),
                        suggestedFix = "Remove duplicate singleton providers or use different types/qualifiers"
                    )
                )
            }
        }

        return issues
    }

    /**
     * Detect named qualifier mismatches between providers and consumers
     */
    private fun detectNamedQualifierMismatches(components: List<KnitComponent>): List<KnitIssue> {
        val issues = mutableListOf<KnitIssue>()


        val availableProviders = mutableMapOf<String, MutableSet<String?>>()
        components.forEach { component ->
            component.providers.forEach { provider ->
                val providedType = provider.providesType ?: provider.returnType
                availableProviders.getOrPut(providedType) { mutableSetOf() }.add(
                    if (provider.isNamed) provider.namedQualifier else null
                )
            }
        }


        components.forEach { component ->
            component.dependencies.filter { it.isNamed }.forEach { dependency ->
                val targetType = dependency.targetType
                val requestedQualifier = dependency.namedQualifier
                val availableQualifiers = availableProviders[targetType]

                if (availableQualifiers != null) {

                    if (requestedQualifier !in availableQualifiers) {
                        val availableQualifiersList = availableQualifiers.filterNotNull()

                        issues.add(
                            KnitIssue(
                                type = IssueType.NAMED_QUALIFIER_MISMATCH,
                                severity = Severity.ERROR,
                                message = "Named qualifier '@Named($requestedQualifier)' not found for type: $targetType",
                                componentName = "${component.packageName}.${component.className}",
                                sourceLocation = component.sourceFile,
                                suggestedFix = if (availableQualifiersList.isNotEmpty()) {
                                    "Available qualifiers: ${availableQualifiersList.joinToString()}"
                                } else {
                                    "Create a provider with @Named($requestedQualifier) for $targetType"
                                }
                            )
                        )
                    }
                }
            }
        }

        return issues
    }

    fun dispose() {
        coroutineScope.cancel()


        try {
            project.service<AdvancedIssueDetector>().clearCaches()
        } catch (e: Exception) {
            logger.debug("Failed to clear advanced detector caches", e)
        }
    }
}

/**
 * Performance metrics for analysis operations
 */
data class AnalysisMetrics(
    val totalAnalysisTime: Long,
    val sourceAnalysisTime: Long,
    val bytecodeAnalysisTime: Long,
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
            |  Bytecode Analysis: ${bytecodeAnalysisTime}ms
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