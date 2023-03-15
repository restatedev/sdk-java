package dev.restate.sdk.core.impl;

/**
 * Callbacks for incoming rpc messages.
 *
 * <p>This interface is strongly inspired by {@link io.grpc.ServerCall.Listener}.
 *
 * @param <M> type of the incoming message
 */
public interface RestateServerCallListener<M> {
  void onMessageAndHalfClose(M message);

  void onCancel();

  void onComplete();

  void onReady();
}
