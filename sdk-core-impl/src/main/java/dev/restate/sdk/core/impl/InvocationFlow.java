package dev.restate.sdk.core.impl;

import com.google.protobuf.MessageLite;
import java.util.concurrent.Flow;

public interface InvocationFlow {

  interface InvocationInputPublisher extends Flow.Publisher<MessageLite> {}

  interface InvocationOutputPublisher extends Flow.Publisher<MessageLite> {}

  interface InvocationInputSubscriber extends Flow.Subscriber<MessageLite> {}

  interface InvocationOutputSubscriber extends Flow.Subscriber<MessageLite> {}

  interface InvocationProcessor
      extends Flow.Processor<MessageLite, MessageLite>,
          InvocationInputSubscriber,
          InvocationOutputPublisher {}
}
