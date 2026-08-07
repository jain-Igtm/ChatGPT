# Resident Agent — Continuity Core

This is the first executable layer of the resident-agent experiment.

It deliberately starts below the model layer. A language model can be swapped,
upgraded, unloaded, or restarted without erasing the resident's durable identity
or unfinished work.

## Implemented in this milestone

- A stable local identity created once and stored in SQLite.
- Append-only chat and event history.
- Durable memory records for every user and resident utterance.
- A persistent activity queue with `pending`, `running`, `paused`, and `complete`
  states.
- Per-activity checkpoints that survive process death, app restarts, and reboots.
- Autonomous wake cycles scheduled with WorkManager.
- Resource-aware sleep when the device reports severe thermal pressure or a
  critically low unplugged battery.
- A private Android dashboard and chat shell with file attachment selection.
- A placeholder cycle engine proving that a queued activity can be claimed,
  checkpointed, completed, and resumed from durable state.

## What is intentionally not connected yet

- Gemma 4 E4B inference.
- FunctionGemma tool-call specialization.
- Semantic retrieval and memory consolidation.
- The public self-directed website.
- GitHub bridge polling from Android.
- General tools, Linux workspace, photo interpretation, and autonomous projects.

The placeholder response explicitly says when no model is connected. It is not
pretending that the resident mind already exists.

## Continuity invariant

The SQLite database is the continuity boundary. Database upgrades are
fail-closed: there is no destructive fallback. Every schema change must have an
explicit migration so an app update cannot silently erase the resident.

## Wake model

WorkManager supplies a reliable periodic heartbeat and immediate wake requests.
Each wake:

1. Inspects battery and thermal state.
2. Records the wake.
3. Claims a paused activity before a new pending activity.
4. Saves a checkpoint.
5. Performs one bounded cycle.
6. Sleeps again with a recorded reason.

The future model will be able to schedule an additional one-time wake through
`AgentScheduler.scheduleWake(...)`.

## Build

```bash
cd android-agent
gradle testDebugUnitTest assembleDebug
```

The debug APK is produced at:

`app/build/outputs/apk/debug/app-debug.apk`
