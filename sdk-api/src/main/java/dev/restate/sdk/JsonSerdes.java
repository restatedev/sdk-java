// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import dev.restate.common.Slice;
import dev.restate.common.function.ThrowingBiConsumer;
import dev.restate.common.function.ThrowingFunction;
import dev.restate.serde.Serde;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import org.jspecify.annotations.NonNull;

/**
 * @deprecated For primitive types, simply use the overloads of the {@link Context}/{@link
 *     dev.restate.sdk.common.StateKey}/{@link dev.restate.sdk.common.DurablePromiseKey} methods
 *     accepting {@link Class}, for example {@code ctx.run(String.class, myClosure)} or {@code
 *     ctx.awakeable(Integer.TYPE)} or {@code StateKey.of("key", String.class)}
 */
@Deprecated(since = "2.0", forRemoval = true)
public abstract class JsonSerdes {

  private JsonSerdes() {}

  /**
   * @deprecated For primitive types, simply use the overloads of the {@link Context}/{@link
   *     dev.restate.sdk.common.StateKey}/{@link dev.restate.sdk.common.DurablePromiseKey} methods
   *     accepting {@link Class}, for example {@code ctx.run(String.class, myClosure)} or {@code
   *     ctx.awakeable(Integer.TYPE)} or {@code StateKey.of("key", String.class)}
   */
  @Deprecated(since = "2.0", forRemoval = true)
  public static final Serde<@NonNull String> STRING =
      usingJackson(
          "string",
          JsonGenerator::writeString,
          p -> {
            if (p.nextToken() != JsonToken.VALUE_STRING) {
              throw new IllegalStateException(
                  "Expecting token " + JsonToken.VALUE_STRING + ", got " + p.getCurrentToken());
            }
            return p.getText();
          });

  /**
   * @deprecated For primitive types, simply use the overloads of the {@link Context}/{@link
   *     dev.restate.sdk.common.StateKey}/{@link dev.restate.sdk.common.DurablePromiseKey} methods
   *     accepting {@link Class}, for example {@code ctx.run(String.class, myClosure)} or {@code
   *     ctx.awakeable(Integer.TYPE)} or {@code StateKey.of("key", String.class)}
   */
  @Deprecated(since = "2.0", forRemoval = true)
  public static final Serde<@NonNull Boolean> BOOLEAN =
      usingJackson(
          "boolean",
          JsonGenerator::writeBoolean,
          p -> {
            p.nextToken();
            return p.getBooleanValue();
          });

  /**
   * @deprecated For primitive types, simply use the overloads of the {@link Context}/{@link
   *     dev.restate.sdk.common.StateKey}/{@link dev.restate.sdk.common.DurablePromiseKey} methods
   *     accepting {@link Class}, for example {@code ctx.run(String.class, myClosure)} or {@code
   *     ctx.awakeable(Integer.TYPE)} or {@code StateKey.of("key", String.class)}
   */
  @Deprecated(since = "2.0", forRemoval = true)
  public static final Serde<@NonNull Byte> BYTE =
      usingJackson(
          "number",
          JsonGenerator::writeNumber,
          p -> {
            p.nextToken();
            return p.getByteValue();
          });

  /**
   * @deprecated For primitive types, simply use the overloads of the {@link Context}/{@link
   *     dev.restate.sdk.common.StateKey}/{@link dev.restate.sdk.common.DurablePromiseKey} methods
   *     accepting {@link Class}, for example {@code ctx.run(String.class, myClosure)} or {@code
   *     ctx.awakeable(Integer.TYPE)} or {@code StateKey.of("key", String.class)}
   */
  @Deprecated(since = "2.0", forRemoval = true)
  public static final Serde<@NonNull Short> SHORT =
      usingJackson(
          "number",
          JsonGenerator::writeNumber,
          p -> {
            p.nextToken();
            return p.getShortValue();
          });

