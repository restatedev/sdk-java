// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.common.function;

/** Like {@link Runnable} but can throw checked exceptions. */
@FunctionalInterface
public interface ThrowingRunnable {

  /** Run, potentially throwing an exception. */
  void run() throws Throwable;
}
