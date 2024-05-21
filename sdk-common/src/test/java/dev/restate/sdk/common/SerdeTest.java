// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SerdeTest {

  private static <T> Arguments roundtripCase(Serde<T> serde, T value) {
    return Arguments.of(
        (value != null ? value.getClass().getSimpleName() : "Null") + ": " + value, serde, value);
  }

  private static Stream<Arguments> roundtrip() {
    return Stream.of(
        roundtripCase(Serde.VOID, null), roundtripCase(Serde.RAW, new byte[] {1, 2, 3, 4}));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource
  <T> void roundtrip(String testName, Serde<T> serde, T value) throws Throwable {
    assertThat(serde.deserialize(serde.serialize(value))).isEqualTo(value);
  }
}
