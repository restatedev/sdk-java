// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CoreSerdesTest {

  private static <T> Arguments testCase(Serde<T> serde, T value) {
    return Arguments.of(
        (value != null ? value.getClass().getSimpleName() : "Null") + ": " + value, serde, value);
  }

  private static Stream<Arguments> roundtripTestCases() {
    var random = new Random();
    return Stream.of(
        testCase(CoreSerdes.VOID, null),
        testCase(CoreSerdes.BYTES, new byte[] {1, 2, 3, 4}),
        testCase(CoreSerdes.STRING_UTF8, ""),
        testCase(CoreSerdes.STRING_UTF8, "Francesco1234"),
        testCase(CoreSerdes.STRING_UTF8, "😀"),
        testCase(CoreSerdes.BOOLEAN, true),
        testCase(CoreSerdes.BOOLEAN, false),
        testCase(CoreSerdes.BYTE, Byte.MIN_VALUE),
        testCase(CoreSerdes.BYTE, Byte.MAX_VALUE),
        testCase(CoreSerdes.BYTE, (byte) random.nextInt()),
        testCase(CoreSerdes.SHORT, Short.MIN_VALUE),
        testCase(CoreSerdes.SHORT, Short.MAX_VALUE),
        testCase(CoreSerdes.SHORT, (short) random.nextInt()),
        testCase(CoreSerdes.INT, Integer.MIN_VALUE),
        testCase(CoreSerdes.INT, Integer.MAX_VALUE),
        testCase(CoreSerdes.INT, random.nextInt()),
        testCase(CoreSerdes.LONG, Long.MIN_VALUE),
        testCase(CoreSerdes.LONG, Long.MAX_VALUE),
        testCase(CoreSerdes.LONG, random.nextLong()),
        testCase(CoreSerdes.FLOAT, Float.MIN_VALUE),
        testCase(CoreSerdes.FLOAT, Float.MAX_VALUE),
        testCase(CoreSerdes.FLOAT, random.nextFloat()),
        testCase(CoreSerdes.DOUBLE, Double.MIN_VALUE),
        testCase(CoreSerdes.DOUBLE, Double.MAX_VALUE),
        testCase(CoreSerdes.DOUBLE, random.nextDouble()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("roundtripTestCases")
  <T> void roundtrip(String testName, Serde<T> serde, T value) throws Throwable {
    assertThat(serde.deserialize(serde.serialize(value))).isEqualTo(value);
  }
}
