package com.example.mittens.services

import com.example.mittens.model.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Service responsible for validating detected issues against source code
 * to ensure accuracy and reduce false positives.
 */
@Service
class IssueValidator(private val project: Project) {

    private val logger = thisLogger()

    /**
     * Validates all issues and returns updated issues with validation status and confidence scores
     */
    fun validateIssues(
        issues: List<KnitIssue>, 
        components: List<KnitComponent>,
        settings: ValidationSettings = ValidationSettings()
    ): List<KnitIssue> {
        if (!settings.validationEnabled) {
            return issues
        }

        logger.info("Starting issue validation for ${issues.size} issues")
        val startTime = System.currentTimeMillis()

        val validatedIssues = issues.map { issue ->
            try {
                when (issue.type) {
                    IssueType.CIRCULAR_DEPENDENCY -> validateCircularDependency(issue, components)
                    IssueType.AMBIGUOUS_PROVIDER -> validateAmbiguousProvider(issue, components)
                }
            } catch (e: Exception) {
                logger.warn("Failed to validate issue ${issue.type} for ${issue.componentName}", e)
                issue.copy(
                    validationStatus = ValidationStatus.VALIDATION_FAILED,
                    confidenceScore = 0.5
                )
            }
        }

        val validationTime = System.currentTimeMillis() - startTime
        logger.info("Issue validation completed in ${validationTime}ms")

        
        
        val filtered = validatedIssues.filter { issue ->
            issue.confidenceScore >= settings.minimumConfidenceThreshold ||
            issue.validationStatus == ValidationStatus.VALIDATED_FALSE_POSITIVE
        }
        if (filtered.size != validatedIssues.size) {
            logger.debug("Filtered out ${validatedIssues.size - filtered.size} low-confidence issues (< ${settings.minimumConfidenceThreshold})")
        }

        return filtered
    }

    /**
     * Validates circular dependency issues by checking for actual cyclic references in source code
     */
    private fun validateCircularDependency(issue: KnitIssue, components: List<KnitComponent>): KnitIssue {
        val componentNames = extractComponentNamesFromIssue(issue)
        
        
        if (componentNames.isEmpty() || componentNames.any { it.isBlank() }) {
            return issue.copy(
                validationStatus = ValidationStatus.VALIDATION_FAILED,
                confidenceScore = 0.5
            )
        }
        
        
        

        
        val involvedComponents = components.filter { component ->
            componentNames.any { name -> 
                component.className == name || "${component.packageName}.${component.className}" == name 
            }
        }

        if (involvedComponents.size < componentNames.size) {
            return issue.copy(
                validationStatus = ValidationStatus.VALIDATED_FALSE_POSITIVE,
                confidenceScore = 0.2
            )
        }

        
        val hasCycle = detectCycleBetweenComponents(involvedComponents)
        val confidenceScore = if (hasCycle) {
            calculateCircularDependencyConfidence(involvedComponents)
        } else {
            0.2
        }

        return issue.copy(
            validationStatus = if (hasCycle) ValidationStatus.VALIDATED_TRUE_POSITIVE else ValidationStatus.VALIDATED_FALSE_POSITIVE,
            confidenceScore = confidenceScore
        )
    }

    /**
     * Validates unresolved dependency issues by checking if providers actually exist
     */
    private fun validateUnresolvedDependency(issue: KnitIssue, components: List<KnitComponent>): KnitIssue {
        val dependencyType = extractDependencyTypeFromIssue(issue)
        val consumerComponent = findComponentByName(issue.componentName, components)

        if (consumerComponent == null) {
            return issue.copy(
                validationStatus = ValidationStatus.VALIDATED_FALSE_POSITIVE,
                confidenceScore = 0.1
            )
        }

        
        val hasProvider = components.any { component ->
            component.providers.any { provider ->
                provider.returnType == dependencyType ||
                provider.providesType == dependencyType ||
                provider.returnType.endsWith(dependencyType) ||
                component.className == dependencyType.substringAfterLast('.')
            }
        }

        
        val isCommentedDependency = checkForCommentedDependency(consumerComponent, dependencyType)

        val confidenceScore = when {
            isCommentedDependency -> 0.1 
            !hasProvider -> 0.9 
            else -> 0.2 
        }

        val validationStatus = when {
            isCommentedDependency -> ValidationStatus.VALIDATED_FALSE_POSITIVE
            !hasProvider -> ValidationStatus.VALIDATED_TRUE_POSITIVE
            else -> ValidationStatus.VALIDATED_FALSE_POSITIVE
        }

        return issue.copy(
            validationStatus = validationStatus,
            confidenceScore = confidenceScore
        )
    }

