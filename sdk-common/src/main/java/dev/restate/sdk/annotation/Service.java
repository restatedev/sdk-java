// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to define a class/interface as Restate Service. This triggers the code generation of
 * the related Client class and the {@link
 * dev.restate.sdk.common.syscalls.ServiceDefinitionFactory}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Service {
  /**
   * Name of the Service for Restate. If not provided, it will be the simple class name of the
   * annotated element.
   */
  String name() default "";
}
