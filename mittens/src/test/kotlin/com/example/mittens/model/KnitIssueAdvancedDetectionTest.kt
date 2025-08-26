package com.example.mittens.model

import org.junit.Test
import org.junit.Assert.*

class KnitIssueAdvancedDetectionTest {
    
    @Test
    fun testIssueInstancesForAdvancedCases() {
        // Singleton violation
        val singletonViolationIssue = KnitIssue(
            type = IssueType.SINGLETON_VIOLATION,
            severity = Severity.ERROR,
            message = "Multiple singleton providers found for type: DatabaseService",
            componentName = "com.example.DatabaseProvider1, com.example.DatabaseProvider2"
        )
        assertEquals(IssueType.SINGLETON_VIOLATION, singletonViolationIssue.type)
        assertEquals(Severity.ERROR, singletonViolationIssue.severity)
        
        // Named qualifier mismatch
        val qualifierMismatchIssue = KnitIssue(
            type = IssueType.NAMED_QUALIFIER_MISMATCH,
            severity = Severity.ERROR,
            message = "Named qualifier '@Named(secondary)' not found for type: EmailClient",
            componentName = "com.example.UserService"
        )
        assertEquals(IssueType.NAMED_QUALIFIER_MISMATCH, qualifierMismatchIssue.type)
        assertTrue(qualifierMismatchIssue.message.contains("@Named(secondary)"))
        
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
