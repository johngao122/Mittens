package com.example.mittens.issues

import com.example.mittens.model.*
import com.example.mittens.services.AdvancedIssueDetector
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

/**
 * Integration tests for end-to-end issue detection scenarios
 * Tests real-world scenarios combining multiple issue types
 */
class IssueDetectionIntegrationTest : BasePlatformTestCase() {
    
    private lateinit var advancedDetector: AdvancedIssueDetector
    
    override fun setUp() {
        super.setUp()
        advancedDetector = AdvancedIssueDetector(project)
    }
    
    @Test
    fun testRealWorldScenarioWithMultipleIssues() {
        // Simulate a real-world scenario with multiple types of issues
        val components = createRealWorldScenario()
        val graph = createDependencyGraph(components)
        
        // Test all detections together
        val cycleIssues = advancedDetector.detectAdvancedCircularDependencies(components, graph)
        val ambiguousIssues = advancedDetector.detectEnhancedAmbiguousProviders(components)
        val unresolvedIssues = advancedDetector.detectImprovedUnresolvedDependencies(components)
        val singletonIssues = advancedDetector.detectAdvancedSingletonViolations(components)
        val qualifierIssues = advancedDetector.detectEnhancedNamedQualifierMismatches(components)
        
        // Verify each type of issue is detected
        assertTrue("Should detect circular dependencies", cycleIssues.isNotEmpty())
        assertTrue("Should detect ambiguous providers", ambiguousIssues.isNotEmpty())
        assertTrue("Should detect unresolved dependencies", unresolvedIssues.isNotEmpty())
        assertTrue("Should detect singleton violations", singletonIssues.isNotEmpty())
        assertTrue("Should detect qualifier mismatches", qualifierIssues.isNotEmpty())
        
        val allIssues = cycleIssues + ambiguousIssues + unresolvedIssues + singletonIssues + qualifierIssues
        
        println("Real World Scenario Results:")
        println("  Components: ${components.size}")
        println("  Total Issues: ${allIssues.size}")
        println("  Circular Dependencies: ${cycleIssues.size}")
        println("  Ambiguous Providers: ${ambiguousIssues.size}")
        println("  Unresolved Dependencies: ${unresolvedIssues.size}")
        println("  Singleton Violations: ${singletonIssues.size}")
        println("  Qualifier Mismatches: ${qualifierIssues.size}")
        
        // Verify issue severity distribution
        val errorCount = allIssues.count { it.severity == Severity.ERROR }
        val warningCount = allIssues.count { it.severity == Severity.WARNING }
        
        assertTrue("Should have error-level issues", errorCount > 0)
        assertTrue("Should have warning-level issues", warningCount > 0)
        
        println("  Errors: $errorCount")
        println("  Warnings: $warningCount")
    }
    
    @Test
    fun testECommerceApplicationScenario() {
        // E-commerce application with typical DI patterns and issues
        val components = createECommerceScenario()
        val graph = createDependencyGraph(components)
        
        val allIssues = mutableListOf<KnitIssue>()
        allIssues.addAll(advancedDetector.detectAdvancedCircularDependencies(components, graph))
        allIssues.addAll(advancedDetector.detectEnhancedAmbiguousProviders(components))
        allIssues.addAll(advancedDetector.detectImprovedUnresolvedDependencies(components))
        allIssues.addAll(advancedDetector.detectAdvancedSingletonViolations(components))
        allIssues.addAll(advancedDetector.detectEnhancedNamedQualifierMismatches(components))
        
        println("E-commerce Application Scenario:")
        println("  Components: ${components.size}")
        println("  Total Issues: ${allIssues.size}")
        
        // Verify specific business logic issues
        val userServiceIssues = allIssues.filter { 
            it.message.contains("UserService") || it.componentName.contains("UserService")
        }
        val orderServiceIssues = allIssues.filter { 
            it.message.contains("OrderService") || it.componentName.contains("OrderService")
        }
        
        assertTrue("Should detect UserService-related issues", userServiceIssues.isNotEmpty())
        assertTrue("Should detect OrderService-related issues", orderServiceIssues.isNotEmpty())
        
        // Test issue categorization
        val businessLogicIssues = allIssues.filter {
            it.componentName.contains("Service") || it.componentName.contains("Controller")
        }
        val infrastructureIssues = allIssues.filter {
            it.componentName.contains("Repository") || it.componentName.contains("Provider")
        }
        
        println("  Business Logic Issues: ${businessLogicIssues.size}")
        println("  Infrastructure Issues: ${infrastructureIssues.size}")
        
        assertTrue("Should categorize business logic issues", businessLogicIssues.isNotEmpty())
        assertTrue("Should categorize infrastructure issues", infrastructureIssues.isNotEmpty())
    }
    
