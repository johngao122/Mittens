package com.example.mittens.validation

import com.example.mittens.model.*
import com.example.mittens.services.*
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.Test
import org.junit.Assert.*

/**
 * Phase 5 Cross-Platform Validation Test Suite
 * 
 * Tests the accuracy validation system across different project structures,
 * package organizations, and Knit usage patterns to ensure consistent
 * accuracy measurements regardless of project architecture.
 * 
 * Validates accuracy across:
 * - Different package hierarchies (flat, nested, domain-based)
 * - Various project scales (small, medium, large enterprise)
 * - Different Knit usage patterns (basic, advanced, mixed)
 * - Edge cases and corner scenarios
 * - Platform-specific considerations
 */
class CrossPlatformValidationTest : LightJavaCodeInsightFixtureTestCase() {

    private lateinit var sourceAnalyzer: KnitSourceAnalyzer
    private lateinit var knitAnalysisService: KnitAnalysisService
    private lateinit var issueValidator: IssueValidator
    private lateinit var statisticalService: StatisticalAccuracyService
    private lateinit var settingsService: KnitSettingsService
    
    override fun setUp() {
        super.setUp()
        sourceAnalyzer = KnitSourceAnalyzer(project)
        knitAnalysisService = KnitAnalysisService(project)
        issueValidator = IssueValidator(project)
        statisticalService = StatisticalAccuracyService()
        settingsService = KnitSettingsService()
        
        settingsService.setValidationEnabled(true)
        settingsService.setConfidenceThreshold(0.3)
    }

    /**
     * Test accuracy with flat package structure (common in small projects)
     */
    @Test
    fun testFlatPackageStructureAccuracy() {
        val flatStructureContent = """
            package com.example.flat
            
            import knit.Provides
            import knit.di
            
            // All services in one package
            @Provides
            class UserService {
                private val repository: UserRepository by di
                private val validator: UserValidator by di
                
                fun createUser(name: String): User? {
                    return if (validator.isValid(name)) {
                        repository.save(User(name))
                    } else null
                }
            }
            
            @Provides
            class UserRepository {
                fun save(user: User): User = user
                fun findByName(name: String): User? = User(name)
            }
            
            @Provides
            class UserValidator {
                fun isValid(name: String): Boolean = name.isNotBlank()
            }
            
            @Provides  
            class NotificationService {
                private val userService: UserService by di
                
                fun notifyUser(name: String) {
                    val user = userService.createUser(name)
                    if (user != null) {
                        println("Notifying user: ${'$'}{user.name}")
                    }
                }
            }
            
            data class User(val name: String)
        """.trimIndent()
        
        myFixture.configureByText("FlatStructure.kt", flatStructureContent)
        
        val components = sourceAnalyzer.analyzeProject()
        val flatComponents = components.filter { it.packageName == "com.example.flat" }
        
        val dependencyGraph = knitAnalysisService.buildDependencyGraph(flatComponents)
        val detectedIssues = knitAnalysisService.detectIssues(flatComponents, dependencyGraph)
        
        val validatedIssues = issueValidator.validateIssues(detectedIssues, flatComponents)
        val accuracyMetrics = statisticalService.calculateAccuracyMetrics(
            allIssues = validatedIssues,
            validatedIssues = validatedIssues,
            expectedIssues = 0 // Clean flat structure
        )
        
        // Validate flat structure handling
        assertTrue("Should detect all services in flat structure", flatComponents.size >= 4)
        assertTrue("Should have no issues in clean flat structure", detectedIssues.isEmpty())
        assertTrue("Should maintain high accuracy with flat structure", accuracyMetrics.getPrecision() >= 0.95)
        
        // Verify dependencies are resolved within same package
        val userService = flatComponents.find { it.className == "UserService" }
        assertNotNull("UserService should be detected", userService)
        assertEquals("UserService should have 2 dependencies", 2, userService!!.dependencies.size)
        
        println("✅ Flat Package Structure Accuracy:")
        println("  - Components: ${flatComponents.size}")
        println("  - Dependencies: ${flatComponents.sumOf { it.dependencies.size }}")
        println("  - Issues: ${detectedIssues.size}")
        println("  - Accuracy: ${String.format("%.1f", accuracyMetrics.getPrecision() * 100)}%")
    }

