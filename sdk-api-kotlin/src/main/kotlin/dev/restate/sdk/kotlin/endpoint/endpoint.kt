// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin.endpoint

import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.endpoint.definition.HandlerDefinition
import dev.restate.sdk.endpoint.definition.InvocationRetryPolicy
import dev.restate.sdk.endpoint.definition.ServiceDefinition
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration

/** Endpoint builder function. */
fun endpoint(init: Endpoint.Builder.() -> Unit): Endpoint {
  val builder = Endpoint.builder()
  builder.init()
  return builder.build()
}

/**
 * Documentation as shown in the UI, Admin REST API, and the generated OpenAPI documentation of this
 * service.
 */
var ServiceDefinition.Configurator.documentation: String?
  get() {
    return this.documentation()
  }
  set(value) {
    this.documentation(value)
  }

/** Service metadata, as propagated in the Admin REST API. */
var ServiceDefinition.Configurator.metadata: Map<String, String>?
  get() {
    return this.metadata()
  }
  set(value) {
    this.metadata(value)
  }

/**
 * This timer guards against stalled invocations. Once it expires, Restate triggers a graceful
 * termination by asking the invocation to suspend (which preserves intermediate progress).
 *
 * The [abortTimeout] is used to abort the invocation, in case it doesn't react to the request to
 * suspend.
 *
 * This overrides the default inactivity timeout configured in the restate-server for all
 * invocations to this service.
 *
 * *NOTE:* You can set this field only if you register this service against restate-server >= 1.4,
 * otherwise the service discovery will fail.
 */
var ServiceDefinition.Configurator.inactivityTimeout: Duration?
  get() {
    return this.inactivityTimeout()?.toKotlinDuration()
  }
  set(value) {
    this.inactivityTimeout(value?.toJavaDuration())
  }

/**
 * This timer guards against stalled service/handler invocations that are supposed to terminate. The
 * abort timeout is started after the [inactivityTimeout] has expired and the service/handler
 * invocation has been asked to gracefully terminate. Once the timer expires, it will abort the
 * service/handler invocation.
 *
 * This timer potentially *interrupts* user code. If the user code needs longer to gracefully
 * terminate, then this value needs to be set accordingly.
 *
 * This overrides the default abort timeout configured in the restate-server for all invocations to
 * this service.
 *
 * *NOTE:* You can set this field only if you register this service against restate-server >= 1.4,
 * otherwise the service discovery will fail.
 */
var ServiceDefinition.Configurator.abortTimeout: Duration?
  get() {
    return this.abortTimeout()?.toKotlinDuration()
  }
  set(value) {
    this.abortTimeout(value?.toJavaDuration())
  }

/**
 * The retention duration of idempotent requests to this service.
 *
 * *NOTE:* You can set this field only if you register this service against restate-server >= 1.4,
 * otherwise the service discovery will fail.
 */
var ServiceDefinition.Configurator.idempotencyRetention: Duration?
  get() {
    return this.idempotencyRetention()?.toKotlinDuration()
  }
  set(value) {
    this.idempotencyRetention(value?.toJavaDuration())
  }

/**
 * The journal retention. When set, this applies to all requests to all handlers of this service.
 *
 * In case the request has an idempotency key, the [idempotencyRetention] caps the journal retention
 * time.
 *
 * *NOTE:* You can set this field only if you register this service against restate-server >= 1.4,
 * otherwise the service discovery will fail.
 *
 * @return this
 */
var ServiceDefinition.Configurator.journalRetention: Duration?
  get() {
    return this.journalRetention()?.toKotlinDuration()
  }
  set(value) {
    this.journalRetention(value?.toJavaDuration())
  }

/**
 * When set to `true`, lazy state will be enabled for all invocations to this service. This is
 * relevant only for workflows and virtual objects.
 *
 * *NOTE:* You can set this field only if you register this service against restate-server >= 1.4,
 * otherwise the service discovery will fail.
 */
var ServiceDefinition.Configurator.enableLazyState: Boolean?
  get() {
    return this.enableLazyState()
  }
  set(value) {
    this.enableLazyState(value)
  }

