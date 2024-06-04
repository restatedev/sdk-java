// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import java.nio.ByteBuffer;
import java.util.concurrent.Flow;

public interface InvocationFlow {

  interface InvocationInputPublisher extends Flow.Publisher<ByteBuffer> {}

  interface InvocationOutputPublisher extends Flow.Publisher<ByteBuffer> {}

  interface InvocationInputSubscriber extends Flow.Subscriber<ByteBuffer> {}

  interface InvocationOutputSubscriber extends Flow.Subscriber<ByteBuffer> {}
}
