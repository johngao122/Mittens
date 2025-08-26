package com.example.mittens.performance

import com.example.mittens.model.*
import com.example.mittens.services.AdvancedIssueDetector
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import kotlin.random.Random

/**
 * Performance benchmark tests for large graph testing
 * Tests scalability and performance optimizations of Phase 4 enhancements
 */
class PerformanceBenchmarkTest : BasePlatformTestCase() {
    
    private lateinit var advancedDetector: AdvancedIssueDetector
    
    override fun setUp() {
        super.setUp()
        advancedDetector = AdvancedIssueDetector(project)
    }
    
    @Test
    fun testLargeGraphCycleDetection() {
        // Test cycle detection performance with large graphs
        val componentCount = 1000
        val components = createLargeComponentGraph(componentCount, cycleFrequency = 0.1)
        val graph = createDependencyGraph(components)
        
        val startTime = System.currentTimeMillis()
        val cycleReport = graph.getCycleReport()
        val cycleDetectionTime = System.currentTimeMillis() - startTime
        
        // Performance assertions
        assertTrue("Cycle detection should complete within 2 seconds for $componentCount components", 
                  cycleDetectionTime < 2000)
        
        val issueStartTime = System.currentTimeMillis()
        val issues = advancedDetector.detectAdvancedCircularDependencies(components, graph)
        val issueDetectionTime = System.currentTimeMillis() - issueStartTime
        
        assertTrue("Issue detection should complete within 1 second", issueDetectionTime < 1000)
        
        println("Performance Results - Large Graph Cycle Detection:")
        println("  Components: $componentCount")
        println("  Cycle Detection Time: ${cycleDetectionTime}ms")
        println("  Issue Detection Time: ${issueDetectionTime}ms")
        println("  Cycles Found: ${cycleReport.cycleCount}")
        println("  Issues Generated: ${issues.size}")
        
        // Verify correctness
        if (cycleReport.hasCycles) {
            assertTrue("Should detect cycle issues", issues.isNotEmpty())
        }
    }
    
    @Test
    fun testLargeScaleAmbiguousProviderDetection() {
        val componentCount = 500
        val providersPerComponent = 5
        val duplicateTypeFrequency = 0.2 // 20% chance of duplicate types
        
        val components = createLargeProviderComponents(componentCount, providersPerComponent, duplicateTypeFrequency)
        
        val startTime = System.currentTimeMillis()
        val issues = advancedDetector.detectEnhancedAmbiguousProviders(components)
        val detectionTime = System.currentTimeMillis() - startTime
        
        // Performance assertion
        assertTrue("Ambiguous provider detection should complete within 1 second", detectionTime < 1000)
        
        val totalProviders = componentCount * providersPerComponent
        println("Performance Results - Large Scale Ambiguous Provider Detection:")
        println("  Components: $componentCount")
        println("  Total Providers: $totalProviders")
        println("  Detection Time: ${detectionTime}ms")
        println("  Ambiguous Provider Issues: ${issues.size}")
        println("  Performance: ${totalProviders.toDouble() / detectionTime} providers/ms")
        
        // Verify some issues are found due to duplicates
        assertTrue("Should find ambiguous provider issues", issues.isNotEmpty())
    }
    
    @Test
    fun testLargeScaleUnresolvedDependencies() {
        val consumerCount = 300
        val dependenciesPerConsumer = 10
        val providerCount = 200
        val resolutionRate = 0.7 // 70% of dependencies will be resolved
        
        val consumers = createLargeConsumerComponents(consumerCount, dependenciesPerConsumer)
        val providers = createProviderComponentsForResolution(providerCount, consumers, resolutionRate)
        val allComponents = consumers + providers
        
        val startTime = System.currentTimeMillis()
        val issues = advancedDetector.detectImprovedUnresolvedDependencies(allComponents)
        val detectionTime = System.currentTimeMillis() - startTime
        
        val totalDependencies = consumerCount * dependenciesPerConsumer
        
        // Performance assertion
        assertTrue("Unresolved dependency detection should complete within 1.5 seconds", detectionTime < 1500)
        
        println("Performance Results - Large Scale Unresolved Dependencies:")
        println("  Consumer Components: $consumerCount")
        println("  Provider Components: $providerCount")
        println("  Total Dependencies: $totalDependencies")
        println("  Expected Resolution Rate: ${resolutionRate * 100}%")
        println("  Detection Time: ${detectionTime}ms")
        println("  Unresolved Issues: ${issues.size}")
        println("  Performance: ${totalDependencies.toDouble() / detectionTime} dependencies/ms")
        
        // Verify expected number of unresolved issues
        val expectedUnresolved = (totalDependencies * (1 - resolutionRate)).toInt()
        val tolerance = (expectedUnresolved * 0.2).toInt() // 20% tolerance
        assertTrue("Should find approximately $expectedUnresolved unresolved dependencies (±$tolerance)", 
                  issues.size in (expectedUnresolved - tolerance)..(expectedUnresolved + tolerance))
    }
    
