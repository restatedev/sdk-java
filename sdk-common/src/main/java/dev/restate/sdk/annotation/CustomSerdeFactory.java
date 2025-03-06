// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.annotation;

import dev.restate.serde.SerdeFactory;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Define the custom {@link SerdeFactory} to use for this service/virtual object/workflow.
 *
 * <p>This should be placed alongside the {@link Service}/{@link VirtualObject}/{@link Workflow}
 * annotation.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface CustomSerdeFactory {
  Class<? extends SerdeFactory> value();
}
