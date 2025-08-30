package com.example.mittens.model

import org.junit.Test
import org.junit.Assert.*

class KnitIssueAdvancedDetectionTest {
    
    @Test
    fun testIssueInstancesForAdvancedCases() {
        // Circular dependency issue
        val circularDependencyIssue = KnitIssue(
            type = IssueType.CIRCULAR_DEPENDENCY,
            severity = Severity.ERROR,
            message = "Circular dependency detected: ServiceA -> ServiceB -> ServiceA",
            componentName = "com.example.ServiceA"
        )
        assertEquals(IssueType.CIRCULAR_DEPENDENCY, circularDependencyIssue.type)
        assertEquals(Severity.ERROR, circularDependencyIssue.severity)
        
        // Another ambiguous provider test
        val ambiguousProviderIssue2 = KnitIssue(
            type = IssueType.AMBIGUOUS_PROVIDER,
            severity = Severity.ERROR,
            message = "Multiple providers found for DatabaseService type",
            componentName = "com.example.DatabaseProvider1"
        )
        assertEquals(IssueType.AMBIGUOUS_PROVIDER, ambiguousProviderIssue2.type)
        assertTrue(ambiguousProviderIssue2.message.contains("Multiple providers"))
        
        // Ambiguous provider with qualifier awareness
        val ambiguousProviderIssue = KnitIssue(
            type = IssueType.AMBIGUOUS_PROVIDER,
            severity = Severity.ERROR,
            message = "Multiple providers found for type: UserRepository without qualifiers",
            componentName = "DatabaseUserRepository, InMemoryUserRepository",
            suggestedFix = "Use @Named qualifiers to distinguish between providers"
        )
        assertEquals(IssueType.AMBIGUOUS_PROVIDER, ambiguousProviderIssue.type)
        assertTrue(ambiguousProviderIssue.suggestedFix?.contains("@Named") ?: false)
    }
}
