package com.example.mittens.services

import com.example.mittens.model.*
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.Test
import org.junit.Assert.*

class IssueValidatorTest : LightJavaCodeInsightFixtureTestCase() {

    private lateinit var issueValidator: IssueValidator

    override fun setUp() {
        super.setUp()
        issueValidator = IssueValidator(project)
    }

    @Test
    fun testValidateCircularDependency_TruePositive() {
        // Create components with actual circular dependency
        val orderService = KnitComponent(
            className = "OrderService",
            packageName = "com.test",
            type = ComponentType.COMPONENT,
            dependencies = listOf(
                KnitDependency(
                    propertyName = "inventoryService",
                    targetType = "InventoryService",
                    isNamed = false
                )
            ),
            providers = emptyList(),
            sourceFile = "OrderService.kt"
        )

        val inventoryService = KnitComponent(
            className = "InventoryService",
            packageName = "com.test",
            type = ComponentType.COMPONENT,
            dependencies = listOf(
                KnitDependency(
                    propertyName = "orderService",
                    targetType = "OrderService",
                    isNamed = false
                )
            ),
            providers = emptyList(),
            sourceFile = "InventoryService.kt"
        )

        val circularIssue = KnitIssue(
            type = IssueType.CIRCULAR_DEPENDENCY,
            severity = Severity.ERROR,
            message = "Circular dependency: OrderService → InventoryService → OrderService",
            componentName = "OrderService, InventoryService"
        )

        val components = listOf(orderService, inventoryService)
        val validatedIssues = issueValidator.validateIssues(listOf(circularIssue), components)

        assertEquals(1, validatedIssues.size)
        val validatedIssue = validatedIssues[0]
        assertEquals(ValidationStatus.VALIDATED_TRUE_POSITIVE, validatedIssue.validationStatus)
        assertTrue("Confidence should be high for true circular dependency", validatedIssue.confidenceScore > 0.6)
    }

    @Test
    fun testValidateCircularDependency_FalsePositive() {
        // Create components without actual circular dependency
        val orderService = KnitComponent(
            className = "OrderService",
            packageName = "com.test",
            type = ComponentType.COMPONENT,
            dependencies = listOf(
                KnitDependency(
                    propertyName = "inventoryService",
                    targetType = "InventoryService",
                    isNamed = false
                )
            ),
            providers = emptyList(),
            sourceFile = "OrderService.kt"
        )

        val inventoryService = KnitComponent(
            className = "InventoryService",
            packageName = "com.test",
            type = ComponentType.COMPONENT,
            dependencies = emptyList(), // No dependency back to OrderService
            providers = emptyList(),
            sourceFile = "InventoryService.kt"
        )

        val falseCircularIssue = KnitIssue(
            type = IssueType.CIRCULAR_DEPENDENCY,
            severity = Severity.ERROR,
            message = "Circular dependency: OrderService → InventoryService → OrderService",
            componentName = "OrderService, InventoryService"
        )

        val components = listOf(orderService, inventoryService)
        val validatedIssues = issueValidator.validateIssues(listOf(falseCircularIssue), components)

        assertEquals(1, validatedIssues.size)
        val validatedIssue = validatedIssues[0]
        assertEquals(ValidationStatus.VALIDATED_FALSE_POSITIVE, validatedIssue.validationStatus)
        assertTrue("Confidence should be low for false positive", validatedIssue.confidenceScore < 0.5)
    }

    @Test
    fun testValidateUnresolvedDependency_TruePositive() {
        val consumerComponent = KnitComponent(
            className = "PaymentService",
            packageName = "com.test",
            type = ComponentType.COMPONENT,
            dependencies = listOf(
                KnitDependency(
                    propertyName = "paymentGateway",
                    targetType = "PaymentGateway",
                    isNamed = false
                )
            ),
            providers = emptyList(),
            sourceFile = "PaymentService.kt"
        )

        val unresolvedIssue = KnitIssue(
            type = IssueType.UNRESOLVED_DEPENDENCY,
            severity = Severity.ERROR,
            message = "No provider found for PaymentGateway",
            componentName = "PaymentService",
            metadata = mapOf("dependencyType" to "PaymentGateway")
        )

        // No provider component for PaymentGateway
        val components = listOf(consumerComponent)
        val validatedIssues = issueValidator.validateIssues(listOf(unresolvedIssue), components)

        assertEquals(1, validatedIssues.size)
        val validatedIssue = validatedIssues[0]
        assertEquals(ValidationStatus.VALIDATED_TRUE_POSITIVE, validatedIssue.validationStatus)
        assertTrue("Confidence should be high for true unresolved dependency", validatedIssue.confidenceScore > 0.8)
    }

