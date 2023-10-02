package dev.restate.sdk.core.impl;

interface SuspendableCallback {

  void onSuspend();

  void onError(Throwable e);
}
