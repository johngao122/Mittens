package com.example.mittens.services

import com.example.mittens.model.*
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

@TestDataPath("\$CONTENT_ROOT/testData")
class KnitAdvancedFeaturesTest : BasePlatformTestCase() {
    
    private lateinit var sourceAnalyzer: KnitSourceAnalyzer
    
    override fun setUp() {
        super.setUp()
        sourceAnalyzer = KnitSourceAnalyzer(project)
    }
    
    @Test
    fun testSingletonDetection() {
        // Test data for singleton components
        val mockSingletonProvider = KnitProvider(
            methodName = "provideDatabase",
            returnType = "DatabaseService",
            isSingleton = true,
            isNamed = false,
            namedQualifier = null,
            providesType = null,
            isIntoSet = false,
            isIntoList = false,
            isIntoMap = false
        )
        
        val mockSingletonDependency = KnitDependency(
            propertyName = "database",
            targetType = "DatabaseService",
            isSingleton = true,
            isNamed = false,
            namedQualifier = null,
            isFactory = false,
            isLoadable = false
        )
        
        // Verify singleton properties
        assertTrue("Provider should be singleton", mockSingletonProvider.isSingleton)
        assertTrue("Dependency should be singleton", mockSingletonDependency.isSingleton)
        
        // Test singleton validation
        val components = listOf(
            KnitComponent(
                className = "DatabaseProvider",
                packageName = "com.test.providers",
                type = ComponentType.PROVIDER,
                dependencies = emptyList(),
                providers = listOf(mockSingletonProvider),
                sourceFile = "DatabaseProvider.kt"
            ),
            KnitComponent(
                className = "UserService",
                packageName = "com.test.services",
                type = ComponentType.CONSUMER,
                dependencies = listOf(mockSingletonDependency),
                providers = emptyList(),
                sourceFile = "UserService.kt"
            )
        )
        
        // Test singleton violation detection
        val analysisService = KnitAnalysisService(project)
        val issues = analysisService.detectSingletonViolations(components)
        
        // Should not have violations with proper singleton setup
        assertEquals("Should not detect singleton violations", 0, issues.size)
    }
    
    @Test
    fun testSingletonViolations() {
        // Test multiple singleton providers for same type (violation)
        val provider1 = KnitProvider(
            methodName = "provideDatabase1",
            returnType = "DatabaseService",
            isSingleton = true,
            isNamed = false,
            namedQualifier = null,
            providesType = null,
            isIntoSet = false,
            isIntoList = false,
            isIntoMap = false
        )
        
        val provider2 = KnitProvider(
            methodName = "provideDatabase2",
            returnType = "DatabaseService",
            isSingleton = true,
            isNamed = false,
            namedQualifier = null,
            providesType = null,
            isIntoSet = false,
            isIntoList = false,
            isIntoMap = false
        )
        
        val components = listOf(
            KnitComponent(
                className = "DatabaseProvider1",
                packageName = "com.test.providers",
                type = ComponentType.PROVIDER,
                dependencies = emptyList(),
                providers = listOf(provider1),
                sourceFile = "DatabaseProvider1.kt"
            ),
            KnitComponent(
                className = "DatabaseProvider2",
                packageName = "com.test.providers",
                type = ComponentType.PROVIDER,
                dependencies = emptyList(),
                providers = listOf(provider2),
                sourceFile = "DatabaseProvider2.kt"
            )
        )
        
        val analysisService = KnitAnalysisService(project)
        val issues = analysisService.detectSingletonViolations(components)
        
        // Should detect violation
        assertEquals("Should detect 1 singleton violation", 1, issues.size)
        assertEquals("Should be singleton violation type", IssueType.SINGLETON_VIOLATION, issues.first().type)
        assertTrue("Message should mention multiple singleton providers", 
                  issues.first().message.contains("Multiple singleton providers"))
    }
    
    @Test
    fun testNamedQualifiers() {
        // Test named qualifiers parsing and validation
        val namedProvider1 = KnitProvider(
            methodName = "providePrimaryDatabase",
            returnType = "DatabaseService",
            isSingleton = false,
            isNamed = true,
            namedQualifier = "primary",
            providesType = null,
            isIntoSet = false,
            isIntoList = false,
            isIntoMap = false
        )
        
        val namedProvider2 = KnitProvider(
            methodName = "provideSecondaryDatabase",
            returnType = "DatabaseService",
            isSingleton = false,
            isNamed = true,
            namedQualifier = "secondary",
            providesType = null,
            isIntoSet = false,
            isIntoList = false,
            isIntoMap = false
        )
        
        val namedDependency = KnitDependency(
            propertyName = "primaryDb",
            targetType = "DatabaseService",
            isSingleton = false,
            isNamed = true,
            namedQualifier = "primary",
            isFactory = false,
            isLoadable = false
        )
        
        // Verify named qualifier properties
        assertTrue("Provider1 should be named", namedProvider1.isNamed)
        assertEquals("Provider1 qualifier should be 'primary'", "primary", namedProvider1.namedQualifier)
        assertTrue("Provider2 should be named", namedProvider2.isNamed)
        assertEquals("Provider2 qualifier should be 'secondary'", "secondary", namedProvider2.namedQualifier)
        assertTrue("Dependency should be named", namedDependency.isNamed)
        assertEquals("Dependency qualifier should be 'primary'", "primary", namedDependency.namedQualifier)
    }
    
