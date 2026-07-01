# Migrating from the codegen API to the reflection-based API

The **codegen API** — the `sdk-api-gen` annotation processor / `sdk-api-kotlin-gen` KSP generator, with
handlers taking a `Context` (or `ObjectContext`/`WorkflowContext`/…) first parameter — is **deprecated**
and will be removed in a future release. Prefer the **reflection-based API**: no annotation processor, and
handlers drop the `Context` parameter for the static `dev.restate.sdk.Restate` methods (Java) or the
top-level functions in `dev.restate.sdk.kotlin` (Kotlin).

Both use the **same** annotations (`@Service`, `@Handler`, …), so migrating is mostly removing the
`Context` parameter and changing how you invoke other services. The two styles coexist, so you can migrate
one service at a time.

---

## TL;DR — API mapping

### Java: `Context` API → `Restate` API

| Codegen / `Context` API                         | Reflection-based `Restate` API                                                    |
|-------------------------------------------------|-----------------------------------------------------------------------------------|
| `void greet(Context ctx, String req)`           | `void greet(String req)` (drop the `Context` parameter)                           |
| `ctx.run(...)` / `ctx.runAsync(...)`            | `Restate.run(...)` / `Restate.runAsync(...)`                                       |
| `ctx.random()`                                  | `Restate.random()`                                                                |
| `ctx.sleep(d)` / `ctx.timer(...)`               | `Restate.sleep(d)` / `Restate.timer(...)`                                          |
| `ctx.instantNow()`                              | `Restate.instantNow()`                                                            |
| `ctx.awakeable(...)` / `ctx.awakeableHandle(id)`| `Restate.awakeable(...)` / `Restate.awakeableHandle(id)`                           |
| `ctx.signal(...)`                               | `Restate.signal(...)`                                                             |
| `ctx.get(key)` / `ctx.set(key, v)` / `ctx.clear(key)` | `Restate.state().get(key)` / `Restate.state().set(key, v)` / `Restate.state().clear(key)` |
| `ctx.key()`                                     | `Restate.key()`                                                                   |
| `ctx.promise(key)` / `ctx.promiseHandle(key)`   | `Restate.promise(key)` / `Restate.promiseHandle(key)`                             |
| `ctx.invocationHandle(id, ...)`                 | `Restate.invocationHandle(id, ...)`                                              |
| Code-generated clients (Service)                | `Restate.service(Class)` / `Restate.serviceHandle(Class)`                         |
| Code-generated clients (Virtual Object)         | `Restate.virtualObject(Class, key)` / `Restate.virtualObjectHandle(Class, key)`   |
| Code-generated clients (Workflow)               | `Restate.workflow(Class, key)` / `Restate.workflowHandle(Class, key)`             |

From **outside** a handler (the ingress client), the equivalents live on `dev.restate.client.Client`:
`client.service(Class)` / `client.serviceHandle(Class)` / `client.virtualObject(Class, key)` / etc.

### Kotlin: `Context` API → top-level functions

| Codegen / `Context` API                             | Reflection-based top-level functions                    |
|-----------------------------------------------------|---------------------------------------------------------|
| `suspend fun greet(ctx: Context, req: String)`      | `suspend fun greet(req: String)` (drop the `Context`)   |
| `ctx.runBlock { ... }` / `ctx.runAsync { ... }`     | `runBlock { ... }` / `runAsync { ... }`                 |
| `ctx.random()`                                      | `random()`                                              |
| `ctx.sleep(d)` / `ctx.timer(...)`                   | `sleep(d)` / `timer(...)`                               |
| `ctx.awakeable<T>()` / `ctx.awakeableHandle(id)`    | `awakeable<T>()` / `awakeableHandle(id)`                |
| `ctx.signal<T>(name)`                               | `signal<T>(name)`                                       |
| `ctx.get(key)` / `ctx.set(key, v)` / `ctx.clear(key)` | `state().get(key)` / `state().set(key, v)` / `state().clear(key)` |
| `ctx.key()`                                         | `objectKey()` / `workflowKey()`                         |
| `ctx.promise(key)` / `ctx.promiseHandle(key)`       | `promise(key)` / `promiseHandle(key)`                   |
| Code-generated clients (Service)                    | `service<T>()` / `toService<T>()`                       |
| Code-generated clients (Virtual Object)             | `virtualObject<T>(key)` / `toVirtualObject<T>(key)`     |
| Code-generated clients (Workflow)                   | `workflow<T>(key)` / `toWorkflow<T>(key)`               |

All the top-level functions are in the `dev.restate.sdk.kotlin` package — add `import dev.restate.sdk.kotlin.*`.

---

## Java migration

### 1. Remove the annotation processor dependency

Delete the `sdk-api-gen` annotation processor from your build; the reflection-based API needs no processor.

```diff
- annotationProcessor("dev.restate:sdk-api-gen:<version>")
  implementation("dev.restate:sdk-java-http:<version>")
```

### 2. Remove the `Context` parameter from your handlers

Remove `Context` / `ObjectContext` / `SharedObjectContext` / `WorkflowContext` / `SharedWorkflowContext`
from your `@Handler`-annotated methods. The same applies to interfaces annotated with Restate annotations.

