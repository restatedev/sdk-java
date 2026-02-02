// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.springboot;

import dev.restate.sdk.annotation.Service;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;

/**
 * Proxy annotation for {@link Service}, to avoid naming clashes with Spring's built in annotations
 *
 * @see Service
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Service
@RestateComponent
public @interface RestateService {

  /**
   * Bean name to use to configure this component.
   *
   * <p>The bean MUST be an instance of {@link RestateComponentProperties}.
   */
  @AliasFor(annotation = RestateComponent.class, attribute = "configuration")
  String configuration() default "";
}