    @Test
    fun testMassiveSingletonViolationDetection() {
        val componentCount = 400
        val singletonTypesCount = 50
        val violationRate = 0.1 // 10% of singleton types will have violations
        
        val components = createComponentsWithSingletonViolations(componentCount, singletonTypesCount, violationRate)
        
        val startTime = System.currentTimeMillis()
        val issues = advancedDetector.detectAdvancedSingletonViolations(components)
        val detectionTime = System.currentTimeMillis() - startTime
        
        // Performance assertion
        assertTrue("Singleton violation detection should complete within 800ms", detectionTime < 800)
        
        println("Performance Results - Massive Singleton Violation Detection:")
        println("  Components: $componentCount")
        println("  Singleton Types: $singletonTypesCount")
        println("  Expected Violation Rate: ${violationRate * 100}%")
        println("  Detection Time: ${detectionTime}ms")
        println("  Violations Found: ${issues.size}")
        
        // Verify expected violations
        val expectedViolations = (singletonTypesCount * violationRate).toInt()
        assertTrue("Should find approximately $expectedViolations violations", 
                  issues.size >= expectedViolations)
    }
    
    @Test
    fun testQualifierMismatchPerformanceWithManyQualifiers() {
        val componentCount = 200
        val qualifiersPerType = 20
        val typeCount = 30
        val mismatchRate = 0.15 // 15% mismatch rate
        
        val components = createComponentsWithQualifierMismatches(componentCount, qualifiersPerType, typeCount, mismatchRate)
        
        val startTime = System.currentTimeMillis()
        val issues = advancedDetector.detectEnhancedNamedQualifierMismatches(components)
        val detectionTime = System.currentTimeMillis() - startTime
        
        val totalQualifiers = typeCount * qualifiersPerType
        
        // Performance assertion
        assertTrue("Qualifier mismatch detection should complete within 600ms", detectionTime < 600)
        
        println("Performance Results - Qualifier Mismatch with Many Qualifiers:")
        println("  Components: $componentCount")
        println("  Total Qualifiers: $totalQualifiers")
        println("  Types: $typeCount")
        println("  Expected Mismatch Rate: ${mismatchRate * 100}%")
        println("  Detection Time: ${detectionTime}ms")
        println("  Mismatches Found: ${issues.size}")
        println("  Performance: ${totalQualifiers.toDouble() / detectionTime} qualifiers/ms")
    }
    