    @Test
    fun testMicroserviceArchitectureScenario() {
        // Microservice architecture with service-to-service dependencies
        val components = createMicroserviceScenario()
        val graph = createDependencyGraph(components)
        
        val startTime = System.currentTimeMillis()
        
        val cycleIssues = advancedDetector.detectAdvancedCircularDependencies(components, graph)
        val ambiguousIssues = advancedDetector.detectEnhancedAmbiguousProviders(components)
        val unresolvedIssues = advancedDetector.detectImprovedUnresolvedDependencies(components)
        val singletonIssues = advancedDetector.detectAdvancedSingletonViolations(components)
        val qualifierIssues = advancedDetector.detectEnhancedNamedQualifierMismatches(components)
        
        val detectionTime = System.currentTimeMillis() - startTime
        val allIssues = cycleIssues + ambiguousIssues + unresolvedIssues + singletonIssues + qualifierIssues
        
        println("Microservice Architecture Scenario:")
        println("  Components: ${components.size}")
        println("  Detection Time: ${detectionTime}ms")
        println("  Total Issues: ${allIssues.size}")
        
        // Performance assertion for microservice complexity
        assertTrue("Should handle microservice complexity efficiently (< 1s)", detectionTime < 1000)
        
        // Verify service-level issue detection
        val serviceIssues = allIssues.filter { it.componentName.contains("Service") }
        val gatewayIssues = allIssues.filter { it.componentName.contains("Gateway") }
        val clientIssues = allIssues.filter { it.componentName.contains("Client") }
        
        println("  Service Issues: ${serviceIssues.size}")
        println("  Gateway Issues: ${gatewayIssues.size}")
        println("  Client Issues: ${clientIssues.size}")
    }
    
    @Test
    fun testLegacySystemRefactoringScenario() {
        // Legacy system being refactored to use DI
        val components = createLegacyRefactoringScenario()
        val graph = createDependencyGraph(components)
        
        val allIssues = mutableListOf<KnitIssue>()
        allIssues.addAll(advancedDetector.detectAdvancedCircularDependencies(components, graph))
        allIssues.addAll(advancedDetector.detectEnhancedAmbiguousProviders(components))
        allIssues.addAll(advancedDetector.detectImprovedUnresolvedDependencies(components))
        allIssues.addAll(advancedDetector.detectAdvancedSingletonViolations(components))
        allIssues.addAll(advancedDetector.detectEnhancedNamedQualifierMismatches(components))
        
        println("Legacy System Refactoring Scenario:")
        println("  Components: ${components.size}")
        println("  Total Issues: ${allIssues.size}")
        
        // Legacy systems often have more severe architectural issues
        val circularDependencies = allIssues.filter { it.type == IssueType.CIRCULAR_DEPENDENCY }
        val singletonViolations = allIssues.filter { it.type == IssueType.SINGLETON_VIOLATION }
        
        assertTrue("Legacy systems should have circular dependency issues", circularDependencies.isNotEmpty())
        assertTrue("Legacy systems should have singleton issues", singletonViolations.isNotEmpty())
        
        println("  Circular Dependencies: ${circularDependencies.size}")
        println("  Singleton Violations: ${singletonViolations.size}")
        
        // Verify issue severity reflects legacy complexity
        val errorCount = allIssues.count { it.severity == Severity.ERROR }
        val totalIssues = allIssues.size
        val errorRate = errorCount.toDouble() / totalIssues
        
        assertTrue("Legacy systems should have high error rate (>50%)", errorRate > 0.5)
        println("  Error Rate: ${(errorRate * 100).toInt()}%")
    }
    
