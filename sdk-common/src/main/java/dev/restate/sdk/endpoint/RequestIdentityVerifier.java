// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.endpoint;

import org.jspecify.annotations.Nullable;

/** Interface to verify requests. */
public interface RequestIdentityVerifier {

  /** Abstraction for headers map. */
  @FunctionalInterface
  interface Headers {
    @Nullable String get(String key);
  }

  /**
   * @throws Exception if the request cannot be verified
   */
  void verifyRequest(Headers headers) throws Exception;
}