    @Test
    fun testValidateAmbiguousProvider_TruePositive() {
        val provider1 = KnitComponent(
            className = "DatabaseProvider1",
            packageName = "com.test",
            type = ComponentType.PROVIDER,
            dependencies = emptyList(),
            providers = listOf(
                KnitProvider(
                    methodName = "provideDatabase",
                    returnType = "DatabaseService",
                    isNamed = false,
                    isSingleton = false
                )
            ),
            sourceFile = "DatabaseProvider1.kt"
        )

        val provider2 = KnitComponent(
            className = "DatabaseProvider2",
            packageName = "com.test",
            type = ComponentType.PROVIDER,
            dependencies = emptyList(),
            providers = listOf(
                KnitProvider(
                    methodName = "provideDatabase",
                    returnType = "DatabaseService",
                    isNamed = false,
                    isSingleton = false
                )
            ),
            sourceFile = "DatabaseProvider2.kt"
        )

        val ambiguousIssue = KnitIssue(
            type = IssueType.AMBIGUOUS_PROVIDER,
            severity = Severity.ERROR,
            message = "Multiple providers found for DatabaseService",
            componentName = "DatabaseProvider1, DatabaseProvider2",
            metadata = mapOf("conflictingType" to "DatabaseService")
        )

        val components = listOf(provider1, provider2)
        val validatedIssues = issueValidator.validateIssues(listOf(ambiguousIssue), components)

        assertEquals(1, validatedIssues.size)
        val validatedIssue = validatedIssues[0]
        assertEquals(ValidationStatus.VALIDATED_TRUE_POSITIVE, validatedIssue.validationStatus)
        assertTrue("Confidence should be high for true ambiguous provider", validatedIssue.confidenceScore > 0.9)
    }

    @Test
    fun testValidateSingletonViolation_TruePositive() {
        val provider1 = KnitComponent(
            className = "SingletonProvider1",
            packageName = "com.test",
            type = ComponentType.PROVIDER,
            dependencies = emptyList(),
            providers = listOf(
                KnitProvider(
                    methodName = "provideService",
                    returnType = "UserService",
                    isNamed = false,
                    isSingleton = true
                )
            ),
            sourceFile = "SingletonProvider1.kt"
        )

        val provider2 = KnitComponent(
            className = "SingletonProvider2",
            packageName = "com.test",
            type = ComponentType.PROVIDER,
            dependencies = emptyList(),
            providers = listOf(
                KnitProvider(
                    methodName = "provideAnotherService",
                    returnType = "UserService",
                    isNamed = false,
                    isSingleton = true
                )
            ),
            sourceFile = "SingletonProvider2.kt"
        )

        val singletonIssue = KnitIssue(
            type = IssueType.SINGLETON_VIOLATION,
            severity = Severity.ERROR,
            message = "Multiple singleton providers found for UserService",
            componentName = "SingletonProvider1, SingletonProvider2",
            metadata = mapOf(
                "conflictingType" to "UserService",
                "providerCount" to 2
            )
        )

        val components = listOf(provider1, provider2)
        val validatedIssues = issueValidator.validateIssues(listOf(singletonIssue), components)

        assertEquals(1, validatedIssues.size)
        val validatedIssue = validatedIssues[0]
        assertEquals(ValidationStatus.VALIDATED_TRUE_POSITIVE, validatedIssue.validationStatus)
        assertTrue("Confidence should be high for true singleton violation", validatedIssue.confidenceScore > 0.8)
    }