    @Test
    fun testComprehensivePerformanceBenchmark() {
        // Comprehensive test with all issue types
        val componentCount = 200
        val components = createComprehensiveTestData(componentCount)
        val graph = createDependencyGraph(components)
        
        println("Comprehensive Performance Benchmark:")
        println("  Total Components: $componentCount")
        
        // Test each detection type individually
        val results = mutableMapOf<String, Long>()
        
        // Circular dependencies
        val cycleStart = System.currentTimeMillis()
        val cycleIssues = advancedDetector.detectAdvancedCircularDependencies(components, graph)
        results["Circular Dependencies"] = System.currentTimeMillis() - cycleStart
        
        // Ambiguous providers
        val ambiguousStart = System.currentTimeMillis()
        val ambiguousIssues = advancedDetector.detectEnhancedAmbiguousProviders(components)
        results["Ambiguous Providers"] = System.currentTimeMillis() - ambiguousStart
        
        // Unresolved dependencies
        val unresolvedStart = System.currentTimeMillis()
        val unresolvedIssues = advancedDetector.detectImprovedUnresolvedDependencies(components)
        results["Unresolved Dependencies"] = System.currentTimeMillis() - unresolvedStart
        
        // Singleton violations
        val singletonStart = System.currentTimeMillis()
        val singletonIssues = advancedDetector.detectAdvancedSingletonViolations(components)
        results["Singleton Violations"] = System.currentTimeMillis() - singletonStart
        
        // Qualifier mismatches
        val qualifierStart = System.currentTimeMillis()
        val qualifierIssues = advancedDetector.detectEnhancedNamedQualifierMismatches(components)
        results["Qualifier Mismatches"] = System.currentTimeMillis() - qualifierStart
        
        val totalTime = results.values.sum()
        val totalIssues = cycleIssues.size + ambiguousIssues.size + unresolvedIssues.size + 
                         singletonIssues.size + qualifierIssues.size
        
        println("  Results:")
        results.forEach { (type, time) ->
            println("    $type: ${time}ms")
        }
        println("  Total Detection Time: ${totalTime}ms")
        println("  Total Issues Found: $totalIssues")
        
        // Performance assertions
        assertTrue("Total detection time should be under 2 seconds", totalTime < 2000)
        assertTrue("Each detection type should complete quickly", 
                  results.values.all { it < 500 })
        
        // Memory usage check (basic)
        System.gc()
        val usedMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024
        println("  Memory Used: ${usedMemory}MB")
        assertTrue("Memory usage should be reasonable (< 200MB)", usedMemory < 200)
    }
    
    @Test
    fun testMemoryEfficiencyWithLargeGraphs() {
        // Test memory efficiency with very large graphs
        val componentCount = 2000
        
        val initialMemory = getCurrentMemoryUsage()
        
        val components = createLargeComponentGraph(componentCount)
        val graph = createDependencyGraph(components)
        
        val afterCreationMemory = getCurrentMemoryUsage()
        
        // Run all detections
        advancedDetector.detectAdvancedCircularDependencies(components, graph)
        advancedDetector.detectEnhancedAmbiguousProviders(components)
        advancedDetector.detectImprovedUnresolvedDependencies(components)
        
        val afterDetectionMemory = getCurrentMemoryUsage()
        
        // Clear caches
        advancedDetector.clearCaches()
        System.gc()
        Thread.sleep(100) // Give GC time to work
        
        val afterCleanupMemory = getCurrentMemoryUsage()
        
        println("Memory Efficiency Test Results:")
        println("  Component Count: $componentCount")
        println("  Initial Memory: ${initialMemory}MB")
        println("  After Creation: ${afterCreationMemory}MB")
        println("  After Detection: ${afterDetectionMemory}MB")
        println("  After Cleanup: ${afterCleanupMemory}MB")
        println("  Creation Overhead: ${afterCreationMemory - initialMemory}MB")
        println("  Detection Overhead: ${afterDetectionMemory - afterCreationMemory}MB")
        println("  Memory Leak: ${afterCleanupMemory - initialMemory}MB")
        
        // Memory assertions
        assertTrue("Memory creation overhead should be reasonable (< 100MB)", 
                  afterCreationMemory - initialMemory < 100)
        assertTrue("Detection overhead should be minimal (< 50MB)", 
                  afterDetectionMemory - afterCreationMemory < 50)
        assertTrue("Memory leak should be minimal (< 20MB)", 
                  afterCleanupMemory - initialMemory < 20)
    }
    
    // Helper methods for creating test data
    
    private fun createLargeComponentGraph(
        componentCount: Int, 
        cycleFrequency: Double = 0.05
    ): List<KnitComponent> {
        val components = mutableListOf<KnitComponent>()
        val random = Random(42) // Fixed seed for reproducible results
        
        for (i in 0 until componentCount) {
            val dependencies = mutableListOf<KnitDependency>()
            val providers = mutableListOf<KnitProvider>()
            
            // Add some dependencies (but not too many to avoid full connectivity)
            val dependencyCount = random.nextInt(1, 4)
            repeat(dependencyCount) { depIndex ->
                val targetIndex = random.nextInt(componentCount)
                if (targetIndex != i) {
                    dependencies.add(KnitDependency(
                        propertyName = "dep$depIndex",
                        targetType = "Component$targetIndex",
                        isNamed = false,
                        namedQualifier = null,
                        isSingleton = false,
                        isFactory = false,
                        isLoadable = false
                    ))
                }
            }
            
            // Add some providers
            val providerCount = random.nextInt(1, 3)
            repeat(providerCount) { provIndex ->
                providers.add(KnitProvider(
                    methodName = "provide$provIndex",
                    returnType = "Service${i}_$provIndex",
                    providesType = null,
                    isNamed = false,
                    namedQualifier = null,
                    isSingleton = random.nextBoolean(),
                    isIntoSet = false,
                    isIntoList = false,
                    isIntoMap = false
                ))
            }
            
            // Occasionally add cycles
            if (random.nextDouble() < cycleFrequency && i > 0) {
                val backRefIndex = random.nextInt(i)
                dependencies.add(KnitDependency(
                    propertyName = "backRef",
                    targetType = "Component$backRefIndex",
                    isNamed = false,
                    namedQualifier = null,
                    isSingleton = false,
                    isFactory = false,
                    isLoadable = false
                ))
            }
            
            components.add(KnitComponent(
                className = "Component$i",
                packageName = "com.test.large",
                type = ComponentType.COMPONENT,
                dependencies = dependencies,
                providers = providers,
                sourceFile = "Component$i.kt"
            ))
        }
        
        return components
    }
    
