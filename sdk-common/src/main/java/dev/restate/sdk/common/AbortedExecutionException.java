// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.common;

/** You MUST NOT catch this exception. */
public final class AbortedExecutionException extends Throwable {
  @SuppressWarnings("StaticAssignmentOfThrowable")
  public static final AbortedExecutionException INSTANCE = new AbortedExecutionException();

  @SuppressWarnings("unchecked")
  public static <E extends Throwable> void sneakyThrow() throws E {
    throw (E) AbortedExecutionException.INSTANCE;
  }

  private AbortedExecutionException() {
    super("AbortedExecutionException");
    setStackTrace(new StackTraceElement[] {});
  }
}
