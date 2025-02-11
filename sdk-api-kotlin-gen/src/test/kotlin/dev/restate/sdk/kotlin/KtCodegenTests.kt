// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.sdk.core.AssertUtils.assertThatDiscovery
import dev.restate.sdk.core.MockMultiThreaded
import dev.restate.sdk.core.MockSingleThread
import dev.restate.sdk.core.TestDefinitions
import dev.restate.sdk.core.TestDefinitions.TestExecutor
import dev.restate.sdk.core.TestRunner
import dev.restate.sdk.core.generated.manifest.Handler
import dev.restate.sdk.core.generated.manifest.Input
import dev.restate.sdk.core.generated.manifest.Output
import dev.restate.sdk.core.generated.manifest.Service
import java.util.stream.Stream
import org.assertj.core.api.InstanceOfAssertFactories.type
import org.junit.jupiter.api.Test

class KtCodegenTests : TestRunner() {
  override fun executors(): Stream<TestExecutor> {
    return Stream.of(MockSingleThread.INSTANCE, MockMultiThreaded.INSTANCE)
  }

  public override fun definitions(): Stream<TestDefinitions.TestSuite> {
    return Stream.of(CodegenTest())
  }

  @Test
  fun checkCustomInputContentType() {
    assertThatDiscovery(CodegenTest.RawInputOutput())
        .extractingService("RawInputOutput")
        .extractingHandler("rawInputWithCustomCt")
        .extracting({ it.input }, type(Input::class.java))
        .extracting { it.contentType }
        .isEqualTo("application/vnd.my.custom")
  }

  @Test
  fun checkCustomInputAcceptContentType() {
    assertThatDiscovery(CodegenTest.RawInputOutput())
        .extractingService("RawInputOutput")
        .extractingHandler("rawInputWithCustomAccept")
        .extracting({ it.input }, type(Input::class.java))
        .extracting { it.contentType }
        .isEqualTo("application/*")
  }

  @Test
  fun checkCustomOutputContentType() {
    assertThatDiscovery(CodegenTest.RawInputOutput())
        .extractingService("RawInputOutput")
        .extractingHandler("rawOutputWithCustomCT")
        .extracting({ it.output }, type(Output::class.java))
        .extracting { it.contentType }
        .isEqualTo("application/vnd.my.custom")
  }

  @Test
  fun workflowType() {
    assertThatDiscovery(CodegenTest.MyWorkflow())
        .extractingService("MyWorkflow")
        .returns(Service.Ty.WORKFLOW) { obj -> obj.ty }
        .extractingHandler("run")
        .returns(Handler.Ty.WORKFLOW) { obj -> obj.ty }
  }
}
