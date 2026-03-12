# Local Model Setup (MVP)

This project now prefers `LlamaCppSummaryGenerator` first, then falls back to `GeminiNanoSummaryGenerator`.

## Model path

Place model at:

`/data/data/com.example.notebook/files/models/qwen2.5-1.5b-instruct-q4_k_m.gguf`

Android-side resolved path:

`File(context.filesDir, "models/qwen2.5-1.5b-instruct-q4_k_m.gguf")`

## Current status

- Llama generator wiring is added.
- JNI bridge is added (`LlamaNativeBridge` + `llama_bridge.cpp`).
- Native build is wired through CMake (`app/src/main/cpp/CMakeLists.txt`).
- `llama_jni` is now compiled as part of app native build.
- If model is unavailable, manager falls back to existing generator path.

## Remaining for real llama.cpp inference

1. Replace MVP native summarize logic with real llama.cpp context + token generation.
2. Add first-run model copy/download strategy.
3. Optionally add ABI split and model version management.
