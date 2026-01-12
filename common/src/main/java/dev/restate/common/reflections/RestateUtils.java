// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.common.reflections;

import dev.restate.common.InvocationOptions;
import dev.restate.common.Request;
import dev.restate.common.Target;
import dev.restate.serde.Serde;
import dev.restate.serde.TypeRef;
import dev.restate.serde.TypeTag;
import java.lang.reflect.Type;
import org.jspecify.annotations.Nullable;

public final class RestateUtils {

  public static <Req, Res> Request<Req, Res> toRequest(
      String serviceName,
      @Nullable String key,
      String handlerName,
      TypeTag<Req> reqTypeTag,
      TypeTag<Res> resTypeTag,
      Req request,
      @Nullable InvocationOptions options) {
    var builder =
        Request.of(
            Target.virtualObject(serviceName, key, handlerName), reqTypeTag, resTypeTag, request);
    if (options != null) {
      builder.setIdempotencyKey(options.getIdempotencyKey());
      if (options.getHeaders() != null) {
        builder.setHeaders(options.getHeaders());
      }
    }

    return builder.build();
  }

  public static TypeTag<?> typeTag(Type type) {
    if (type.equals(Void.TYPE)) {
      return Serde.VOID;
    }
    return TypeTag.of(
        new TypeRef<>() {
          @Override
          public Type getType() {
            return type;
          }
        });
  }
}