    @Test
    fun testComplexDependencyChainScenario() {
        // Complex dependency chains with multiple levels
        val components = createComplexDependencyChainScenario()
        val graph = createDependencyGraph(components)
        
        val cycleReport = graph.getCycleReport()
        val issues = advancedDetector.detectAdvancedCircularDependencies(components, graph)
        
        println("Complex Dependency Chain Scenario:")
        println("  Components: ${components.size}")
        println("  Graph Edges: ${graph.edges.size}")
        println("  Has Cycles: ${cycleReport.hasCycles}")
        println("  Cycle Count: ${cycleReport.cycleCount}")
        println("  Issues Generated: ${issues.size}")
        
        if (cycleReport.hasCycles) {
            val shortestCycle = cycleReport.getShortestCycle()
            val longestCycle = cycleReport.getLongestCycle()
            
            println("  Shortest Cycle: ${shortestCycle?.length} components")
            println("  Longest Cycle: ${longestCycle?.length} components")
            println("  Total Nodes in Cycles: ${cycleReport.getTotalNodesInCycles()}")
        }
        
        // Test strongly connected components
        val stronglyConnectedComponents = graph.getStronglyConnectedComponents()
        println("  Strongly Connected Components: ${stronglyConnectedComponents.size}")
        
        if (stronglyConnectedComponents.isNotEmpty()) {
            val largestSCC = stronglyConnectedComponents.maxByOrNull { it.size }
            println("  Largest SCC Size: ${largestSCC?.size}")
        }
    }
    
    @Test
    fun testIssueMetadataAndSuggestions() {
        // Test that issues contain rich metadata and helpful suggestions
        val components = createScenariosWithRichMetadata()
        val graph = createDependencyGraph(components)
        
        val allIssues = mutableListOf<KnitIssue>()
        allIssues.addAll(advancedDetector.detectAdvancedCircularDependencies(components, graph))
        allIssues.addAll(advancedDetector.detectEnhancedAmbiguousProviders(components))
        allIssues.addAll(advancedDetector.detectImprovedUnresolvedDependencies(components))
        allIssues.addAll(advancedDetector.detectAdvancedSingletonViolations(components))
        allIssues.addAll(advancedDetector.detectEnhancedNamedQualifierMismatches(components))
        
        println("Issue Metadata and Suggestions Test:")
        println("  Total Issues: ${allIssues.size}")
        
        // Verify metadata completeness
        val issuesWithMetadata = allIssues.count { it.metadata.isNotEmpty() }
        val metadataRate = issuesWithMetadata.toDouble() / allIssues.size
        
        assertTrue("Most issues should have metadata (>80%)", metadataRate > 0.8)
        println("  Issues with Metadata: $issuesWithMetadata (${(metadataRate * 100).toInt()}%)")
        
        // Verify suggestion quality
        val issuesWithSuggestions = allIssues.count { !it.suggestedFix.isNullOrBlank() }
        val suggestionRate = issuesWithSuggestions.toDouble() / allIssues.size
        
        assertTrue("Most issues should have suggestions (>90%)", suggestionRate > 0.9)
        println("  Issues with Suggestions: $issuesWithSuggestions (${(suggestionRate * 100).toInt()}%)")
        
        // Test suggestion quality
        val detailedSuggestions = allIssues.count { 
            (it.suggestedFix?.length ?: 0) > 30 // Substantial suggestions
        }
        val detailRate = detailedSuggestions.toDouble() / allIssues.size
        
        assertTrue("Many issues should have detailed suggestions (>60%)", detailRate > 0.6)
        println("  Issues with Detailed Suggestions: $detailedSuggestions (${(detailRate * 100).toInt()}%)")
    }
    
