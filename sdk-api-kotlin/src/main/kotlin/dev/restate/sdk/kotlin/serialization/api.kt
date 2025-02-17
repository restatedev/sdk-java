// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin.serialization

import dev.restate.serde.Serde
import dev.restate.serde.SerdeInfo
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import kotlin.reflect.typeOf

/** Creates a [Serde] implementation using the `kotlinx.serialization` json module. */
inline fun <reified T : Any?> jsonSerde(json: Json = Json.Default): Serde<T> {
  @Suppress("UNCHECKED_CAST")
  return when (typeOf<T>()) {
    typeOf<Unit>() -> KotlinSerializationSerdeFactory.UNIT as Serde<T>
    else -> KotlinSerializationSerdeFactory.jsonSerde(json, serializer())
  }
}

inline fun <reified T : Any?> serdeInfo(): SerdeInfo<T> =
  KotlinSerializationSerdeFactory.KtSerdeInfo(T::class, typeOf<T>())
