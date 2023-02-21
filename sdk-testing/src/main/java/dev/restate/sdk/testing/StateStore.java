package dev.restate.sdk.testing;

import com.google.protobuf.ByteString;
import java.util.HashMap;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class StateStore {

  private static final Logger LOG = LogManager.getLogger(StateStore.class);

  private final HashMap<String, ByteString> state;

  private StateStore() {
    this.state = new HashMap<>();
  }

  private static class StateStoreHelper {
    private static final StateStore INSTANCE = new StateStore();
  }

  public static StateStore get() {
    return StateStoreHelper.INSTANCE;
  }

  public static void close() {
    LOG.debug("Closing state store instance");
    StateStoreHelper.INSTANCE.state.clear();
  }

  public ByteString get(String serviceName, String instanceKey, ByteString key) {
    return state.get(asKey(serviceName, instanceKey, key));
  }

  public void set(String serviceName, String instanceKey, ByteString key, ByteString value) {
    state.put(asKey(serviceName, instanceKey, key), value);
    LOG.debug("State store contents: " + toString());
  }

  // Clears state for a single key
  public void clear(String serviceName, String instanceKey, ByteString key) {
    state.remove(asKey(serviceName, instanceKey, key));
  }

  private String asKey(String serviceName, String instanceKey, ByteString key) {
    return serviceName + "/" + instanceKey + "/" + key.toStringUtf8();
  }

  @Override
  public String toString() {
    return state.entrySet().stream()
        .map(
            entry ->
                "{ key: " + entry.getKey() + ", value: " + entry.getValue().toStringUtf8() + "}")
        .collect(Collectors.joining(", "));
  }
}
