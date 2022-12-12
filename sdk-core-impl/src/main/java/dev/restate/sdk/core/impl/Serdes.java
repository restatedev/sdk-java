package dev.restate.sdk.core.impl;

import dev.restate.sdk.core.serde.Serde;
import dev.restate.sdk.core.serde.SerdeProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Collectors;

class Serdes {

  private final Serde discoveredSerde;

  private Serdes() {
    this.discoveredSerde =
        chain(
            ServiceLoader.load(SerdeProvider.class).stream()
                .map(serdeProviderProvider -> serdeProviderProvider.get().create())
                .collect(Collectors.toList()));
  }

  private static class SingletonHelper {
    private static final Serdes INSTANCE = new Serdes();
  }

  static Serdes getInstance() {
    return SingletonHelper.INSTANCE;
  }

  public Serde getDiscoveredSerde() {
    return discoveredSerde;
  }

  /**
   * See {@link ChainedSerde}.
   *
   * @throws IllegalArgumentException if the input chain contains more than one {@link Serde} which
   *     {@link Serde#supportsAnyType()} returns true.
   */
  static Serde chain(Serde... chain) throws IllegalArgumentException {
    return chain(Arrays.asList(chain));
  }

  /**
   * See {@link ChainedSerde}.
   *
   * @throws IllegalArgumentException if the input chain contains more than one {@link Serde} which
   *     {@link Serde#supportsAnyType()} returns true.
   */
  static Serde chain(List<Serde> chain) throws IllegalArgumentException {
    if (chain.size() == 0) {
      return NoopSerde.INSTANCE;
    }
    if (chain.size() == 1) {
      return chain.get(0);
    }
    return new ChainedSerde(chain);
  }

  /**
   * This serde will try to serialize/deserialize by finding the first serde of the provided ones
   * which supports the given type.
   *
   * <p>There can only be one registered {@link Serde} which {@link Serde#supportsAnyType()} returns
   * {@code true}.
   */
  private static class ChainedSerde implements Serde {

    private final Serde[] chain;
    private final boolean supportsAnyType;

    private ChainedSerde(List<Serde> chain) {
      int supportsAnyTypeIdx = -1;
      for (int i = 0; i < chain.size(); i++) {
        if (chain.get(i).supportsAnyType()) {
          if (supportsAnyTypeIdx != -1) {
            throw new IllegalArgumentException(
                "More than one serde in the Serde chain supporting any type: " + chain);
          }
          supportsAnyTypeIdx = i;
        }
      }
      if (supportsAnyTypeIdx != -1) {
        List<Serde> newChain = new ArrayList<>(chain);
        Serde s = newChain.remove(supportsAnyTypeIdx);
        newChain.add(s);
        this.chain = newChain.toArray(Serde[]::new);
        this.supportsAnyType = true;
      } else {
        this.chain = chain.toArray(Serde[]::new);
        this.supportsAnyType = false;
      }
    }

    @Override
    public boolean supportsAnyType() {
      return supportsAnyType;
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

  static class NoopSerde implements Serde {

    static final NoopSerde INSTANCE = new NoopSerde();

    @Override
    public boolean supportsAnyType() {
      return false;
    }

    @Override
    public <T> T deserialize(Class<T> clazz, byte[] bytes) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> byte[] serialize(T value) {
      throw new UnsupportedOperationException();
    }
  }
}
