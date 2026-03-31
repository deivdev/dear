# Dear

Android app for on-device LLM-powered diary assistance. Features a timeline of diary entries and a chat interface with a local language model.

## Stack

- Kotlin + Jetpack Compose
- llama.cpp for on-device LLM inference
- KSP (Kotlin Symbol Processing)

## Architecture

```
app/src/main/java/dev/deiv/dear/
├── DearApp.kt              # Application entry point
├── llm/
│   ├── LlamaModel.kt       # llama.cpp bindings
│   ├── ModelManager.kt      # Model lifecycle management
│   └── DiaryAssistant.kt   # LLM-powered diary assistant
├── service/
│   └── LocationService.kt  # Location tracking
└── ui/
    ├── MainActivity.kt
    ├── timeline/            # Diary timeline view
    └── chat/                # Chat with LLM view
```

## Build

```bash
./gradlew assembleDebug
```
