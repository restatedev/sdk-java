package dev.restate.sdk.testing;

import com.google.protobuf.*;
import dev.restate.generated.ext.Ext;
import dev.restate.generated.ext.ServiceType;
import dev.restate.generated.sdk.java.Java;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.impl.InvocationHandler;
import dev.restate.sdk.core.impl.RestateGrpcServer;
import io.grpc.*;
import io.grpc.protobuf.ProtoMethodDescriptorSupplier;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class TestRestateRuntime {

  private static final Logger LOG = LogManager.getLogger(TestRestateRuntime.class);
  private CompletableFuture<Protocol.OutputStreamEntryMessage> future;
  private String rootCallerId;
  private final StateStore stateStore;
  private final RestateGrpcServer server;
  private final List<ServerServiceDefinition> services;
  private Protocol.OutputStreamEntryMessage testResult;
  private final HashMap<String, InvocationProcessor> invocationProcessorHashMap;
  // For inter-service calls, we need to keep track of where the response needs to go
  private final HashMap<String, String> calleeToCallerInvocationIds;

  private TestRestateRuntime(BindableService... bindableServices) {
    this.invocationProcessorHashMap = new HashMap<>();
    this.calleeToCallerInvocationIds = new HashMap<>();
    this.stateStore = StateStore.init();
    this.services =
        Arrays.asList(bindableServices).stream()
            .map(BindableService::bindService)
            .collect(Collectors.toList());

    // Start Grpc server and add all the services
    RestateGrpcServer.Builder serverBuilder = RestateGrpcServer.newBuilder();
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
    // Beginning of new request so reset future.
    future = new CompletableFuture<>();

    MessageLite msg =
        (parameter instanceof MessageLite)
            ? (MessageLite) parameter
            : ((MessageLite.Builder) parameter).build();

    // Handle the call
    handle(
        methodDescriptor.getServiceName(),
        methodDescriptor.getBareMethodName(),
        Protocol.PollInputStreamEntryMessage.newBuilder().setValue(msg.toByteString()).build(),
        null);

    Protocol.OutputStreamEntryMessage outputMsg;
    try {
      outputMsg = future.get();
    } catch (InterruptedException | ExecutionException e) {
      throw new StatusRuntimeException(Status.UNKNOWN.withDescription(e.getMessage()));
    }

    if (outputMsg.hasFailure()) {
      throw new StatusRuntimeException(
          Status.fromCodeValue(outputMsg.getFailure().getCode())
              .withDescription(outputMsg.getFailure().getMessage()));
    }

    return methodDescriptor.parseResponse(outputMsg.getValue().newInput());
  }

  // Gets called for new service calls: either test input messages or inter-service calls
  // callerInvocationId is the function invocation ID of the service which did the call.
  private void handle(
      String serviceName,
      String method,
      Protocol.PollInputStreamEntryMessage inputMessage,
      String callerInvocationId) {
    String invocationId = UUID.randomUUID().toString();
    if (callerInvocationId == null) {
      // Register the ID of the call of the external test client (callerInvocationId == null)
      // When we receive the oncomplete call of the invocation processor with this id,
      // we will know the test is over.
      rootCallerId = invocationId;
    }

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
            .setKnownServiceVersion(1)
            .build();
    List<MessageLite> inputMessages = List.of(startMessage, inputMessage);

    if (callerInvocationId != null) {
      // For inter-service calls, register where the response needs to go
      calleeToCallerInvocationIds.put(invocationId, callerInvocationId);
    }

    // Create a new invocation processor on the runtime-side
    InvocationProcessor invocationProcessor =
        new InvocationProcessor(serviceName, instanceKey, invocationId, inputMessages);
    invocationProcessorHashMap.put(invocationId, invocationProcessor);

    // Wire invocation processor with the service-side state machine
    serviceInvocationStateMachineHandler.output().subscribe(invocationProcessor);
    invocationProcessor.subscribe(serviceInvocationStateMachineHandler.input());

    // Start invocation
    serviceInvocationStateMachineHandler.start();
  }

  /**
   * Handles the output messages of calls. There are two options: - They are test results and need
   * to be added to the result list. - They are responses to inter-service calls and the response
   * needs to be forwarded to the caller.
   */
  private void handleCallResult(
      String functionInvocationId, Protocol.OutputStreamEntryMessage msg) {
    if (calleeToCallerInvocationIds.containsKey(functionInvocationId)) {
      // This was an inter-service call, redirect the answer
      LOG.debug("Forwarding inter-service call result");
      String callerInvocationId = calleeToCallerInvocationIds.get(functionInvocationId);
      // If this was a background call, the caller invocation Id was set to "ignore".
      // Only send a response to the caller, if it was not a background call.
      if (!callerInvocationId.equals("ignore")) {
        InvocationProcessor caller = invocationProcessorHashMap.get(callerInvocationId);
        caller.handleCompletionMessage(msg.getValue());
      }
    } else {
      // This is a test result; add it to the list
      LOG.debug("Set message as result");
      testResult = msg;
    }
  }

  private void handleAwakeableCompletion(
      String functionInvocationId, Protocol.CompleteAwakeableEntryMessage msg) {
    InvocationProcessor caller = invocationProcessorHashMap.get(functionInvocationId);
    caller.routeMessage(
        Protocol.CompletionMessage.newBuilder()
            .setEntryIndex(msg.getEntryIndex())
            .setValue(msg.getPayload())
            .build());
  }

  private void onError(Throwable throwable) {
    this.future.completeExceptionally(throwable);
  }

  private void onComplete(String invocationId) {
    if (invocationId.equals(rootCallerId)) {
      // root call has been completed so end the test
      this.future.complete(testResult);
    }
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
      implements Flow.Processor<MessageLite, MessageLite>,
          Flow.Publisher<MessageLite>,
          Flow.Subscriber<MessageLite> {

    private final String serviceName;
    private final String instanceKey;
    private final String functionInvocationId;
    private final Collection<MessageLite> elements;

    private final AtomicBoolean publisherSubscriptionCancelled;
    private Flow.Subscriber<? super MessageLite>
        publisher; // publisher = ExceptionCatchingInvocationInputSubscriber

    // Flow subscriber
    // Subscription to get input from the services
    private Flow.Subscription inputSubscription; // = subscription on InvocationStateMachine
    private Flow.Subscription outputSubscription; // = delivery path to publish to
    // ExceptionCatchingInvocationInputSubscriber

    // Index tracking progress in the journal
    private int currentJournalIndex;

    public InvocationProcessor(
        String serviceName,
        String instanceKey,
        String functionInvocationId,
        Collection<MessageLite> elements) {
      this.serviceName = serviceName;
      this.instanceKey = instanceKey;
      this.functionInvocationId = functionInvocationId;
      this.elements = elements;
      this.publisherSubscriptionCancelled = new AtomicBoolean(false);
    }

    // PUBLISHER LOGIC: to send messages to the service

    @Override
    public void subscribe(Flow.Subscriber<? super MessageLite> publisher) {
      this.publisher = publisher;
      this.currentJournalIndex = 0;
      this.outputSubscription =
          new PublishSubscription<MessageLite>(
              publisher, new ArrayDeque<>(elements), publisherSubscriptionCancelled);

      publisher.onSubscribe(this.outputSubscription);
    }

    public boolean getPublisherSubscriptionCancelled() {
      return publisherSubscriptionCancelled.get();
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
      TestRestateRuntime.this.onError(throwable);
    }

    @Override
    public void onComplete() {
      LOG.trace("End of test: Gathering output messages");
      if (inputSubscription != null) {
        LOG.trace("End of test: Canceling input subscription");
        this.inputSubscription.cancel();
      }
      if (this.publisher != null) {
        LOG.trace("End of test: Closing publisher");
        this.publisher.onComplete();
      }
      LOG.trace("End of test: Closing the runtime state machine");

      TestRestateRuntime.this.onComplete(functionInvocationId);
    }

    private void handleCompletionMessage(ByteString value) {
      routeMessage(
          Protocol.CompletionMessage.newBuilder()
              .setEntryIndex(currentJournalIndex)
              .setValue(value)
              .build());
    }

    // All messages that go through the runtime go through this handler.
    private void routeMessage(MessageLite t) {
      if (t instanceof Protocol.CompletionMessage) {
        LOG.trace("Sending completion message");
        publisher.onNext(t);

      } else if (t instanceof Protocol.PollInputStreamEntryMessage) {
        LOG.trace("Sending poll input stream message");
        publisher.onNext(t);

      } else if (t instanceof Protocol.OutputStreamEntryMessage) {
        LOG.trace("Handling call result");
        TestRestateRuntime.this.handleCallResult(
            functionInvocationId, (Protocol.OutputStreamEntryMessage) t);
        onComplete();

      } else if (t instanceof Protocol.GetStateEntryMessage) {
        Protocol.GetStateEntryMessage msg = (Protocol.GetStateEntryMessage) t;
        LOG.trace("Received GetStateEntryMessage: " + msg);
        ByteString value = stateStore.get(serviceName, instanceKey, msg.getKey());
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
        stateStore.set(serviceName, instanceKey, msg.getKey(), msg.getValue());

      } else if (t instanceof Protocol.ClearStateEntryMessage) {
        Protocol.ClearStateEntryMessage msg = (Protocol.ClearStateEntryMessage) t;
        LOG.trace("Received ClearStateEntryMessage: " + msg);
        stateStore.clear(serviceName, instanceKey, msg.getKey());

      } else if (t instanceof Protocol.InvokeEntryMessage) {
        Protocol.InvokeEntryMessage msg = (Protocol.InvokeEntryMessage) t;
        LOG.trace("Handling InvokeEntryMessage: " + msg);

        // Let the runtime create an invocation processor to handle the call
        TestRestateRuntime.this.handle(
            msg.getServiceName(),
            msg.getMethodName(),
            Protocol.PollInputStreamEntryMessage.newBuilder().setValue(msg.getParameter()).build(),
            functionInvocationId);

      } else if (t instanceof Protocol.BackgroundInvokeEntryMessage) {
        Protocol.BackgroundInvokeEntryMessage msg = (Protocol.BackgroundInvokeEntryMessage) t;
        LOG.trace("Handling BackgroundInvokeEntryMessage: " + msg);

        // Let the runtime create an invocation processor to handle the call
        // We set the caller id to "ignore" because we do not want a response.
        // The response will then be ignored by runtime.
        TestRestateRuntime.this.handle(
            msg.getServiceName(),
            msg.getMethodName(),
            Protocol.PollInputStreamEntryMessage.newBuilder().setValue(msg.getParameter()).build(),
            "ignore");

      } else if (t instanceof Java.SideEffectEntryMessage) {
        Java.SideEffectEntryMessage msg = (Java.SideEffectEntryMessage) t;
        LOG.trace("Received SideEffectEntryMessage: " + msg);
        // Immediately send back acknowledgment of side effect
        Protocol.CompletionMessage completionMessage =
            Protocol.CompletionMessage.newBuilder().setEntryIndex(currentJournalIndex).build();
        publisher.onNext(completionMessage);

      } else if (t instanceof Protocol.AwakeableEntryMessage) {
        Protocol.AwakeableEntryMessage msg = (Protocol.AwakeableEntryMessage) t;
        LOG.trace("Received AwakeableEntryMessage: " + msg);
        // The test runtime doesn't do anything with these messages.

      } else if (t instanceof Protocol.CompleteAwakeableEntryMessage) {
        Protocol.CompleteAwakeableEntryMessage msg = (Protocol.CompleteAwakeableEntryMessage) t;
        LOG.trace("Received CompleteAwakeableEntryMessage: " + msg);
        TestRestateRuntime.this.handleAwakeableCompletion(
            msg.getInvocationId().toStringUtf8(), msg);

      } else if (t instanceof Java.CombinatorAwaitableEntryMessage) {
        Java.CombinatorAwaitableEntryMessage msg = (Java.CombinatorAwaitableEntryMessage) t;
        LOG.trace("Received CombinatorAwaitableEntryMessage: " + msg);
        // The test runtime doesn't do anything with these messages.

      } else if (t instanceof Protocol.SleepEntryMessage) {
        throw new IllegalStateException(
            "This type is not yet implemented in the test runtime: "
                + t.getClass().toGenericString());

      } else if (t instanceof Protocol.StartMessage) {
        throw new IllegalStateException("Start message should not end up in router.");
      } else {
        throw new IllegalStateException(
            "This type is not implemented in the test runtime: " + t.getClass().toGenericString());
      }
    }
  }
}
