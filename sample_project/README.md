# Knit DI Framework Demo - Classic Scenarios

This project demonstrates **TikTok's Knit framework** with intentionally included classic dependency injection scenarios for educational purposes.

## Project Overview

A simple **e-commerce order processing system** built with Kotlin and Knit DI framework, showcasing real-world dependency injection patterns and common pitfalls.

## Architecture

```
src/main/kotlin/com/example/knit/demo/
├── main/           - Application entry point
├── core/
│   ├── models/     - User, Order, Product data models
│   ├── services/   - Business logic with DI scenarios
│   └── repositories/ - Data access layer
└── payment/        - Separate module (unresolved scenario)
```

## Complete DI Error Scenarios (All 6 IssueTypes)

This project demonstrates **all 6 error types** that the Mittens plugin can detect in Knit DI projects:

### 1. **CIRCULAR_DEPENDENCY** 🔄

-   **Classes**: `OrderService` ↔ `InventoryService`
-   **Issue**: Mutual dependency cycle via `by di`
-   **Location**: `src/main/kotlin/com/example/knit/demo/core/services/`
-   **Severity**: ERROR - Prevents compilation

```kotlin
@Provides
class OrderService {
    private val inventoryService: InventoryService by di
    fun processOrder() = inventoryService.checkStock(...)
}

@Provides  
class InventoryService {
    private val orderService: OrderService by di  // Creates cycle!
    fun releaseStock() = orderService.cancelOrder(...)
}
```

### 2. **AMBIGUOUS_PROVIDER** 🎯

-   **Classes**: `DatabaseUserRepository`, `InMemoryUserRepository`, `CachedUserRepository`, `RemoteUserRepository`
-   **Issue**: Multiple providers for `UserRepository` interface
-   **Location**: `src/main/kotlin/com/example/knit/demo/core/repositories/`
-   **Severity**: ERROR - Unclear which provider to use

```kotlin
@Provides(UserRepository::class)
class DatabaseUserRepository : UserRepository { ... }

@Provides(UserRepository::class) 
class InMemoryUserRepository : UserRepository { ... }

@Provides(UserRepository::class)  // Too many choices!
class CachedUserRepository : UserRepository { ... }

@Provides(UserRepository::class)  // Even more ambiguity!
class RemoteUserRepository : UserRepository { ... }
```

### 3. **UNRESOLVED_DEPENDENCY** ❓

-   **Interface**: `PaymentGateway`
-   **Consumer**: `PaymentService`
-   **Issue**: No provider implementation defined
-   **Location**: `payment/` module and services
-   **Severity**: ERROR - Missing required dependency

```kotlin
@Provides
class PaymentService {
    // Commented out to demonstrate unresolved dependency
    // private val paymentGateway: PaymentGateway by di // No provider exists!
}
```

### 4. **SINGLETON_VIOLATION** 🔁

-   **Classes**: Multiple singleton conflicts in auth and database services
-   **Issue**: Multiple singleton providers or singleton/non-singleton conflicts
-   **Location**: `UserAuthService`, `DatabaseService`, and related classes
-   **Severity**: WARNING/ERROR - Unexpected multiple instances

```kotlin
@Provides
@Singleton
class UserAuthService { ... }

@Provides  
@Singleton
class BackupUserAuthService { ... }  // Singleton violation!

@Provides
class SessionManager {
    private val userAuthService: UserAuthService by di  // Non-singleton depending on singleton
}
```

### 5. **NAMED_QUALIFIER_MISMATCH** 🏷️

-   **Classes**: Product services with mismatched `@Named` qualifiers
-   **Issue**: Named qualifiers don't match between providers and consumers  
-   **Location**: `ProductRepository`, `ProductService`, `CacheService`
-   **Severity**: ERROR - Named dependency not found

```kotlin
@Provides(ProductRepository::class)
@Named("primary")  // Provider uses "primary"
class InMemoryProductRepository : ProductRepository { ... }

@Provides
class ProductService {
    @Named("primaryRepo")  // Consumer uses "primaryRepo" - MISMATCH!
    private val productRepository: ProductRepository by di
}

@Provides
class CacheService {
    @Named("rediss")  // Typo: should be "redis"
    private val primaryCache: CacheProvider by di
}
```

### 6. **MISSING_COMPONENT_ANNOTATION** 📝

