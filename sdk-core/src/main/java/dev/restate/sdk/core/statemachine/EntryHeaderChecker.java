// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine;

import static dev.restate.sdk.core.ProtocolException.JOURNAL_MISMATCH_CODE;

import com.google.protobuf.MessageLite;
import dev.restate.sdk.core.ProtocolException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * A builder-style utility for checking entry headers and generating detailed error messages. This
 * class encapsulates the logic for validating message fields and throwing appropriate protocol
 * exceptions when mismatches are found.
 */
final class EntryHeaderChecker<E extends MessageLite> {
  private final int commandIndex;
  private final E expected;
  private final E actual;
  private List<FieldMismatch> mismatches;

  private EntryHeaderChecker(int commandIndex, E expected, E actual) {
    this.commandIndex = commandIndex;
    this.expected = expected;
    this.actual = actual;
  }

  /**
   * Creates a new EntryHeaderChecker for the given expected and actual messages.
   *
   * @param <E> The type of the expected message
   * @param commandIndex The index of this command
   * @param expected The expected message
   * @param actual The actual message
   * @return A new EntryHeaderChecker
   */
  @SuppressWarnings("unchecked")
  public static <E extends MessageLite> EntryHeaderChecker<E> check(
      int commandIndex, Class<E> expectedClass, E expected, MessageLite actual) {
    if (!expectedClass.isInstance(actual)) {
      throw new ProtocolException(
          "Found a mismatch between the code paths taken during the previous execution and the paths taken during this execution.\n"
              + "This typically happens when some parts of the code are non-deterministic.\n"
              + "- Expecting command '"
              + Util.commandMessageToString(expected)
              + "' (index "
              + commandIndex
              + ") but was '"
              + Util.commandMessageToString(actual)
              + "'",
          JOURNAL_MISMATCH_CODE);
    }
    return new EntryHeaderChecker<>(commandIndex, expected, (E) actual);
  }

  /**
   * Checks that a field in the expected and actual messages match.
   *
   * @param fieldName The name of the field being checked
   * @param getter Function to extract the field value from the message
   * @param <T> The type of the field
   * @return This EntryHeaderChecker for method chaining
   */
  public <T> EntryHeaderChecker<E> checkField(String fieldName, Function<E, T> getter) {
    T expectedValue = getter.apply(expected);
    T actualValue = getter.apply(actual);

    if (!Objects.equals(expectedValue, actualValue)) {
      if (mismatches == null) {
        mismatches = new ArrayList<>();
      }
      mismatches.add(new FieldMismatch(fieldName, expectedValue, actualValue));
    }

    return this;
  }

  /**
   * Verifies all checks and throws a ProtocolException if any mismatches were found.
   *
   * @throws ProtocolException if any mismatches were found
   */
  public void verify() throws ProtocolException {
    if (mismatches != null && !mismatches.isEmpty()) {
      throw createMismatchException();
    }
  }

  private ProtocolException createMismatchException() {
    StringBuilder customMessage =
        new StringBuilder(
            "Found a mismatch between the code paths taken during the previous execution and the paths taken during this execution.\n"
                + "This typically happens when some parts of the code are non-deterministic.\n"
                + "- The mismatch happened while executing '"
                + Util.commandMessageToString(expected)
                + " (index "
                + commandIndex
                + ")'\n"
                + "- Difference:");
    for (FieldMismatch mismatch : mismatches) {
      customMessage
          .append("\n   ")
          .append(mismatch.fieldName)
          .append(": '")
          .append(mismatch.expectedValue)
          .append("' != '")
          .append(mismatch.actualValue)
          .append("'");
    }

    return new ProtocolException(customMessage.toString(), JOURNAL_MISMATCH_CODE);
  }

  private record FieldMismatch(String fieldName, Object expectedValue, Object actualValue) {}
}
