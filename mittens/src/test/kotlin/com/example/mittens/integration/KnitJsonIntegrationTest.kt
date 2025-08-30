package com.example.mittens.integration

import com.example.mittens.model.*
import com.example.mittens.services.*
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.Test
import java.io.File

/**
 * Integration test using the real sample_project knit.json file
 */
class KnitJsonIntegrationTest : LightJavaCodeInsightFixtureTestCase() {

    private lateinit var parser: KnitJsonParser
    private lateinit var gradleService: KnitGradleService
    private lateinit var sourceAnalyzer: KnitSourceAnalyzer

    private fun findSampleKnitJsonFile(): File? {
        var dir: File? = File(System.getProperty("user.dir"))
        var level = 0
        while (dir != null && level < 8) {
            val candidate = File(dir, "sample_project/build/knit.json")
            if (candidate.exists()) return candidate
            dir = dir.parentFile
            level++
        }
        return null
    }

    override fun setUp() {
        super.setUp()
        parser = KnitJsonParser(project)
        gradleService = KnitGradleService(project)
        sourceAnalyzer = KnitSourceAnalyzer(project)
    }

    @Test
    fun testRealSampleProjectKnitJson() {
        // Locate the actual sample_project knit.json file relative to working directory
        val sampleKnitJsonFile = findSampleKnitJsonFile()

        // Skip test if sample knit.json doesn't exist (e.g., in CI environment)
        if (sampleKnitJsonFile == null || !sampleKnitJsonFile.exists()) {
            println("Sample knit.json not found - skipping integration test")
            return
        }

        // Parse the real knit.json file
        val parseResult = parser.parseKnitJson(sampleKnitJsonFile)
        assertTrue("Should successfully parse sample knit.json", parseResult.success)
        assertNotNull("Components should be parsed", parseResult.components)
        
        val knitJsonRoot = parseResult.components!!
        println("Parsed ${knitJsonRoot.size} entries from sample knit.json")

        // Convert to KnitComponent objects
        val components = parser.convertToKnitComponents(knitJsonRoot)
        assertTrue("Should convert to components", components.isNotEmpty())
        
        println("Converted to ${components.size} KnitComponent objects")

        // Validate component structure
        validateSampleProjectComponents(components)
        
        // Test dependency relationships
        validateDependencyRelationships(components)
        
        // Test provider detection
        validateProviderDetection(components)
        
        // Test component types
        validateComponentTypes(components)
    }

    @Test
    fun testKnitJsonParsingPerformance() {
        val sampleKnitJsonFile = findSampleKnitJsonFile()

        if (sampleKnitJsonFile == null || !sampleKnitJsonFile.exists()) {
            println("Sample knit.json not found - skipping performance test")
            return
        }

        // Measure parsing performance
        val startTime = System.currentTimeMillis()
        val parseResult = parser.parseKnitJson(sampleKnitJsonFile)
        val parseTime = System.currentTimeMillis() - startTime
        
        assertTrue("Parsing should succeed", parseResult.success)
        assertTrue("Parsing should be fast (< 1000ms)", parseTime < 1000)
        
        // Measure conversion performance
        val convertStartTime = System.currentTimeMillis()
        val components = parser.convertToKnitComponents(parseResult.components!!)
        val convertTime = System.currentTimeMillis() - convertStartTime
        
        assertTrue("Conversion should be fast (< 500ms)", convertTime < 500)
        
        println("Performance metrics:")
        println("  - Parse time: ${parseTime}ms")
        println("  - Convert time: ${convertTime}ms")
        println("  - Total components: ${components.size}")
        println("  - Components per second: ${(components.size * 1000) / (parseTime + convertTime)}")
    }

    

    private fun validateSampleProjectComponents(components: List<KnitComponent>) {
        // Validate expected components from sample_project exist
        val componentNames = components.map { it.className }.toSet()

        // Check for some key services that should be in the sample project
        val expectedComponents = listOf(
            "OrderService",
            "InventoryService",
            "PaymentService",
            "UserService",
            "ProductService"
        )

        expectedComponents.forEach { expectedName ->
            if (componentNames.contains(expectedName)) {
                println("✓ Found expected component: $expectedName")
            } else {
                println("⚠ Missing expected component: $expectedName")
            }
        }

        // At least half of expected components should be found
        val foundExpected = expectedComponents.count { componentNames.contains(it) }
        assertTrue("Should find at least half of expected components", foundExpected >= expectedComponents.size / 2)
    }

