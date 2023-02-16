package dev.restate.sdk.testing;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import com.google.protobuf.Message;
import com.google.protobuf.MessageLite;
import dev.restate.generated.ext.Ext;
import dev.restate.generated.ext.ServiceType;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.impl.InvocationHandler;
import dev.restate.sdk.core.impl.RestateGrpcServer;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.protobuf.ProtoMethodDescriptorSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import static dev.restate.sdk.testing.ProtoUtils.*;

final class TestRestateRuntime {

    private static TestRestateRuntime INSTANCE;

    private static final Logger LOG = LogManager.getLogger(TestRestateRuntime.class);
    private final CompletableFuture<List<Protocol.OutputStreamEntryMessage>> future = new CompletableFuture<>();
    private final RestateGrpcServer server;
    private final List<ServerServiceDefinition> services;
    private final RestateTestDriver.ThreadingModel threadingModel;
    private final List<Protocol.OutputStreamEntryMessage> testResults = new ArrayList<>();
    private final HashMap<String, InvocationProcessor> invocationProcessorHashMap;
    // For inter-service calls, we need to keep track of where the response needs to go
    private final HashMap<String, String> calleeToCallerInvocationIds;

    private TestRestateRuntime(List<ServerServiceDefinition> services,
                              RestateTestDriver.ThreadingModel threadingModel) {
        this.invocationProcessorHashMap = new HashMap<>();
        this.calleeToCallerInvocationIds = new HashMap<>();
        this.services = services;
        this.threadingModel = threadingModel;

        // Initialize the state store
        StateStore.init();

        // Start Grpc server and add all the services
        RestateGrpcServer.Builder serverBuilder = RestateGrpcServer.newBuilder();
        for (ServerServiceDefinition svc : services) {
            serverBuilder.withService(svc);
        }
        server = serverBuilder.build();
    }

    public static TestRestateRuntime get(){
        if(INSTANCE == null){
            throw new AssertionError("You have to call init first");
        }
        return INSTANCE;
    }

    public synchronized static TestRestateRuntime init(List<ServerServiceDefinition> services,
                                                       RestateTestDriver.ThreadingModel threadingModel){
        if(INSTANCE != null){
            throw new AssertionError("TestRestateRuntime was already initialized. You cannot call init twice.");
        }
        LOG.debug("Initializing TestRestateRuntime");
        INSTANCE = new TestRestateRuntime(services, threadingModel);
        return INSTANCE;
    }

    public static void close(){
        StateStore.close();
        LOG.debug("Closing TestRestateRuntime");
        INSTANCE = null;
    }

    // Gets called for new test input messages
    public void handle(RestateTestDriver.TestInput testInput) {
        handle(testInput.getService(),
                testInput.getMethod(),
                testInput.getInputMessage(),
                null);
    }

    // Gets called for new service calls: either test input messages or inter-service calls
    // callerInvocationId is the function invocation ID of the service which did the call.
    public void handle(String serviceName,
                       String method,
                       Protocol.PollInputStreamEntryMessage inputMessage,
                       String callerInvocationId){
        String invocationId = UUID.randomUUID().toString();

        //TODO executors
        // Create invocation handler on the side of the service
        InvocationHandler serviceInvocationStateMachineHandler =
                server.resolve(
                        serviceName,
                        method,
                        io.opentelemetry.context.Context.current(),
                        null,
                        null);

        // Get the key of the instance. Either value of key, random value (unkeyed service) or empty value (singleton).
        String instanceKey = extractKey(serviceName, method, inputMessage).toString();
        Protocol.StartMessage startMessage = startMessage(instanceKey, invocationId, 1).build();
        List<MessageLite> inputMessages = List.of(startMessage, inputMessage);

        if(callerInvocationId != null){
            // For inter-service calls, register where the response needs to go
            calleeToCallerInvocationIds.put(invocationId, callerInvocationId);
        }

        // Create a new invocation processor on the runtime-side
        InvocationProcessor invocationProcessor = new InvocationProcessor(
                serviceName, instanceKey, invocationId, inputMessages);
        invocationProcessorHashMap.put(invocationId, invocationProcessor);

        // Wire invocation processor with the service-side state machine
        serviceInvocationStateMachineHandler.output().subscribe(invocationProcessor);
        invocationProcessor.subscribe(serviceInvocationStateMachineHandler.input());

        // Start invocation
        serviceInvocationStateMachineHandler.start();
    }

