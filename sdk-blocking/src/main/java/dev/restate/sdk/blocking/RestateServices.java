package dev.restate.sdk.blocking;

import com.google.protobuf.MessageLite;
import io.grpc.MethodDescriptor;

// TODO javadocs, explain some properties of restate coordination features
public interface RestateServices {

    /**
     * Invoke another Restate service method.
     *
     * @return an {@link Awaitable} that wraps the Restate service method result
     */
    <T extends MessageLite, R extends MessageLite> Awaitable<R> call(
            MethodDescriptor<T, R> methodDescriptor, T parameter);

    /** Invoke another Restate service in a fire and forget fashion. */
    <T extends MessageLite> void backgroundCall(
            MethodDescriptor<T, ? extends MessageLite> methodDescriptor, T parameter);

    // TODO delayed call https://github.com/restatedev/restate/issues/20

}
