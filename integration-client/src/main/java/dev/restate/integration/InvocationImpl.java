// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.integration;

import com.google.protobuf.ByteString;
import dev.restate.ingestion.v1.IngestionInvocation;
import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Mutable {@link Invocation} backed directly by the {@link IngestionInvocation.Builder} inherited
 * from {@link InvocationMetadataImpl}; {@link #toProtoInvocation(long)} just stamps the offset and
 * builds, with no field copying.
 */
final class InvocationImpl extends InvocationMetadataImpl implements Invocation {

  @Override
  public Invocation setBody(byte @Nullable [] body) {
    if (body == null) {
      builder.clearPayload();
    } else {
      builder.setPayload(ByteString.copyFrom(body));
    }
    return this;
  }

  @Override
  public byte[] getBody() {
    return builder.getPayload().toByteArray();
  }

  @Override
  public Invocation setInvokeTime(@Nullable Instant invokeTime) {
    if (invokeTime == null) {
      builder.clearInvokeTimeTsMs();
    } else {
      builder.setInvokeTimeTsMs(invokeTime.toEpochMilli());
    }
    return this;
  }

  @Override
  public @Nullable Instant getInvokeTime() {
    return builder.hasInvokeTimeTsMs() ? Instant.ofEpochMilli(builder.getInvokeTimeTsMs()) : null;
  }

  @Override
  public Invocation setTraceparent(@Nullable String traceparent) {
    if (traceparent == null) {
      builder.clearTraceparent();
    } else {
      builder.setTraceparent(traceparent);
    }
    return this;
  }

  @Override
  public @Nullable String getTraceparent() {
    return builder.hasTraceparent() ? builder.getTraceparent() : null;
  }

  @Override
  public Invocation setTracestate(@Nullable String tracestate) {
    if (tracestate == null) {
      builder.clearTracestate();
    } else {
      builder.setTracestate(tracestate);
    }
    return this;
  }

  @Override
  public @Nullable String getTracestate() {
    return builder.hasTracestate() ? builder.getTracestate() : null;
  }

  @Override
  public Invocation setIdempotencyKey(@Nullable String idempotencyKey) {
    if (idempotencyKey == null) {
      builder.clearIdempotencyKey();
    } else {
      builder.setIdempotencyKey(idempotencyKey);
    }
    return this;
  }

  @Override
  public @Nullable String getIdempotencyKey() {
    return builder.hasIdempotencyKey() ? builder.getIdempotencyKey() : null;
  }

  // Covariant overrides so per-invocation chaining keeps the Invocation type. The mutation logic
  // lives once in InvocationMetadataImpl (against the shared builder); these only refine the type.

  @Override
  public Invocation setServiceName(@Nullable String serviceName) {
    super.setServiceName(serviceName);
    return this;
  }

  @Override
  public Invocation setHandlerName(@Nullable String handlerName) {
    super.setHandlerName(handlerName);
    return this;
  }

  @Override
  public Invocation setKey(@Nullable String key) {
    super.setKey(key);
    return this;
  }

  @Override
  public Invocation setScope(@Nullable String scope) {
    super.setScope(scope);
    return this;
  }

  @Override
  public Invocation setLimitKey(@Nullable String limitKey) {
    super.setLimitKey(limitKey);
    return this;
  }

  @Override
  public Invocation putHeader(String key, String value) {
    super.putHeader(key, value);
    return this;
  }

  @Override
  public Invocation setHeaders(@Nullable Map<String, String> headers) {
    super.setHeaders(headers);
    return this;
  }

  /** Stamp the producer-assigned offset and build the wire message (no field copying). */
  IngestionInvocation toProtoInvocation(long offset) {
    return builder.setOffset(offset).build();
  }
}
