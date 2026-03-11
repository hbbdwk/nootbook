# Code Review Reference

## Kotlin/Android Best Practices

### 1. Architecture

- **MVVM**: Use ViewModel for UI state, not Activity/Fragment
- **Repository Pattern**: Single source of truth for data
- **Clean Architecture**: UI → ViewModel → Repository → Data Source

### 2. Thread Safety

- Use `Dispatchers.IO` for blocking operations
- Use `Dispatchers.Main` for UI updates
- Prefer `Flow` over `LiveData` for data streams
- Use `viewModelScope` for ViewModel coroutines
- Use `lifecycleScope` for Activity/Fragment coroutines

### 3. Performance

- Use `ListAdapter` + `DiffUtil`
- Use image loading libraries with size constraints (Glide/Coil)
- Use pagination for large datasets
- Avoid memory leaks (watch for context references)
- Profile with Android Profiler

### 4. Error Handling

- Never use empty catch blocks
- Provide meaningful error messages
- Use custom exceptions for domain errors
- Show user-friendly error messages

### 5. Null Safety

- Use nullable types (`?`) appropriately
- Use safe calls (`?.`) and Elvis (`?:`)
- Avoid force unwrapping (`!!`) unless absolutely certain
- Use `requireNotNull()` for preconditions

### 6. Testing

- Unit test ViewModels
- Instrument test for Room
- Use mockito/kotest for mocking

## Common False Positives to Skip

- Style issues (use ktlint)
- Naming conventions (use ktlint)
- Unused imports (IDE catches this)
- Simple optimizations the compiler does
- Personal preferences not in style guide

## Files to Always Review

- `*ViewModel.kt` - State management
- `*Repository.kt` - Data layer
- `*Activity.kt`, `*Fragment.kt` - UI lifecycle
- `*Application.kt` - App-wide setup
