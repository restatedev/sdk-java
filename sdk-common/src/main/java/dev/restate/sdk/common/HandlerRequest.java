// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.common;

import dev.restate.common.Slice;
import dev.restate.sdk.endpoint.definition.HandlerType;
import dev.restate.sdk.endpoint.definition.ServiceType;
import io.opentelemetry.context.Context;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** This class encapsulates the inputs to a handler. */
public final class HandlerRequest {
  private final InvocationId invocationId;
  private final Context otelContext;
  private final Slice body;
  private final Map<String, String> headers;
  private final String serviceName;
  private final String handlerName;
  private final ServiceType serviceType;
  private final @Nullable HandlerType handlerType;

  public HandlerRequest(
      InvocationId invocationId,
      Context otelContext,
      Slice body,
      Map<String, String> headers,
      String serviceName,
      String handlerName,
      ServiceType serviceType,
      @Nullable HandlerType handlerType) {
    this.invocationId = invocationId;
    this.otelContext = otelContext;
    this.body = body;
    this.headers = headers;
    this.serviceName = serviceName;
    this.handlerName = handlerName;
    this.serviceType = serviceType;
    this.handlerType = handlerType;
  }

  public InvocationId invocationId() {
    return invocationId;
  }

  /**
   * @deprecated Use the new {@code sdk-interceptor-opentelemetry} module instead
   */
  @Deprecated(forRemoval = true)
  public Context openTelemetryContext() {
    return otelContext;
  }

  public Slice body() {
    return body;
  }

  public byte[] bodyAsByteArray() {
    return body.toByteArray();
  }

  public ByteBuffer bodyAsBodyBuffer() {
    return body.asReadOnlyByteBuffer();
  }

  public Map<String, String> headers() {
    return headers;
  }

  /** Name of the service being invoked. */
  public String serviceName() {
    return serviceName;
  }

  /** Name of the handler being invoked. */
  public String handlerName() {
    return handlerName;
  }

  /** Type of the service being invoked. */
  public ServiceType serviceType() {
    return serviceType;
  }

  /** Type of the handler being invoked. {@code null} when {@code serviceType == SERVICE}. */
  public @Nullable HandlerType handlerType() {
    return handlerType;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (HandlerRequest) obj;
    return Objects.equals(this.invocationId, that.invocationId)
        && Objects.equals(this.otelContext, that.otelContext)
        && Objects.equals(this.body, that.body)
        && Objects.equals(this.headers, that.headers)
        && Objects.equals(this.serviceName, that.serviceName)
        && Objects.equals(this.handlerName, that.handlerName)
        && this.serviceType == that.serviceType
        && this.handlerType == that.handlerType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        invocationId,
        otelContext,
        body,
        headers,
        serviceName,
        handlerName,
        serviceType,
        handlerType);
  }

  @Override
  public String toString() {
    return "HandlerRequest["
        + "invocationId="
        + invocationId
        + ", "
        + "serviceName="
        + serviceName
        + ", "
        + "handlerName="
        + handlerName
        + ", "
        + "serviceType="
        + serviceType
        + ", "
        + "handlerType="
        + handlerType
        + ", "
        + "otelContext="
        + otelContext
        + ", "
        + "body="
        + body
        + ", "
        + "headers="
        + headers
        + ']';
  }
}
