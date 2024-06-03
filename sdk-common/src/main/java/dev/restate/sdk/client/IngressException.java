// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.client;

import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

public class IngressException extends RuntimeException {

  private final int statusCode;
  private final byte[] responseBody;

  public IngressException(String message, Throwable cause) {
    this(message, -1, null, cause);
  }

  public IngressException(String message, int statusCode, byte[] responseBody) {
    this(message, statusCode, responseBody, null);
  }

  public IngressException(String message, int statusCode, byte[] responseBody, Throwable cause) {
    super(message, cause);
    this.statusCode = statusCode;
    this.responseBody = responseBody;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public byte @Nullable [] getResponseBody() {
    return responseBody;
  }

  @Override
  public String getMessage() {
    return "["
        + statusCode
        + "] "
        + super.getMessage()
        + ". Got response body: "
        + new String(responseBody, StandardCharsets.UTF_8);
  }
}
