// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.client;

import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

public class IngressException extends RuntimeException {

  private final String requestMethod;
  private final String requestURI;
  private final int statusCode;
  private final byte[] responseBody;

  public IngressException(
      String message,
      String requestMethod,
      String requestURI,
      int statusCode,
      byte[] responseBody,
      Throwable cause) {
    super(message, cause);
    this.statusCode = statusCode;
    this.responseBody = responseBody;
    this.requestMethod = requestMethod;
    this.requestURI = requestURI;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public byte @Nullable [] getResponseBody() {
    return responseBody;
  }

  public String getRequestMethod() {
    return requestMethod;
  }

  public String getRequestURI() {
    return requestURI;
  }

  @Override
  public String getMessage() {
    return "["
        + requestMethod
        + " "
        + requestURI
        + "][Status: "
        + statusCode
        + "] "
        + super.getMessage()
        + ". Got response body: "
        + new String(responseBody, StandardCharsets.UTF_8);
  }
}
