// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.common.reflection.kotlin

import dev.restate.common.Request
import dev.restate.common.Target
import dev.restate.common.reflections.ProxyFactory
import dev.restate.common.reflections.ReflectionUtils
import dev.restate.sdk.annotation.Raw
import dev.restate.serde.Serde
import dev.restate.serde.TypeTag
import dev.restate.serde.kotlinx.KotlinSerializationSerdeFactory
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.kotlinFunction
import kotlin.reflect.typeOf

/**
 * Captured information from a method invocation on a proxy.
 *
 * @property target the target service/handler
 * @property inputTypeTag type tag for serializing the input
 * @property outputTypeTag type tag for deserializing the output
 * @property input the input value (may be null for no-arg methods)
 */
data class CapturedInvocation(
    val target: Target,
    val inputTypeTag: TypeTag<*>,
    val outputTypeTag: TypeTag<*>,
    val input: Any?,
) {
  @Suppress("UNCHECKED_CAST")
  fun toRequest(): Request<*, *> {
    return Request.of(target, inputTypeTag as TypeTag<Any?>, outputTypeTag as TypeTag<Any?>, input)
  }
}

fun ProxyFactory.MethodInvocation.captureInvocation(
    serviceName: String,
    key: String?,
): CapturedInvocation {
  val handlerInfo = ReflectionUtils.mustHaveHandlerAnnotation(method)
  val handlerName = handlerInfo.name
  val kFunction = method.kotlinFunction
  require(kFunction != null && kFunction.isSuspend) {
    "Method '${method.name}' is not a suspend function, this is not supported."
  }

  val parameters = kFunction.valueParameters
  val inputTypeTag =
      if (parameters.isEmpty()) {
        resolveKotlinTypeTag(typeOf<Unit>(), null)
      } else {
        parameters[0].let { inputParam ->
          resolveKotlinTypeTag(
              inputParam.type,
              inputParam.findAnnotation<Raw>(),
          )
        }
      }

  val outputTypeTag =
      resolveKotlinTypeTag(
          kFunction.returnType,
          kFunction.findAnnotation<Raw>(),
      )

  val target =
      if (key != null) {
        Target.virtualObject(serviceName, key, handlerName)
      } else {
        Target.service(serviceName, handlerName)
      }

  // For suspend functions, arguments are: [input?, continuation]
  // Extract the input (first argument, excluding continuation)
  val input =
      if (this.arguments.size > 1) {
        this.arguments[0]
      } else {
        null
      }

  return CapturedInvocation(target, inputTypeTag, outputTypeTag, input)
}

private fun resolveKotlinTypeTag(kType: KType, rawAnnotation: Raw?): TypeTag<*> {
  if (kType.classifier == Unit::class) {
    return KotlinSerializationSerdeFactory.UNIT
  }

  if (rawAnnotation != null && rawAnnotation.contentType != "application/octet-stream") {
    return Serde.withContentType(rawAnnotation.contentType, Serde.RAW)
  } else if (rawAnnotation != null) {
    return Serde.RAW
  }

  @Suppress("UNCHECKED_CAST")
  return KotlinSerializationSerdeFactory.KtTypeTag<Any?>(
      kType.classifier as KClass<*>,
      kType,
  )
}
