// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.common.reflections;

import dev.restate.sdk.annotation.Raw;
import dev.restate.serde.Serde;
import dev.restate.serde.TypeTag;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import org.jspecify.annotations.Nullable;

public class MethodInfo extends RuntimeException {
  private final String handlerName;
  private final TypeTag<?> inputType;
  private final TypeTag<?> outputType;

  private MethodInfo(String handlerName, TypeTag<?> inputType, TypeTag<?> outputType) {
    super("MethodInfo message should not be used", null, false, false);
    this.inputType = inputType;
    this.outputType = outputType;
    this.handlerName = handlerName;
  }

  public String getHandlerName() {
    return handlerName;
  }

  public TypeTag<? extends Object> getInputType() {
    return inputType;
  }

  public TypeTag<? extends Object> getOutputType() {
    return outputType;
  }

  public static MethodInfo fromMethod(Method method) {
    if (ReflectionUtils.isKotlinClass(method.getDeclaringClass())) {
      throw new IllegalArgumentException("Using Kotlin classes with Java's API is not supported");
    }
    var handlerInfo = ReflectionUtils.mustHaveHandlerAnnotation(method);
    var genericParameters = method.getGenericParameterTypes();
    var handlerName = handlerInfo.name();
    TypeTag<?> inputTypeTag =
        genericParameters.length == 0
            ? Serde.VOID
            : resolveTypeTag(
                genericParameters[0], method.getParameters()[0].getAnnotation(Raw.class));
    TypeTag<?> outputTypeTag =
        resolveTypeTag(method.getGenericReturnType(), method.getAnnotation(Raw.class));

    return new MethodInfo(handlerName, inputTypeTag, outputTypeTag);
  }

  private static TypeTag<?> resolveTypeTag(@Nullable Type type, @Nullable Raw rawAnnotation) {
    if (type == null) {
      return Serde.VOID;
    }

    if (rawAnnotation != null && !rawAnnotation.contentType().equals("application/octet-stream")) {
      return Serde.withContentType(rawAnnotation.contentType(), Serde.RAW);
    } else if (rawAnnotation != null) {
      return Serde.RAW;
    } else {
      return RestateUtils.typeTag(type);
    }
  }
}
