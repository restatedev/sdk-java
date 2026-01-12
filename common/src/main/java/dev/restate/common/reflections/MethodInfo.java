// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.common.reflections;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

public class MethodInfo extends RuntimeException {
  private final String handlerName;
  private final Type inputType;
  private final Type outputType;

  private MethodInfo(String handlerName, Type inputType, Type outputType) {
    this.inputType = inputType;
    this.outputType = outputType;
    this.handlerName = handlerName;
  }

  public String getHandlerName() {
    return handlerName;
  }

  public Type getInputType() {
    return inputType;
  }

  public Type getOutputType() {
    return outputType;
  }

  public static MethodInfo fromMethod(Method method) {
    var handlerInfo = ReflectionUtils.mustHaveHandlerAnnotation(method);
    var genericParameters = method.getGenericParameterTypes();
    var inputType = genericParameters.length == 0 ? Void.TYPE : genericParameters[0];
    var outputType = method.getGenericReturnType();
    var handlerName = handlerInfo.name();

    return new MethodInfo(handlerName, inputType, outputType);
  }
}
