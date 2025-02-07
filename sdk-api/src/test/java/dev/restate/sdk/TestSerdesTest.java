// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.restate.serde.Serde;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TestSerdesTest {

  private static <T> Arguments roundtripCase(Serde<T> serde, T value) {
    return Arguments.of(
        (value != null ? value.getClass().getSimpleName() : "Null") + ": " + value, serde, value);
  }

  private static Stream<Arguments> roundtrip() {
    var random = new Random();
    return Stream.of(
        roundtripCase(JsonSerdes.STRING, ""),
        roundtripCase(JsonSerdes.STRING, "Francesco1234"),
        roundtripCase(JsonSerdes.STRING, "😀"),
        roundtripCase(JsonSerdes.BOOLEAN, true),
        roundtripCase(JsonSerdes.BOOLEAN, false),
        roundtripCase(JsonSerdes.BYTE, Byte.MIN_VALUE),
        roundtripCase(JsonSerdes.BYTE, Byte.MAX_VALUE),
        roundtripCase(JsonSerdes.BYTE, (byte) random.nextInt()),
        roundtripCase(JsonSerdes.SHORT, Short.MIN_VALUE),
        roundtripCase(JsonSerdes.SHORT, Short.MAX_VALUE),
        roundtripCase(JsonSerdes.SHORT, (short) random.nextInt()),
        roundtripCase(JsonSerdes.INT, Integer.MIN_VALUE),
        roundtripCase(JsonSerdes.INT, Integer.MAX_VALUE),
        roundtripCase(JsonSerdes.INT, random.nextInt()),
        roundtripCase(JsonSerdes.LONG, Long.MIN_VALUE),
        roundtripCase(JsonSerdes.LONG, Long.MAX_VALUE),
        roundtripCase(JsonSerdes.LONG, random.nextLong()),
        roundtripCase(JsonSerdes.FLOAT, Float.MIN_VALUE),
        roundtripCase(JsonSerdes.FLOAT, Float.MAX_VALUE),
        roundtripCase(JsonSerdes.FLOAT, random.nextFloat()),
        roundtripCase(JsonSerdes.DOUBLE, Double.MIN_VALUE),
        roundtripCase(JsonSerdes.DOUBLE, Double.MAX_VALUE),
        roundtripCase(JsonSerdes.DOUBLE, random.nextDouble()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource
  <T> void roundtrip(String testName, Serde<T> serde, T value) throws Throwable {
    assertThat(serde.deserialize(serde.serialize(value))).isEqualTo(value);
  }

  private static Stream<Arguments> failDeserialization() {
    return Stream.of(
        Arguments.of("String unquoted", JsonSerdes.STRING, "my string"),
        Arguments.of("Not a boolean", JsonSerdes.BOOLEAN, "something"),
        Arguments.of("Not a byte", JsonSerdes.BYTE, "something"),
        Arguments.of("Not a short", JsonSerdes.SHORT, "something"),
        Arguments.of("Not a int", JsonSerdes.INT, "something"),
        Arguments.of("Not a long", JsonSerdes.LONG, "something"),
        Arguments.of("Not a float", JsonSerdes.FLOAT, "something"),
        Arguments.of("Not a double", JsonSerdes.DOUBLE, "something"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource
  <T> void failDeserialization(String testName, Serde<T> serde, String value) throws Throwable {
    assertThatThrownBy(() -> serde.deserialize(value.getBytes(StandardCharsets.UTF_8))).isNotNull();
  }
}
