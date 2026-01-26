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
import dev.restate.serde.Serde
import dev.restate.serde.SerdeFactory
import dev.restate.serde.TypeRef
import dev.restate.serde.TypeTag
import dev.restate.serde.kotlinx.KotlinSerializationSerdeFactory
import kotlinx.serialization.Serializable

@Service
class ServiceGreeter {
  @Handler
  suspend fun greet(request: String): String {
    return request
  }
}

@VirtualObject
class ObjectGreeter {
  @Exclusive
  suspend fun greet(request: String): String {
    return request
  }

  @Handler
  @Shared
  suspend fun sharedGreet(request: String): String {
    return request
  }
}

@VirtualObject
class NestedDataClass {
  @Serializable data class Input(val a: String)

  @Serializable data class Output(val a: String)

  @Exclusive
  suspend fun greet(request: Input): Output {
    return Output(request.a)
  }

  @Exclusive
  suspend fun complexType(request: Map<String, List<out Input>>): Map<String, List<out Output>> {
    return mapOf()
  }
}

@VirtualObject
interface GreeterInterface {
  @Exclusive suspend fun greet(request: String): String
}

class ObjectGreeterImplementedFromInterface : GreeterInterface {
  override suspend fun greet(request: String): String {
    return request
  }
}

@Service
@Name("Empty")
class Empty {
  @Handler
  suspend fun emptyInput(): String {
    return service<Empty>().emptyInput()
  }

  @Handler
  suspend fun emptyOutput(request: String) {
    service<Empty>().emptyOutput(request)
  }

  @Handler
  suspend fun emptyInputOutput() {
    service<Empty>().emptyInputOutput()
  }
}

@Service
@Name("PrimitiveTypes")
class PrimitiveTypes {
  @Handler
  suspend fun primitiveOutput(): Int {
    return service<PrimitiveTypes>().primitiveOutput()
  }

  @Handler
  suspend fun primitiveInput(input: Int) {
    service<PrimitiveTypes>().primitiveInput(input)
  }
}

@VirtualObject
class CornerCases {

  @Exclusive
  suspend fun returnNull(request: String?): String? {
    return virtualObject<CornerCases>(key()).returnNull(request)
  }

  @Exclusive
  suspend fun badReturnTypeInferred(): Unit {
    virtualObjectHandle<CornerCases>(key()).request(CornerCases::badReturnTypeInferred).send()
  }
}

@Service
@Name("RawInputOutput")
class RawInputOutput {
  @Handler @Raw suspend fun rawOutput(): ByteArray = service<RawInputOutput>().rawOutput()

  @Handler
  @Raw(contentType = "application/vnd.my.custom")
  suspend fun rawOutputWithCustomCT(): ByteArray = service<RawInputOutput>().rawOutputWithCustomCT()

  @Handler
  suspend fun rawInput(@Raw input: ByteArray) {
    service<RawInputOutput>().rawInput(input)
  }

  @Handler
  suspend fun rawInputWithCustomCt(
      @Raw(contentType = "application/vnd.my.custom") input: ByteArray
  ) {
    service<RawInputOutput>().rawInputWithCustomCt(input)
  }

  @Handler
  suspend fun rawInputWithCustomAccept(
      @Accept("application/*") @Raw(contentType = "application/vnd.my.custom") input: ByteArray
  ) {
    service<RawInputOutput>().rawInputWithCustomAccept(input)
  }
}

@Workflow
@Name("MyWorkflow")
class MyWorkflow {
  @Workflow
  suspend fun run(myInput: String) {
    workflowHandle<MyWorkflow>(key()).request(MyWorkflow::sharedHandler, myInput).send()
  }

  @Handler
  suspend fun sharedHandler(myInput: String): String =
      workflow<MyWorkflow>(key()).sharedHandler(myInput)
}

@Suppress("UNCHECKED_CAST")
class MyCustomSerdeFactory : SerdeFactory {
  override fun <T> create(typeTag: TypeTag<T?>): Serde<T?> {
    check(typeTag is KotlinSerializationSerdeFactory.KtTypeTag)
    check(typeTag.type == Byte::class)
    return Serde.using<Byte>({ b -> byteArrayOf(b) }, { it[0] }) as Serde<T?>
  }

  override fun <T> create(typeRef: TypeRef<T?>): Serde<T?> {
    check(typeRef.type == Byte::class)
    return Serde.using<Byte>({ b -> byteArrayOf(b) }, { it[0] }) as Serde<T?>
  }

  override fun <T> create(clazz: Class<T?>?): Serde<T?> {
    check(clazz == Byte::class.java)
    return Serde.using<Byte>({ b -> byteArrayOf(b) }, { it[0] }) as Serde<T?>
  }
}

@CustomSerdeFactory(MyCustomSerdeFactory::class)
@Service
@Name("CustomSerdeService")
class CustomSerdeService {
  @Handler
  suspend fun echo(input: Byte): Byte {
    return input
  }
}

@Service
@Name("MyExplicitName")
interface GreeterWithExplicitName {
  @Handler @Name("my_greeter") suspend fun greet(request: String): String
}
