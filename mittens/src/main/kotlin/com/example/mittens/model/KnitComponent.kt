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
    COMPONENT,
    PROVIDER,
    CONSUMER,
    COMPOSITE
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
    val providesType: String? = null,
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
    val suggestedFix: String? = null,
    val metadata: Map<String, Any> = emptyMap(),
    val confidenceScore: Double = 1.0,
    val validationStatus: ValidationStatus = ValidationStatus.NOT_VALIDATED
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

enum class ValidationStatus {
    NOT_VALIDATED,
    VALIDATED_TRUE_POSITIVE,
    VALIDATED_FALSE_POSITIVE,
    VALIDATION_FAILED
}