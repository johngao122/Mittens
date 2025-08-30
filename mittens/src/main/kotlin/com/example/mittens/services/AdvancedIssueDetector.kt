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


    /**
     * Detect advanced circular dependencies with detailed path information
     */
    fun detectAdvancedCircularDependencies(
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











    /**
     * Phase 2: Validate if a provider is active and should be included in analysis.
     * This method provides a double-validation layer to ensure commented providers are filtered out.
     */
    private fun isProviderActive(component: KnitComponent, provider: KnitProvider): Boolean {

        if (provider.methodName.isBlank() || provider.returnType.isBlank()) {
            logger.debug("Phase 2: Filtering out provider with blank method name or return type in ${component.fullyQualifiedName}")
            return false
        }





        if (isProviderSuspicious(provider)) {
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
    private fun isProviderSuspicious(provider: KnitProvider): Boolean {
        // Generic pattern-based filtering for suspicious provider method names
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

    
    
    










}