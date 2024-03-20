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
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.google.protobuf.MessageLite;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.common.TerminalException;
import java.util.List;
import java.util.function.Consumer;

public class AssertUtils {

  public static Consumer<List<MessageLite>> containsOnly(Consumer<? super MessageLite> consumer) {
    return msgs -> assertThat(msgs).satisfiesExactly(consumer);
  }

  public static Consumer<List<MessageLite>> containsOnlyExactErrorMessage(Throwable e) {
    return containsOnly(exactErrorMessage(e));
  }

  public static Consumer<? super MessageLite> errorMessage(
      Consumer<? super Protocol.ErrorMessage> consumer) {
    return msg ->
        assertThat(msg).asInstanceOf(type(Protocol.ErrorMessage.class)).satisfies(consumer);
  }

  public static Consumer<? super MessageLite> exactErrorMessage(Throwable e) {
    return errorMessage(
        msg ->
            assertThat(msg)
                .returns(e.toString(), Protocol.ErrorMessage::getMessage)
                .returns(
                    TerminalException.INTERNAL_SERVER_ERROR_CODE, Protocol.ErrorMessage::getCode));
  }

  public static Consumer<? super MessageLite> errorMessageStartingWith(String str) {
    return errorMessage(
        msg ->
            assertThat(msg).extracting(Protocol.ErrorMessage::getMessage, STRING).startsWith(str));
  }

  public static Consumer<? super MessageLite> protocolExceptionErrorMessage(int code) {
    return errorMessage(
        msg ->
            assertThat(msg)
                .returns(code, Protocol.ErrorMessage::getCode)
                .extracting(Protocol.ErrorMessage::getMessage, STRING)
                .startsWith(ProtocolException.class.getCanonicalName()));
  }
}
