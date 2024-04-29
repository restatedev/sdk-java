// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.auth.signing;

import java.util.Arrays;

// Copied and adapted from
// https://github.com/bitcoinj/bitcoinj/blob/7df957e4c6817036c096283c5f0dcb7e4d60c982/core/src/main/java/org/bitcoinj/base/Base58.java#L50
// License Apache 2.0
// Copyright 2011 Google Inc.
// Copyright 2018 Andreas Schildbach

class Base58 {
  public static final char[] ALPHABET =
      "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();
  private static final int[] INDEXES = new int[128];

  static {
    Arrays.fill(INDEXES, -1);
    for (int i = 0; i < ALPHABET.length; i++) {
      INDEXES[ALPHABET[i]] = i;
    }
  }

  /**
   * Decodes the given base58 string into the original data bytes.
   *
   * @param input the base58-encoded string to decode
   * @return the decoded data bytes
   */
  public static byte[] decode(String input) {
    if (input.isEmpty()) {
      return new byte[0];
    }
    // Convert the base58-encoded ASCII chars to a base58 byte sequence (base58 digits).
    byte[] input58 = new byte[input.length()];
    for (int i = 0; i < input.length(); ++i) {
      char c = input.charAt(i);
      int digit = c < 128 ? INDEXES[c] : -1;
      if (digit < 0) {
        throw new IllegalArgumentException(
            String.format("Invalid character in Base58: 0x%04x", (int) c));
      }
      input58[i] = (byte) digit;
    }
    // Count leading zeros.
    int zeros = 0;
    while (zeros < input58.length && input58[zeros] == 0) {
      ++zeros;
    }
    // Convert base-58 digits to base-256 digits.
    byte[] decoded = new byte[input.length()];
    int outputStart = decoded.length;
    for (int inputStart = zeros; inputStart < input58.length; ) {
      decoded[--outputStart] = divmod(input58, inputStart, 58, 256);
      if (input58[inputStart] == 0) {
        ++inputStart; // optimization - skip leading zeros
      }
    }
    // Ignore extra leading zeroes that were added during the calculation.
    while (outputStart < decoded.length && decoded[outputStart] == 0) {
      ++outputStart;
    }
    // Return decoded data (including original number of leading zeros).
    return Arrays.copyOfRange(decoded, outputStart - zeros, decoded.length);
  }

  /**
   * Divides a number, represented as an array of bytes each containing a single digit in the
   * specified base, by the given divisor. The given number is modified in-place to contain the
   * quotient, and the return value is the remainder.
   *
   * @param number the number to divide
   * @param firstDigit the index within the array of the first non-zero digit (this is used for
   *     optimization by skipping the leading zeros)
   * @param base the base in which the number's digits are represented (up to 256)
   * @param divisor the number to divide by (up to 256)
   * @return the remainder of the division operation
   */
  private static byte divmod(byte[] number, int firstDigit, int base, int divisor) {
    // this is just long division which accounts for the base of the input digits
    int remainder = 0;
    for (int i = firstDigit; i < number.length; i++) {
      int digit = (int) number[i] & 0xFF;
      int temp = remainder * base + digit;
      number[i] = (byte) (temp / divisor);
      remainder = temp % divisor;
    }
    return (byte) remainder;
  }
}
