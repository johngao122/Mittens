package com.example.mittens.services

import com.example.mittens.model.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

/**
 * Advanced issue detection service with performance optimizations
 * Provides comprehensive analysis for complex dependency injection scenarios
 */
@Service(Service.Level.PROJECT)
class AdvancedIssueDetector(private val project: Project) {
    
    private val logger = Logger.getInstance(AdvancedIssueDetector::class.java)
    private val typeCache = mutableMapOf<String, TypeMatchResult>()
    private val providerLookupCache = mutableMapOf<String, List<ProviderMatch>>()
    
    data class TypeMatchResult(
        val exactMatches: List<String>,
        val fuzzyMatches: List<String>,
        val inheritanceMatches: List<String>
    )
    
    data class ProviderMatch(
        val providerId: String,
        val providerType: String,
        val matchType: MatchType,
        val confidence: Double
    )
    
    enum class MatchType {
        EXACT,
        INHERITANCE,
        GENERIC_COMPATIBLE,
        FUZZY
    }
    
    /**
     * Detect advanced circular dependencies with detailed path information
     */
    fun detectAdvancedCircularDependencies(
        components: List<KnitComponent>, 
        dependencyGraph: DependencyGraph
    ): List<KnitIssue> {
        val issues = mutableListOf<KnitIssue>()
        val cycleReport = dependencyGraph.getCycleReport()
        
        if (cycleReport.hasCycles) {
            // Create issues for each individual cycle with detailed information
            cycleReport.cycles.forEach { cycleInfo ->
                val cycleComponentNames = cycleInfo.nodes.map { "${it.packageName}.${it.label}" }
                
                issues.add(KnitIssue(
                    type = IssueType.CIRCULAR_DEPENDENCY,
                    severity = when (cycleInfo.length) {
                        2 -> Severity.ERROR
                        in 3..4 -> Severity.ERROR
                        else -> Severity.WARNING
                    },
                    message = "Circular dependency detected: ${cycleInfo.getDisplayPath()}",
                    componentName = cycleComponentNames.joinToString(", "),
                    suggestedFix = "Break the cycle by: 1) Using dependency injection interfaces, " +
                            "2) Introducing a mediator component, or 3) Restructuring component relationships",
                    metadata = mapOf(
                        "cycleLength" to cycleInfo.length,
                        "cyclePath" to cycleInfo.path,
                        "affectedComponents" to cycleComponentNames
                    )
                ))
            }
            // Also add warnings for substantial strongly connected components
            cycleReport.stronglyConnectedComponents
                .filter { it.size >= 3 }
                .forEach { scc ->
                    val sccNodes = scc.mapNotNull { dependencyGraph.findNode(it) }
                    val sccComponentNames = sccNodes.map { "${it.packageName}.${it.label}" }
                    
                    issues.add(KnitIssue(
                        type = IssueType.CIRCULAR_DEPENDENCY,
                        severity = Severity.WARNING,
                        message = "strongly connected component detected with ${scc.size} components",
                        componentName = sccComponentNames.joinToString(", "),
                        suggestedFix = "Consider restructuring these tightly coupled components",
                        metadata = mapOf(
                            "stronglyConnectedComponents" to scc,
                            "componentCount" to scc.size
                        )
                    ))
                }
        }
        
        return issues
    }
    
