package dev.restate.sdk.testing;

import java.util.*;
import java.util.concurrent.*;

import com.google.protobuf.MessageLite;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.impl.InvocationHandler;
import dev.restate.sdk.core.impl.RestateGrpcServer;
import io.grpc.ServerServiceDefinition;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static dev.restate.sdk.testing.ProtoUtils.*;

// Singleton Runtime
final class TestRestateRuntime {

    private static final Logger LOG = LogManager.getLogger(TestRestateRuntime.class);

    private final CompletableFuture<List<Protocol.OutputStreamEntryMessage>> future = new CompletableFuture<>();

    private final RestateGrpcServer server;

    private final StateStore stateStore;

    private final TestDriver.ThreadingModel threadingModel;


    // Output of test
    private final List<Protocol.OutputStreamEntryMessage> testResults = new ArrayList<>();


    HashMap<String, InvocationProcessor<MessageLite>> invocationProcessorHashMap;

    // For inter-service calls, we need to keep track of where the response needs to go
    HashMap<String, String> calleeToCallerInvocationIds;


//  private final AtomicBoolean publisherSubscriptionCancelled;

    public TestRestateRuntime(List<ServerServiceDefinition> services,
                              TestDriver.ThreadingModel threadingModel) {
        this.invocationProcessorHashMap = new HashMap<>();
        this.calleeToCallerInvocationIds = new HashMap<>();
        this.stateStore = new StateStore();
        this.threadingModel = threadingModel;
//    this.publisherSubscriptionCancelled = new AtomicBoolean(false);

        // Start Grpc server and add all the services
        RestateGrpcServer.Builder serverBuilder = RestateGrpcServer.newBuilder();
        for (ServerServiceDefinition svc : services) {
            serverBuilder.withService(svc);
        }
        server = serverBuilder.build();
    }

    public void handle(TestInput testInput) {
        handle(testInput.getService(), testInput.getMethod(), testInput.getInputMessage(), null);
    }

    public void handle(String serviceName, String methodName, MessageLite inputMessage, String callerFunctionInvocationId){
        String functionInvocationId = UUID.randomUUID().toString();

        //TODO executors
        // Create invocation handler on the side of the service
        InvocationHandler serviceInvocationStateMachineHandler =
                server.resolve(
                        serviceName,
                        methodName,
                        io.opentelemetry.context.Context.current(),
                        null,
                        null);

        Protocol.StartMessage startMessage = startMessage(serviceName, functionInvocationId, 1).build();
        List<MessageLite> inputMessages = List.of(startMessage, inputMessage);

        if(callerFunctionInvocationId != null){
            // For inter-service calls, register where the response needs to go
            calleeToCallerInvocationIds.put(functionInvocationId, callerFunctionInvocationId);
        }

        InvocationProcessor<MessageLite> invocationProcessor = new InvocationProcessor<>(serviceName, functionInvocationId, inputMessages, this, stateStore);
        invocationProcessorHashMap.put(functionInvocationId, invocationProcessor);

        // Wire invocation processor with the service-side state machine
        serviceInvocationStateMachineHandler.output().subscribe(invocationProcessor);
        invocationProcessor.subscribe(serviceInvocationStateMachineHandler.input());

        // Start invocation
        serviceInvocationStateMachineHandler.start();
    }

    public boolean getPublisherSubscriptionCancelled() {
        // TODO fix this and put this in the right place
        return true;
//    return publisherSubscriptionCancelled.get();
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
            InvocationProcessor<MessageLite> caller = invocationProcessorHashMap.get(callerInvocationId);
            caller.handleInterServiceCallResult(msg);
        } else {
            // This is a test result; add it to the list
            LOG.debug("Adding new element to result set");
            synchronized (this.testResults) {
                this.testResults.add(msg);
            }
        }
    }
}
