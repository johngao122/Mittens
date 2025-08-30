package com.example.mittens.services

import com.example.mittens.model.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.io.File
import java.io.IOException

@Service
class KnitJsonParser(private val project: Project) {

    private val logger = thisLogger()
    
    private val objectMapper = ObjectMapper().apply {
        registerModule(KotlinModule.Builder().build())
    }

    /**
     * Parse a knit.json file and convert it to KnitComponent objects
     * @param knitJsonFile The knit.json file to parse
     * @return Result containing parsed components or error information
     */
    fun parseKnitJson(knitJsonFile: File): KnitJsonParseResult {
        if (!knitJsonFile.exists()) {
            logger.warn("Knit JSON file does not exist: ${knitJsonFile.absolutePath}")
            return KnitJsonParseResult(
                success = false,
                errorMessage = "Knit JSON file not found: ${knitJsonFile.absolutePath}"
            )
        }

        if (!knitJsonFile.canRead()) {
            logger.warn("Cannot read knit JSON file: ${knitJsonFile.absolutePath}")
            return KnitJsonParseResult(
                success = false,
                errorMessage = "Cannot read knit JSON file: ${knitJsonFile.absolutePath}"
            )
        }

        try {
            logger.info("Parsing knit.json file: ${knitJsonFile.absolutePath}")
            
            val knitJsonRoot: KnitJsonRoot = objectMapper.readValue(knitJsonFile, KnitJsonTypeReference())
            
            logger.info("Successfully parsed knit.json with ${knitJsonRoot.size} components")
            
            return KnitJsonParseResult(
                success = true,
                components = knitJsonRoot,
                sourceFile = knitJsonFile.absolutePath
            )
            
        } catch (e: IOException) {
            logger.warn("Failed to parse knit.json file: ${knitJsonFile.absolutePath}", e)
            return KnitJsonParseResult(
                success = false,
                errorMessage = "JSON parsing error: ${e.message}"
            )
        } catch (e: Exception) {
            logger.warn("Unexpected error parsing knit.json file: ${knitJsonFile.absolutePath}", e)
            return KnitJsonParseResult(
                success = false,
                errorMessage = "Unexpected error: ${e.message}"
            )
        }
    }

    /**
     * Convert parsed knit.json data to KnitComponent objects
     * @param knitJsonRoot The parsed knit.json root object
     * @return List of KnitComponent objects
     */
    fun convertToKnitComponents(knitJsonRoot: KnitJsonRoot): List<KnitComponent> {
        val components = mutableListOf<KnitComponent>()
        
        logger.info("Converting ${knitJsonRoot.size} knit.json entries to KnitComponent objects")
        
        for ((componentPath, knitJsonComponent) in knitJsonRoot) {
            try {
                val component = convertSingleComponent(componentPath, knitJsonComponent)
                if (component != null) {
                    components.add(component)
                }
            } catch (e: Exception) {
                logger.warn("Failed to convert component: $componentPath", e)
            }
        }
        
        logger.info("Successfully converted ${components.size} components from knit.json")
        return components
    }

    /**
     * Convert a single knit.json component entry to a KnitComponent
     */
    private fun convertSingleComponent(componentPath: String, knitJsonComponent: KnitJsonComponent): KnitComponent? {
        // Skip Knit framework internal components
        if (componentPath.startsWith("knit/") && !componentPath.contains("demo")) {
            logger.debug("Skipping Knit framework internal component: $componentPath")
            return null
        }

        val packageName = knitJsonComponent.getPackageName(componentPath)
        val className = knitJsonComponent.getSimpleClassName(componentPath)
        
        // Convert injections to dependencies
        val dependencies = knitJsonComponent.injections.map { (propertyName, injection) ->
            convertToKnitDependency(propertyName, injection)
        }
        
        // Convert providers
        val providers = knitJsonComponent.providers.map { provider ->
            convertToKnitProvider(provider)
        }
        
        // Determine component type
        val componentType = determineComponentType(dependencies, providers)
        
        logger.debug("Converted component: $packageName.$className (${dependencies.size} deps, ${providers.size} providers)")
        
        return KnitComponent(
            className = className,
            packageName = packageName,
            type = componentType,
            dependencies = dependencies,
            providers = providers,
            sourceFile = null // Will be populated by source analysis if needed
        )
    }

