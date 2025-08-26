package com.example.mittens.services

import com.example.mittens.model.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import org.objectweb.asm.*
import org.objectweb.asm.tree.*
import java.io.File
import java.io.FileInputStream

@Service
class KnitBytecodeAnalyzer(private val project: Project) {
    
    private val logger = thisLogger()
    
    data class BytecodeAnalysisResult(
        val detectedComponents: List<KnitComponent>,
        val knitTransformations: List<KnitTransformation>,
        val injectionPatterns: List<InjectionPattern>
    )
    
    data class KnitTransformation(
        val className: String,
        val transformationType: String,
        val details: String
    )
    
    data class InjectionPattern(
        val className: String,
        val fieldName: String,
        val targetType: String,
        val isSingleton: Boolean,
        val pattern: String
    )
    
    fun analyzeBytecode(classesDir: File): BytecodeAnalysisResult {
        logger.info("Starting bytecode analysis in: ${classesDir.absolutePath}")
        
        val components = mutableListOf<KnitComponent>()
        val transformations = mutableListOf<KnitTransformation>()
        val patterns = mutableListOf<InjectionPattern>()
        
        if (!classesDir.exists() || !classesDir.isDirectory) {
            logger.warn("Classes directory does not exist: ${classesDir.absolutePath}")
            return BytecodeAnalysisResult(components, transformations, patterns)
        }
        
        classesDir.walkTopDown().filter { it.extension == "class" }.forEach { classFile ->
            try {
                analyzeClassFile(classFile, components, transformations, patterns)
            } catch (e: Exception) {
                logger.warn("Failed to analyze class file: ${classFile.absolutePath}", e)
            }
        }
        
        logger.info("Bytecode analysis complete: ${components.size} components, " +
                   "${transformations.size} transformations, ${patterns.size} patterns")
        
        return BytecodeAnalysisResult(components, transformations, patterns)
    }
    
    private fun analyzeClassFile(
        classFile: File,
        components: MutableList<KnitComponent>,
        transformations: MutableList<KnitTransformation>,
        patterns: MutableList<InjectionPattern>
    ) {
        val classReader = ClassReader(FileInputStream(classFile))
        val classNode = ClassNode()
        classReader.accept(classNode, ClassReader.EXPAND_FRAMES)
        
        val className = classNode.name.replace('/', '.')
        val packageName = if (className.contains('.')) {
            className.substringBeforeLast('.')
        } else ""
        val simpleClassName = className.substringAfterLast('.')
        
        logger.debug("Analyzing bytecode for class: $className")
        
        val dependencies = mutableListOf<KnitDependency>()
        val providers = mutableListOf<KnitProvider>()
        val issues = mutableListOf<KnitIssue>()
        
        // Analyze fields for DI patterns
        for (field in classNode.fields) {
            analyzeField(field, className, dependencies, patterns)
        }
        
        // Analyze methods for DI patterns and providers
        for (method in classNode.methods) {
            analyzeMethod(method, className, providers, patterns, transformations)
        }
        
        // Look for Knit-specific bytecode patterns
        detectKnitTransformations(classNode, transformations)
        
        // Only create component if we found DI evidence
        if (dependencies.isNotEmpty() || providers.isNotEmpty() || 
            hasKnitAnnotations(classNode) || hasKnitTransformations(className, transformations)) {
            
            val componentType = determineComponentType(dependencies, providers, classNode)
            
            components.add(KnitComponent(
                className = simpleClassName,
                packageName = packageName,
                type = componentType,
                dependencies = dependencies,
                providers = providers,
                sourceFile = null, // Bytecode analysis doesn't have source file info
                issues = issues
            ))
        }
    }
    
