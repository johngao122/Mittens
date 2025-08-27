package com.example.mittens.services

import com.example.mittens.model.*
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.util.TextRange
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
        
        // Handle class-level @Provides annotations
        // Only process class-level @Provides if there are no method-level providers
        val classLevelProvidesAnnotation = classAnnotations.find { it.shortName?.asString() == "Provides" }
        if (classLevelProvidesAnnotation != null && !isAnnotationCommentedOut(classLevelProvidesAnnotation) && providers.isEmpty()) {
            val providesType = extractProvidesType(classLevelProvidesAnnotation)
            if (providesType != null) {
                logger.info("Found class-level @Provides annotation on $className providing $providesType")
                providers.add(
                    KnitProvider(
                        methodName = "provide$className",
                        returnType = providesType,
                        providesType = providesType,
                        isNamed = false,
                        namedQualifier = null,
                        isSingleton = false,
                        isIntoSet = false,
                        isIntoList = false,
                        isIntoMap = false
                    )
                )
            } else {
                // Fallback: class-level @Provides without explicit type means the class provides itself
                logger.info("Found class-level @Provides annotation on $className (fallback to self-providing)")
                providers.add(
                    KnitProvider(
                        methodName = "provide$className",
                        returnType = className,
                        providesType = className,
                        isNamed = false,
                        namedQualifier = null,
                        isSingleton = false,
                        isIntoSet = false,
                        isIntoList = false,
                        isIntoMap = false
                    )
                )
            }
        }

        // Check other class-level annotations for component type determination
        val hasComponent = classAnnotations.any { it.shortName?.asString() == "Component" }
        val hasProvides = classAnnotations.any { it.shortName?.asString() == "Provides" }
        val hasKnitViewModel = classAnnotations.any { it.shortName?.asString() == "KnitViewModel" }

        // Determine component type
        val componentType = when {
            hasKnitViewModel -> ComponentType.COMPONENT // KnitViewModel is a special type of component
            hasComponent && (dependencies.isNotEmpty() || providers.isNotEmpty()) -> ComponentType.COMPOSITE
            hasComponent -> ComponentType.COMPONENT
            hasProvides || providers.isNotEmpty() -> ComponentType.PROVIDER
            dependencies.isNotEmpty() -> ComponentType.CONSUMER
            else -> return null // Not a Knit component
        }

        // Check for missing @Component annotation when using 'by di' (do not warn if @Provides is present)
        if (dependencies.isNotEmpty() && !hasComponent && !hasProvides) {
            issues.add(
                KnitIssue(
                    type = IssueType.MISSING_COMPONENT_ANNOTATION,
                    severity = Severity.WARNING,
                    message = "Class has 'by di' properties but missing @Component annotation",
                    componentName = "$packageName.$className",
                    sourceLocation = filePath,
                    suggestedFix = "Add @Component annotation to class $className"
                )
            )
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

        // Check if the property is commented out - skip if it is
        if (isCommentedOut(property)) {
            logger.debug("Skipping commented 'by di' property: ${property.name} in $containingClassName")
            return null
        }

        val propertyName = property.name ?: return null
        val typeReference = property.typeReference
        val targetType = typeReference?.text ?: "Unknown"

        logger.debug("Found 'by di' property: $propertyName: $targetType in $containingClassName")

        // Parse annotations on the property
        val annotations = property.annotationEntries
        val namedAnnotation = annotations.find { it.shortName?.asString() == "Named" }
        val singletonAnnotation = annotations.find { it.shortName?.asString() == "Singleton" }

        // Extract named qualifier (string-based or class-based)
        val (isNamed, namedQualifier) = extractNamedQualifier(namedAnnotation)

        // Enhanced factory and function type detection
        val isFactory = isFactoryType(targetType)
        val isLoadable = isLoadableType(targetType)

        // Singleton detection from annotations or delegate patterns
        val isSingleton = singletonAnnotation != null || isSingletonDelegate(delegateText)

        return KnitDependency(
            propertyName = propertyName,
            targetType = cleanTargetType(targetType),
            isNamed = isNamed,
            namedQualifier = namedQualifier,
            isFactory = isFactory,
            isLoadable = isLoadable,
            isSingleton = isSingleton
        )
    }

    private fun analyzeMethod(method: KtNamedFunction, containingClassName: String): KnitProvider? {
        val annotations = method.annotationEntries
        val providesAnnotation = annotations.find { it.shortName?.asString() == "Provides" }
            ?: return null


        if (isAnnotationCommentedOut(providesAnnotation)) {
            logger.debug("Skipping commented @Provides annotation on method: ${method.name} in $containingClassName")
            return null
        }

        val methodName = method.name ?: return null
        val returnType = method.typeReference?.text ?: "Unit"


        val providesType = extractProvidesType(providesAnnotation)

        logger.info("Found @Provides method: $methodName(): $returnType in $containingClassName")


        val namedAnnotation = annotations.find { it.shortName?.asString() == "Named" }
        val (isNamed, namedQualifier) = extractNamedQualifier(namedAnnotation)


        val singletonAnnotation = annotations.find { it.shortName?.asString() == "Singleton" }
        val isSingleton = singletonAnnotation != null


        val hasIntoSet = annotations.any { it.shortName?.asString() == "IntoSet" }
        val hasIntoList = annotations.any { it.shortName?.asString() == "IntoList" }
        val hasIntoMap = annotations.any { it.shortName?.asString() == "IntoMap" }

        return KnitProvider(
            methodName = methodName,
            returnType = returnType,
            providesType = providesType,
            isNamed = isNamed,
            namedQualifier = namedQualifier,
            isSingleton = isSingleton,
            isIntoSet = hasIntoSet,
            isIntoList = hasIntoList,
            isIntoMap = hasIntoMap
        )
    }

    private fun extractProvidesType(providesAnnotation: KtAnnotationEntry): String? {

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

    /**
     * Extract named qualifier from @Named annotation
     * Supports both string-based @Named("qualifier") and class-based @Named(qualifier = SomeClass::class)
     */
    private fun extractNamedQualifier(namedAnnotation: KtAnnotationEntry?): Pair<Boolean, String?> {
        if (namedAnnotation == null) return false to null

        val valueArguments = namedAnnotation.valueArguments
        if (valueArguments.isEmpty()) return true to null // @Named without parameters

        val firstArg = valueArguments.first()
        val expression = firstArg.getArgumentExpression()?.text

        return when {
            expression == null -> true to null
            expression.startsWith("\"") && expression.endsWith("\"") -> {
                // String-based: @Named("qualifier")
                true to expression.removeSurrounding("\"")
            }

            expression.endsWith("::class") -> {
                // Class-based: @Named(qualifier = SomeClass::class)
                true to expression.removeSuffix("::class")
            }

            else -> {
                // Handle named parameter: @Named(qualifier = "value")
                val namedParam = valueArguments.find { it.getArgumentName()?.asName?.asString() == "qualifier" }
                val paramExpression = namedParam?.getArgumentExpression()?.text
                when {
                    paramExpression?.startsWith("\"") == true && paramExpression.endsWith("\"") -> {
                        true to paramExpression.removeSurrounding("\"")
                    }

                    paramExpression?.endsWith("::class") == true -> {
                        true to paramExpression.removeSuffix("::class")
                    }

                    else -> true to expression
                }
            }
        }
    }

    /**
     * Enhanced factory type detection
     */
    private fun isFactoryType(targetType: String): Boolean {
        return targetType.startsWith("Factory<") ||
                targetType.contains("() ->") ||
                targetType.matches(Regex(".*\\(\\)\\s*->\\s*.*"))
    }

    /**
     * Enhanced loadable type detection
     */
    private fun isLoadableType(targetType: String): Boolean {
        return targetType.startsWith("Loadable<")
    }

    /**
     * Detect singleton patterns in delegate expressions
     */
    private fun isSingletonDelegate(delegateText: String): Boolean {

        return delegateText.contains("singleton") || delegateText.contains("single")
    }

    /**
     * Clean target type by removing generic noise for better matching
     */
    private fun cleanTargetType(targetType: String): String {

        return targetType.replace("?", "").trim()
    }

    /**
     * Check if a property declaration is commented out
     */
    private fun isCommentedOut(property: KtProperty): Boolean {

        if (isInCommentBlock(property)) return true


        if (isLineCommented(property)) return true

        return false
    }

    /**
     * Check if a method/annotation is commented out
     */
    private fun isAnnotationCommentedOut(annotation: KtAnnotationEntry): Boolean {

        if (isInCommentBlock(annotation)) return true


        if (isLineCommented(annotation)) return true

        return false
    }

    /**
     * Check if an element is within a multi-line comment block (/* ... */)
     */
    private fun isInCommentBlock(element: PsiElement): Boolean {

        val containingFile = element.containingFile
        val comments = PsiTreeUtil.findChildrenOfType(containingFile, PsiComment::class.java)

        for (comment in comments) {
            if (comment.text.startsWith("/*") && comment.text.endsWith("*/")) {
                val commentRange = comment.textRange
                val elementRange = element.textRange


                if (commentRange.contains(elementRange)) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Check if an element's line is commented with //
     */
    private fun isLineCommented(element: PsiElement): Boolean {
        val containingFile = element.containingFile
        val document = com.intellij.psi.PsiDocumentManager.getInstance(project).getDocument(containingFile)
            ?: return false

        val elementOffset = element.textRange.startOffset
        val lineNumber = document.getLineNumber(elementOffset)
        val lineStartOffset = document.getLineStartOffset(lineNumber)
        val lineEndOffset = document.getLineEndOffset(lineNumber)
        val lineText = document.getText(TextRange(lineStartOffset, lineEndOffset))

        val commentIndex = lineText.indexOf("//")

        if (commentIndex == -1) {

            return false
        }


        val elementPositionInLine = elementOffset - lineStartOffset


        return elementPositionInLine > commentIndex
    }
}