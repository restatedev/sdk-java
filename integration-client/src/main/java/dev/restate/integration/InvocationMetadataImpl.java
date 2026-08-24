// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.integration;

import dev.restate.ingestion.v1.IngestionDefaults;
import dev.restate.ingestion.v1.IngestionInvocation;
import java.util.Map;

/**
 * Mutable {@link InvocationMetadata} backed directly by an {@link IngestionInvocation.Builder}, so
 * setters write straight to the wire object with no intermediate field copy. {@link InvocationImpl}
 * extends this and reuses the same builder; when the object is used as producer defaults, {@link
 * #toDefaults()} projects the shared fields onto an {@link IngestionDefaults}.
 */
sealed class InvocationMetadataImpl implements InvocationMetadata permits InvocationImpl {

  final IngestionInvocation.Builder builder = IngestionInvocation.newBuilder();

  @Override
  public InvocationMetadata setServiceName(String serviceName) {
    if (serviceName == null) {
      builder.clearService();
    } else {
      builder.setService(serviceName);
    }
    return this;
  }

  @Override
  public String getServiceName() {
    return builder.hasService() ? builder.getService() : null;
  }

  @Override
  public InvocationMetadata setHandlerName(String handlerName) {
    if (handlerName == null) {
      builder.clearHandler();
    } else {
      builder.setHandler(handlerName);
    }
    return this;
  }

  @Override
  public String getHandlerName() {
    return builder.hasHandler() ? builder.getHandler() : null;
  }

  @Override
  public InvocationMetadata setKey(String key) {
    if (key == null) {
      builder.clearKey();
    } else {
      builder.setKey(key);
    }
    return this;
  }

  @Override
  public String getKey() {
    return builder.hasKey() ? builder.getKey() : null;
  }

  @Override
  public InvocationMetadata setScope(String scope) {
    if (scope == null) {
      builder.clearScope();
    } else {
      builder.setScope(scope);
    }
    return this;
  }

  @Override
  public String getScope() {
    return builder.hasScope() ? builder.getScope() : null;
  }

  @Override
  public InvocationMetadata setLimitKey(String limitKey) {
    if (limitKey == null) {
      builder.clearLimitKey();
    } else {
      builder.setLimitKey(limitKey);
    }
    return this;
  }

  @Override
  public String getLimitKey() {
    return builder.hasLimitKey() ? builder.getLimitKey() : null;
  }

  @Override
  public InvocationMetadata setIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null) {
      builder.clearIdempotencyKey();
    } else {
      builder.setIdempotencyKey(idempotencyKey);
    }
    return this;
  }

  @Override
  public String getIdempotencyKey() {
    return builder.hasIdempotencyKey() ? builder.getIdempotencyKey() : null;
  }

  @Override
  public InvocationMetadata putHeader(String key, String value) {
    builder.putAdditionalHeaders(key, value);
    return this;
  }

  @Override
  public InvocationMetadata setHeaders(Map<String, String> headers) {
    builder.clearAdditionalHeaders();
    if (headers != null) {
      builder.putAllAdditionalHeaders(headers);
    }
    return this;
  }

  @Override
  public Map<String, String> getHeaders() {
    return builder.getAdditionalHeadersMap();
  }

  /** Project the shared fields onto an {@code IngestionDefaults} for the producer Start frame. */
  IngestionDefaults toDefaults() {
    IngestionDefaults.Builder d = IngestionDefaults.newBuilder();
    if (builder.hasService()) {
      d.setService(builder.getService());
    }
    if (builder.hasHandler()) {
      d.setHandler(builder.getHandler());
    }
    if (builder.hasKey()) {
      d.setKey(builder.getKey());
    }
    if (builder.hasScope()) {
      d.setScope(builder.getScope());
    }
    if (builder.hasLimitKey()) {
      d.setLimitKey(builder.getLimitKey());
    }
    if (builder.hasIdempotencyKey()) {
      d.setIdempotencyKey(builder.getIdempotencyKey());
    }
    d.putAllHeaders(builder.getAdditionalHeadersMap());
    return d.build();
  }
}