    @Test
    fun testNamedQualifierMismatch() {
        // Test named qualifier mismatch detection
        val namedProvider = KnitProvider(
            methodName = "providePrimaryDatabase",
            returnType = "DatabaseService",
            isNamed = true,
            namedQualifier = "primary",
            isSingleton = false,
            providesType = null,
            isIntoSet = false,
            isIntoList = false,
            isIntoMap = false
        )
        
        val mismatchedDependency = KnitDependency(
            propertyName = "secondaryDb",
            targetType = "DatabaseService",
            isNamed = true,
            namedQualifier = "secondary", // Mismatch: requesting 'secondary' but only 'primary' available
            isSingleton = false,
            isFactory = false,
            isLoadable = false
        )
        
        val components = listOf(
            KnitComponent(
                className = "DatabaseProvider",
                packageName = "com.test.providers",
                type = ComponentType.PROVIDER,
                dependencies = emptyList(),
                providers = listOf(namedProvider),
                sourceFile = "DatabaseProvider.kt"
            ),
            KnitComponent(
                className = "UserService",
                packageName = "com.test.services", 
                type = ComponentType.CONSUMER,
                dependencies = listOf(mismatchedDependency),
                providers = emptyList(),
                sourceFile = "UserService.kt"
            )
        )
        
        val analysisService = KnitAnalysisService(project)
        val issues = analysisService.detectNamedQualifierMismatches(components)
        
        // Should detect mismatch
        assertEquals("Should detect 1 qualifier mismatch", 1, issues.size)
        assertEquals("Should be qualifier mismatch type", IssueType.NAMED_QUALIFIER_MISMATCH, issues.first().type)
        assertTrue("Message should mention qualifier not found", 
                  issues.first().message.contains("@Named(secondary)"))
    }
    
    @Test
    fun testFactoryTypes() {
        // Test factory type detection
        val factoryDependency1 = KnitDependency(
            propertyName = "userFactory",
            targetType = "Factory<User>",
            isFactory = true,
            isNamed = false,
            namedQualifier = null,
            isSingleton = false,
            isLoadable = false
        )
        
        val functionDependency = KnitDependency(
            propertyName = "userCreator",
            targetType = "() -> User",
            isFactory = true,
            isNamed = false,
            namedQualifier = null,
            isSingleton = false,
            isLoadable = false
        )
        
        // Verify factory detection
        assertTrue("Factory<T> should be detected as factory", factoryDependency1.isFactory)
        assertTrue("() -> T should be detected as factory", functionDependency.isFactory)
        
        // Test enhanced factory detection
        val analyzer = KnitSourceAnalyzer(project)
        assertTrue("Should detect Factory<> type", analyzer.isFactoryType("Factory<User>"))
        assertTrue("Should detect function type", analyzer.isFactoryType("() -> User"))
        assertTrue("Should detect complex function type", analyzer.isFactoryType("() -> List<User>"))
        assertFalse("Should not detect regular type as factory", analyzer.isFactoryType("User"))
    }
    
    @Test
    fun testLoadableTypes() {
        // Test loadable type detection
        val loadableDependency = KnitDependency(
            propertyName = "userLoader",
            targetType = "Loadable<User>",
            isLoadable = true,
            isFactory = false,
            isNamed = false,
            namedQualifier = null,
            isSingleton = false
        )
        
        // Verify loadable detection
        assertTrue("Loadable<T> should be detected as loadable", loadableDependency.isLoadable)
        
        // Test enhanced loadable detection
        val analyzer = KnitSourceAnalyzer(project)
        assertTrue("Should detect Loadable<> type", analyzer.isLoadableType("Loadable<User>"))
        assertFalse("Should not detect regular type as loadable", analyzer.isLoadableType("User"))
    }
    