    private fun createLargeProviderComponents(
        componentCount: Int,
        providersPerComponent: Int,
        duplicateTypeFrequency: Double
    ): List<KnitComponent> {
        val components = mutableListOf<KnitComponent>()
        val random = Random(42)
        
        val commonTypes = listOf("UserService", "DatabaseService", "CacheService", "EmailService", "LogService")
        
        for (i in 0 until componentCount) {
            val providers = mutableListOf<KnitProvider>()
            
            repeat(providersPerComponent) { provIndex ->
                val returnType = if (random.nextDouble() < duplicateTypeFrequency) {
                    commonTypes.random(random)
                } else {
                    "Service${i}_$provIndex"
                }
                
                providers.add(KnitProvider(
                    methodName = "provide$provIndex",
                    returnType = returnType,
                    providesType = null,
                    isNamed = random.nextBoolean(),
                    namedQualifier = if (random.nextBoolean()) "qualifier$provIndex" else null,
                    isSingleton = random.nextBoolean(),
                    isIntoSet = false,
                    isIntoList = false,
                    isIntoMap = false
                ))
            }
            
            components.add(KnitComponent(
                className = "Provider$i",
                packageName = "com.test.providers",
                type = ComponentType.PROVIDER,
                dependencies = emptyList(),
                providers = providers,
                sourceFile = "Provider$i.kt"
            ))
        }
        
        return components
    }
    
    private fun createLargeConsumerComponents(
        consumerCount: Int,
        dependenciesPerConsumer: Int
    ): List<KnitComponent> {
        val components = mutableListOf<KnitComponent>()
        
        for (i in 0 until consumerCount) {
            val dependencies = mutableListOf<KnitDependency>()
            
            repeat(dependenciesPerConsumer) { depIndex ->
                // Create mostly unique dependencies with minimal overlap
                // This should create close to 3000 unique dependencies
                // When we resolve 70% (2100), we should have ~900 unresolved
                dependencies.add(KnitDependency(
                    propertyName = "dependency$depIndex",
                    targetType = "RequiredService${i}_${depIndex}",
                    isNamed = false, // Simplify by removing named qualifiers to avoid matching issues
                    namedQualifier = null,
                    isSingleton = false,
                    isFactory = false,
                    isLoadable = false
                ))
            }
            
            components.add(KnitComponent(
                className = "Consumer$i",
                packageName = "com.test.consumers",
                type = ComponentType.CONSUMER,
                dependencies = dependencies,
                providers = emptyList(),
                sourceFile = "Consumer$i.kt"
            ))
        }
        
        return components
    }
    
