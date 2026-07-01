// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin.internal

internal class MalformedRestateServiceException : Exception {
  constructor(
      serviceName: String,
      message: String,
  ) : super("Failed to instantiate Restate service '$serviceName'.\nReason: $message")

  constructor(
      serviceName: String,
      message: String,
      cause: Throwable,
  ) : super("Failed to instantiate Restate service '$serviceName'.\nReason: $message", cause)
}
