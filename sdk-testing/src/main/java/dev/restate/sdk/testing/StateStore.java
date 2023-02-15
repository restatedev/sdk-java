package dev.restate.sdk.testing;

import com.google.protobuf.ByteString;

import java.util.HashMap;

public class StateStore {

    private final HashMap<String, ByteString> state;

    public StateStore(){
        this.state = new HashMap<>();
    }

    public ByteString get(String serviceName, String instanceKey, ByteString key){
        return state.get(serviceName + "/" + instanceKey + "/" + key.toStringUtf8());
    }

    public void set(String serviceName, String instanceKey, ByteString key, ByteString value) {
        state.put(serviceName + "/" + instanceKey + "/" + key.toStringUtf8(), value);
    }

    // Clears state for a single key
    public void clear(String serviceName, String instanceKey, ByteString key) {
        state.remove(serviceName + "/" + instanceKey + "/" + key.toStringUtf8());
    }

}
