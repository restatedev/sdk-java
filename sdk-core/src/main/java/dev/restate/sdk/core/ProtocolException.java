// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.sdk.common.TerminalException;

public class ProtocolException extends RuntimeException {

  static final int UNAUTHORIZED_CODE = 401;
  static final int NOT_FOUND_CODE = 404;
  public static final int UNSUPPORTED_MEDIA_TYPE_CODE = 415;
  public static final int INTERNAL_CODE = 500;
  @Deprecated public static final int UNSUPPORTED_FEATURE = 573;

  private final int code;

  public ProtocolException(String message, int code) {
    this(message, code, null);
  }

  public ProtocolException(String message, int code, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public int getCode() {
    return code;
  }

  static ProtocolException unexpectedNotificationVariant(Class<?> clazz) {
    return new ProtocolException(
        "Unexpected notification variant " + clazz.getName(), INTERNAL_CODE);
  }

  public static ProtocolException methodNotFound(String serviceName, String handlerName) {
    return new ProtocolException(
        "Cannot find handler '" + serviceName + "/" + handlerName + "'", NOT_FOUND_CODE);
  }

  @Deprecated
  public static ProtocolException idempotencyKeyIsEmpty() {
    return new ProtocolException(
        "The provided idempotency key is empty.",
        TerminalException.INTERNAL_SERVER_ERROR_CODE,
        null);
  }

  public static ProtocolException unauthorized(Throwable e) {
    return new ProtocolException("Unauthorized", UNAUTHORIZED_CODE, e);
  }
}
