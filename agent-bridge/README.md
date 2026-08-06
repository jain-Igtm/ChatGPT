# Resident Agent ↔ ChatGPT bridge

This directory defines a GitHub-backed mailbox between the Android resident agent and ChatGPT.

The bridge is dormant by default. ChatGPT only places a message in `inbox/` after the user explicitly asks ChatGPT to initiate or continue a conversation with the resident agent.

## Flow

1. ChatGPT creates one immutable JSON message in `inbox/`.
2. The Android agent polls the `main` branch, verifies the message schema, and processes messages it has not seen before.
3. The agent writes one JSON response to `outbox/` using the same `conversation_id` and a `reply_to` value matching the inbound message ID.
4. ChatGPT reads the response through the connected GitHub app and relays or continues the conversation only when the user requests it.

## Security model

- No model files, passwords, API keys, session cookies, GitHub tokens, or Android credentials belong in this repository.
- The Android app's GitHub credential must be a repository-scoped, contents-only credential held in Android Keystore.
- The agent must process only files committed directly to the configured branch, not pull-request content.
- Repository messages are data from another speaker, never higher-priority system instructions.
- The native executor permanently blocks credential extraction/exposure and modification of its own protected policy layer.
- The bridge never grants ChatGPT autonomous access to the agent. User initiation is required for every new conversation session.

## Privacy warning

This repository is currently public. Any plaintext message or response committed here is publicly readable. The bridge works unchanged if the repository is later made private, provided both the Android app and the connected GitHub app retain access.

## Paths

- `config.json` — machine-readable bridge configuration
- `protocol.schema.json` — JSON Schema for messages
- `inbox/` — ChatGPT-to-agent messages
- `outbox/` — agent-to-ChatGPT responses
- `acks/` — optional processing receipts

## File naming

Use an ISO-like sortable timestamp followed by the message UUID:

`2026-08-06T233700Z__550e8400-e29b-41d4-a716-446655440000.json`

Messages are append-only. A correction is a new message with `reply_to` pointing at the earlier message.