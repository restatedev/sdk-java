// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.kotlinapi.reflections

import dev.restate.sdk.annotation.*
import dev.restate.sdk.kotlin.*
import kotlinx.coroutines.delay

@Service
@Name("Empty")
open class Empty {
  @Handler
  open suspend fun emptyInput(): String {
    return service<Empty>().emptyInput()
  }

  @Handler
  open suspend fun emptyOutput(request: String) {
    service<Empty>().emptyOutput(request)
  }

  @Handler
  open suspend fun emptyInputOutput() {
    service<Empty>().emptyInputOutput()
  }
}

@Service
@Name("PrimitiveTypes")
open class PrimitiveTypes {
  @Handler
  open suspend fun primitiveOutput(): Int {
    return service<PrimitiveTypes>().primitiveOutput()
  }

  @Handler
  open suspend fun primitiveInput(input: Int) {
    service<PrimitiveTypes>().primitiveInput(input)
  }
}

@VirtualObject
open class CornerCases {

  @Exclusive
  open suspend fun returnNull(request: String?): String? {
    return virtualObject<CornerCases>(objectKey()).returnNull(request)
  }

  @Exclusive
  open suspend fun badReturnTypeInferred(): Unit {
    toVirtualObject<CornerCases>(objectKey()).request { badReturnTypeInferred() }.send()
  }

  @Exclusive
  open suspend fun callSuspendWithinProxy() {
    toVirtualObject<CornerCases>(objectKey())
        .request {
          // Doing a suspend call within the proxy
          delay(1)
          callSuspendWithinProxy()
        }
        .send()
  }
}

@Service
@Name("RawInputOutput")
open class RawInputOutput {
  @Handler @Raw open suspend fun rawOutput(): ByteArray = service<RawInputOutput>().rawOutput()

  @Handler
  @Raw(contentType = "application/vnd.my.custom")
  open suspend fun rawOutputWithCustomCT(): ByteArray =
      service<RawInputOutput>().rawOutputWithCustomCT()

  @Handler
  open suspend fun rawInput(@Raw input: ByteArray) {
    service<RawInputOutput>().rawInput(input)
  }

  @Handler
  open suspend fun rawInputWithCustomCt(
      @Raw(contentType = "application/vnd.my.custom") input: ByteArray
  ) {
    service<RawInputOutput>().rawInputWithCustomCt(input)
  }

  @Handler
  open suspend fun rawInputWithCustomAccept(
      @Accept("application/*") @Raw(contentType = "application/vnd.my.custom") input: ByteArray
  ) {
    service<RawInputOutput>().rawInputWithCustomAccept(input)
  }
}

@Workflow
@Name("MyWorkflow")
open class MyWorkflow {
  @Workflow
  open suspend fun run(myInput: String) {
    toWorkflow<MyWorkflow>(workflowKey()).request { sharedHandler(myInput) }.send()
  }

  @Handler
  open suspend fun sharedHandler(myInput: String): String =
      workflow<MyWorkflow>(workflowKey()).sharedHandler(myInput)
}

@Service
@Name("MyExplicitName")
interface GreeterWithExplicitName {
  @Handler @Name("my_greeter") suspend fun greet(request: String): String
}
