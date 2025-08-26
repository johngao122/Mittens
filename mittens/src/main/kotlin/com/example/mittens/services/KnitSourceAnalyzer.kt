package com.example.mittens.services

import com.example.mittens.model.*
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

@Service
class KnitSourceAnalyzer(private val project: Project) {
    
    private val logger = thisLogger()
    
    fun analyzeProject(): List<KnitComponent> {
        return runReadAction {
            logger.info("Starting source analysis for Knit components")
            
            val components = mutableListOf<KnitComponent>()
            val kotlinFiles = FileTypeIndex.getFiles(
                KotlinFileType.INSTANCE, 
                GlobalSearchScope.projectScope(project)
            )
            
            val psiManager = PsiManager.getInstance(project)
            
            for (file in kotlinFiles) {
                val psiFile = psiManager.findFile(file) as? KtFile ?: continue
                val classes = psiFile.collectDescendantsOfType<KtClass>()
                
                for (ktClass in classes) {
                    val component = analyzeClass(ktClass, psiFile, file.path)
                    if (component != null) {
                        components.add(component)
                    }
                }
            }
            
            logger.info("Found ${components.size} Knit components")
            components
        }
    }
    
    private fun analyzeClass(ktClass: KtClass, psiFile: KtFile, filePath: String): KnitComponent? {
        val className = ktClass.name ?: return null
        val packageName = psiFile.packageFqName.asString()
        
        val dependencies = mutableListOf<KnitDependency>()
        val providers = mutableListOf<KnitProvider>()
        val issues = mutableListOf<KnitIssue>()
        
        // Analyze properties for 'by di' dependencies
        val properties = ktClass.collectDescendantsOfType<KtProperty>()
        for (property in properties) {
            val dependency = analyzeProperty(property, className)
            if (dependency != null) {
                dependencies.add(dependency)
            }
        }
        
        // Analyze methods for @Provides
        val methods = ktClass.collectDescendantsOfType<KtNamedFunction>()
        for (method in methods) {
            val provider = analyzeMethod(method, className)
            if (provider != null) {
                providers.add(provider)
            }
        }
        
        // Check class-level annotations
        val classAnnotations = ktClass.annotationEntries
        val hasComponent = classAnnotations.any { it.shortName?.asString() == "Component" }
        val hasProvides = classAnnotations.any { it.shortName?.asString() == "Provides" }
        
        // Determine component type
        val componentType = when {
            hasComponent && (dependencies.isNotEmpty() || providers.isNotEmpty()) -> ComponentType.COMPOSITE
            hasComponent -> ComponentType.COMPONENT
            hasProvides || providers.isNotEmpty() -> ComponentType.PROVIDER
            dependencies.isNotEmpty() -> ComponentType.CONSUMER
            else -> return null // Not a Knit component
        }
        
        // Check for missing @Component annotation when using 'by di'
        if (dependencies.isNotEmpty() && !hasComponent) {
            issues.add(KnitIssue(
                type = IssueType.MISSING_COMPONENT_ANNOTATION,
                severity = Severity.WARNING,
                message = "Class has 'by di' properties but missing @Component annotation",
                componentName = "$packageName.$className",
                sourceLocation = filePath,
                suggestedFix = "Add @Component annotation to class $className"
            ))
        }
        
        return KnitComponent(
            className = className,
            packageName = packageName,
            type = componentType,
            dependencies = dependencies,
            providers = providers,
            sourceFile = filePath,
            issues = issues
        )
    }
    
    private fun analyzeProperty(property: KtProperty, containingClassName: String): KnitDependency? {
        val delegate = property.delegate ?: return null
        val delegateText = delegate.text
        
        // Check if it's a 'by di' property
        if (!delegateText.contains("di")) return null
        
        val propertyName = property.name ?: return null
        val typeReference = property.typeReference
        val targetType = typeReference?.text ?: "Unknown"
        
        logger.debug("Found 'by di' property: $propertyName: $targetType in $containingClassName")
        
        // TODO: Parse named qualifiers, factory types, etc. in Phase 3
        return KnitDependency(
            propertyName = propertyName,
            targetType = targetType,
            isNamed = false, // TODO: Implement in Phase 3
            namedQualifier = null,
            isFactory = targetType.startsWith("Factory<") || targetType.contains("() ->"),
            isLoadable = targetType.startsWith("Loadable<"),
            isSingleton = false // TODO: Detect in Phase 3
        )
    }
    
    private fun analyzeMethod(method: KtNamedFunction, containingClassName: String): KnitProvider? {
        val annotations = method.annotationEntries
        val providesAnnotation = annotations.find { it.shortName?.asString() == "Provides" }
            ?: return null
        
        val methodName = method.name ?: return null
        val returnType = method.typeReference?.text ?: "Unit"
        
        // Check if @Provides has explicit type parameter: @Provides(Interface::class)
        val providesType = extractProvidesType(providesAnnotation)
        
        logger.debug("Found @Provides method: $methodName(): $returnType in $containingClassName")
        
        // Check for collection annotations
        val hasIntoSet = annotations.any { it.shortName?.asString() == "IntoSet" }
        val hasIntoList = annotations.any { it.shortName?.asString() == "IntoList" }
        val hasIntoMap = annotations.any { it.shortName?.asString() == "IntoMap" }
        
        return KnitProvider(
            methodName = methodName,
            returnType = returnType,
            providesType = providesType,
            isNamed = false, // TODO: Implement in Phase 3
            namedQualifier = null,
            isSingleton = false, // TODO: Detect in Phase 3  
            isIntoSet = hasIntoSet,
            isIntoList = hasIntoList,
            isIntoMap = hasIntoMap
        )
    }
    
    private fun extractProvidesType(providesAnnotation: KtAnnotationEntry): String? {
        // Extract type from @Provides(SomeInterface::class)
        val valueArguments = providesAnnotation.valueArguments
        if (valueArguments.isNotEmpty()) {
            val firstArg = valueArguments.first()
            val expression = firstArg.getArgumentExpression()?.text
            if (expression?.endsWith("::class") == true) {
                return expression.removeSuffix("::class")
            }
        }
        return null
    }
}