    @Test
    fun testEndToEndPerformanceWithRealWorldComplexity() {
        // End-to-end performance test with realistic complexity
        val componentCount = 150
        val components = createRealisticComplexScenario(componentCount)
        val graph = createDependencyGraph(components)
        
        val totalStartTime = System.currentTimeMillis()
        
        val cycleIssues = advancedDetector.detectAdvancedCircularDependencies(components, graph)
        val ambiguousIssues = advancedDetector.detectEnhancedAmbiguousProviders(components)
        val unresolvedIssues = advancedDetector.detectImprovedUnresolvedDependencies(components)
        val singletonIssues = advancedDetector.detectAdvancedSingletonViolations(components)
        val qualifierIssues = advancedDetector.detectEnhancedNamedQualifierMismatches(components)
        
        val totalTime = System.currentTimeMillis() - totalStartTime
        val allIssues = cycleIssues + ambiguousIssues + unresolvedIssues + singletonIssues + qualifierIssues
        
        println("End-to-End Performance Test:")
        println("  Components: $componentCount")
        println("  Graph Edges: ${graph.edges.size}")
        println("  Total Detection Time: ${totalTime}ms")
        println("  Total Issues Found: ${allIssues.size}")
        println("  Performance: ${componentCount.toDouble() / totalTime * 1000} components/second")
        
        // Performance assertions
        assertTrue("Should complete within 2 seconds", totalTime < 2000)
        assertTrue("Should detect issues efficiently", allIssues.size > 0)
        
        // Memory efficiency check
        System.gc()
        val memoryUsage = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024
        println("  Memory Usage: ${memoryUsage}MB")
        assertTrue("Memory usage should be reasonable", memoryUsage < 100)
    }
    
    // Helper methods for creating test scenarios
    
