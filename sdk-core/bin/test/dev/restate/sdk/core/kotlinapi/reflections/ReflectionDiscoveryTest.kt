// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.kotlinapi.reflections

import dev.restate.sdk.core.AssertUtils.assertThatDiscovery
import dev.restate.sdk.core.generated.manifest.Handler
import dev.restate.sdk.core.generated.manifest.Input
import dev.restate.sdk.core.generated.manifest.Output
import dev.restate.sdk.core.generated.manifest.Service
import dev.restate.sdk.kotlin.endpoint.*
import dev.restate.serde.Serde
import org.assertj.core.api.InstanceOfAssertFactories.type
import org.junit.jupiter.api.Test

class ReflectionDiscoveryTest {

  @Test
  fun checkCustomInputContentType() {
    assertThatDiscovery(RawInputOutput())
        .extractingService("RawInputOutput")
        .extractingHandler("rawInputWithCustomCt")
        .extracting({ it.input }, type(Input::class.java))
        .extracting { it.contentType }
        .isEqualTo("application/vnd.my.custom")
  }

  @Test
  fun checkCustomInputAcceptContentType() {
    assertThatDiscovery(RawInputOutput())
        .extractingService("RawInputOutput")
        .extractingHandler("rawInputWithCustomAccept")
        .extracting({ it.input }, type(Input::class.java))
        .extracting { it.contentType }
        .isEqualTo("application/*")
  }

  @Test
  fun checkCustomOutputContentType() {
    assertThatDiscovery(RawInputOutput())
        .extractingService("RawInputOutput")
        .extractingHandler("rawOutputWithCustomCT")
        .extracting({ it.output }, type(Output::class.java))
        .extracting { it.contentType }
        .isEqualTo("application/vnd.my.custom")
  }

  @Test
  fun checkRawInputContentType() {
    assertThatDiscovery(RawInputOutput())
        .extractingService("RawInputOutput")
        .extractingHandler("rawInput")
        .extracting({ it.input }, type(Input::class.java))
        .extracting { it.contentType }
        .isEqualTo(Serde.RAW.contentType())
  }

  @Test
  fun checkRawOutputContentType() {
    assertThatDiscovery(RawInputOutput())
        .extractingService("RawInputOutput")
        .extractingHandler("rawOutput")
        .extracting({ it.output }, type(Output::class.java))
        .extracting { it.contentType }
        .isEqualTo(Serde.RAW.contentType())
  }

  @Test
  fun explicitNames() {
    assertThatDiscovery(
            object : GreeterWithExplicitName {
              override suspend fun greet(request: String): String {
                TODO("Not yet implemented")
              }
            }
        )
        .extractingService("MyExplicitName")
        .extractingHandler("my_greeter")
  }

  @Test
  fun workflowType() {
    assertThatDiscovery(MyWorkflow())
        .extractingService("MyWorkflow")
        .returns(Service.Ty.WORKFLOW) { obj -> obj.ty }
        .extractingHandler("run")
        .returns(Handler.Ty.WORKFLOW) { obj -> obj.ty }
  }

  @Test
  fun usingTransformer() {
    assertThatDiscovery(
            endpoint {
              bind(RawInputOutput()) {
                it.documentation = "My service documentation"
                it.configureHandler("rawInputWithCustomCt") {
                  it.documentation = "My handler documentation"
                }
              }
            }
        )
        .extractingService("RawInputOutput")
        .returns("My service documentation", Service::getDocumentation)
        .extractingHandler("rawInputWithCustomCt")
        .returns("My handler documentation", Handler::getDocumentation)
  }
}
