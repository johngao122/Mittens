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
                // Find provider for this dependency type
                val providerComponent = findProviderForType(components, dependency.targetType)
                if (providerComponent != null) {
                    val providerNodeId = "${providerComponent.packageName}.${providerComponent.className}"
                    
                    edges.add(GraphEdge(
                        from = consumerNodeId,
                        to = providerNodeId,
                        type = when {
                            dependency.isSingleton -> EdgeType.SINGLETON
                            dependency.isFactory -> EdgeType.FACTORY
                            dependency.isNamed -> EdgeType.NAMED
                            else -> EdgeType.DEPENDENCY
                        },
                        label = dependency.propertyName
                    ))
                }
            }
        }
        
        return DependencyGraph(nodes, edges)
    }
    
    private fun findProviderForType(components: List<KnitComponent>, targetType: String): KnitComponent? {
        // Simple type matching - look for components that provide the target type
        return components.find { component ->
            // Check if component class matches target type
            component.className == targetType.substringAfterLast('.') ||
            "${component.packageName}.${component.className}" == targetType ||
            // Check if component has providers for this type
            component.providers.any { provider ->
                provider.returnType == targetType || 
                provider.providesType == targetType ||
                provider.returnType.substringAfterLast('.') == targetType.substringAfterLast('.')
            }
        }
    }
    
    private fun detectIssues(components: List<KnitComponent>, dependencyGraph: DependencyGraph): List<KnitIssue> {
        val issues = mutableListOf<KnitIssue>()
        
        // Collect existing issues from components
        components.forEach { component ->
            issues.addAll(component.issues)
        }
        
        // Detect circular dependencies
        if (dependencyGraph.hasCycles()) {
            issues.add(KnitIssue(
                type = IssueType.CIRCULAR_DEPENDENCY,
                severity = Severity.ERROR,
                message = "Circular dependencies detected in the dependency graph",
                componentName = "Multiple components",
                suggestedFix = "Review dependency graph and break circular references"
            ))
        }
        
        // Detect ambiguous providers
        val typeToProviders = mutableMapOf<String, MutableList<String>>()
        components.forEach { component ->
            component.providers.forEach { provider ->
                val providedType = provider.providesType ?: provider.returnType
                typeToProviders.getOrPut(providedType) { mutableListOf() }.add(
                    "${component.packageName}.${component.className}.${provider.methodName}"
                )
            }
        }
        
        typeToProviders.forEach { (type, providers) ->
            if (providers.size > 1) {
                issues.add(KnitIssue(
                    type = IssueType.AMBIGUOUS_PROVIDER,
                    severity = Severity.ERROR,
                    message = "Multiple providers found for type: $type",
                    componentName = providers.joinToString(", "),
                    suggestedFix = "Use @Named qualifiers to distinguish between providers"
                ))
            }
        }
        
        // Detect unresolved dependencies
        components.forEach { component ->
            component.dependencies.forEach { dependency ->
                val provider = findProviderForType(components, dependency.targetType)
                if (provider == null) {
                    issues.add(KnitIssue(
                        type = IssueType.UNRESOLVED_DEPENDENCY,
                        severity = Severity.ERROR,
                        message = "No provider found for dependency: ${dependency.targetType}",
                        componentName = "${component.packageName}.${component.className}",
                        sourceLocation = component.sourceFile,
                        suggestedFix = "Create a @Provides method or @Component class for ${dependency.targetType}"
                    ))
                }
            }
        }
        
        return issues
    }
    
    fun dispose() {
        coroutineScope.cancel()
    }
}