    @Test
    fun testValidateNamedQualifierMismatch_TruePositive() {
        val consumer = KnitComponent(
            className = "OrderService",
            packageName = "com.test",
            type = ComponentType.COMPONENT,
            dependencies = listOf(
                KnitDependency(
                    propertyName = "userRepository",
                    targetType = "UserRepository",
                    isNamed = true,
                    namedQualifier = "database"
                )
            ),
            providers = emptyList(),
            sourceFile = "OrderService.kt"
        )

        val provider = KnitComponent(
            className = "UserRepositoryProvider",
            packageName = "com.test",
            type = ComponentType.PROVIDER,
            dependencies = emptyList(),
            providers = listOf(
                KnitProvider(
                    methodName = "provideUserRepository",
                    returnType = "UserRepository",
                    isNamed = true,
                    namedQualifier = "memory", // Different qualifier
                    isSingleton = false
                )
            ),
            sourceFile = "UserRepositoryProvider.kt"
        )

        val qualifierIssue = KnitIssue(
            type = IssueType.NAMED_QUALIFIER_MISMATCH,
            severity = Severity.ERROR,
            message = "Named qualifier mismatch for UserRepository",
            componentName = "OrderService",
            metadata = mapOf("dependencyType" to "UserRepository")
        )

        val components = listOf(consumer, provider)
        val validatedIssues = issueValidator.validateIssues(listOf(qualifierIssue), components)

        assertEquals(1, validatedIssues.size)
        val validatedIssue = validatedIssues[0]
        assertEquals(ValidationStatus.VALIDATED_TRUE_POSITIVE, validatedIssue.validationStatus)
        assertTrue("Confidence should be high for true qualifier mismatch", validatedIssue.confidenceScore > 0.7)
    }

    @Test
    fun testValidationSettings_Disabled() {
        val issue = KnitIssue(
            type = IssueType.CIRCULAR_DEPENDENCY,
            severity = Severity.ERROR,
            message = "Test issue",
            componentName = "TestComponent"
        )

        val settings = IssueValidator.ValidationSettings(validationEnabled = false)
        val validatedIssues = issueValidator.validateIssues(listOf(issue), emptyList(), settings)

        assertEquals(1, validatedIssues.size)
        assertEquals(ValidationStatus.NOT_VALIDATED, validatedIssues[0].validationStatus)
        assertEquals(1.0, validatedIssues[0].confidenceScore, 0.001)
    }

    @Test
    fun testValidationSettings_ConfidenceThreshold() {
        val lowConfidenceIssue = KnitIssue(
            type = IssueType.MISSING_COMPONENT_ANNOTATION,
            severity = Severity.WARNING,
            message = "Missing component annotation",
            componentName = "TestComponent"
        )

        val emptyComponent = KnitComponent(
            className = "TestComponent",
            packageName = "com.test",
            type = ComponentType.COMPONENT,
            dependencies = emptyList(),
            providers = emptyList(),
            sourceFile = "TestComponent.kt"
        )

        val settings = IssueValidator.ValidationSettings(
            validationEnabled = true,
            minimumConfidenceThreshold = 0.5
        )

        val validatedIssues = issueValidator.validateIssues(
            listOf(lowConfidenceIssue), 
            listOf(emptyComponent), 
            settings
        )

        assertEquals(1, validatedIssues.size)
        val validatedIssue = validatedIssues[0]
        assertTrue("Should be validated as false positive for empty component", 
                  validatedIssue.validationStatus == ValidationStatus.VALIDATED_FALSE_POSITIVE)
        assertTrue("Confidence should be below threshold", validatedIssue.confidenceScore < 0.5)
    }

    @Test
    fun testValidationFailure_HandlingException() {
        // Create malformed issue that will cause validation to fail
        val malformedIssue = KnitIssue(
            type = IssueType.CIRCULAR_DEPENDENCY,
            severity = Severity.ERROR,
            message = "Malformed circular dependency",
            componentName = "" // Empty component name should cause issues
        )

        val validatedIssues = issueValidator.validateIssues(listOf(malformedIssue), emptyList())

        assertEquals(1, validatedIssues.size)
        val validatedIssue = validatedIssues[0]
        assertEquals(ValidationStatus.VALIDATION_FAILED, validatedIssue.validationStatus)
        assertEquals(0.5, validatedIssue.confidenceScore, 0.001) // Default failure confidence
    }
}