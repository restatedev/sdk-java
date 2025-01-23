// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.sdk.types.Slice;
import java.util.concurrent.Flow;

public interface InvocationFlow {

  interface InvocationInputPublisher extends Flow.Publisher<Slice> {}

  interface InvocationOutputPublisher extends Flow.Publisher<Slice> {}

  interface InvocationInputSubscriber extends Flow.Subscriber<Slice> {}

  interface InvocationOutputSubscriber extends Flow.Subscriber<Slice> {}
}
