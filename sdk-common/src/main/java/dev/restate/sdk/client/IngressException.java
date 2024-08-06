// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.client;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

public class IngressException extends RuntimeException {

  private final String requestMethod;
  private final String requestURI;
  private final int statusCode;
  private final byte[] responseBody;

  public IngressException(
      String message, String requestMethod, String requestURI, Throwable cause) {
    this(message, requestMethod, requestURI, -1, null, cause);
  }

  IngressException(String message, HttpRequest request, Throwable cause) {
    this(message, request.method(), request.uri().toString(), -1, null, cause);
  }

  IngressException(String message, HttpRequest request) {
    this(message, request, null);
  }

  public IngressException(
      String message,
      String requestMethod,
      String requestURI,
      int statusCode,
      byte[] responseBody) {
    this(message, requestMethod, requestURI, statusCode, responseBody, null);
  }

  IngressException(String message, HttpResponse<byte[]> response, Throwable cause) {
    this(
        message,
        response.request().method(),
        response.request().uri().toString(),
        response.statusCode(),
        response.body(),
        cause);
  }

  IngressException(String message, HttpResponse<byte[]> response) {
    this(message, response, null);
  }

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
