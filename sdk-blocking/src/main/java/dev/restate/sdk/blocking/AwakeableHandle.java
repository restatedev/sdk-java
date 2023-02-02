package dev.restate.sdk.blocking;

import dev.restate.sdk.core.TypeTag;
import io.grpc.Status;

import javax.annotation.Nonnull;

public interface AwakeableHandle {

    <T> void complete(TypeTag<T> typeTag, @Nonnull T payload);

    void fail(Status reason);

}