```java
// Before
@VirtualObject
public class Counter {
  @Handler
  public void add(ObjectContext ctx, long request) {}

  @Shared
  @Handler
  public long get(SharedObjectContext ctx) {}
}

// After
@VirtualObject
public class Counter {
  @Handler
  public void add(long request) {}

  @Shared
  @Handler
  public long get() {}
}
```

### 3. Replace `ctx.` calls with `Restate.`

```java
// Before
@Handler
public void add(ObjectContext ctx, long value) {
  long currentValue = ctx.get(TOTAL).orElse(0L);
  ctx.set(TOTAL, currentValue + value);
}

// After
@Handler
public void add(long value) {
  var state = Restate.state();
  long currentValue = state.get(TOTAL).orElse(0L);
  state.set(TOTAL, currentValue + value);
}
```

### 4. Replace code-generated clients

**Simple proxy (direct calls):**

```java
// Direct method call on a virtual object
Restate.virtualObject(Counter.class, "my-key").add(1);
```

**Handle-based (advanced patterns):**

```java
// call() with a method reference returns a DurableFuture you can await and/or compose
int count = Restate.virtualObjectHandle(Counter.class, "my-counter")
    .call(Counter::increment)
    .await();

// send() for one-way invocation without waiting
InvocationHandle<Integer> handle = Restate.virtualObjectHandle(Counter.class, "my-counter")
    .send(Counter::increment);

// Invocation options such as an idempotency key
int idempotentCount = Restate.virtualObjectHandle(Counter.class, "my-counter")
    .call(Counter::increment, InvocationOptions.idempotencyKey("my-idempotency-key"))
    .await();
```

---

## Kotlin migration

### 1. Remove the KSP code generator dependency

```diff
- ksp("dev.restate:sdk-api-kotlin-gen:<version>")
  implementation("dev.restate:sdk-kotlin-http:<version>")
```

You can also remove the `com.google.devtools.ksp` Gradle plugin if it's no longer used elsewhere.

### 2. Remove the `Context` parameter from your handlers

```kotlin
// Before
@VirtualObject
class Counter {
  @Handler
  suspend fun add(ctx: ObjectContext, value: Long) {}

  @Shared
  @Handler
  suspend fun get(ctx: SharedObjectContext): Long {}
}

// After
import dev.restate.sdk.kotlin.*

@VirtualObject
class Counter {
  @Handler
  suspend fun add(value: Long) {}

  @Shared
  @Handler
  suspend fun get(): Long {}
}
```

### 3. Replace `ctx.` calls with the top-level functions

```kotlin
// Before
@Handler
suspend fun add(ctx: ObjectContext, value: Long) {
  val currentValue = ctx.get(TOTAL) ?: 0L
  ctx.set(TOTAL, currentValue + value)
}

// After
@Handler
suspend fun add(value: Long) {
  val state = state()
  val currentValue = state.get(TOTAL) ?: 0L
  state.set(TOTAL, currentValue + value)
}
```

### 4. Replace code-generated clients

**Simple proxy (direct calls):**

```kotlin
virtualObject<Counter>("my-key").add(1) // Direct method call
```

**Handle-based (advanced patterns):**

```kotlin
// call() with a lambda returns a DurableFuture you can await and/or compose
val count = toVirtualObject<Counter>("my-counter")
    .request { add(1) }
    .call()
    .await()

// send() for one-way invocation without waiting
val handle = toVirtualObject<Counter>("my-counter")
    .request { add(1) }
    .send()

// Invocation options such as an idempotency key
val idempotentCount = toVirtualObject<Counter>("my-counter")
    .request { add(1) }
    .options { idempotencyKey = "my-idempotency-key" }
    .call()
    .await()
```

### Kotlin gotcha: proxy clients need non-final classes

The proxy clients (`service<T>()`, `virtualObject<T>(key)`, `toService<T>()`, …) create a runtime proxy of
`T`. Kotlin classes are `final` by default, which prevents proxy generation.

> **Using Kotlin + Spring Boot?** You don't need any of the below — the Restate Spring Boot Kotlin starter
> already applies the all-open plugin for the Restate annotations for you.

Otherwise, pick one of:

- **Define an interface** carrying the Restate annotations, implement it, and use the interface type for
  proxies: `service<MyServiceInterface>()`. (Recommended.)
- Mark the annotated classes/methods `open`.
- Apply the [Kotlin all-open compiler plugin](https://kotlinlang.org/docs/all-open-plugin.html):

  ```kotlin
  plugins {
      kotlin("plugin.allopen") version "<kotlin-version>"
  }

  allOpen {
      annotations(
          "dev.restate.sdk.annotation.Service",
          "dev.restate.sdk.annotation.VirtualObject",
          "dev.restate.sdk.annotation.Workflow")
  }
  ```

  This makes any class annotated with a Restate annotation (and its methods) `open` automatically.

---

## Deterministic time

Use the deterministic clock instead of `Instant.now()` / `Clock.System.now()`:

```java
// Java
Instant now = Restate.instantNow();
```

```kotlin
// Kotlin — requires opting in to the experimental kotlin.time API
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import dev.restate.sdk.kotlin.*

@OptIn(ExperimentalTime::class)
@Handler
suspend fun myHandler(): String {
  val now = Clock.Restate.now()
  return "Current time: $now"
}
```