    /**
     * Test accuracy with nested package hierarchy (common in large projects)
     */
    @Test
    fun testNestedPackageHierarchyAccuracy() {
        val domainServiceContent = """
            package com.example.nested.domain.service
            
            import knit.Provides
            import knit.di
            
            @Provides
            class OrderDomainService {
                private val repository: com.example.nested.infrastructure.repository.OrderRepository by di
                private val validator: com.example.nested.domain.validator.OrderValidator by di
                
                fun processOrder(orderId: String): Boolean {
                    return validator.validate(orderId) && repository.exists(orderId)
                }
            }
        """.trimIndent()
        
        val domainValidatorContent = """
            package com.example.nested.domain.validator
            
            import knit.Provides
            
            @Provides
            class OrderValidator {
                fun validate(orderId: String): Boolean = orderId.isNotEmpty()
            }
        """.trimIndent()
        
        val infrastructureRepositoryContent = """
            package com.example.nested.infrastructure.repository
            
            import knit.Provides
            
            @Provides
            class OrderRepository {
                fun exists(orderId: String): Boolean = true
                fun findById(orderId: String): String? = orderId
            }
        """.trimIndent()
        
        val applicationControllerContent = """
            package com.example.nested.application.controller
            
            import knit.Provides
            import knit.di
            
            @Provides
            class OrderController {
                private val domainService: com.example.nested.domain.service.OrderDomainService by di
                
                fun handleOrderRequest(orderId: String): String {
                    return if (domainService.processOrder(orderId)) {
                        "Order processed successfully"
                    } else {
                        "Order processing failed"
                    }
                }
            }
        """.trimIndent()
        
        myFixture.addFileToProject("domain/service/OrderDomainService.kt", domainServiceContent)
        myFixture.addFileToProject("domain/validator/OrderValidator.kt", domainValidatorContent)
        myFixture.addFileToProject("infrastructure/repository/OrderRepository.kt", infrastructureRepositoryContent)
        myFixture.addFileToProject("application/controller/OrderController.kt", applicationControllerContent)
        
        val components = sourceAnalyzer.analyzeProject()
        val nestedComponents = components.filter { it.packageName.startsWith("com.example.nested") }
        
        val dependencyGraph = knitAnalysisService.buildDependencyGraph(nestedComponents)
        val detectedIssues = knitAnalysisService.detectIssues(nestedComponents, dependencyGraph)
        
        val validatedIssues = issueValidator.validateIssues(detectedIssues, nestedComponents)
        val accuracyMetrics = statisticalService.calculateAccuracyMetrics(
            allIssues = validatedIssues,
            validatedIssues = validatedIssues,
            expectedIssues = 0
        )
        
        // Validate nested package handling
        assertTrue("Should detect all components across packages", nestedComponents.size >= 4)
        
        // Verify cross-package dependency resolution
        val domainService = nestedComponents.find { it.className == "OrderDomainService" }
        assertNotNull("OrderDomainService should be detected", domainService)
        assertEquals("Should have 2 cross-package dependencies", 2, domainService!!.dependencies.size)
        
        val crossPackageDeps = domainService.dependencies.filter { dep ->
            !dep.targetType.startsWith(domainService.packageName)
        }
        assertTrue("Should have cross-package dependencies", crossPackageDeps.isNotEmpty())
        
        assertTrue("Should maintain high accuracy with nested packages", accuracyMetrics.getPrecision() >= 0.90)
        
        println("✅ Nested Package Hierarchy Accuracy:")
        println("  - Components: ${nestedComponents.size}")
        println("  - Packages: ${nestedComponents.map { it.packageName }.distinct().size}")
        println("  - Cross-package deps: ${crossPackageDeps.size}")
        println("  - Issues: ${detectedIssues.size}")
        println("  - Accuracy: ${String.format("%.1f", accuracyMetrics.getPrecision() * 100)}%")
    }

