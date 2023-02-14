package dev.restate.sdk.testing;

import com.google.protobuf.ByteString;
import dev.restate.generated.service.protocol.Protocol;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;

public class StateStore {

    private static final Logger LOG = LogManager.getLogger(StateStore.class);

    private HashMap<String, ByteString> state;

    public StateStore(){
        this.state = new HashMap<>();
    }

    public ByteString get(String serviceName, ByteString key){
        return state.get(serviceName + key.toStringUtf8());
    }

    public void set(String serviceName, Protocol.SetStateEntryMessage msg) {
        LOG.trace("Received setStateEntryMessage: " + msg.toString());
        state.put(serviceName + msg.getKey().toStringUtf8(), msg.getValue());
    }

    // Clears state for a single key
    public void clear(String serviceName, Protocol.ClearStateEntryMessage msg) {
        LOG.trace("Received clearStateEntryMessage: " + msg.toString());
        state.remove(serviceName + msg.getKey().toStringUtf8());
    }

}
