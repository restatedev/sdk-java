package dev.restate.sdk.core.serde;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * This serde will try to serialize/deserialize by finding the first serde of the provided ones
 * which supports the given type.
 */
public class ChainedSerde implements Serde {

  private final List<Serde> chain;

  public ChainedSerde(Serde... chain) {
    this(Arrays.asList(chain));
  }

  public ChainedSerde(List<Serde> chain) {
    this.chain = chain;
  }

  @Override
  public <T> T deserialize(Class<T> clazz, byte[] bytes) {
    return tryChain(s -> s.deserialize(clazz, bytes));
  }

  @SuppressWarnings("TypeParameterUnusedInFormals")
  @Override
  public <T> T deserialize(Object typeTag, byte[] value) {
    return tryChain(s -> s.deserialize(typeTag, value));
  }

  @Override
  public <T> byte[] serialize(T value) {
    return tryChain(s -> s.serialize(value));
  }

  @SuppressWarnings("TypeParameterUnusedInFormals")
  private <T> T tryChain(Function<Serde, T> doSerde) {
    for (Serde serde : chain) {
      try {
        return doSerde.apply(serde);
      } catch (UnsupportedOperationException ignored) {
        // Let's try with the next serde
      }
    }
    throw new UnsupportedOperationException("No serde found supporting the provided operation");
  }
}