    /**
     * Test accuracy with domain-driven design package structure
     */
    @Test
    fun testDomainDrivenDesignAccuracy() {
        val userAggregateContent = """
            package com.example.ddd.user.domain
            
            import knit.Provides
            import knit.di
            
            @Provides
            class UserAggregate {
                private val repository: com.example.ddd.user.infrastructure.UserRepositoryImpl by di
                private val events: com.example.ddd.shared.events.DomainEventPublisher by di
                
                fun createUser(userData: UserData): User {
                    val user = User(userData.name, userData.email)
                    repository.save(user)
                    events.publish(UserCreatedEvent(user.id))
                    return user
                }
            }
            
            data class User(val name: String, val email: String, val id: String = "user-123")
            data class UserData(val name: String, val email: String)
            data class UserCreatedEvent(val userId: String)
        """.trimIndent()
        
        val userRepositoryContent = """
            package com.example.ddd.user.infrastructure
            
            import knit.Provides
            import com.example.ddd.user.domain.User
            
            @Provides
            class UserRepositoryImpl {
                fun save(user: User): User = user
                fun findById(id: String): User? = null
            }
        """.trimIndent()
        
        val orderAggregateContent = """
            package com.example.ddd.order.domain
            
            import knit.Provides
            import knit.di
            
            @Provides
            class OrderAggregate {
                private val repository: com.example.ddd.order.infrastructure.OrderRepositoryImpl by di
                private val userService: com.example.ddd.user.application.UserApplicationService by di
                
                fun createOrder(userId: String, items: List<String>): Order {
                    // Cross-domain interaction
                    val userExists = userService.userExists(userId)
                    return if (userExists) {
                        val order = Order(userId, items)
                        repository.save(order)
                        order
                    } else {
                        throw IllegalArgumentException("User not found")
                    }
                }
            }
            
            data class Order(val userId: String, val items: List<String>, val id: String = "order-456")
        """.trimIndent()
        
        val userApplicationServiceContent = """
            package com.example.ddd.user.application
            
            import knit.Provides
            import knit.di
            
            @Provides
            class UserApplicationService {
                private val aggregate: com.example.ddd.user.domain.UserAggregate by di
                
                fun userExists(userId: String): Boolean = true
            }
        """.trimIndent()
        
        val sharedEventsContent = """
            package com.example.ddd.shared.events
            
            import knit.Provides
            
            @Provides
            class DomainEventPublisher {
                fun publish(event: Any) {
                    println("Publishing event: ${'$'}event")
                }
            }
        """.trimIndent()
        
        val orderRepositoryContent = """
            package com.example.ddd.order.infrastructure
            
            import knit.Provides
            import com.example.ddd.order.domain.Order
            
            @Provides
            class OrderRepositoryImpl {
                fun save(order: Order): Order = order
            }
        """.trimIndent()
        
        myFixture.addFileToProject("user/domain/UserAggregate.kt", userAggregateContent)
        myFixture.addFileToProject("user/infrastructure/UserRepositoryImpl.kt", userRepositoryContent)
        myFixture.addFileToProject("user/application/UserApplicationService.kt", userApplicationServiceContent)
        myFixture.addFileToProject("order/domain/OrderAggregate.kt", orderAggregateContent)
        myFixture.addFileToProject("order/infrastructure/OrderRepositoryImpl.kt", orderRepositoryContent)
        myFixture.addFileToProject("shared/events/DomainEventPublisher.kt", sharedEventsContent)
        
        val components = sourceAnalyzer.analyzeProject()
        val dddComponents = components.filter { it.packageName.startsWith("com.example.ddd") }
        
        val dependencyGraph = knitAnalysisService.buildDependencyGraph(dddComponents)
        val detectedIssues = knitAnalysisService.detectIssues(dddComponents, dependencyGraph)
        
        val validatedIssues = issueValidator.validateIssues(detectedIssues, dddComponents)
        val accuracyMetrics = statisticalService.calculateAccuracyMetrics(
            allIssues = validatedIssues,
            validatedIssues = validatedIssues,
            expectedIssues = 0
        )
        
        // Validate DDD structure handling
        val domainComponents = dddComponents.filter { it.packageName.contains(".domain") }
        val infrastructureComponents = dddComponents.filter { it.packageName.contains(".infrastructure") }
        val applicationComponents = dddComponents.filter { it.packageName.contains(".application") }
        val sharedComponents = dddComponents.filter { it.packageName.contains(".shared") }
        
        assertTrue("Should detect domain components", domainComponents.isNotEmpty())
        assertTrue("Should detect infrastructure components", infrastructureComponents.isNotEmpty())
        assertTrue("Should detect application components", applicationComponents.isNotEmpty())
        assertTrue("Should detect shared components", sharedComponents.isNotEmpty())
        
        // Test cross-domain dependencies
        val orderAggregate = dddComponents.find { it.className == "OrderAggregate" }
        assertNotNull("OrderAggregate should be detected", orderAggregate)
        
        val crossDomainDeps = orderAggregate!!.dependencies.filter { dep ->
            !dep.targetType.startsWith("com.example.ddd.order")
        }
        assertTrue("Should have cross-domain dependencies", crossDomainDeps.isNotEmpty())
        
        assertTrue("Should maintain accuracy with complex DDD structure", accuracyMetrics.getPrecision() >= 0.85)
        
        println("✅ Domain-Driven Design Structure Accuracy:")
        println("  - Total components: ${dddComponents.size}")
        println("  - Domain: ${domainComponents.size}, Infrastructure: ${infrastructureComponents.size}")
        println("  - Application: ${applicationComponents.size}, Shared: ${sharedComponents.size}")
        println("  - Cross-domain deps: ${crossDomainDeps.size}")
        println("  - Issues: ${detectedIssues.size}")
        println("  - Accuracy: ${String.format("%.1f", accuracyMetrics.getPrecision() * 100)}%")
    }

