// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import com.google.protobuf.MessageLite;
import java.util.concurrent.Flow;

public interface InvocationFlow {

  interface InvocationInput {
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

  interface InvocationInputPublisher extends Flow.Publisher<InvocationInput> {}

  interface InvocationOutputPublisher extends Flow.Publisher<MessageLite> {}

  interface InvocationInputSubscriber extends Flow.Subscriber<InvocationInput> {}

  interface InvocationOutputSubscriber extends Flow.Subscriber<MessageLite> {}

  interface InvocationProcessor
      extends Flow.Processor<InvocationInput, MessageLite>,
          InvocationInputSubscriber,
          InvocationOutputPublisher {}
}