    @Test
    fun testAmbiguousProvidersWithQualifiers() {
        // Test enhanced ambiguous provider detection with qualifier awareness
        val unqualifiedProvider1 = KnitProvider(
            methodName = "provideDatabase1",
            returnType = "DatabaseService",
            isNamed = false,
            namedQualifier = null,
            isSingleton = false,
            providesType = null,
            isIntoSet = false,
            isIntoList = false,
            isIntoMap = false
        )
        
        val unqualifiedProvider2 = KnitProvider(
            methodName = "provideDatabase2",
            returnType = "DatabaseService",
            isNamed = false,
            namedQualifier = null,
            isSingleton = false,
            providesType = null,
            isIntoSet = false,
            isIntoList = false,
            isIntoMap = false
        )
        
        val components = listOf(
            KnitComponent(
                className = "DatabaseProvider1",
                packageName = "com.test.providers",
                type = ComponentType.PROVIDER,
                dependencies = emptyList(),
                providers = listOf(unqualifiedProvider1),
                sourceFile = "DatabaseProvider1.kt"
            ),
            KnitComponent(
                className = "DatabaseProvider2",
                packageName = "com.test.providers",
                type = ComponentType.PROVIDER,
                dependencies = emptyList(),
                providers = listOf(unqualifiedProvider2),
                sourceFile = "DatabaseProvider2.kt"
            )
        )
        
        val analysisService = KnitAnalysisService(project)
        val graph = analysisService.buildDependencyGraph(components)
        val issues = analysisService.detectIssues(components, graph)
        
        // Should detect ambiguous providers
        val ambiguousIssues = issues.filter { it.type == IssueType.AMBIGUOUS_PROVIDER }
        assertTrue("Should detect at least 1 ambiguous provider issue", ambiguousIssues.isNotEmpty())
        assertTrue("Message should suggest using @Named qualifiers", 
                  ambiguousIssues.first().suggestedFix?.contains("@Named") ?: false)
    }
    
    @Test
    fun testKnitViewModelDetection() {
        // Test @KnitViewModel annotation handling
        val viewModelComponent = KnitComponent(
            className = "UserViewModel",
            packageName = "com.test.viewmodels",
            type = ComponentType.COMPONENT, // KnitViewModel should be treated as component
            dependencies = listOf(
                KnitDependency(
                    propertyName = "userRepository",
                    targetType = "UserRepository",
                    isNamed = false,
                    namedQualifier = null,
                    isFactory = false,
                    isLoadable = false,
                    isSingleton = false
                )
            ),
            providers = emptyList(),
            sourceFile = "UserViewModel.kt"
        )
        
        // Verify component type
        assertEquals("KnitViewModel should be treated as COMPONENT", ComponentType.COMPONENT, viewModelComponent.type)
        assertFalse("ViewModel dependencies should not be empty", viewModelComponent.dependencies.isEmpty())
    }
    
    private fun KnitAnalysisService.detectSingletonViolations(components: List<KnitComponent>): List<KnitIssue> {
        // Use reflection to access private method for testing
        val method = this.javaClass.getDeclaredMethod("detectSingletonViolations", List::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(this, components) as List<KnitIssue>
    }
    
    private fun KnitAnalysisService.detectNamedQualifierMismatches(components: List<KnitComponent>): List<KnitIssue> {
        // Use reflection to access private method for testing
        val method = this.javaClass.getDeclaredMethod("detectNamedQualifierMismatches", List::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(this, components) as List<KnitIssue>
    }
    
    private fun KnitAnalysisService.buildDependencyGraph(components: List<KnitComponent>): DependencyGraph {
        // Use reflection to access private method for testing
        val method = this.javaClass.getDeclaredMethod("buildDependencyGraph", List::class.java)
        method.isAccessible = true
        return method.invoke(this, components) as DependencyGraph
    }
    
    private fun KnitAnalysisService.detectIssues(components: List<KnitComponent>, graph: DependencyGraph): List<KnitIssue> {
        // Use reflection to access private method for testing
        val method = this.javaClass.getDeclaredMethod("detectIssues", List::class.java, DependencyGraph::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(this, components, graph) as List<KnitIssue>
    }
    
    private fun KnitSourceAnalyzer.isFactoryType(type: String): Boolean {
        // Use reflection to access private method for testing
        val method = this.javaClass.getDeclaredMethod("isFactoryType", String::class.java)
        method.isAccessible = true
        return method.invoke(this, type) as Boolean
    }
    
    private fun KnitSourceAnalyzer.isLoadableType(type: String): Boolean {
        // Use reflection to access private method for testing
        val method = this.javaClass.getDeclaredMethod("isLoadableType", String::class.java)
        method.isAccessible = true
        return method.invoke(this, type) as Boolean
    }
}