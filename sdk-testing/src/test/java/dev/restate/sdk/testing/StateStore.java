package dev.restate.sdk.testing;

import com.google.protobuf.ByteString;

import java.util.HashMap;

// Singleton state store
final class StateStore {

    // Restate state of all the services
    private HashMap<ByteString, ByteString> state = new HashMap<>();

    private static StateStore INSTANCE;

    private StateStore(){}

    public static StateStore getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new StateStore();
        }
        return INSTANCE;
    }

    public void set(ByteString key, ByteString value){
        state.put(key, value);
    }

    public ByteString get(ByteString key) {
        return state.get(key);
    }

    public void clear(ByteString key){
        state.remove(key);
    }
}
