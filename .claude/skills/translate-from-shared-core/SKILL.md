---
name: translate-from-shared-core
description: Translate Rust changes from restatedev/sdk-shared-core into equivalent Java code in this repo. Use when the user mentions translating, porting, or syncing commits from sdk-shared-core.
argument-hint: <commit-sha or range e.g. abc123 or abc123..def456>
---

# Translate sdk-shared-core (Rust) to sdk-java

You are translating Rust code changes from [restatedev/sdk-shared-core](https://github.com/restatedev/sdk-shared-core) into equivalent Java code in this repository.

## Input

`$ARGUMENTS` contains one or more commit references from sdk-shared-core. These can be:
- A single commit SHA (e.g., `abc123`)
- Multiple commit SHAs separated by spaces (e.g., `abc123 def456`)
- A commit range (e.g., `abc123..def456`)

## Step 1: Fetch the Rust diffs

For each commit or range, fetch the diff from GitHub:

- Single commit: `gh api repos/restatedev/sdk-shared-core/commits/<sha> --header "Accept: application/vnd.github.diff"`
- Range: `gh api repos/restatedev/sdk-shared-core/compare/<base>...<head> --header "Accept: application/vnd.github.diff"`

Also fetch the commit message(s) for context:
- `gh api repos/restatedev/sdk-shared-core/commits/<sha> --jq '.commit.message'`

## Step 2: Understand the changes

Before translating, analyze:
1. What the Rust change does semantically (not just syntactically)
2. Which Rust files/modules are affected
3. What the commit message says about intent and motivation

## Step 3: Architectural mapping between the two codebases

The primary mapping target is:
```
sdk-core/src/main/java/dev/restate/sdk/core/statemachine/
```

The two codebases implement the same state machine but with fundamentally different architectural patterns. Read the existing Java code before making changes. Don't blindly transliterate — adapt to the Java architecture while preserving semantics.

### Public interface: VM trait vs StateMachine interface

- **Rust**: `VM` trait in `src/lib.rs` with `sys_*` methods (e.g., `sys_state_get`, `sys_call`, `sys_run`)
- **Java**: `StateMachine` interface in `StateMachine.java` with plain method names (e.g., `stateGet`, `call`, `run`)
- Both return integer handles (`NotificationHandle` in Rust, `int` in Java) for async operations
- Both have `doProgress()` / `do_progress()` as the core async driver
- Both have `takeNotification()` / `take_notification()` for retrieving results

### State representation: enum variants vs class implementations

This is the **biggest architectural difference**.

**Rust**: States are variants of a single `State` enum in `src/vm/mod.rs`. Each variant carries its own data inline:
```rust
enum State {
    WaitingStart,
    WaitingReplayEntries { received_entries: u32, commands: VecDeque<RawMessage>, async_results: AsyncResultsState },
    Replaying { commands: VecDeque<RawMessage>, run_state: RunState, async_results: AsyncResultsState },
    Processing { processing_first_entry: bool, run_state: RunState, async_results: AsyncResultsState },
    Closed,
}
```

**Java**: States are classes implementing a sealed `State` interface. Each state class contains its own data as fields:
- `WaitingStartState` → `WaitingReplayEntriesState` → `ReplayingState` → `ProcessingState` → `ClosedState`
- The `State` interface declares default methods that throw `ProtocolException.badState()` for unsupported operations
- Concrete state classes override the methods they support

### Transitions: structs with pattern matching vs methods on state classes

**Rust**: Transitions are **structs** (e.g., `NewMessage`, `SysGetState`, `DoProgress`) that implement `Transition<Context, Event>` or `TransitionAndReturn<Context, Event>` for `State`. Inside each impl, you **pattern match on the current state**:
```rust
impl TransitionAndReturn<Context, PopJournalEntry<M>> for State {
    fn transition_and_return(self, context: &mut Context, event) -> Result<(Self, Output), Error> {
        match self {
            State::Replaying { mut commands, run_state, async_results } => { ... }
            State::Processing { ... } => { ... }
            s => Err(s.as_unexpected_state(...))
        }
    }
}
```
Transitions live in separate modules: `transitions/input.rs`, `transitions/journal.rs`, `transitions/async_results.rs`, `transitions/terminal.rs`.

**Java**: Transitions are **methods on the state classes themselves**. Each state class implements the transitions it supports:
```java
// In ReplayingState.java
int processStateGetCommand(String key, StateContext ctx) { ... }

// In ProcessingState.java
int processStateGetCommand(String key, StateContext ctx) { ... }
```
The `StateMachineImpl` delegates to the current state: `this.stateContext.getCurrentState().processStateGetCommand(key, this.stateContext)`.

**Key implication**: When a Rust commit adds or modifies a transition struct, in Java you need to add or modify the corresponding method across multiple state classes (typically `ReplayingState` and `ProcessingState`).

### Transition dispatch: do_transition vs StateHolder

**Rust**: `CoreVM.do_transition(event)` uses `mem::replace` to extract the current state, calls the transition, and stores the new state. Errors automatically send an error message and close output.

**Java**: `StateHolder.transition(newState)` simply swaps the current state reference. Error handling is done explicitly in state methods. `StateContext` acts as the central hub holding `StateHolder`, `Journal`, `EagerState`, etc.

### Context: Context struct vs StateContext class

- **Rust**: `Context` in `src/vm/context.rs` — holds `start_info`, `journal`, `output`, `eager_state`, `input_is_closed`
- **Java**: `StateContext` in `StateContext.java` — holds `StateHolder`, `Journal`, `EagerState`, `StartInfo`, `inputClosed`, `outputSubscriber`
- Both serve the same purpose: mutable state that persists across transitions

### Journal and async results

These are structurally very similar between both codebases:
- `Journal` tracks `commandIndex`, `notificationIndex`, `completionIndex`, `signalIndex`
- `AsyncResultsState` maps handles to `NotificationId`s, with `toProcess` queue and `ready` map
- `RunState` tracks pending/executing run blocks
- `EagerState` caches state values from StartMessage

### File mapping

| Rust file | Java file |
|-----------|-----------|
| `src/lib.rs` (VM trait) | `StateMachine.java` |
| `src/vm/mod.rs` (CoreVM) | `StateMachineImpl.java` |
| `src/vm/context.rs` (Context) | `StateContext.java`, `Journal.java`, `EagerState.java`, `StartInfo.java` |
| `src/vm/context.rs` (AsyncResultsState) | `AsyncResultsState.java` |
| `src/vm/context.rs` (RunState) | `RunState.java` |
| `src/vm/transitions/input.rs` | Logic in `WaitingStartState.java`, `WaitingReplayEntriesState.java` |
| `src/vm/transitions/journal.rs` | Methods across `ReplayingState.java` and `ProcessingState.java` |
| `src/vm/transitions/async_results.rs` | Methods in `ReplayingState.java`, `ProcessingState.java`, `AsyncResultsState.java` |
| `src/vm/transitions/terminal.rs` | `hitError()`/`hitSuspended()` methods on `State` interface |
| `src/vm/errors.rs` | `ProtocolException.java` |
| `src/service_protocol/` | `MessageDecoder.java`, `MessageEncoder.java`, `MessageType.java`, `ServiceProtocol.java` |

### Command processing patterns

Both codebases distinguish two kinds of commands:
- **Non-completable** (fire-and-forget): e.g., `stateSet`, `stateClear` — no handle returned
- **Completable** (returns a handle): e.g., `stateGet`, `call`, `run` — returns an int handle mapped to a NotificationId

In **Rust**, these are generic transitions like `SysNonCompletableEntry<M>` and `SysCompletableEntry<M>`.
In **Java**, these are `processNonCompletableCommand()` and `processCompletableCommand()` methods on the state classes.

### Tests: builder VMTestCase vs 3-layer handler tests

**Rust** tests are low-level, directly exercising the VM:
```rust
VMTestCase::new()
    .input(StartMessage { known_entries: 1, .. })
    .input(input_entry_message(b"my-data"))
    .run(|vm| {
        let input = vm.sys_input().unwrap();
        vm.sys_write_output(NonEmptyValue::Success(input), ...).unwrap();
        vm.sys_end().unwrap();
    });
```

**Java** tests use a 3-layer architecture:
1. **Base test suites** (abstract classes like `StateTestSuite`, `CallTestSuite`) define test scenarios as `TestDefinition` streams
2. **Concrete implementations** (e.g., `StateTest`) extend suites with actual handler code using the SDK's context API
3. **Test executors** (`MockRequestResponse`, `MockBidiStream`) run each test in both buffered and streaming modes

Java test inputs are built with `ProtoUtils` helpers (`startMessage()`, `inputCmd()`, `getLazyStateCmd()`, etc.) and assertions use `AssertUtils`.

**Key implication**: When a Rust commit adds a new VM-level test, in Java you typically need to add a handler-level test in the appropriate test suite, not a direct state machine test.

## Step 4: Apply the translation

1. **Read the affected Java files first** before making changes
2. Adapt the Rust change to Java's architecture (methods on state classes, not transition structs)
3. If a Rust change doesn't apply to Java (borrow checker workarounds, Rust-specific memory management), skip it and note why
4. **Run a build check** after translating: `./gradlew :sdk-core:compileJava`

## Step 5: Summary

After translating, provide:
1. A summary of what was translated
2. Any Rust changes that were skipped and why
3. Any areas that need manual review or testing