    /**
     * Validates ambiguous provider issues by checking for actual conflicting providers
     */
    private fun validateAmbiguousProvider(issue: KnitIssue, components: List<KnitComponent>): KnitIssue {
        val providerType = extractProviderTypeFromIssue(issue)
        val namedQualifier = issue.metadata["namedQualifier"] as? String

        val activeProviders = components.flatMap { component ->
            component.providers.filter { provider ->
                val typeMatches = provider.returnType == providerType ||
                               provider.providesType == providerType ||
                               provider.returnType.endsWith(providerType)
                
                val qualifierMatches = if (namedQualifier != null) {
                    provider.isNamed && provider.namedQualifier == namedQualifier
                } else {
                    !provider.isNamed
                }

                typeMatches && qualifierMatches
            }.map { provider -> 
                Pair(component, provider)
            }
        }

        
        val validProviders = activeProviders.filter { (component, provider) ->
            !isProviderCommented(component, provider)
        }

        val hasActualAmbiguity = validProviders.size > 1
        val confidenceScore = when {
            validProviders.isEmpty() -> 0.1 
            validProviders.size == 1 -> 0.15 
            validProviders.size > 1 -> 0.95 
            else -> 0.5
        }

        return issue.copy(
            validationStatus = if (hasActualAmbiguity) ValidationStatus.VALIDATED_TRUE_POSITIVE else ValidationStatus.VALIDATED_FALSE_POSITIVE,
            confidenceScore = confidenceScore
        )
    }

    /**
     * Validates singleton violation issues
     */
    private fun validateSingletonViolation(issue: KnitIssue, components: List<KnitComponent>): KnitIssue {
        val singletonType = issue.metadata["conflictingType"] as? String ?: issue.componentName

        val actualSingletonProviders = components.flatMap { component ->
            component.providers.filter { provider ->
                provider.isSingleton && (
                    provider.returnType == singletonType ||
                    provider.providesType == singletonType ||
                    provider.returnType.endsWith(singletonType)
                )
            }
        }

        val hasActualViolation = actualSingletonProviders.size > 1
        val confidenceScore = if (hasActualViolation) 0.9 else 0.2

        return issue.copy(
            validationStatus = if (hasActualViolation) ValidationStatus.VALIDATED_TRUE_POSITIVE else ValidationStatus.VALIDATED_FALSE_POSITIVE,
            confidenceScore = confidenceScore
        )
    }

    /**
     * Validates named qualifier mismatch issues
     */
    private fun validateNamedQualifierMismatch(issue: KnitIssue, components: List<KnitComponent>): KnitIssue {
        val dependencyType = issue.metadata["dependencyType"] as? String
        val consumerComponent = findComponentByName(issue.componentName, components)

        if (consumerComponent == null || dependencyType == null) {
            return issue.copy(
                validationStatus = ValidationStatus.VALIDATED_FALSE_POSITIVE,
                confidenceScore = 0.2
            )
        }

        
        val dependency = consumerComponent.dependencies.find { dep ->
            dep.targetType == dependencyType
        }

        if (dependency == null) {
            return issue.copy(
                validationStatus = ValidationStatus.VALIDATED_FALSE_POSITIVE,
                confidenceScore = 0.2
            )
        }

        
        val matchingProviders = components.flatMap { component ->
            component.providers.filter { provider ->
                val typeMatches = provider.returnType == dependencyType || provider.providesType == dependencyType
                val qualifierMatches = provider.isNamed == dependency.isNamed &&
                                     provider.namedQualifier == dependency.namedQualifier
                typeMatches && qualifierMatches
            }
        }

        val hasActualMismatch = matchingProviders.isEmpty()
        val confidenceScore = if (hasActualMismatch) 0.8 else 0.25

        return issue.copy(
            validationStatus = if (hasActualMismatch) ValidationStatus.VALIDATED_TRUE_POSITIVE else ValidationStatus.VALIDATED_FALSE_POSITIVE,
            confidenceScore = confidenceScore
        )
    }

    /**
     * Validates missing component annotation issues
     */
    private fun validateMissingComponentAnnotation(issue: KnitIssue, components: List<KnitComponent>): KnitIssue {
        val component = findComponentByName(issue.componentName, components)
        
        if (component == null) {
            return issue.copy(
                validationStatus = ValidationStatus.VALIDATED_FALSE_POSITIVE,
                confidenceScore = 0.1
            )
        }

        
        val hasDependencies = component.dependencies.isNotEmpty()
        val hasProviders = component.providers.isNotEmpty()
        
        val needsAnnotation = hasDependencies || hasProviders
        val confidenceScore = if (needsAnnotation) 0.7 else 0.25

        return issue.copy(
            validationStatus = if (needsAnnotation) ValidationStatus.VALIDATED_TRUE_POSITIVE else ValidationStatus.VALIDATED_FALSE_POSITIVE,
            confidenceScore = confidenceScore
        )
    }

    