    private fun createRealWorldScenario(): List<KnitComponent> {
        val components = mutableListOf<KnitComponent>()
        
        // Service layer with circular dependency (A -> B -> A)
        val userService = KnitComponent(
            className = "UserService",
            packageName = "com.app.service",
            type = ComponentType.COMPONENT,
            dependencies = listOf(
                KnitDependency("orderService", "OrderService", false, null, false, false, false)
            ),
            providers = emptyList(),
            sourceFile = "UserService.kt"
        )
        
        val orderService = KnitComponent(
            className = "OrderService", 
            packageName = "com.app.service",
            type = ComponentType.COMPONENT,
            dependencies = listOf(
                KnitDependency("userService", "UserService", false, null, false, false, false)
            ),
            providers = emptyList(),
            sourceFile = "OrderService.kt"
        )
        
        // Ambiguous providers
        val dbProvider1 = KnitComponent(
            className = "DatabaseProvider1",
            packageName = "com.app.data",
            type = ComponentType.PROVIDER,
            dependencies = emptyList(),
            providers = listOf(
                KnitProvider("provideDatabase", "DatabaseService", null, false, null, true, false, false, false)
            ),
            sourceFile = "DatabaseProvider1.kt"
        )
        
        val dbProvider2 = KnitComponent(
            className = "DatabaseProvider2",
            packageName = "com.app.data", 
            type = ComponentType.PROVIDER,
            dependencies = emptyList(),
            providers = listOf(
                KnitProvider("provideDatabase", "DatabaseService", null, false, null, true, false, false, false)
            ),
            sourceFile = "DatabaseProvider2.kt"
        )
        
        // Unresolved dependency
        val emailService = KnitComponent(
            className = "EmailService",
            packageName = "com.app.notification",
            type = ComponentType.COMPONENT,
            dependencies = listOf(
                KnitDependency("smtpClient", "SMTPClient", false, null, false, false, false) // No provider
            ),
            providers = emptyList(),
            sourceFile = "EmailService.kt"
        )
        
        // Singleton violations
        val cacheProvider1 = KnitComponent(
            className = "CacheProvider1",
            packageName = "com.app.cache",
            type = ComponentType.PROVIDER,
            dependencies = emptyList(),
            providers = listOf(
                KnitProvider("provideCache", "CacheService", null, false, null, true, false, false, false)
            ),
            sourceFile = "CacheProvider1.kt"
        )
        
        val cacheProvider2 = KnitComponent(
            className = "CacheProvider2",
            packageName = "com.app.cache",
            type = ComponentType.PROVIDER,
            dependencies = emptyList(),
            providers = listOf(
                KnitProvider("provideCache", "CacheService", null, false, null, true, false, false, false)
            ),
            sourceFile = "CacheProvider2.kt"
        )
        
        // Qualifier mismatch
        val logService = KnitComponent(
            className = "LogService",
            packageName = "com.app.logging",
            type = ComponentType.COMPONENT,
            dependencies = listOf(
                KnitDependency("fileLogger", "Logger", true, "file", false, false, false)
            ),
            providers = emptyList(),
            sourceFile = "LogService.kt"
        )
        
        val logProvider = KnitComponent(
            className = "LogProvider",
            packageName = "com.app.logging",
            type = ComponentType.PROVIDER,
            dependencies = emptyList(),
            providers = listOf(
                KnitProvider("provideLogger", "Logger", null, true, "console", false, false, false, false) // Wrong qualifier
            ),
            sourceFile = "LogProvider.kt"
        )
        
        // Add a longer cycle that should generate WARNING severity
        // Create a 5-component cycle: A -> B -> C -> D -> E -> A
        val componentA = KnitComponent(
            className = "ComponentA",
            packageName = "com.app.chain",
            type = ComponentType.COMPONENT,
            dependencies = listOf(
                KnitDependency("componentB", "ComponentB", false, null, false, false, false)
            ),
            providers = emptyList(),
            sourceFile = "ComponentA.kt"
        )
        
        val componentB = KnitComponent(
            className = "ComponentB",
            packageName = "com.app.chain",
            type = ComponentType.COMPONENT,
            dependencies = listOf(
                KnitDependency("componentC", "ComponentC", false, null, false, false, false)
            ),
            providers = emptyList(),
            sourceFile = "ComponentB.kt"
        )
        
        val componentC = KnitComponent(
            className = "ComponentC",
            packageName = "com.app.chain",
            type = ComponentType.COMPONENT,
            dependencies = listOf(
                KnitDependency("componentD", "ComponentD", false, null, false, false, false)
            ),
            providers = emptyList(),
            sourceFile = "ComponentC.kt"
        )
        
        val componentD = KnitComponent(
            className = "ComponentD",
            packageName = "com.app.chain",
            type = ComponentType.COMPONENT,
            dependencies = listOf(
                KnitDependency("componentE", "ComponentE", false, null, false, false, false)
            ),
            providers = emptyList(),
            sourceFile = "ComponentD.kt"
        )
        
        val componentE = KnitComponent(
            className = "ComponentE",
            packageName = "com.app.chain",
            type = ComponentType.COMPONENT,
            dependencies = listOf(
                KnitDependency("componentA", "ComponentA", false, null, false, false, false)
            ),
            providers = emptyList(),
            sourceFile = "ComponentE.kt"
        )
        
        components.addAll(listOf(
            userService, orderService, dbProvider1, dbProvider2, 
            emailService, cacheProvider1, cacheProvider2, logService, logProvider,
            componentA, componentB, componentC, componentD, componentE
        ))
        
        return components
    }
    