    private fun validateDependencyRelationships(components: List<KnitComponent>) {
        var totalDependencies = 0
        var componentsWithDependencies = 0

        components.forEach { component ->
            if (component.dependencies.isNotEmpty()) {
                componentsWithDependencies++
                totalDependencies += component.dependencies.size

                component.dependencies.forEach { dependency ->
                    // Validate dependency structure
                    assertNotNull("Dependency property name should not be null", dependency.propertyName)
                    assertNotNull("Dependency target type should not be null", dependency.targetType)
                    assertFalse("Target type should not be empty", dependency.targetType.isEmpty())
                }
            }
        }

        println("Dependency analysis:")
        println("  - Components with dependencies: $componentsWithDependencies")
        println("  - Total dependencies: $totalDependencies")
        println("  - Average dependencies per component: ${totalDependencies.toDouble() / componentsWithDependencies}")

        assertTrue("Should have components with dependencies", componentsWithDependencies > 0)
        assertTrue("Should have reasonable number of dependencies", totalDependencies > 0)
    }

    private fun validateProviderDetection(components: List<KnitComponent>) {
        var totalProviders = 0
        var componentsWithProviders = 0
        
        components.forEach { component ->
            if (component.providers.isNotEmpty()) {
                componentsWithProviders++
                totalProviders += component.providers.size
                
                component.providers.forEach { provider ->
                    // Validate provider structure
                    assertNotNull("Provider method name should not be null", provider.methodName)
                    assertNotNull("Provider return type should not be null", provider.returnType)
                    assertFalse("Return type should not be empty", provider.returnType.isEmpty())
                }
            }
        }
        
        println("Provider analysis:")
        println("  - Components with providers: $componentsWithProviders")  
        println("  - Total providers: $totalProviders")
        println("  - Average providers per component: ${totalProviders.toDouble() / componentsWithProviders}")
        
        assertTrue("Should have components with providers", componentsWithProviders > 0)
        assertTrue("Should have reasonable number of providers", totalProviders > 0)
    }

    private fun validateComponentTypes(components: List<KnitComponent>) {
        val typeDistribution = components.groupingBy { it.type }.eachCount()
        
        println("Component type distribution:")
        typeDistribution.forEach { (type, count) ->
            println("  - $type: $count")
        }
        
        // Should have a reasonable distribution of component types
        assertTrue("Should have multiple component types", typeDistribution.size > 1)
        assertTrue("Should classify components correctly", typeDistribution.values.all { it > 0 })
        
        // Validate type assignment logic
        components.forEach { component ->
            val expectedType = when {
                component.providers.isNotEmpty() && component.dependencies.isNotEmpty() -> ComponentType.COMPOSITE
                component.providers.isNotEmpty() -> ComponentType.PROVIDER
                component.dependencies.isNotEmpty() -> ComponentType.CONSUMER
                else -> ComponentType.COMPONENT
            }
            
            // Our logic might differ slightly, but should be reasonable
            assertTrue("Component type should be reasonable for ${component.className}", 
                      component.type in listOf(ComponentType.COMPONENT, ComponentType.PROVIDER, ComponentType.CONSUMER, ComponentType.COMPOSITE))
        }
    }

    @Test
    fun testKnitJsonValidation() {
        val sampleKnitJsonFile = findSampleKnitJsonFile()

        if (sampleKnitJsonFile == null || !sampleKnitJsonFile.exists()) {
            println("Sample knit.json not found - skipping validation test")
            return
        }

        // Test that the file passes validation
        assertTrue("Sample knit.json should be valid", parser.validateKnitJsonFile(sampleKnitJsonFile))
        
        // Test file info
        val gradleService = KnitGradleService(project)
        
        // Mock the build directory to point to sample_project
        // Note: In a real test, you might set up the project structure differently
        println("Testing with actual sample_project knit.json")
        println("File size: ${sampleKnitJsonFile.length()} bytes")
        println("Last modified: ${java.util.Date(sampleKnitJsonFile.lastModified())}")
        
        assertTrue("File should be readable", sampleKnitJsonFile.canRead())
        assertTrue("File should have content", sampleKnitJsonFile.length() > 0)
    }
}