    /**
     * Enhanced ambiguous provider detection with context-aware suggestions
     */
    fun detectEnhancedAmbiguousProviders(components: List<KnitComponent>): List<KnitIssue> {
        val issues = mutableListOf<KnitIssue>()

        // Aggregate providers by base provided type (ignore qualifiers for grouping)
        val baseTypeToProviders = mutableMapOf<String, MutableList<Pair<String, KnitProvider>>>()
        components.forEach { component ->
            component.providers.forEach { provider ->
                val providedType = provider.providesType ?: provider.returnType
                val providerId = "${component.packageName}.${component.className}.${provider.methodName}"
                baseTypeToProviders.getOrPut(providedType) { mutableListOf() }.add(providerId to provider)
            }
        }

        baseTypeToProviders.forEach { (providedType, providerPairsAll) ->
            // Ignore multibindings (collection contributions) for ambiguity purposes
            val nonCollectionProviders = providerPairsAll.filter { (_, provider) ->
                !provider.isIntoSet && !provider.isIntoList && !provider.isIntoMap
            }

            if (nonCollectionProviders.size <= 1) return@forEach

            val unqualifiedCount = nonCollectionProviders.count { (_, p) -> !p.isNamed }
            val qualifiers = nonCollectionProviders.mapNotNull { (_, p) -> if (p.isNamed) p.namedQualifier else null }
            val hasQualifierDuplicates = qualifiers.size != qualifiers.toSet().size

            // Ambiguity rules:
            // - Duplicate unqualified providers -> ambiguous
            // - Same qualifier used by 2+ providers -> ambiguous (named conflict)
            // - Mixed qualified and unqualified with only one of each -> not ambiguous
            val isAmbiguous = hasQualifierDuplicates || unqualifiedCount > 1
            if (!isAmbiguous) return@forEach

            val providers = nonCollectionProviders.map { it.first }
            val suggestions = generateAmbiguousProviderSuggestions(providedType, nonCollectionProviders, hasQualifierDuplicates)

            issues.add(KnitIssue(
                type = IssueType.AMBIGUOUS_PROVIDER,
                severity = Severity.ERROR,
                message = if (hasQualifierDuplicates) {
                    "Multiple providers found for type: $providedType with same qualifier"
                } else {
                    "Multiple providers found for type: $providedType without qualifiers"
                },
                componentName = providers.joinToString(", "),
                suggestedFix = suggestions,
                metadata = mapOf(
                    "providedType" to providedType,
                    "isNamedConflict" to hasQualifierDuplicates,
                    "providerCount" to nonCollectionProviders.size,
                    "providers" to providers
                )
            ))
        }

        return issues
    }
    
    /**
     * Improved unresolved dependency detection with generic type support
     */
    fun detectImprovedUnresolvedDependencies(components: List<KnitComponent>): List<KnitIssue> {
        val issues = mutableListOf<KnitIssue>()
        val providerIndex = buildProviderIndex(components)
        
        components.forEach { component ->
            component.dependencies.forEach { dependency ->
                val matchResult = findEnhancedProviderMatches(
                    dependency.targetType,
                    dependency.namedQualifier,
                    providerIndex,
                    components
                )
                
                if (matchResult.isEmpty()) {
                    val suggestions = generateUnresolvedDependencySuggestions(
                        dependency.targetType,
                        dependency.namedQualifier,
                        providerIndex
                    )
                    
                    val message = if (dependency.isNamed) {
                        "No provider found for dependency: ${dependency.targetType} with qualifier '@Named(${dependency.namedQualifier})'"
                    } else {
                        "No provider found for dependency: ${dependency.targetType}"
                    }
                    
                    issues.add(KnitIssue(
                        type = IssueType.UNRESOLVED_DEPENDENCY,
                        severity = Severity.ERROR,
                        message = message,
                        componentName = "${component.packageName}.${component.className}",
                        sourceLocation = component.sourceFile,
                        suggestedFix = suggestions,
                        metadata = mapOf(
                            "targetType" to dependency.targetType,
                            "namedQualifier" to (dependency.namedQualifier ?: ""),
                            "isNamed" to dependency.isNamed,
                            "propertyName" to dependency.propertyName
                        )
                    ))
                }
            }
        }
        
        return issues
    }
    
