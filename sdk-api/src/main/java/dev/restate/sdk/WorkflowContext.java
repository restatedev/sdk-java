// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

/**
 * This interface can be used only within workflow handlers of workflow. It extends {@link Context}
 * adding access to the workflow instance key-value state storage and to the {@link DurablePromise}
 * API.
 *
 * <p>NOTE: This interface MUST NOT be accessed concurrently since it can lead to different
 * orderings of user actions, corrupting the execution of the invocation.
 *
 * @see Context
 * @see ObjectContext
 * @deprecated The {@code Context}-parameter programming model is superseded by the reflection-based
 *     API. Rather than accepting a {@code WorkflowContext} parameter, use {@code
 *     Restate.promise(...)}, {@code Restate.promiseHandle(...)} and {@code Restate.state()} inside
 *     the handler. See the <a
 *     href="https://github.com/restatedev/sdk-java/blob/main/MIGRATION.md">migration guide</a>.
 */
@Deprecated(since = "2.9", forRemoval = true)
public interface WorkflowContext extends SharedWorkflowContext, ObjectContext {}