/**
 * When set to `true` this service, with all its handlers, cannot be invoked from the restate-server
 * HTTP and Kafka ingress, but only from other services.
 *
 * *NOTE:* You can set this field only if you register this service against restate-server >= 1.4,
 * otherwise the service discovery will fail.
 */
var ServiceDefinition.Configurator.ingressPrivate: Boolean?
  get() {
    return this.ingressPrivate()
  }
  set(value) {
    this.ingressPrivate(value)
  }

/**
 * Retry policy used by Restate when invoking this service.
 *
 * <p><b>NOTE:</b> You can set this field only if you register this service against
 * restate-server >= 1.5, otherwise the service discovery will fail.
 *
 * @see InvocationRetryPolicy
 */
var ServiceDefinition.Configurator.invocationRetryPolicy: InvocationRetryPolicy?
  get() {
    return this.invocationRetryPolicy()
  }
  set(value) {
    this.invocationRetryPolicy(value)
  }

/**
 * Set the acceptable content type when ingesting HTTP requests. Wildcards can be used, e.g.
 * `application/*` or `*/*`.
 */
var HandlerDefinition.Configurator.acceptContentType: String?
  get() {
    return this.acceptContentType()
  }
  set(value) {
    this.acceptContentType(value)
  }

/**
 * Documentation as shown in the UI, Admin REST API, and the generated OpenAPI documentation of this
 * handler.
 */
var HandlerDefinition.Configurator.documentation: String?
  get() {
    return this.documentation()
  }
  set(value) {
    this.documentation(value)
  }

/** Handler metadata, as propagated in the Admin REST API. */
var HandlerDefinition.Configurator.metadata: Map<String, String>?
  get() {
    return this.metadata()
  }
  set(value) {
    this.metadata(value)
  }

/**
 * This timer guards against stalled invocations. Once it expires, Restate triggers a graceful
 * termination by asking the invocation to suspend (which preserves intermediate progress).
 *
 * The [abortTimeout] is used to abort the invocation, in case it doesn't react to the request to
 * suspend.
 *
 * This overrides the inactivity timeout set for the service and the default set in restate-server.
 *
 * *NOTE:* You can set this field only if you register this service against restate-server >= 1.4,
 * otherwise the service discovery will fail.
 */
var HandlerDefinition.Configurator.inactivityTimeout: Duration?
  get() {
    return this.inactivityTimeout()?.toKotlinDuration()
  }
  set(value) {
    this.inactivityTimeout(value?.toJavaDuration())
  }

/**
 * This timer guards against stalled invocations that are supposed to terminate. The abort timeout
 * is started after the [inactivityTimeout] has expired and the invocation has been asked to
 * gracefully terminate. Once the timer expires, it will abort the invocation.
 *
 * This timer potentially *interrupts* user code. If the user code needs longer to gracefully
 * terminate, then this value needs to be set accordingly.
 *
 * This overrides the abort timeout set for the service and the default set in restate-server.
 *
 * *NOTE:* You can set this field only if you register this service against restate-server >= 1.4,
 * otherwise the service discovery will fail.
 */
var HandlerDefinition.Configurator.abortTimeout: Duration?
  get() {
    return this.abortTimeout()?.toKotlinDuration()
  }
  set(value) {
    this.abortTimeout(value?.toJavaDuration())
  }

/**
 * The retention duration of idempotent requests to this service.
 *
 * *NOTE:* You can set this field only if you register this service against restate-server >= 1.4,
 * otherwise the service discovery will fail.
 */
var HandlerDefinition.Configurator.idempotencyRetention: Duration?
  get() {
    return this.idempotencyRetention()?.toKotlinDuration()
  }
  set(value) {
    this.idempotencyRetention(value?.toJavaDuration())
  }

/**
 * The retention duration for this workflow handler.
 *
 * *NOTE:* You can set this field only if you register this service against restate-server >= 1.4,
 * otherwise the service discovery will fail.
 */
var HandlerDefinition.Configurator.workflowRetention: Duration?
  get() {
    return this.workflowRetention()?.toKotlinDuration()
  }
  set(value) {
    this.workflowRetention(value?.toJavaDuration())
  }

/**
 * The journal retention for invocations to this handler.
 *
 * In case the request has an idempotency key, the [idempotencyRetention] caps the journal retention
 * time.
 *
 * *NOTE:* You can set this field only if you register this service against restate-server >= 1.4,
 * otherwise the service discovery will fail.
 */
var HandlerDefinition.Configurator.journalRetention: Duration?
  get() {
    return this.journalRetention()?.toKotlinDuration()
  }
  set(value) {
    this.journalRetention(value?.toJavaDuration())
  }

/**
 * When set to `true` this handler cannot be invoked from the restate-server HTTP and Kafka ingress,
 * but only from other services.
 *
 * *NOTE:* You can set this field only if you register this service against restate-server >= 1.4,
 * otherwise the service discovery will fail.
 */
var HandlerDefinition.Configurator.ingressPrivate: Boolean?
  get() {
    return this.ingressPrivate()
  }
  set(value) {
    this.ingressPrivate(value)
  }

/**
 * When set to `true`, lazy state will be enabled for all invocations to this handler. This is
 * relevant only for workflows and virtual objects.
 *
 * *NOTE:* You can set this field only if you register this service against restate-server >= 1.4,
 * otherwise the service discovery will fail.
 */
var HandlerDefinition.Configurator.enableLazyState: Boolean?
  get() {
    return this.enableLazyState()
  }
  set(value) {
    this.enableLazyState(value)
  }

/**
 * Retry policy used by Restate when invoking this handler.
 *
 * <p><b>NOTE:</b> You can set this field only if you register this service against
 * restate-server >= 1.5, otherwise the service discovery will fail.
 *
 * @see InvocationRetryPolicy
 */
var HandlerDefinition.Configurator.invocationRetryPolicy: InvocationRetryPolicy?
  get() {
    return this.invocationRetryPolicy()
  }
  set(value) {
    this.invocationRetryPolicy(value)
  }

/** Initial delay before the first retry attempt. If unset, server defaults apply. */
var InvocationRetryPolicy.Builder.initialInterval: Duration?
  get() {
    return this.initialInterval()?.toKotlinDuration()
  }
  set(value) {
    this.initialInterval(value?.toJavaDuration())
  }

/** Exponential backoff multiplier used to compute the next retry delay. */
var InvocationRetryPolicy.Builder.exponentiationFactor: Double?
  get() {
    return this.exponentiationFactor()
  }
  set(value) {
    this.exponentiationFactor(value)
  }

/** Upper bound for any computed retry delay. */
var InvocationRetryPolicy.Builder.maxInterval: Duration?
  get() {
    return this.maxInterval()?.toKotlinDuration()
  }
  set(value) {
    this.maxInterval(value?.toJavaDuration())
  }

/**
 * Maximum number of attempts before giving up retrying.
 *
 * The initial call counts as the first attempt; retries increment the count by 1. When giving up,
 * the behavior defined with [onMaxAttempts] will be applied.
 *
 * @see InvocationRetryPolicy.OnMaxAttempts
 */
var InvocationRetryPolicy.Builder.maxAttempts: Int?
  get() {
    return this.maxAttempts()
  }
  set(value) {
    this.maxAttempts(value)
  }

/**
 * Behavior when reaching max attempts.
 *
 * @see InvocationRetryPolicy.OnMaxAttempts
 */
var InvocationRetryPolicy.Builder.onMaxAttempts: InvocationRetryPolicy.OnMaxAttempts?
  get() {
    return this.onMaxAttempts()
  }
  set(value) {
    this.onMaxAttempts(value)
  }

/** [InvocationRetryPolicy] builder function. */
fun invocationRetryPolicy(init: InvocationRetryPolicy.Builder.() -> Unit): InvocationRetryPolicy {
  val builder = InvocationRetryPolicy.builder()
  builder.init()
  return builder.build()
}