    /**
     * Advanced singleton violation detection with lifecycle analysis
     */
    fun detectAdvancedSingletonViolations(components: List<KnitComponent>): List<KnitIssue> {
        val issues = mutableListOf<KnitIssue>()
        val singletonAnalysis = analyzeSingletonLifecycles(components)
        
        // Multiple singleton providers for same type (considering qualifiers separately)
        singletonAnalysis.conflictingProviders.forEach { (mapKey, providers) ->
            val baseType = mapKey.substringBefore('@')
            issues.add(KnitIssue(
                type = IssueType.SINGLETON_VIOLATION,
                severity = Severity.ERROR,
                message = "Multiple singleton providers found for type: $baseType",
                componentName = providers.joinToString(", "),
                suggestedFix = "Remove duplicate singleton providers or use different types/qualifiers. " +
                        "Consider using component-level singletons instead of global ones.",
                metadata = mapOf(
                    "conflictingType" to baseType,
                    "providerCount" to providers.size,
                    "providers" to providers
                )
            ))
        }
        
        // Singleton dependency with non-singleton provider
        singletonAnalysis.lifecycleMismatches.forEach { mismatch ->
            issues.add(KnitIssue(
                type = IssueType.SINGLETON_VIOLATION,
                severity = Severity.WARNING,
                message = "Singleton dependency '${mismatch.dependencyType}' is provided by non-singleton providers",
                componentName = mismatch.consumerComponent,
                sourceLocation = mismatch.sourceFile,
                suggestedFix = "Mark providers as @Singleton: ${mismatch.nonSingletonProviders.joinToString()}",
                metadata = mapOf(
                    "dependencyType" to mismatch.dependencyType,
                    "consumerComponent" to mismatch.consumerComponent,
                    "nonSingletonProviders" to mismatch.nonSingletonProviders
                )
            ))
        }
        
        return issues
    }
    
    /**
     * Enhanced named qualifier mismatch detection with fuzzy matching
     */
    fun detectEnhancedNamedQualifierMismatches(components: List<KnitComponent>): List<KnitIssue> {
        val issues = mutableListOf<KnitIssue>()
        val qualifierAnalysis = analyzeQualifierUsage(components)
        
        qualifierAnalysis.mismatches.forEach { mismatch ->
            val suggestions = generateQualifierSuggestions(
                mismatch.requestedQualifier,
                mismatch.availableQualifiers
            )
            
            issues.add(KnitIssue(
                type = IssueType.NAMED_QUALIFIER_MISMATCH,
                severity = Severity.ERROR,
                message = "Named qualifier '@Named(${mismatch.requestedQualifier})' not found for type: ${mismatch.dependencyType}",
                componentName = mismatch.consumerComponent,
                sourceLocation = mismatch.sourceFile,
                suggestedFix = if (suggestions.isNotEmpty()) {
                    "Did you mean: ${suggestions.joinToString(", ") { "'$it'" }}? Available qualifiers: ${mismatch.availableQualifiers.joinToString(", ") { "'$it'" }}"
                } else {
                    "Create a provider with @Named(${mismatch.requestedQualifier}) or check available qualifiers: ${mismatch.availableQualifiers.joinToString(", ") { "'$it'" }}"
                },
                metadata = mapOf(
                    "requestedQualifier" to mismatch.requestedQualifier,
                    "availableQualifiers" to mismatch.availableQualifiers,
                    "dependencyType" to mismatch.dependencyType,
                    "suggestions" to suggestions
                )
            ))
        }
        
        return issues
    }
    
    // Helper classes and methods
    
    private data class SingletonAnalysis(
        val conflictingProviders: Map<String, List<String>>,
        val lifecycleMismatches: List<LifecycleMismatch>
    )
    
    private data class LifecycleMismatch(
        val dependencyType: String,
        val consumerComponent: String,
        val sourceFile: String,
        val nonSingletonProviders: List<String>
    )
    
    private data class QualifierAnalysis(
        val mismatches: List<QualifierMismatch>
    )
    
    private data class QualifierMismatch(
        val dependencyType: String,
        val requestedQualifier: String,
        val availableQualifiers: List<String>,
        val consumerComponent: String,
        val sourceFile: String
    )
    
    private fun buildProviderIndex(components: List<KnitComponent>): Map<String, List<Pair<String, KnitProvider>>> {
        val typeToProviders = mutableMapOf<String, MutableList<Pair<String, KnitProvider>>>()
        
        components.forEach { component ->
            component.providers.forEach { provider ->
                val providedType = provider.providesType ?: provider.returnType
                val providerKey = if (provider.isNamed) {
                    "$providedType@${provider.namedQualifier ?: "unnamed"}"
                } else {
                    providedType
                }
                
                typeToProviders.getOrPut(providerKey) { mutableListOf() }.add(
                    "${component.packageName}.${component.className}.${provider.methodName}" to provider
                )
            }
        }
        
        return typeToProviders
    }
    