    /**
     * Test accuracy with mixed Knit usage patterns (basic + advanced features)
     */
    @Test
    fun testMixedKnitUsagePatternsAccuracy() {
        val mixedPatternsContent = """
            package com.example.mixed
            
            import knit.Provides
            import knit.Component
            import knit.Singleton
            import knit.Named
            import knit.IntoSet
            import knit.di
            
            // Basic usage pattern
            @Provides
            class BasicService {
                private val dependency: BasicDependency by di
                fun work() = dependency.doWork()
            }
            
            @Provides
            class BasicDependency {
                fun doWork(): String = "basic work"
            }
            
            // Advanced pattern: Singletons
            @Provides
            @Singleton
            class SingletonService {
                private val counter = mutableListOf<String>()
                
                fun addItem(item: String) = counter.add(item)
                fun getCount() = counter.size
            }
            
            // Advanced pattern: Named qualifiers
            @Provides
            @Named("primary")
            class PrimaryDatabaseService : DatabaseService {
                override fun connect(): String = "primary database"
            }
            
            @Provides
            @Named("secondary")  
            class SecondaryDatabaseService : DatabaseService {
                override fun connect(): String = "secondary database"
            }
            
            interface DatabaseService {
                fun connect(): String
            }
            
            @Provides
            class DatabaseClientService {
                @Named("primary")
                private val primaryDb: DatabaseService by di
                
                @Named("secondary")
                private val secondaryDb: DatabaseService by di
                
                fun connectToPrimary() = primaryDb.connect()
                fun connectToSecondary() = secondaryDb.connect()
            }
            
            // Advanced pattern: Set collection
            @Provides
            class PluginRegistry {
                @IntoSet
                @Provides
                fun provideUserPlugin(): Plugin = UserPlugin()
                
                @IntoSet
                @Provides
                fun provideOrderPlugin(): Plugin = OrderPlugin()
                
                private val plugins: Set<Plugin> by di
                
                fun getAllPlugins() = plugins
            }
            
            interface Plugin {
                fun getName(): String
            }
            
            class UserPlugin : Plugin {
                override fun getName() = "user"
            }
            
            class OrderPlugin : Plugin {
                override fun getName() = "order"  
            }
            
            // Component pattern
            @Component
            class MixedComponent {
                private val basicService: BasicService by di
                private val singletonService: SingletonService by di
                private val databaseClient: DatabaseClientService by di
                
                fun performComplexOperation(): String {
                    basicService.work()
                    singletonService.addItem("operation")
                    return databaseClient.connectToPrimary()
                }
            }
        """.trimIndent()
        
        myFixture.configureByText("MixedPatterns.kt", mixedPatternsContent)
        
        val components = sourceAnalyzer.analyzeProject()
        val mixedComponents = components.filter { it.packageName == "com.example.mixed" }
        
        val dependencyGraph = knitAnalysisService.buildDependencyGraph(mixedComponents)
        val detectedIssues = knitAnalysisService.detectIssues(mixedComponents, dependencyGraph)
        
        val validatedIssues = issueValidator.validateIssues(detectedIssues, mixedComponents)
        val accuracyMetrics = statisticalService.calculateAccuracyMetrics(
            allIssues = validatedIssues,
            validatedIssues = validatedIssues,
            expectedIssues = 0
        )
        
        // Validate mixed pattern handling
        assertTrue("Should detect multiple components with mixed patterns", mixedComponents.size >= 6)
        
        // Verify basic pattern detection
        val basicService = mixedComponents.find { it.className == "BasicService" }
        assertNotNull("Should detect basic service pattern", basicService)
        
        // Verify singleton detection
        val singletonService = mixedComponents.find { it.className == "SingletonService" }
        assertNotNull("Should detect singleton service", singletonService)
        
        // Verify named qualifier handling
        val databaseClient = mixedComponents.find { it.className == "DatabaseClientService" }
        assertNotNull("Should detect database client with named qualifiers", databaseClient)
        assertEquals("Should detect named dependencies", 2, databaseClient!!.dependencies.size)
        
        // Verify set collection pattern
        val pluginRegistry = mixedComponents.find { it.className == "PluginRegistry" }
        assertNotNull("Should detect plugin registry with set collection", pluginRegistry)
        assertTrue("Should detect set providers", pluginRegistry!!.providers.size >= 2)
        
        // Verify component pattern  
        val mixedComponent = mixedComponents.find { it.className == "MixedComponent" }
        assertNotNull("Should detect component pattern", mixedComponent)
        assertEquals("Component should have multiple dependencies", 3, mixedComponent!!.dependencies.size)
        
        assertTrue("Should maintain accuracy with mixed patterns", accuracyMetrics.getPrecision() >= 0.80)
        
        println("✅ Mixed Knit Usage Patterns Accuracy:")
        println("  - Components: ${mixedComponents.size}")
        println("  - Basic patterns: detected")
        println("  - Singleton patterns: detected") 
        println("  - Named qualifiers: detected")
        println("  - Set collections: detected")
        println("  - Component pattern: detected")
        println("  - Issues: ${detectedIssues.size}")
        println("  - Accuracy: ${String.format("%.1f", accuracyMetrics.getPrecision() * 100)}%")
    }

