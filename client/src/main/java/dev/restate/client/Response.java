// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.client;

import java.util.Objects;

public final class Response<Res> implements ResponseHead {

  private final int statusCode;
  private final Headers headers;
  private final Res response;

  public Response(int statusCode, Headers headers, Res response) {
    this.statusCode = statusCode;
    this.headers = headers;
    this.response = response;
  }

  @Override
  public int statusCode() {
    return statusCode;
  }

  @Override
  public Headers headers() {
    return headers;
  }

  public Res response() {
    return response;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (Response<?>) obj;
    return this.statusCode == that.statusCode
        && Objects.equals(this.headers, that.headers)
        && Objects.equals(this.response, that.response);
  }

  @Override
  public int hashCode() {
    return Objects.hash(statusCode, headers, response);
  }

  @Override
  public String toString() {
    return "ClientResponse["
        + "statusCode="
        + statusCode
        + ", "
        + "headers="
        + headers
        + ", "
        + "response="
        + response
        + ']';
  }
}
