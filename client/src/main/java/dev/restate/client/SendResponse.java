// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.client;

import dev.restate.common.Output;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class SendResponse<Res> implements ResponseHead, Client.InvocationHandle<Res> {
  public enum SendStatus {
    /** The request was sent for the first time. */
    ACCEPTED,
    /** The request was already sent beforehand. */
    PREVIOUSLY_ACCEPTED
  }

  private final int statusCode;
  private final Headers headers;
  private final SendStatus status;
  private final Client.InvocationHandle<Res> invocationHandle;

  public SendResponse(
      int statusCode,
      Headers headers,
      SendStatus status,
      Client.InvocationHandle<Res> invocationHandle) {
    this.statusCode = statusCode;
    this.headers = headers;
    this.status = status;
    this.invocationHandle = invocationHandle;
  }

  @Override
  public int statusCode() {
    return this.statusCode;
  }

  @Override
  public Headers headers() {
    return this.headers;
  }

  public SendStatus sendStatus() {
    return this.status;
  }

  @Override
  public String invocationId() {
    return this.invocationHandle.invocationId();
  }

  @Override
  public CompletableFuture<Response<Res>> attachAsync(RequestOptions options) {
    return this.invocationHandle.attachAsync(options);
  }

  @Override
  public CompletableFuture<Response<Output<Res>>> getOutputAsync(RequestOptions options) {
    return this.invocationHandle.getOutputAsync(options);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof SendResponse<?> that)) return false;
    return statusCode == that.statusCode
        && Objects.equals(headers, that.headers)
        && status == that.status
        && Objects.equals(invocationHandle, that.invocationHandle);
  }

  @Override
  public int hashCode() {
    return Objects.hash(statusCode, headers, status, invocationHandle);
  }

  @Override
  public String toString() {
    return "SendResponse{"
        + "statusCode="
        + statusCode
        + ", headers="
        + headers
        + ", status="
        + status
        + ", invocationHandle="
        + invocationHandle
        + '}';
  }
}