    private fun findEnhancedProviderMatches(
        targetType: String,
        namedQualifier: String?,
        providerIndex: Map<String, List<Pair<String, KnitProvider>>>,
        components: List<KnitComponent>
    ): List<ProviderMatch> {
        val matches = mutableListOf<ProviderMatch>()
        val possibleKeys = mutableListOf<String>()
        if (namedQualifier != null) {
            possibleKeys.add("$targetType@$namedQualifier")
        } else {
            possibleKeys.add(targetType)
            // Also consider providers that were indexed with an explicit unnamed qualifier
            possibleKeys.add("$targetType@unnamed")
        }

        // Exact matches across possible keys
        possibleKeys.forEach { key ->
            providerIndex[key]?.forEach { (providerId, provider) ->
                matches.add(ProviderMatch(providerId, provider.returnType, MatchType.EXACT, 1.0))
            }
        }
        
        // If no exact matches, try other strategies
        if (matches.isEmpty()) {
            // Generic type matching
            findGenericTypeMatches(targetType, providerIndex).forEach { match ->
                matches.add(match)
            }
            
            // Inheritance matching (simplified version)
            findInheritanceMatches(targetType, components).forEach { match ->
                matches.add(match)
            }
        }
        
        return matches
    }
    
    private fun findGenericTypeMatches(
        targetType: String,
        providerIndex: Map<String, List<Pair<String, KnitProvider>>>
    ): List<ProviderMatch> {
        val matches = mutableListOf<ProviderMatch>()
        val baseType = targetType.substringBefore('<')
        
        providerIndex.keys
            .filter { it.startsWith(baseType) && it.contains('<') }
            .forEach { key ->
                val providers = providerIndex[key] ?: emptyList()
                providers.forEach { (providerId, provider) ->
                    matches.add(ProviderMatch(
                        providerId, 
                        provider.returnType, 
                        MatchType.GENERIC_COMPATIBLE, 
                        0.8
                    ))
                }
            }
        
        return matches
    }
    
    private fun findInheritanceMatches(
        targetType: String,
        components: List<KnitComponent>
    ): List<ProviderMatch> {
        // Simplified inheritance matching - in a real implementation,
        // this would use the IDE's type system for proper inheritance analysis
        return emptyList()
    }
    
    private fun generateAmbiguousProviderSuggestions(
        providedType: String,
        providerPairs: List<Pair<String, KnitProvider>>,
        isNamedConflict: Boolean
    ): String {
        return if (!isNamedConflict) {
            "Use @Named qualifiers to distinguish between providers:\n" +
                    providerPairs.mapIndexed { index, (providerId, _) ->
                        "  @Named(\"${providedType.substringAfterLast('.')}_$index\") $providerId"
                    }.joinToString("\n")
        } else {
            "Use different @Named qualifiers or remove duplicate providers:\n" +
                    providerPairs.map { (providerId, _) -> "  $providerId" }.joinToString("\n")
        }
    }
    
    private fun generateUnresolvedDependencySuggestions(
        targetType: String,
        namedQualifier: String?,
        providerIndex: Map<String, List<Pair<String, KnitProvider>>>
    ): String {
        val suggestions = mutableListOf<String>()
        
        if (namedQualifier != null) {
            suggestions.add("Create a @Provides method with @Named($namedQualifier) for $targetType")
            
            // Look for similar types without qualifiers
            val baseTypeProviders = providerIndex[targetType]
            if (baseTypeProviders?.isNotEmpty() == true) {
                suggestions.add("Available providers for $targetType (without qualifiers): ${baseTypeProviders.map { it.first }.joinToString(", ")}")
            }
        } else {
            suggestions.add("Create a @Provides method or @Component class for $targetType")
            
            // Look for similar named providers
            val namedProviders = providerIndex.keys.filter { it.startsWith("$targetType@") }
            if (namedProviders.isNotEmpty()) {
                val qualifiers = namedProviders.map { it.substringAfter('@') }
                suggestions.add("Available named providers: ${qualifiers.joinToString(", ") { "@Named($it)" }}")
            }
        }
        
        return suggestions.joinToString("\n")
    }
    