    private fun analyzeField(
        field: FieldNode,
        className: String,
        dependencies: MutableList<KnitDependency>,
        patterns: MutableList<InjectionPattern>
    ) {
        // Look for Knit-generated lazy fields (typically for singletons)
        val fieldType = Type.getType(field.desc).className
        
        // Check if field looks like a DI field (lazy initialization, specific patterns)
        val isLazyField = field.name.endsWith("\$delegate") || fieldType.contains("Lazy")
        val isDIField = isLazyField || isDependencyInjectionField(field)
        
        if (isDIField) {
            logger.debug("Found potential DI field: ${field.name} in $className")
            
            val propertyName = field.name.removeSuffix("\$delegate")
            val targetType = extractTargetTypeFromSignature(field.signature)
            
            // Enhanced singleton detection from field annotations and patterns
            val fieldAnnotations = field.visibleAnnotations ?: emptyList()
            val isSingleton = isLazyField || hasSingletonAnnotation(fieldAnnotations) || 
                            isSingletonFieldPattern(field.name)
            
            // Extract named qualifiers from field annotations
            val (isNamed, namedQualifier) = extractNamedQualifierFromBytecode(fieldAnnotations)
            
            // Enhanced type detection
            val isFactory = isFactoryTypeFromBytecode(targetType, field.signature)
            val isLoadable = isLoadableTypeFromBytecode(targetType, field.signature)
            
            dependencies.add(KnitDependency(
                propertyName = propertyName,
                targetType = targetType,
                isNamed = isNamed,
                namedQualifier = namedQualifier,
                isFactory = isFactory,
                isLoadable = isLoadable,
                isSingleton = isSingleton
            ))
            
            patterns.add(InjectionPattern(
                className = className,
                fieldName = field.name,
                targetType = targetType,
                isSingleton = isSingleton,
                pattern = if (isLazyField) "Lazy field delegation" else "DI field injection"
            ))
        }
    }
    
    private fun analyzeMethod(
        method: MethodNode,
        className: String,
        providers: MutableList<KnitProvider>,
        patterns: MutableList<InjectionPattern>,
        transformations: MutableList<KnitTransformation>
    ) {
        // Look for provider methods (methods that return instances)
        val returnType = Type.getReturnType(method.desc).className
        
        // Check for @Provides annotation in runtime visible annotations
        val hasProvides = method.visibleAnnotations?.any { annotation ->
            Type.getType(annotation.desc).className.endsWith("Provides")
        } ?: false
        
        if (hasProvides) {
            logger.debug("Found provider method: ${method.name} returning $returnType in $className")
            
            // Extract annotation information
            val annotations = method.visibleAnnotations ?: emptyList()
            val (isNamed, namedQualifier) = extractNamedQualifierFromBytecode(annotations)
            val isSingleton = hasSingletonAnnotation(annotations)
            val (isIntoSet, isIntoList, isIntoMap) = extractCollectionAnnotations(annotations)
            
            providers.add(KnitProvider(
                methodName = method.name,
                returnType = returnType,
                providesType = extractProvidesTypeFromBytecode(annotations),
                isNamed = isNamed,
                namedQualifier = namedQualifier,
                isSingleton = isSingleton,
                isIntoSet = isIntoSet,
                isIntoList = isIntoList,
                isIntoMap = isIntoMap
            ))
        }
        
        // Look for Knit-generated injection code patterns
        analyzeMethodInstructions(method, className, patterns, transformations)
    }
    
