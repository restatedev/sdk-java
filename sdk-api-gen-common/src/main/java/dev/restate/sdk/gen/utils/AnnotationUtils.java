// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.gen.utils;

import java.lang.annotation.Annotation;
import java.util.Objects;

public class AnnotationUtils {
  public static Object getAnnotationDefaultValue(
      Class<? extends Annotation> annotation, String name) {
    try {
      return Objects.requireNonNull(annotation.getMethod(name).getDefaultValue());
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }
}
