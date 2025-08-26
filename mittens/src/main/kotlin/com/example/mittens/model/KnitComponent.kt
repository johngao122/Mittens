package com.example.mittens.model

data class KnitComponent(
    val className: String,
    val packageName: String,
    val type: ComponentType,
    val dependencies: List<KnitDependency>,
    val providers: List<KnitProvider>,
    val sourceFile: String?,
    val issues: List<KnitIssue> = emptyList()
) {
    val fullyQualifiedName: String
        get() = if (packageName.isNotEmpty()) "$packageName.$className" else className
}

enum class ComponentType {
    COMPONENT,       // @Component annotated
    PROVIDER,        // @Provides annotated
    CONSUMER,        // Has 'by di' properties
    COMPOSITE        // Has both providers and consumers
}

data class KnitDependency(
    val propertyName: String,
    val targetType: String,
    val isNamed: Boolean = false,
    val namedQualifier: String? = null,
    val isFactory: Boolean = false,
    val isLoadable: Boolean = false,
    val isSingleton: Boolean = false
)

data class KnitProvider(
    val methodName: String,
    val returnType: String,
    val providesType: String? = null, // For interface injection @Provides(Interface::class)
    val isNamed: Boolean = false,
    val namedQualifier: String? = null,
    val isSingleton: Boolean = false,
    val isIntoSet: Boolean = false,
    val isIntoList: Boolean = false,
    val isIntoMap: Boolean = false
)

data class KnitIssue(
    val type: IssueType,
    val severity: Severity,
    val message: String,
    val componentName: String,
    val sourceLocation: String? = null,
    val suggestedFix: String? = null
)

enum class IssueType {
    CIRCULAR_DEPENDENCY,
    AMBIGUOUS_PROVIDER,
    UNRESOLVED_DEPENDENCY,
    SINGLETON_VIOLATION,
    NAMED_QUALIFIER_MISMATCH,
    MISSING_COMPONENT_ANNOTATION
}

enum class Severity {
    ERROR,
    WARNING,
    INFO
}