    private fun analyzeMethodInstructions(
        method: MethodNode,
        className: String,
        patterns: MutableList<InjectionPattern>,
        transformations: MutableList<KnitTransformation>
    ) {
        // Look for specific bytecode patterns that indicate Knit transformations
        val instructions = method.instructions
        var i = 0
        
        while (i < instructions.size()) {
            val instruction = instructions[i]
            
            // Look for IFNULL pattern (typical for singleton checking)
            if (instruction.opcode == Opcodes.IFNULL) {
                val jumpInstr = instruction as JumpInsnNode
                logger.debug("Found IFNULL pattern in ${className}.${method.name} - potential singleton check")
                
                // Enhanced singleton pattern detection
                val isSingletonPattern = isEnhancedSingletonPattern(instructions, i)
                
                transformations.add(KnitTransformation(
                    className = className,
                    transformationType = if (isSingletonPattern) "Singleton Lazy Initialization" else "Conditional Check",
                    details = "IFNULL instruction at position $i in method ${method.name}" + 
                             if (isSingletonPattern) " (confirmed singleton pattern)" else ""
                ))
            }
            
            // Look for direct field access patterns (GETFIELD/PUTFIELD)
            if (instruction.opcode == Opcodes.GETFIELD || instruction.opcode == Opcodes.PUTFIELD) {
                val fieldInstr = instruction as FieldInsnNode
                if (fieldInstr.name.contains("$") || fieldInstr.name.endsWith("delegate")) {
                    logger.debug("Found DI field access pattern: ${fieldInstr.name} in $className")
                    
                    patterns.add(InjectionPattern(
                        className = className,
                        fieldName = fieldInstr.name,
                        targetType = Type.getType(fieldInstr.desc).className,
                        isSingleton = method.name.contains("get") && instruction.previous?.opcode == Opcodes.IFNULL,
                        pattern = "Direct field access"
                    ))
                }
            }
            
            i++
        }
    }
    
    private fun detectKnitTransformations(
        classNode: ClassNode,
        transformations: MutableList<KnitTransformation>
    ) {
        // Look for Knit-specific class metadata or signatures
        val className = classNode.name.replace('/', '.')
        
        // Check for generated methods with specific patterns
        val generatedMethods = classNode.methods.filter { method ->
            method.name.startsWith("get") && 
            method.desc.startsWith("()") &&
            method.access and Opcodes.ACC_SYNTHETIC != 0
        }
        
        if (generatedMethods.isNotEmpty()) {
            transformations.add(KnitTransformation(
                className = className,
                transformationType = "Generated Getters",
                details = "Found ${generatedMethods.size} synthetic getter methods"
            ))
        }
    }
    
    private fun hasKnitAnnotations(classNode: ClassNode): Boolean {
        // Check for Knit annotations in runtime visible annotations
        return classNode.visibleAnnotations?.any { annotation ->
            val annotationType = Type.getType(annotation.desc).className
            annotationType.endsWith("Component") || 
            annotationType.endsWith("Provides") ||
            annotationType.endsWith("Singleton") ||
            annotationType.endsWith("Named") ||
            annotationType.endsWith("KnitViewModel")
        } ?: false
    }
    
    private fun hasKnitTransformations(className: String, transformations: List<KnitTransformation>): Boolean {
        return transformations.any { it.className == className }
    }
    
    private fun determineComponentType(
        dependencies: List<KnitDependency>,
        providers: List<KnitProvider>,
        classNode: ClassNode
    ): ComponentType {
        val hasComponent = hasKnitAnnotations(classNode)
        
        return when {
            hasComponent && (dependencies.isNotEmpty() || providers.isNotEmpty()) -> ComponentType.COMPOSITE
            hasComponent -> ComponentType.COMPONENT
            providers.isNotEmpty() -> ComponentType.PROVIDER
            dependencies.isNotEmpty() -> ComponentType.CONSUMER
            else -> ComponentType.PROVIDER // Default for bytecode-detected classes
        }
    }
    
    private fun extractTargetTypeFromSignature(signature: String?): String {
        // Extract generic type information from field signatures
        if (signature == null) return "Unknown"
        
        // Parse generic signatures like "Lkotlin/Lazy<Lcom/example/SomeClass;>;"
        val genericMatch = Regex("L([^<]+)<L([^;]+);>;").find(signature)
        return if (genericMatch != null) {
            genericMatch.groupValues[2].replace('/', '.')
        } else {
            "Unknown"
        }
    }
    