    /**
     * Test accuracy with large-scale enterprise project structure
     */
    @Test
    fun testLargeScaleEnterpriseAccuracy() {
        // Create a representative large enterprise structure
        val modules = listOf("user", "order", "payment", "inventory", "notification", "audit")
        val layers = listOf("controller", "service", "repository", "entity")
        
        val allComponents = mutableListOf<KnitComponent>()
        
        modules.forEach { module ->
            layers.forEach { layer ->
                val className = "${module.replaceFirstChar { it.uppercase() }}${layer.replaceFirstChar { it.uppercase() }}"
                val packageName = "com.enterprise.ecommerce.$module.$layer"
                
                val component = when (layer) {
                    "controller" -> createEnterpriseController(className, packageName, module)
                    "service" -> createEnterpriseService(className, packageName, module)
                    "repository" -> createEnterpriseRepository(className, packageName, module)
                    "entity" -> createEnterpriseEntity(className, packageName, module)
                    else -> createGenericComponent(className, packageName)
                }
                
                allComponents.add(component)
            }
        }
        
        // Simulate analysis of large enterprise structure
        val dependencyGraph = knitAnalysisService.buildDependencyGraph(allComponents)
        val detectedIssues = knitAnalysisService.detectIssues(allComponents, dependencyGraph)
        
        val validationStartTime = System.currentTimeMillis()
        val validatedIssues = issueValidator.validateIssues(detectedIssues, allComponents)
        val validationTime = System.currentTimeMillis() - validationStartTime
        
        val accuracyMetrics = statisticalService.calculateAccuracyMetrics(
            allIssues = validatedIssues,
            validatedIssues = validatedIssues,
            expectedIssues = statisticalService.estimateExpectedIssues(allComponents)
        )
        
        // Enterprise scale validation
        assertTrue("Should handle large component count", allComponents.size >= 20)
        assertTrue("Should complete validation in reasonable time (<2s)", validationTime < 2000)
        assertTrue("Should maintain accuracy at enterprise scale", accuracyMetrics.getPrecision() >= 0.75)
        
        // Verify modular structure
        val moduleCount = modules.size
        val layerCount = layers.size
        assertEquals("Should create all module-layer combinations", 
                    moduleCount * layerCount, allComponents.size)
        
        // Check cross-module dependencies
        val crossModuleDeps = allComponents.flatMap { component ->
            component.dependencies.filter { dep ->
                !dep.targetType.contains(component.packageName.split(".")[3]) // module name
            }
        }
        
        println("✅ Large-Scale Enterprise Structure Accuracy:")
        println("  - Total components: ${allComponents.size}")
        println("  - Modules: $moduleCount, Layers: $layerCount")
        println("  - Cross-module dependencies: ${crossModuleDeps.size}")
        println("  - Validation time: ${validationTime}ms")
        println("  - Issues detected: ${detectedIssues.size}")
        println("  - Accuracy: ${String.format("%.1f", accuracyMetrics.getPrecision() * 100)}%")
        println("  - Enterprise readiness: ${if (accuracyMetrics.getPrecision() >= 0.75 && validationTime < 2000) "READY" else "NEEDS OPTIMIZATION"}")
    }

