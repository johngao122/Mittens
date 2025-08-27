# Knit Dependency Injection Analysis Plugin Implementation Plan

## Project Overview

Knit is TikTok's open-source dependency injection framework for JVM/Android that rewrites bytecode directly to wire dependencies. While this offers high performance and clean syntax, it makes dependency relationships opaque—developers may struggle with hidden cycles, ambiguous providers, and onboarding complexity.

**Goals**: The IntelliJ plugin introduces a dedicated "Run Knit Analysis" action that compiles the project with Knit's Gradle transform, scans the resulting bytecode, and produces a live dependency graph. The tool helps developers visualize components, providers, and consumers; detect common issues like circular or ambiguous dependencies; and confirm that source-level intent matches post-transform injection.

**Deliverables**: A source-code IntelliJ plugin with documentation; a tool window showing summary metrics, an interactive dependency graph, and issue lists; quick navigation and quick-fix actions inside the editor; and export options (JSON + SVG/PNG) for CI and documentation.

## Phase 1: Plugin Infrastructure & Settings (2 days) - **CURRENT PHASE**

1. **Setup plugin structure and dependencies**

    - Configure `build.gradle.kts` with IntelliJ SDK, ASM for bytecode analysis
    - Add plugin service registration and lifecycle management

2. **User configuration system**

    - Add settings page with options: "Auto-analyze after build" vs "Manual only"
    - Implement settings persistence and retrieval

3. **Analysis action registration**
    - Create "Run Knit Analysis" action in menu/toolbar
    - Add keyboard shortcut and progress indicators

## Phase 2: Knit Project Detection & Bytecode Analysis (3 days)

4. **Knit project detection**

    - Scan `build.gradle.kts` for Knit plugin and dependencies
    - Detect Knit components using the three criteria from docs:
        - Classes with `by di` properties
        - Classes with `@Provides`/`@Component` annotations
        - Classes explicitly marked with `@Component`

5. **Gradle integration and bytecode parsing**

    - Execute Knit's bytecode transformation via Gradle tasks
    - Parse transformed `.class` files using ASM library
    - Extract Knit-generated dependency injection code

6. **Core dependency graph construction**
    - Build graph with nodes (components/providers) and edges (dependencies)
    - Handle component composition (`@Component` properties)
    - Track inheritance relationships between components and interfaces

## Phase 3: Advanced Knit Features Analysis (3 days)

7. **Singleton and scope analysis**

    - Detect `@Singleton` annotations and backing field generation
    - Verify singleton constraints and detect violations
    - Handle component-level vs global singletons

8. **Named qualifiers and multi-provides**

    - Parse `@Named` annotations (both string and class-based)
    - Handle `@IntoSet`, `@IntoList`, `@IntoMap` collections
    - Detect qualifier mismatches and ambiguous providers

9. **Advanced injection patterns**
    - `Factory<T>` and `() -> T` function types
    - `Loadable<T>` lifecycle management
    - `@KnitViewModel` injection patterns
    - Interface injection with explicit type declaration

## Phase 4: Issue Detection Algorithms (2 days)

10. **Implement detection for common DI issues**
    -   **Circular dependencies**: DFS-based cycle detection in component graph
    -   **Ambiguous providers**: Multiple providers for same type without qualifiers
    -   **Unresolved dependencies**: `by di` properties without corresponding providers
    -   **Singleton violations**: Multiple singleton instances of same type
    -   **Named qualifier mismatches**: Consumer/producer name inconsistencies

## Phase 5: Web-based Graph Visualization (3 days)

11. **Enhance existing React app in `view/mittens/`**

    -   Install graph visualization library (e.g., vis-network, cytoscape.js)
    -   Create component for interactive dependency graph
    -   Add node filtering, search, zoom, and pan capabilities

12. **IntelliJ webview integration**

    -   Embed React app in IntelliJ tool window using JCEF
    -   Create bi-directional communication between plugin and web view
    -   Pass dependency graph data from plugin to React app

13. **Graph visualization features**
    -   Color-coding: components (blue), providers (green), issues (red/orange)
    -   Node grouping by package/module
    -   Edge styling for different dependency types (singleton, named, multi-provides)
    -   Interactive tooltips showing dependency details

## Phase 6: Tool Window and Editor Integration (2 days)

14. **Tool window with tabbed interface**

    -   **Summary tab**: Metrics (component count, provider count, issues)
    -   **Graph tab**: Embedded web view with dependency graph
    -   **Issues tab**: Categorized list of detected problems with severity

15. **Editor integration and navigation**
    -   Click-to-navigate from graph nodes to source code
    -   Gutter icons showing injection status
    -   Highlight problematic `by di` injections in editor
    -   Quick-fix suggestions for common issues

## Phase 7: Export and Build Integration (2 days)

16. **JSON export with graph-friendly format**

    ```json
    {
        "nodes": [{ "id": "ComponentA", "type": "component", "issues": [] }],
        "edges": [
            { "from": "ComponentA", "to": "ProviderB", "type": "singleton" }
        ],
        "metadata": { "timestamp": "", "project": "", "knitVersion": "" }
    }
    ```

17. **Build system integration**
    -   Hook into Gradle build lifecycle when auto-analysis enabled
    -   Incremental analysis: only re-analyze changed components
    -   Cache results until source code changes

## Phase 8: Testing and Documentation (2 days)

