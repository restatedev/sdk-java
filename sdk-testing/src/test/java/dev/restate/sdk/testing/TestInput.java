package dev.restate.sdk.testing;

import com.google.protobuf.MessageLite;
import dev.restate.generated.service.protocol.Protocol;
import io.grpc.MethodDescriptor;

import java.util.List;

public class TestInput{
    private final String method;
    private final String service;

    private final Protocol.PollInputStreamEntryMessage inputMessage;

    TestInput(MethodDescriptor<?, ?> method, Protocol.PollInputStreamEntryMessage msg){
        this.service = method.getServiceName();
        this.method = method.getBareMethodName();
        this.inputMessage = msg;
    }

    static TestInput of(MethodDescriptor<?, ?> method, Protocol.PollInputStreamEntryMessage inputMsg){
        return new TestInput(method, inputMsg);
    }

    public String getService() {
        return service;
    }

    public String getMethod() {
        return method;
    }

    public MessageLite getInputMessage() {
        return inputMessage;
    }


}