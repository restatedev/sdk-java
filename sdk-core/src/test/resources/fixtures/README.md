# Protocol test fixtures

Pre-encoded service-protocol byte sequences used by `StateMachineTest` to drive
happy-path flows that cross the WASM boundary. The SDK has no protobuf encoder
of its own ([[feedback-shared-core-design-constraints]]), so wire bytes are
generated from `sdk-shared-core` and committed as opaque blobs.

## Files

- `start_only.bin` — a single `StartMessage` frame (known_entries=1).
- `input_only.bin` — a single `InputCommandMessage` frame carrying `"my-data"`.
- `start_plus_input.bin` — the two frames concatenated; the simplest valid
  wire input that drives `notify_input → is_ready_to_execute → sys_input`.

## Regenerating

Run the ignored test in sdk-shared-core that emits these files, then copy them
in:

```
cd /path/to/sdk-shared-core
RESTATE_FIXTURES_OUT=/tmp/restate-fixtures \
  cargo test --lib -- --ignored dump_protocol_fixtures --nocapture
cp /tmp/restate-fixtures/*.bin \
  /path/to/sdk-java/sdk-core/src/test/resources/fixtures/
```

The fixtures encode the maximum supported protocol version. If the wire format
changes incompatibly, regenerate and update tests as needed.