18. **Comprehensive testing using sample_project**

    -   Test circular dependency detection (OrderService ↔ InventoryService)
    -   Test ambiguous provider detection (multiple UserRepository implementations)
    -   Test component composition and inheritance scenarios
    -   UI testing for tool window and graph interactions

19. **Documentation and demo video**
    -   README with setup instructions and feature overview
    -   Record 3-minute demo showing issue detection and graph navigation
    -   Document JSON export schema for external tools

## Technical Decisions Based on Knit Documentation

### Knit Component Detection (3 criteria from docs):

1. Classes with `by di` properties
2. Classes with `@Provides` or `@Component` annotations
3. Classes explicitly marked with `@Component`

### Bytecode Analysis Approach:

-   Focus on post-Knit-transform bytecode since Knit generates direct injection code
-   Use ASM to detect Knit's bytecode patterns (IFNULL for singletons, direct field access)
-   Parse both source annotations and generated bytecode patterns

### Advanced Features to Handle:

-   **Singleton Support**: Global vs component-level, backing field detection
-   **Component Composition**: `@Component` properties for capability inheritance
-   **Component Inheritance**: Interface/parent class provider inheritance
-   **Named Qualifiers**: String-based and class-based (`@Named(qualifier = SomeClass::class)`)
-   **Multi-Provides**: `@IntoSet`, `@IntoList`, `@IntoMap` collection injection
-   **Factory/Loadable**: `Factory<T>`, `() -> T`, `Loadable<T>` lifecycle management
-   **ViewModel Injection**: Special `@KnitViewModel` handling
-   **Interface Injection**: Explicit type declaration with `@Provides(Interface::class)`

### Web Visualization:

-   Leverage existing React app structure in `view/mittens/`
-   Use vis-network or cytoscape.js for interactive graph rendering
-   Export format compatible with common graph libraries (D3, Gephi, etc.)

## Estimated Timeline: 12-15 days

This plan takes advantage of the existing React infrastructure and focuses on Knit-specific patterns documented in the provided materials.

## Progress Tracking

-   [x] Phase 1 **COMPLETED** ✅
    -   [x] Setup plugin structure and dependencies
    -   [x] Created core plugin services (KnitAnalysisService, KnitSettingsService, KnitGradleService)
    -   [x] Registered services in plugin.xml
    -   [x] User configuration system with settings UI
    -   [x] Analysis action registration with keyboard shortcut (Ctrl+Alt+K)
    -   [x] Data model foundation (KnitComponent, DependencyGraph, AnalysisResult)
    -   [x] Build verification successful
-   [x] Phase 2 **COMPLETED** ✅
    -   [x] Enhanced Knit project detection with KnitProjectDetector service
    -   [x] Implemented KnitSourceAnalyzer for comprehensive source code analysis
    -   [x] Created KnitBytecodeAnalyzer using ASM for bytecode pattern detection
    -   [x] Built GradleTaskRunner for executing Knit compilation tasks
    -   [x] Replaced mock implementation with real analysis pipeline
    -   [x] Integrated all services in KnitAnalysisService with progress reporting
    -   [x] Core dependency graph construction with issue detection
    -   [x] Fixed SLF4J LinkageError by excluding conflicting dependencies
    -   [x] Created comprehensive test suite (34 tests covering all major components)
    -   [x] Build verification and testing successful - plugin fully functional
-   [x] Phase 3 **COMPLETED** ✅
    -   [x] **Singleton and scope analysis**
        -   [x] Enhanced KnitSourceAnalyzer to parse @Singleton annotations from source code
        -   [x] Improved KnitBytecodeAnalyzer with advanced singleton detection patterns (IFNULL sequences)
        -   [x] Implemented singleton violation detection (multiple singleton instances of same type)
        -   [x] Added component-level vs global singleton distinction
    -   [x] **Named qualifiers and multi-provides**
        -   [x] Comprehensive @Named annotation parsing (string-based and class-based qualifiers)
        -   [x] Enhanced existing @IntoSet, @IntoList, @IntoMap collection support
        -   [x] Implemented named qualifier mismatch detection between providers and consumers
        -   [x] Added qualifier-aware ambiguous provider detection
    -   [x] **Advanced injection patterns**
        -   [x] Enhanced Factory<T> and () -> T function type detection with improved regex patterns
        -   [x] Implemented Loadable<T> lifecycle management pattern recognition
        -   [x] Added @KnitViewModel annotation detection and handling (treated as specialized component)
        -   [x] Enhanced interface injection support with @Provides(Interface::class) parsing
    -   [x] **Enhanced issue detection algorithms**
        -   [x] Singleton violation detection with component-level analysis
        -   [x] Named qualifier mismatch detection with detailed error messages
        -   [x] Qualifier-aware ambiguous provider detection with context-sensitive suggestions
        -   [x] Integration with existing circular dependency detection system
    -   [x] **Comprehensive testing and validation**
        -   [x] Created KnitAdvancedFeaturesTest with comprehensive Phase 3 feature validation
        -   [x] Created Phase3ValidationTest demonstrating all advanced capabilities
        -   [x] Added test coverage for singleton detection, named qualifiers, factory types, and loadable patterns
        -   [x] Validated implementation against sample_project scenarios
        -   [x] Build verification successful with all Phase 3 enhancements
-   [ ] Phase 4
-   [ ] Phase 5
-   [ ] Phase 6
-   [ ] Phase 7
-   [ ] Phase 8