    /**
     * Extract named qualifier from bytecode annotations
     */
    private fun extractNamedQualifierFromBytecode(annotations: List<AnnotationNode>): Pair<Boolean, String?> {
        val namedAnnotation = annotations.find { 
            Type.getType(it.desc).className.endsWith("Named")
        } ?: return false to null
        
        // Extract value from annotation
        val values = namedAnnotation.values
        if (values != null && values.size >= 2) {
            for (i in 0 until values.size step 2) {
                val key = values[i] as String
                val value = values[i + 1]
                
                if (key == "value" || key == "qualifier") {
                    return true to value.toString()
                }
            }
        }
        
        return true to null // @Named without parameters
    }
    
    /**
     * Check if annotation list contains @Singleton
     */
    private fun hasSingletonAnnotation(annotations: List<AnnotationNode>): Boolean {
        return annotations.any { Type.getType(it.desc).className.endsWith("Singleton") }
    }
    
    /**
     * Extract collection annotations (@IntoSet, @IntoList, @IntoMap)
     */
    private fun extractCollectionAnnotations(annotations: List<AnnotationNode>): Triple<Boolean, Boolean, Boolean> {
        val isIntoSet = annotations.any { Type.getType(it.desc).className.endsWith("IntoSet") }
        val isIntoList = annotations.any { Type.getType(it.desc).className.endsWith("IntoList") }
        val isIntoMap = annotations.any { Type.getType(it.desc).className.endsWith("IntoMap") }
        return Triple(isIntoSet, isIntoList, isIntoMap)
    }
    
    /**
     * Extract provides type from @Provides annotation in bytecode
     */
    private fun extractProvidesTypeFromBytecode(annotations: List<AnnotationNode>): String? {
        val providesAnnotation = annotations.find { 
            Type.getType(it.desc).className.endsWith("Provides")
        } ?: return null
        
        val values = providesAnnotation.values
        if (values != null && values.size >= 2) {
            for (i in 0 until values.size step 2) {
                val key = values[i] as String
                val value = values[i + 1]
                
                if (key == "value") {
                    return (value as? Type)?.className
                }
            }
        }
        
        return null
    }
    
    /**
     * Check if field is a dependency injection field based on naming patterns
     */
    private fun isDependencyInjectionField(field: FieldNode): Boolean {
        return field.name.contains("\$di") || 
               field.name.contains("_injected") ||
               field.name.endsWith("\$delegate")
    }
    
    /**
     * Check if field name indicates singleton pattern
     */
    private fun isSingletonFieldPattern(fieldName: String): Boolean {
        return fieldName.contains("singleton") || 
               fieldName.contains("\$instance") ||
               fieldName.contains("_single")
    }
    
    /**
     * Enhanced singleton pattern detection by analyzing instruction sequence
     */
    private fun isEnhancedSingletonPattern(instructions: InsnList, ifnullIndex: Int): Boolean {
        // Look for typical singleton pattern: IFNULL -> initialization -> PUTFIELD/PUTSTATIC
        if (ifnullIndex + 3 >= instructions.size()) return false
        
        val nextInstr = instructions[ifnullIndex + 1]
        val afterNext = instructions[ifnullIndex + 2]
        
        // Look for NEW instruction after IFNULL (creating instance)
        return nextInstr.opcode == Opcodes.NEW || 
               afterNext.opcode == Opcodes.PUTFIELD ||
               afterNext.opcode == Opcodes.PUTSTATIC
    }
    
    /**
     * Enhanced factory type detection from bytecode
     */
    private fun isFactoryTypeFromBytecode(targetType: String, signature: String?): Boolean {
        return targetType.contains("Factory") || 
               signature?.contains("Factory") == true ||
               targetType.contains("Function")
    }
    
    /**
     * Enhanced loadable type detection from bytecode
     */
    private fun isLoadableTypeFromBytecode(targetType: String, signature: String?): Boolean {
        return targetType.contains("Loadable") || 
               signature?.contains("Loadable") == true
    }
}