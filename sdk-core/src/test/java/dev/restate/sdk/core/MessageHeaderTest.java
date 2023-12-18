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

import org.junit.jupiter.api.Test;

public class MessageHeaderTest {

  @Test
  void requiresAckFlag() {
    assertThat(
            new MessageHeader(
                    MessageType.InvokeEntryMessage,
                    MessageHeader.DONE_FLAG | MessageHeader.REQUIRES_ACK_FLAG,
                    2)
                .encode())
        .isEqualTo(0x0C01_8001_0000_0002L);
  }
}
