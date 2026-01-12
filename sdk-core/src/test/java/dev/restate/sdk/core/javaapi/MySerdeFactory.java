// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.javaapi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.restate.serde.Serde;
import dev.restate.serde.SerdeFactory;
import dev.restate.serde.TypeRef;
import java.nio.charset.StandardCharsets;

@SuppressWarnings("unchecked")
public class MySerdeFactory implements SerdeFactory {

  public static Serde<String> SERDE =
      Serde.using(
          "mycontent/type",
          s -> s.toUpperCase().getBytes(),
          b -> new String(b, StandardCharsets.UTF_8).toUpperCase());

  @Override
  public <T> Serde<T> create(TypeRef<T> typeRef) {
    assertThat(typeRef.getType()).isEqualTo(String.class);
    return (Serde<T>) SERDE;
  }

  @Override
  public <T> Serde<T> create(Class<T> clazz) {
    assertThat(clazz).isEqualTo(String.class);
    return (Serde<T>) SERDE;
  }
}