    /**
     * Handles the output messages of calls.
     * There are two options:
     * - They are test results and need to be added to the result list.
     * - They are responses to inter-service calls and the response needs to be forwarded to the caller.
     */
    public void handleCallResult(String functionInvocationId, Protocol.OutputStreamEntryMessage msg) {
        if(calleeToCallerInvocationIds.containsKey(functionInvocationId)) {
            // This was an inter-service call, redirect the answer
            LOG.debug("Forwarding inter-service call result");
            String callerInvocationId = calleeToCallerInvocationIds.get(functionInvocationId);
            // If this was a background call, the caller invocation Id was set to "ignore".
            // Only send a response to the caller, if it was not a background call.
            if(!callerInvocationId.equals("ignore")) {
                InvocationProcessor caller = invocationProcessorHashMap.get(callerInvocationId);
                caller.handleCompletionMessage(msg.getValue());
            }
        } else {
            // This is a test result; add it to the list
            LOG.debug("Adding new element to result set");
            synchronized (this.testResults) {
                this.testResults.add(msg);
            }
        }
    }

    public void handleAwakeableCompletion(String functionInvocationId, Protocol.CompleteAwakeableEntryMessage msg){
        InvocationProcessor caller = invocationProcessorHashMap.get(functionInvocationId);
        caller.routeMessage(completionMessage(msg.getEntryIndex(), msg.getPayload()));
    }

    // Future logic to send response back to TestDriver when done
    public CompletableFuture<List<Protocol.OutputStreamEntryMessage>> getFuture() {
        return future;
    }

    public void onError(Throwable throwable) {
        this.future.completeExceptionally(throwable);
    }

    public void onComplete() {
        this.future.complete(testResults);
    }

    public List<Protocol.OutputStreamEntryMessage> getTestResults() {
        List<Protocol.OutputStreamEntryMessage> l;
        synchronized (this.testResults) {
            l = new ArrayList<>(this.testResults);
        }
        return l;
    }

    public boolean getPublisherSubscriptionsCancelled() {
        // checks if all the invocation processor subscriptions were cancelled
        return invocationProcessorHashMap.values().stream()
                .allMatch(InvocationProcessor::getPublisherSubscriptionCancelled);
    }

    private Object extractKey(String serviceName, String methodName, Protocol.PollInputStreamEntryMessage message) {
        LOG.debug("Extracting key for service {} and method {}", serviceName, methodName);

        List<ServerServiceDefinition> servicesWithThisName = services.stream()
                .filter(el -> el.getServiceDescriptor().getName().equals(serviceName)).collect(Collectors.toList());
        if(servicesWithThisName.size() > 1) {
            throw new IllegalStateException(
                    "Multiple services registered with the same name: \""
                            + serviceName
                            + "\"");
        } else if (servicesWithThisName.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot find service with service name: \""
                            + serviceName
                            + "\". The only registered services are: "
                            + services.stream().map(el -> el.getServiceDescriptor().getName())
                            .collect(Collectors.joining(", ")));
        }

        var methodDefinition = (ServerMethodDefinition<MessageLite, MessageLite>)
                servicesWithThisName.get(0).getMethod(serviceName + "/" + methodName);

        var methodDescriptor =
                ((ProtoMethodDescriptorSupplier) methodDefinition.getMethodDescriptor().getSchemaDescriptor()).getMethodDescriptor();
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
            return ( (Message) methodDefinition.getMethodDescriptor()
                    .parseRequest(message.getValue().newInput())).getField(keyParam);
        } else if (serviceDescriptor.getOptions().getExtension(Ext.serviceType)
                == ServiceType.UNKEYED) {
            return UUID.randomUUID();
        } else {
            return "SINGLETON";
        }
    }
}
