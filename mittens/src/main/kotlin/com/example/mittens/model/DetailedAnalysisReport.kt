package com.example.mittens.model

class DetailedAnalysisReport(val summary: AnalysisSummary) {

    fun generateNotificationMessage(): String {
        return buildString {
            appendLine("Knit Analysis Complete! ✓")
            appendLine(
                "Components: ${summary.totalComponents} • Dependencies: ${summary.totalDependencies} • Analysis: ${
                    formatTime(
                        summary.analysisTime
                    )
                }"
            )

            if (summary.totalIssues > 0) {
                appendLine()
                appendLine("ISSUES FOUND (${summary.totalIssues} total):")


                if (summary.errorCount > 0) {
                    append("🔴 Errors (${summary.errorCount}): ")
                    append(formatIssueBreakdown(summary.issueBreakdown, Severity.ERROR))
                    appendLine()
                }

                if (summary.warningCount > 0) {
                    append("🟡 Warnings (${summary.warningCount}): ")
                    append(formatIssueBreakdown(summary.issueBreakdown, Severity.WARNING))
                    appendLine()
                }

                if (summary.infoCount > 0) {
                    append("ℹ️ Info (${summary.infoCount}): ")
                    append(formatIssueBreakdown(summary.issueBreakdown, Severity.INFO))
                    appendLine()
                }


                if (summary.topIssues.isNotEmpty()) {
                    appendLine()
                    appendLine("Top Issues:")
                    summary.topIssues.forEach { issue ->
                        val icon = when (issue.severity) {
                            Severity.ERROR -> "•"
                            Severity.WARNING -> "•"
                            Severity.INFO -> "•"
                        }
                        appendLine("$icon ${formatTopIssue(issue)}")
                    }
                }
            } else {
                appendLine()
                appendLine("✨ No issues found! Your Knit dependency injection is clean.")
            }


            appendLine()
            if (summary.filesScanned > 0) {
                appendLine("📊 Scanned ${summary.filesScanned} files • ${summary.componentsWithIssues} components with issues")
            }
        }.trim()
    }

    fun generateExpandedDetails(): String {
        return buildString {
            appendLine("=== Knit Analysis Detailed Report ===")
            appendLine("Generated: ${java.time.LocalDateTime.now()}")
            appendLine("Analysis Time: ${formatTime(summary.analysisTime)}")
            appendLine()

            appendLine("=== Project Scan Summary ===")
            appendLine("Files Scanned: ${summary.filesScanned}")
            appendLine("Components Found: ${summary.totalComponents}")
            appendLine("Dependencies Analyzed: ${summary.totalDependencies}")
            appendLine("Components with Issues: ${summary.componentsWithIssues}")
            appendLine("Circular Dependencies: ${if (summary.hasCycles) "Yes ⚠️" else "No ✓"}")
            appendLine()

            if (summary.totalIssues > 0) {
                appendLine("=== Issue Breakdown by Type ===")
                summary.issueBreakdown.toList().sortedByDescending { it.second }.forEach { (type, count) ->
                    appendLine("${formatIssueTypeIcon(type)} ${formatIssueType(type)}: $count")
                }
                appendLine()

                appendLine("=== Issue Breakdown by Severity ===")
                appendLine("🔴 Critical Errors: ${summary.errorCount}")
                appendLine("🟡 Warnings: ${summary.warningCount}")
                appendLine("ℹ️ Information: ${summary.infoCount}")
                appendLine()

                appendLine("=== Detailed Issue Analysis ===")
                val groupedIssues = summary.topIssues.groupBy { it.severity }


                groupedIssues[Severity.ERROR]?.let { errors ->
                    appendLine("🔴 CRITICAL ERRORS (${errors.size}):")
                    appendLine("${"=".repeat(60)}")
                    errors.forEachIndexed { index, issue ->
                        appendLine("${index + 1}. ${issue.type}")
                        appendLine("   Component: ${issue.componentName}")
                        appendLine("   Issue: ${issue.message}")
                        if (issue.suggestedFix != null) {
                            appendLine("   💡 Suggested Fix: ${issue.suggestedFix}")
                        }
                        appendLine("   Impact: ${getIssueImpactDescription(issue.type)}")
                        appendLine()
                    }
                }


                groupedIssues[Severity.WARNING]?.let { warnings ->
                    appendLine("🟡 WARNINGS (${warnings.size}):")
                    appendLine("${"=".repeat(40)}")
                    warnings.forEachIndexed { index, issue ->
                        appendLine("${index + 1}. ${issue.type}")
                        appendLine("   Component: ${issue.componentName}")
                        appendLine("   Issue: ${issue.message}")
                        if (issue.suggestedFix != null) {
                            appendLine("   💡 Suggested Fix: ${issue.suggestedFix}")
                        }
                        appendLine("   Impact: ${getIssueImpactDescription(issue.type)}")
                        appendLine()
                    }
                }


                groupedIssues[Severity.INFO]?.let { infos ->
                    appendLine("ℹ️ INFORMATION (${infos.size}):")
                    appendLine("${"=".repeat(30)}")
                    infos.forEachIndexed { index, issue ->
                        appendLine("${index + 1}. ${issue.type}")
                        appendLine("   Component: ${issue.componentName}")
                        appendLine("   Info: ${issue.message}")
                        if (issue.suggestedFix != null) {
                            appendLine("   💡 Suggestion: ${issue.suggestedFix}")
                        }
                        appendLine("   Impact: ${getIssueImpactDescription(issue.type)}")
                        appendLine()
                    }
                }
            } else {
                appendLine("=== ✨ Clean Analysis Results ===")
                appendLine("Congratulations! Your Knit dependency injection setup is clean and well-structured.")
                appendLine("No issues were found during the analysis.")
                appendLine()
            }

            appendLine("=== Resolution Guide ===")
            appendIssueResolutionGuide()

            appendLine()
            appendLine("=== Best Practices Recommendations ===")
            appendBestPracticesGuide()
        }
    }

    private fun StringBuilder.appendIssueResolutionGuide() {
        appendLine("🔄 Circular Dependencies:")
        appendLine("   • Extract interfaces to break direct dependencies")
        appendLine("   • Use provider methods (@Provides) instead of direct injection")
        appendLine("   • Consider architectural redesign with layered approach")
        appendLine("   • Implement mediator or event-driven patterns")
        appendLine()

        appendLine("❓ Unresolved Dependencies:")
        appendLine("   • Add @Provides annotation to provider classes/methods")
        appendLine("   • Check import statements and class visibility")
        appendLine("   • Verify @Named qualifiers match exactly (case-sensitive)")
        appendLine("   • Ensure return types match dependency types")
        appendLine()

        appendLine("🔁 Singleton Violations:")
        appendLine("   • Use @Singleton annotation consistently")
        appendLine("   • Avoid multiple providers for the same type")
        appendLine("   • Consider component scope appropriateness")
        appendLine("   • Review lifecycle management requirements")
        appendLine()

        appendLine("🏷️ Named Qualifier Issues:")
        appendLine("   • Double-check qualifier names for typos")
        appendLine("   • Ensure @Named annotations match between providers and consumers")
        appendLine("   • Consider using type-safe qualifiers with annotation classes")
        appendLine("   • Verify qualifier uniqueness within scope")
        appendLine()

        appendLine("🎯 Ambiguous Providers:")
        appendLine("   • Use @Named qualifiers to distinguish providers")
        appendLine("   • Remove duplicate or unnecessary providers")
        appendLine("   • Consider using @Primary annotation for default providers")
        appendLine("   • Review provider scope and specificity")
        appendLine()
    }

    private fun StringBuilder.appendBestPracticesGuide() {
        appendLine("🏗️ Architecture:")
        appendLine("   • Keep dependency graphs shallow and well-layered")
        appendLine("   • Use interfaces to define clear contracts")
        appendLine("   • Separate concerns into distinct components")
        appendLine("   • Avoid deep injection chains")
        appendLine()

        appendLine("🔧 Implementation:")
        appendLine("   • Prefer constructor injection over property injection")
        appendLine("   • Use @Provides methods for complex object creation")
        appendLine("   • Keep provider methods simple and focused")
        appendLine("   • Document complex dependency relationships")
        appendLine()

        appendLine("🧪 Testing:")
        appendLine("   • Design components for easy testing and mocking")
        appendLine("   • Use test-specific configurations for DI")
        appendLine("   • Verify injection works in integration tests")
        appendLine("   • Test error scenarios and edge cases")
        appendLine()

        appendLine("⚡ Performance:")
        appendLine("   • Use singletons judiciously for heavy objects")
        appendLine("   • Avoid creating unnecessary object graphs")
        appendLine("   • Consider lazy initialization for expensive dependencies")
        appendLine("   • Profile and monitor injection performance")
        appendLine()
    }

    fun generateCompactSummary(): String {
        return buildString {
            appendLine("Knit Analysis Results")
            appendLine("${summary.totalComponents} components, ${summary.totalDependencies} dependencies")

            if (summary.totalIssues > 0) {
                val issueText = mutableListOf<String>()
                if (summary.errorCount > 0) issueText.add("${summary.errorCount} errors")
                if (summary.warningCount > 0) issueText.add("${summary.warningCount} warnings")
                if (summary.infoCount > 0) issueText.add("${summary.infoCount} info")
                appendLine("Issues: ${issueText.joinToString(", ")}")
            } else {
                appendLine("✨ No issues found!")
            }
        }.trim()
    }

    private fun formatTime(millis: Long): String {
        return when {
            millis < 1000 -> "${millis}ms"
            millis < 60000 -> "${"%.1f".format(millis / 1000.0)}s"
            else -> "${millis / 60000}m ${(millis % 60000) / 1000}s"
        }
    }

    private fun formatIssueBreakdown(breakdown: Map<IssueType, Int>, targetSeverity: Severity): String {

        val relevantIssues = breakdown.entries.sortedByDescending { it.value }
        return relevantIssues.take(3).joinToString(", ") { (type, count) ->
            "${formatIssueTypeShort(type)} ($count)"
        }
    }

    private fun formatTopIssue(issue: IssuePreview): String {
        val componentPart = if (issue.componentName.isNotBlank()) {
            " in ${issue.componentName.substringAfterLast(".")}"
        } else ""
        return "${issue.message}$componentPart"
    }

    private fun formatIssueType(type: IssueType): String {
        return when (type) {
            IssueType.CIRCULAR_DEPENDENCY -> "Circular Dependencies"
            IssueType.AMBIGUOUS_PROVIDER -> "Ambiguous Providers"
            IssueType.UNRESOLVED_DEPENDENCY -> "Unresolved Dependencies"
            IssueType.SINGLETON_VIOLATION -> "Singleton Violations"
            IssueType.NAMED_QUALIFIER_MISMATCH -> "Qualifier Mismatches"
            IssueType.MISSING_COMPONENT_ANNOTATION -> "Missing Annotations"
        }
    }

    private fun formatIssueTypeShort(type: IssueType): String {
        return when (type) {
            IssueType.CIRCULAR_DEPENDENCY -> "Circular"
            IssueType.AMBIGUOUS_PROVIDER -> "Ambiguous"
            IssueType.UNRESOLVED_DEPENDENCY -> "Unresolved"
            IssueType.SINGLETON_VIOLATION -> "Singletons"
            IssueType.NAMED_QUALIFIER_MISMATCH -> "Qualifiers"
            IssueType.MISSING_COMPONENT_ANNOTATION -> "Annotations"
        }
    }

    private fun getSeverityIcon(severity: Severity): String {
        return when (severity) {
            Severity.ERROR -> "🔴"
            Severity.WARNING -> "🟡"
            Severity.INFO -> "ℹ️"
        }
    }

    private fun formatIssueTypeIcon(type: IssueType): String {
        return when (type) {
            IssueType.CIRCULAR_DEPENDENCY -> "🔄"
            IssueType.AMBIGUOUS_PROVIDER -> "🎯"
            IssueType.UNRESOLVED_DEPENDENCY -> "❓"
            IssueType.SINGLETON_VIOLATION -> "🔁"
            IssueType.NAMED_QUALIFIER_MISMATCH -> "🏷️"
            IssueType.MISSING_COMPONENT_ANNOTATION -> "📝"
        }
    }

    private fun getIssueImpactDescription(type: IssueType): String {
        return when (type) {
            IssueType.CIRCULAR_DEPENDENCY -> "Prevents compilation, can cause runtime stack overflow"
            IssueType.AMBIGUOUS_PROVIDER -> "Runtime injection failure, unclear dependency resolution"
            IssueType.UNRESOLVED_DEPENDENCY -> "Compilation failure, missing required dependencies"
            IssueType.SINGLETON_VIOLATION -> "Unexpected multiple instances, potential memory/performance issues"
            IssueType.NAMED_QUALIFIER_MISMATCH -> "Runtime injection failure, dependency not found"
            IssueType.MISSING_COMPONENT_ANNOTATION -> "Component not registered, injection will fail"
        }
    }

    companion object {
        fun formatIssueForQuickInfo(issue: IssuePreview): String {
            val severity = when (issue.severity) {
                Severity.ERROR -> "ERROR"
                Severity.WARNING -> "WARNING"
                Severity.INFO -> "INFO"
            }

            val typeDescription = when (issue.type) {
                IssueType.CIRCULAR_DEPENDENCY -> "Components depend on each other in a cycle"
                IssueType.AMBIGUOUS_PROVIDER -> "Multiple providers found for the same dependency"
                IssueType.UNRESOLVED_DEPENDENCY -> "No provider found for this dependency"
                IssueType.SINGLETON_VIOLATION -> "Singleton lifecycle not properly maintained"
                IssueType.NAMED_QUALIFIER_MISMATCH -> "Named qualifier doesn't match any provider"
                IssueType.MISSING_COMPONENT_ANNOTATION -> "Component missing required annotation"
            }

            return "[$severity] $typeDescription: ${issue.message}"
        }

    }
}