    private fun createProviderComponentsForResolution(
        providerCount: Int,
        consumers: List<KnitComponent>,
        resolutionRate: Double
    ): List<KnitComponent> {
        val allDependencies = consumers.flatMap { it.dependencies }
        val random = Random(42)
        
        val providersToCreate = (allDependencies.size * resolutionRate).toInt()
        val selectedDependencies = allDependencies.shuffled(random).take(providersToCreate)
        
        // Distribute dependencies evenly across the specified number of provider components
        val components = mutableListOf<KnitComponent>()
        val providersPerComponent = if (selectedDependencies.isEmpty()) 0 else 
            (selectedDependencies.size + providerCount - 1) / providerCount // Ceiling division
        
        for (componentIndex in 0 until providerCount) {
            val startIndex = componentIndex * providersPerComponent
            val endIndex = minOf((componentIndex + 1) * providersPerComponent, selectedDependencies.size)
            
            if (startIndex < selectedDependencies.size) {
                val dependencyChunk = selectedDependencies.subList(startIndex, endIndex)
                val providers = dependencyChunk.map { dependency ->
                    KnitProvider(
                        methodName = "provide${dependency.propertyName}",
                        returnType = dependency.targetType,
                        providesType = null,
                        isNamed = dependency.isNamed,
                        namedQualifier = dependency.namedQualifier,
                        isSingleton = dependency.isSingleton,
                        isIntoSet = false,
                        isIntoList = false,
                        isIntoMap = false
                    )
                }
                
                if (providers.isNotEmpty()) {
                    components.add(KnitComponent(
                        className = "ResolverProvider$componentIndex",
                        packageName = "com.test.resolvers",
                        type = ComponentType.PROVIDER,
                        dependencies = emptyList(),
                        providers = providers,
                        sourceFile = "ResolverProvider$componentIndex.kt"
                    ))
                }
            }
        }
        
        return components
    }
    
    private fun createComponentsWithSingletonViolations(
        componentCount: Int,
        singletonTypesCount: Int,
        violationRate: Double
    ): List<KnitComponent> {
        val components = mutableListOf<KnitComponent>()
        val random = Random(42)
        val violatingTypes = (0 until singletonTypesCount).shuffled(random)
            .take((singletonTypesCount * violationRate).toInt())
        
        for (i in 0 until componentCount) {
            val providers = mutableListOf<KnitProvider>()
            val typeIndex = i % singletonTypesCount
            
            val isViolating = typeIndex in violatingTypes
            val providerCount = if (isViolating) random.nextInt(2, 4) else 1
            
            repeat(providerCount) { provIndex ->
                providers.add(KnitProvider(
                    methodName = "provideSingleton${typeIndex}_$provIndex",
                    returnType = "SingletonService$typeIndex",
                    providesType = null,
                    isNamed = false,
                    namedQualifier = null,
                    isSingleton = true,
                    isIntoSet = false,
                    isIntoList = false,
                    isIntoMap = false
                ))
            }
            
            if (providers.isNotEmpty()) {
                components.add(KnitComponent(
                    className = "SingletonProvider$i",
                    packageName = "com.test.singletons",
                    type = ComponentType.PROVIDER,
                    dependencies = emptyList(),
                    providers = providers,
                    sourceFile = "SingletonProvider$i.kt"
                ))
            }
        }
        
        return components
    }
    
    private fun createComponentsWithQualifierMismatches(
        componentCount: Int,
        qualifiersPerType: Int,
        typeCount: Int,
        mismatchRate: Double
    ): List<KnitComponent> {
        val components = mutableListOf<KnitComponent>()
        val random = Random(42)
        
        // Create providers with known qualifiers
        val availableQualifiers = (0 until qualifiersPerType).map { "qualifier$it" }
        val providerComponent = KnitComponent(
            className = "QualifierProvider",
            packageName = "com.test.qualifiers",
            type = ComponentType.PROVIDER,
            dependencies = emptyList(),
            providers = (0 until typeCount).flatMap { typeIndex ->
                availableQualifiers.map { qualifier ->
                    KnitProvider(
                        methodName = "provideType${typeIndex}_$qualifier",
                        returnType = "Type$typeIndex",
                        providesType = null,
                        isNamed = true,
                        namedQualifier = qualifier,
                        isSingleton = false,
                        isIntoSet = false,
                        isIntoList = false,
                        isIntoMap = false
                    )
                }
            },
            sourceFile = "QualifierProvider.kt"
        )
        components.add(providerComponent)
        
        // Create consumers with some mismatched qualifiers
        for (i in 0 until componentCount - 1) {
            val dependencies = mutableListOf<KnitDependency>()
            val typeIndex = i % typeCount
            
            val shouldMismatch = random.nextDouble() < mismatchRate
            val qualifier = if (shouldMismatch) {
                "nonexistent_qualifier_$i" // Guaranteed mismatch
            } else {
                availableQualifiers.random(random)
            }
            
            dependencies.add(KnitDependency(
                propertyName = "qualifiedDep",
                targetType = "Type$typeIndex",
                isNamed = true,
                namedQualifier = qualifier,
                isSingleton = false,
                isFactory = false,
                isLoadable = false
            ))
            
            components.add(KnitComponent(
                className = "QualifierConsumer$i",
                packageName = "com.test.consumers",
                type = ComponentType.CONSUMER,
                dependencies = dependencies,
                providers = emptyList(),
                sourceFile = "QualifierConsumer$i.kt"
            ))
        }
        
        return components
    }
    
