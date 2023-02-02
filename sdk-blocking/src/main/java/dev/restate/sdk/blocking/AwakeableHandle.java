package dev.restate.sdk.blocking;

import dev.restate.generated.core.AwakeableIdentifier;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.serde.Serde;
import io.grpc.Status;

import javax.annotation.Nonnull;

// TODO javadocs
public interface AwakeableHandle {

    /**
     * Complete the suspended service instance waiting on the {@link Awakeable} identified by the
     * provided {@link AwakeableIdentifier}.
     *
     * @param payload the payload of the response. This can be either {@code byte[]}, {@link
     *     com.google.protobuf.ByteString}, or any object, which will be serialized by using the
     *     configured {@link Serde}. MUST NOT be null.
     * @see Awakeable
     */
    <T> void complete(TypeTag<T> typeTag, @Nonnull T payload);

    // TODO javadocs
    void fail(Status reason);

}
