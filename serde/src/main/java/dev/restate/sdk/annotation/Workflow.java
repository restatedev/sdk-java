// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.annotation;

import dev.restate.sdk.definition.ServiceDefinitionFactory;

import java.lang.annotation.*;

/**
 * Annotation to define a class/interface as Restate Workflow. This triggers the code generation of
 * the related Client class and the {@link
 * ServiceDefinitionFactory}. When defining a class/interface as
 * workflow, you must annotate one of its methods too as {@link Workflow}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Workflow {
  /**
   * Name of the Workflow for Restate. If not provided, it will be the simple class name of the
   * annotated element.
   */
  String name() default "";
}
