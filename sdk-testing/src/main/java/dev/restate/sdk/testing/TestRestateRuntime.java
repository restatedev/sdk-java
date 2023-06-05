package dev.restate.sdk.testing;

import com.google.protobuf.*;
import dev.restate.generated.ext.Ext;
import dev.restate.generated.ext.ServiceType;
import dev.restate.generated.sdk.java.Java;
import dev.restate.generated.service.discovery.Discovery;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.impl.InvocationFlow;
import dev.restate.sdk.core.impl.InvocationHandler;
import dev.restate.sdk.core.impl.MessageHeader;
import dev.restate.sdk.core.impl.RestateGrpcServer;
import io.grpc.*;
import io.grpc.protobuf.ProtoMethodDescriptorSupplier;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class TestRestateRuntime {

  private static final Logger LOG = LogManager.getLogger(TestRestateRuntime.class);

  private final StateStore stateStore;
  private final RestateGrpcServer server;
  private final List<ServerServiceDefinition> services;
  private final HashMap<String, CompletableFuture<? super MessageLite>> invocationFuturesHashMap;

  private TestRestateRuntime(BindableService... bindableServices) {
    this.invocationFuturesHashMap = new HashMap<>();
    this.stateStore = StateStore.init();
    this.services =
        Arrays.asList(bindableServices).stream()
            .map(BindableService::bindService)
            .collect(Collectors.toList());

    // Start Grpc server and add all the services
    RestateGrpcServer.Builder serverBuilder =
        RestateGrpcServer.newBuilder(Discovery.ProtocolMode.BIDI_STREAM);
    for (ServerServiceDefinition svc : this.services) {
      serverBuilder.withService(svc);
    }
    server = serverBuilder.build();
  }

  public static TestRestateRuntime init(BindableService... services) {
    return new TestRestateRuntime(services);
  }

  public void close() {
    stateStore.close();
    LOG.debug("Closing TestRestateRuntime");
  }

  public <T extends MessageLiteOrBuilder, R extends MessageLiteOrBuilder> R invoke(
      MethodDescriptor<T, R> methodDescriptor, T parameter) {
    var future = new CompletableFuture<>();
    String invocationId = UUID.randomUUID().toString();
    invocationFuturesHashMap.put(invocationId, future);

    MessageLite msg =
        (parameter instanceof MessageLite)
            ? (MessageLite) parameter
            : ((MessageLite.Builder) parameter).build();

    // Handle the call
    handle(
        invocationId,
        methodDescriptor.getServiceName(),
        methodDescriptor.getBareMethodName(),
        Protocol.PollInputStreamEntryMessage.newBuilder().setValue(msg.toByteString()).build());

    Protocol.OutputStreamEntryMessage outputMsg;
    try {
      outputMsg = (Protocol.OutputStreamEntryMessage) future.get();
    } catch (ExecutionException e) {
      throw (RuntimeException) e.getCause();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    if (outputMsg.hasFailure()) {
      throw Status.fromCodeValue(outputMsg.getFailure().getCode())
          .withDescription(outputMsg.getFailure().getMessage())
          .asRuntimeException();
    }

    return methodDescriptor.parseResponse(outputMsg.getValue().newInput());
  }

  // Gets called for new service calls: either test input messages or inter-service calls
  private void handle(
      String invocationId,
      String serviceName,
      String method,
      Protocol.PollInputStreamEntryMessage inputMessage) {

    // Create invocation handler on the side of the service
    InvocationHandler serviceInvocationStateMachineHandler =
        server.resolve(serviceName, method, io.opentelemetry.context.Context.current(), null, null);

    // Get the key of the instance. Either value of key, random value (unkeyed service) or empty
    // value (singleton).
    String instanceKey = extractKey(serviceName, method, inputMessage).toString();
    Protocol.StartMessage startMessage =
        Protocol.StartMessage.newBuilder()
            .setInstanceKey(ByteString.copyFromUtf8(instanceKey))
            .setInvocationId(ByteString.copyFromUtf8(invocationId))
            .setKnownEntries(1)
            .build();
    List<MessageLite> inputMessages = List.of(startMessage, inputMessage);

    // Create a new invocation processor on the runtime-side
    InvocationProcessor invocationProcessor =
        new InvocationProcessor(invocationId, serviceName, instanceKey, inputMessages);

    // Wire invocation processor with the service-side state machine
    serviceInvocationStateMachineHandler.output().subscribe(invocationProcessor);
    invocationProcessor.subscribe(serviceInvocationStateMachineHandler.input());

    // Start invocation
    serviceInvocationStateMachineHandler.start();
  }

  private Object extractKey(
      String serviceName, String methodName, Protocol.PollInputStreamEntryMessage message) {
    LOG.debug("Extracting key for service {} and method {}", serviceName, methodName);

    List<ServerServiceDefinition> servicesWithThisName =
        services.stream()
            .filter(el -> el.getServiceDescriptor().getName().equals(serviceName))
            .collect(Collectors.toList());
    if (servicesWithThisName.size() > 1) {
      throw new IllegalStateException(
          "Multiple services registered with the same name: \"" + serviceName + "\"");
    } else if (servicesWithThisName.isEmpty()) {
      throw new IllegalStateException(
          "Cannot find service with service name: \""
              + serviceName
              + "\". The only registered services are: "
              + services.stream()
                  .map(el -> el.getServiceDescriptor().getName())
                  .collect(Collectors.joining(", ")));
    }

    var methodDefinition =
        (ServerMethodDefinition<MessageLite, MessageLite>)
            servicesWithThisName.get(0).getMethod(serviceName + "/" + methodName);

    var methodDescriptor =
        ((ProtoMethodDescriptorSupplier)
                methodDefinition.getMethodDescriptor().getSchemaDescriptor())
            .getMethodDescriptor();
    var serviceDescriptor = methodDescriptor.getService();
    var parameterDescriptor = methodDescriptor.getInputType();

    // Check if the service is keyed
    if (!serviceDescriptor.getOptions().hasExtension(Ext.serviceType)) {
      throw new IllegalStateException(
          "Cannot find "
              + Ext.serviceType
              + " extension in the service descriptor "
              + serviceDescriptor.getFullName());
    }
    if (serviceDescriptor.getOptions().getExtension(Ext.serviceType) == ServiceType.KEYED) {
      var keyParam =
          parameterDescriptor.getFields().stream()
              .filter(f -> f.getOptions().hasExtension(Ext.field))
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Cannot find dev.restate.key option in the message "
                              + parameterDescriptor.getFullName()));
      return ((Message)
              methodDefinition.getMethodDescriptor().parseRequest(message.getValue().newInput()))
          .getField(keyParam);
    } else if (serviceDescriptor.getOptions().getExtension(Ext.serviceType)
        == ServiceType.UNKEYED) {
      return UUID.randomUUID();
    } else {
      return "SINGLETON";
    }
  }

  class InvocationProcessor
      implements InvocationFlow.InvocationInputPublisher,
          InvocationFlow.InvocationOutputSubscriber {

    private final String serviceName;
    private final String instanceKey;
    private final String functionInvocationId;
    private final Collection<MessageLite> inputMessages;

    private Flow.Subscriber<? super InvocationFlow.InvocationInput>
        publisher; // publisher = ExceptionCatchingInvocationInputSubscriber

    // Flow subscriber
    // Subscription to get input from the services
    private Flow.Subscription inputSubscription; // = subscription on InvocationStateMachine
    private Flow.Subscription outputSubscription; // = delivery path to publish to
    // ExceptionCatchingInvocationInputSubscriber

    // Index tracking progress in the journal
    private int currentJournalIndex;

    public InvocationProcessor(
        String functionInvocationId,
        String serviceName,
        String instanceKey,
        Collection<MessageLite> inputMessages) {
      this.serviceName = serviceName;
      this.instanceKey = instanceKey;
      this.functionInvocationId = functionInvocationId;
      this.inputMessages = inputMessages;
    }

    // PUBLISHER LOGIC: to send messages to the service

    @Override
    public void subscribe(Flow.Subscriber<? super InvocationFlow.InvocationInput> publisher) {
      this.publisher = publisher;
      this.currentJournalIndex = 0;
      this.outputSubscription = new PublishSubscription(publisher, new ArrayDeque<>(inputMessages));

      publisher.onSubscribe(this.outputSubscription);
    }

    // SUBSCRIBER LOGIC: to receive input from the service

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      this.inputSubscription = subscription;
      this.inputSubscription.request(Long.MAX_VALUE);
    }

    // Called for each message that comes in. Sent by the service to the runtime.
    @Override
    public void onNext(MessageLite msg) {
      // increase the journal index because we received a new message
      currentJournalIndex++;

      routeMessage(msg);
    }

    @Override
    public void onError(Throwable throwable) {
      var future = TestRestateRuntime.this.invocationFuturesHashMap.get(functionInvocationId);
      future.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
      LOG.trace("End of test: Gathering output messages");
      if (inputSubscription != null) {
        this.inputSubscription.cancel();
      }
      if (this.publisher != null) {
        this.publisher.onComplete();
      }
      LOG.trace("End of test: Closing the runtime state machine");
    }

    // All messages that go through the runtime go through this handler.
    private void routeMessage(MessageLite t) {
      if (t instanceof Protocol.CompletionMessage) {
        LOG.trace("Sending completion message");
        publisher.onNext(
            InvocationFlow.InvocationInput.of(PublishSubscription.headerFromMessage(t), t));
      } else if (t instanceof Protocol.PollInputStreamEntryMessage) {
        LOG.trace("Sending poll input stream message");
        publisher.onNext(InvocationFlow.InvocationInput.of(MessageHeader.fromMessage(t), t));
      } else if (t instanceof Protocol.OutputStreamEntryMessage) {
        LOG.trace("Handling call result");
        Protocol.OutputStreamEntryMessage msg = (Protocol.OutputStreamEntryMessage) t;
        var future = TestRestateRuntime.this.invocationFuturesHashMap.get(functionInvocationId);

        // if the invocation id is not present in the map, then it was a background call
        if (future != null) {
          if (msg.hasValue()) {
            future.complete(t);
            onComplete();
          } else {
            onError(
                Status.fromCodeValue(msg.getFailure().getCode())
                    .withDescription(msg.getFailure().getMessage())
                    .asRuntimeException());
          }
        }

      } else if (t instanceof Protocol.GetStateEntryMessage) {
        Protocol.GetStateEntryMessage msg = (Protocol.GetStateEntryMessage) t;
        LOG.trace("Received GetStateEntryMessage: " + msg);
        ByteString value =
            TestRestateRuntime.this.stateStore.get(serviceName, instanceKey, msg.getKey());
        if (value != null) {
          routeMessage(
              Protocol.CompletionMessage.newBuilder()
                  .setEntryIndex(currentJournalIndex)
                  .setValue(value)
                  .build());
        } else {
          routeMessage(
              Protocol.CompletionMessage.newBuilder()
                  .setEntryIndex(currentJournalIndex)
                  .setEmpty(Empty.getDefaultInstance())
                  .build());
        }

      } else if (t instanceof Protocol.SetStateEntryMessage) {
        Protocol.SetStateEntryMessage msg = (Protocol.SetStateEntryMessage) t;
        LOG.trace("Received SetStateEntryMessage: " + msg);
        TestRestateRuntime.this.stateStore.set(
            serviceName, instanceKey, msg.getKey(), msg.getValue());

      } else if (t instanceof Protocol.ClearStateEntryMessage) {
        Protocol.ClearStateEntryMessage msg = (Protocol.ClearStateEntryMessage) t;
        LOG.trace("Received ClearStateEntryMessage: " + msg);
        TestRestateRuntime.this.stateStore.clear(serviceName, instanceKey, msg.getKey());

      } else if (t instanceof Protocol.InvokeEntryMessage) {
        Protocol.InvokeEntryMessage msg = (Protocol.InvokeEntryMessage) t;
        LOG.trace("Handling InvokeEntryMessage: " + msg);
        handleInvokeEntryMessage(msg);

      } else if (t instanceof Protocol.BackgroundInvokeEntryMessage) {
        Protocol.BackgroundInvokeEntryMessage msg = (Protocol.BackgroundInvokeEntryMessage) t;
        LOG.trace("Handling BackgroundInvokeEntryMessage: " + msg);

        // Let the runtime create an invocation processor to handle the call
        // We set the caller id to "ignore" because we do not want a response.
        // The response will then be ignored by runtime.
        TestRestateRuntime.this.handle(
            UUID.randomUUID().toString(),
            msg.getServiceName(),
            msg.getMethodName(),
            Protocol.PollInputStreamEntryMessage.newBuilder().setValue(msg.getParameter()).build());

      } else if (t instanceof Java.SideEffectEntryMessage) {
        Java.SideEffectEntryMessage msg = (Java.SideEffectEntryMessage) t;
        LOG.trace("Received SideEffectEntryMessage: " + msg);
        // Immediately send back acknowledgment of side effect
        Protocol.CompletionMessage completionMessage =
            Protocol.CompletionMessage.newBuilder().setEntryIndex(currentJournalIndex).build();
        routeMessage(completionMessage);

      } else if (t instanceof Protocol.AwakeableEntryMessage) {
        Protocol.AwakeableEntryMessage msg = (Protocol.AwakeableEntryMessage) t;
        LOG.trace("Received AwakeableEntryMessage: " + msg);
        handleAwakeableEntryMessage();

      } else if (t instanceof Protocol.CompleteAwakeableEntryMessage) {
        Protocol.CompleteAwakeableEntryMessage msg = (Protocol.CompleteAwakeableEntryMessage) t;
        LOG.trace("Received CompleteAwakeableEntryMessage: " + msg);

        var future =
            TestRestateRuntime.this.invocationFuturesHashMap.get(
                msg.getInvocationId().toStringUtf8() + "-awake");
        if (future != null) {
          future.complete(msg);
        } else {
          onError(
              new IllegalStateException(
                  "Test runtime received a CompleteAwakeableEntryMessage "
                      + "but there is no registered awakeable for this message."));
        }

      } else if (t instanceof Java.CombinatorAwaitableEntryMessage) {
        Java.CombinatorAwaitableEntryMessage msg = (Java.CombinatorAwaitableEntryMessage) t;
        LOG.trace("Received CombinatorAwaitableEntryMessage: " + msg);
        // The test runtime doesn't do anything with these messages.

      } else if (t instanceof Protocol.SleepEntryMessage) {
        LOG.info(
            "We do not have full timer support in the test runtime: Timer gets completed immediately without sleeping.");
        routeMessage(
            Protocol.CompletionMessage.newBuilder().setEntryIndex(currentJournalIndex).build());

      } else if (t instanceof Protocol.StartMessage) {
        throw new IllegalStateException("Start message should not end up in router.");
      } else {
        throw new IllegalStateException(
            "This type is not implemented in the test runtime: " + t.getClass().toGenericString());
      }
    }

    public void handleInvokeEntryMessage(Protocol.InvokeEntryMessage msg) {
      String invocationId = UUID.randomUUID().toString();
      CompletableFuture<? super MessageLite> future = new CompletableFuture<>();
      future.handle(
          (resp, throwable) -> {
            if (throwable == null) {
              routeMessage(
                  Protocol.CompletionMessage.newBuilder()
                      .setEntryIndex(currentJournalIndex)
                      .setValue(((Protocol.OutputStreamEntryMessage) resp).getValue())
                      .build());
              return null;
            } else {
              onError(throwable);
              return null;
            }
          });
      TestRestateRuntime.this.invocationFuturesHashMap.put(invocationId, future);
      TestRestateRuntime.this.handle(
          invocationId,
          msg.getServiceName(),
          msg.getMethodName(),
          Protocol.PollInputStreamEntryMessage.newBuilder().setValue(msg.getParameter()).build());
    }

    public void handleAwakeableEntryMessage() {
      CompletableFuture<? super MessageLite> future = new CompletableFuture<>();
      future.handle(
          (resp, throwable) -> {
            if (throwable == null) {
              Protocol.CompleteAwakeableEntryMessage completeAwakeMsg =
                  (Protocol.CompleteAwakeableEntryMessage) resp;
              routeMessage(
                  Protocol.CompletionMessage.newBuilder()
                      .setEntryIndex(completeAwakeMsg.getEntryIndex())
                      .setValue(completeAwakeMsg.getPayload())
                      .build());
              return null;
            } else {
              onError(throwable);
              return null;
            }
          });

      TestRestateRuntime.this.invocationFuturesHashMap.put(functionInvocationId + "-awake", future);
    }
  }
}
