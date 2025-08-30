package com.example.mittens.debug

import com.example.mittens.model.*
import com.example.mittens.services.AdvancedIssueDetector
import com.example.mittens.services.KnitJsonParser
import com.example.mittens.services.KnitAnalysisService
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.Test
import java.io.File

/**
 * Test to reproduce the false positive unresolved dependency issue using real knit.json data
 */
class KnitJsonDependencyIssueTest : LightJavaCodeInsightFixtureTestCase() {

    @Test
    fun testRealKnitJsonDependencyResolution() {
        // Find the sample project knit.json file
        val sampleKnitJsonFile = findSampleKnitJsonFile()
        
        if (sampleKnitJsonFile == null || !sampleKnitJsonFile.exists()) {
            println("Sample knit.json not found - skipping test")
            return
        }
        
        println("Testing with knit.json: ${sampleKnitJsonFile.absolutePath}")
        
        // Parse the real knit.json file
        val parser = KnitJsonParser(project)
        val parseResult = parser.parseKnitJson(sampleKnitJsonFile)
        
        assertTrue("Should successfully parse knit.json", parseResult.success)
        assertNotNull("Components should be parsed", parseResult.components)
        
        // Convert to KnitComponent objects
        val components = parser.convertToKnitComponents(parseResult.components!!)
        assertTrue("Should have components", components.isNotEmpty())
        
        println("Parsed ${components.size} components from knit.json")
        
        // Find specific components we know should work
        val auditService = components.find { it.className == "AuditService" }
        val notificationManager = components.find { it.className == "NotificationManager" }
        
        assertNotNull("Should find AuditService", auditService)
        assertNotNull("Should find NotificationManager", notificationManager)
        
        println("AuditService providers: ${auditService!!.providers.size}")
        auditService.providers.forEach { provider ->
            println("  - ${provider.methodName} -> ${provider.returnType} (providesType: ${provider.providesType})")
        }
        
        println("NotificationManager dependencies: ${notificationManager!!.dependencies.size}")
        notificationManager.dependencies.forEach { dependency ->
            println("  - ${dependency.propertyName}: ${dependency.targetType}")
        }
        
        // First detect circular dependencies (like the full analysis does)
        val detector = AdvancedIssueDetector(project)
        val analysisService = KnitAnalysisService(project)
        val dependencyGraph = analysisService.buildDependencyGraph(components)
        val circularIssues = detector.detectAdvancedCircularDependencies(components, dependencyGraph)
        
        // Build exclusion set from circular dependencies
        val excludedComponents = mutableSetOf<String>()
        circularIssues.forEach { issue ->
            if (issue.type == IssueType.CIRCULAR_DEPENDENCY) {
                val componentNames = issue.componentName.split(", ")
                componentNames.forEach { compName ->
                    excludedComponents.add(compName.trim())
                }
            }
        }
        
        println("Circular dependency issues: ${circularIssues.size}")
        circularIssues.forEach { issue ->
            println("  - ${issue.message}")
        }
        println("Excluded components: ${excludedComponents.size}")
        excludedComponents.forEach { excluded ->
            println("  - $excluded")
        }
        
        // Run dependency resolution with exclusions (like the full analysis does)
        val issues = detector.detectImprovedUnresolvedDependencies(components, excludedComponents)
        
        println("Total issues found: ${issues.size}")
        
        // Check specifically for AuditService dependency resolution
        val auditServiceIssues = issues.filter { 
            it.type == IssueType.UNRESOLVED_DEPENDENCY && 
            it.message.contains("AuditService")
        }
        
        println("AuditService-related unresolved dependency issues: ${auditServiceIssues.size}")
        auditServiceIssues.forEach { issue ->
            println("  - ${issue.componentName}: ${issue.message}")
        }
        
        // This should pass (no false positives), but currently fails
        if (auditServiceIssues.isNotEmpty()) {
            println("❌ FALSE POSITIVE DETECTED: AuditService should be resolvable but isn't")
            
            // Debug the provider index
            debugProviderIndex(components)
        } else {
            println("✅ AuditService dependency resolution working correctly")
        }
    }
    
    private fun debugProviderIndex(components: List<KnitComponent>) {
        println("\n=== DEBUGGING PROVIDER INDEX ===")
        
        val auditService = components.find { it.className == "AuditService" }!!
        val notificationManager = components.find { it.className == "NotificationManager" }!!
        
        println("AuditService component:")
        println("  Package: ${auditService.packageName}")
        println("  Class: ${auditService.className}")
        println("  Providers: ${auditService.providers.size}")
        
        auditService.providers.forEach { provider ->
            println("    Provider:")
            println("      methodName: '${provider.methodName}'")
            println("      returnType: '${provider.returnType}'")
            println("      providesType: '${provider.providesType}'")
            println("      isNamed: ${provider.isNamed}")
            println("      namedQualifier: '${provider.namedQualifier}'")
            
            // Check if this provider would be filtered out
            val detector = AdvancedIssueDetector(project)
            val isActive = try {
                // Use reflection to access private method for testing
                val method = detector.javaClass.getDeclaredMethod("isProviderActive", KnitComponent::class.java, KnitProvider::class.java)
                method.isAccessible = true
                method.invoke(detector, auditService, provider) as Boolean
            } catch (e: Exception) {
                println("      Could not check isProviderActive: ${e.message}")
                true
            }
            println("      isActive: $isActive")
        }
        
        println("\nNotificationManager dependencies:")
        notificationManager.dependencies.forEach { dependency ->
            println("    Dependency:")
            println("      propertyName: '${dependency.propertyName}'")
            println("      targetType: '${dependency.targetType}'")
            println("      isNamed: ${dependency.isNamed}")
            println("      namedQualifier: '${dependency.namedQualifier}'")
        }
    }
    
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
}
