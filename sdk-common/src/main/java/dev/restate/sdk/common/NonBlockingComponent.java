// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.common;

/**
 * Marker interface for non-blocking components. This is used by some component endpoint
 * implementations (like http-vertx) to select on which executor/context the component code should
 * be executed. Refer to the *EndpointBuilder javadocs for more details.
 */
public interface NonBlockingComponent extends Component {}
