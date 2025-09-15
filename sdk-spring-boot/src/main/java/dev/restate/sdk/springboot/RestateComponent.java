// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.springboot;

import java.lang.annotation.*;
import org.springframework.stereotype.Component;

/**
 * Add this annotation to a class annotated with Restate's {@link
 * dev.restate.sdk.annotation.Service} or {@link dev.restate.sdk.annotation.VirtualObject} or {@link
 * dev.restate.sdk.annotation.Workflow} to bind them to the Restate HTTP Endpoint.
 *
 * <p>You can configure the Restate HTTP Endpoint using {@link RestateEndpointProperties} and {@link
 * RestateHttpServerProperties}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface RestateComponent {
  /**
   * Bean name to use to configure this component. The bean MUST be an instance of {@link
   * RestateServiceConfigurator}.
   */
  String configuration() default "";
}
