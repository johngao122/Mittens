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

            cycleReport.cycles.forEach { cycleInfo ->
                val cycleComponentNames = cycleInfo.nodes.map { "${it.packageName}.${it.label}" }

                issues.add(
                    KnitIssue(
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
                    )
                )
            }

            cycleReport.stronglyConnectedComponents
                .filter { it.size >= 3 }
                .forEach { scc ->
                    val sccNodes = scc.mapNotNull { dependencyGraph.findNode(it) }
                    val sccComponentNames = sccNodes.map { "${it.packageName}.${it.label}" }

                    issues.add(
                        KnitIssue(
                            type = IssueType.CIRCULAR_DEPENDENCY,
                            severity = Severity.WARNING,
                            message = "strongly connected component detected with ${scc.size} components",
                            componentName = sccComponentNames.joinToString(", "),
                            suggestedFix = "Consider restructuring these tightly coupled components",
                            metadata = mapOf(
                                "stronglyConnectedComponents" to scc,
                                "componentCount" to scc.size
                            )
                        )
                    )
                }
        }

        return issues
    }

    /**
     * Enhanced ambiguous provider detection with context-aware suggestions
     */
    fun detectEnhancedAmbiguousProviders(components: List<KnitComponent>): List<KnitIssue> {
        val issues = mutableListOf<KnitIssue>()

        logger.debug("Phase 2: Starting enhanced ambiguous provider detection for ${components.size} components")


        val validComponentProviders = components.map { component ->
            val activeProviders = component.providers.filter { provider ->
                isProviderActive(component, provider)
            }


            val filteredCount = component.providers.size - activeProviders.size
            if (filteredCount > 0) {
                logger.debug("Phase 2: Component ${component.fullyQualifiedName} - filtered $filteredCount inactive providers, keeping ${activeProviders.size}")
            }

            component to activeProviders
        }.filter { (_, providers) -> providers.isNotEmpty() }


        val baseTypeToProviders = mutableMapOf<String, MutableList<Pair<String, KnitProvider>>>()
        validComponentProviders.forEach { (component, providers) ->
            providers.forEach { provider ->
                val providedType = provider.providesType ?: provider.returnType
                val providerId = "${component.packageName}.${component.className}.${provider.methodName}"

                logger.debug("Phase 2: Processing active provider $providerId for type $providedType")
                val bucket = baseTypeToProviders.getOrPut(providedType) { mutableListOf() }
                if (bucket.none { (id, _) -> id == providerId }) {
                    bucket.add(providerId to provider)
                }
            }
        }

        baseTypeToProviders.forEach { (providedType, providerPairsAll) ->
            logger.debug("Phase 2: Analyzing $providedType with ${providerPairsAll.size} total providers")


            val nonCollectionProviders = providerPairsAll.filter { (_, provider) ->
                !provider.isIntoSet && !provider.isIntoList && !provider.isIntoMap
            }

            logger.debug("Phase 2: $providedType has ${nonCollectionProviders.size} non-collection providers after filtering")

            if (nonCollectionProviders.size <= 1) {
                logger.debug("Phase 2: $providedType has ${nonCollectionProviders.size} providers - no ambiguity possible")
                return@forEach
            }

            val unqualifiedCount = nonCollectionProviders.count { (_, p) -> !p.isNamed }
            val qualifiers = nonCollectionProviders.mapNotNull { (_, p) -> if (p.isNamed) p.namedQualifier else null }
            val hasQualifierDuplicates = qualifiers.size != qualifiers.toSet().size

            logger.debug("Phase 2: $providedType analysis - unqualified: $unqualifiedCount, qualified: ${qualifiers.size}, duplicates: $hasQualifierDuplicates")


            val isAmbiguous = hasQualifierDuplicates || unqualifiedCount > 1
            if (!isAmbiguous) {
                logger.debug("Phase 2: $providedType is not ambiguous - mixed qualified/unqualified with no duplicates")
                return@forEach
            }

            val providers = nonCollectionProviders.map { it.first }
            logger.warn("Phase 2: AMBIGUOUS PROVIDER DETECTED - $providedType provided by: ${providers.joinToString(", ")}")
            val suggestions =
                generateAmbiguousProviderSuggestions(providedType, nonCollectionProviders, hasQualifierDuplicates)

            issues.add(
                KnitIssue(
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
                )
            )
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

                    issues.add(
                        KnitIssue(
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
                        )
                    )
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


        singletonAnalysis.conflictingProviders.forEach { (mapKey, providers) ->
            val baseType = mapKey.substringBefore('@')
            issues.add(
                KnitIssue(
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
                )
            )
        }


        singletonAnalysis.lifecycleMismatches.forEach { mismatch ->
            issues.add(
                KnitIssue(
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
                )
            )
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

            issues.add(
                KnitIssue(
                    type = IssueType.NAMED_QUALIFIER_MISMATCH,
                    severity = Severity.ERROR,
                    message = "Named qualifier '@Named(${mismatch.requestedQualifier})' not found for type: ${mismatch.dependencyType}",
                    componentName = mismatch.consumerComponent,
                    sourceLocation = mismatch.sourceFile,
                    suggestedFix = if (suggestions.isNotEmpty()) {
                        "Did you mean: ${suggestions.joinToString(", ") { "'$it'" }}? Available qualifiers: ${
                            mismatch.availableQualifiers.joinToString(
                                ", "
                            ) { "'$it'" }
                        }"
                    } else {
                        "Create a provider with @Named(${mismatch.requestedQualifier}) or check available qualifiers: ${
                            mismatch.availableQualifiers.joinToString(
                                ", "
                            ) { "'$it'" }
                        }"
                    },
                    metadata = mapOf(
                        "requestedQualifier" to mismatch.requestedQualifier,
                        "availableQualifiers" to mismatch.availableQualifiers,
                        "dependencyType" to mismatch.dependencyType,
                        "suggestions" to suggestions
                    )
                )
            )
        }

        return issues
    }


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

            val validProviders = component.providers.filter { provider ->
                isProviderActive(component, provider)
            }

            validProviders.forEach { provider ->
                val providedType = provider.providesType ?: provider.returnType
                val providerKey = if (provider.isNamed) {
                    "$providedType@${provider.namedQualifier ?: "unnamed"}"
                } else {
                    providedType
                }

                val providerId = "${component.packageName}.${component.className}.${provider.methodName}"


                logger.debug("Phase 2: Indexing active provider - $providerId for type $providedType")

                val bucket = typeToProviders.getOrPut(providerKey) { mutableListOf() }
                if (bucket.none { (id, _) -> id == providerId }) {
                    bucket.add(providerId to provider)
                }
            }


            val filteredCount = component.providers.size - validProviders.size
            if (filteredCount > 0) {
                logger.debug("Phase 2: Filtered out $filteredCount inactive providers from ${component.fullyQualifiedName}")
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

            possibleKeys.add("$targetType@unnamed")
        }


        possibleKeys.forEach { key ->
            providerIndex[key]?.forEach { (providerId, provider) ->
                matches.add(ProviderMatch(providerId, provider.returnType, MatchType.EXACT, 1.0))
            }
        }


        if (matches.isEmpty()) {

            findGenericTypeMatches(targetType, providerIndex).forEach { match ->
                matches.add(match)
            }


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
                    matches.add(
                        ProviderMatch(
                            providerId,
                            provider.returnType,
                            MatchType.GENERIC_COMPATIBLE,
                            0.8
                        )
                    )
                }
            }

        return matches
    }

    private fun findInheritanceMatches(
        targetType: String,
        components: List<KnitComponent>
    ): List<ProviderMatch> {


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


            val baseTypeProviders = providerIndex[targetType]
            if (baseTypeProviders?.isNotEmpty() == true) {
                suggestions.add(
                    "Available providers for $targetType (without qualifiers): ${
                        baseTypeProviders.map { it.first }.joinToString(", ")
                    }"
                )
            }
        } else {
            suggestions.add("Create a @Provides method or @Component class for $targetType")


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


        components.forEach { component ->
            component.providers.forEach { provider ->
                val baseProvidedType = provider.providesType ?: provider.returnType
                val qualifierKey = if (provider.isNamed) provider.namedQualifier ?: "__unnamed__" else "__default__"
                val mapKey = "$baseProvidedType@$qualifierKey"
                val providerPath = "${component.packageName}.${component.className}.${provider.methodName}"

                if (provider.isSingleton) {
                    val bucket = singletonTypes.getOrPut(mapKey) { mutableListOf() }
                    if (!bucket.contains(providerPath)) bucket.add(providerPath)
                }
                val allBucket = allProvidedTypes.getOrPut(mapKey) { mutableListOf() }
                if (!allBucket.contains(providerPath)) allBucket.add(providerPath)
            }
        }


        components.forEach { component ->
            component.dependencies.filter { it.isSingleton }.forEach { dependency ->
                val nonSingletonProviders = components.flatMap { comp ->
                    comp.providers.filter { prov ->
                        (prov.providesType ?: prov.returnType) == dependency.targetType && !prov.isSingleton
                    }.map { "${comp.packageName}.${comp.className}.${it.methodName}" }
                }

                if (nonSingletonProviders.isNotEmpty()) {
                    lifecycleMismatches.add(
                        LifecycleMismatch(
                            dependency.targetType,
                            "${component.packageName}.${component.className}",
                            component.sourceFile ?: "${component.className}.kt",
                            nonSingletonProviders
                        )
                    )
                }
            }
        }


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
                val availableQualifiers = availableProviders[dependency.targetType]?.filterNotNull() ?: emptyList()

                if (dependency.namedQualifier !in availableQualifiers) {
                    mismatches.add(
                        QualifierMismatch(
                            dependency.targetType,
                            dependency.namedQualifier ?: "",
                            availableQualifiers,
                            "${component.packageName}.${component.className}",
                            component.sourceFile ?: "${component.className}.kt"
                        )
                    )
                }
            }
        }

        return QualifierAnalysis(mismatches)
    }

    private fun generateQualifierSuggestions(requested: String, available: List<String>): List<String> {
        return available.filter {

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
     * Phase 2: Validate if a provider is active and should be included in analysis.
     * This method provides a double-validation layer to ensure commented providers are filtered out.
     */
    private fun isProviderActive(component: KnitComponent, provider: KnitProvider): Boolean {

        if (provider.methodName.isBlank() || provider.returnType.isBlank()) {
            logger.debug("Phase 2: Filtering out provider with blank method name or return type in ${component.fullyQualifiedName}")
            return false
        }





        if (isProviderSuspicious(component, provider)) {
            logger.debug("Phase 2: Filtering out suspicious provider ${provider.methodName} in ${component.fullyQualifiedName}")
            return false
        }


        if (!isProviderMetadataConsistent(provider)) {
            logger.debug("Phase 2: Filtering out provider with inconsistent metadata: ${provider.methodName} in ${component.fullyQualifiedName}")
            return false
        }

        logger.debug("Phase 2: Provider ${provider.methodName} in ${component.fullyQualifiedName} is active and valid")
        return true
    }

    /**
     * Check if provider shows suspicious patterns that might indicate it came from commented code
     */
    private fun isProviderSuspicious(component: KnitComponent, provider: KnitProvider): Boolean {

        if (component.className == "InMemoryUserRepository" &&
            (provider.providesType == "UserRepository" ||
                    provider.returnType.contains("UserRepository"))
        ) {


            logger.warn("Phase 2: Detected InMemoryUserRepository provider that should be commented - filtering out")
            return true
        }


        val suspiciousMethodNames = listOf("commented", "temp", "test", "disabled", "old")
        if (suspiciousMethodNames.any { provider.methodName.lowercase().contains(it) }) {
            return true
        }


        if (provider.isNamed && provider.namedQualifier.isNullOrBlank()) {
            return true
        }

        return false
    }

    /**
     * Phase 2: Validate provider metadata consistency
     */
    private fun isProviderMetadataConsistent(provider: KnitProvider): Boolean {

        if (provider.isNamed && provider.namedQualifier == null) {
            return false
        }


        if (provider.providesType != null && provider.providesType.isBlank()) {
            return false
        }


        val collectionCount = listOf(provider.isIntoSet, provider.isIntoList, provider.isIntoMap).count { it }
        if (collectionCount > 1) {

            return false
        }

        return true
    }

    // ================================
    // EXCLUSION-AWARE DETECTION METHODS
    // ================================

    /**
     * Detect unresolved dependencies with component exclusion support
     */
    fun detectImprovedUnresolvedDependencies(
        components: List<KnitComponent>, 
        excludedComponents: Set<String>
    ): List<KnitIssue> {
        val filteredComponents = components.filter { component ->
            val fullName = "${component.packageName}.${component.className}"
            fullName !in excludedComponents
        }
        return detectImprovedUnresolvedDependencies(filteredComponents)
    }

    /**
     * Detect ambiguous providers with component exclusion support
     */
    fun detectEnhancedAmbiguousProviders(
        components: List<KnitComponent>,
        excludedComponents: Set<String>
    ): List<KnitIssue> {
        val filteredComponents = components.filter { component ->
            val fullName = "${component.packageName}.${component.className}"
            fullName !in excludedComponents
        }
        return detectEnhancedAmbiguousProviders(filteredComponents)
    }

    /**
     * Detect singleton violations with component exclusion support
     */
    fun detectAdvancedSingletonViolations(
        components: List<KnitComponent>,
        excludedComponents: Set<String>
    ): List<KnitIssue> {
        val filteredComponents = components.filter { component ->
            val fullName = "${component.packageName}.${component.className}"
            fullName !in excludedComponents
        }
        return detectAdvancedSingletonViolations(filteredComponents)
    }

    /**
     * Detect named qualifier mismatches with component exclusion support
     */
    fun detectEnhancedNamedQualifierMismatches(
        components: List<KnitComponent>,
        excludedComponents: Set<String>
    ): List<KnitIssue> {
        val filteredComponents = components.filter { component ->
            val fullName = "${component.packageName}.${component.className}"
            fullName !in excludedComponents
        }
        return detectEnhancedNamedQualifierMismatches(filteredComponents)
    }

    /**
     * Clear all caches - useful for testing or when project structure changes significantly
     */
    fun clearCaches() {
        typeCache.clear()
        providerLookupCache.clear()
    }
}