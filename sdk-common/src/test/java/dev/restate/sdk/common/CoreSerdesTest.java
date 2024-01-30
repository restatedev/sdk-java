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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CoreSerdesTest {

  private static <T> Arguments roundtripCase(Serde<T> serde, T value) {
    return Arguments.of(
        (value != null ? value.getClass().getSimpleName() : "Null") + ": " + value, serde, value);
  }

  private static Stream<Arguments> roundtrip() {
    var random = new Random();
    return Stream.of(
        roundtripCase(CoreSerdes.VOID, null),
        roundtripCase(CoreSerdes.BYTES, new byte[] {1, 2, 3, 4}),
        roundtripCase(CoreSerdes.JSON_STRING, ""),
        roundtripCase(CoreSerdes.JSON_STRING, "Francesco1234"),
        roundtripCase(CoreSerdes.JSON_STRING, "😀"),
        roundtripCase(CoreSerdes.JSON_BOOLEAN, true),
        roundtripCase(CoreSerdes.JSON_BOOLEAN, false),
        roundtripCase(CoreSerdes.JSON_BYTE, Byte.MIN_VALUE),
        roundtripCase(CoreSerdes.JSON_BYTE, Byte.MAX_VALUE),
        roundtripCase(CoreSerdes.JSON_BYTE, (byte) random.nextInt()),
        roundtripCase(CoreSerdes.JSON_SHORT, Short.MIN_VALUE),
        roundtripCase(CoreSerdes.JSON_SHORT, Short.MAX_VALUE),
        roundtripCase(CoreSerdes.JSON_SHORT, (short) random.nextInt()),
        roundtripCase(CoreSerdes.JSON_INT, Integer.MIN_VALUE),
        roundtripCase(CoreSerdes.JSON_INT, Integer.MAX_VALUE),
        roundtripCase(CoreSerdes.JSON_INT, random.nextInt()),
        roundtripCase(CoreSerdes.JSON_LONG, Long.MIN_VALUE),
        roundtripCase(CoreSerdes.JSON_LONG, Long.MAX_VALUE),
        roundtripCase(CoreSerdes.JSON_LONG, random.nextLong()),
        roundtripCase(CoreSerdes.JSON_FLOAT, Float.MIN_VALUE),
        roundtripCase(CoreSerdes.JSON_FLOAT, Float.MAX_VALUE),
        roundtripCase(CoreSerdes.JSON_FLOAT, random.nextFloat()),
        roundtripCase(CoreSerdes.JSON_DOUBLE, Double.MIN_VALUE),
        roundtripCase(CoreSerdes.JSON_DOUBLE, Double.MAX_VALUE),
        roundtripCase(CoreSerdes.JSON_DOUBLE, random.nextDouble()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource
  <T> void roundtrip(String testName, Serde<T> serde, T value) throws Throwable {
    assertThat(serde.deserialize(serde.serialize(value))).isEqualTo(value);
  }

  private static Stream<Arguments> failDeserialization() {
    return Stream.of(
        Arguments.of("String unquoted", CoreSerdes.JSON_STRING, "my string"),
        Arguments.of("Not a boolean", CoreSerdes.JSON_BOOLEAN, "something"),
        Arguments.of("Not a byte", CoreSerdes.JSON_BYTE, "something"),
        Arguments.of("Not a short", CoreSerdes.JSON_SHORT, "something"),
        Arguments.of("Not a int", CoreSerdes.JSON_INT, "something"),
        Arguments.of("Not a long", CoreSerdes.JSON_LONG, "something"),
        Arguments.of("Not a float", CoreSerdes.JSON_FLOAT, "something"),
        Arguments.of("Not a double", CoreSerdes.JSON_DOUBLE, "something"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource
  <T> void failDeserialization(String testName, Serde<T> serde, String value) throws Throwable {
    assertThatThrownBy(() -> serde.deserialize(value.getBytes(StandardCharsets.UTF_8))).isNotNull();
  }
}
