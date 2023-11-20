package dev.restate.sdk.core;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import javax.annotation.Nullable;

public abstract class CoreSerdes {

  private CoreSerdes() {}

  /** Noop {@link Serde} for void. */
  public static Serde<Void> VOID =
      new Serde<>() {
        @Override
        public byte[] serialize(Void value) {
          return new byte[0];
        }

        @Override
        public ByteString serializeToByteString(@Nullable Void value) {
          return ByteString.EMPTY;
        }

        @Override
        public Void deserialize(byte[] value) {
          return null;
        }

        @Override
        public Void deserialize(ByteString byteString) {
          return null;
        }
      };

  /** Pass through {@link Serde} for byte array. */
  public static Serde<byte[]> BYTES =
      new Serde<>() {
        @Override
        public byte[] serialize(byte[] value) {
          return Objects.requireNonNull(value);
        }

        @Override
        public byte[] deserialize(byte[] value) {
          return value;
        }
      };

  /** Serde for primitive types */
  public static Serde<String> STRING_UTF8 =
      Serde.using(
          s -> s.getBytes(StandardCharsets.UTF_8), b -> new String(b, StandardCharsets.UTF_8));

  public static Serde<Boolean> BOOLEAN =
      Serde.using(
          bool -> {
            byte[] encodedValue = new byte[1];
            encodedValue[0] = (byte) (bool ? 1 : 0);
            return encodedValue;
          },
          encodedValue -> encodedValue[0] != 0);
  public static Serde<Byte> BYTE =
      Serde.using(
          boxedN -> {
            byte[] encodedValue = new byte[1];
            encodedValue[0] = boxedN;
            return encodedValue;
          },
          encodedValue -> encodedValue[0]);
  public static Serde<Short> SHORT =
      Serde.using(
          boxedN -> {
            short n = boxedN;
            byte[] encodedValue = new byte[Short.SIZE / Byte.SIZE];
            encodedValue[1] = (byte) (n >> Byte.SIZE);
            encodedValue[0] = (byte) n;
            return encodedValue;
          },
          encodedValue -> {
            short n = 0;
            n |= (short) ((encodedValue[1] & 0xFF) << 8);
            n |= (short) (encodedValue[0] & 0xFF);
            return n;
          });

  public static Serde<Integer> INT =
      Serde.using(
          boxedN -> {
            int n = boxedN;
            byte[] encodedValue = new byte[Integer.SIZE / Byte.SIZE];
            encodedValue[3] = (byte) (n >> Byte.SIZE * 3);
            encodedValue[2] = (byte) (n >> Byte.SIZE * 2);
            encodedValue[1] = (byte) (n >> Byte.SIZE);
            encodedValue[0] = (byte) n;
            return encodedValue;
          },
          encodedValue -> {
            int n = 0;
            n |= (encodedValue[3] & 0xFF) << Byte.SIZE * 3;
            n |= (encodedValue[2] & 0xFF) << Byte.SIZE * 2;
            n |= (encodedValue[1] & 0xFF) << Byte.SIZE;
            n |= encodedValue[0] & 0xFF;
            return n;
          });

  public static Serde<Long> LONG =
      Serde.using(
          boxedN -> {
            long n = boxedN;
            byte[] encodedValue = new byte[Long.SIZE / Byte.SIZE];
            encodedValue[7] = (byte) (n >> Byte.SIZE * 7);
            encodedValue[6] = (byte) (n >> Byte.SIZE * 6);
            encodedValue[5] = (byte) (n >> Byte.SIZE * 5);
            encodedValue[4] = (byte) (n >> Byte.SIZE * 4);
            encodedValue[3] = (byte) (n >> Byte.SIZE * 3);
            encodedValue[2] = (byte) (n >> Byte.SIZE * 2);
            encodedValue[1] = (byte) (n >> Byte.SIZE);
            encodedValue[0] = (byte) n;
            return encodedValue;
          },
          encodedValue -> {
            long n = 0;
            n |= ((long) (encodedValue[7] & 0xFF) << Byte.SIZE * 7);
            n |= ((long) (encodedValue[6] & 0xFF) << Byte.SIZE * 6);
            n |= ((long) (encodedValue[5] & 0xFF) << Byte.SIZE * 5);
            n |= ((long) (encodedValue[4] & 0xFF) << Byte.SIZE * 4);
            n |= ((long) (encodedValue[3] & 0xFF) << Byte.SIZE * 3);
            n |= ((encodedValue[2] & 0xFF) << Byte.SIZE * 2);
            n |= ((encodedValue[1] & 0xFF) << Byte.SIZE);
            n |= (encodedValue[0] & 0xFF);
            return n;
          });

  public static Serde<Float> FLOAT =
      Serde.using(
          boxedN -> INT.serialize(Float.floatToIntBits(boxedN)),
          encodedValue -> Float.intBitsToFloat(INT.deserialize(encodedValue)));

  public static Serde<Double> DOUBLE =
      Serde.using(
          boxedN -> LONG.serialize(Double.doubleToLongBits(boxedN)),
          encodedValue -> Double.longBitsToDouble(LONG.deserialize(encodedValue)));

  public static <T extends MessageLite> Serde<T> ofProtobuf(Parser<T> parser) {
    return new Serde<>() {
      @Override
      public byte[] serialize(@Nullable T value) {
        return Objects.requireNonNull(value).toByteArray();
      }

      @Override
      public T deserialize(byte[] value) {
        try {
          return parser.parseFrom(value);
        } catch (InvalidProtocolBufferException e) {
          throw new RuntimeException("Cannot deserialize Protobuf object", e);
        }
      }

      // -- We reimplement the ByteString variants here as it might be more efficient to use them.
      @Override
      public ByteString serializeToByteString(@Nullable T value) {
        return Objects.requireNonNull(value).toByteString();
      }

      @Override
      public T deserialize(ByteString byteString) {
        try {
          return parser.parseFrom(byteString);
        } catch (InvalidProtocolBufferException e) {
          throw new RuntimeException("Cannot deserialize Protobuf object", e);
        }
      }
    };
  }
}
