package dev.restate.sdk.testing;

import com.google.protobuf.ByteString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.stream.Collectors;

class StateStore {

    private static StateStore INSTANCE;

    private static final Logger LOG = LogManager.getLogger(StateStore.class);

    private final HashMap<String, ByteString> state;

    private StateStore(){
        this.state = new HashMap<>();
    }

    public synchronized static void init(){
        LOG.debug("Initializing");
        if(INSTANCE != null){
            throw new AssertionError("StateStore was already initialized. You cannot call init twice.");
        }
        INSTANCE = new StateStore();
    }

    public static StateStore get(){
        LOG.debug("Retrieving state store instance");
        if(INSTANCE == null){
            throw new AssertionError("StateStore was not initialized. Did you initialize the TestRestateRuntime?");
        }
        return INSTANCE;
    }

    public static void close(){
        LOG.debug("Closing state store instance");
        INSTANCE = null;
    }


    public ByteString get(String serviceName, String instanceKey, ByteString key){
        return state.get(serviceName + "/" + instanceKey + "/" + key.toStringUtf8());
    }

    public void set(String serviceName, String instanceKey, ByteString key, ByteString value) {
        state.put(serviceName + "/" + instanceKey + "/" + key.toStringUtf8(), value);
        LOG.debug("State store contents: " + getContentsAsString());
    }

    // Clears state for a single key
    public void clear(String serviceName, String instanceKey, ByteString key) {
        state.remove(serviceName + "/" + instanceKey + "/" + key.toStringUtf8());
    }

    public String getContentsAsString(){
        return state.entrySet().stream()
                .map(entry -> "{ key: " + entry.getKey() + ", value: " + entry.getValue().toStringUtf8() + "}")
                .collect(Collectors.joining(", "));
    }
}
