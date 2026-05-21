# Dear

Android app. Second brain on-device, powered by local LLM.

## Goals

1. **Collect everything.** Single inbox for video, image, text, audio, links — anything worth keeping.
2. **Frictionless capture.** Register as Android share target — send from any app via the system share sheet. One tap, done.
3. **Auto-catalog.** Every input gets classified, tagged, and indexed automatically by the on-device LLM. No manual sorting.
4. **Find anything later.** Keyword/semantic search across months of captures.

## Stack

- Kotlin + Jetpack Compose
- llama.cpp for on-device LLM inference (JNI)
- Room for local persistence
- KSP (Kotlin Symbol Processing)

## Privacy

On-device by default. No telemetry, no location tracking. All entries stored in app-internal storage. LLM inference local via llama.cpp + GGUF model on device.

**Network use:** when you save a LINK entry, the app fetches that URL directly (no proxy) to read its `<title>`/Open Graph metadata for cataloging. The destination site sees your IP and the URL. All other features (text notes, shared media, chat, search) are fully offline.

## Architecture

```
app/src/main/java/dev/deiv/dear/
├── DearApp.kt              # Application entry point
├── llm/
│   ├── LlamaModel.kt       # llama.cpp JNI bindings
│   ├── ModelManager.kt     # Model lifecycle
│   └── DiaryAssistant.kt   # Cataloging + chat logic
├── data/
│   ├── db/                 # Room DB, DAOs (EntryDao)
│   ├── model/              # Entry entity (table: entries)
│   └── repository/         # EntryRepository
└── ui/
    ├── MainActivity.kt
    ├── capture/            # Manual note entry
    ├── share/              # ShareReceiverActivity (system share target)
    ├── timeline/           # Chronological view of all captures
    ├── chat/               # Query/chat with collection
    └── theme/
```

## Build

```bash
./gradlew assembleDebug   # build APK
./gradlew installDebug    # build + install on connected device
```

First build pulls the `llama.cpp` submodule:

```bash
git submodule update --init --recursive
```
