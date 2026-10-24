# LocalRush Android — conventions

These rules keep three apps built by three engineers looking and behaving like one product. Follow them; if you need
to break one, document why in the PR.

## Packages & modules

- Roots: `in.localrush.core.*` (shared), `in.localrush.feature.*` (shared feature modules), `in.localrush.customer`,
  `in.localrush.rider`, `in.localrush.partner`. Kotlin requires the backtick: `` package `in`.localrush.customer ``.
- Inside an app, every feature lives in `feature/<name>/{ui,vm,data}`:
  - `ui` — composables only (screens, components). No network, no DB, no business logic.
  - `vm` — `ViewModel`s exposing one `StateFlow<UiState>` (+ an optional `SharedFlow<Event>` for one-shot events).
  - `data` — repositories, DTO→model mappers, Room DAOs/entities specific to the feature.
- Something needed by two apps goes into a `:core:*` or `:feature:*` module, never copy-pasted.
- Module dependency direction: `app → feature → core`, `core:push → core:auth → core:network → core:common`.
  `core:common` has no Android UI dependencies; `core:designsystem` has no network dependency.

## Networking & data

- Repositories return `Result<T, AppError>` (`in.localrush.core.common.result`). Never throw across the repository
  boundary; wrap Retrofit calls with `apiCall { }` / `apiCallUnit { }` which map exceptions and the backend
  error envelope `{"error":{"code","message","fields"}}` into `AppError`.
- All network DTOs are `@Serializable` data classes in `core:network/dto` (shared) or `feature/<x>/data` (feature-only),
  with `@SerialName` for snake_case and **defaults for every optional field** (`ignoreUnknownKeys` + `coerceInputValues`
  are on; a missing field must not crash the app). Decimal amounts are `String` on the wire → `Money.parse()`.
- Add endpoints to the matching Retrofit interface; public endpoints get `@Headers("X-No-Auth: true")`.
- Status/timeline strings always go through `OrderStatus.fromWire()` / `TimelineKey.fromWire()`; never compare raw strings.
- Realtime: use `WebSocketClient.Factory` (`orderTracking(id)`, `rider()`, `partner()`); collect `messages`, switch on
  `obj.type`. It already handles `?token=`, ping/pong, reconnect/backoff and 4401/4403/4404.
- Cache with Room (`:core:database`): `CachedJsonDao` for raw responses, or add a proper entity + DAO. Bump
  `LocalRushDatabase.VERSION`.

## UI

- Every screen handles **loading, empty, error (with retry) and offline**. Use `LoadingState`/shimmers, `EmptyState`,
  `ErrorState(error, onRetry)` and `OfflineBanner` from `core:designsystem`. `UiState<T>` in `core:common` is the default
  shape; richer sealed classes are fine as long as those four states exist.
- Only `core:designsystem` components and `MaterialTheme` tokens (`MaterialTheme.colorScheme`, `LRTheme.colors`,
  `LRTheme.spacing`). No hard-coded colours or font sizes in features. Cards use `LRCard` (16–20 dp radius).
- All user-visible strings live in `res/values/strings.xml` of the owning module (plurals via `<plurals>`). No literals in
  composables except developer-only previews.
- Keep business logic out of composables: formatting, validation and decisions belong in the ViewModel or `core:common`
  helpers (`Money.format()`, `DateTimeUtils`, `Geo`). Composables take state in and send events out.
- Collect flows with `collectAsStateWithLifecycle()`. Use `LaunchedEffect` for one-shot events; never `runBlocking`
  on the main thread; never block in `init {}` of a ViewModel.
- Navigation: type-safe routes (`@Serializable` objects/classes in `navigation/Routes.kt`), one `NavHost` per app.
  Deep links arrive via `DeepLinkHandler.pending`.
- Images: `LRImage` (Coil). Maps: `LRMap` + `Marker`/`RoutePolyline`; never add Google Maps or a WebView.

## Architecture

- MVVM: `@HiltViewModel` + `UiState` + repositories. ViewModels never hold `Context`/`Activity`.
- Dependency injection with Hilt everywhere; `@Singleton` for repositories, scoped Hilt components otherwise.
- Coroutines: inject dispatchers (`AppDispatchers`), use `viewModelScope`, structured concurrency, `Flow` for streams.
- Permissions are requested from the UI layer with `rememberLauncherForActivityResult`; state is read via
  `LocationPermissions.state(context)`. Background location is a **separate** request after foreground is granted.
- Auth: observe `SessionManager.state`; a `LoggedOut` emission must always return the user to the Auth route (the
  root NavHost already does this). Forced logouts (`token_revoked`, `account_suspended`) surface via
  `SessionManager.forcedLogout`.

## Quality

- Unit-test mappers, ViewModels (with `kotlinx-coroutines-test` + Turbine) and anything with branching logic.
  Network tests use MockWebServer; see `core/network/src/test`.
- Build must be warning-free in your module (`./BUILD.sh :app-<x>:assembleDebug`); fix deprecations instead of
  suppressing them.
- Commit messages: imperative, scoped (`customer: add cart screen`). One feature per PR.
