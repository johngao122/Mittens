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
    
    // Performance metrics
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
                
                // Step 1: Detect if this is a Knit project
                val projectDetector = project.service<KnitProjectDetector>()
                val detectionResult = projectDetector.detectKnitProject()
                
                if (!detectionResult.isKnitProject) {
                    throw IllegalStateException("This project does not use the Knit framework")
                }
                
                logger.info("Knit project detected - Version: ${detectionResult.knitVersion ?: "Unknown"}")
                
                progressIndicator?.text = "Analyzing source code..."
                progressIndicator?.fraction = 0.3
                
                // Step 2: Analyze source code
                val sourceAnalyzer = project.service<KnitSourceAnalyzer>()
                val sourceComponents = sourceAnalyzer.analyzeProject()
                
                logger.info("Found ${sourceComponents.size} components in source analysis")
                
                progressIndicator?.text = "Compiling project with Knit transformations..."
                progressIndicator?.fraction = 0.5
                
                // Step 3: Execute Gradle compilation (if needed)
                val gradleRunner = project.service<GradleTaskRunner>()
                val compilationResult = gradleRunner.executeKnitCompilation(progressIndicator)
                
                if (!compilationResult.success) {
                    logger.warn("Gradle compilation had issues: ${compilationResult.errorOutput}")
                }
                
                progressIndicator?.text = "Analyzing bytecode..."
                progressIndicator?.fraction = 0.7
                
                // Step 4: Analyze bytecode
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
                
                // Step 5: Merge analysis results and build dependency graph
                val mergedComponents = mergeAnalysisResults(sourceComponents, bytecodeResult.detectedComponents)
                val dependencyGraph = buildDependencyGraph(mergedComponents)
                val issues = detectIssues(mergedComponents, dependencyGraph)
                
                val analysisTime = System.currentTimeMillis() - startTime
                
                val result = AnalysisResult(
                    components = mergedComponents,
                    dependencyGraph = dependencyGraph,
                    issues = issues,
                    timestamp = System.currentTimeMillis(),
                    projectName = project.name,
                    knitVersion = detectionResult.knitVersion,
                    metadata = AnalysisMetadata(
                        analysisTimeMs = analysisTime,
                        bytecodeFilesScanned = bytecodeResult.detectedComponents.size,
                        sourceFilesScanned = sourceComponents.size
                    )
                )
                
                lastAnalysisResult = result
                logger.info("Knit analysis completed successfully in ${analysisTime}ms - " +
                           "Found ${result.components.size} components, ${result.dependencyGraph.edges.size} dependencies, " +
                           "${result.issues.size} issues")
                
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
        // Merge source and bytecode analysis results
        val mergedMap = mutableMapOf<String, KnitComponent>()
        
        // Add source components first (they have more detailed information)
        sourceComponents.forEach { component ->
            val key = "${component.packageName}.${component.className}"
            mergedMap[key] = component
        }
        
        // Merge or add bytecode components
        bytecodeComponents.forEach { bytecodeComponent ->
            val key = "${bytecodeComponent.packageName}.${bytecodeComponent.className}"
            val existingComponent = mergedMap[key]
            
            if (existingComponent != null) {
                // Merge the components, preferring source analysis but adding bytecode insights
                mergedMap[key] = existingComponent.copy(
                    dependencies = (existingComponent.dependencies + bytecodeComponent.dependencies).distinctBy { it.propertyName },
                    providers = (existingComponent.providers + bytecodeComponent.providers).distinctBy { it.methodName }
                )
            } else {
                // Add new component found only in bytecode
                mergedMap[key] = bytecodeComponent
            }
        }
        
        return mergedMap.values.toList()
    }
    
    private fun buildDependencyGraph(components: List<KnitComponent>): DependencyGraph {
        val nodes = mutableListOf<GraphNode>()
        val edges = mutableListOf<GraphEdge>()
        
        // Create nodes for all components
        components.forEach { component ->
            val nodeId = "${component.packageName}.${component.className}"
            val nodeType = when (component.type) {
                ComponentType.COMPONENT -> NodeType.COMPONENT
                ComponentType.PROVIDER -> NodeType.PROVIDER
                ComponentType.CONSUMER -> NodeType.COMPONENT
                ComponentType.COMPOSITE -> NodeType.COMPONENT
            }
            
            nodes.add(GraphNode(
                id = nodeId,
                label = component.className,
                type = nodeType,
                packageName = component.packageName,
                issues = component.issues
            ))
            
            // Add provider nodes for methods that provide specific types
            component.providers.forEach { provider ->
                val providerNodeId = "$nodeId.${provider.methodName}"
                nodes.add(GraphNode(
                    id = providerNodeId,
                    label = "${provider.methodName}(): ${provider.returnType}",
                    type = NodeType.PROVIDER,
                    packageName = component.packageName
                ))
                
                // Edge from component to its provider
                edges.add(GraphEdge(
                    from = nodeId,
                    to = providerNodeId,
                    type = EdgeType.PROVIDES,
                    label = "provides"
                ))
            }
        }
        
        // Create edges for dependencies
        components.forEach { component ->
            val consumerNodeId = "${component.packageName}.${component.className}"
            
            component.dependencies.forEach { dependency ->
                // Find provider for this dependency type with qualifier awareness
                val providerComponent = findProviderForType(
                    components, 
                    dependency.targetType,
                    if (dependency.isNamed) dependency.namedQualifier else null
                )
                if (providerComponent != null) {
                    val providerNodeId = "${providerComponent.packageName}.${providerComponent.className}"
                    
                    edges.add(GraphEdge(
                        from = consumerNodeId,
                        to = providerNodeId,
                        type = when {
                            dependency.isSingleton -> EdgeType.SINGLETON
                            dependency.isFactory -> EdgeType.FACTORY
                            dependency.isNamed -> EdgeType.NAMED
                            dependency.isLoadable -> EdgeType.DEPENDENCY // Could add LOADABLE type later
                            else -> EdgeType.DEPENDENCY
                        },
                        label = if (dependency.isNamed && dependency.namedQualifier != null) {
                            "${dependency.propertyName} (@Named(${dependency.namedQualifier}))"
                        } else {
                            dependency.propertyName
                        }
                    ))
                }
            }
        }
        
        return DependencyGraph(nodes, edges)
    }
    
    private fun findProviderForType(components: List<KnitComponent>, targetType: String, namedQualifier: String? = null): KnitComponent? {
        // Enhanced type matching with named qualifier support
        return components.find { component ->
            // Check if component class matches target type
            val classMatches = component.className == targetType.substringAfterLast('.') ||
                              "${component.packageName}.${component.className}" == targetType
            
            // Check if component has providers for this type
            val providerMatches = component.providers.any { provider ->
                val typeMatches = provider.returnType == targetType || 
                                 provider.providesType == targetType ||
                                 provider.returnType.substringAfterLast('.') == targetType.substringAfterLast('.')
                
                // Check named qualifier matching
                val qualifierMatches = if (namedQualifier != null) {
                    provider.isNamed && provider.namedQualifier == namedQualifier
                } else {
                    !provider.isNamed // If no qualifier specified, match only non-named providers
                }
                
                typeMatches && qualifierMatches
            }
            
            classMatches || providerMatches
        }
    }
    
    private fun detectIssues(components: List<KnitComponent>, dependencyGraph: DependencyGraph): List<KnitIssue> {
        val issues = mutableListOf<KnitIssue>()
        val startTime = System.currentTimeMillis()
        
        // Collect existing issues from components
        components.forEach { component ->
            issues.addAll(component.issues)
        }
        
        logger.info("Starting advanced issue detection for ${components.size} components")
        
        // Use the advanced issue detector for comprehensive analysis
        val advancedDetector = project.service<AdvancedIssueDetector>()
        
        try {
            // Advanced circular dependency detection with detailed paths
            logger.debug("Detecting circular dependencies...")
            issues.addAll(advancedDetector.detectAdvancedCircularDependencies(components, dependencyGraph))
            
            // Enhanced ambiguous provider detection
            logger.debug("Detecting ambiguous providers...")
            issues.addAll(advancedDetector.detectEnhancedAmbiguousProviders(components))
            
            // Improved unresolved dependency detection
            logger.debug("Detecting unresolved dependencies...")
            issues.addAll(advancedDetector.detectImprovedUnresolvedDependencies(components))
            
            // Advanced singleton violation detection
            logger.debug("Detecting singleton violations...")
            issues.addAll(advancedDetector.detectAdvancedSingletonViolations(components))
            
            // Enhanced named qualifier mismatch detection
            logger.debug("Detecting named qualifier mismatches...")
            issues.addAll(advancedDetector.detectEnhancedNamedQualifierMismatches(components))
            
        } catch (e: Exception) {
            logger.error("Error during advanced issue detection, falling back to basic detection", e)
            
            // Fallback to basic detection if advanced detection fails
            issues.addAll(basicIssueDetection(components, dependencyGraph))
        }
        
        val detectionTime = System.currentTimeMillis() - startTime
        logger.info("Issue detection completed in ${detectionTime}ms. Found ${issues.size} issues.")
        
        // Sort issues by severity (ERROR first, then WARNING, then INFO)
        return issues.sortedWith(compareBy<KnitIssue> {
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
    private fun basicIssueDetection(components: List<KnitComponent>, dependencyGraph: DependencyGraph): List<KnitIssue> {
        val issues = mutableListOf<KnitIssue>()
        
        // Basic circular dependency detection
        if (dependencyGraph.hasCycles()) {
            issues.add(KnitIssue(
                type = IssueType.CIRCULAR_DEPENDENCY,
                severity = Severity.ERROR,
                message = "Circular dependencies detected in the dependency graph",
                componentName = "Multiple components",
                suggestedFix = "Review dependency graph and break circular references"
            ))
        }
        
        // Basic singleton violations
        issues.addAll(detectSingletonViolations(components))
        
        // Basic named qualifier mismatches
        issues.addAll(detectNamedQualifierMismatches(components))
        
        return issues
    }
    
    /**
     * Detect singleton violations - multiple singleton instances of the same type
     */
    private fun detectSingletonViolations(components: List<KnitComponent>): List<KnitIssue> {
        val issues = mutableListOf<KnitIssue>()
        val singletonTypes = mutableMapOf<String, MutableList<String>>()
        
        // Collect all singleton providers
        components.forEach { component ->
            component.providers.filter { it.isSingleton }.forEach { provider ->
                val providedType = provider.providesType ?: provider.returnType
                val providerPath = "${component.packageName}.${component.className}.${provider.methodName}"
                
                singletonTypes.getOrPut(providedType) { mutableListOf() }.add(providerPath)
            }
            
            // Also check for singleton dependencies that might create multiple instances
            component.dependencies.filter { it.isSingleton }.forEach { dependency ->
                val dependencyType = dependency.targetType
                val dependencyPath = "${component.packageName}.${component.className}.${dependency.propertyName}"
                
                // Check if there are multiple non-singleton providers for this singleton dependency
                val nonSingletonProviders = components.flatMap { comp ->
                    comp.providers.filter { prov ->
                        (prov.providesType ?: prov.returnType) == dependencyType && !prov.isSingleton
                    }.map { "${comp.packageName}.${comp.className}.${it.methodName}" }
                }
                
                if (nonSingletonProviders.isNotEmpty()) {
                    issues.add(KnitIssue(
                        type = IssueType.SINGLETON_VIOLATION,
                        severity = Severity.WARNING,
                        message = "Singleton dependency '${dependency.targetType}' is provided by non-singleton providers",
                        componentName = "${component.packageName}.${component.className}",
                        sourceLocation = component.sourceFile,
                        suggestedFix = "Mark providers as @Singleton: ${nonSingletonProviders.joinToString()}"
                    ))
                }
            }
        }
        
        // Check for multiple singleton providers of same type
        singletonTypes.forEach { (type, providers) ->
            if (providers.size > 1) {
                issues.add(KnitIssue(
                    type = IssueType.SINGLETON_VIOLATION,
                    severity = Severity.ERROR,
                    message = "Multiple singleton providers found for type: $type",
                    componentName = providers.joinToString(", "),
                    suggestedFix = "Remove duplicate singleton providers or use different types/qualifiers"
                ))
            }
        }
        
        return issues
    }
    
    /**
     * Detect named qualifier mismatches between providers and consumers
     */
    private fun detectNamedQualifierMismatches(components: List<KnitComponent>): List<KnitIssue> {
        val issues = mutableListOf<KnitIssue>()
        
        // Build map of available providers with their qualifiers
        val availableProviders = mutableMapOf<String, MutableSet<String?>>()
        components.forEach { component ->
            component.providers.forEach { provider ->
                val providedType = provider.providesType ?: provider.returnType
                availableProviders.getOrPut(providedType) { mutableSetOf() }.add(
                    if (provider.isNamed) provider.namedQualifier else null
                )
            }
        }
        
        // Check each dependency for qualifier mismatches
        components.forEach { component ->
            component.dependencies.filter { it.isNamed }.forEach { dependency ->
                val targetType = dependency.targetType
                val requestedQualifier = dependency.namedQualifier
                val availableQualifiers = availableProviders[targetType]
                
                if (availableQualifiers != null) {
                    // Check if the requested qualifier exists
                    if (requestedQualifier !in availableQualifiers) {
                        val availableQualifiersList = availableQualifiers.filterNotNull()
                        
                        issues.add(KnitIssue(
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
                        ))
                    }
                }
            }
        }
        
        return issues
    }
    
    fun dispose() {
        coroutineScope.cancel()
        
        // Clear advanced detector caches
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