    private fun extractComponentNamesFromIssue(issue: KnitIssue): List<String> {
        if (issue.componentName.isBlank()) {
            return emptyList()
        }
        return issue.componentName.split(", ", " -> ", " ↔ ").map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun extractDependencyTypeFromIssue(issue: KnitIssue): String {
        return issue.metadata["dependencyType"] as? String
            ?: issue.message.substringAfter("for ").substringBefore(" ")
    }

    private fun extractProviderTypeFromIssue(issue: KnitIssue): String {
        return issue.metadata["conflictingType"] as? String
            ?: issue.message.substringAfter("for type: ").substringBefore(" ")
    }

    private fun findComponentByName(componentName: String, components: List<KnitComponent>): KnitComponent? {
        return components.find { component ->
            component.className == componentName ||
            "${component.packageName}.${component.className}" == componentName ||
            componentName.contains(component.className)
        }
    }

    private fun detectCycleBetweenComponents(components: List<KnitComponent>): Boolean {
        
        val adjacencyList = mutableMapOf<String, MutableList<String>>()

        components.forEach { component ->
            val componentKey = component.className
            adjacencyList.putIfAbsent(componentKey, mutableListOf())

            
            val hasSelfRef = component.dependencies.any { dep ->
                dep.targetType.substringAfterLast('.') == component.className
            }
            if (hasSelfRef) {
                adjacencyList[componentKey]!!.add(componentKey)
            }

            component.dependencies.forEach { dependency ->
                val targetName = dependency.targetType.substringAfterLast('.')
                val targetComponent = components.find { it.className == targetName }
                if (targetComponent != null && targetComponent.className != component.className) {
                    adjacencyList[componentKey]!!.add(targetComponent.className)
                }
            }
        }

        
        return hasCycleInGraph(adjacencyList, components.map { it.className })
    }

    private fun hasCycleInGraph(adjList: Map<String, List<String>>, nodes: List<String>): Boolean {
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()

        fun dfs(node: String): Boolean {
            visited.add(node)
            recursionStack.add(node)

            adjList[node]?.forEach { neighbor ->
                if (!visited.contains(neighbor)) {
                    if (dfs(neighbor)) return true
                } else if (recursionStack.contains(neighbor)) {
                    return true
                }
            }

            recursionStack.remove(node)
            return false
        }

        return nodes.any { node ->
            if (!visited.contains(node)) {
                dfs(node)
            } else false
        }
    }

    private fun calculateCircularDependencyConfidence(components: List<KnitComponent>): Double {
        
        val componentNames = components.map { it.className }.toSet()
        var internalEdgeCount = 0

        components.forEach { component ->
            component.dependencies.forEach { dependency ->
                val targetSimpleName = dependency.targetType.substringAfterLast('.')
                if (targetSimpleName == component.className || componentNames.contains(targetSimpleName)) {
                    internalEdgeCount += 1
                }
            }
        }

        
        val base = if (components.size >= 2) 0.8 else 0.7
        val score = base + (0.1 * internalEdgeCount)
        
        return min(0.98, max(0.85, score))
    }

    private fun checkForCommentedDependency(component: KnitComponent, dependencyType: String): Boolean {
        val sourceFile = File(project.basePath, component.sourceFile)
        if (!sourceFile.exists()) return false

        try {
            val content = sourceFile.readText()
            val dependencyPattern = Regex("//.*@Inject.*$dependencyType", RegexOption.MULTILINE)
            return dependencyPattern.containsMatchIn(content)
        } catch (e: Exception) {
            logger.debug("Could not read source file ${component.sourceFile}", e)
            return false
        }
    }

    private fun isProviderCommented(component: KnitComponent, provider: KnitProvider): Boolean {
        val sourceFile = File(project.basePath, component.sourceFile)
        if (!sourceFile.exists()) return false

        try {
            val content = sourceFile.readText()
            val providerPattern = Regex("//.*${provider.methodName}", RegexOption.MULTILINE)
            return providerPattern.containsMatchIn(content)
        } catch (e: Exception) {
            logger.debug("Could not read source file ${component.sourceFile}", e)
            return false
        }
    }

    /**
     * Configuration settings for issue validation
     */
    data class ValidationSettings(
        val validationEnabled: Boolean = true,
        val validateCircularDependencies: Boolean = true,
        val validateUnresolvedDependencies: Boolean = true,
        val validateAmbiguousProviders: Boolean = true,
        val validateSingletonViolations: Boolean = true,
        val validateQualifierMismatches: Boolean = true,
        val validateMissingAnnotations: Boolean = true,
        val minimumConfidenceThreshold: Double = 0.3
    )
}