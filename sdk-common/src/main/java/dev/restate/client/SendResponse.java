// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.client;

public record SendResponse(
        SendStatus status,
        String invocationId
) {
  public enum SendStatus {
    /** The request was sent for the first time. */
    ACCEPTED,
    /** The request was already sent beforehand. */
    PREVIOUSLY_ACCEPTED
  }
}
