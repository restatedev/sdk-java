// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine;

import com.google.protobuf.MessageLite;

public interface InvocationInput {
  MessageHeader header();

  MessageLite message();

  static InvocationInput of(MessageHeader header, MessageLite message) {
    return new InvocationInput() {
      @Override
      public MessageHeader header() {
        return header;
      }

      @Override
      public MessageLite message() {
        return message;
      }

      @Override
      public String toString() {
        return header.toString() + " " + message.toString();
      }
    };
  }
}
