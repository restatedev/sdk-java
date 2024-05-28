// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.client;

import java.util.Objects;

public class SendResponse {

  public enum SendStatus {
    /** The request was sent for the first time. */
    ACCEPTED,
    /** The request was already sent beforehand. */
    PREVIOUSLY_ACCEPTED
  }

  private final SendStatus status;
  private final String invocationId;

  public SendResponse(SendStatus status, String invocationId) {
    this.status = status;
    this.invocationId = invocationId;
  }

  public SendStatus getStatus() {
    return status;
  }

  public String getInvocationId() {
    return invocationId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SendResponse that = (SendResponse) o;
    return status == that.status && Objects.equals(invocationId, that.invocationId);
  }

  @Override
  public int hashCode() {
    int result = Objects.hashCode(status);
    result = 31 * result + Objects.hashCode(invocationId);
    return result;
  }

  @Override
  public String toString() {
    return "SendResponse{" + "status=" + status + ", invocationId='" + invocationId + '\'' + '}';
  }
}
