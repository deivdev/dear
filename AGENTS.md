# AGENTS.md — Dear

## Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device/emulator
./gradlew installDebug

# Clean build
./gradlew clean assembleDebug

# Run a specific Gradle task
./gradlew :app:tasks                         # list all available tasks
./gradlew :app:compileDebugKotlin            # compile only Kotlin sources
./gradlew :app:compileDebugAndroidTestKotlin # compile instrumented test sources

# Build release (requires signing config)
./gradlew assembleRelease
```

## Testing

No test dependencies or test files exist yet. When adding tests:
- **Unit tests**: `app/src/test/java/dev/deiv/dear/...` (JUnit 5 + kotlinx-coroutines-test is conventional for this type of project)
- **Instrumented tests**: `app/src/androidTest/java/dev/deiv/dear/...`
- Room requires instrumented tests or `Room.inMemoryDatabaseBuilder` for unit tests
- Compose UI tests use `androidx.compose.ui.test.junit4.ComposeTestRule`

```bash
# Run all unit tests (once test deps are added)
./gradlew testDebugUnitTest

# Run all instrumented tests
./gradlew connectedDebugAndroidTest

# Run a single test class
./gradlew :app:testDebugUnitTest --tests "dev.deiv.dear.data.EntryDaoTest"

# Run a single test method
./gradlew :app:testDebugUnitTest --tests "dev.deiv.dear.data.EntryDaoTest.insert_entry"
```

## Linting & Formatting

No linting/formatting tools are currently configured. No `.editorconfig`, no `ktlint`, no `detekt`, no `spotless`. The project uses `kotlin.code.style=official` in `gradle.properties`, which means IntelliJ/Android Studio follows official Kotlin style (not Android style). If adding a linter:

```bash
# ktlint (after adding plugin)
./gradlew ktlintCheck
./gradlew ktlintFormat

# detekt (after adding plugin)
./gradlew detekt
```

---

## Code Style Guidelines

### Imports
- All imports **explicit** — no wildcard imports (`import ...*`).
- Grouped by origin: Android SDK → AndroidX/Jetpack → project packages (`dev.deiv.dear.*`) → Kotlin stdlib → Java stdlib → third-party.
- One blank line between groups (no blank lines within group).

### Formatting
- **Indentation**: 4 spaces. No tabs.
- **Braces**: K&R style — opening brace on same line.
- **Line length**: Aim for ~120 chars. Long lines (Room queries, chained calls) are acceptable but prefer line breaks after `=`, `(`, or `.`.
- **Trailing commas**: Always use trailing commas in multi-line function calls, builders, and constructor invocations.
- **Blank lines**: One blank line between properties, between methods, and around nested class/companion object boundaries.

### Types & Nullability
- Use nullable types (`Type?`) explicitly — never suppress warnings.
- `lateinit` is acceptable for properties initialized in `onCreate` (e.g., `DearApp`).
- `@Volatile` for thread-safe primitive/ref fields accessed from multiple coroutines.
- Prefer `data class` for pure data holders, `sealed class` or `enum class` for state modeling.
- Use `buildList`, `buildString`, `buildMap` for efficient collection construction.
- Use `coerceAtLeast` / `coerceIn` for bounds clamping.
- Use `?.let`, `?:` (Elvis), `takeIf` for null-safe chaining.

### Naming Conventions
- **Packages**: `dev.deiv.dear.{layer}.{subdomain}` — all lowercase, no underscores (`ui.timeline`, `data.db`, `llm`).
- **Classes/Objects/Interfaces**: PascalCase (`EntryRepository`, `ModelManager`).
- **Composable functions**: PascalCase (`CaptureScreen`, `ChatBubble`, `StatsCard`).
- **Methods/Functions**: camelCase (`addText`, `loadModel`, `getUncataloged`).
- **Properties**: camelCase (`modelDir`, `currentModelFile`, `lastTokensPerSec`).
- **Constants**: UPPER_SNAKE_CASE in `companion object` (`const val MAX_HISTORY = 50`).
- **Private mutable state backing fields**: underscore prefix (`_state`, `_messages`, `_current`).
- **Boolean properties**: prefix with `is` where natural (`isUser`, `isNotBlank`).
- **TAG constant**: `private const val TAG = "ClassName"` in every class that uses `Log`.

### Error Handling
- Use `runCatching { }.getOrDefault()` / `.getOrElse()` for expected/operational failures.
- Use explicit `try/catch` with `Log.e(TAG, message, e)` for unexpected exceptions.
- Use `error("message")` for impossible states / violated invariants.
- Use `?: run { ... return }` for null-handling with early return.
- Use `Throwable` in catch blocks for coroutine safety (not just `Exception`).

### Coroutines & Concurrency
- Use `viewModelScope.launch` in ViewModels for fire-and-forget work.
- Use `withContext(Dispatchers.IO)` for blocking I/O (disk, network, native calls).
- Use `Flow` (especially `StateFlow`) for reactive state exposure.
- Expose `StateFlow` as **read-only**: `private val _x = MutableStateFlow(...)` / `val x: StateFlow<X> = _x.asStateFlow()`.
- Use `Channel` for producer/consumer queues; prefer `Channel.UNLIMITED` for job queues.
- Background work launched in `DearApp` uses a dedicated `CoroutineScope(SupervisorJob() + Dispatchers.Main)`.

### ViewModels
- Extend `AndroidViewModel` (not `ViewModel`) when `Application` context is needed.
- Obtain services via `(application as DearApp).serviceName` — no DI framework.
- Never hold references to Composables, Views, or Activity.

### Compose UI
- Screens are `@Composable functions` (not classes) at the package level.
- Use `val context = LocalContext.current` / `val scope = rememberCoroutineScope()` at top of screen.
- Use `by remember { mutableStateOf(...) }` for local UI state.
- Modifier order: size/position first, then padding, then clickable, then testTag.
- Use `Arrangement.spacedBy(...)` instead of repetitive `Spacer()` calls.
- Wrap `scope.launch { }` in `onClick` for one-shot async operations.

### Room / Database
- DAOs are interfaces annotated with `@Dao`.
- Methods returning `Flow` for reactive reads; `suspend` for one-shot queries.
- Entity classes are `data class` with `@Entity` and `@PrimaryKey`.
- Use `@Insert`, `@Update`, `@Delete` annotations — never raw SQL for mutations unless necessary.

### Architecture
- **Single-module MVVM** with Repository pattern.
- **No DI framework** — manual DI via `DearApp : Application()` singleton holder.
- Screens → ViewModels → Repositories → DAOs / Services.
- Business logic lives in Repository layer, not ViewModel.
- LLM layer is isolated under `llm/` package with its own lifecycle (`ModelManager`, `CatalogQueue`).

### JNI / Native
- External functions declared as `private external fun` in companion object or class body.
- Native pointers stored as `Long` (context pointer).
- Wrap native loading in `withContext(Dispatchers.IO)`.
- Always check library-loaded flag before native calls.

### ProGuard
- Keep JNI classes: `-keep class dev.deiv.dear.llm.** { *; }`
- Room entities and DAOs are kept automatically by KSP.
