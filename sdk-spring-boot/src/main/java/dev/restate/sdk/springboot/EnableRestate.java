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
import org.springframework.context.annotation.Import;

/**
 * Add this annotation in your application class next to the {@link
 * org.springframework.boot.autoconfigure.SpringBootApplication} annotation to enable the Restate
 * Spring features.
 *
 * @see RestateComponent
 * @see RestateClientAutoConfiguration
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({
  RestateEndpointConfiguration.class,
  RestateHttpConfiguration.class,
  RestateClientAutoConfiguration.class
})
public @interface EnableRestate {}