    private fun createECommerceScenario(): List<KnitComponent> {
        // Simplified e-commerce scenario
        return listOf(
            createComponent("UserController", "com.shop.web", dependencies = listOf("UserService")),
            createComponent("UserService", "com.shop.service", dependencies = listOf("UserRepository", "EmailService")),
            createComponent("OrderController", "com.shop.web", dependencies = listOf("OrderService")),
            createComponent("OrderService", "com.shop.service", dependencies = listOf("OrderRepository", "UserService")),
            createComponent("PaymentService", "com.shop.payment", dependencies = listOf("PaymentGateway")),
            createProviderComponent("UserRepository", "com.shop.data", provides = listOf("UserRepository")),
            createProviderComponent("OrderRepository", "com.shop.data", provides = listOf("OrderRepository")),
            // Add ambiguous providers to create infrastructure issues
            createProviderComponent("AmbiguousUserProvider", "com.shop.data", provides = listOf("UserRepository")), // Duplicate UserRepository
            createProviderComponent("AmbiguousOrderProvider", "com.shop.data", provides = listOf("OrderRepository")), // Duplicate OrderRepository
            // Missing EmailService and PaymentGateway providers (unresolved dependencies)
        )
    }
    
    private fun createMicroserviceScenario(): List<KnitComponent> {
        return listOf(
            createComponent("APIGateway", "com.ms.gateway", dependencies = listOf("UserServiceClient", "OrderServiceClient")),
            createComponent("UserServiceClient", "com.ms.client", dependencies = listOf("HttpClient")),
            createComponent("OrderServiceClient", "com.ms.client", dependencies = listOf("HttpClient")),
            createComponent("UserService", "com.ms.user", dependencies = listOf("UserRepository", "MessageBroker")),
            createComponent("OrderService", "com.ms.order", dependencies = listOf("OrderRepository", "MessageBroker")),
            createComponent("NotificationService", "com.ms.notification", dependencies = listOf("MessageBroker")),
            createProviderComponent("MessageBroker", "com.ms.infra", provides = listOf("MessageBroker")),
            createProviderComponent("HttpClientProvider", "com.ms.infra", provides = listOf("HttpClient")),
            // Some repositories missing for complexity
        )
    }
    
    private fun createLegacyRefactoringScenario(): List<KnitComponent> {
        // Legacy systems often have tight coupling and circular dependencies
        return listOf(
            createComponent("LegacyUserManager", "com.legacy.user", 
                dependencies = listOf("LegacyOrderManager", "LegacyDataAccess")),
            createComponent("LegacyOrderManager", "com.legacy.order", 
                dependencies = listOf("LegacyUserManager", "LegacyPaymentProcessor")),
            createComponent("LegacyPaymentProcessor", "com.legacy.payment", 
                dependencies = listOf("LegacyDataAccess")),
            createComponent("LegacyDataAccess", "com.legacy.data", 
                dependencies = listOf("LegacyUserManager")), // Creates cycles
            // Use two distinct provider components to realistically represent duplicate providers
            createProviderComponent("GlobalSingletonProvider1", "com.legacy.infra", 
                provides = listOf("LegacyDataAccess")),
            createProviderComponent("GlobalSingletonProvider2", "com.legacy.infra", 
                provides = listOf("LegacyDataAccess")),
        )
    }
    
    private fun createComplexDependencyChainScenario(): List<KnitComponent> {
        val components = mutableListOf<KnitComponent>()
        val chainLength = 20
        
        // Create a chain: A -> B -> C -> ... -> T -> A (circular)
        for (i in 0 until chainLength) {
            val currentName = "ChainComponent$i"
            val nextName = "ChainComponent${(i + 1) % chainLength}"
            
            components.add(KnitComponent(
                className = currentName,
                packageName = "com.chain.test",
                type = ComponentType.COMPONENT,
                dependencies = listOf(KnitDependency("next", nextName, false, null, false, false, false)),
                providers = emptyList(),
                sourceFile = "$currentName.kt"
            ))
        }
        
        return components
    }
    
    private fun createScenariosWithRichMetadata(): List<KnitComponent> {
        return listOf(
            // Component with detailed metadata-worthy dependencies
            KnitComponent(
                className = "ComplexService",
                packageName = "com.test.complex",
                type = ComponentType.COMPONENT,
                dependencies = listOf(
                    KnitDependency("primaryDb", "DatabaseService", true, "primary", true, false, false),
                    KnitDependency("userFactory", "Factory<User>", false, null, false, true, false),
                    KnitDependency("configLoader", "Loadable<Config>", false, null, false, false, true),
                    KnitDependency("missingService", "MissingService", false, null, false, false, false)
                ),
                providers = emptyList(),
                sourceFile = "ComplexService.kt"
            ),
            // Provider with singleton violations
            createProviderComponent("ConflictingProvider", "com.test.provider", 
                provides = listOf("DatabaseService", "DatabaseService")) // Ambiguous
        )
    }
    
