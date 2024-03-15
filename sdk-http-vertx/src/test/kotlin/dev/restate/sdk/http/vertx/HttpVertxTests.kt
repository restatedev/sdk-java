// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.http.vertx

import dev.restate.sdk.JavaBlockingTests
import dev.restate.sdk.JavaCodegenTests
import dev.restate.sdk.core.TestDefinitions.TestExecutor
import dev.restate.sdk.core.TestDefinitions.TestSuite
import dev.restate.sdk.kotlin.KotlinCoroutinesTests
import dev.restate.sdk.kotlin.KtCodegenTests
import io.vertx.core.Vertx
import java.util.stream.Stream
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll

class HttpVertxTests : dev.restate.sdk.core.TestRunner() {

  lateinit var vertx: Vertx

  @BeforeAll
  fun beforeAll() {
    vertx = Vertx.vertx()
  }

  @AfterAll
  fun afterAll() {
    vertx.close().toCompletionStage().toCompletableFuture().get()
  }

  override fun executors(): Stream<TestExecutor> {
    return Stream.of(HttpVertxTestExecutor(vertx))
  }

  override fun definitions(): Stream<TestSuite> {
    return Stream.concat(
        Stream.concat(
            Stream.concat(JavaBlockingTests().definitions(), JavaCodegenTests().definitions()),
            Stream.concat(KotlinCoroutinesTests().definitions(), KtCodegenTests().definitions())),
        Stream.of(VertxExecutorsTest()))
  }
}
