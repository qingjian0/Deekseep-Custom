# Android UI Architecture Standard — Kotlin, Jetpack Compose, MVVM/MVI

> Applies to the launcher/settings application in this repository.
> Hook runtime architecture remains independent of Android UI lifecycle.
> This document supplements REFACTOR_KOTLIN_FIRST_PLAN.md.

## 1. Approved stack

- Kotlin as the primary implementation language.
- Jetpack Compose for declarative UI.
- Material 3 for components, color, typography, shapes and adaptive layouts.
- AndroidX Lifecycle ViewModel and lifecycle-aware Flow collection.
- Kotlin Coroutines and StateFlow for asynchronous work and observable state.
- Repository interfaces for persistence, IPC and import/export boundaries.
- Prefer existing AndroidX/platform capabilities; do not add a DI framework or state-management library without a demonstrated need.

Pin versions centrally. Use the stable Compose BOM and stable Material 3 releases; do not upgrade unrelated dependencies as part of a screen-only refactor.

## 2. Architecture decision: MVVM + UDF, selective MVI

Use **MVVM as the default screen architecture**, with **unidirectional data flow (UDF)** as a mandatory state-management rule. Use an MVI-style reducer for screens with complex, interdependent user actions.

This is not a requirement to introduce a heavyweight MVI framework.

### Responsibilities

| Component | Owns | Must not own |
|---|---|---|
| Compose Screen | Rendering immutable UI state; forwarding user intents | Persistence, IPC protocols, Hook registration, business rules |
| ViewModel | Screen state, intent handling, orchestration, UI-scoped coroutine work | Android view references, static global state, Hook callback lifecycle |
| Use case / domain operation | A cohesive application operation and its rules | Compose types, Activity, Android View |
| Repository | Data access contract and source coordination | Screen presentation decisions |
| Data source / adapter | Preferences, files, IPC, platform APIs | UI state and navigation policy |

Keep domain/use-case classes only where they clarify reusable or non-trivial behavior. Do not create a class for every trivial getter/setter.

## 3. State and event contract

Each screen exposes one immutable state stream:

~~~kotlin
data class FeatureUiState(
    val isLoading: Boolean = false,
    val enabled: Boolean = false,
    val items: List<FeatureItemUi> = emptyList(),
    val error: UiMessage? = null,
)

class FeatureViewModel(
    private val repository: FeatureRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FeatureUiState())
    val uiState: StateFlow<FeatureUiState> = _uiState.asStateFlow()

    fun onAction(action: FeatureAction) {
        when (action) {
            FeatureAction.Refresh -> refresh()
            is FeatureAction.SetEnabled -> setEnabled(action.enabled)
        }
    }
}
~~~

Rules:

- UI state is immutable; expose StateFlow, never MutableStateFlow.
- Compose collects with collectAsStateWithLifecycle().
- User actions flow from UI to ViewModel; state flows from ViewModel to UI.
- Do not pass callbacks deep through multiple layers when a typed action is clearer.
- Represent loading, empty, content, and recoverable error states explicitly.
- One-off effects (navigation, snackbar, permission request) use a separately defined effect stream or an explicit state protocol; do not encode transient effects as mutable global flags.
- Avoid storing large files, Activity, Context, View, or host ClassLoader references in UI state.
- Use SavedStateHandle only for small restorable screen state, not as a general database.

For complex screens, define Action, UiState, and a pure reducer. Keep reducers deterministic and unit-testable. Side effects execute outside the reducer and return results as actions.

## 4. Compose screen rules

- Screens are small composable functions that receive immutable state and event callbacks.
- Separate route/container composables (ViewModel collection and navigation wiring) from stateless content composables.
- Prefer Material 3 components before custom widgets.
- Reuse the existing theme and visual language; do not introduce a parallel design system.
- Hoist state to the lowest common owner that needs it. Keep ephemeral widget state local.
- Use stable keys in lazy lists.
- Do not perform disk, IPC, network, reflection, or Hook work during composition.
- Do not launch coroutines directly during recomposition; use ViewModel scopes or narrowly justified Compose effects.
- Accessibility: provide meaningful labels, touch targets, semantics, contrast and scalable text.
- Support light/dark themes and dynamic color only through the existing theme policy.
- Preview representative loading, empty, error and populated states where practical.

Suggested package layout (adapt to current repository namespace rather than renaming packages blindly):

~~~text
ui/
  theme/
  components/
  navigation/
  home/
    HomeRoute.kt
    HomeScreen.kt
    HomeViewModel.kt
    HomeUiState.kt
    HomeAction.kt
  features/
  settings/
  data/
  help/
domain/
  model/
  repository/
  usecase/
data/
  repository/
  datasource/
  ipc/
  importexport/
~~~

Do not split Gradle modules solely to match this directory tree. Start with package boundaries and extract modules only when build isolation or ownership requires it.

## 5. Dependency and lifecycle boundaries

- ViewModels depend on repository interfaces or narrowly scoped operations, not directly on concrete file/IPC implementations.
- The Activity is a thin host for Compose, navigation, Android permission contracts and legacy IPC entry points.
- Keep existing exported component names, intent actions, extras/bundle keys, preference keys, storage paths, file formats and package identity stable.
- Use constructor injection manually where the graph is small. A DI framework is not a prerequisite.
- Repositories own IO dispatching; use Dispatchers.IO for blocking file/platform work and keep CPU-heavy parsing off the main thread.
- Never use runBlocking in UI or Hook callbacks.
- Cancellation must be respected; do not swallow CancellationException.
- Errors are translated into typed domain/UI outcomes at the appropriate boundary; do not leak raw stack traces into normal UI.

## 6. Critical separation from Hook runtime

The Compose/MVVM/MVI architecture applies to the launcher application only. It must not become the owner of runtime Hook installation.

- Hook installation starts from the LSPosed/Xposed module entry and target-process lifecycle, not from an Activity, Compose screen or ViewModel.
- Hook callbacks must not depend on a ViewModel, Compose state, navigation state or launcher process.
- Hook runtime state is process-scoped and independent from UI state.
- UI communicates with the runtime through the repository/IPC boundary already supported by the app.
- A UI process restart must not reinstall hooks in the target process.
- A target-process restart must not require the launcher UI to be open.
- Do not pass framework callback objects, host ClassLoader instances or reflected host members into UI/domain presentation models.

## 7. Testing requirements

- ViewModel tests: initial state, actions, loading, success, failure, cancellation and repeated actions.
- Reducer tests: deterministic state transitions and invalid-action handling.
- Repository tests: persistence, IPC mapping, import/export compatibility and error translation.
- Compose UI tests: key interactions, accessibility semantics, loading/empty/error/content states.
- Regression tests: existing preference keys, exported components, IPC contract, storage paths and serialized formats remain compatible.
- Hook tests remain in the Hook runtime test matrix; UI tests cannot substitute for LSPosed/device verification.

## 8. Migration order

1. Inventory current Activity responsibilities and freeze externally visible contracts.
2. Extract configuration/data access behind repository interfaces without changing behavior.
3. Split Compose screens into Route and stateless Screen functions.
4. Add ViewModels and immutable StateFlow state screen by screen.
5. Introduce reducer-style MVI only for genuinely complex interaction flows.
6. Add tests before deleting old bridge/facade code.
7. Verify all build artifacts and real launcher-to-runtime IPC behavior.

No all-at-once rewrite. Keep each migration reversible and independently testable.
