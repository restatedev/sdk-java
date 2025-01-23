// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

/** Interface to verify requests. */
public interface RequestIdentityVerifier {

  /**
   * @throws Exception if the request cannot be verified
   */
  void verifyRequest(EndpointRequestHandler.Headers headers) throws Exception;

  /**
   * @return a noop request identity verifier
   */
  static RequestIdentityVerifier noop() {
    return headers -> {};
  }
}