    private fun createComprehensiveTestData(componentCount: Int): List<KnitComponent> {
        val components = mutableListOf<KnitComponent>()
        val random = Random(42)
        
        // Mix different types of components to test all detection algorithms
        for (i in 0 until componentCount) {
            when (i % 4) {
                0 -> components.add(createCyclicComponent(i, componentCount, random))
                1 -> components.add(createProviderComponent(i, random))
                2 -> components.add(createConsumerComponent(i, random))
                3 -> components.add(createMixedComponent(i, random))
            }
        }
        
        return components
    }
    
    private fun createCyclicComponent(index: Int, totalCount: Int, random: Random): KnitComponent {
        val nextIndex = (index + random.nextInt(1, 5)) % totalCount
        return KnitComponent(
            className = "CyclicComponent$index",
            packageName = "com.test.cyclic",
            type = ComponentType.COMPONENT,
            dependencies = listOf(KnitDependency(
                propertyName = "nextComponent",
                targetType = "CyclicComponent$nextIndex",
                isNamed = false,
                namedQualifier = null,
                isSingleton = false,
                isFactory = false,
                isLoadable = false
            )),
            providers = emptyList(),
            sourceFile = "CyclicComponent$index.kt"
        )
    }
    
    private fun createProviderComponent(index: Int, random: Random): KnitComponent {
        return KnitComponent(
            className = "ProviderComponent$index",
            packageName = "com.test.providers",
            type = ComponentType.PROVIDER,
            dependencies = emptyList(),
            providers = listOf(KnitProvider(
                methodName = "provideService",
                returnType = if (random.nextBoolean()) "CommonService" else "Service$index",
                providesType = null,
                isNamed = random.nextBoolean(),
                namedQualifier = if (random.nextBoolean()) "qualifier$index" else null,
                isSingleton = random.nextBoolean(),
                isIntoSet = false,
                isIntoList = false,
                isIntoMap = false
            )),
            sourceFile = "ProviderComponent$index.kt"
        )
    }
    
    private fun createConsumerComponent(index: Int, random: Random): KnitComponent {
        return KnitComponent(
            className = "ConsumerComponent$index",
            packageName = "com.test.consumers",
            type = ComponentType.CONSUMER,
            dependencies = listOf(KnitDependency(
                propertyName = "requiredService",
                targetType = if (random.nextBoolean()) "MissingService" else "Service$index",
                isNamed = random.nextBoolean(),
                namedQualifier = if (random.nextBoolean()) "wrong_qualifier" else null,
                isSingleton = random.nextBoolean(),
                isFactory = false,
                isLoadable = false
            )),
            providers = emptyList(),
            sourceFile = "ConsumerComponent$index.kt"
        )
    }
    
    private fun createMixedComponent(index: Int, random: Random): KnitComponent {
        val dependencies = if (random.nextBoolean()) {
            listOf(KnitDependency(
                propertyName = "mixedDep",
                targetType = "MixedService$index",
                isNamed = false,
                namedQualifier = null,
                isSingleton = true,
                isFactory = false,
                isLoadable = false
            ))
        } else emptyList()
        
        val providers = if (random.nextBoolean()) {
            listOf(KnitProvider(
                methodName = "provideMixedService",
                returnType = "MixedService$index",
                providesType = null,
                isNamed = false,
                namedQualifier = null,
                isSingleton = false, // Potential singleton violation
                isIntoSet = false,
                isIntoList = false,
                isIntoMap = false
            ))
        } else emptyList()
        
        return KnitComponent(
            className = "MixedComponent$index",
            packageName = "com.test.mixed",
            type = ComponentType.COMPOSITE,
            dependencies = dependencies,
            providers = providers,
            sourceFile = "MixedComponent$index.kt"
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
    
    private fun getCurrentMemoryUsage(): Long {
        System.gc()
        Thread.sleep(50) // Give GC time to work
        return (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024
    }
}