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
 * Defines a method as a Shared handler. It can be used only on methods of either {@link
 * VirtualObject} or {@link Workflow}.
 *
 * <p>Shared handlers can execute concurrently with the other handlers of Virtual Objects or
 * Workflows. They can therefore not set or clear state.
 *
 * <p>This implies the annotation {@link Handler}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface Shared {}