-   **Classes**: `AuditService`, `ValidationService`, `EmailValidationService`
-   **Issue**: Components missing `@Provides` annotation but still referenced
-   **Location**: Various service classes
-   **Severity**: ERROR - Component not registered for DI

```kotlin
// Missing @Provides annotation!
// @Provides  
class AuditService {
    fun logEvent(...) { ... }
}

class ValidationService {  // Also missing @Provides
    fun validateUser(user: User): ValidationResult { ... }
}

@Provides
class UserService {
    private val auditService: AuditService by di  // Will fail - not registered!
    private val validationService: ValidationService by di  // Will fail!
}
```

## Build & Run

### Prerequisites

-   **JDK 17+**
-   **Gradle 8.4+**

### Build Commands

```bash
# Standard build
./gradlew build

# Build with Knit transformations (recommended)
./gradlew runWithKnit

# Run the demo
./gradlew run
# or
java -jar build/libs/knit-demo-1.0.0.jar
```

### What to Expect

The build will **fail** due to the intentional DI issues, demonstrating how Knit detects **all 6 error types**:

-   🔄 **Circular Dependencies** - OrderService ↔ InventoryService cycle
-   🎯 **Ambiguous Providers** - 4 different UserRepository implementations 
-   ❓ **Unresolved Dependencies** - PaymentGateway interface with no provider
-   🔁 **Singleton Violations** - Multiple singleton providers and lifecycle conflicts
-   🏷️ **Named Qualifier Mismatches** - Typos and missing qualifiers in @Named annotations
-   📝 **Missing Component Annotations** - Services lacking @Provides but still injected

### Testing with Mittens Plugin

This project is perfect for testing the **Mittens IntelliJ plugin** which can detect and analyze all these DI issues:

1. Open the project in IntelliJ IDEA
2. Install the Mittens plugin 
3. Run "Analyze Knit Dependencies" 
4. View the comprehensive error report with all 6 issue types
5. Use the dependency graph visualization to see circular dependencies
6. Follow suggested fixes for each error type

## Knit Framework Features Demonstrated

-   **Zero-intermediation DI** - Direct bytecode generation
-   **Compile-time safety** - Issues caught at build time
-   **Kotlin-first design** - Uses `by di` property delegation
-   **Interface injection** - `@Provides(Interface::class)` annotation
-   **ShadowJar integration** - Complete dependency packaging

## Configuration

-   **Knit Version**: `0.1.4` (latest)
-   **Kotlin**: `1.9.22`
-   **JVM Target**: `17`
-   **Build Tool**: Gradle with Kotlin DSL

## Real Use Case Features

-   User management and authentication
-   Product catalog with inventory tracking
-   Order processing workflow
-   Payment processing integration
-   Multi-step business logic with proper DI patterns

## Learning Objectives

1. **Understand** common DI anti-patterns
2. **Experience** compile-time DI validation  
3. **Learn** Knit framework syntax and capabilities
4. **Practice** dependency injection best practices

## Error Summary

This project now contains **comprehensive examples** of all 6 IssueType errors:

| Error Type | Count | Examples |
|------------|-------|----------|
| 🔄 **CIRCULAR_DEPENDENCY** | 1 | OrderService ↔ InventoryService |
| 🎯 **AMBIGUOUS_PROVIDER** | 4 | Multiple UserRepository implementations |
| ❓ **UNRESOLVED_DEPENDENCY** | 1 | PaymentGateway interface (no provider) |
| 🔁 **SINGLETON_VIOLATION** | 4+ | Auth & Database service conflicts |
| 🏷️ **NAMED_QUALIFIER_MISMATCH** | 6+ | Product & Cache service qualifier typos |
| 📝 **MISSING_COMPONENT_ANNOTATION** | 3 | AuditService, ValidationService missing @Provides |

**Total Error Scenarios**: 19+ intentional DI errors for comprehensive testing

## Expected Plugin Behavior

When analyzing this project with the Mittens plugin, you should see:

- ✅ All 6 IssueType categories detected
- ✅ Detailed error messages with file locations
- ✅ Suggested fixes for each error type
- ✅ Dependency graph visualization showing circular dependencies
- ✅ Component relationship mapping
- ✅ Comprehensive analysis report

---

_This project is for educational purposes to demonstrate dependency injection concepts using TikTok's Knit framework and to test the Mittens plugin's comprehensive error detection capabilities._