    /**
     * Convert a knit.json injection to a KnitDependency
     */
    private fun convertToKnitDependency(propertyName: String, injection: KnitInjection): KnitDependency {
        val targetType = injection.getTargetType()
        val isSingleton = injection.isSingleton()
        
        // Check for named qualifiers (basic heuristic)
        val isNamed = propertyName.contains("Named") || propertyName.contains("Qualified")
        val namedQualifier = if (isNamed) extractNamedQualifier(propertyName) else null
        
        // Check for factory patterns
        val isFactory = targetType.contains("Factory<") || targetType.contains("Provider<")
        
        // Check for loadable patterns
        val isLoadable = targetType.contains("Loadable<") || targetType.contains("Lazy<")
        
        return KnitDependency(
            propertyName = propertyName,
            targetType = simplifyTargetType(targetType),
            isNamed = isNamed,
            namedQualifier = namedQualifier,
            isFactory = isFactory,
            isLoadable = isLoadable,
            isSingleton = isSingleton
        )
    }

    /**
     * Convert a knit.json provider to a KnitProvider
     */
    private fun convertToKnitProvider(provider: KnitJsonProvider): com.example.mittens.model.KnitProvider {
        val methodName = provider.getMethodName()
        val returnType = provider.getReturnType()
        val providesType = simplifyTargetType(returnType)
        
        // Check for collection providers
        val isIntoSet = methodName.contains("IntoSet") || returnType.contains("Set<")
        val isIntoList = methodName.contains("IntoList") || returnType.contains("List<")
        val isIntoMap = methodName.contains("IntoMap") || returnType.contains("Map<")
        
        // Check for named providers
        val isNamed = methodName.contains("Named") || methodName.contains("Qualified")
        val namedQualifier = if (isNamed) extractNamedQualifier(methodName) else null
        
        return com.example.mittens.model.KnitProvider(
            methodName = methodName,
            returnType = returnType,
            providesType = providesType,
            isNamed = isNamed,
            namedQualifier = namedQualifier,
            isSingleton = true, // Knit providers are typically singletons
            isIntoSet = isIntoSet,
            isIntoList = isIntoList,
            isIntoMap = isIntoMap
        )
    }

    /**
     * Simplify complex type names for better matching
     */
    private fun simplifyTargetType(targetType: String): String {
        // Remove generic type parameters for basic matching
        val simplified = targetType.substringBefore('<')
        
        // Convert from path format to class name format
        return simplified.replace('/', '.')
    }

    /**
     * Extract named qualifier from property/method names (basic heuristic)
     */
    private fun extractNamedQualifier(name: String): String? {
        // Look for common naming patterns
        val patterns = listOf(
            Regex("""get(\w+)Named"""),
            Regex("""provide(\w+)Named"""),
            Regex("""(\w+)Qualified""")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(name)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1].lowercase()
            }
        }
        
        return null
    }

    /**
     * Determine component type based on dependencies and providers
     */
    private fun determineComponentType(
        dependencies: List<KnitDependency>, 
        providers: List<KnitProvider>
    ): ComponentType {
        return when {
            providers.isNotEmpty() && dependencies.isNotEmpty() -> ComponentType.COMPOSITE
            providers.isNotEmpty() -> ComponentType.PROVIDER
            dependencies.isNotEmpty() -> ComponentType.CONSUMER
            else -> ComponentType.COMPONENT
        }
    }

    /**
     * Parse knit.json from a file path string
     */
    fun parseKnitJsonFromPath(filePath: String): KnitJsonParseResult {
        return parseKnitJson(File(filePath))
    }

    /**
     * Check if a knit.json file is valid and readable
     */
    fun validateKnitJsonFile(knitJsonFile: File): Boolean {
        if (!knitJsonFile.exists() || !knitJsonFile.canRead()) {
            return false
        }
        
        return try {
            objectMapper.readValue(knitJsonFile, KnitJsonTypeReference())
            true
        } catch (e: Exception) {
            logger.debug("Knit JSON validation failed for: ${knitJsonFile.absolutePath}", e)
            false
        }
    }
}