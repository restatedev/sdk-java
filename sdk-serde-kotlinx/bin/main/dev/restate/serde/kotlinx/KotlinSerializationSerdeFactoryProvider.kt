// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.serde.kotlinx

import dev.restate.serde.provider.DefaultSerdeFactoryProvider

public class KotlinSerializationSerdeFactoryProvider : DefaultSerdeFactoryProvider {
  override fun create() = KotlinSerializationSerdeFactory()
}