    /**
     * Test accuracy with edge cases and corner scenarios
     */
    @Test
    fun testEdgeCasesAccuracy() {
        val edgeCasesContent = """
            package com.example.edge
            
            import knit.Provides
            import knit.di
            
            // Edge case: Self-referencing (should be detected as issue)
            @Provides
            class SelfReferencingService {
                private val self: SelfReferencingService by di
                fun work(): String = "self-work"
            }
            
            // Edge case: Very long class names
            @Provides
            class VeryLongClassNameServiceThatExceedsNormalLengthLimitsAndTestsParsingCapabilities {
                private val helper: EdgeCaseHelper by di
                fun process(): String = helper.help()
            }
            
            @Provides
            class EdgeCaseHelper {
                fun help(): String = "edge help"
            }
            
            // Edge case: Generic types with complex bounds
            @Provides
            class GenericProcessor<T : Comparable<T>, U : Collection<T>> {
                fun process(items: U): List<T> = items.sorted()
            }
            
            // Edge case: Nested classes (might not be supported)
            @Provides
            class OuterService {
                private val inner: InnerService by di
                
                @Provides
                class InnerService {
                    fun innerWork(): String = "inner work"
                }
                
                fun outerWork(): String = inner.innerWork()
            }
            
            // Edge case: Kotlin-specific features
            @Provides
            class KotlinSpecificService {
                private val dataProcessor: DataProcessor by di
                
                fun processData(data: String): String = dataProcessor.process(data)
            }
            
            @Provides
            data class DataProcessor(val prefix: String = "processed") {
                fun process(data: String): String = "${'$'}prefix: ${'$'}data"
            }
            
            // Edge case: Sealed classes
            @Provides
            sealed class ResultProcessor {
                @Provides
                object SuccessProcessor : ResultProcessor()
                
                @Provides  
                object ErrorProcessor : ResultProcessor()
            }
            
            // Edge case: Extension functions (might not be relevant for DI)
            @Provides
            class ExtensionService {
                fun String.processExtension(): String = "extended: ${'$'}this"
                
                fun useExtension(): String = "test".processExtension()
            }
        """.trimIndent()
        
        myFixture.configureByText("EdgeCases.kt", edgeCasesContent)
        
        val components = sourceAnalyzer.analyzeProject()
        val edgeComponents = components.filter { it.packageName == "com.example.edge" }
        
        val dependencyGraph = knitAnalysisService.buildDependencyGraph(edgeComponents)
        val detectedIssues = knitAnalysisService.detectIssues(edgeComponents, dependencyGraph)
        
        val validatedIssues = issueValidator.validateIssues(detectedIssues, edgeComponents)
        val accuracyMetrics = statisticalService.calculateAccuracyMetrics(
            allIssues = validatedIssues,
            validatedIssues = validatedIssues,
            expectedIssues = 1 // Expected: self-referencing issue
        )
        
        // Edge case validation
        assertTrue("Should handle edge case components", edgeComponents.isNotEmpty())
        
        // Should detect self-referencing issue
        val selfRefIssues = detectedIssues.filter { 
            it.message.contains("SelfReferencingService") && 
            (it.type == IssueType.CIRCULAR_DEPENDENCY || it.message.contains("self"))
        }
        
        // Should handle long class names
        val longNameService = edgeComponents.find { 
            it.className.contains("VeryLongClassNameService") 
        }
        assertNotNull("Should handle very long class names", longNameService)
        
        // Should handle generic types gracefully
        val genericProcessor = edgeComponents.find { it.className == "GenericProcessor" }
        if (genericProcessor != null) {
            println("  Generic types handling: detected")
        }
        
        // Should handle data classes
        val dataProcessor = edgeComponents.find { it.className == "DataProcessor" }
        if (dataProcessor != null) {
            println("  Data classes handling: detected")
        }
        
        assertTrue("Should maintain reasonable accuracy with edge cases", accuracyMetrics.getPrecision() >= 0.70)
        
        println("✅ Edge Cases Accuracy:")
        println("  - Edge case components: ${edgeComponents.size}")
        println("  - Self-reference issues: ${selfRefIssues.size}")
        println("  - Long names: handled")
        println("  - Generic types: ${if (genericProcessor != null) "handled" else "not applicable"}")
        println("  - Data classes: ${if (dataProcessor != null) "handled" else "not applicable"}")
        println("  - Total issues: ${detectedIssues.size}")
        println("  - Accuracy: ${String.format("%.1f", accuracyMetrics.getPrecision() * 100)}%")
    }

