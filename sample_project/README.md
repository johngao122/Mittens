# Knit DI Framework Demo - Classic Scenarios

This project demonstrates **TikTok's Knit framework** with intentionally included classic dependency injection scenarios for educational purposes.

## 🎯 Project Overview

A simple **e-commerce order processing system** built with Kotlin and Knit DI framework, showcasing real-world dependency injection patterns and common pitfalls.

## 🏗️ Architecture

```
src/main/kotlin/com/example/knit/demo/
├── main/           - Application entry point
├── core/
│   ├── models/     - User, Order, Product data models
│   ├── services/   - Business logic with DI scenarios
│   └── repositories/ - Data access layer
└── payment/        - Separate module (unresolved scenario)
```

## 🎪 Classic DI Scenarios

### 1. 🔄 **Cycle Scenario**
- **Classes**: `OrderService` ↔ `InventoryService`
- **Issue**: Mutual dependency via `by di`
- **Location**: `src/main/kotlin/com/example/knit/demo/core/services/`

```kotlin
@Provides
class OrderService {
    private val inventoryService: InventoryService by di
    // ...
}

@Provides  
class InventoryService {
    private val orderService: OrderService by di
    // ...
}
```

### 2. ⚡ **Ambiguous Scenario**
- **Classes**: `DatabaseUserRepository` & `InMemoryUserRepository`
- **Issue**: Both provide `UserRepository` interface
- **Location**: `src/main/kotlin/com/example/knit/demo/core/repositories/`

```kotlin
@Provides(UserRepository::class)
class DatabaseUserRepository : UserRepository { ... }

@Provides(UserRepository::class)  
class InMemoryUserRepository : UserRepository { ... }
```

### 3. ❓ **Unresolved Scenario**
- **Interface**: `PaymentGateway` 
- **Consumer**: `PaymentService`
- **Issue**: No provider implementation defined
- **Location**: `payment/` module (separate for optional transforms)

```kotlin
@Provides
class PaymentService {
    private val paymentGateway: PaymentGateway by di // ❌ No provider!
}
```

### 4. 💀 **Dead Scenario**
- **Class**: `NotificationService`
- **Issue**: Provides `EmailChannel` & `SmsChannel` but never consumed
- **Location**: `src/main/kotlin/com/example/knit/demo/core/services/NotificationService.kt`

```kotlin
@Provides
class NotificationService {
    @Provides
    fun provideEmailChannel(): EmailChannel = EmailChannel()
    
    @Provides  
    fun provideSmsChannel(): SmsChannel = SmsChannel()
    // ☠️ Never consumed anywhere!
}
```

## 🚀 Build & Run

### Prerequisites
- **JDK 17+**
- **Gradle 8.4+**

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
The build will likely **fail** due to the intentional DI issues, demonstrating how Knit detects:
- Circular dependencies
- Ambiguous providers  
- Missing implementations
- Unused/dead code

## 🧪 Knit Framework Features Demonstrated

- **Zero-intermediation DI** - Direct bytecode generation
- **Compile-time safety** - Issues caught at build time
- **Kotlin-first design** - Uses `by di` property delegation
- **Interface injection** - `@Provides(Interface::class)` annotation
- **ShadowJar integration** - Complete dependency packaging

## 🔧 Configuration

- **Knit Version**: `0.1.4` (latest)
- **Kotlin**: `1.9.22`
- **JVM Target**: `17`
- **Build Tool**: Gradle with Kotlin DSL

## 📚 Real Use Case Features

- User management and authentication
- Product catalog with inventory tracking
- Order processing workflow  
- Payment processing integration
- Multi-step business logic with proper DI patterns

## 🎓 Learning Objectives

1. **Understand** common DI anti-patterns
2. **Experience** compile-time DI validation
3. **Learn** Knit framework syntax and capabilities
4. **Practice** dependency injection best practices

---

*This project is for educational purposes to demonstrate dependency injection concepts using TikTok's Knit framework.*