  /**
   * @deprecated For primitive types, simply use the overloads of the {@link Context}/{@link
   *     dev.restate.sdk.common.StateKey}/{@link dev.restate.sdk.common.DurablePromiseKey} methods
   *     accepting {@link Class}, for example {@code ctx.run(String.class, myClosure)} or {@code
   *     ctx.awakeable(Integer.TYPE)} or {@code StateKey.of("key", String.class)}
   */
  @Deprecated(since = "2.0", forRemoval = true)
  public static final Serde<@NonNull Integer> INT =
      usingJackson(
          "number",
          JsonGenerator::writeNumber,
          p -> {
            p.nextToken();
            return p.getIntValue();
          });

  /**
   * @deprecated For primitive types, simply use the overloads of the {@link Context}/{@link
   *     dev.restate.sdk.common.StateKey}/{@link dev.restate.sdk.common.DurablePromiseKey} methods
   *     accepting {@link Class}, for example {@code ctx.run(String.class, myClosure)} or {@code
   *     ctx.awakeable(Integer.TYPE)} or {@code StateKey.of("key", String.class)}
   */
  @Deprecated(since = "2.0", forRemoval = true)
  public static final Serde<@NonNull Long> LONG =
      usingJackson(
          "number",
          JsonGenerator::writeNumber,
          p -> {
            p.nextToken();
            return p.getLongValue();
          });

  /**
   * @deprecated For primitive types, simply use the overloads of the {@link Context}/{@link
   *     dev.restate.sdk.common.StateKey}/{@link dev.restate.sdk.common.DurablePromiseKey} methods
   *     accepting {@link Class}, for example {@code ctx.run(String.class, myClosure)} or {@code
   *     ctx.awakeable(Integer.TYPE)} or {@code StateKey.of("key", String.class)}
   */
  @Deprecated(since = "2.0", forRemoval = true)
  public static final Serde<@NonNull Float> FLOAT =
      usingJackson(
          "number",
          JsonGenerator::writeNumber,
          p -> {
            p.nextToken();
            return p.getFloatValue();
          });

  /**
   * @deprecated For primitive types, simply use the overloads of the {@link Context}/{@link
   *     dev.restate.sdk.common.StateKey}/{@link dev.restate.sdk.common.DurablePromiseKey} methods
   *     accepting {@link Class}, for example {@code ctx.run(String.class, myClosure)} or {@code
   *     ctx.awakeable(Integer.TYPE)} or {@code StateKey.of("key", String.class)}
   */
  @Deprecated(since = "2.0", forRemoval = true)
  public static final Serde<@NonNull Double> DOUBLE =
      usingJackson(
          "number",
          JsonGenerator::writeNumber,
          p -> {
            p.nextToken();
            return p.getDoubleValue();
          });

  // --- Helpers for jackson-core

  private static final JsonFactory JSON_FACTORY = new JsonFactory();

  private static <T extends @NonNull Object> Serde<T> usingJackson(
      String type,
      ThrowingBiConsumer<JsonGenerator, T> serializer,
      ThrowingFunction<JsonParser, T> deserializer) {
    return new Serde<>() {

      @Override
      public Schema jsonSchema() {
        return new JsonSchema(Map.of("type", type));
      }

      @Override
      public Slice serialize(T value) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (JsonGenerator gen = JSON_FACTORY.createGenerator(outputStream)) {
          serializer.asBiConsumer().accept(gen, value);
        } catch (IOException e) {
          throw new RuntimeException("Cannot create JsonGenerator", e);
        }
        return Slice.wrap(outputStream.toByteArray());
      }

      @Override
      public T deserialize(Slice value) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(value.toByteArray());
        try (JsonParser parser = JSON_FACTORY.createParser(inputStream)) {
          return deserializer.asFunction().apply(parser);
        } catch (IOException e) {
          throw new RuntimeException("Cannot create JsonGenerator", e);
        }
      }

      @Override
      public String contentType() {
        return "application/json";
      }
    };
  }
}