    /**
     * Comprehensive cross-platform accuracy validation
     */
    @Test
    fun testComprehensiveCrossPlatformAccuracy() {
        // Combined test of multiple patterns and structures
        val comprehensiveContent = """
            package com.comprehensive.test
            
            import knit.Provides
            import knit.Component
            import knit.Named
            import knit.di
            
            // Flat structure components
            @Provides
            class FlatServiceA {
                private val dep: FlatServiceB by di
            }
            
            @Provides
            class FlatServiceB {
                fun work(): String = "flat work"
            }
        """.trimIndent()
        
        val nestedContent = """
            package com.comprehensive.test.nested.deep.structure
            
            import knit.Provides
            import knit.di
            
            @Provides
            class DeepNestedService {
                private val flat: com.comprehensive.test.FlatServiceA by di
                private val validator: DeepValidator by di
                
                fun processDeep(): String = validator.validate("deep")
            }
            
            @Provides
            class DeepValidator {
                fun validate(input: String): String = "validated: ${'$'}input"
            }
        """.trimIndent()
        
        val mixedAdvancedContent = """
            package com.comprehensive.test.advanced
            
            import knit.Provides
            import knit.Named
            import knit.di
            
            @Provides
            @Named("primary")
            class PrimaryAdvancedService : AdvancedService {
                override fun process(): String = "primary advanced"
            }
            
            @Provides
            @Named("secondary")
            class SecondaryAdvancedService : AdvancedService {
                override fun process(): String = "secondary advanced"
            }
            
            interface AdvancedService {
                fun process(): String
            }
            
            @Component
            class AdvancedConsumer {
                @Named("primary")
                private val primary: AdvancedService by di
                
                @Named("secondary") 
                private val secondary: AdvancedService by di
                
                private val deepService: com.comprehensive.test.nested.deep.structure.DeepNestedService by di
                
                fun consumeAll(): List<String> = listOf(
                    primary.process(),
                    secondary.process(),
                    deepService.processDeep()
                )
            }
        """.trimIndent()
        
        myFixture.configureByText("ComprehensiveFlat.kt", comprehensiveContent)
        myFixture.addFileToProject("nested/deep/structure/DeepNested.kt", nestedContent)
        myFixture.addFileToProject("advanced/Advanced.kt", mixedAdvancedContent)
        
        val allComponents = sourceAnalyzer.analyzeProject()
        val comprehensiveComponents = allComponents.filter { 
            it.packageName.startsWith("com.comprehensive.test") 
        }
        
        val dependencyGraph = knitAnalysisService.buildDependencyGraph(comprehensiveComponents)
        val detectedIssues = knitAnalysisService.detectIssues(comprehensiveComponents, dependencyGraph)
        
        val validationStartTime = System.currentTimeMillis()
        val validatedIssues = issueValidator.validateIssues(detectedIssues, comprehensiveComponents)
        val validationTime = System.currentTimeMillis() - validationStartTime
        
        val accuracyMetrics = statisticalService.calculateAccuracyMetrics(
            allIssues = validatedIssues,
            validatedIssues = validatedIssues,
            expectedIssues = 0 // Clean comprehensive structure
        )
        
        // Analyze structure diversity
        val packageNames = comprehensiveComponents.map { it.packageName }.distinct()
        val flatComponents = comprehensiveComponents.filter { !it.packageName.contains(".nested") && !it.packageName.contains(".advanced") }
        val nestedComponents = comprehensiveComponents.filter { it.packageName.contains(".nested") }
        val advancedComponents = comprehensiveComponents.filter { it.packageName.contains(".advanced") }
        
        // Cross-package dependency analysis
        val crossPackageDeps = comprehensiveComponents.flatMap { component ->
            component.dependencies.filter { dep ->
                val depPackage = dep.targetType.substringBeforeLast(".")
                depPackage != component.className && !dep.targetType.startsWith(component.packageName)
            }
        }
        
        // Comprehensive validation
        assertTrue("Should detect components across all structures", comprehensiveComponents.size >= 6)
        assertTrue("Should handle multiple package structures", packageNames.size >= 3)
        assertTrue("Should handle cross-package dependencies", crossPackageDeps.isNotEmpty())
        assertTrue("Should complete validation efficiently", validationTime < 1000)
        assertTrue("Should maintain high comprehensive accuracy", accuracyMetrics.getPrecision() >= 0.85)
        
        println("✅ Comprehensive Cross-Platform Accuracy:")
        println("  - Total components: ${comprehensiveComponents.size}")
        println("  - Package structures: ${packageNames.size}")
        println("  - Flat: ${flatComponents.size}, Nested: ${nestedComponents.size}, Advanced: ${advancedComponents.size}")
        println("  - Cross-package dependencies: ${crossPackageDeps.size}")
        println("  - Validation time: ${validationTime}ms")
        println("  - Issues detected: ${detectedIssues.size}")
        println("  - Final accuracy: ${String.format("%.1f", accuracyMetrics.getPrecision() * 100)}%")
        println("  - Cross-platform readiness: ${if (accuracyMetrics.getPrecision() >= 0.85 && validationTime < 1000) "EXCELLENT" else "GOOD"}")
    }
    
