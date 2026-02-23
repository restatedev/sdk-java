// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.springboot.kotlin

import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Name
import dev.restate.sdk.kotlin.runBlock
import dev.restate.sdk.springboot.RestateService
import org.springframework.beans.factory.annotation.Value

@RestateService
@Name("greeterNewApi")
open class GreeterNewApi {
  @Value($$"${greetingPrefix}") internal lateinit var greetingPrefix: String

  @Handler
  open suspend fun greet(person: String): String {
    return runBlock { greetingPrefix } + person
  }
}