    private fun analyzeSingletonLifecycles(components: List<KnitComponent>): SingletonAnalysis {
        val singletonTypes = mutableMapOf<String, MutableList<String>>()
        val allProvidedTypes = mutableMapOf<String, MutableList<String>>()
        val lifecycleMismatches = mutableListOf<LifecycleMismatch>()
        
        // Collect providers: both explicit singletons and all providers (to detect duplicate provision regardless of @Singleton)
        components.forEach { component ->
            component.providers.forEach { provider ->
                val baseProvidedType = provider.providesType ?: provider.returnType
                val qualifierKey = if (provider.isNamed) provider.namedQualifier ?: "__unnamed__" else "__default__"
                val mapKey = "$baseProvidedType@$qualifierKey"
                val providerPath = "${component.packageName}.${component.className}.${provider.methodName}"

                if (provider.isSingleton) {
                    singletonTypes.getOrPut(mapKey) { mutableListOf() }.add(providerPath)
                }
                allProvidedTypes.getOrPut(mapKey) { mutableListOf() }.add(providerPath)
            }
        }
        
        // Check for lifecycle mismatches
        components.forEach { component ->
            component.dependencies.filter { it.isSingleton }.forEach { dependency ->
                val nonSingletonProviders = components.flatMap { comp ->
                    comp.providers.filter { prov ->
                        (prov.providesType ?: prov.returnType) == dependency.targetType && !prov.isSingleton
                    }.map { "${comp.packageName}.${comp.className}.${it.methodName}" }
                }
                
                if (nonSingletonProviders.isNotEmpty()) {
                    lifecycleMismatches.add(LifecycleMismatch(
                        dependency.targetType,
                        "${component.packageName}.${component.className}",
                        component.sourceFile ?: "${component.className}.kt",
                        nonSingletonProviders
                    ))
                }
            }
        }
        
        // Conflicts: multiple singleton providers for same type+qualifier OR multiple providers of same type+qualifier (legacy duplicate singletons)
        val conflictingProviders = mutableMapOf<String, List<String>>()
        singletonTypes.filterValues { it.size > 1 }.forEach { (k, v) -> conflictingProviders[k] = v }
        allProvidedTypes.filterValues { it.size > 1 }.forEach { (k, v) ->
            if (!conflictingProviders.containsKey(k)) conflictingProviders[k] = v
        }
        
        return SingletonAnalysis(conflictingProviders, lifecycleMismatches)
    }
    
    private fun analyzeQualifierUsage(components: List<KnitComponent>): QualifierAnalysis {
        val mismatches = mutableListOf<QualifierMismatch>()
        val availableProviders = mutableMapOf<String, MutableSet<String?>>()
        
        // Build available providers map
        components.forEach { component ->
            component.providers.forEach { provider ->
                val providedType = provider.providesType ?: provider.returnType
                availableProviders.getOrPut(providedType) { mutableSetOf() }.add(
                    if (provider.isNamed) provider.namedQualifier else null
                )
            }
        }
        
        // Check for mismatches
        components.forEach { component ->
            component.dependencies.filter { it.isNamed }.forEach { dependency ->
                val availableQualifiers = availableProviders[dependency.targetType]?.filterNotNull() ?: emptyList()
                
                if (dependency.namedQualifier !in availableQualifiers) {
                    mismatches.add(QualifierMismatch(
                        dependency.targetType,
                        dependency.namedQualifier ?: "",
                        availableQualifiers,
                        "${component.packageName}.${component.className}",
                        component.sourceFile ?: "${component.className}.kt"
                    ))
                }
            }
        }
        
        return QualifierAnalysis(mismatches)
    }
    
    private fun generateQualifierSuggestions(requested: String, available: List<String>): List<String> {
        return available.filter { 
            // Simple fuzzy matching based on edit distance
            calculateEditDistance(requested.lowercase(), it.lowercase()) <= 2
        }.sortedBy { 
            calculateEditDistance(requested.lowercase(), it.lowercase()) 
        }
    }
    
    private fun calculateEditDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                dp[i][j] = if (s1[i - 1] == s2[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        
        return dp[s1.length][s2.length]
    }
    
    /**
     * Clear all caches - useful for testing or when project structure changes significantly
     */
    fun clearCaches() {
        typeCache.clear()
        providerLookupCache.clear()
    }
}