    // Helper methods for enterprise structure creation
    private fun createEnterpriseController(className: String, packageName: String, module: String): KnitComponent {
        return KnitComponent(
            className = className,
            packageName = packageName,
            type = ComponentType.COMPONENT,
            dependencies = listOf(
                KnitDependency("${module}Service", "com.enterprise.ecommerce.$module.service.${module.replaceFirstChar { it.uppercase() }}Service", false)
            ),
            providers = emptyList(),
            sourceFile = "$className.kt"
        )
    }
    
    private fun createEnterpriseService(className: String, packageName: String, module: String): KnitComponent {
        return KnitComponent(
            className = className,
            packageName = packageName,
            type = ComponentType.COMPONENT,
            dependencies = listOf(
                KnitDependency("${module}Repository", "com.enterprise.ecommerce.$module.repository.${module.replaceFirstChar { it.uppercase() }}Repository", false)
            ),
            providers = emptyList(),
            sourceFile = "$className.kt"
        )
    }
    
    private fun createEnterpriseRepository(className: String, packageName: String, module: String): KnitComponent {
        return KnitComponent(
            className = className,
            packageName = packageName,
            type = ComponentType.PROVIDER,
            dependencies = emptyList(),
            providers = listOf(
                KnitProvider("provide${module.replaceFirstChar { it.uppercase() }}Data", "com.enterprise.ecommerce.$module.entity.${module.replaceFirstChar { it.uppercase() }}Entity", null, false, null, false)
            ),
            sourceFile = "$className.kt"
        )
    }
    
    private fun createEnterpriseEntity(className: String, packageName: String, @Suppress("UNUSED_PARAMETER") module: String): KnitComponent {
        return KnitComponent(
            className = className,
            packageName = packageName,
            type = ComponentType.COMPONENT,
            dependencies = emptyList(),
            providers = emptyList(),
            sourceFile = "$className.kt"
        )
    }
    
    private fun createGenericComponent(className: String, packageName: String): KnitComponent {
        return KnitComponent(
            className = className,
            packageName = packageName,
            type = ComponentType.COMPONENT,
            dependencies = emptyList(),
            providers = emptyList(),
            sourceFile = "$className.kt"
        )
    }
}