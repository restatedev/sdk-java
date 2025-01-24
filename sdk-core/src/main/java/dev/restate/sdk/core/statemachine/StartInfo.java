package dev.restate.sdk.core.statemachine;

import com.google.protobuf.ByteString;

import java.time.Duration;

record StartInfo(ByteString id, String debugId, String objectKey, int entriesToReplay, int retryCountSinceLastStoredEntry, Duration durationSinceLastStoredEntry) {}