    private fun createRealisticComplexScenario(componentCount: Int): List<KnitComponent> {
        val components = mutableListOf<KnitComponent>()
        
        // Mix of different component patterns found in real applications
        val patterns = listOf("Controller", "Service", "Repository", "Provider", "Client", "Manager")
        val packages = listOf("web", "service", "data", "infra", "client", "util")
        
        for (i in 0 until componentCount) {
            val pattern = patterns[i % patterns.size]
            val pkg = packages[i % packages.size]
            val name = "$pattern$i"
            
            val dependencies = mutableListOf<KnitDependency>()
            val providers = mutableListOf<KnitProvider>()
            
            // Add realistic dependencies based on pattern
            when (pattern) {
                "Controller" -> {
                    dependencies.add(KnitDependency("service", "Service${i/2}", false, null, false, false, false))
                }
                "Service" -> {
                    dependencies.add(KnitDependency("repository", "Repository${i/3}", false, null, false, false, false))
                    if (i % 3 == 0) {
                        dependencies.add(KnitDependency("otherService", "Service${(i+1)%componentCount}", false, null, false, false, false))
                    }
                }
                "Repository" -> {
                    dependencies.add(KnitDependency("dataSource", "DataSource", false, null, true, false, false))
                }
                "Provider" -> {
                    providers.add(KnitProvider("provide$pattern", "${pattern}Type$i", null, false, null, i % 5 == 0, false, false, false))
                }
            }
            
            components.add(KnitComponent(
                className = name,
                packageName = "com.app.$pkg",
                type = when (pattern) {
                    "Provider" -> ComponentType.PROVIDER
                    else -> ComponentType.COMPONENT
                },
                dependencies = dependencies,
                providers = providers,
                sourceFile = "$name.kt"
            ))
        }
        
        return components
    }
    
    private fun createComponent(
        name: String, 
        packageName: String, 
        dependencies: List<String> = emptyList()
    ): KnitComponent {
        return KnitComponent(
            className = name,
            packageName = packageName,
            type = ComponentType.COMPONENT,
            dependencies = dependencies.map { 
                KnitDependency(it.lowercase(), it, false, null, false, false, false) 
            },
            providers = emptyList(),
            sourceFile = "$name.kt"
        )
    }
    
    private fun createProviderComponent(
        name: String,
        packageName: String,
        provides: List<String>
    ): KnitComponent {
        return KnitComponent(
            className = name,
            packageName = packageName,
            type = ComponentType.PROVIDER,
            dependencies = emptyList(),
            providers = provides.map { 
                KnitProvider("provide${it.replace("Service", "")}", it, null, false, null, false, false, false, false) 
            },
            sourceFile = "$name.kt"
        )
    }
    
    private fun createDependencyGraph(components: List<KnitComponent>): DependencyGraph {
        val nodes = components.map { component ->
            GraphNode(
                id = "${component.packageName}.${component.className}",
                label = component.className,
                type = NodeType.COMPONENT,
                packageName = component.packageName
            )
        }
        
        val edges = mutableListOf<GraphEdge>()
        components.forEach { component ->
            val fromId = "${component.packageName}.${component.className}"
            component.dependencies.forEach { dependency ->
                val targetComponents = components.filter { it.className == dependency.targetType }
                targetComponents.forEach { targetComponent ->
                    val toId = "${targetComponent.packageName}.${targetComponent.className}"
                    edges.add(GraphEdge(
                        from = fromId,
                        to = toId,
                        type = EdgeType.DEPENDENCY,
                        label = dependency.propertyName
                    ))
                }
            }
        }
        
        return DependencyGraph(nodes, edges)
    }
}