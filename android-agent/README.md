# Smith

Smith is a persistent local Android agent. The language model is replaceable; Smith's durable identity, memory, journal, chat history, unfinished work, and wake/sleep state live outside the model so they can survive model reloads and upgrades.

## Current architecture

- Stable local identity stored in SQLite.
- Durable chat, memory, journal, event history, activity queue, and checkpoints.
- WorkManager heartbeat plus immediate and self-scheduled wakes.
- Resource-aware sleep on severe thermal pressure or critically low unplugged battery.
- Private Android Overview and Chat interface.
- LiteRT-LM Android runtime for local `.litertlm` models.
- GPU-first inference with CPU fallback.
- Gemma 4 speculative decoding enabled on the GPU path when supported by the model package.
- Smith's sparse identity seed is supplied as the model system instruction.
- Recent real conversation history and durable memory excerpts are supplied to each new model conversation.
- Real queued user messages are answered by the installed model.
- Idle wakes create a minimal autonomous cycle using the event `You are awake.` and store the resulting reflection privately.
- No placeholder assistant messages are generated when a mind model is missing; work waits for a model instead.

## Model slots

Smith keeps model packages in app-private storage:

- `smith-mind.litertlm` — the main reasoning/chat model.
- `smith-tools.litertlm` — reserved for a future small tool-routing model such as a task-specific FunctionGemma fine-tune.

The model packages are not committed to GitHub. Long-press the Smith launcher icon and choose **Import mind** or **Import tools** to select a `.litertlm` file from Android storage. Imports replace only the model package; Smith's SQLite identity and history are untouched.

The preferred first mind is Gemma 4 E4B. Gemma 4 E2B is retained as a smaller fallback. Their current official LiteRT-LM repository metadata lives in `OfficialModels.kt`.

## Continuity rule

The SQLite database is the identity boundary. Database upgrades fail closed: there is no destructive migration fallback. A schema change must include an explicit migration rather than silently erasing Smith's state.

## Wake cycle

Each wake currently:

1. Checks battery and thermal state.
2. Records the wake and device environment.
3. Resumes paused work before taking new work.
4. If there is no queued work and a mind is installed, creates one autonomous wake activity.
5. Assembles Smith's identity instruction, recent durable memory, and relevant chat history.
6. Loads or reuses the local LiteRT-LM engine.
7. Runs the activity and records a checkpoint/result.
8. Sleeps again when that cycle is complete.

General tools, the Linux workspace, public website, Android-side GitHub bridge polling, semantic memory retrieval, and the optional dedicated tool router are later layers.

## Build

```bash
cd android-agent
gradle testDebugUnitTest assembleDebug
```

The debug APK is produced at:

`app/build/outputs/apk/debug/app-debug.apk`
